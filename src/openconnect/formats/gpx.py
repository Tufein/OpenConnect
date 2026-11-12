"""GPX 1.1 import/export with the Garmin TrackPointExtension (heart rate, cadence)."""

from __future__ import annotations

from datetime import datetime
from xml.etree import ElementTree as ET

from defusedxml import ElementTree as SafeET
from defusedxml.common import DefusedXmlException

from openconnect.domain import Activity, TrackPoint, ensure_utc, finalize_activity
from openconnect.formats.common import FormatError, default_name, import_id, sport_from_text

GPX_NS = "http://www.topografix.com/GPX/1/1"
TPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"


def _iso(dt: datetime) -> str:
    return ensure_utc(dt).strftime("%Y-%m-%dT%H:%M:%SZ")


def export_gpx(activity: Activity) -> bytes:
    positioned = [p for p in activity.track if p.has_position]
    if not positioned:
        raise FormatError("GPX needs GPS positions; this activity has none")

    # Namespaces are declared as plain attributes so the output uses a default namespace for GPX
    # without touching ElementTree's process-wide namespace registry.
    root = ET.Element(
        "gpx",
        {"version": "1.1", "creator": "OpenConnect", "xmlns": GPX_NS, "xmlns:gpxtpx": TPX_NS},
    )
    meta = ET.SubElement(root, "metadata")
    ET.SubElement(meta, "name").text = activity.name or default_name(activity)
    ET.SubElement(meta, "time").text = _iso(activity.start_time)

    trk = ET.SubElement(root, "trk")
    ET.SubElement(trk, "name").text = activity.name or default_name(activity)
    ET.SubElement(trk, "type").text = activity.sport.value
    seg = ET.SubElement(trk, "trkseg")
    for p in positioned:
        pt = ET.SubElement(seg, "trkpt", lat=f"{p.lat:.7f}", lon=f"{p.lon:.7f}")
        if p.ele is not None:
            ET.SubElement(pt, "ele").text = f"{p.ele:.2f}"
        ET.SubElement(pt, "time").text = _iso(p.time)
        if p.hr is not None or p.cadence is not None:
            ext = ET.SubElement(pt, "extensions")
            tpx = ET.SubElement(ext, "gpxtpx:TrackPointExtension")
            if p.hr is not None:
                ET.SubElement(tpx, "gpxtpx:hr").text = str(p.hr)
            if p.cadence is not None:
                ET.SubElement(tpx, "gpxtpx:cad").text = str(p.cadence)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _child_text(el: ET.Element, name: str) -> str | None:
    for child in el:
        if _local(child.tag) == name and child.text:
            return child.text.strip()
    return None


def _descendant_text(el: ET.Element, name: str) -> str | None:
    for node in el.iter():
        if _local(node.tag) == name and node.text:
            return node.text.strip()
    return None


def parse_gpx(data: bytes) -> Activity:
    try:
        root = SafeET.fromstring(data)
    except (ET.ParseError, DefusedXmlException) as exc:
        raise FormatError(f"Not a valid GPX file: {exc}") from exc
    if _local(root.tag) != "gpx":
        raise FormatError("Not a GPX file (root element is not <gpx>)")

    track: list[TrackPoint] = []
    name = sport_text = None
    for trk in (n for n in root.iter() if _local(n.tag) == "trk"):
        name = name or _child_text(trk, "name")
        sport_text = sport_text or _child_text(trk, "type")
        for pt in (n for n in trk.iter() if _local(n.tag) == "trkpt"):
            time_text = _child_text(pt, "time")
            if not time_text:
                continue
            try:
                lat, lon = float(pt.attrib["lat"]), float(pt.attrib["lon"])
                when = datetime.fromisoformat(time_text.replace("Z", "+00:00"))
            except (KeyError, ValueError) as exc:
                raise FormatError(f"Malformed track point: {exc}") from exc
            ele = _child_text(pt, "ele")
            hr = _descendant_text(pt, "hr")
            cad = _descendant_text(pt, "cad")
            track.append(
                TrackPoint(
                    time=when,
                    lat=lat,
                    lon=lon,
                    ele=float(ele) if ele else None,
                    hr=int(float(hr)) if hr else None,
                    cadence=int(float(cad)) if cad else None,
                )
            )
    if not track:
        raise FormatError("GPX file contains no timestamped track points")

    meta_name = None
    for node in root:
        if _local(node.tag) == "metadata":
            meta_name = _child_text(node, "name")
    activity = Activity(
        id="pending",
        source="import",
        name=name or meta_name or "",
        sport=sport_from_text(sport_text),
        start_time=track[0].time,
        track=track,
    )
    finalize_activity(activity)
    activity.name = activity.name or default_name(activity)
    activity.id = import_id(activity)
    return activity
