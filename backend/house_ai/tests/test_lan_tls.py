import ipaddress
import json
import shutil
import socket
import ssl
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from lan_tls import LanServer, handler, tls_context


class Backend(BaseHTTPRequestHandler):
    seen = []

    def log_message(self, *_args):
        pass

    def answer(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode() if length else None
        Backend.seen.append((self.command, self.path, self.headers.get("Authorization"),
                             self.headers.get("X-Forwarded-For"), body))
        data = json.dumps({"path": self.path, "body": body}).encode()
        self.send_response(401 if self.headers.get("Authorization") != "Bearer ok" else 200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    do_GET = answer
    do_POST = answer


@unittest.skipIf(shutil.which("openssl") is None, "openssl CLI required to create a test certificate")
class LanTlsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.cert, cls.key = Path(cls.tmp.name, "cert.pem"), Path(cls.tmp.name, "key.pem")
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=localhost", "-addext", "subjectAltName=IP:127.0.0.1",
                        "-keyout", str(cls.key), "-out", str(cls.cert)],
                       check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def start(self, server):
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)

    def setUp(self):
        Backend.seen = []
        self.backend = HTTPServer(("127.0.0.1", 0), Backend)
        self.start(self.backend)
        self.proxy(ipaddress.ip_network("127.0.0.0/8"))
        self.client = ssl.create_default_context(cafile=str(self.cert))

    def proxy(self, allowed):
        server = LanServer(("127.0.0.1", 0),
                           handler(tls_context(str(self.cert), str(self.key)), "127.0.0.1",
                                   self.backend.server_port), allowed)
        self.start(server)
        self.url = f"https://127.0.0.1:{server.server_port}"

    def call(self, path, body=None, auth="Bearer ok", headers=None):
        request = Request(self.url + path, data=body, headers={"Authorization": auth, **(headers or {})})
        try:
            with urlopen(request, timeout=10, context=self.client) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            with error:
                return error.code, json.load(error)

    def test_forwards_get_with_query_and_authorization(self):
        status, data = self.call("/v1/report?day=2026-10-10")
        self.assertEqual((200, "/v1/report?day=2026-10-10"), (status, data["path"]))
        self.assertEqual([("GET", "/v1/report?day=2026-10-10", "Bearer ok", None, None)], Backend.seen)

    def test_forwards_assistant_post_body(self):
        body = json.dumps({"question": "Living?"}).encode()
        status, data = self.call("/v1/assistant/query", body, headers={"Content-Type": "application/json"})
        self.assertEqual((200, '{"question": "Living?"}'), (status, data["body"]))

    def test_backend_status_is_passed_through(self):
        self.assertEqual(401, self.call("/v1/health", auth="Bearer wrong")[0])

    def test_unknown_paths_are_not_forwarded(self):
        self.assertEqual(404, self.call("/admin")[0])
        self.assertEqual(404, self.call("/v1/assistant/query")[0])  # GET on a POST route
        self.assertEqual([], Backend.seen)

    def test_oversized_body_is_rejected(self):
        try:  # the 400 may race with the unread body and arrive as a reset instead
            self.assertEqual(400, self.call("/v1/assistant/query", b"x" * 40000)[0])
        except OSError:  # reset, broken pipe or TLS abort, wrapped or not by urllib
            pass
        self.assertEqual([], Backend.seen)

    def test_backend_down_gives_502(self):
        self.backend.shutdown()
        self.backend.server_close()
        self.assertEqual(502, self.call("/v1/health")[0])

    def test_clients_outside_the_home_subnet_are_dropped(self):
        self.proxy(ipaddress.ip_network("192.168.1.0/24"))
        with self.assertRaises(OSError):
            self.call("/v1/health")
        self.assertEqual([], Backend.seen)

    def test_plain_http_gets_no_answer(self):
        port = int(self.url.rsplit(":", 1)[1])
        with socket.create_connection(("127.0.0.1", port), timeout=10) as raw:
            raw.sendall(b"GET /v1/health HTTP/1.1\r\nHost: x\r\n\r\n")
            try:
                self.assertNotIn(b"HTTP/1.", raw.recv(100))
            except ConnectionResetError:
                pass
        self.assertEqual([], Backend.seen)


if __name__ == "__main__":
    unittest.main()
