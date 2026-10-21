"""Provider-neutral JSON planner adapter for natural-language tool selection."""
import json
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener

from emoncms import NoRedirect

DEFAULT_RETRY_S = 30
MAX_RETRY_S = 60


class PlannerUnavailable(RuntimeError):
    """The model or its gateway failed, or returned an unusable plan."""


class RateLimited(PlannerUnavailable):
    """The provider quota is exhausted for now (Groq free tier: ~2 questions per minute)."""
    def __init__(self, retry_after_s=DEFAULT_RETRY_S):
        super().__init__("Planner rate limited")
        self.retry_after_s = retry_after_s


class TooLarge(PlannerUnavailable):
    """The request exceeds the provider's per-request token limit (Groq free tier: 8000 per minute)."""
    def __init__(self):
        super().__init__("Planner request too large")


def close_quietly(error):
    """HTTPError.close() fails on Python 3.9 when the error has no body."""
    if getattr(error, "fp", None) is not None:
        error.close()


def retry_seconds(value):
    """Bounded wait from a Retry-After header or JSON value; default when missing or odd."""
    try:
        seconds = float(value)
    except (TypeError, ValueError):
        return DEFAULT_RETRY_S
    if seconds != seconds:  # NaN
        return DEFAULT_RETRY_S
    return int(min(max(seconds, 1), MAX_RETRY_S) + 0.999)


class HttpJsonPlanner:
    """Calls a configured planner gateway; credentials stay on the backend."""
    def __init__(self, url, token):
        parts = urlsplit(url)
        if (parts.scheme not in ("http", "https") or not parts.hostname
                or parts.username or parts.password or parts.query or parts.fragment):
            raise ValueError("Invalid planner URL")
        if not token:
            raise ValueError("Planner token is required")
        self.url, self.token = url, token

    def plan(self, question, tool_catalog, today):
        payload = json.dumps({
            "schema": "house_ai.planner_request.v1",
            "instruction": (
                "Interpret the Italian user request using only the supplied tools. "
                "Return an envelope {plan: {operations: [...]}} or {plan: {clarification: text}}. "
                "Each operation needs id, tool and exactly the listed tool parameters. Never invent metrics, "
                "sources or values. Resolve dates using today and Europe/Rome."
            ),
            "question": question,
            "today": today.isoformat(),
            "timezone": "Europe/Rome",
            "tool_catalog": tool_catalog
        }, ensure_ascii=False).encode()
        request = Request(self.url, data=payload, method="POST", headers={
            "Authorization": "Bearer " + self.token,
            "Content-Type": "application/json",
            "Accept": "application/json"
        })
        try:
            with build_opener(NoRedirect).open(request, timeout=30) as response:
                raw = response.read(256 * 1024 + 1)
            if len(raw) > 256 * 1024:
                raise ValueError("Planner response exceeds limit")
            envelope = json.loads(raw)
            plan = envelope.get("plan") if isinstance(envelope, dict) else None
            if not isinstance(plan, dict):
                raise ValueError("Planner response has no plan")
            return plan
        except HTTPError as error:
            try:
                if error.code == 429:
                    try:
                        retry = json.loads(error.read(4096)).get("retry_after_s")
                    except (ValueError, AttributeError, OSError):
                        retry = None
                    raise RateLimited(retry_seconds(retry)) from None
                if error.code == 502:
                    try:
                        code = json.loads(error.read(4096)).get("error")
                    except (ValueError, AttributeError, OSError):
                        code = None
                    if code == "request_too_large":
                        raise TooLarge() from None
            finally:
                close_quietly(error)
            raise PlannerUnavailable("Planner unavailable") from None
        except ValueError:
            raise
        except Exception:
            raise PlannerUnavailable("Planner unavailable") from None

    def health(self):
        request = Request(self.url.rsplit('/', 1)[0] + '/health', headers={
            "Authorization": "Bearer " + self.token, "Accept": "application/json"})
        try:
            with build_opener(NoRedirect).open(request, timeout=5) as response:
                raw = response.read(16 * 1024 + 1)
            if len(raw) > 16 * 1024:
                raise ValueError("Planner health response exceeds limit")
            result = json.loads(raw)
            if (not isinstance(result, dict)
                    or result.get('schema') != 'house_ai.gateway_health.v1'
                    or result.get('status') != 'online'
                    or result.get('provider_status') not in ('online', 'offline', 'unknown')):
                raise ValueError("Invalid planner health response")
            return result
        except Exception:
            raise RuntimeError("Planner health unavailable") from None
