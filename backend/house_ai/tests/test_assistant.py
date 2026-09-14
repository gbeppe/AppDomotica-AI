import unittest
from datetime import date

from assistant import ask
from energy_tools import execute, validate_lights_snapshot, validate_plan


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

    def test_daily_extreme_plan_is_closed_and_presented(self):
        operation = {"id": "peak", "tool": "energy_daily_extreme",
                     "metric": "grid_import_kwh", "start": "2026-09-01",
                     "end": "2026-09-04", "extremum": "maximum"}
        result = ask(Client(), Planner({"operations": [operation]}),
                     "Quale giorno ha avuto il maggior prelievo?", date(2026, 9, 12))
        self.assertEqual(result["status"], "complete")
        self.assertIn("tra i giorni con copertura completa", result["answer"].lower())
        self.assertIn("Giorni completi confrontati: 3", result["answer"])
        self.assertIn("Considerando tutti i dati EmonCMS disponibili", result["answer"])
        for invalid in ({**operation, "extremum": "largest"},
                        {**operation, "metric": "soc_mean_percent"},
                        {**operation, "extra": True}):
            with self.assertRaises(ValueError):
                validate_plan({"operations": [invalid]})

    def test_current_digital_twin_metric_keeps_provenance(self):
        planner = Planner({"operations": [{"id": "now", "tool": "current_energy_metric",
                                            "metric": "solar_power_w"}]})
        snapshot = {"schema": "house_ai.current_energy_input.v1", "connected": True,
                    "observations": {"solar_power_w": {"value": 3210.0,
                        "received_at_ms": 1789200000000, "retained": True,
                        "source_topic": "zara/interface/energy/solar/power/stat"}}}
        result = ask(None, planner, "Come va il fotovoltaico adesso?",
                     date(2026, 9, 12), snapshot)
        self.assertIn("3210,0 W", result["answer"])
        self.assertTrue(result["results"][0]["result"]["observation"]["retained"])

    def test_missing_and_disconnected_current_data_are_qualified(self):
        planner = Planner({"operations": [{"id": "now", "tool": "current_energy_metric",
                                            "metric": "battery_soc_percent"}]})
        empty = {"schema": "house_ai.current_energy_input.v1", "connected": False,
                 "observations": {}}
        self.assertIn("dato non disponibile", ask(None, planner, "carica?", date(2026, 9, 12), empty)["answer"])
        snapshot = {**empty, "observations": {"battery_soc_percent": {"value": 81.0,
            "received_at_ms": 1, "retained": False,
            "source_topic": "custom/energy/battery/soc/stat"}}}
        self.assertIn("Digital Twin disconnesso", ask(None, planner, "carica?", date(2026,9,12), snapshot)["answer"])

    def test_invalid_current_snapshot_is_rejected(self):
        operation = {"operations": [{"id": "x", "tool": "current_energy_metric",
                                      "metric": "battery_soc_percent"}]}
        for snapshot in [
            {"schema": "wrong", "connected": True, "observations": {}},
            {"schema": "house_ai.current_energy_input.v1", "connected": True,
             "observations": {"battery_soc_percent": {"value": 101,
                "received_at_ms": 1, "retained": False, "source_topic": "x/stat"}}},
        ]:
            with self.assertRaises(ValueError):
                execute(None, operation, current_snapshot=snapshot)

    def test_current_air_conditioner_uses_recorded_reason_and_returns_context(self):
        planner = Planner({"operations": [{"id": "clima", "tool": "current_air_conditioner"}]})
        snapshot = {"schema": "house_ai.current_climate_input.v1", "connected": True,
                    "observations": {
                        "current_state": {"value": "ACCESO", "received_at_ms": 1789200000000,
                            "retained": True, "source_topic": "zara/interface/stato_condizionatore/stato_attuale/stat"},
                        "temperature_set_c": {"value": "25", "received_at_ms": 1789200000001,
                            "retained": False, "source_topic": "zara/interface/stato_condizionatore/temperatura_impostata_c/stat"},
                        "recorded_reason": {"value": "humidex sopra soglia", "received_at_ms": 1789200000002,
                            "retained": False, "source_topic": "zara/interface/stato_condizionatore/motivo_logica/stat"}}}
        result = ask(None, planner, "Perché il condizionatore è acceso?", date(2026, 9, 12),
                     climate_snapshot=snapshot)
        self.assertIn("Stato dichiarato: ACCESO", result["answer"])
        self.assertIn("Motivo registrato dal controller: humidex sopra soglia", result["answer"])
        self.assertIn("non una deduzione", result["answer"])
        self.assertEqual(result["conversation_context"],
                         {"domain": "climate", "focus": "air_conditioner"})

    def test_bare_why_is_expanded_only_for_valid_air_conditioner_context(self):
        plan = {"operations": [{"id": "clima", "tool": "current_air_conditioner"}]}
        planner = Planner(plan)
        ask(None, planner, "Perché?", date(2026, 9, 12),
            conversation_context={"domain": "climate", "focus": "air_conditioner"})
        self.assertEqual(planner.seen[0], "Perché il condizionatore è nello stato attuale?")
        with self.assertRaises(ValueError):
            ask(None, planner, "Perché?", date(2026, 9, 12),
                conversation_context={"domain": "energy", "focus": "air_conditioner"})

    def test_invalid_climate_snapshot_is_rejected(self):
        operation = {"operations": [{"id": "clima", "tool": "current_air_conditioner"}]}
        base = {"schema": "house_ai.current_climate_input.v1", "connected": True,
                "observations": {}}
        invalid = [
            {**base, "observations": {"invented": {"value": "on", "received_at_ms": 1,
                "retained": False, "source_topic": "zara/interface/stato_condizionatore/x/stat"}}},
            {**base, "observations": {"current_state": {"value": "on", "received_at_ms": 1,
                "retained": False, "source_topic": "zara/interface/stato_condizionatore/stato_attuale/cmd"}}},
        ]
        for snapshot in invalid:
            with self.assertRaises(ValueError):
                execute(None, operation, climate_snapshot=snapshot)

    def test_current_lights_are_read_only_declared_states(self):
        plan = {"operations": [{"id": "luci", "tool": "current_lights", "light": "all"}]}
        snapshot = {"schema": "house_ai.current_lights_input.v1", "connected": True,
                    "observations": {"lights_libreria": {"value": True,
                        "received_at_ms": 1789200000000, "retained": True,
                        "source_topic": "zara/interface/lights/libreria/power/stat"}}}
        result = ask(None, Planner(plan), "Quali luci sono accese?", date(2026, 9, 12),
                     lights_snapshot=snapshot)
        self.assertIn("risultano accesi 1", result["answer"])
        self.assertIn("Libreria", result["answer"])
        self.assertIn("non prova freschezza, durata o stato fisico", result["answer"])

        invalid = {**snapshot, "observations": {"lights_libreria": {
            **snapshot["observations"]["lights_libreria"], "source_topic": "lights/libreria/power/cmnd"}}}
        with self.assertRaises(ValueError):
            validate_lights_snapshot(invalid)

    def test_light_command_is_closed_and_contains_no_topic(self):
        plan = {"operations": [{"id": "cmd", "tool": "set_light_state",
                                "light": "lights_libreria", "state": "on"}]}
        result = ask(None, Planner(plan), "Accendi la libreria", date(2026, 9, 12))
        command = result["results"][0]["result"]
        self.assertEqual(command["schema"], "house_ai.light_command.v1")
        self.assertNotIn("topic", command)
        self.assertEqual(result["status"], "complete")
        with self.assertRaises(ValueError):
            validate_plan({"operations": [{**plan["operations"][0], "light": "inventata"}]})
