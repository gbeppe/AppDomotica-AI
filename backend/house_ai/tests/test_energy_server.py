import json
import threading
import unittest
from http.server import HTTPServer
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

from server import handler


class EnergyServerTests(unittest.TestCase):
    def setUp(self):
        class Client:
            def history(self, _feed, start, _end, interval):
                return [[start * 1000, 50], [(start + interval) * 1000, 50]]
        self.token = "t" * 24
        class Planner:
            def plan(self, question, tools, today):
                if "corrente" in question:
                    return {"operations": [{"id": "now", "tool": "current_energy_metric",
                        "metric": "solar_power_w"}]}
                return {"operations": [{"id": "one", "tool": "energy_metric",
                    "metric": "soc_mean_percent", "start": "2026-09-01", "end": "2026-09-02"}]}
        self.server = HTTPServer(("127.0.0.1", 0), handler(Client(), self.token, None, Planner()))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def get(self, path, authenticated=True):
        request = Request(self.url + path)
        if authenticated:
            request.add_header("Authorization", "Bearer " + self.token)
        with urlopen(request) as response:
            return response.status, json.load(response)

    def test_authenticated_energy_query(self):
        status, body = self.get("/v1/energy/query?q=" + quote("SOC medio ultimi 7 giorni"))
        self.assertEqual(status, 200)
        self.assertEqual(body["schema"], "house_ai.energy_answer.v1")
        self.assertEqual(body["result"]["source"]["feed_id"], 304)

    def test_query_validation_and_authentication(self):
        with self.assertRaises(HTTPError) as unauthenticated:
            self.get("/v1/energy/query?q=test", False)
        self.assertEqual(unauthenticated.exception.code, 401)
        unauthenticated.exception.close()
        with self.assertRaises(HTTPError) as invalid:
            self.get("/v1/energy/query")
        self.assertEqual(invalid.exception.code, 400)
        invalid.exception.close()

    def test_dynamic_assistant_post(self):
        data = json.dumps({"question": "Una domanda mai cablata"}).encode()
        request = Request(self.url + "/v1/assistant/query", data=data, method="POST",
                          headers={"Authorization": "Bearer " + self.token,
                                   "Content-Type": "application/json"})
        with urlopen(request) as response:
            body = json.load(response)
        self.assertEqual(body["schema"], "house_ai.assistant_answer.v1")
        self.assertEqual(body["results"][0]["metric"], "soc_mean_percent")

    def test_dynamic_post_accepts_current_digital_twin_snapshot(self):
        payload = {"question": "potenza corrente", "current_energy": {
            "schema": "house_ai.current_energy_input.v1", "connected": True,
            "observations": {"solar_power_w": {"value": 2500.0,
                "received_at_ms": 1789200000000, "retained": True,
                "source_topic": "zara/interface/energy/solar/power/stat"}}}}
        request = Request(self.url + "/v1/assistant/query",
            data=json.dumps(payload).encode(), method="POST",
            headers={"Authorization": "Bearer " + self.token,
                     "Content-Type": "application/json"})
        with urlopen(request) as response:
            body = json.load(response)
        self.assertIn("2500.0 W", body["answer"])
        self.assertEqual(body["results"][0]["result"]["schema"],
                         "house_ai.current_energy_result.v1")
