import pytest

from openconnect.formats import export_activity
from tests.factories import make_activity
from tests.fakes import FakeAnthropic


def test_healthz(app):
    assert app.client.get("/healthz").json() == {"status": "ok", "version": "0.28.0"}


# --------------------------------------------------------------------- sync and activities


def test_sync_in_mock_mode_populates_everything(app):
    body = app.client.post("/api/sync", params={"days_back": 28}).json()
    assert body["activities"] >= 8 and body["dailies"] >= 27 and body["sleeps"] >= 27
    assert body["errors"] == []
    listing = app.client.get("/api/activities").json()
    assert listing["total"] == body["activities"]
    assert listing["items"][0]["start_time"] > listing["items"][-1]["start_time"]
    assert "track" not in listing["items"][0]


def test_activity_list_filters_and_paginates(synced):
    c = synced.client
    runs = c.get("/api/activities", params={"sport": "running"}).json()
    assert runs["total"] > 0 and all(a["sport"] == "running" for a in runs["items"])
    page = c.get("/api/activities", params={"limit": 2, "offset": 1}).json()
    assert len(page["items"]) == 2 and page["total"] > 2
    assert c.get("/api/activities", params={"sport": "curling"}).status_code == 422
    assert c.get("/api/activities", params={"limit": 0}).status_code == 422


def test_activity_detail_includes_track_and_derived_fields(synced):
    first = synced.client.get("/api/activities", params={"sport": "running"}).json()["items"][0]
    detail = synced.client.get(f"/api/activities/{first['id']}").json()
    assert len(detail["track"]) > 20 and detail["track"][0]["lat"] is not None
    assert detail["pace_s_per_km"] and detail["avg_speed_mps"]


def test_unknown_activity_is_404(app):
    assert app.client.get("/api/activities/nope").status_code == 404
    assert app.client.delete("/api/activities/nope").status_code == 404
    assert app.client.get("/api/activities/nope/export?format=gpx").status_code == 404


def test_delete_activity(synced):
    item = synced.client.get("/api/activities").json()["items"][0]
    assert synced.client.delete(f"/api/activities/{item['id']}").status_code == 204
    assert synced.client.get(f"/api/activities/{item['id']}").status_code == 404


@pytest.mark.parametrize(
    ("fmt", "magic"), [("gpx", b"<gpx"), ("tcx", b"<TrainingCenterDatabase"), ("fit", b".FIT")]
)
def test_export_formats(synced, fmt, magic):
    item = synced.client.get("/api/activities", params={"sport": "running"}).json()["items"][0]
    response = synced.client.get(f"/api/activities/{item['id']}/export", params={"format": fmt})
    assert response.status_code == 200
    assert magic in response.content[:200]
    assert 'filename="running-' in response.headers["content-disposition"]
    assert response.headers["content-disposition"].endswith(f'.{fmt}"')


def test_export_rejects_unknown_format_and_gps_less_gpx(synced):
    c = synced.client
    run = c.get("/api/activities", params={"sport": "running"}).json()["items"][0]
    assert (
        c.get(f"/api/activities/{run['id']}/export", params={"format": "docx"}).status_code == 400
    )
    strength = c.get("/api/activities", params={"sport": "strength"}).json()["items"][0]
    response = c.get(f"/api/activities/{strength['id']}/export", params={"format": "gpx"})
    assert response.status_code == 422 and "GPS" in response.json()["detail"]


# --------------------------------------------------------------------- import


@pytest.mark.parametrize("fmt", ["fit", "gpx", "tcx"])
def test_import_roundtrip(app, fmt):
    data, _, name = export_activity(make_activity(), fmt)
    response = app.client.post("/api/import", files={"file": (name, data)})
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["created"] is True and body["activity"]["source"] == "import"
    again = app.client.post("/api/import", files={"file": (name, data)})
    assert again.status_code == 200 and again.json()["created"] is False  # same file, no duplicate
    assert app.client.get("/api/activities").json()["total"] == 1
    assert app.client.get(f"/api/activities/{body['activity']['id']}").json()["track"]


def test_import_rejects_garbage_and_oversized_files(app, monkeypatch):
    response = app.client.post("/api/import", files={"file": ("x.fit", b"definitely not fit")})
    assert response.status_code == 400 and "FIT" in response.json()["detail"]
    monkeypatch.setattr("openconnect.web.routes_api.MAX_UPLOAD_BYTES", 100)
    data, _, name = export_activity(make_activity(), "gpx")
    assert app.client.post("/api/import", files={"file": (name, data)}).status_code == 413


