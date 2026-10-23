import unittest
from datetime import datetime
from zoneinfo import ZoneInfo

from grid_import_publisher import TOPICS, run

NOW = datetime(2026, 10, 11, 12, 0, tzinfo=ZoneInfo('Europe/Rome'))
TODAY_START = int(datetime(2026, 10, 11, tzinfo=ZoneInfo('Europe/Rome')).timestamp())


class Client:
    """Feed 305 (Tesla site power, positive = import): 500 W today, 250 W yesterday."""
    def __init__(self, today_until_s=None, down=False):
        self.today_until_s, self.down = today_until_s, down

    def history(self, feed, start, end, interval):
        if self.down:
            raise RuntimeError('EmonCMS request failed; verify access and endpoint')
        assert feed == 305
        stop = end if self.today_until_s is None or start < TODAY_START else min(end, TODAY_START + self.today_until_s)
        power = 500 if start >= TODAY_START else 250
        return [[t * 1000, power] for t in range(start, stop + 1, interval)]


class GridImportPublisherTests(unittest.TestCase):
    def publish_log(self, client):
        sent = []
        result = run(client, now=NOW, publish=lambda topic, payload: sent.append((topic, payload)))
        return result, dict(sent)

    def test_today_until_now_and_full_yesterday_retained(self):
        result, sent = self.publish_log(Client())
        self.assertEqual(result, {'today': 6.0, 'yesterday': 6.0})       # 0.5 kW * 12 h, 0.25 kW * 24 h
        self.assertEqual(sent, {TOPICS['today']: '6.00', TOPICS['yesterday']: '6.00'})
        self.assertEqual(TOPICS['today'], 'zara/interface/energy/grid/import_kwh_oggi/stat')

    def test_low_coverage_clears_instead_of_publishing_zero(self):
        _, sent = self.publish_log(Client(today_until_s=3 * 3600))      # samples stop at 03:00
        self.assertIsNone(sent[TOPICS['today']])
        self.assertEqual(sent[TOPICS['yesterday']], '6.00')

    def test_emoncms_down_clears_both(self):
        _, sent = self.publish_log(Client(down=True))
        self.assertEqual(sent, {TOPICS['today']: None, TOPICS['yesterday']: None})


if __name__ == '__main__':
    unittest.main()
