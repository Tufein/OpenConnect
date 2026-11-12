import re

from openconnect.formats import export_activity
from tests.factories import make_activity


def first_activity(app, sport="running"):
    return app.client.get("/api/activities", params={"sport": sport}).json()["items"][0]


def test_dashboard_empty_state_guides_the_user(app):
    html = app.client.get("/").text
    assert "OpenConnect" in html and "No data yet" in html
    assert 'action="/ui/sync"' in html


def test_dashboard_shows_health_and_recent_activities(synced):
    html = synced.client.get("/").text
    assert "Steps" in html and "Resting HR" in html and "Body Battery" in html
    assert "This week" in html and "<svg" in html
    run = first_activity(synced)
    assert f'href="/activities/{run["id"]}"' in html


def test_activities_page_filters_by_sport_and_links_to_details(synced):
    html = synced.client.get("/activities", params={"sport": "cycling"}).text
    assert "Weekend Ride" in html and "Tempo Run" not in html
    assert re.search(r'href="/activities/garmin:\d+"', html)
    assert 'value="cycling" selected' in html


def test_activity_page_shows_map_stats_exports_and_report_button(synced):
    run = first_activity(synced)
    html = synced.client.get(f"/activities/{run['id']}").text
    assert 'class="route"' in html and run["name"] in html
    for fmt in ("fit", "gpx", "tcx"):
        assert f"/api/activities/{run['id']}/export?format={fmt}" in html
    assert f'action="/ui/reports/activity/{run["id"]}"' in html


def test_activity_names_are_html_escaped(app):
    app.ctx.repo.upsert_activity(make_activity(id="x:1", name="<script>alert(1)</script>"))
    for url in ("/activities", "/activities/x:1", "/"):
        html = app.client.get(url).text
        assert "<script>alert(1)" not in html
    assert "&lt;script&gt;" in app.client.get("/activities/x:1").text


def test_unknown_activity_page_is_a_404(app):
    response = app.client.get("/activities/nope")
    assert response.status_code == 404 and "not found" in response.text.lower()


def test_health_page(synced):
    html = synced.client.get("/health").text
    assert "Sleep" in html and "Stress" in html and "Body Battery" in html and "<svg" in html


def test_records_page_lists_records_achievements_and_devices(synced):
    html = synced.client.get("/records").text
    assert "Longest distance" in html and "Player One" in html and "Forerunner 965" in html
    assert "unlocked" in html and "locked" in html


def test_weekly_page_generates_and_renders_a_report(synced):
    page = synced.client.get("/weekly", params={"week": "2026-05-20"}).text
    assert "2026-05-18" in page and 'action="/ui/reports/weekly"' in page
    done = synced.client.post(
        "/ui/reports/weekly", data={"week": "2026-05-18"}, follow_redirects=False
    )
    assert done.status_code == 303 and done.headers["location"] == "/weekly?week=2026-05-18"
    shown = synced.client.get("/weekly", params={"week": "2026-05-18"}).text
    assert "<strong>Week of 2026-05-18</strong>" in shown


def test_activity_report_form_redirects_back_and_renders_markdown(synced):
    run = first_activity(synced)
    done = synced.client.post(f"/ui/reports/activity/{run['id']}", follow_redirects=False)
    assert done.status_code == 303 and done.headers["location"] == f"/activities/{run['id']}"
    html = synced.client.get(f"/activities/{run['id']}").text
    assert "<strong>" in html and "template" in html  # provider is shown next to the report


def test_import_page_and_form(app):
    assert 'enctype="multipart/form-data"' in app.client.get("/import").text
    data, _, name = export_activity(make_activity(), "gpx")
    done = app.client.post("/ui/import", files={"file": (name, data)}, follow_redirects=False)
    assert done.status_code == 303 and done.headers["location"].startswith("/activities/import:")
    bad = app.client.post("/ui/import", files={"file": ("a.gpx", b"nope")})
    assert bad.status_code == 400 and "Not a valid GPX" in bad.text


def test_sync_form_redirects_to_the_dashboard(app):
    done = app.client.post("/ui/sync", follow_redirects=False)
    assert done.status_code == 303 and done.headers["location"] == "/"
    assert app.client.get("/api/activities").json()["total"] > 0


def test_settings_page_explains_mock_mode(app):
    html = app.client.get("/settings").text
    assert "mock" in html.lower() and "OPENCONNECT_GARMIN_MODE" in html
    assert "template" in html  # AI provider in use


def test_live_settings_page_offers_connect(make_app):
    html = (
        make_app(garmin_mode="live", garmin_client_id="c", garmin_client_secret="s")
        .client.get("/settings")
        .text
    )
    assert 'href="/api/garmin/connect"' in html


def test_static_css_is_served(app):
    response = app.client.get("/static/openconnect.css")
    assert response.status_code == 200 and "text/css" in response.headers["content-type"]
