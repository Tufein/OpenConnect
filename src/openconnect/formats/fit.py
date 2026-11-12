"""Minimal FIT activity encoder plus a decoder built on ``fitdecode``.

Encoding writes the messages Garmin Connect and most platforms need for an activity upload:
file_id, record*, lap, session, activity. Decoding reads real-world files from devices.
"""

from __future__ import annotations

import io
import struct
from datetime import UTC, datetime

import fitdecode

from openconnect.domain import Activity, Sport, TrackPoint, ensure_utc, finalize_activity
from openconnect.formats.common import FormatError, default_name, import_id

FIT_EPOCH = datetime(1989, 12, 31, tzinfo=UTC)
PROTOCOL_VERSION = 0x20
PROFILE_VERSION = 2132

# base types: (type byte, struct format, invalid value)
ENUM = (0x00, "B", 0xFF)
UINT8 = (0x02, "B", 0xFF)
UINT16 = (0x84, "H", 0xFFFF)
SINT32 = (0x85, "i", 0x7FFFFFFF)
UINT32 = (0x86, "I", 0xFFFFFFFF)
UINT32Z = (0x8C, "I", 0)

_CRC_TABLE = (
    0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
    0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
)  # fmt: skip

_SPORT_TO_FIT = {  # sport, sub_sport
    Sport.RUNNING: (1, 0),
    Sport.CYCLING: (2, 0),
    Sport.SWIMMING: (5, 0),
    Sport.WALKING: (11, 0),
    Sport.HIKING: (17, 0),
    Sport.STRENGTH: (10, 20),
    Sport.YOGA: (10, 43),
    Sport.OTHER: (0, 0),
}


def crc16(data: bytes, crc: int = 0) -> int:
    for byte in data:
        tmp = _CRC_TABLE[crc & 0xF]
        crc = (crc >> 4) & 0x0FFF
        crc = crc ^ tmp ^ _CRC_TABLE[byte & 0xF]
        tmp = _CRC_TABLE[crc & 0xF]
        crc = (crc >> 4) & 0x0FFF
        crc = crc ^ tmp ^ _CRC_TABLE[(byte >> 4) & 0xF]
    return crc


def _fit_ts(dt: datetime) -> int:
    return int((ensure_utc(dt) - FIT_EPOCH).total_seconds())


def _semicircles(degrees: float) -> int:
    return round(degrees * (2**31) / 180)


class _Writer:
    def __init__(self) -> None:
        self.buf = bytearray()

    def define(self, local: int, global_num: int, fields: list[tuple[int, tuple]]) -> None:
        self.buf += struct.pack("<BBBHB", 0x40 | local, 0, 0, global_num, len(fields))
        for num, (base, fmt, _inv) in fields:
            self.buf += struct.pack("<BBB", num, struct.calcsize("<" + fmt), base)

    def data(self, local: int, fields: list[tuple[int, tuple]], values: list[int | None]) -> None:
        self.buf.append(local)
        for (_num, (_base, fmt, invalid)), value in zip(fields, values, strict=True):
            self.buf += struct.pack("<" + fmt, invalid if value is None else value)


FILE_ID = [(0, ENUM), (1, UINT16), (2, UINT16), (3, UINT32Z), (4, UINT32)]
RECORD = [
    (253, UINT32), (0, SINT32), (1, SINT32), (2, UINT16), (3, UINT8), (4, UINT8),
    (5, UINT32), (7, UINT16),
]  # fmt: skip
LAP = [(254, UINT16), (253, UINT32), (2, UINT32), (7, UINT32), (8, UINT32), (9, UINT32),
       (0, ENUM), (1, ENUM)]  # fmt: skip
SESSION = [
    (254, UINT16), (253, UINT32), (2, UINT32), (7, UINT32), (8, UINT32), (9, UINT32),
    (5, ENUM), (6, ENUM), (11, UINT16), (16, UINT8), (17, UINT8), (22, UINT16),
    (25, UINT16), (26, UINT16), (0, ENUM), (1, ENUM),
]  # fmt: skip
ACTIVITY = [(253, UINT32), (0, UINT32), (1, UINT16), (2, ENUM), (3, ENUM), (4, ENUM)]


def _clamp(value: float | int | None, lo: int, hi: int) -> int | None:
    if value is None:
        return None
    return max(lo, min(hi, round(value)))


