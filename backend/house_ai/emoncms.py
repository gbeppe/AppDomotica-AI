"""Bounded, read-only EmonCMS adapter using credentials from the environment."""
import json
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward the API key to a redirected destination.


class Emoncms:
    def __init__(self, base_url: str, api_key: str):
        parts = urlsplit(base_url)
        if (parts.scheme not in ("http", "https") or not parts.hostname
                or parts.username or parts.password or parts.query or parts.fragment):
            raise ValueError("Invalid EmonCMS base URL")
        if not api_key:
            raise ValueError("EMONCMS_API_KEY is required")
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def _get(self, endpoint, params):
        query = urlencode({**params, "apikey": self.api_key})
        request = Request(f"{self.base_url}/feed/{endpoint}.json?{query}")
        try:
            with build_opener(NoRedirect).open(request, timeout=20) as response:
                raw = response.read(16 * 1024 * 1024 + 1)
            if len(raw) > 16 * 1024 * 1024:
                raise ValueError("Response exceeds size limit")
            result = json.loads(raw)
        except Exception:
            # urllib exceptions can contain the full URL and its credential.
            raise RuntimeError("EmonCMS request failed; verify access and endpoint") from None
        if not isinstance(result, list):
            raise RuntimeError("EmonCMS did not return a list")
        return result

    def catalog(self):
        feeds = self._get("list", {"meta": 1})
        fields = ("id", "name", "tag", "unit", "engine", "start_time",
                  "end_time", "interval", "npoints", "time", "value")
        if not all(isinstance(feed, dict) for feed in feeds):
            raise RuntimeError("Invalid feed catalog")
        return [{key: feed.get(key) for key in fields} for feed in feeds]

    def history(self, feed_id: int, start_seconds: int, end_seconds: int,
                interval_seconds: int = 30):
        if (feed_id <= 0 or interval_seconds <= 0 or start_seconds < 0
                or end_seconds <= start_seconds):
            raise ValueError("Invalid history range")
        if (end_seconds - start_seconds) / interval_seconds > 10000:
            raise ValueError("Split the range: maximum 10000 requested samples")
        # Keep null values intact. Interpretation belongs to the analysis layer.
        return self._get("data", {"id": feed_id, "start": start_seconds,
            "end": end_seconds, "interval": interval_seconds})
