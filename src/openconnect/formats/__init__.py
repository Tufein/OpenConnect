"""Activity file formats: FIT, GPX and TCX."""

from __future__ import annotations

from openconnect.domain import Activity
from openconnect.formats import fit, gpx, tcx
from openconnect.formats.common import FormatError

_EXPORTERS = {
    "fit": (fit.export_fit, "application/vnd.ant.fit"),
    "gpx": (gpx.export_gpx, "application/gpx+xml"),
    "tcx": (tcx.export_tcx, "application/vnd.garmin.tcx+xml"),
}
_PARSERS = {"fit": fit.parse_fit, "gpx": gpx.parse_gpx, "tcx": tcx.parse_tcx}

SUPPORTED_FORMATS = tuple(_EXPORTERS)


def export_activity(activity: Activity, fmt: str) -> tuple[bytes, str, str]:
    """Return ``(bytes, content_type, filename)`` for an activity in the requested format."""
    fmt = fmt.lower().lstrip(".")
    if fmt not in _EXPORTERS:
        raise FormatError(f"Unsupported export format {fmt!r}; use one of {SUPPORTED_FORMATS}")
    exporter, content_type = _EXPORTERS[fmt]
    stamp = activity.start_time.strftime("%Y%m%d-%H%M%S")
    return exporter(activity), content_type, f"{activity.sport.value}-{stamp}.{fmt}"


def detect_format(data: bytes, filename: str = "") -> str:
    """Detect by content first (reliable), falling back to the file extension."""
    if len(data) >= 12 and data[8:12] == b".FIT":
        return "fit"
    head = data[:2048].lstrip().lower()
    if b"<gpx" in head:
        return "gpx"
    if b"<trainingcenterdatabase" in head:
        return "tcx"
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext in _PARSERS:
        return ext
    raise FormatError("Could not detect the file format (expected FIT, GPX or TCX)")


def import_activity(data: bytes, filename: str = "") -> Activity:
    return _PARSERS[detect_format(data, filename)](data)


__all__ = [
    "SUPPORTED_FORMATS",
    "FormatError",
    "detect_format",
    "export_activity",
    "import_activity",
]
