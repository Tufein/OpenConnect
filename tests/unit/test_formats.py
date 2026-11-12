import io
import struct
from datetime import UTC, datetime
from xml.etree import ElementTree as ET

import fitdecode
import pytest

from openconnect.domain import Sport
from openconnect.formats import FormatError, export_activity, import_activity
from openconnect.formats import fit as fitfmt
from openconnect.formats import gpx as gpxfmt
from openconnect.formats import tcx as tcxfmt
from tests.factories import make_activity, make_track


def assert_tracks_close(a, b, ele_tol=0.25):
    assert len(a) == len(b)
    for p, q in zip(a, b, strict=True):
        assert p.time == q.time
        assert abs(p.lat - q.lat) < 1e-6
        assert abs(p.lon - q.lon) < 1e-6
        assert abs(p.ele - q.ele) < ele_tol
        assert p.hr == q.hr


# ---------------------------------------------------------------- GPX


def test_gpx_export_is_wellformed_and_has_trackpoint_extensions():
    act = make_activity()
    xml = gpxfmt.export_gpx(act)
    root = ET.fromstring(xml)
    assert root.tag.endswith("gpx")
    assert root.attrib["version"] == "1.1"
    assert xml.count(b"<trkpt") == len(act.track)
    assert b"<gpxtpx:hr>" in xml


def test_gpx_roundtrip_preserves_track_and_metadata():
    act = make_activity(name="Park & Ride <fast>", sport=Sport.CYCLING)
    parsed = gpxfmt.parse_gpx(gpxfmt.export_gpx(act))
    assert parsed.name == "Park & Ride <fast>"
    assert parsed.sport is Sport.CYCLING
    assert parsed.start_time == act.start_time
    assert_tracks_close(act.track, parsed.track, ele_tol=1e-6)
    assert parsed.duration_s == act.duration_s
    assert parsed.distance_m > 0  # derived from the track
    assert parsed.source == "import"


def test_gpx_parse_minimal_file_without_extensions_or_elevation():
    xml = b"""<?xml version="1.0"?>
    <gpx version="1.1" creator="x" xmlns="http://www.topografix.com/GPX/1/1">
      <trk><name>Walk</name><trkseg>
        <trkpt lat="52.0" lon="5.0"><time>2026-03-01T10:00:00Z</time></trkpt>
        <trkpt lat="52.001" lon="5.0"><time>2026-03-01T10:01:00Z</time></trkpt>
      </trkseg></trk></gpx>"""
    act = gpxfmt.parse_gpx(xml)
    assert act.name == "Walk"
    assert act.duration_s == 60
    assert 100 < act.distance_m < 120
    assert act.track[0].ele is None and act.track[0].hr is None


def test_gpx_parse_rejects_garbage_and_empty_tracks():
    with pytest.raises(FormatError):
        gpxfmt.parse_gpx(b"not xml at all")
    with pytest.raises(FormatError):
        gpxfmt.parse_gpx(b'<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"/>')


def test_gpx_parse_blocks_entity_expansion_attacks():
    bomb = b"""<?xml version="1.0"?>
    <!DOCTYPE gpx [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]>
    <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>&b;</name></metadata></gpx>"""
    with pytest.raises(FormatError):
        gpxfmt.parse_gpx(bomb)


# ---------------------------------------------------------------- TCX


def test_tcx_export_has_lap_totals_and_sport():
    act = make_activity(sport=Sport.RUNNING)
    root = ET.fromstring(tcxfmt.export_tcx(act))
    ns = {"t": "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"}
    activity = root.find("t:Activities/t:Activity", ns)
    assert activity.attrib["Sport"] == "Running"
    lap = activity.find("t:Lap", ns)
    assert float(lap.find("t:DistanceMeters", ns).text) == pytest.approx(5000.0)
    assert float(lap.find("t:TotalTimeSeconds", ns).text) == pytest.approx(59.0)
    assert len(lap.findall("t:Track/t:Trackpoint", ns)) == len(act.track)


def test_tcx_roundtrip():
    act = make_activity(sport=Sport.CYCLING)
    parsed = tcxfmt.parse_tcx(tcxfmt.export_tcx(act))
    assert parsed.sport is Sport.CYCLING
    assert parsed.start_time == act.start_time
    assert parsed.distance_m == pytest.approx(5000.0)
    assert parsed.avg_hr == 150 and parsed.max_hr == 159
    assert parsed.calories == 420
    assert_tracks_close(act.track, parsed.track, ele_tol=1e-6)


def test_tcx_indoor_activity_without_positions():
    act = make_activity(sport=Sport.OTHER)
    for p in act.track:
        p.lat = p.lon = None
    parsed = tcxfmt.parse_tcx(tcxfmt.export_tcx(act))
    assert not parsed.has_gps
    assert len(parsed.track) == len(act.track)
    assert parsed.track[3].hr == act.track[3].hr


def test_tcx_parse_rejects_garbage():
    with pytest.raises(FormatError):
        tcxfmt.parse_tcx(b"<nope/>")


