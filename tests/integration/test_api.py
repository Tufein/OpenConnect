import httpx
import pytest

from openconnect.formats import export_activity
from tests.factories import make_activity
from tests.fakes import FakeAnthropic


def test_healthz(app):
    assert app.client.get("/healthz").json() == {"status": "ok", "version": "1.13.3"}


def test_password_protects_pages_and_api(make_app):
    app = make_app(password="secret")
    assert app.client.get("/healthz").status_code == 200
    assert app.client.get("/", follow_redirects=False).status_code == 303
    assert app.client.get("/api/activities").status_code == 401

    wrong = app.client.post("/ui/login", data={"password": "nope"}, follow_redirects=False)
    assert wrong.status_code == 303 and wrong.headers["location"] == "/login?error=1"
    good = app.client.post("/ui/login", data={"password": "secret"}, follow_redirects=False)
    assert good.status_code == 303 and good.headers["location"] == "/"
    assert "oc_session=" in good.headers["set-cookie"]
    assert app.client.get("/api/activities").status_code == 200
    assert "password required" in app.client.get("/settings").text

    out = app.client.post("/ui/logout", follow_redirects=False)
    assert out.status_code == 303 and out.headers["location"] == "/login"
    assert app.client.get("/", follow_redirects=False).status_code == 303
    app.client.cookies.set("oc_session", "forged.token")
    assert app.client.get("/", follow_redirects=False).status_code == 303


def test_login_works_with_a_password_that_has_accents(make_app):
    app = make_app(password="wachtwoord€é")
    wrong = app.client.post("/ui/login", data={"password": "wachtwoord€e"}, follow_redirects=False)
    assert wrong.status_code == 303 and wrong.headers["location"] == "/login?error=1"
    good = app.client.post("/ui/login", data={"password": "wachtwoord€é"}, follow_redirects=False)
    assert good.status_code == 303 and good.headers["location"] == "/"
    assert app.client.get("/api/activities").status_code == 200
    # a cookie with characters outside ASCII (raw bytes, as a hostile client can send) is refused,
    # not a server error
    app.client.cookies.clear()
    raw = {"cookie": "oc_session=1.é".encode("latin-1")}
    assert app.client.get("/api/activities", headers=raw).status_code == 401


def test_the_login_cookie_is_secure_only_behind_https(make_app):
    plain = make_app(password="secret")
    cookie = plain.client.post("/ui/login", data={"password": "secret"}, follow_redirects=False)
    assert "secure" not in cookie.headers["set-cookie"].lower()
    assert "httponly" in cookie.headers["set-cookie"].lower()

    behind_proxy = make_app(password="secret", base_url="https://oc.example")
    cookie = behind_proxy.client.post(
        "/ui/login",
        data={"password": "secret"},
        follow_redirects=False,
        headers={"host": "oc.example"},
    )
    assert "secure" in cookie.headers["set-cookie"].lower()


def test_refusals_carry_the_security_headers_too(make_app):
    app = make_app(password="secret")
    for response in (
        app.client.get("/api/activities"),  # 401 from the login check
        app.client.get("/", follow_redirects=False),  # redirect to the login page
        app.client.post("/ui/sync", headers={"origin": "https://evil.example"}),  # 403, cross-site
    ):
        assert response.status_code in {401, 303, 403}
        assert response.headers["x-content-type-options"] == "nosniff"
        assert "frame-ancestors 'none'" in response.headers["content-security-policy"]
        assert response.headers["x-frame-options"] == "DENY"


# --------------------------------------------------------------------- sync and activities


def test_sync_in_mock_mode_populates_everything(app):
    body = app.client.post("/api/sync", params={"days_back": 28}).json()
    assert body["activities"] >= 8 and body["dailies"] >= 27 and body["sleeps"] >= 27
    assert body["hrv"] >= 27 and body["pulseox"] >= 27 and body["respiration"] >= 27
    assert body["body_comps"] >= 6
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


