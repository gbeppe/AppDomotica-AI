import json
import unittest
from pathlib import Path
from tools.build_device_catalog import objects


class DeviceCatalogTest(unittest.TestCase):
    def test_parser_ignores_commented_routes_and_nested_braces(self):
        source = '''const registry = [
        // --- SENSOR ---
        /* { id: "disabled", int_cmd: "bad" }, */
        { id: "real", int_stat: "ok", transform: (p) => { return "}"; } },
        ];'''
        rows=list(objects(source,'registry'))
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0][0],{'id':'real','int_stat':'ok'})
        self.assertEqual(rows[0][2],'SENSOR')

    def test_catalog_separates_commands_obsolete_routes_and_gaps(self):
        data=json.loads(Path(__file__).resolve().parents[1].joinpath('device_catalog.json').read_text())
        entries={e['id']:e for e in data['entries']}
        self.assertEqual(entries['garage.gate_1']['access_in_router'],'command_only')
        self.assertIsNone(entries['garage.gate_1']['state_topic'])
        self.assertEqual(entries['heating.heat_pump.enabled']['lifecycle'],'obsolete_per_runtime_contract')
        self.assertNotIn('climate.ai_enabling',entries)
        self.assertEqual(data['composite_topic'],'casa/clima/stato_completo')
        topics=[e['state_topic'] for e in data['entries'] if e['state_topic']]
        self.assertEqual(len(topics),len(set(topics)))
        self.assertEqual(len(data['screens']),13)
        self.assertTrue(any('lavanderia' in g['topic'] for g in data['gaps']))
