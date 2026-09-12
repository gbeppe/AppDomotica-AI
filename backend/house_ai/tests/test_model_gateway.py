import io
import json
import threading
import unittest
from contextlib import contextmanager
from datetime import date
from http.server import HTTPServer
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from energy_tools import catalog
from model_gateway import GroqPlanner, gateway_handler, checked_request, plan_schema, MAX_RESPONSE
from planner import HttpJsonPlanner
from server import handler


def envelope(question='Quanto produce il FV?'):
    return {'schema': 'house_ai.planner_request.v1', 'instruction': 'ignored caller instruction',
            'question': question, 'today': '2026-09-12', 'timezone': 'Europe/Rome',
            'tool_catalog': catalog()}


def completion(plan, finish='stop'):
    return json.dumps({'choices': [{'finish_reason': finish,
                                   'message': {'content': json.dumps(plan)}}]}).encode()


@contextmanager
def serving(handler_class):
    server = HTTPServer(('127.0.0.1', 0), handler_class)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield 'http://127.0.0.1:' + str(server.server_port)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class GatewayTests(unittest.TestCase):
    def test_provider_protocol_and_credential_separation(self):
        plan = {'operations': [], 'clarification': 'Quale periodo?'}
        with patch('model_gateway.build_opener') as opener:
            opener.return_value.open.return_value = io.BytesIO(completion(plan))
            self.assertEqual(GroqPlanner('provider-secret').plan(envelope()), {'plan': plan})
            request = opener.return_value.open.call_args.args[0]
            self.assertEqual(request.full_url, 'https://api.groq.com/openai/v1/chat/completions')
            self.assertEqual(request.get_header('Authorization'), 'Bearer provider-secret')
            self.assertEqual(request.get_header('User-agent'), 'DomoPi-HouseAI/1.0')
            payload = json.loads(request.data)
            self.assertTrue(payload['response_format']['json_schema']['strict'])
            self.assertEqual(payload['model'], 'openai/gpt-oss-20b')
            self.assertNotIn('ignored caller instruction', payload['messages'][0]['content'])
            context = json.loads(payload['messages'][1]['content'])
            self.assertEqual(set(context), {'question', 'today', 'timezone', 'tool_catalog'})
            self.assertNotIn('provider-secret', request.data.decode())

    def test_invalid_model_outputs_fail_closed(self):
        invalid = [b'not json', b'{}', b'x' * (MAX_RESPONSE + 1),
                   completion({'operations': [], 'clarification': 'Why?'}, 'length'),
                   completion({'operations': [{'id': 'a', 'tool': 'shell'}], 'clarification': None}),
                   completion({'operations': [], 'clarification': None}),
                   completion({'operations': [{'id': 'a', 'tool': 'energy_comparison',
                                               'left_id': 'missing', 'right_id': 'x'}], 'clarification': None})]
        for raw in invalid:
            with self.subTest(size=len(raw)), patch('model_gateway.build_opener') as opener:
                opener.return_value.open.return_value = io.BytesIO(raw)
                with self.assertRaisesRegex(RuntimeError, '^Model unavailable or invalid plan$'):
                    GroqPlanner('secret').plan(envelope())

    def test_provider_errors_are_sanitized_without_retry(self):
        with patch('model_gateway.build_opener') as opener:
            opener.return_value.open.side_effect = HTTPError('https://provider', 429, 'secret body', {}, None)
            with self.assertRaisesRegex(RuntimeError, '^Model unavailable or invalid plan$'):
                GroqPlanner('secret').plan(envelope())
            self.assertEqual(opener.return_value.open.call_count, 1)

    def test_request_catalog_and_dates_are_owned_by_gateway(self):
        for change in [{'today': '2026-99-12'}, {'tool_catalog': {}}, {'question': ''},
                       {'question': 'x' * 1001}, {'timezone': 'UTC'}, {'current_energy': {}}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                checked_request({**envelope(), **change})
        variants = plan_schema()['properties']['operations']['items']['anyOf']
        self.assertEqual(len(variants), 4)
        for variant in variants:
            self.assertFalse(variant['additionalProperties'])
            self.assertEqual(set(variant['properties']), set(variant['required']))

    def test_http_authentication_and_malformed_request_never_call_provider(self):
        from unittest.mock import Mock
        provider = Mock()
        with serving(gateway_handler(provider, 'gateway-token')) as url:
            for token, body, expected in [('wrong', envelope(), 401),
                                          ('gateway-token', {}, 400)]:
                request = Request(url + '/v1/plan', data=json.dumps(body).encode(),
                                  headers={'Authorization': 'Bearer ' + token})
                with self.assertRaises(HTTPError) as result:
                    urlopen(request)
                self.assertEqual(result.exception.code, expected)
                result.exception.close()
        provider.plan.assert_not_called()

    def test_invalid_provider_plan_reaches_backend_as_502_without_source_reads(self):
        from unittest.mock import Mock
        client = Mock()
        with patch('model_gateway.build_opener') as opener:
            opener.return_value.open.return_value = io.BytesIO(completion({
                'operations': [{'id': 'x', 'tool': 'shell'}], 'clarification': None}))
            with serving(gateway_handler(GroqPlanner('secret'), 'gateway-secret')) as gateway:
                planner = HttpJsonPlanner(gateway + '/v1/plan', 'gateway-secret')
                with serving(handler(client, 'backend-secret', None, planner)) as backend:
                    request = Request(backend + '/v1/assistant/query',
                                      data=json.dumps({'question': 'Accendi tutto'}).encode(),
                                      headers={'Authorization': 'Bearer backend-secret'})
                    with self.assertRaises(HTTPError) as result:
                        urlopen(request)
                    self.assertEqual(result.exception.code, 502)
                    self.assertNotIn('secret', result.exception.read().decode())
                    result.exception.close()
        client.history.assert_not_called()

    def test_complete_http_chain_with_current_historical_and_log_tools(self):
        from pathlib import Path
        from tempfile import TemporaryDirectory
        plan = {'clarification': None, 'operations': [
            {'id': 'now', 'tool': 'current_energy_metric', 'metric': 'solar_power_w'},
            {'id': 'past', 'tool': 'energy_metric', 'metric': 'grid_import_kwh',
             'start': '2026-09-06', 'end': '2026-09-07'},
            {'id': 'forecast', 'tool': 'backend_log_day', 'source': 'solar_forecast',
             'day': '2026-09-12'}]}
        class Client:
            calls = 0
            def history(self, feed, start, end, interval):
                self.calls += 1
                return [[t * 1000, 1000] for t in range(start, end, interval)]
        client = Client()
        with TemporaryDirectory() as root, patch('model_gateway.build_opener') as opener:
            Path(root, 'zara_previsione_kwh_openmeteo.log').write_text(
                json.dumps({'data_previsione': '2026-09-12', 'kwh_stimati_impianto_reali': 18}) + '\n')
            opener.return_value.open.side_effect = lambda *a, **k: io.BytesIO(completion(plan))
            with serving(gateway_handler(GroqPlanner('provider-secret'), 'gateway-secret')) as gateway:
                planner = HttpJsonPlanner(gateway + '/v1/plan', 'gateway-secret')
                with serving(handler(client, 'backend-secret', None, planner, root)) as backend:
                    body = {'question': 'FV attuale, prelievo 6 settembre e previsione 12 settembre',
                            'current_energy': {'schema': 'house_ai.current_energy_input.v1',
                                'connected': True, 'observations': {'solar_power_w': {
                                    'value': 1234, 'received_at_ms': 1789200000000, 'retained': True,
                                    'source_topic': 'zara/interface/energy/solar/power/stat'}}}}
                    req = Request(backend + '/v1/assistant/query', data=json.dumps(body).encode(),
                                  headers={'Authorization': 'Bearer backend-secret'})
                    with urlopen(req) as response:
                        answer = json.load(response)
                    self.assertEqual(len(answer['results']), 3)
                    self.assertIn(answer['status'], ('complete', 'partial'))
                    self.assertGreater(client.calls, 0)
                    outgoing = opener.return_value.open.call_args.args[0].data.decode()
                    for private in ['backend-secret', 'gateway-secret', 'received_at_ms', '1234']:
                        self.assertNotIn(private, outgoing)
                    self.assertIn('1234,0 W', answer['answer'])
                    self.assertIn('24,00 kWh', answer['answer'])
                    self.assertIn('18,00 kWh', answer['answer'])


if __name__ == '__main__':
    unittest.main()
