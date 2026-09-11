import json
import tempfile
import unittest
from pathlib import Path

from evidence import climate_day, day_bounds
from emoncms import Emoncms


class EvidenceTest(unittest.TestCase):
    def test_local_day_handles_dst(self):
        for day, hours in [("2026-03-29", 23), ("2026-10-25", 25)]:
            start, end = day_bounds(day)
            self.assertEqual(end - start, hours * 3600000)

    def test_exclusions_preserve_evidence_and_unknown_cause(self):
        start, end = day_bounds("2026-09-06")
        valid = {"timestamp": start, "logica_controllo":
                 {"motivo_ac": "ESECUZIONE_SPEGNIMENTO"}}
        fallback = {"timestamp": start + 1000, "logica_controllo":
                    {"previsione_solare_data": "N/A"},
                    "condizioni_ambientali": {"temp_cameraMatrimoniale": 0}}
        forecast_only = {"timestamp": start + 2000, "logica_controllo":
                         {"previsione_solare_data": "N/A"}}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "events.log"
            original = "RESET\n" + "\n".join(json.dumps(r) for r in
                [valid, valid, fallback, forecast_only, {"timestamp": end}])
            path.write_text(original)
            result = climate_day(path, "2026-09-06")
            self.assertEqual(path.read_text(), original)
        self.assertEqual(len(result["events"]), 2)
        self.assertEqual(len(result["excluded"]), 2)
        self.assertEqual(result["events"][0]["source"]["line"], 2)
        self.assertEqual(result["events"][0]["causal_detail"], "unspecified")
        self.assertEqual(result["events"][0]["physical_confirmation"], "not_checked")

    def test_request_limit_before_network(self):
        client = Emoncms("http://localhost", "test-only")
        with self.assertRaises(ValueError):
            client.history(1, 0, 1000000, 1)
        with self.assertRaises(ValueError):
            Emoncms("http://user:password@localhost", "test-only")


if __name__ == "__main__":
    unittest.main()
