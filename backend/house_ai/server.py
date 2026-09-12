"""Local read-only API. Use a TLS reverse proxy for remote access."""
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from emoncms import Emoncms
from energy_query import answer as energy_answer
from service import report


def handler(client, token, log_path):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass  # Do not persist household queries or credentials.

        def send_json(self, status, value):
            body = json.dumps(value, ensure_ascii=False, allow_nan=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            provided = self.headers.get("Authorization", "")
            if not hmac.compare_digest(provided.encode(), ("Bearer " + token).encode()):
                self.send_json(401, {"error": "Authentication required"})
                return
            url = urlsplit(self.path)
            try:
                if url.path == "/v1/health":
                    self.send_json(200, {"status": "ok", "mode": "read_only"})
                elif url.path == "/v1/report":
                    days = parse_qs(url.query).get("day", [])
                    if len(days) != 1:
                        raise ValueError("One day is required")
                    self.send_json(200, report(client, log_path, days[0]))
                elif url.path == "/v1/energy/query":
                    questions = parse_qs(url.query).get("q", [])
                    if len(questions) != 1 or len(questions[0]) > 500:
                        raise ValueError("One bounded question is required")
                    self.send_json(200, energy_answer(client, questions[0]))
                else:
                    self.send_json(404, {"error": "Not found"})
            except ValueError:
                self.send_json(400, {"error": "Invalid request parameters"})
            except (OSError, RuntimeError):
                self.send_json(502, {"error": "Source unavailable"})
    return Handler


def main():
    token = os.environ.get("HOUSE_AI_TOKEN", "")
    if len(token) < 24:
        raise SystemExit("Set HOUSE_AI_TOKEN to a secret of at least 24 characters")
    client = Emoncms(os.environ.get("EMONCMS_URL", ""), os.environ.get("EMONCMS_API_KEY", ""))
    log = os.environ.get("HOUSE_AI_CLIMATE_LOG")
    server = HTTPServer((os.environ.get("HOUSE_AI_BIND", "127.0.0.1"),
                         int(os.environ.get("HOUSE_AI_PORT", "8765"))),
                        handler(client, token, Path(log) if log else None))
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