# --------------------------------------------------------------------- health and dashboard


def test_health_endpoints(synced):
    c = synced.client
    days = c.get("/api/health/daily", params={"start": "2026-05-20", "end": "2026-05-25"}).json()
    assert [d["date"] for d in days] == [f"2026-05-{d}" for d in range(20, 26)]
    assert days[0]["steps"] > 0 and days[0]["body_battery_high"] > days[0]["body_battery_low"]
    sleep = c.get("/api/health/sleep", params={"start": "2026-05-20", "end": "2026-05-22"}).json()
    assert len(sleep) == 3 and sleep[0]["score"] > 0
    assert (
        c.get("/api/health/daily", params={"start": "nope", "end": "2026-05-25"}).status_code == 422
    )
    assert (
        c.get("/api/health/daily", params={"start": "2026-05-25", "end": "2026-05-20"}).status_code
        == 422
    )


def test_dashboard_endpoints(synced):
    c = synced.client
    week = c.get("/api/dashboard/weekly", params={"week": "2026-05-20"}).json()
    assert week["week_start"] == "2026-05-18" and week["activity_count"] >= 3
    overview = c.get("/api/dashboard/overview").json()
    assert overview["today"]["date"] == "2026-05-31" and len(overview["trend"]) == 30
    assert overview["this_week"]["week_start"] == "2026-05-25"


def test_records_achievements_devices(synced):
    c = synced.client
    records = c.get("/api/records").json()
    assert records["running"]["longest_distance"]["value"] > 5000
    achievements = {a["id"]: a for a in c.get("/api/achievements").json()}
    assert achievements["first_activity"]["unlocked"] and achievements["steps_10k"]["unlocked"]
    devices = {d["name"] for d in c.get("/api/devices").json()}
    assert {"Forerunner 965", "Edge 1050"} <= devices


# --------------------------------------------------------------------- reports


def test_activity_report_is_generated_then_served_from_cache(synced):
    c = synced.client
    run = c.get("/api/activities", params={"sport": "running"}).json()["items"][0]
    assert c.get(f"/api/reports/activity/{run['id']}").status_code == 404
    first = c.post(f"/api/reports/activity/{run['id']}")
    assert first.status_code == 200 and run["name"] in first.json()["text"]
    assert first.json()["provider"] == "template"
    second = c.post(f"/api/reports/activity/{run['id']}")
    assert second.json()["id"] == first.json()["id"]  # unchanged data: no new report
    forced = c.post(f"/api/reports/activity/{run['id']}", params={"force": "true"})
    assert forced.json()["id"] != first.json()["id"]
    assert c.get(f"/api/reports/activity/{run['id']}").json()["id"] == forced.json()["id"]


def test_weekly_report(synced):
    report = synced.client.post("/api/reports/weekly", params={"week": "2026-05-20"}).json()
    assert report["subject"] == "2026-05-18" and "Week of 2026-05-18" in report["text"]
    assert synced.client.get("/api/reports/weekly", params={"week": "2026-05-24"}).json()["id"]


def test_report_for_unknown_activity_is_404(app):
    assert app.client.post("/api/reports/activity/nope").status_code == 404


def test_claude_provider_errors_surface_as_502(make_app):
    app = make_app(
        provider=__import__("openconnect.ai.provider", fromlist=["x"]).AnthropicReportProvider(
            FakeAnthropic(stop_reason="refusal")
        )
    )
    app.client.post("/api/sync", params={"days_back": 14})
    run = app.client.get("/api/activities").json()["items"][0]
    response = app.client.post(f"/api/reports/activity/{run['id']}")
    assert response.status_code == 502 and "declined" in response.json()["detail"]


# --------------------------------------------------------------------- Garmin push webhook


PUSH = {
    "dailies": [{"summaryId": "p1", "calendarDate": "2026-05-30", "steps": 4321}],
    "sleeps": [{"summaryId": "p2", "calendarDate": "2026-05-30", "durationInSeconds": 25200}],
}


def test_webhook_ingests_pushed_summaries(app):
    response = app.client.post("/api/garmin/webhook", json=PUSH)
    assert response.status_code == 200
    assert response.json()["ingested"] == {"activities": 0, "dailies": 1, "sleeps": 1}
    days = app.client.get(
        "/api/health/daily", params={"start": "2026-05-30", "end": "2026-05-30"}
    ).json()
    assert days[0]["steps"] == 4321