# ---------------------------------------------------------------- FIT


def decode(data: bytes):
    with fitdecode.FitReader(io.BytesIO(data)) as reader:
        return [f for f in reader if isinstance(f, fitdecode.FitDataMessage)]


def test_fit_header_and_crc_are_valid():
    data = fitfmt.export_fit(make_activity())
    header_size, _proto, _profile, data_size = struct.unpack("<BBHI", data[:8])
    assert header_size == 14
    assert data[8:12] == b".FIT"
    assert len(data) == header_size + data_size + 2
    assert fitfmt.crc16(data[:-2]) == struct.unpack("<H", data[-2:])[0]
    assert fitfmt.crc16(data[:12]) == struct.unpack("<H", data[12:14])[0]


def test_fit_is_readable_by_an_independent_decoder():
    act = make_activity()
    msgs = decode(fitfmt.export_fit(act))
    names = [m.name for m in msgs]
    assert names[0] == "file_id"
    assert names.count("record") == len(act.track)
    assert "session" in names and "lap" in names and names[-1] == "activity"
    session = next(m for m in msgs if m.name == "session")
    assert session.get_value("total_distance") == pytest.approx(5000.0)
    assert session.get_value("avg_heart_rate") == 150
    assert session.get_value("sport") == "running"


def test_fit_roundtrip_through_own_parser():
    act = make_activity(sport=Sport.CYCLING)
    parsed = fitfmt.parse_fit(fitfmt.export_fit(act))
    assert parsed.sport is Sport.CYCLING
    assert parsed.start_time == act.start_time
    assert parsed.distance_m == pytest.approx(5000.0)
    assert parsed.calories == 420
    assert parsed.elevation_gain_m == pytest.approx(12.0)
    assert_tracks_close(act.track, parsed.track, ele_tol=0.21)
    assert parsed.track[0].cadence == 84


@pytest.mark.parametrize(
    "sport", [Sport.RUNNING, Sport.CYCLING, Sport.SWIMMING, Sport.WALKING, Sport.HIKING,
              Sport.STRENGTH, Sport.YOGA, Sport.OTHER]
)
def test_fit_sport_roundtrip(sport):
    parsed = fitfmt.parse_fit(fitfmt.export_fit(make_activity(sport=sport, n=5)))
    assert parsed.sport is sport


def test_fit_roundtrip_without_gps_or_hr():
    act = make_activity(n=10, avg_hr=None, max_hr=None)
    for p in act.track:
        p.lat = p.lon = p.hr = p.cadence = None
    parsed = fitfmt.parse_fit(fitfmt.export_fit(act))
    assert len(parsed.track) == 10
    assert not parsed.has_gps
    assert parsed.track[0].hr is None


def test_fit_rejects_corrupt_files():
    good = fitfmt.export_fit(make_activity(n=5))
    flipped = bytearray(good)
    flipped[20] ^= 0xFF
    with pytest.raises(FormatError):
        fitfmt.parse_fit(bytes(flipped))
    with pytest.raises(FormatError):
        fitfmt.parse_fit(good[:30])
    with pytest.raises(FormatError):
        fitfmt.parse_fit(b"hello world")


def test_fit_timestamps_use_fit_epoch():
    # 1989-12-31T00:00:00Z is FIT second 0; 2026-05-02T07:00:00Z is 1_146_034_800 s later
    act = make_activity(n=2, start=datetime(2026, 5, 2, 7, 0, tzinfo=UTC))
    msgs = decode(fitfmt.export_fit(act))
    first_record = next(m for m in msgs if m.name == "record")
    assert first_record.get_value("timestamp") == act.start_time


# ---------------------------------------------------------------- facade


@pytest.mark.parametrize("fmt", ["fit", "gpx", "tcx"])
def test_export_import_facade_roundtrip(fmt):
    act = make_activity()
    data, content_type, filename = export_activity(act, fmt)
    assert filename.endswith(f".{fmt}")
    assert content_type
    parsed = import_activity(data, filename)
    assert parsed.start_time == act.start_time
    assert len(parsed.track) == len(act.track)


def test_import_detects_format_by_content_when_extension_is_misleading():
    data, _, _ = export_activity(make_activity(n=5), "fit")
    assert import_activity(data, "upload.bin").sport is Sport.RUNNING


def test_import_ids_are_stable_for_identical_files_and_distinct_otherwise():
    a1, _, _ = export_activity(make_activity(n=5), "gpx")
    a2, _, _ = export_activity(make_activity(n=5), "gpx")
    other, _, _ = export_activity(make_activity(n=6), "gpx")
    assert import_activity(a1, "a.gpx").id == import_activity(a2, "b.gpx").id
    assert import_activity(a1, "a.gpx").id != import_activity(other, "a.gpx").id
    assert import_activity(a1, "a.gpx").id.startswith("import:")


def test_export_rejects_unknown_format():
    with pytest.raises(FormatError):
        export_activity(make_activity(), "docx")


def test_make_track_helper_is_deterministic():
    assert make_track(3) == make_track(3)
