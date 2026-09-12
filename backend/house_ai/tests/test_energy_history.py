import unittest
from datetime import date
from energy_history import calculate, energy_report, period_bounds, relative_period


class EnergyTests(unittest.TestCase):
    def calc(self, values, metric='grid_import_kwh', sign=1, end=60):
        t = 1788213600000
        return calculate([[t+s*1000, v] for s, v in values], t, t+end*1000, metric, 30000, sign)

    def test_import_export_crossing(self):
        self.assertAlmostEqual(self.calc([(0, 1000), (30, 1000), (60, 1000)])['value'], 1/60)
        self.assertEqual(self.calc([(0, -1000), (30, -1000)])['value'], 0)
        self.assertAlmostEqual(self.calc([(0, -1000), (30, -1000)], sign=-1)['value'], 1/120)
        self.assertAlmostEqual(self.calc([(0, -1000), (30, 1000)])['value'], 250*30/3600000)

    def test_time_weighted_soc(self):
        r = self.calc([(0, 0), (10, 100), (30, 100)], 'soc_mean_percent', end=30)
        self.assertAlmostEqual(r['value'], 2500/30)
        self.assertEqual(r['status'], 'complete')

    def test_missing_invalid_gap_and_duplicates(self):
        for values in [[(0, 1), (30, None), (60, 1)], [(0, 1), (60, 1)],
                       [(0, 1), (30, float('nan'))], [(0, 1), (30, True)],
                       [(0, 50), (30, 40), (30, 60), (30, 60), (60, 50)]]:
            self.assertIsNone(self.calc(values)['value'])
        self.assertIsNone(self.calc([(0, 50), (30, 101)], 'soc_mean_percent')['value'])

    def test_partial_not_extrapolated(self):
        r = self.calc([(0, 1000), (30, 1000)])
        self.assertEqual(r['coverage_ratio'], .5)
        self.assertAlmostEqual(r['value'], 1/120)
        self.assertEqual(r['status'], 'partial')

    def test_calendar_and_dst(self):
        for day, end, hours in [('2026-03-29', '2026-03-30', 23), ('2026-10-25', '2026-10-26', 25)]:
            a, b = period_bounds(day, end)
            self.assertEqual(b-a, hours*3600000)
        self.assertEqual(relative_period('previous_month', date(2026, 9, 12)), ('2026-08-01', '2026-09-01'))
        self.assertEqual(relative_period('previous_week', date(2026, 9, 12)), ('2026-08-31', '2026-09-07'))
        self.assertEqual(relative_period('last_7_days', date(2026, 9, 12)), ('2026-09-05', '2026-09-12'))

    def test_month_chunking(self):
        calls = []
        outer = self
        class Client:
            def history(self, feed, start, end, interval):
                calls.append((start, end))
                outer.assertLessEqual((end-start)/interval, 10000)
                return [[t*1000, 1000] for t in range(start, end, interval)]
        r = energy_report(Client(), '2026-08-01', '2026-09-01', 'grid_import_kwh', 305, 'W', 1)
        self.assertEqual(r['status'], 'complete')
        self.assertAlmostEqual(r['value'], 744)
        self.assertGreater(len(calls), 1)

    def test_validate_before_network(self):
        for metric, unit, sign in [('grid_import_kwh', 'W', None), ('grid_import_kwh', '%', 1), ('soc_mean_percent', 'W', None)]:
            with self.assertRaises(ValueError):
                energy_report(None, '2026-08-01', '2026-09-01', metric, 305, unit, sign)
