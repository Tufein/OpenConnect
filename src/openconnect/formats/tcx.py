"""Garmin Training Center XML (TCX) import/export. TCX only knows Running, Biking and Other."""

from __future__ import annotations

from datetime import datetime
from xml.etree import ElementTree as ET

from defusedxml import ElementTree as SafeET
from defusedxml.common import DefusedXmlException

from openconnect.domain import Activity, Sport, TrackPoint, ensure_utc, finalize_activity
from openconnect.formats.common import FormatError, default_name, import_id

TCX_NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"

_SPORT_TO_TCX = {Sport.RUNNING: "Running", Sport.CYCLING: "Biking"}
_TCX_TO_SPORT = {"Running": Sport.RUNNING, "Biking": Sport.CYCLING}


def _iso(dt: datetime) -> str:
    return ensure_utc(dt).strftime("%Y-%m-%dT%H:%M:%SZ")


def _text(parent: ET.Element, tag: str, value: object) -> ET.Element:
    el = ET.SubElement(parent, tag)
    el.text = str(value)
    return el


def export_tcx(activity: Activity) -> bytes:
    root = ET.Element("TrainingCenterDatabase", {"xmlns": TCX_NS})
    activities = ET.SubElement(root, "Activities")
    act = ET.SubElement(
        activities, "Activity", Sport=_SPORT_TO_TCX.get(activity.sport, "Other")
    )
    _text(act, "Id", _iso(activity.start_time))
    lap = ET.SubElement(act, "Lap", StartTime=_iso(activity.start_time))
    _text(lap, "TotalTimeSeconds", f"{activity.duration_s:.1f}")
    _text(lap, "DistanceMeters", f"{activity.distance_m:.1f}")
    _text(lap, "Calories", activity.calories or 0)
    if activity.avg_hr:
        _text(ET.SubElement(lap, "AverageHeartRateBpm"), "Value", activity.avg_hr)
    if activity.max_hr:
        _text(ET.SubElement(lap, "MaximumHeartRateBpm"), "Value", activity.max_hr)
    _text(lap, "Intensity", "Active")
    _text(lap, "TriggerMethod", "Manual")

    track = ET.SubElement(lap, "Track")
    for p in activity.track:
        tp = ET.SubElement(track, "Trackpoint")
        _text(tp, "Time", _iso(p.time))
        if p.has_position:
            pos = ET.SubElement(tp, "Position")
            _text(pos, "LatitudeDegrees", f"{p.lat:.7f}")
            _text(pos, "LongitudeDegrees", f"{p.lon:.7f}")
        if p.ele is not None:
            _text(tp, "AltitudeMeters", f"{p.ele:.2f}")
        if p.distance_m is not None:
            _text(tp, "DistanceMeters", f"{p.distance_m:.1f}")
        if p.hr is not None:
            _text(ET.SubElement(tp, "HeartRateBpm"), "Value", p.hr)
        if p.cadence is not None:
            _text(tp, "Cadence", p.cadence)
    if activity.name:
        _text(act, "Notes", activity.name)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _find(el: ET.Element, *path: str) -> ET.Element | None:
    cur: ET.Element | None = el
    for name in path:
        if cur is None:
            return None
        cur = next((c for c in cur if _local(c.tag) == name), None)
    return cur


def _num(el: ET.Element, *path: str) -> float | None:
    node = _find(el, *path)
    if node is None or not (node.text or "").strip():
        return None
    try:
        return float(node.text.strip())  # type: ignore[union-attr]
    except ValueError as exc:
        raise FormatError(f"Invalid number in <{path[-1]}>: {node.text!r}") from exc


def _time(text: str) -> datetime:
    try:
        return datetime.fromisoformat(text.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise FormatError(f"Invalid timestamp: {text!r}") from exc


def parse_tcx(data: bytes) -> Activity:
    try:
        root = SafeET.fromstring(data)
    except (ET.ParseError, DefusedXmlException) as exc:
        raise FormatError(f"Not a valid TCX file: {exc}") from exc
    act_el = _find(root, "Activities", "Activity")
    if _local(root.tag) != "TrainingCenterDatabase" or act_el is None:
        raise FormatError("Not a TCX activity file")

    track: list[TrackPoint] = []
    duration = distance = 0.0
    calories = 0
    max_hr = 0
    hr_weighted = 0.0
    start = None
    for lap in (c for c in act_el if _local(c.tag) == "Lap"):
        if lap.attrib.get("StartTime") and start is None:
            start = _time(lap.attrib["StartTime"])
        lap_time = _num(lap, "TotalTimeSeconds") or 0.0
        duration += lap_time
        distance += _num(lap, "DistanceMeters") or 0.0
        calories += int(_num(lap, "Calories") or 0)
        lap_max = int(_num(lap, "MaximumHeartRateBpm", "Value") or 0)
        max_hr = max(max_hr, lap_max)
        hr_weighted += (_num(lap, "AverageHeartRateBpm", "Value") or 0.0) * lap_time
        for tp in (n for n in lap.iter() if _local(n.tag) == "Trackpoint"):
            time_el = _find(tp, "Time")
            if time_el is None or not time_el.text:
                continue
            lat = _num(tp, "Position", "LatitudeDegrees")
            lon = _num(tp, "Position", "LongitudeDegrees")
            hr = _num(tp, "HeartRateBpm", "Value")
            cad = _num(tp, "Cadence")
            track.append(
                TrackPoint(
                    time=_time(time_el.text),
                    lat=lat,
                    lon=lon,
                    ele=_num(tp, "AltitudeMeters"),
                    hr=int(hr) if hr is not None else None,
                    cadence=int(cad) if cad is not None else None,
                    distance_m=_num(tp, "DistanceMeters"),
                )
            )
    if start is None:
        id_el = _find(act_el, "Id")
        if id_el is not None and id_el.text:
            start = _time(id_el.text)
        elif track:
            start = track[0].time
        else:
            raise FormatError("TCX activity has no start time")

    notes = _find(act_el, "Notes")
    activity = Activity(
        id="pending",
        source="import",
        name=(notes.text.strip() if notes is not None and notes.text else ""),
        sport=_TCX_TO_SPORT.get(act_el.attrib.get("Sport", ""), Sport.OTHER),
        start_time=start,
        duration_s=duration,
        distance_m=distance,
        calories=calories or None,
        avg_hr=round(hr_weighted / duration) if duration and hr_weighted else None,
        max_hr=max_hr or None,
        track=track,
    )
    finalize_activity(activity)
    activity.name = activity.name or default_name(activity)
    activity.id = import_id(activity)
    return activity
