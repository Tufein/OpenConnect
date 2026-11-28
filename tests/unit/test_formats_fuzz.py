"""Damaged files must be refused with FormatError, never with an unexplained exception.

The server's parsers read files from outside (uploads, a watch folder). Each export is damaged
in a few hundred ways from a fixed seed: cut short, bytes changed, numbers replaced by awkward
ones. A parser may return an activity or raise FormatError; anything else is a crash waiting for
the one real file that triggers it. A returned activity must hold only real numbers and places on
Earth, because NaN breaks every sum, chart and JSON answer.
"""

import math
import random
import re

import pytest

from openconnect.formats import FormatError, export_activity, import_activity
from tests.factories import make_activity

AWKWARD = [
    "NaN",
    "nan",
    "inf",
    "-Infinity",
    "1e999",
    "-1",
    "0",
    "99999999999999999999",
    "",
    "abc",
    "1,5",
    "-0.0",
]
NUMBER = re.compile(rb"-?\d+(\.\d+)?([eE][-+]?\d+)?")


def _sane(activity) -> list[str]:
    bad = []
    for name in ("duration_s", "distance_m", "elevation_gain_m"):
        value = getattr(activity, name, 0.0)
        if value is not None and not math.isfinite(value):
            bad.append(f"{name}={value}")
    for p in activity.track:
        for name in ("lat", "lon", "ele"):
            value = getattr(p, name, None)
            if value is not None and not math.isfinite(value):
                bad.append(f"track {name}={value}")
        if p.lat is not None and not -90 <= p.lat <= 90:
            bad.append(f"lat={p.lat}")
        if p.lon is not None and not -180 <= p.lon <= 180:
            bad.append(f"lon={p.lon}")
    return bad


def _text_mutants(seed: bytes, rnd: random.Random, count: int):
    numbers = list(NUMBER.finditer(seed))
    for _ in range(count):
        kind = rnd.randrange(3)
        if kind == 0:
            yield seed[: rnd.randrange(len(seed) + 1)]
        elif kind == 1 and numbers:
            hit = numbers[rnd.randrange(len(numbers))]
            yield seed[: hit.start()] + rnd.choice(AWKWARD).encode() + seed[hit.end() :]
        else:
            a = rnd.randrange(len(seed))
            b = min(len(seed), a + 1 + rnd.randrange(200))
            yield seed[:a] + seed[b:]


def _byte_mutants(seed: bytes, rnd: random.Random, count: int):
    for _ in range(count):
        data = bytearray(seed)
        for _ in range(1 + rnd.randrange(4)):
            data[rnd.randrange(len(data))] = rnd.choice(
                [0x00, 0x01, 0x7F, 0x80, 0xFF, rnd.randrange(256)]
            )
        yield bytes(data)
    for _ in range(100):
        yield seed[: rnd.randrange(len(seed) + 1)]


def _check(data: bytes, filename: str, failures: dict) -> None:
    try:
        activity = import_activity(data, filename)
    except FormatError:
        return
    except Exception as exc:  # noqa: BLE001 - anything but FormatError is what this test looks for
        failures.setdefault(f"{filename}: {type(exc).__name__}", repr(exc)[:300])
        return
    bad = _sane(activity)
    if bad:
        failures.setdefault(f"{filename}: insane {bad[0].split('=')[0]}", ", ".join(bad[:3]))


@pytest.mark.parametrize("fmt", ["gpx", "tcx"])
def test_damaged_text_files_are_refused_with_format_error(fmt):
    seed, _, filename = export_activity(make_activity(), fmt)
    rnd = random.Random(f"text-{fmt}")
    failures: dict = {}
    for data in _text_mutants(seed, rnd, 1500):
        _check(data, f"run.{fmt}", failures)
    assert not failures, "\n".join(f"{k}: {v}" for k, v in failures.items())


def test_damaged_fit_files_are_refused_with_format_error():
    seed, _, _ = export_activity(make_activity(), "fit")
    rnd = random.Random("fit")
    failures: dict = {}
    for data in _byte_mutants(seed, rnd, 1500):
        _check(data, "run.fit", failures)
    assert not failures, "\n".join(f"{k}: {v}" for k, v in failures.items())
