import json
import threading
import unittest
from datetime import date
from http.server import BaseHTTPRequestHandler, HTTPServer
from planner import HttpJsonPlanner
from energy_tools import catalog


class PlannerProtocolTests(unittest.TestCase):
    def test_gateway_receives_catalog_question_and_rome_calendar_without_household_credentials(self):
        captured = {}
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_POST(self):
                captured['body'] = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                captured['authorization'] = self.headers['Authorization']
                raw = json.dumps({'plan': {'clarification': 'Quale periodo?'}}).encode()
                self.send_response(200)
                self.send_header('Content-Length', str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
        server = HTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            result = HttpJsonPlanner(f'http://127.0.0.1:{server.server_port}', 'fixture-token').plan('Quanto ho usato?', catalog(), date(2026,9,12))
            self.assertEqual(result['clarification'], 'Quale periodo?')
            body = captured['body']
            self.assertEqual(body['today'], '2026-09-12')
            self.assertEqual(body['timezone'], 'Europe/Rome')
            self.assertEqual(body['question'], 'Quanto ho usato?')
            self.assertEqual(captured['authorization'], 'Bearer fixture-token')
            self.assertEqual(set(body), {'schema','instruction','question','today','timezone','tool_catalog'})
        finally:
            server.shutdown(); server.server_close(); thread.join()
