"""Optional single-password authentication for a self-hosted OpenConnect server.

Off by default: with no ``OPENCONNECT_PASSWORD`` the app behaves exactly as before. When a password
is set, a signed, time-limited cookie is the only session state, so no session table is needed and a
restart does not log anyone out (the cookie is bound to the password, not to a server secret).
"""

from __future__ import annotations

import hashlib
import hmac
import time

SESSION_COOKIE = "oc_session"
SESSION_MAX_AGE_S = 7 * 24 * 60 * 60


def _signature(message: str, password: str) -> str:
    return hmac.new(password.encode(), message.encode(), hashlib.sha256).hexdigest()


def make_session(password: str, now: float | None = None) -> str:
    issued = str(int(time.time() if now is None else now))
    return f"{issued}.{_signature(issued, password)}"


def valid_session(cookie: str, password: str, now: float | None = None) -> bool:
    try:
        issued, signature = cookie.split(".", 1)
        age = (time.time() if now is None else now) - int(issued)
    except (ValueError, TypeError):
        return False
    if not 0 <= age <= SESSION_MAX_AGE_S:
        return False
    return hmac.compare_digest(signature, _signature(issued, password))