def test_webhook_requires_the_token_when_configured(make_app):
    app = make_app(webhook_token="s3cret")
    assert app.client.post("/api/garmin/webhook", json=PUSH).status_code == 401
    assert app.client.post("/api/garmin/webhook?token=wrong", json=PUSH).status_code == 401
    assert app.client.post("/api/garmin/webhook?token=s3cret", json=PUSH).status_code == 200


def test_webhook_rejects_bad_json_and_reports_ping_notifications(app):
    assert (
        app.client.post(
            "/api/garmin/webhook", content=b"{nope", headers={"content-type": "application/json"}
        ).status_code
        == 400
    )
    assert app.client.post("/api/garmin/webhook", json=[1, 2]).status_code == 400
    ping = {"dailies": [{"userId": "u", "callbackURL": "https://apis.garmin.com/x"}]}
    body = app.client.post("/api/garmin/webhook", json=ping).json()
    assert body["ping_notifications"] == 1 and body["ingested"]["dailies"] == 0


# --------------------------------------------------------------------- Garmin live OAuth


def token_ok(request):
    import httpx

    return httpx.Response(200, json={"access_token": "a", "refresh_token": "r", "expires_in": 3600})


@pytest.fixture
def live(make_app):
    return make_app(
        oauth_handler=token_ok,
        garmin_mode="live",
        garmin_client_id="cid",
        garmin_client_secret="sec",
    )


def test_status_in_mock_mode(app):
    assert app.client.get("/api/garmin/status").json() == {"mode": "mock", "connected": True}
    assert app.client.get("/api/garmin/connect", follow_redirects=False).status_code == 409


def test_live_mode_requires_connection_before_sync(live):
    assert live.client.get("/api/garmin/status").json() == {"mode": "live", "connected": False}
    response = live.client.post("/api/sync")
    assert response.status_code == 409 and "connect" in response.json()["detail"].lower()


def test_connect_flow_redirects_to_garmin_then_stores_tokens(live):
    redirect = live.client.get("/api/garmin/connect", follow_redirects=False)
    assert redirect.status_code == 307
    location = redirect.headers["location"]
    assert location.startswith("https://connect.garmin.com/oauth2Confirm?")
    assert "code_challenge_method=S256" in location
    state = location.split("state=")[1].split("&")[0]

    done = live.client.get(
        "/api/garmin/callback", params={"code": "abc", "state": state}, follow_redirects=False
    )
    assert done.status_code == 303 and done.headers["location"] == "/settings?connected=1"
    assert live.client.get("/api/garmin/status").json()["connected"] is True
    assert live.client.post("/api/garmin/disconnect").status_code == 204
    assert live.client.get("/api/garmin/status").json()["connected"] is False


def test_callback_rejects_forged_state_and_provider_errors(live):
    assert (
        live.client.get("/api/garmin/callback", params={"code": "x", "state": "forged"}).status_code
        == 400
    )
    denied = live.client.get("/api/garmin/callback", params={"error": "access_denied"})
    assert denied.status_code == 400 and "access_denied" in denied.json()["detail"]


# --------------------------------------------------------------------- request guards


def test_cross_origin_posts_are_refused(app):
    evil = {"Origin": "http://evil.example"}
    assert app.client.post("/api/sync", headers=evil).status_code == 403
    assert app.client.post("/api/sync", headers={"Origin": "http://testserver"}).status_code == 200
    assert app.client.post("/api/sync").status_code == 200  # no Origin: curl, scripts, Garmin


def test_fetch_metadata_marks_cross_site_posts(app):
    for site in ("cross-site", "same-site"):
        assert app.client.post("/api/sync", headers={"Sec-Fetch-Site": site}).status_code == 403
    same = {"Sec-Fetch-Site": "same-origin", "Origin": "http://testserver"}
    assert app.client.post("/api/sync", headers=same).status_code == 200


def test_browser_style_form_posts_with_a_null_origin_are_refused(app):
    # Browsers send "Origin: null" for sandboxed or privacy-stripped requests; never trust it.
    assert app.client.post("/ui/sync", headers={"Origin": "null"}).status_code == 403


def test_unknown_host_headers_are_refused(app):
    assert app.client.get("/healthz", headers={"Host": "evil.example"}).status_code == 400


def test_security_headers_are_set(app):
    headers = app.client.get("/").headers
    assert "frame-ancestors 'none'" in headers["content-security-policy"]
    assert headers["x-content-type-options"] == "nosniff"
    assert headers["referrer-policy"] == "same-origin"
