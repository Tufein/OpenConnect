from __future__ import annotations

from datetime import date, timedelta
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

from openconnect import __version__
from openconnect.ai.provider import ReportError
from openconnect.domain import Sport
from openconnect.services.achievements import compute_achievements
from openconnect.services.dashboard import week_start
from openconnect.services.records import personal_records
from openconnect.web import charts, formatting
from openconnect.web.context import AppContext
from openconnect.web.markup import render_markdown
from openconnect.web.routes_api import get_ctx, run_sync, store_upload

PAGE_SIZE = 25
templates = Jinja2Templates(directory=Path(__file__).parent / "templates")
templates.env.filters.update(
    km=formatting.km,
    duration=formatting.duration,
    pace=formatting.pace,
    hours=formatting.hours,
    markdown=render_markdown,
)
templates.env.globals.update(
    version=__version__,
    route_svg=charts.route_svg,
    sparkline=charts.sparkline,
    bar_chart=charts.bar_chart,
)

router = APIRouter(include_in_schema=False)
Ctx = Depends(get_ctx)


def render(request: Request, name: str, status_code: int = 200, **context) -> HTMLResponse:
    return templates.TemplateResponse(request, name, context, status_code=status_code)


def error_page(request: Request, status_code: int, message: str) -> HTMLResponse:
    return render(request, "error.html", status_code=status_code, message=message, code=status_code)


@router.get("/", response_class=HTMLResponse)
def dashboard(request: Request, ctx: AppContext = Ctx):
    overview = ctx.dashboard.overview()
    trend = overview["trend"]
    last_week = trend[-7:]
    return render(
        request,
        "dashboard.html",
        ov=overview,
        steps=[r["steps"] for r in trend],
        resting=[r["resting_hr"] for r in trend],
        sleep=[r["sleep_hours"] for r in trend],
        battery=[r["body_battery_high"] for r in trend],
        stress=[r["avg_stress"] for r in trend],
        week_bars=[(r["date"].strftime("%a"), r["steps"]) for r in last_week],
        last_sleep=next((r for r in reversed(trend) if r["sleep_hours"]), None),
        has_data=bool(overview["today"] or overview["recent_activities"]),
    )


@router.get("/activities", response_class=HTMLResponse)
def activities(
    request: Request,
    sport: str | None = None,
    page: int = Query(1, ge=1),
    ctx: AppContext = Ctx,
):
    selected = Sport(sport) if sport in {s.value for s in Sport} else None
    total = ctx.repo.count_activities(sport=selected)
    items = ctx.repo.list_activities(sport=selected, limit=PAGE_SIZE, offset=(page - 1) * PAGE_SIZE)
    return render(
        request,
        "activities.html",
        activities=items,
        sports=[s.value for s in Sport],
        selected=selected.value if selected else "",
        page=page,
        pages=max(1, -(-total // PAGE_SIZE)),
        total=total,
    )


def activity_page(request: Request, ctx: AppContext, activity_id: str, error: str | None = None):
    activity = ctx.repo.get_activity(activity_id)
    if activity is None:
        raise HTTPException(404, "Activity not found")
    return render(
        request,
        "activity.html",
        status_code=502 if error else 200,
        a=activity,
        report=ctx.repo.get_report("activity", activity_id),
        hr=[p.hr for p in activity.track[:: max(1, len(activity.track) // 120)]],
        elevation=[p.ele for p in activity.track[:: max(1, len(activity.track) // 120)]],
        error=error,
    )


@router.get("/activities/{activity_id}", response_class=HTMLResponse)
def activity_detail(request: Request, activity_id: str, ctx: AppContext = Ctx):
    return activity_page(request, ctx, activity_id)


@router.get("/health", response_class=HTMLResponse)
def health(request: Request, ctx: AppContext = Ctx):
    today = ctx.clock().date()
    trend = ctx.dashboard.trend_series(today - timedelta(days=29), today)
    sleeps = ctx.repo.list_sleep(today - timedelta(days=13), today)
    return render(
        request,
        "health.html",
        trend=trend,
        steps=[r["steps"] for r in trend],
        resting=[r["resting_hr"] for r in trend],
        stress=[r["avg_stress"] for r in trend],
        battery=[r["body_battery_high"] for r in trend],
        sleep_hours=[r["sleep_hours"] for r in trend],
        sleep_bars=[(s.date.strftime("%d"), s.hours) for s in sleeps],
        sleeps=list(reversed(sleeps)),
    )


@router.get("/weekly", response_class=HTMLResponse)
def weekly(request: Request, week: date | None = None, ctx: AppContext = Ctx):
    monday = week_start(week or ctx.clock().date())
    return render(
        request,
        "weekly.html",
        w=ctx.dashboard.weekly_summary(monday),
        monday=monday,
        previous=monday - timedelta(days=7),
        following=monday + timedelta(days=7),
        report=ctx.repo.get_report("weekly", monday.isoformat()),
        activities=ctx.repo.list_activities(
            start=_midnight(monday), end=_midnight(monday + timedelta(days=7)), limit=100
        ),
        error=None,
    )


def _midnight(day: date):
    from datetime import UTC, datetime, time

    return datetime.combine(day, time.min, UTC)


@router.get("/records", response_class=HTMLResponse)
def records(request: Request, ctx: AppContext = Ctx):
    return render(
        request,
        "records.html",
        records=personal_records(ctx.repo),
        achievements=compute_achievements(ctx.repo),
        devices=ctx.repo.list_devices(),
    )


@router.get("/import", response_class=HTMLResponse)
def import_page(request: Request):
    return render(request, "import.html")


@router.get("/settings", response_class=HTMLResponse)
def settings_page(request: Request, connected: int = 0, ctx: AppContext = Ctx):
    return render(
        request,
        "settings.html",
        s=ctx.settings,
        mode=ctx.mode,
        connected=ctx.garmin_connected,
        just_connected=bool(connected),
        provider=ctx.provider,
        last_sync=ctx.repo.get_state("last_sync"),
    )


# ---------------------------------------------------------- form actions (POST/redirect/GET)


@router.post("/ui/sync")
def ui_sync(days_back: Annotated[int | None, Form()] = None, ctx: AppContext = Ctx):
    run_sync(ctx, days_back)
    return RedirectResponse("/", status_code=303)


@router.post("/ui/import")
def ui_import(file: UploadFile, ctx: AppContext = Ctx):
    activity, _created = store_upload(ctx, file)
    return RedirectResponse(f"/activities/{activity.id}", status_code=303)


@router.post("/ui/reports/activity/{activity_id}")
def ui_activity_report(
    request: Request,
    activity_id: str,
    force: Annotated[bool, Form()] = False,
    ctx: AppContext = Ctx,
):
    try:
        ctx.reports.activity_report(activity_id, force)
    except LookupError as exc:
        raise HTTPException(404, "Activity not found") from exc
    except ReportError as exc:
        return activity_page(request, ctx, activity_id, error=str(exc))
    return RedirectResponse(f"/activities/{activity_id}", status_code=303)


@router.post("/ui/reports/weekly")
def ui_weekly_report(
    request: Request,
    week: Annotated[date, Form()],
    force: Annotated[bool, Form()] = False,
    ctx: AppContext = Ctx,
):
    monday = week_start(week)
    try:
        ctx.reports.weekly_report(monday, force)
    except ReportError as exc:
        return error_page(request, 502, str(exc))
    return RedirectResponse(f"/weekly?week={monday.isoformat()}", status_code=303)


__all__ = ["router", "error_page"]
