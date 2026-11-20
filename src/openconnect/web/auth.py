"""Optional single-password authentication for a self-hosted OpenConnect server.

Off by default: with no ``OPENCONNECT_PASSWORD`` the app behaves exactly as before. When a password
is set, a signed, time-limited cookie is the only session state, so no session table is needed and a
restart does not log anyone out (the cookie is bound to the password, not to a server secret).

The cookie is signed with a key *derived* from the password (PBKDF2), not with the password itself:
anyone who sees a cookie could otherwise test password guesses against it at the speed of one HMAC
per guess. The derived key is worked out once per process and remembered.
"""

from __future__ import annotations

import hashlib
import hmac
import time
from functools import lru_cache

SESSION_COOKIE = "oc_session"
SESSION_MAX_AGE_S = 7 * 24 * 60 * 60

_KEY_SALT = b"openconnect-session-v2"
_KEY_ROUNDS = 100_000


@lru_cache(maxsize=8)
def _key(password: str) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", password.encode(), _KEY_SALT, _KEY_ROUNDS)


def _signature(message: str, password: str) -> str:
    return hmac.new(_key(password), message.encode(), hashlib.sha256).hexdigest()


def same_text(a: str, b: str) -> bool:
    """Constant-time comparison of two strings.

    ``hmac.compare_digest`` refuses ``str`` arguments with non-ASCII characters (a TypeError, so a
    password with an accent or a euro sign could never log in and a crafted cookie caused a 500),
    so both sides are compared as UTF-8 bytes.
    """
    return hmac.compare_digest(a.encode(), b.encode())


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
    return same_text(signature, _signature(issued, password))
