from __future__ import annotations

from collections.abc import Callable
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import httpx
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.middleware.trustedhost import TrustedHostMiddleware

from openconnect import __version__
from openconnect.ai.provider import ReportProvider
from openconnect.config import Settings
from openconnect.garmin.client import GarminSource
from openconnect.services.sync import utcnow
from openconnect.web.auth import SESSION_COOKIE, valid_session
from openconnect.web.context import build_context

WEB_DIR = Path(__file__).parent
SAFE_METHODS = {"GET", "HEAD", "OPTIONS"}
CSP = (
    "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
    "base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
)


def create_app(
    settings: Settings | None = None,
    *,
    clock: Callable[[], datetime] = utcnow,
    source: GarminSource | None = None,
    provider: ReportProvider | None = None,
    oauth_http: httpx.Client | None = None,
    anthropic_client: Any | None = None,
) -> FastAPI:
    from openconnect.web import routes_api, routes_ui

    settings = settings or Settings.from_env()
    app = FastAPI(
        title="OpenConnect",
        version=__version__,
        docs_url="/api/docs",
        openapi_url="/api/openapi.json",
        redoc_url=None,
    )
    app.state.ctx = build_context(
        settings,
        clock=clock,
        source=source,
        provider=provider,
        oauth_http=oauth_http,
        anthropic_client=anthropic_client,
    )

    # Middleware wraps in reverse order of adding: what is added first sits innermost. The login
    # check goes first so that the guard below wraps it, which means a 401 or a redirect to the
    # login page carries the security headers as well, and a cross-site request is refused before
    # the login check sees it.
    #
    # When a password is configured, everything except the login page and static files needs a
    # signed cookie. Health checks stay public so a reverse proxy can still poll /healthz.
    if settings.password:

        @app.middleware("http")
        async def require_login(request: Request, call_next):
            path = request.url.path
            if path in {"/healthz", "/login", "/ui/login", "/ui/logout"} or path.startswith(
                "/static/"
            ):
                return await call_next(request)
            cookie = request.cookies.get(SESSION_COOKIE, "")
            if not valid_session(cookie, settings.password or ""):
                if path.startswith("/api"):
                    return JSONResponse({"detail": "Not authenticated"}, status_code=401)
                return RedirectResponse("/login", status_code=303)
            return await call_next(request)

    @app.middleware("http")
    async def guard_and_harden(request: Request, call_next):
        # Browsers send Origin (and Sec-Fetch-Site) on cross-site POSTs; scripts, curl and Garmin's
        # push do not. A foreign page must not be able to trigger syncs or (billed) AI reports.
        origin = request.headers.get("origin")
        cross_origin = (
            origin is not None and urlparse(origin).netloc != request.headers.get("host", "")
        ) or request.headers.get("sec-fetch-site") in {"cross-site", "same-site"}
        if request.method not in SAFE_METHODS and cross_origin:
            response = JSONResponse({"detail": "Cross-origin request refused"}, status_code=403)
        else:
            response = await call_next(request)
        response.headers["Content-Security-Policy"] = CSP
        response.headers["X-Content-Type-Options"] = "nosniff"
        # same-origin (not no-referrer): with no-referrer browsers send "Origin: null" on form posts
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        return response

    # Added last so it runs first: reject unknown Host headers before anything else (DNS rebinding).
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=settings.allowed_hosts)

    @app.get("/healthz", include_in_schema=False)
    def healthz():
        return {"status": "ok", "version": __version__}

    @app.exception_handler(StarletteHTTPException)
    async def http_error(request: Request, exc: StarletteHTTPException):
        if request.url.path.startswith("/api") or request.url.path == "/healthz":
            return JSONResponse(
                {"detail": exc.detail}, status_code=exc.status_code, headers=exc.headers
            )
        return routes_ui.error_page(request, exc.status_code, str(exc.detail))

    app.mount("/static", StaticFiles(directory=WEB_DIR / "static"), name="static")
    app.include_router(routes_api.router)
    app.include_router(routes_ui.router)
    return app
