"""Local read-only API. Use a TLS reverse proxy for remote access."""
import hmac
import json
import os
import stat
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from emoncms import Emoncms
from energy_query import answer as energy_answer
from service import report
from assistant import ask as assistant_ask
from planner import HttpJsonPlanner
from log_tools import SOURCES


def detailed_health(client, planner, log_root):
    components = [{'id': 'backend', 'label': 'Backend House AI (.20)',
                   'status': 'online', 'detail': 'API read-only operativa'}]
    started = time.monotonic()
    try:
        gateway = planner.health() if planner is not None else None
        components.append({'id': 'gateway', 'label': 'Gateway modello (.20)',
                           'status': 'online' if gateway else 'offline',
                           'latency_ms': int((time.monotonic() - started) * 1000),
                           'detail': 'Processo locale raggiungibile' if gateway else 'Non configurato'})
        provider_status = gateway['provider_status'] if gateway else 'unknown'
        components.append({'id': 'groq', 'label': 'Groq',
                           'status': provider_status,
                           'last_checked_ms': gateway.get('provider_last_checked_ms') if gateway else None,
                           'detail': {'online': 'Ultima richiesta riuscita',
                                      'offline': 'Ultima richiesta non riuscita',
                                      'unknown': 'Configurato, non ancora verificato dal riavvio'}[provider_status]})
    except RuntimeError:
        components.extend([
            {'id': 'gateway', 'label': 'Gateway modello (.20)', 'status': 'offline',
             'latency_ms': int((time.monotonic() - started) * 1000), 'detail': 'Non raggiungibile'},
            {'id': 'groq', 'label': 'Groq', 'status': 'unknown',
             'detail': 'Gateway non raggiungibile'}])
    started = time.monotonic()
    try:
        feeds = client.catalog()
        components.append({'id': 'emoncms', 'label': 'Storico EmonCMS (.15)',
                           'status': 'online', 'latency_ms': int((time.monotonic() - started) * 1000),
                           'detail': '{} feed leggibili'.format(len(feeds))})
    except RuntimeError:
        components.append({'id': 'emoncms', 'label': 'Storico EmonCMS (.15)',
                           'status': 'offline', 'latency_ms': int((time.monotonic() - started) * 1000),
                           'detail': 'Sorgente non raggiungibile'})
    missing = []
    for filename, _key, _kind in SOURCES.values():
        try:
            if not stat.S_ISREG((Path(log_root) / filename).lstat().st_mode):
                missing.append(filename)
        except (OSError, TypeError):
            missing.append(filename)
    components.append({'id': 'logs', 'label': 'Log Node-RED (.20)',
                       'status': 'online' if not missing else 'offline',
                       'detail': '4 file leggibili' if not missing else '{} file non disponibili'.format(len(missing))})
    return {'schema': 'house_ai.stack_health.v1', 'mode': 'read_only',
            'checked_at_ms': int(time.time() * 1000), 'components': components}


def handler(client, token, log_path, planner=None, log_root=None):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(120)

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
                    self.send_json(200, {"status": "ok", "mode": "read_only",
                                         "planner_configured": planner is not None,
                                         "log_root_configured": log_root is not None})
                elif url.path == "/v1/health/details":
                    self.send_json(200, detailed_health(client, planner, log_root))
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

        def do_POST(self):
            provided = self.headers.get("Authorization", "")
            if not hmac.compare_digest(provided.encode(), ("Bearer " + token).encode()):
                self.send_json(401, {"error": "Authentication required"})
                return
            if urlsplit(self.path).path != "/v1/assistant/query":
                self.send_json(404, {"error": "Not found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 8192 or self.headers.get("Transfer-Encoding") is not None:
                    raise ValueError("Invalid body size")
                body = json.loads(self.rfile.read(length))
                if (not isinstance(body, dict)
                        or set(body) - {"question", "current_energy"}
                        or "question" not in body):
                    raise ValueError("Invalid body")
                self.send_json(200, assistant_ask(client, planner, body["question"],
                                                  current_snapshot=body.get("current_energy"),
                                                  log_root=log_root))
            except (ValueError, json.JSONDecodeError):
                self.send_json(400, {"error": "Invalid request"})
            except (OSError, RuntimeError):
                self.send_json(502, {"error": "Planner or source unavailable"})
    return Handler


def main():
    token = os.environ.get("HOUSE_AI_TOKEN", "")
    if len(token) < 24:
        raise SystemExit("Set HOUSE_AI_TOKEN to a secret of at least 24 characters")
    client = Emoncms(os.environ.get("EMONCMS_URL", ""), os.environ.get("EMONCMS_API_KEY", ""))
    log = os.environ.get("HOUSE_AI_CLIMATE_LOG")
    planner_url = os.environ.get("HOUSE_AI_PLANNER_URL", "")
    planner = (HttpJsonPlanner(planner_url, os.environ.get("HOUSE_AI_PLANNER_TOKEN", ""))
               if planner_url else None)
    server = HTTPServer((os.environ.get("HOUSE_AI_BIND", "127.0.0.1"),
                         int(os.environ.get("HOUSE_AI_PORT", "8765"))),
                        handler(client, token, Path(log) if log else None, planner,
                                os.environ.get("HOUSE_AI_LOG_ROOT")))
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