def test_health_vitals_and_body_composition_endpoints(synced):
    c = synced.client
    vitals = c.get("/api/health/vitals", params={"start": "2026-05-20", "end": "2026-05-25"}).json()
    assert [v["date"] for v in vitals] == [f"2026-05-{d}" for d in range(20, 26)]
    first = vitals[0]
    assert first["hrv_last_night_ms"] > 0 and first["hrv_weekly_avg_ms"] > 0
    assert first["hrv_status"] in {"BALANCED", "LOW"}
    assert 90 <= first["spo2_avg_pct"] <= 100
    assert first["spo2_min_pct"] <= first["spo2_avg_pct"]
    assert 8 <= first["respiration_avg_brpm"] <= 25
    assert first["respiration_min_brpm"] <= first["respiration_max_brpm"]
    assert first["vo2max"] > 40 and first["fitness_age"] > 20
    assert first["systolic_bp"] > first["diastolic_bp"] and first["skin_temp_c"] > 30

    body = c.get(
        "/api/health/body-composition", params={"start": "2026-05-01", "end": "2026-05-31"}
    ).json()
    assert len(body) >= 6
    assert body[0]["measured_at"] < body[-1]["measured_at"]
    assert 60 < body[-1]["weight_kg"] < 90 and body[-1]["device_name"] == "Index S2"
    assert (
        c.get(
            "/api/health/body-composition", params={"start": "2026-05-31", "end": "2026-05-01"}
        ).status_code
        == 422
    )


def test_series_endpoint(synced):
    body = synced.client.get("/api/health/series", params={"day": "2026-05-30"}).json()
    assert "stress" in body and "body_battery" in body
    assert all(0 <= value <= 100 for value in body["body_battery"].values())
    assert synced.client.get("/api/health/series", params={"day": "nope"}).status_code == 422
    snapshot = synced.client.get("/api/health/series", params={"day": "2026-05-27"}).json()
    assert "health_snapshot" in snapshot and "epoch_summary" in snapshot


def test_dashboard_endpoints(synced):
    c = synced.client
    week = c.get("/api/dashboard/weekly", params={"week": "2026-05-20"}).json()
    assert week["week_start"] == "2026-05-18" and week["activity_count"] >= 3
    overview = c.get("/api/dashboard/overview").json()
    assert overview["today"]["date"] == "2026-05-31" and len(overview["trend"]) == 30
    assert overview["this_week"]["week_start"] == "2026-05-25"
    assert overview["trend"][-1]["hrv_last_night_ms"] > 0
    assert overview["this_week"]["avg_spo2_pct"] > 90


def test_activity_splits_and_best_efforts(synced):
    c = synced.client
    run = c.get("/api/activities", params={"sport": "running"}).json()["items"][0]
    splits = c.get(f"/api/activities/{run['id']}/splits").json()
    assert splits and splits[0]["km"] == 1 and splits[0]["seconds_s"] > 0
    efforts = c.get("/api/records/best-efforts").json()
    assert "1000" in efforts and efforts["1000"]["seconds_s"] > 0
    assert efforts["1000"]["activity_id"].startswith("garmin:")


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
    assert response.json()["ingested"] == {
        "activities": 0,
        "dailies": 1,
        "sleeps": 1,
        "hrv": 0,
        "respiration": 0,
        "pulseox": 0,
        "body_comps": 0,
        "extra_metrics": 0,
    }
    days = app.client.get(
        "/api/health/daily", params={"start": "2026-05-30", "end": "2026-05-30"}
    ).json()
    assert days[0]["steps"] == 4321


def test_webhook_ingests_pushed_vitals_and_weigh_ins(app):
    response = app.client.post(
        "/api/garmin/webhook",
        json={
            "hrv": [
                {
                    "summaryId": "h1",
                    "calendarDate": "2026-05-30",
                    "lastNightAvg": 61,
                    "weeklyAvg": 57,
                    "status": "BALANCED",
                }
            ],
            "pulseox": [
                {
                    "summaryId": "px1",
                    "calendarDate": "2026-05-30",
                    "timeOffsetSpo2Values": {"0": 96, "3600": 94},
                }
            ],
            "allDayRespiration": [
                {
                    "summaryId": "r1",
                    "calendarDate": "2026-05-30",
                    "timeOffsetRespirationValues": {"0": 14, "3600": 16},
                }
            ],
            "bodyComps": [
                {
                    "summaryId": "bc1",
                    "calendarDate": "2026-05-30",
                    "weightInGrams": 74000,
                    "bodyFatInPercent": 18.0,
                }
            ],
        },
    )
    assert response.status_code == 200
    assert response.json()["ingested"]["body_comps"] == 1
    assert response.json()["ingested"]["hrv"] == 1
    vitals = app.client.get(
        "/api/health/vitals", params={"start": "2026-05-30", "end": "2026-05-30"}
    ).json()
    assert vitals[0]["hrv_last_night_ms"] == 61 and vitals[0]["spo2_min_pct"] == 94.0
    body = app.client.get(
        "/api/health/body-composition", params={"start": "2026-05-30", "end": "2026-05-30"}
    ).json()
    assert body[0]["weight_kg"] == 74.0 and body[0]["body_fat_pct"] == 18.0


