import unittest
from datetime import date

from assistant import ask
from energy_tools import execute, validate_plan


class Planner:
    def __init__(self, plan):
        self.result = plan
        self.seen = None

    def plan(self, question, tools, today):
        self.seen = (question, tools, today)
        return self.result


class Client:
    def history(self, feed, start, end, interval):
        value = 50 if feed == 304 else 1000
        return [[t * 1000, value] for t in range(start, end + 1, interval)]


class AssistantTests(unittest.TestCase):
    def test_arbitrary_wording_is_delegated_without_phrase_matching(self):
        planner = Planner({"operations": [{"id": "x", "tool": "energy_metric",
            "metric": "solar_production_kwh", "start": "2026-09-01", "end": "2026-09-02"}]})
        result = ask(Client(), planner, "Che giornata luminosa abbiamo trasformato in energia?",
                     date(2026, 9, 12))
        self.assertEqual(result["status"], "complete")
        self.assertIn("Produzione fotovoltaica", result["answer"])
        self.assertEqual(planner.seen[0], "Che giornata luminosa abbiamo trasformato in energia?")
        self.assertIn("solar_production_kwh", planner.seen[1]["tools"][0]["parameters"]["metric"])

    def test_compound_plan_executes_multiple_tools(self):
        planner = Planner({"operations": [
            {"id": "a", "tool": "energy_metric", "metric": "grid_import_kwh",
             "start": "2026-09-01", "end": "2026-09-02"},
            {"id": "b", "tool": "energy_metric", "metric": "soc_mean_percent",
             "start": "2026-09-01", "end": "2026-09-02"}]})
        result = ask(Client(), planner, "Confrontami rete e batteria", date(2026, 9, 12))
        self.assertEqual(len(result["results"]), 2)
        self.assertIn("Livello medio", result["answer"])

    def test_clarification_does_not_touch_data(self):
        planner = Planner({"clarification": "Quale settimana intendi?"})
        result = ask(None, planner, "come è andata?", date(2026, 9, 12))
        self.assertEqual(result["status"], "clarification_required")

    def test_unconfigured_planner_is_explicit(self):
        self.assertEqual(ask(None, None, "consumi?")["status"], "not_configured")

    def test_invalid_plans_fail_before_source(self):
        invalid = [
            {"operations": [{"id": "x", "tool": "shell", "metric": "grid_import_kwh",
                              "start": "2026-09-01", "end": "2026-09-02"}]},
            {"operations": [{"id": "x", "tool": "energy_metric", "metric": "invented",
                              "start": "2026-09-01", "end": "2026-09-02"}]},
            {"operations": []},
            {"clarification": "?", "operations": [{"id": "x"}]}
        ]
        for plan in invalid:
            with self.assertRaises(ValueError):
                execute(None, plan)

    def test_plan_is_strict_and_bounded(self):
        operation = {"id": "x", "tool": "energy_metric", "metric": "grid_import_kwh",
                     "start": "2026-09-01", "end": "2026-09-02"}
        with self.assertRaises(ValueError):
            validate_plan({"operations": [operation] * 7})
        with self.assertRaises(ValueError):
            validate_plan({"operations": [{**operation, "extra": True}]})
