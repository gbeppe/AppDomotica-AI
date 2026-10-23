import unittest
from datetime import date
from energy_history import calculate, daily_extreme_report, energy_report, period_bounds, relative_period


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

    def test_future_samples_and_future_period_are_not_measurements(self):
        start, end = period_bounds('2026-09-12', '2026-09-13')
        class Future:
            def history(self, *args):
                return [[start, 1000], [start+30000, 1000], [end, 1000]]
        r = energy_report(Future(), '2026-09-12', '2026-09-13', 'grid_import_kwh', 305, 'W', 1,
                          now_ms=start+30000)
        self.assertEqual(r['covered_ms'], 30000)
        self.assertEqual(r['status'], 'partial')
        r = energy_report(None, '2026-09-12', '2026-09-13', 'grid_import_kwh', 305, 'W', 1,
                          now_ms=start-1)
        self.assertIsNone(r['value'])
        self.assertEqual(r['source']['requests'], 0)

    def test_validate_before_network(self):
        for metric, unit, sign in [('grid_import_kwh', 'W', None), ('grid_import_kwh', '%', 1), ('soc_mean_percent', 'W', None)]:
            with self.assertRaises(ValueError):
                energy_report(None, '2026-08-01', '2026-09-01', metric, 305, unit, sign)

    def test_other_directional_energy_metrics(self):
        for metric, sign in [('grid_export_kwh', -1), ('home_consumption_kwh', 1),
                             ('solar_production_kwh', 1), ('battery_charge_kwh', -1),
                             ('battery_discharge_kwh', 1)]:
            value = -1000 if sign == -1 else 1000
            result = self.calc([(0, value), (30, value)], metric, sign, end=30)
            self.assertAlmostEqual(result['value'], 1/120)

    def test_daily_extreme_uses_complete_days_and_earliest_tie(self):
        starts = {day: period_bounds(day, end)[0] // 1000 for day, end in
                  [('2026-09-01', '2026-09-02'), ('2026-09-02', '2026-09-03'),
                   ('2026-09-03', '2026-09-04')]}
        class Daily:
            def history(self, _feed, start, end, interval):
                rows = []
                for timestamp in range(start, end + 1, interval):
                    if starts['2026-09-02'] <= timestamp < starts['2026-09-03']:
                        value = 2000
                    else:
                        value = 1000
                    rows.append([timestamp * 1000, value])
                return rows
        result = daily_extreme_report(Daily(), '2026-09-01', '2026-09-04',
                                      'grid_import_kwh', 305, 'W', 1, 30,
                                      'maximum', now_ms=period_bounds('2026-09-04', '2026-09-05')[0])
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['winner']['day'], '2026-09-02')
        self.assertAlmostEqual(result['winner']['value'], 48, places=2)
        self.assertEqual(result['eligible_days'], 3)

    def test_daily_extreme_excludes_incomplete_days(self):
        missing_lo, missing_hi = period_bounds('2026-09-02', '2026-09-03')
        class Missing:
            def history(self, _feed, start, end, interval):
                return [[t * 1000, 1000] for t in range(start, end + 1, interval)
                        if not missing_lo // 1000 < t < missing_hi // 1000]
        result = daily_extreme_report(Missing(), '2026-09-01', '2026-09-04',
                                      'grid_import_kwh', 305, 'W', 1, 30,
                                      'maximum', now_ms=period_bounds('2026-09-04', '2026-09-05')[0])
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(result['eligible_days'], 2)
        self.assertEqual(result['excluded_days'][0]['day'], '2026-09-02')
        self.assertEqual(result['winner']['day'], '2026-09-01')
        self.assertEqual(result['observed_winner']['day'], '2026-09-01')
        self.assertIsNone(result['absolute_winner'])

    def test_partial_observations_remain_available_as_lower_bounds(self):
        day_lo, day_hi = period_bounds('2026-09-02', '2026-09-03')
        class PartialPeak:
            def history(self, _feed, start, end, interval):
                rows = []
                for timestamp in range(start, end + 1, interval):
                    if day_lo // 1000 < timestamp < day_hi // 1000:
                        if timestamp >= day_lo // 1000 + 12 * 3600:
                            continue
                        value = 4000
                    else:
                        value = 1000
                    rows.append([timestamp * 1000, value])
                return rows
        result = daily_extreme_report(PartialPeak(), '2026-09-01', '2026-09-04',
                                      'grid_import_kwh', 305, 'W', 1, 30,
                                      'maximum', now_ms=period_bounds('2026-09-04', '2026-09-05')[0])
        self.assertEqual(result['winner']['day'], '2026-09-01')
        self.assertEqual(result['observed_winner']['day'], '2026-09-02')
        self.assertAlmostEqual(result['observed_winner']['coverage_ratio'], .5, places=2)
        self.assertEqual(result['absolute_winner']['day'], '2026-09-02')


class CoverageSpeechTests(unittest.TestCase):
    """23/10/2026: periods in progress always said "Dati parziali: copertura N per cento"."""

    def report(self, start, end, now_ms, gap_ms=(0, 0)):
        class Client:
            def history(self, feed, lo, hi, interval):
                return [[t * 1000, 500] for t in range(lo, hi + 1, interval)
                        if not gap_ms[0] <= t * 1000 < gap_ms[1]]
        return energy_report(Client(), start, end, 'grid_import_kwh', 305, 'W', 1, 30, now_ms=now_ms)

    def spoken_and_notes(self, result):
        from energy_presenter import describe, notes
        item = {'id': 'op1', 'tool': 'energy_metric', 'metric': 'grid_import_kwh', 'result': result}
        return describe(item), notes(item)

    def test_period_in_progress_with_all_data_so_far_says_nothing(self):
        lo = period_bounds('2026-10-19', '2026-10-24')[0]
        text, written = self.spoken_and_notes(self.report('2026-10-19', '2026-10-24', lo + 4 * 86400_000))
        self.assertNotIn('copertura', text.lower())
        self.assertNotIn('Mancano dati', text)
        self.assertTrue(written[0].startswith('Copertura 100,0 per cento del periodo trascorso, fino al 23/10'), written[0])

    def test_real_gaps_are_spoken_without_numbers(self):
        lo = period_bounds('2026-10-19', '2026-10-24')[0]
        result = self.report('2026-10-19', '2026-10-24', lo + 86400_000, gap_ms=(lo, lo + 2 * 3600_000))
        text, written = self.spoken_and_notes(result)
        self.assertIn('Mancano dati per una parte del periodo.', text)
        self.assertNotIn('per cento', text)
        self.assertIn('Copertura 91,7 per cento del periodo trascorso', written[0])

    def test_past_period_keeps_the_whole_period_coverage(self):
        lo, hi = period_bounds('2026-09-01', '2026-09-02')
        text, written = self.spoken_and_notes(self.report('2026-09-01', '2026-09-02', hi + 3600_000))
        self.assertNotIn('Mancano dati', text)
        self.assertEqual(written[0], 'Copertura 100,0 per cento.')


class CounterTests(unittest.TestCase):
    """23/10/2026: kWh from cumulative counters, as the EmonCMS MySolarBattery app."""
    lo, hi = period_bounds('2026-09-01', '2026-09-02')

    def counter(self, values):
        from energy_history import counter_delta
        return counter_delta([[self.lo + s * 1000, v] for s, v in values], self.lo, self.hi, 'home_consumption_kwh', 300_000)

    def test_a_gap_loses_no_energy(self):
        day = 86400
        result = self.counter([(0, 100.0), (3600, 101.0), (20 * 3600, 118.0), (day, 124.0)])
        self.assertAlmostEqual(result['value'], 24.0)
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(result['coverage_ratio'], 1.0)

    def test_small_drops_are_rounding_and_resets_are_refused(self):
        self.assertAlmostEqual(self.counter([(0, 10.0), (300, 10.3), (600, 10.26), (86400, 30.0)])['value'], 20.0)
        reset = self.counter([(0, 500.0), (300, 501.0), (600, 0.5), (86400, 20.0)])
        self.assertIsNone(reset['value'])
        self.assertEqual(reset['status'], 'insufficient_data')
        self.assertEqual(reset['excluded'][-1]['reason'], 'counter_reset')

    def test_missing_start_is_partial(self):
        result = self.counter([(6 * 3600, 106.0), (86400, 124.0)])
        self.assertAlmostEqual(result['value'], 18.0)
        self.assertEqual(result['status'], 'partial')
        self.assertAlmostEqual(result['coverage_ratio'], 0.75)

    def test_report_and_daily_extreme_use_counters(self):
        class Counter:
            def history(self, feed, start, end, interval):
                # 1 kW, 2 kW on 2 September: the counter in kWh.
                two = period_bounds('2026-09-02', '2026-09-03')
                def kwh(t):
                    ms = t * 1000
                    extra = max(0, min(ms, two[1]) - two[0]) / 3_600_000
                    return t / 3600 + extra
                return [[t * 1000, kwh(t)] for t in range(start, end + 1, interval)]
        report = energy_report(Counter(), '2026-09-01', '2026-09-03', 'grid_import_kwh', 26, 'kWh', None, 300,
                               now_ms=period_bounds('2026-09-04', '2026-09-05')[0])
        self.assertAlmostEqual(report['value'], 72.0)
        self.assertEqual(report['status'], 'complete')
        self.assertIn('contatore cumulativo', report['limitations'][0])
        extreme = daily_extreme_report(Counter(), '2026-09-01', '2026-09-04', 'grid_import_kwh', 26, 'kWh', None,
                                       300, 'maximum', now_ms=period_bounds('2026-09-05', '2026-09-06')[0])
        self.assertEqual(extreme['winner']['day'], '2026-09-02')
        self.assertAlmostEqual(extreme['winner']['value'], 48.0)
        with self.assertRaises(ValueError):
            energy_report(Counter(), '2026-09-01', '2026-09-03', 'soc_mean_percent', 304, 'kWh', None, 300)
