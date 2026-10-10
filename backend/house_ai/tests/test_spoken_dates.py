import unittest
from datetime import datetime
from zoneinfo import ZoneInfo

from energy_presenter import day_text, moment_text, period_text


class SpokenDateTests(unittest.TestCase):
    """Answers are read aloud: gg/mm and hh:mm, never ISO timestamps."""
    year = datetime.now(ZoneInfo('Europe/Rome')).year

    def test_day_drops_current_year_only(self):
        self.assertEqual(day_text(f'{self.year}-09-06'), '06/09')
        self.assertEqual(day_text(f'{self.year - 1}-12-31'), f'31/12/{self.year - 1}')

    def test_moment_is_rome_local_without_seconds(self):
        ms = int(datetime(self.year, 10, 10, 23, 48, 39, tzinfo=ZoneInfo('Europe/Rome')).timestamp() * 1000)
        self.assertEqual(moment_text(ms), '10/10 alle 23:48')

    def test_period_is_spoken_inclusive(self):
        self.assertEqual(period_text(f'{self.year}-08-01', f'{self.year}-09-01'), 'dal 01/08 al 31/08')
        self.assertEqual(period_text(f'{self.year}-09-11', f'{self.year}-09-12'), 'il 11/09')


if __name__ == '__main__':
    unittest.main()
