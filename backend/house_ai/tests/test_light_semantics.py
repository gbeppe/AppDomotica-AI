import unittest
from datetime import date
from assistant import ask
from energy_tools import validate_lights_snapshot, LIGHT_TOPICS
from light_semantics import STAGES


class Planner:
    def plan(self, question, tools, today):
        return {"operations": [{"id": "who", "tool": "current_lights", "light": "lights_tv"}]}


def snapshot(stage="send_invoked"):
    return {"schema": "house_ai.current_lights_input.v1", "connected": True, "observations": {},
            "commands": {"lights_tv": {
                "schema": "domopi.semantic_command.v1", "entity_id": "lights.tv.power",
                "command_id": "sample:1", "actor": "utente", "actor_policy": "dashboard_only_v2",
                "origin": "app_interface", "attribution": "interface_contract", "requested_state": True,
                "stage": stage, "observed_at_ms": 1790076639642, "received_at_ms": 1790076640000,
                "retained": True, "source_topic": f"zara/interface/semantic/v1/{STAGES[stage]}/lights.tv.power/stat",
                "physical_confirmation": False, "execution_confirmed": False, "publisher_authenticated": False}}}


def diagnostics(result):
    """Spoken answer plus the technical notes shown under provenance and limits."""
    return result['answer'] + '\n' + '\n'.join(
        note for item in result.get('results', []) for note in item['result'].get('limitations', []))


class LightSemanticTests(unittest.TestCase):
    def test_command_without_state_can_answer_without_physical_claim(self):
        result = ask(None, Planner(), "Chi ha acceso la lampada TV?", date(2026, 9, 22), lights_snapshot=snapshot())
        self.assertIn("La lampada TV: stato non disponibile. Ultimo comando registrato: accensione dell'utente",
                      result["answer"])
        for part in ("autore utente", "origine app_interface", "sendData",
                     "non la causa provata", "conferma di esecuzione fisica", "retained"):
            self.assertIn(part, diagnostics(result))
            self.assertNotIn(part, result["answer"])
        self.assertEqual(result["results"][0]["result"]["status"], "available")

    def test_every_stage_has_distinct_wording_and_state_remains_independent(self):
        words = {"routed": "instradato dal router", "dispatched": "inoltrato al ramo",
                 "send_invoked": "sendData", "publisher_input": "ingresso del publisher MQTT"}
        for stage, word in words.items():
            data = snapshot(stage)
            data["observations"]["lights_tv"] = {"value": False, "received_at_ms": 1790076640000,
                "retained": False, "source_topic": LIGHT_TOPICS["lights_tv"]}
            result = ask(None, Planner(), "Chi ha acceso la TV?", date(2026, 9, 22), lights_snapshot=data)
            self.assertIn("La lampada TV è spenta.", result["answer"])
            self.assertIn("accensione, autore utente", diagnostics(result))
            self.assertIn(word, diagnostics(result))

    def test_legacy_android_actor_is_discarded(self):
        data = snapshot()
        del data["commands"]
        data["observations"]["lights_tv"] = {"value": True, "received_at_ms": 2000, "retained": False,
            "source_topic": LIGHT_TOPICS["lights_tv"], "last_actor": "automazione", "action_at_ms": 1000}
        clean = validate_lights_snapshot(data)
        self.assertNotIn("last_actor", clean["observations"]["lights_tv"])
        result = ask(None, Planner(), "Chi ha acceso la TV?", date(2026, 9, 22), lights_snapshot=data)
        self.assertIn("Autore non disponibile", diagnostics(result))
        self.assertNotIn("autore automazione", diagnostics(result))
        self.assertNotIn("automazione", result["answer"])

    def test_bad_provenance_and_claims_are_rejected(self):
        for key, value in (("actor_policy", "old"), ("entity_id", "lights.libreria.power"),
                           ("origin", "alexa"), ("stage", "confirmed"), ("physical_confirmation", True),
                           ("requested_state", 1), ("observed_at_ms", True), ("source_topic", "anything")):
            data = snapshot()
            data["commands"]["lights_tv"][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_lights_snapshot(data)

    def test_alexa_is_automation_and_new_lights_match_catalog(self):
        data = snapshot()
        data["commands"]["lights_tv"].update(actor="automazione", origin="alexa")
        self.assertEqual(validate_lights_snapshot(data)["commands"]["lights_tv"]["actor"], "automazione")
        for light in ("lights_laundry", "lights_porch_entrance", "lights_service_entrance"):
            self.assertIn(light, LIGHT_TOPICS)

    def test_retired_old_client_states_are_validated_then_excluded(self):
        data = snapshot()
        data["observations"]["lights_hifi"] = {"value": True, "received_at_ms": 2000,
            "retained": True, "source_topic": "zara/interface/lights/hifi/power/stat"}
        self.assertEqual(validate_lights_snapshot(data)["observations"], {})
        data["observations"]["lights_hifi"]["source_topic"] = "invalid"
        with self.assertRaises(ValueError):
            validate_lights_snapshot(data)
