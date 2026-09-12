import unittest
from datetime import date

from energy_query import answer, interpret, load_sources


class EnergyQueryTests(unittest.TestCase):
    def setUp(self):
        self.today = date(2026, 9, 12)

    def test_supported_questions_and_periods(self):
        grid = interpret("Quanti kWh ho prelevato dalla rete nel mese scorso?", self.today)
        self.assertEqual((grid["metric"], grid["start"], grid["end"]),
                         ("grid_import_kwh", "2026-08-01", "2026-09-01"))
        soc = interpret("Qual è stato il SOC medio negli ultimi 7 giorni?", self.today)
        self.assertEqual((soc["metric"], soc["start"], soc["end"]),
                         ("soc_mean_percent", "2026-09-05", "2026-09-12"))

    def test_ambiguous_period_requests_clarification(self):
        for question in ("prelievo rete ultimo mese", "carica media ultima settimana"):
            self.assertEqual(interpret(question, self.today)["status"],
                             "clarification_required")

    def test_compound_and_unsupported_do_not_call_source(self):
        for question in ("prelievo rete e carica Tesla mese scorso", "quanto piove?"):
            result = answer(None, question, self.today)
            self.assertIn(result["status"], ("clarification_required", "unsupported"))

    def test_answer_exposes_partial_coverage(self):
        class Client:
            def history(self, _feed, start, _end, interval):
                return [[start * 1000, 50], [(start + interval) * 1000, 50]]
        result = answer(Client(), "SOC medio settimana scorsa", self.today)
        self.assertEqual(result["status"], "partial")
        self.assertIn("solo gli intervalli coperti", result["answer"])
        self.assertEqual(result["result"]["source"]["feed_id"], 304)

    def test_catalog_has_validated_sources(self):
        sources = load_sources()
        self.assertEqual(sources["grid_import_kwh"]["import_sign"], 1)
        self.assertEqual(sources["soc_mean_percent"]["unit"], "%")
