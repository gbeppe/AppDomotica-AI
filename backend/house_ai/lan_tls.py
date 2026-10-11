"""HTTPS entry point on the home LAN, so AI Smart works at home without Tailscale.

Terminates TLS with the Tailscale certificate of the same host name (Pi-hole resolves
that name to the LAN address) and forwards the backend API to 127.0.0.1:8765. Only
clients of the home subnet are served; authentication stays in the backend.
Independent from Apache, Pi-hole and Tailscale Serve: stopping it restores the
previous setup.
"""
import http.client
import ipaddress
import os
import socket
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

ROUTES = {"GET": ("/v1/health", "/v1/health/details", "/v1/report", "/v1/energy/query", "/v1/summary/day"),
          "POST": ("/v1/assistant/query",)}
MAX_BODY = 32768
HANDSHAKE_TIMEOUT_S = 15
UPSTREAM_TIMEOUT_S = 130  # backend closes idle requests after 120 s
MAX_CLIENTS = 16


def tls_context(cert_file, key_file):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(cert_file, key_file)
    return context


class LanServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, handler, allowed, freebind=False):
        self.allowed = allowed
        self.freebind = freebind
        self.slots = threading.BoundedSemaphore(MAX_CLIENTS)
        super().__init__(address, handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def server_bind(self):
        if self.freebind:  # bind even if the LAN address is not up yet at boot
            self.socket.setsockopt(socket.IPPROTO_IP, getattr(socket, "IP_FREEBIND", 15), 1)
        super().server_bind()

    def verify_request(self, request, client_address):
        try:
            return ipaddress.ip_address(client_address[0]) in self.allowed
        except ValueError:
            return False

    def handle_error(self, request, client_address):
        pass  # failed handshakes and resets are routine; never log household traffic


def handler(context, upstream_host, upstream_port):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def setup(self):
            self.request.settimeout(HANDSHAKE_TIMEOUT_S)
            self.request = context.wrap_socket(self.request, server_side=True)
            self.request.settimeout(UPSTREAM_TIMEOUT_S)
            super().setup()

        def finish(self):
            try:
                super().finish()
            finally:
                self.request.close()

        def log_message(self, *_args):
            pass

        def reply(self, status, body, content_type="application/json; charset=utf-8"):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.close_connection = True

        def forward(self):
            if urlsplit(self.path).path not in ROUTES[self.command]:
                self.reply(404, b'{"error": "Not found"}')
                return
            body = None
            if self.command == "POST":
                try:
                    length = int(self.headers.get("Content-Length", ""))
                except ValueError:
                    length = -1
                if not 0 < length <= MAX_BODY or self.headers.get("Transfer-Encoding") is not None:
                    self.reply(400, b'{"error": "Invalid request"}')
                    return
                body = self.rfile.read(length)
            headers = {name: self.headers[name] for name in ("Authorization", "Content-Type")
                       if self.headers.get(name) is not None}
            upstream = http.client.HTTPConnection(upstream_host, upstream_port, timeout=UPSTREAM_TIMEOUT_S)
            try:
                upstream.request(self.command, self.path, body=body, headers=headers)
                response = upstream.getresponse()
                data = response.read()
                self.reply(response.status, data,
                           response.getheader("Content-Type", "application/json; charset=utf-8"))
            except (OSError, http.client.HTTPException):
                self.reply(502, b'{"error": "Backend unavailable"}')
            finally:
                upstream.close()

        do_GET = forward
        do_POST = forward
    return Handler


def main():
    allowed = ipaddress.ip_network(os.environ.get("HOUSE_AI_LAN_ALLOW", "192.168.1.0/24"))
    upstream = urlsplit(os.environ.get("HOUSE_AI_LAN_UPSTREAM", "http://127.0.0.1:8765"))
    if upstream.scheme != "http" or upstream.hostname not in ("127.0.0.1", "localhost"):
        raise SystemExit("HOUSE_AI_LAN_UPSTREAM must be a local http URL")
    context = tls_context(os.environ["HOUSE_AI_LAN_CERT"], os.environ["HOUSE_AI_LAN_KEY"])
    server = LanServer((os.environ.get("HOUSE_AI_LAN_BIND", "192.168.1.20"),
                        int(os.environ.get("HOUSE_AI_LAN_PORT", "443"))),
                       handler(context, upstream.hostname, upstream.port or 80), allowed, freebind=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
