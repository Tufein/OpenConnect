from __future__ import annotations

import hmac
from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response, UploadFile
from fastapi.responses import RedirectResponse
from starlette.concurrency import run_in_threadpool

from openconnect.ai.provider import ReportError
from openconnect.domain import Activity, GeneratedReport, Sport
from openconnect.formats import SUPPORTED_FORMATS, FormatError, export_activity, import_activity
from openconnect.garmin.client import GarminAPIError, GarminRateLimitError
from openconnect.garmin.ingest import ingest_payload
from openconnect.garmin.oauth import GarminAuthError
from openconnect.services.achievements import compute_achievements
from openconnect.services.records import personal_records
from openconnect.web.context import AppContext

MAX_UPLOAD_BYTES = 25 * 1024 * 1024
MAX_RANGE_DAYS = 366

router = APIRouter(prefix="/api")


def get_ctx(request: Request) -> AppContext:
    return request.app.state.ctx


Ctx = Depends(get_ctx)


def activity_out(a: Activity, include_track: bool = False) -> dict:
    data = a.model_dump(mode="json", exclude=None if include_track else {"track"})
    data["pace_s_per_km"] = a.pace_s_per_km
    data["avg_speed_mps"] = a.avg_speed_mps
    return data


def report_out(report: GeneratedReport) -> dict:
    return report.model_dump(mode="json", exclude={"facts_hash"})


def date_range(start: date, end: date) -> tuple[date, date]:
    if end < start:
        raise HTTPException(422, "end must not be before start")
    if (end - start) > timedelta(days=MAX_RANGE_DAYS):
        raise HTTPException(422, f"range must not exceed {MAX_RANGE_DAYS} days")
    return start, end


def require_activity(ctx: AppContext, activity_id: str) -> Activity:
    activity = ctx.repo.get_activity(activity_id)
    if activity is None:
        raise HTTPException(404, "Activity not found")
    return activity


@router.get("/activities")
def list_activities(
    sport: Sport | None = None,
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
    ctx: AppContext = Ctx,
):
    return {
        "total": ctx.repo.count_activities(sport=sport),
        "items": [
            activity_out(a)
            for a in ctx.repo.list_activities(sport=sport, limit=limit, offset=offset)
        ],
    }


@router.get("/activities/{activity_id}")
def get_activity(activity_id: str, ctx: AppContext = Ctx):
    return activity_out(require_activity(ctx, activity_id), include_track=True)


@router.delete("/activities/{activity_id}", status_code=204)
def delete_activity(activity_id: str, ctx: AppContext = Ctx):
    if not ctx.repo.delete_activity(activity_id):
        raise HTTPException(404, "Activity not found")
    return Response(status_code=204)


@router.get("/activities/{activity_id}/export")
def export(activity_id: str, format: str = "fit", ctx: AppContext = Ctx):
    activity = require_activity(ctx, activity_id)
    if format.lower() not in SUPPORTED_FORMATS:
        raise HTTPException(400, f"Unsupported format {format!r}; use one of {SUPPORTED_FORMATS}")
    try:
        data, content_type, filename = export_activity(activity, format)
    except FormatError as exc:
        raise HTTPException(422, str(exc)) from exc
    return Response(
        data,
        media_type=content_type,
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


def store_upload(ctx: AppContext, file: UploadFile) -> tuple[Activity, bool]:
    data = file.file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, f"File exceeds the {MAX_UPLOAD_BYTES // (1024 * 1024)} MB limit")
    try:
        activity = import_activity(data, file.filename or "")
    except FormatError as exc:
        raise HTTPException(400, str(exc)) from exc
    created = ctx.repo.get_activity(activity.id, include_track=False) is None
    ctx.repo.upsert_activity(activity)
    return activity, created


@router.post("/import")
def import_file(file: UploadFile, response: Response, ctx: AppContext = Ctx):
    activity, created = store_upload(ctx, file)
    response.status_code = 201 if created else 200
    return {"created": created, "activity": activity_out(activity)}


@router.get("/health/daily")
def health_daily(start: date, end: date, ctx: AppContext = Ctx):
    start, end = date_range(start, end)
    return [d.model_dump(mode="json") for d in ctx.repo.list_daily(start, end)]


@router.get("/health/sleep")
def health_sleep(start: date, end: date, ctx: AppContext = Ctx):
    start, end = date_range(start, end)
    return [s.model_dump(mode="json") for s in ctx.repo.list_sleep(start, end)]


@router.get("/dashboard/weekly")
def dashboard_weekly(week: date | None = None, ctx: AppContext = Ctx):
    return ctx.dashboard.weekly_summary(week or ctx.clock().date()).model_dump(mode="json")


@router.get("/dashboard/overview")
def dashboard_overview(ctx: AppContext = Ctx):
    ov = ctx.dashboard.overview()
    return {
        "today": ov["today"].model_dump(mode="json") if ov["today"] else None,
        "this_week": ov["this_week"].model_dump(mode="json"),
        "last7": ov["last7"],
        "previous7": ov["previous7"],
        "steps_change_pct": ov["steps_change_pct"],
        "trend": [{**row, "date": row["date"].isoformat()} for row in ov["trend"]],
        "recent_activities": [activity_out(a) for a in ov["recent_activities"]],
    }


@router.get("/records")
def records(ctx: AppContext = Ctx):
    return _jsonable(personal_records(ctx.repo))


