import re

from openconnect.domain import TrackPoint
from openconnect.web.charts import bar_chart, route_svg, sparkline
from openconnect.web.formatting import duration, km, pace
from openconnect.web.markup import render_markdown
from tests.factories import make_track


def test_formatting_helpers():
    assert km(5234) == "5.23 km" and km(None) == "–"
    assert duration(3725) == "1:02:05" and duration(125) == "2:05" and duration(None) == "–"
    assert pace(300) == "5:00 /km" and pace(None) == "–"


def test_route_svg_draws_a_polyline_within_the_viewbox():
    svg = str(route_svg(make_track(200), width=300, height=200))
    assert svg.startswith("<svg") and 'viewBox="0 0 300 200"' in svg
    points = re.search(r'points="([^"]+)"', svg).group(1).split()
    assert 2 <= len(points) <= 300  # downsampled
    for pt in points:
        x, y = map(float, pt.split(","))
        assert 0 <= x <= 300 and 0 <= y <= 200


def test_route_svg_marks_start_and_finish():
    svg = str(route_svg(make_track(20)))
    assert svg.count("<circle") == 2


def test_route_svg_without_gps_returns_a_placeholder_message():
    track = [TrackPoint(time=t.time, hr=100) for t in make_track(5)]
    out = str(route_svg(track))
    assert "No GPS" in out and "<polyline" not in out
    assert "No GPS" in str(route_svg([]))


def test_route_svg_keeps_aspect_ratio():
    # a track that is wide and flat must not be stretched into the box
    flat = [
        TrackPoint(time=t.time, lat=52.0, lon=5.0 + i * 0.001) for i, t in enumerate(make_track(20))
    ]
    pts = re.search(r'points="([^"]+)"', str(route_svg(flat, 300, 200))).group(1).split()
    ys = {float(p.split(",")[1]) for p in pts}
    assert len(ys) == 1


def test_sparkline_handles_gaps_and_flat_series():
    svg = str(sparkline([1, 2, None, 4, 5], width=100, height=20))
    assert svg.count("<polyline") == 2  # the gap splits the line in two
    assert "<polyline" in str(sparkline([3, 3, 3]))
    assert "<svg" in str(sparkline([]))


def test_bar_chart_scales_bars_and_labels_them():
    svg = str(bar_chart([("Mon", 10), ("Tue", 5), ("Wed", None)], width=300, height=100))
    assert svg.count("<rect") == 2  # the missing value draws no bar
    assert "Mon" in svg and "Wed" in svg
    heights = [float(h) for h in re.findall(r'<rect[^>]* height="([\d.]+)"', svg)]
    assert heights[0] > heights[1] > 0


def test_chart_labels_are_escaped():
    svg = str(bar_chart([("<b>x</b>", 1)]))
    assert "<b>" not in svg and "&lt;b&gt;" in svg


def test_markdown_renders_the_subset_reports_use():
    html = str(render_markdown("## Highlights\n- Fast **5K**\n- Steady HR\n\nNext line"))
    assert "<h3>Highlights</h3>" in html
    assert html.count("<li>") == 2 and "<strong>5K</strong>" in html
    assert "<p>Next line</p>" in html


def test_markdown_never_emits_raw_html():
    html = str(render_markdown("<script>alert(1)</script> **ok** [x](javascript:alert(1))"))
    assert "<script>" not in html and "&lt;script&gt;" in html
    assert "<a " not in html
