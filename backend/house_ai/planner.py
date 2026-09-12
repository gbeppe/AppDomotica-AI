"""Provider-neutral JSON planner adapter for natural-language tool selection."""
import json
from urllib.parse import urlsplit
from urllib.request import Request, build_opener

from emoncms import NoRedirect


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
        except ValueError:
            raise
        except Exception:
            raise RuntimeError("Planner unavailable") from None
