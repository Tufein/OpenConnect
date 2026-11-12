# Architecture

OpenConnect is a small, layered, test-driven Python application. Everything that touches the
outside world (Garmin, Claude, the filesystem) sits behind a narrow interface so the whole system
runs and is tested offline.

## Stack and why

| Choice | Why |
|--------|-----|
| **Python 3.11+** | Best ecosystem for fitness files (`fitdecode`), first-class Anthropic SDK, trivial to self-host and to script for an IT engineer. TypeScript was the alternative; Python wins on FIT/data handling. |
| **FastAPI + Pydantic v2** | Typed JSON API with free OpenAPI docs at `/api/docs`; Pydantic models double as the domain model. |
| **SQLite (stdlib `sqlite3`)** | One file, no server, easy backups. Single-user workload, so a single serialized connection is enough. Tracks are stored as compressed JSON blobs next to the activity row. |
| **Jinja2 + inline SVG** | Server-rendered pages with no JavaScript build, no CDN and a strict CSP. Charts and route maps are generated as SVG, so the dashboard works fully offline. |
| **httpx** | Garmin OAuth and data calls; `httpx.MockTransport` makes them testable without a network. |
| **Anthropic SDK** | AI reports; client is injected so tests need no key. |
| **pytest, ruff, GitHub Actions** | Tests first, lint and format gates, Python 3.11 to 3.13 matrix. |

## Layers

```
            ┌──────────────── CLI (cli.py) ────────────────┐   ┌── Web (FastAPI) ──┐
            │ serve · sync · import · export · report      │   │ JSON API + HTML UI │
            └───────────────────────┬──────────────────────┘   └─────────┬──────────┘
                                    └──────────────┬─────────────────────┘
                                         AppContext (web/context.py)
        ┌──────────────┬───────────────┬───────────┴────┬───────────────┬───────────────┐
   SyncService   DashboardService   ReportService   formats/ (FIT·GPX·TCX)   achievements/records
        │              │                │
   GarminSource    Repository      ReportProvider
   ├ HttpGarminSource  (SQLite)    ├ TemplateReportProvider (offline)
   │  └ GarminOAuth (PKCE)         └ AnthropicReportProvider (Claude)
   └ FakeGarminSource (demo/tests)
```

* `domain.py` holds the models (`Activity`, `TrackPoint`, `DailySummary`, `SleepSummary`,
  `GeneratedReport`) and pure helpers (haversine, elevation gain with noise hysteresis).
* `garmin/` is the only package that knows Garmin's JSON:
  * `mapper.py` converts Garmin summaries to domain objects (tolerant of missing fields).
  * `client.py` defines `GarminSource` and the HTTP implementation (24 h upload-time windows,
    one refresh-and-retry on 401, 429 with `Retry-After`, 403 means "data type not granted").
  * `oauth.py` implements the authorization-code flow with PKCE (S256). The verifier stays
    server-side, the `state` is single use and expires after 10 minutes, refresh tokens rotate.
  * `ingest.py` stores a payload. **Pull, push webhook and the offline fake all produce the same
    Garmin-shaped payload and share this one ingest path**, so there is no second code path to
    test or to drift.
  * `fake.py` generates deterministic Garmin-shaped data (activities with GPS tracks, dailies,
    sleep, Body Battery). `OPENCONNECT_GARMIN_MODE=mock` (the default) uses it.
* `formats/` converts between files and the domain model. FIT export is a small hand-written
  encoder (header, CRC, `file_id`, `record`, `lap`, `session`, `activity`); tests validate it with
  the independent `fitdecode` parser. XML is parsed with `defusedxml`.
* `services/` is pure business logic over the repository: weekly summaries and trends, personal
  records and local achievements.
* `ai/` builds *facts* (structured, small, hashable) and hands them to a `ReportProvider`.
  `ReportService` stores reports with a hash of their inputs, so an unchanged activity or week is
  never sent to the model twice.
* `web/` wires everything: `app.py` (factory, security middleware), `routes_api.py` (JSON),
  `routes_ui.py` (pages and POST/redirect/GET forms), `charts.py` and `markup.py` (SVG and a
  deliberately tiny, escaping Markdown renderer).

## Data flow

```
Garmin ──pull──▶ HttpGarminSource ─┐
Garmin ──push──▶ /api/garmin/webhook ─┼─▶ ingest_payload ─▶ mapper ─▶ Repository (SQLite)
FakeGarminSource (demo) ───────────┘                                   ▲
FIT/GPX/TCX upload ─▶ formats.import_activity ─────────────────────────┘
Repository ─▶ DashboardService / facts ─▶ UI, API, ReportProvider ─▶ stored report
```

## AI reports

* The provider interface (`ReportProvider.generate(ReportRequest) -> ReportResult`) is the test
  seam. `TemplateReportProvider` is rule-based, deterministic and the default without an API key;
  `AnthropicReportProvider` calls Claude with an injected client.
* Defaults: model `claude-opus-5-5`, effort `medium`, both configurable. Adaptive thinking is on
  by model default. Requests set `fallbacks: "default"` so a safety-classifier refusal is retried
  on a fallback model server-side; a refusal that survives that becomes a clear `ReportError`.
* Prompt safety: activity names and other free text are untrusted. They travel only inside a JSON
  data block, and the system prompt states that the block is data, not instructions. The rendered
  report is HTML-escaped by the Markdown renderer.
* Privacy: only the facts for the chosen activity or week are sent, and only when the user asks.

## Security model (v0.1.0)

OpenConnect has **no login**. It is meant for `localhost` or a trusted network, behind a reverse
proxy with authentication if exposed. What it does enforce:

* binds to `127.0.0.1` by default; `Host` header allow-list (DNS-rebinding defence);
* rejects cross-origin state-changing requests (an `Origin` that differs from `Host`), so another
  web page cannot trigger syncs or billed AI calls;
* strict Content-Security-Policy (no inline script or style), `nosniff`, `Referrer-Policy: same-origin`, no framing;
* uploads are size-limited, FIT files are CRC-checked, XML is parsed without entity expansion;
* the Garmin webhook can require a shared-secret token; secrets come from the environment.

OAuth tokens are stored in the SQLite file in plain text; protect the file like a password.

## Test strategy

Tests were written before the code for each layer.

| Level | Where | What |
|-------|-------|------|
| Unit | `tests/unit` | Domain maths, format round-trips (cross-checked with `fitdecode`), mapper, OAuth/PKCE (RFC 7636 vector), HTTP source with `MockTransport`, ingest, sync, services, AI providers with a fake Anthropic client, CLI |
| Integration | `tests/integration` | The whole app through FastAPI's `TestClient`: API, HTML pages, webhook, OAuth flow, request guards |

No test needs the network, a Garmin account or an API key. CI runs ruff, the format check and the
suite on Python 3.11, 3.12 and 3.13 and builds the wheel.

## Extending

* **A new Garmin data type**: add a mapper function, store it in `ingest_payload`, add it to
  `KINDS` in `garmin/client.py`, and extend `garmin/fake.py` so demos and tests cover it.
* **A new AI provider**: implement `ReportProvider` and select it in `build_provider`.
* **A new file format**: add a module under `formats/` with `export_x`/`parse_x` and register it
  in `formats/__init__.py`.
