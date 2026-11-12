"""Command line interface: ``openconnect serve|sync|activities|export|import|report``."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Mapping, Sequence
from datetime import date
from pathlib import Path

from openconnect import __version__
from openconnect.ai.provider import ReportError
from openconnect.config import ConfigError, Settings
from openconnect.formats import FormatError, export_activity, import_activity
from openconnect.garmin.client import GarminAPIError
from openconnect.garmin.oauth import GarminAuthError
from openconnect.web.context import AppContext, build_context


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="openconnect", description="Self-hosted alternative to Garmin Connect."
    )
    parser.add_argument("--version", action="version", version=f"openconnect {__version__}")
    sub = parser.add_subparsers(dest="command", required=True)

    serve = sub.add_parser("serve", help="Run the web dashboard and API")
    serve.add_argument("--host")
    serve.add_argument("--port", type=int)

    sync = sub.add_parser("sync", help="Pull data from Garmin (or the mock source)")
    sync.add_argument("--days", type=int, help="Look back this many days instead of resuming")

    listing = sub.add_parser("activities", help="List stored activities")
    listing.add_argument("--limit", type=int, default=20)

    export = sub.add_parser("export", help="Export an activity as FIT, GPX or TCX")
    export.add_argument("activity_id")
    export.add_argument("--format", choices=["fit", "gpx", "tcx"], default="fit")
    export.add_argument("-o", "--output", type=Path, help="Output file (default: generated name)")

    imp = sub.add_parser("import", help="Import FIT, GPX or TCX files")
    imp.add_argument("files", nargs="+", type=Path)

    report = sub.add_parser("report", help="Generate an AI (or template) report")
    kind = report.add_subparsers(dest="kind", required=True)
    activity = kind.add_parser("activity")
    activity.add_argument("activity_id")
    weekly = kind.add_parser("weekly")
    weekly.add_argument("--week", type=date.fromisoformat, help="Any date in the week (YYYY-MM-DD)")
    for p in (activity, weekly):
        p.add_argument("--force", action="store_true", help="Regenerate even if cached")
    return parser


def _fail(message: str, code: int = 1) -> int:
    print(f"error: {message}", file=sys.stderr)
    return code


def cmd_serve(args: argparse.Namespace, settings: Settings) -> int:
    import uvicorn

    from openconnect.web.app import create_app

    uvicorn.run(
        create_app(settings),
        host=args.host or settings.host,
        port=args.port or settings.port,
        log_level="info",
    )
    return 0


def cmd_sync(args: argparse.Namespace, ctx: AppContext) -> int:
    try:
        result = ctx.sync.sync(days_back=args.days)
    except (GarminAPIError, GarminAuthError) as exc:
        return _fail(str(exc))
    ing = result.ingest
    print(f"Synced {ing.activities} activities, {ing.dailies} days of health data, "
          f"{ing.sleeps} nights of sleep.")  # fmt: skip
    for error in ing.errors:
        print(f"warning: {error}", file=sys.stderr)
    return 0


def cmd_activities(args: argparse.Namespace, ctx: AppContext) -> int:
    for a in ctx.repo.list_activities(limit=args.limit):
        print(f"{a.id}  {a.start_time:%Y-%m-%d %H:%M}  {a.sport.value:<9} "
              f"{a.distance_m / 1000:7.2f} km  {a.name}")  # fmt: skip
    return 0


def cmd_export(args: argparse.Namespace, ctx: AppContext) -> int:
    activity = ctx.repo.get_activity(args.activity_id)
    if activity is None:
        return _fail(f"activity {args.activity_id!r} not found")
    try:
        data, _content_type, filename = export_activity(activity, args.format)
    except FormatError as exc:
        return _fail(str(exc))
    target = args.output or Path(filename)
    target.write_bytes(data)
    print(f"Wrote {target} ({len(data):,} bytes)")
    return 0


def cmd_import(args: argparse.Namespace, ctx: AppContext) -> int:
    status = 0
    for path in args.files:
        try:
            activity = import_activity(path.read_bytes(), path.name)
        except (FormatError, OSError) as exc:
            print(f"error: {path}: {exc}", file=sys.stderr)
            status = 1
            continue
        existed = ctx.repo.get_activity(activity.id, include_track=False) is not None
        ctx.repo.upsert_activity(activity)
        print(f"{'Updated' if existed else 'Imported'} {activity.id} ({path.name})")
    return status


def cmd_report(args: argparse.Namespace, ctx: AppContext) -> int:
    try:
        if args.kind == "activity":
            report = ctx.reports.activity_report(args.activity_id, force=args.force)
        else:
            report = ctx.reports.weekly_report(args.week or ctx.clock().date(), force=args.force)
    except LookupError:
        return _fail(f"activity {args.activity_id!r} not found")
    except ReportError as exc:
        return _fail(str(exc))
    print(report.text)
    return 0


def main(argv: Sequence[str] | None = None, env: Mapping[str, str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        settings = Settings.from_env(env)
        if args.command == "serve":
            return cmd_serve(args, settings)
        ctx = build_context(settings)
    except ConfigError as exc:
        return _fail(str(exc), code=2)

    handlers = {
        "sync": cmd_sync,
        "activities": cmd_activities,
        "export": cmd_export,
        "import": cmd_import,
        "report": cmd_report,
    }
    try:
        return handlers[args.command](args, ctx)
    finally:
        ctx.repo.db.close()


if __name__ == "__main__":
    raise SystemExit(main())
