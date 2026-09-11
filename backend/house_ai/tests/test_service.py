import unittest
from service import normalize, integrate, report
from evidence import day_bounds


class ServiceTest(unittest.TestCase):
    def test_missing_samples_are_not_bridged(self):
        self.assertEqual(integrate([[0,1000],[30000,1000],[60000,None],[90000,1000]])[1],30000)
        self.assertAlmostEqual(integrate([[0,1000],[30000,1000]])[0],1/120)

    def test_soc_spike_removed_but_sustained_change_kept(self):
        start,_=day_bounds("2026-09-06")
        raw=[[start+i*30000,v] for i,v in enumerate([50,99,50,49,45,45])]
        points, excluded=normalize(raw,start,start+200000,True)
        self.assertEqual(len(excluded),1)
        self.assertIsNone(points[1][1])
        self.assertEqual(points[-1][1],45)

    def test_report_with_no_log_and_empty_feed(self):
        class Client:
            def history(self,*args): return []
        result=report(Client(),None,"2026-09-06")
        self.assertIsNone(result['metrics']['ac_energy_kwh'])
        self.assertEqual(result['metrics']['coverage_ratio'],0)
        self.assertEqual(result['evidence']['events'],[])
        self.assertIn('Log climatico non configurato.', result['limitations'])