def test_webhook_follows_an_allowed_callback(app, monkeypatch):
    class FakeClient:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def get(self, url):
            return httpx.Response(
                200,
                json={
                    "dailies": [{"summaryId": "cb", "calendarDate": "2026-05-29", "steps": 1234}]
                },
            )

    monkeypatch.setattr("openconnect.web.routes_api.httpx.AsyncClient", FakeClient)
    body = app.client.post(
        "/api/garmin/webhook",
        json={"dailies": [{"callbackURL": "https://apis.garmin.com/wellness-api/rest/x"}]},
    ).json()
    assert body["ping_notifications"] == 1 and body["callbacks_followed"] == 1
    assert body["callback_errors"] == []
    days = app.client.get(
        "/api/health/daily", params={"start": "2026-05-29", "end": "2026-05-29"}
    ).json()
    assert days[0]["steps"] == 1234


def test_webhook_refuses_a_foreign_callback(app, monkeypatch):
    class FakeClient:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def get(self, url):
            raise AssertionError("a foreign callback must never be fetched")

    monkeypatch.setattr("openconnect.web.routes_api.httpx.AsyncClient", FakeClient)
    body = app.client.post(
        "/api/garmin/webhook",
        json={"dailies": [{"callbackURL": "https://evil.example/x"}]},
    ).json()
    assert body["callbacks_followed"] == 0
    assert "not allowed" in body["callback_errors"][0]


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


def test_personal_mode_syncs_with_a_key(make_app, monkeypatch):
    class Source:
        skipped = {}

        def fetch(self, start, end):
            return {
                "dailies": [{"calendarDate": "2026-05-30", "steps": 4321, "stepsGoal": 10000}],
                "hrv": [{"calendarDate": "2026-05-30", "lastNightAvg": 55}],
            }

    monkeypatch.setattr("openconnect.web.context.PersonalApiSource", lambda key: Source())
    app = make_app(garmin_mode="personal", garmin_personal_key="key")
    assert app.client.get("/api/garmin/status").json() == {"mode": "personal", "connected": True}
    assert app.client.get("/api/garmin/connect", follow_redirects=False).status_code == 409
    result = app.client.post("/api/sync").json()
    assert result["dailies"] == 1 and result["hrv"] == 1 and result["errors"] == []
    day = app.client.get(
        "/api/health/daily", params={"start": "2026-05-30", "end": "2026-05-30"}
    ).json()[0]
    assert day["steps"] == 4321 and day["hrv_last_night_ms"] == 55
    assert "personal key" in app.client.get("/settings").text


def test_backfill_endpoint_and_ui(app):
    app.ctx.source.request_backfill = lambda kind, start, end: 3
    body = app.client.post(
        "/api/garmin/backfill", params={"kind": "dailies", "days_back": 30}
    ).json()
    assert body == {"kind": "dailies", "requests": 3}
    assert app.client.post("/api/garmin/backfill", params={"kind": "nope"}).status_code == 422
    done = app.client.post(
        "/ui/backfill", data={"kind": "dailies", "days_back": 30}, follow_redirects=False
    )
    assert done.status_code == 303 and done.headers["location"] == "/settings?backfill=1"
    assert "asked to resend" in app.client.get(done.headers["location"]).text


def test_backfill_unavailable_in_mock_mode(app):
    assert app.client.post("/api/garmin/backfill", params={"kind": "dailies"}).status_code == 409


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