@router.get("/achievements")
def achievements(ctx: AppContext = Ctx):
    return _jsonable(compute_achievements(ctx.repo))


@router.get("/devices")
def devices(ctx: AppContext = Ctx):
    return ctx.repo.list_devices()


def _jsonable(value):
    if isinstance(value, dict):
        return {k: _jsonable(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_jsonable(v) for v in value]
    if isinstance(value, date | datetime):
        return value.isoformat()
    return value


# ------------------------------------------------------------------------- sync and Garmin


def run_sync(ctx: AppContext, days_back: int | None = None) -> dict:
    if ctx.oauth is not None and not ctx.oauth.is_connected():
        raise HTTPException(409, "Garmin is not connected yet; connect first via /settings")
    try:
        result = ctx.sync.sync(days_back=days_back)
    except GarminRateLimitError as exc:
        headers = {"Retry-After": str(exc.retry_after)} if exc.retry_after else None
        raise HTTPException(429, str(exc), headers=headers) from exc
    except (GarminAPIError, GarminAuthError) as exc:
        raise HTTPException(502, str(exc)) from exc
    return {
        "start": result.start.isoformat(),
        "end": result.end.isoformat(),
        "activities": result.ingest.activities,
        "dailies": result.ingest.dailies,
        "sleeps": result.ingest.sleeps,
        "errors": result.ingest.errors,
        "skipped": getattr(ctx.source, "skipped", {}),
    }


@router.post("/sync")
def sync(days_back: int | None = Query(None, ge=1, le=365), ctx: AppContext = Ctx):
    return run_sync(ctx, days_back)


@router.get("/garmin/status")
def garmin_status(ctx: AppContext = Ctx):
    return {"mode": ctx.mode, "connected": ctx.garmin_connected}


@router.get("/garmin/connect")
def garmin_connect(ctx: AppContext = Ctx):
    if ctx.oauth is None:
        raise HTTPException(
            409, "Running in mock mode; set OPENCONNECT_GARMIN_MODE=live to connect"
        )
    url, _state = ctx.oauth.start()
    return RedirectResponse(url, status_code=307)


@router.get("/garmin/callback")
def garmin_callback(
    code: str | None = None,
    state: str | None = None,
    error: str | None = None,
    ctx: AppContext = Ctx,
):
    if ctx.oauth is None:
        raise HTTPException(409, "Running in mock mode")
    if error:
        raise HTTPException(400, f"Garmin returned an error: {error}")
    if not code or not state:
        raise HTTPException(400, "Missing code or state")
    try:
        ctx.oauth.complete(code, state)
    except GarminAuthError as exc:
        raise HTTPException(400, str(exc)) from exc
    return RedirectResponse("/settings?connected=1", status_code=303)


@router.post("/garmin/disconnect", status_code=204)
def garmin_disconnect(ctx: AppContext = Ctx):
    if ctx.oauth is not None:
        ctx.oauth.disconnect()
    return Response(status_code=204)


@router.post("/garmin/webhook")
async def garmin_webhook(request: Request, token: str | None = None, ctx: AppContext = Ctx):
    expected = ctx.settings.webhook_token
    if expected and not (token and hmac.compare_digest(token.encode(), expected.encode())):
        raise HTTPException(401, "Invalid webhook token")
    try:
        payload = await request.json()
    except ValueError as exc:
        raise HTTPException(400, "Body is not valid JSON") from exc
    if not isinstance(payload, dict):
        raise HTTPException(400, "Expected a JSON object keyed by data type")

    # Ping/Pull notifications only carry a callbackURL, not data; they are counted, not ingested.
    pings, data = 0, {}
    for kind, items in payload.items():
        if not isinstance(items, list):
            continue
        real = [i for i in items if isinstance(i, dict) and "callbackURL" not in i]
        pings += len(items) - len(real)
        data[kind] = real
    result = await run_in_threadpool(ingest_payload, ctx.repo, data)
    return {
        "ingested": {
            "activities": result.activities,
            "dailies": result.dailies,
            "sleeps": result.sleeps,
        },
        "ping_notifications": pings,
        "errors": result.errors,
    }


# ------------------------------------------------------------------------- reports


def report_errors(fn):
    try:
        return fn()
    except LookupError as exc:
        raise HTTPException(404, str(exc)) from exc
    except ReportError as exc:
        raise HTTPException(502, str(exc)) from exc


@router.post("/reports/activity/{activity_id}")
def create_activity_report(activity_id: str, force: bool = False, ctx: AppContext = Ctx):
    return report_out(report_errors(lambda: ctx.reports.activity_report(activity_id, force)))


@router.get("/reports/activity/{activity_id}")
def get_activity_report(activity_id: str, ctx: AppContext = Ctx):
    report = ctx.repo.get_report("activity", activity_id)
    if report is None:
        raise HTTPException(404, "No report generated yet")
    return report_out(report)


@router.post("/reports/weekly")
def create_weekly_report(week: date | None = None, force: bool = False, ctx: AppContext = Ctx):
    day = week or ctx.clock().date()
    return report_out(report_errors(lambda: ctx.reports.weekly_report(day, force)))


@router.get("/reports/weekly")
def get_weekly_report(week: date | None = None, ctx: AppContext = Ctx):
    from openconnect.services.dashboard import week_start

    monday = week_start(week or ctx.clock().date())
    report = ctx.repo.get_report("weekly", monday.isoformat())
    if report is None:
        raise HTTPException(404, "No report generated yet")
    return report_out(report)