def export_fit(activity: Activity) -> bytes:
    w = _Writer()
    start_ts = _fit_ts(activity.start_time)
    if activity.track:
        end_ts = _fit_ts(activity.track[-1].time)
    else:
        end_ts = start_ts + round(activity.duration_s)
    elapsed_ms = round(activity.duration_s * 1000)
    distance_cm = round(activity.distance_m * 100)
    sport, sub_sport = _SPORT_TO_FIT[activity.sport]

    w.define(0, 0, FILE_ID)
    w.data(0, FILE_ID, [4, 255, 0, 1, start_ts])  # type=activity, manufacturer=development

    w.define(1, 20, RECORD)
    for p in activity.track:
        w.data(
            1,
            RECORD,
            [
                _fit_ts(p.time),
                _semicircles(p.lat) if p.lat is not None else None,
                _semicircles(p.lon) if p.lon is not None else None,
                _clamp((p.ele + 500) * 5, 0, 0xFFFE) if p.ele is not None else None,
                _clamp(p.hr, 0, 254),
                _clamp(p.cadence, 0, 254),
                round(p.distance_m * 100) if p.distance_m is not None else None,
                _clamp(p.power, 0, 0xFFFE),
            ],
        )

    w.define(2, 19, LAP)
    w.data(2, LAP, [0, end_ts, start_ts, elapsed_ms, elapsed_ms, distance_cm, 9, 1])

    w.define(3, 18, SESSION)
    w.data(
        3,
        SESSION,
        [
            0, end_ts, start_ts, elapsed_ms, elapsed_ms, distance_cm,
            sport, sub_sport,
            _clamp(activity.calories, 0, 0xFFFE),
            _clamp(activity.avg_hr, 0, 254),
            _clamp(activity.max_hr, 0, 254),
            _clamp(activity.elevation_gain_m, 0, 0xFFFE),
            0, 1, 8, 1,
        ],
    )  # fmt: skip

    w.define(4, 34, ACTIVITY)
    w.data(4, ACTIVITY, [end_ts, elapsed_ms, 1, 0, 26, 1])

    body = bytes(w.buf)
    header = struct.pack("<BBHI4s", 14, PROTOCOL_VERSION, PROFILE_VERSION, len(body), b".FIT")
    header += struct.pack("<H", crc16(header))
    return header + body + struct.pack("<H", crc16(header + body))


def _verify(data: bytes) -> None:
    if len(data) < 16 or data[8:12] != b".FIT":
        raise FormatError("Not a FIT file (missing .FIT signature)")
    header_size = data[0]
    if header_size not in (12, 14):
        raise FormatError(f"Unsupported FIT header size {header_size}")
    data_size = struct.unpack_from("<I", data, 4)[0]
    end = header_size + data_size + 2
    if len(data) < end:
        raise FormatError("FIT file is truncated")
    if header_size == 14:
        stored = struct.unpack_from("<H", data, 12)[0]
        if stored and stored != crc16(data[:12]):
            raise FormatError("FIT header CRC mismatch")
    if struct.unpack_from("<H", data, end - 2)[0] != crc16(data[: end - 2]):
        raise FormatError("FIT file CRC mismatch (file is corrupt)")


def _value(msg: fitdecode.FitDataMessage, *names: str):
    for name in names:
        value = msg.get_value(name, fallback=None)
        if value is not None:
            return value
    return None


def _sport_from_fit(sport: str | None, sub_sport: str | None) -> Sport:
    if sport == "training":
        return {"strength_training": Sport.STRENGTH, "yoga": Sport.YOGA}.get(
            sub_sport or "", Sport.OTHER
        )
    return {
        "running": Sport.RUNNING,
        "cycling": Sport.CYCLING,
        "swimming": Sport.SWIMMING,
        "walking": Sport.WALKING,
        "hiking": Sport.HIKING,
    }.get(sport or "", Sport.OTHER)


def parse_fit(data: bytes) -> Activity:
    _verify(data)
    track: list[TrackPoint] = []
    session = None
    try:
        with fitdecode.FitReader(io.BytesIO(data)) as reader:
            for frame in reader:
                if not isinstance(frame, fitdecode.FitDataMessage):
                    continue
                if frame.name == "record":
                    ts = _value(frame, "timestamp")
                    if ts is None:
                        continue
                    lat = _value(frame, "position_lat")
                    lon = _value(frame, "position_long")
                    track.append(
                        TrackPoint(
                            time=ts,
                            lat=lat * 180 / 2**31 if lat is not None else None,
                            lon=lon * 180 / 2**31 if lon is not None else None,
                            ele=_value(frame, "enhanced_altitude", "altitude"),
                            hr=_value(frame, "heart_rate"),
                            cadence=_value(frame, "cadence"),
                            power=_value(frame, "power"),
                            distance_m=_value(frame, "distance"),
                        )
                    )
                elif frame.name == "session" and session is None:
                    session = frame
    except fitdecode.FitError as exc:
        raise FormatError(f"Could not decode FIT file: {exc}") from exc

    if session is None and not track:
        raise FormatError("FIT file contains no activity data")

    start = _value(session, "start_time") if session is not None else None
    start = start or track[0].time
    activity = Activity(id="pending", source="import", start_time=start, track=track)
    if session is not None:
        activity.sport = _sport_from_fit(_value(session, "sport"), _value(session, "sub_sport"))
        activity.duration_s = float(_value(session, "total_timer_time", "total_elapsed_time") or 0)
        activity.distance_m = float(_value(session, "total_distance") or 0)
        activity.elevation_gain_m = float(_value(session, "total_ascent") or 0)
        activity.avg_hr = _value(session, "avg_heart_rate")
        activity.max_hr = _value(session, "max_heart_rate")
        activity.calories = _value(session, "total_calories")
    finalize_activity(activity)
    activity.name = default_name(activity)
    activity.id = import_id(activity)
    return activity


__all__ = ["FIT_EPOCH", "crc16", "export_fit", "parse_fit"]
