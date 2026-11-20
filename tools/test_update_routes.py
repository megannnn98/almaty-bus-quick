import unittest
from update_routes import index_routes, details


class ScraperTests(unittest.TestCase):
    def test_index_unique_buses_and_trolleybuses(self):
        page = '''<a href="/ru/routes/bus-12/">12</a>
        <a href="/ru/routes/trolleybus-12/">12</a>
        <a href="/ru/routes/bus-12/">repeat</a>
        <a href="https://evil.example/ru/routes/bus-13/">evil</a>
        <a href="/ru/routes/bus-38a/">letter suffix</a>'''
        got = index_routes(page)
        self.assertEqual(2, len(got))
        self.assertEqual('bus', got[0][0])
        self.assertEqual('trolleybus', got[1][0])

    def test_detail_extracts_2gis_route_and_description(self):
        page = '''<h1>Автобус №12: Алматы-2 — Медеу</h1>
        <a href="https://2gis.kz/almaty/route/9430017310195922?m=foo">Карта</a>'''
        got = details(page)
        self.assertEqual('https://2gis.kz/almaty/route/9430017310195922', got[0])
        self.assertEqual('Алматы-2 — Медеу', got[1])

    def test_detail_has_no_magic_fake_route(self):
        self.assertIsNone(details('<h1>Автобус №12</h1>'))


if __name__ == '__main__':
    unittest.main()
