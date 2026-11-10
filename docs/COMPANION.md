# Optional AI companion (oiep-backendAI)

This repository contains an **optional, self-contained component** that connects OIEP (Spring
Boot) to [oiep-backendAI](https://github.com/Coffet/oiep-backendAI) — a separate, local-first
FastAPI service that provides a form-filling chat helper and read-only corpus analysis.

The component is **off by default**. With `OIEP_COMPANION_ENABLED=false` (the default) no
component beans, routes, page fragments or CSP directives exist: OIEP serves the same pages, routes
and security headers as before the component was added. (The component's static JS/CSS files remain
on the classpath and are still served until they are deleted; nothing references them — see
[Soft-disable](#b-soft-disable-the-normal-way-to-turn-it-off).) The component was written so that it
can be deleted again without touching the rest of the application; the exact procedure is in
[Adding and removing the component](#adding-and-removing-the-component).

> Short version: the companion is a **separate service you run next to OIEP**. OIEP never embeds
> model or chat logic, and the companion never writes to OIEP. The only data that crosses the
> boundary is (a) the user-helper widget loaded by the visitor's browser from the companion's own
> origin, and (b) already-published experiences that the companion pulls read-only through a
> token-protected API.

Contents:

1. [What the component adds](#what-the-component-adds)
2. [Architecture and trust boundaries](#architecture-and-trust-boundaries)
3. [Configuration reference](#configuration-reference)
4. [Quick start (local)](#quick-start-local)
5. [Docker Compose overlay](#docker-compose-overlay)
6. [Read-only experiences API](#read-only-experiences-api)
7. [Mapping: OIEP report → InterviewExperience v1](#mapping-oiep-report--interviewexperience-v1)
8. [Tombstones](#tombstones)
9. [Security notes](#security-notes)
10. [Privacy notes](#privacy-notes)
11. [Adding and removing the component](#adding-and-removing-the-component)
12. [Troubleshooting](#troubleshooting)
13. [Limitations](#limitations)

---

## What the component adds

Three surfaces, each with its own switch and its own failure mode. They share the master switch
but fail closed independently: a misconfigured sub-feature registers **no** beans at all (no
route, no page fragment, no CSP change), and the startup log says why.

| Surface | Where | Master switch + sub-switch | Default | Requires |
| --- | --- | --- | --- | --- |
| **User helper** | Optional chat widget on the public form page (`/`) | `OIEP_COMPANION_ENABLED` + `OIEP_COMPANION_USER_HELPER_ENABLED` | master off; user helper on *within* the enabled component | a browser-reachable companion **widget URL** |
| **Read-only experiences API** | `GET /api/v1/experiences` for the companion's admin corpus sync | + `OIEP_COMPANION_READ_API_ENABLED` | off | a read-only bearer token (`OIEP_COMPANION_READ_TOKEN`) |
| **Operator admin helper** | `GET /admin/assistant` in the existing admin area, server-side proxy to `/v1/admin/*` | + `OIEP_COMPANION_ADMIN_HELPER_ENABLED` | off | companion base URL + operator bearer token (`OIEP_COMPANION_ADMIN_TOKEN`) |

What the component deliberately does **not** do:

- No chat/model/embedding code, no model weights, no prompts and no AI client libraries in this
  repository. OIEP only renders an iframe and proxies fixed admin routes.
- No writes to OIEP from the companion. The companion's corpus mirror is read-only and is
  populated by pulling `GET /api/v1/experiences`.
- No new dependency: the admin proxy uses the JDK `java.net.http` client, the user helper uses
  one vanilla JavaScript file and the read API uses the existing SQLite/Jackson stack.

---

## Architecture and trust boundaries

```
  visitor's browser                                  operator's browser
  ┌─────────────────────────────┐                    ┌──────────────────────────────┐
  │ OIEP page (public origin)   │                    │ OIEP admin pages             │
  │                             │                    │ /admin/assistant (same       │
  │  <iframe src="<widget-url>">│                    │  origin, admin session+CSRF) │
  └──────────┬──────────────────┘                    └──────────────┬───────────────┘
             │ iframe loads the widget origin                      │ fetch, same origin
             │ browser talks to the companion DIRECTLY             │
             │ (short-lived session token in widget memory)        ▼
             ▼                                               OIEP (Spring Boot)
  oiep-backendAI  ◄──────── server-side admin proxy ───────────────┘
  (FastAPI, one process)      OIEP → companion /v1/admin/*  [operator bearer token]
             │
             │  read-only pull on the OIEP read API
             ▼
  OIEP  GET /api/v1/experiences   [Bearer read-only token]
```

| Boundary | Who can call it | Credential | Notes |
| --- | --- | --- | --- |
| Companion widget + public user API | any visitor's browser | none for public reads; a 256-bit session token for that visitor's chat | the widget calls the companion origin directly; OIEP only embeds its URL |
| Companion admin API (`/v1/admin/*`) | the companion's operator | `OIEP_AI_ADMIN_TOKEN` bearer | never exposed to a browser; OIEP proxies it server-side and the public proxy must block it |
| OIEP read API (`/api/v1/experiences`) | the companion service | `OIEP_COMPANION_READ_TOKEN` bearer | read-only, published reports only, no CORS |
| OIEP operator page (`/admin/assistant`) | an authenticated OIEP admin | existing admin session + per-session CSRF token | the browser never sees the companion token |

Consequences worth remembering:

- The user helper is cross-origin: the iframe is loaded from the companion origin. OIEP's CSP
  therefore gains **one extra directive** — `frame-src 'self' <widget-origin>` — containing exactly
  the configured origin (never a wildcard).
- The admin helper is same-origin from the browser's point of view. It only works while the OIEP
  admin session is valid; POSTs additionally require the `X-OIEP-CSRF` header.
- The companion's admin token never reaches a browser, a JSP, a JavaScript file or a log line.

### User-helper bridge (postMessage)

The iframe is a complete widget on its own; OIEP only adds two progressive enhancements:

1. **Embed** — the page includes the configured widget URL in a sandboxed iframe
   (`allow-scripts allow-forms allow-same-origin`, `loading="lazy"`,
   `referrerpolicy="no-referrer"`). The visitor chats inside the frame; OIEP sees no messages.
2. **Prefill** — a "Use the helper's answers" button on the OIEP page asks the widget for the
   reviewed draft and fills matching form fields. The hand-off is an origin-checked
   `postMessage` conversation:

   | Direction | Message |
   | --- | --- |
   | OIEP page → widget | `{type: "oiep-ai:request-prefill", requestId: "<random>"}` posted to the **exact widget origin** |
   | Widget → OIEP page | `{type: "oiep-ai:prefill", requestId, payload}` where `payload` is the validated `InterviewExperience` v1 object, or `{type: "oiep-ai:prefill-unavailable", requestId, reason}` when the chat is not finished |

   The OIEP script accepts a message only when `event.origin` equals the configured widget origin
   **and** `event.source` is the embedded iframe's window; stale replies are ignored and a request
   times out after 12 seconds. Prefill is deliberately conservative: only **empty** text fields are
   filled, a radio group is set only when nothing is selected, checkbox topics are only added (never
   removed), unmapped/unknown values are skipped, and long values that would fail the form's
   `maxlength` are left for the visitor. **Nothing is submitted** — the visitor reviews and submits
   through the normal OIEP flow.

   This bridge needs no secret and no CORS: the widget has its own origin and is asked for data the
   visitor already produced in it.

   Prefill maps the payload onto the form controls conservatively (the authoritative mapping block
   is marked `PREFILL MAPPING` in `static/js/companion.js`; adapt that one block if the form
   changes):

   | Payload field | OIEP control | Rule |
   | --- | --- | --- |
   | `company` | `#company` | fill only when empty; skipped if longer than `maxlength` |
   | `role_title` | `#role` | fill only when empty; skipped if > `maxlength` |
   | `location` | `#location` | fill only when empty; skipped if > `maxlength` |
   | `overall_experience` | `#story` | fill only when empty; skipped if > `maxlength` |
   | `work_mode` | radio `workMode` | `ONSITE`/`HYBRID`/`REMOTE`; only when no option is selected |
   | `outcome` | radio `outcome` | `OFFER`/`REJECTED`/`WITHDRAWN`/`PENDING`/`NO_RESPONSE`; only when no option is selected |
   | `rounds[lowest index].type` | radio `round` | `SCREEN`/`PHONE` → `phone`, `TECHNICAL`, `TAKE_HOME`, `SYSTEM_DESIGN`, `BEHAVIORAL`, `ONSITE`/`HR`/`OTHER` → `other`; only when no option is selected |
   | `rounds[].topics` | `questionTypes` checkboxes | case-insensitive keyword match to `algorithm`, `systemdesign`, `takehome`, `behavioral`, `domain`, `culture`, `casestudy`; additive only (never unchecks) and it opens the collapsed extras panel so the visitor sees what was added |

   Unknown values are ignored; values that would exceed a field's `maxlength` are left empty and
   reported in the status line ("N answer(s) could not be filled in automatically"). The status
   region reports checking / N fields filled / unchanged (nothing overwritten) / no match / skipped
   / chat-not-finished / helper-unavailable.

### Admin-helper proxy routes

While the admin helper is configured, these same-origin routes exist under the existing admin
session (login required, `no-store`). POSTs additionally require the `X-OIEP-CSRF` header with the
per-session token (a CSRF token in the URL query string is rejected, because URLs end up in
history and proxy logs); bodies are JSON, at most 64 KB, and are reduced to an allowlist before
they are forwarded:

| Route | Companion route | Notes |
| --- | --- | --- |
| `GET /admin/assistant` | — | the operator page |
| `GET /admin/assistant/api/health` | `GET /v1/admin/health` | status, corpus count, model, tools |
| `GET /admin/assistant/api/corpus/stats` | `GET /v1/admin/corpus/stats` | corpus counts and last sync |
| `GET /admin/assistant/api/corpus/sync` | `GET /v1/admin/corpus/sync` | sync state |
| `POST /admin/assistant/api/corpus/sync` | `POST /v1/admin/corpus/sync` | incremental (`{}`) or full (`{"full":true}`) |
| `POST /admin/assistant/api/chat` | `POST /v1/admin/chat` | `{message, conversation_id?}`; 4000-character message cap |
| `POST /admin/assistant/api/query` | `POST /v1/admin/query` | allowlisted query DSL fields only |
| `POST /admin/assistant/api/summarise` | `POST /v1/admin/summarise` | `{filters?, focus?, limit?}` |
| `GET /admin/assistant/api/audit?limit=` | `GET /v1/admin/audit` | limit parsed and clamped to 1–200 server-side |

Error responses are a small JSON envelope (`{"error":{"code":…,"message":…}}`): `403 csrf_invalid`,
`415 unsupported_media_type`, `400 invalid_request`, `413 body_too_large`, and
`502 companion_unavailable` / `502 companion_auth_failed` when the service is down or rejects the
token. Companion 4xx validation errors are passed through as JSON because they are useful operator
feedback; 5xx, redirects and authentication failures are never echoed.

---

## Configuration reference

Every OIEP-side setting is exposed as an environment variable through
[`src/main/resources/application.properties`](../src/main/resources/application.properties).
Values copied verbatim from this document or `.env.example` as tokens are detected and rejected
(they are treated as "not configured", never as a password).

| Property | Environment variable | Default | Purpose | Matching companion-side setting |
| --- | --- | --- | --- | --- |
| `oiep.companion.enabled` | `OIEP_COMPANION_ENABLED` | `false` | master switch; `false` = component completely inert | — |
| `oiep.companion.base-url` | `OIEP_COMPANION_BASE_URL` | *(empty)* | companion **origin** used by the OIEP server (plain origin, optional trailing `/`, no path/query); `http(s)://host[:port]` | `OIEP_AI_PUBLIC_BASE_URL` (informational) |
| `oiep.companion.user-helper.enabled` | `OIEP_COMPANION_USER_HELPER_ENABLED` | `true` | render the widget block on the public form page | — |
| `oiep.companion.user-helper.widget-url` | `OIEP_COMPANION_WIDGET_URL` | `<base-url>/ui/` | full browser-visible iframe URL; its **origin** becomes the CSP `frame-src` entry | — (served at `/ui/` when `OIEP_AI_UI_ENABLED=true`) |
| `oiep.companion.admin-helper.enabled` | `OIEP_COMPANION_ADMIN_HELPER_ENABLED` | `false` | expose `/admin/assistant` and its same-origin proxy | — |
| `oiep.companion.admin-helper.token` | `OIEP_COMPANION_ADMIN_TOKEN` | *(empty)* | companion operator bearer token; server-side only | `OIEP_AI_ADMIN_TOKEN` |
| `oiep.companion.read-api.enabled` | `OIEP_COMPANION_READ_API_ENABLED` | `false` | expose `GET /api/v1/experiences` | — |
| `oiep.companion.read-api.token` | `OIEP_COMPANION_READ_TOKEN` | *(empty)* | read-only bearer token presented by the companion | `OIEP_AI_OIEP_SYNC_TOKEN` |
| `oiep.companion.read-api.page-size` | `OIEP_COMPANION_READ_PAGE_SIZE` | `200` | default page size (clamped 1–500; the service maximum) | the companion requests pages of at most 500 |
| `oiep.companion.read-api.difficulty` | `OIEP_COMPANION_READ_DIFFICULTY` | `MODERATE` | value emitted for every item when difficulty mode is `constant` (see [the difficulty limitation](#the-difficulty-limitation-read-this-before-trusting-difficulty-analytics)) | — |
| `oiep.companion.read-api.difficulty-mode` | `OIEP_COMPANION_READ_DIFFICULTY_MODE` | `constant` | `constant` or `vibe-proxy` | — |
| `oiep.companion.http.connect-timeout-ms` | `OIEP_COMPANION_CONNECT_TIMEOUT_MS` | `3000` | admin proxy connect timeout | — |
| `oiep.companion.http.request-timeout-ms` | `OIEP_COMPANION_REQUEST_TIMEOUT_MS` | `15000` | admin proxy request timeout | — |

The matching companion-side settings you will actually set (see the companion's
`.env.example` and `docs/DEPLOYMENT.md`):

| Companion variable | Default | Notes |
| --- | --- | --- |
| `OIEP_AI_ENV` | `dev` | set `prod` for a public deployment: the companion then fails closed on weak secrets and requires `https://` for CORS/sync URLs |
| `OIEP_AI_ADMIN_TOKEN` | dev default | must equal `OIEP_COMPANION_ADMIN_TOKEN`; 32+ random characters in prod |
| `OIEP_AI_IP_HASH_SALT` | dev default | prod-required; the companion stores only salted IP hashes |
| `OIEP_AI_OIEP_BASE_URL` | *(empty)* | **public** HTTPS URL of your OIEP instance, e.g. `https://oiep.example` (prod requires `https://`) |
| `OIEP_AI_OIEP_SYNC_TOKEN` | *(empty)* | must equal `OIEP_COMPANION_READ_TOKEN` |
| `OIEP_AI_OIEP_EXPERIENCES_PATH` | `/api/v1/experiences` | the read API path |
| `OIEP_AI_SYNC_ENABLED` | `false` | background sync; the operator can always trigger a sync from `/admin/assistant` |
| `OIEP_AI_UI_ENABLED` | `true` | serve the widget at `/ui/` |
| `OIEP_AI_CORS_ORIGINS` | *(empty)* | only needed if the widget is hosted off-origin; the iframe embed does **not** need CORS |

### Tokens: generate real ones

Two distinct secrets. Never reuse the admin password, the tunnel token or one token for the
other side of a pair.

```bash
python3 -c 'import secrets; print(secrets.token_urlsafe(48))'   # OIEP_COMPANION_ADMIN_TOKEN = OIEP_AI_ADMIN_TOKEN
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'   # OIEP_COMPANION_READ_TOKEN  = OIEP_AI_OIEP_SYNC_TOKEN
```

OIEP rejects tokens that are empty, shorter than 16 characters, longer than 512 characters, or
equal to a placeholder documented in this repository (`<paste-the-operator-token>`,
`change-me`, …). The companion additionally requires a 32+ character admin token and a 16+
character IP-hash salt when `OIEP_AI_ENV=prod`.

---

## Quick start (local)

The companion runs as a separate process. It needs Python 3.11+ (or Docker) and roughly
150 MB of RAM; no GPU and no cloud LLM.

### 1. Start the companion

Follow the companion's own quick start (its
[README](https://github.com/Coffet/oiep-backendAI#quick-start-development)):

```bash
cd ../oiep-backendAI
python -m venv .venv
. .venv/bin/activate                       # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt

export OIEP_AI_ENV=dev
export OIEP_AI_ADMIN_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
export OIEP_AI_IP_HASH_SALT="$(python -c 'import secrets; print(secrets.token_urlsafe(16))')"

uvicorn app.main:app --host 127.0.0.1 --port 8090 --workers 1
```

Run exactly **one** worker (rate limiting and session state are in-process in v1). Check the
widget at <http://127.0.0.1:8090/ui/> and the API at <http://127.0.0.1:8090/readyz>.

### 2. Point OIEP at it

Bare metal, in the shell that starts OIEP. On Windows use `$env:NAME = "value"` instead of
`export`.

```bash
# needed to use /admin/assistant; see the main README
export OIEP_ADMIN_PASSWORD='<your long admin password>'

export OIEP_COMPANION_ENABLED=true
export OIEP_COMPANION_BASE_URL=http://127.0.0.1:8090
export OIEP_COMPANION_USER_HELPER_ENABLED=true
export OIEP_COMPANION_WIDGET_URL=http://127.0.0.1:8090/ui/
export OIEP_COMPANION_ADMIN_HELPER_ENABLED=true
export OIEP_COMPANION_ADMIN_TOKEN='<the companion OIEP_AI_ADMIN_TOKEN>'

# Optional: read API + corpus sync. The same value must be set on the companion as
# OIEP_AI_OIEP_SYNC_TOKEN, and OIEP_AI_OIEP_BASE_URL must point back at this OIEP instance.
export OIEP_COMPANION_READ_API_ENABLED=true
export OIEP_COMPANION_READ_TOKEN='<a generated read token>'

./mvnw spring-boot:run
```

For the corpus-sync part of the quick start, also restart the companion with the read-only sync
configuration (in `OIEP_AI_ENV=dev` the OIEP URL may be plain HTTP; in `prod` it must be HTTPS):

```bash
export OIEP_AI_OIEP_BASE_URL=http://127.0.0.1:8080
export OIEP_AI_OIEP_SYNC_TOKEN='<same value as OIEP_COMPANION_READ_TOKEN>'
uvicorn app.main:app --host 127.0.0.1 --port 8090 --workers 1
```

### 3. Verify

| Check | How | Expected |
| --- | --- | --- |
| Component is wired | the OIEP startup log | `AI companion component enabled: … userHelper=true adminHelper=true readApi=true` |
| User helper | open <http://localhost:8080/> and look under the form | the optional helper block with an embedded widget |
| CSP | `curl -sI http://localhost:8080/` | `frame-src 'self' http://127.0.0.1:8090` in `Content-Security-Policy` |
| Admin helper | sign in at `/admin` and open `/admin/assistant` | status card loads when the companion is up |
| Read API | the curl below | `{"items":[...],"next_cursor":…,"has_more":false}` |
| Corpus sync | `/admin/assistant` → "Sync now" (or `POST /v1/admin/corpus/sync` on the companion) | `inserted`/`updated` counts and a rising "Mirrored experiences" count |

```bash
curl -sS -H "Authorization: Bearer $OIEP_COMPANION_READ_TOKEN" \
  "http://127.0.0.1:8080/api/v1/experiences?page_size=2"
```

---

## Docker Compose overlay

The base [`compose.yaml`](../compose.yaml) is untouched. [`compose.companion.yaml`](../compose.companion.yaml)
is an **optional overlay** that adds an `ai` service and adds the `OIEP_COMPANION_*` environment
variables to the existing `app` service:

```bash
# .env: OIEP_COMPANION_ADMIN_TOKEN and OIEP_AI_IP_HASH_SALT are REQUIRED (see below);
# compile the effective configuration first — this fails fast if a secret is missing.
docker compose -f compose.yaml -f compose.companion.yaml config >/dev/null
docker compose -f compose.yaml -f compose.companion.yaml up -d --build
docker compose -f compose.yaml -f compose.companion.yaml ps
docker compose -f compose.yaml -f compose.companion.yaml logs -f ai
```

**Before the first `up`:** copy `.env.example` to `.env` and set
`OIEP_COMPANION_ADMIN_TOKEN` (32+ random characters), `OIEP_AI_IP_HASH_SALT` (16+ random
characters) and — for sync — `OIEP_COMPANION_READ_TOKEN` (16+ random characters). The overlay
marks the token and salt as required, so `docker compose config`/`up` stop with a clear
"required variable … is missing a value" error instead of starting a companion container that
fails on every request with `ValueError: OIEP_AI_ADMIN_TOKEN is required in production` (the
companion creates its app lazily, so a missing prod secret surfaces as HTTP 500s from `/healthz`
rather than a startup exit). Generate the secrets with:

```bash
python3 -c 'import secrets; print("OIEP_COMPANION_ADMIN_TOKEN=" + secrets.token_urlsafe(48))'
python3 -c 'import secrets; print("OIEP_COMPANION_READ_TOKEN=" + secrets.token_urlsafe(32))'
python3 -c 'import secrets; print("OIEP_AI_IP_HASH_SALT=" + secrets.token_urlsafe(32))'
```

How the overlay wires things:

- `ai` builds from a **sibling checkout** of the companion at `../oiep-backendAI` (override the
  checkout path with `OIEP_AI_BUILD_CONTEXT`). Clone it next to `oiep/`:
  `git clone https://github.com/Coffet/oiep-backendAI ../oiep-backendAI`. To use a prebuilt image
  instead, set `OIEP_AI_IMAGE` to it, make sure it is present locally (e.g.
  `docker compose -f compose.yaml -f compose.companion.yaml pull ai`), and run `up -d` without
  `--build` — otherwise Compose rebuilds from the build context.
- `app` reaches the companion over the private Compose network at `http://ai:8090`
  (`OIEP_COMPANION_BASE_URL`), so the admin proxy never crosses the public internet.
- The companion's SQLite file lives in the `oiep-ai-data` volume.
- The companion port is published on **loopback only** (`127.0.0.1:8090`) for operator curl/SSH
  tunnels; it is never published on `0.0.0.0`.
- The same `.env` serves both services: the overlay maps `OIEP_COMPANION_ADMIN_TOKEN` →
  `OIEP_AI_ADMIN_TOKEN` and `OIEP_COMPANION_READ_TOKEN` → `OIEP_AI_OIEP_SYNC_TOKEN`, so each token
  is set once.
- `OIEP_AI_ENV=prod` refuses a plain-HTTP `OIEP_AI_OIEP_BASE_URL`, so a corpus sync points at the
  **public HTTPS OIEP URL** (`OIEP_AI_OIEP_BASE_URL=https://oiep.example`), not at
  `http://app:8080`. Leave it empty to run without sync.
- The overlay defaults `OIEP_AI_TRUSTED_PROXIES` to the private Docker ranges because requests from
  the `cloudflared` container arrive on the Compose network; that lets the companion read the real
  visitor address from `X-Forwarded-For` for per-IP rate limits and hashed-IP audit rows. If you
  leave the companion's stock default (`127.0.0.1/32,::1/128`), every visitor appears to come from
  the tunnel container and shares one rate-limit bucket.
- The overlay does **not** include the optional local Ollama model; the default
  `structured-extractive` backend needs no model. See the companion's own deployment docs for
  the optional local model.

### Browser-reachable widget URL (important)

`OIEP_COMPANION_BASE_URL` must stay the **internal** origin (`http://ai:8090`) because the admin
proxy uses it, but the user-helper iframe is loaded by the **visitor's browser**, which cannot
resolve `ai`. For the widget to work, set `OIEP_COMPANION_WIDGET_URL` to a public URL of the
companion widget:

- **Cloudflare Tunnel deployment:** add a second public hostname (e.g. `ai.oiep.example`) in
  the Cloudflare tunnel pointing at `http://ai:8090` (the `cloudflared` container resolves the
  Compose service name). Then set `OIEP_COMPANION_WIDGET_URL=https://ai.oiep.example/ui/`.
  **Block `/v1/admin/` on that public hostname** before enabling it — add a more specific public
  hostname rule for path `v1/admin/*` returning `http_status:404`, or a Cloudflare WAF custom rule
  blocking `(http.host eq "ai.oiep.example" and starts_with(http.request.uri.path, "/v1/admin/"))`.
  Verify: `curl -I https://ai.oiep.example/v1/admin/health` → `404`, not a JSON `401`.
- **Local walkthrough:** set `OIEP_COMPANION_WIDGET_URL=http://127.0.0.1:8090/ui/`; the browser on
  the same machine can reach the loopback-published port.
- **No public widget:** set `OIEP_COMPANION_USER_HELPER_ENABLED=false` (the overlay defaults the
  user helper to `false` precisely so a missing public URL cannot render a broken iframe). The
  admin helper and the read API keep working with the internal base URL only.

### First-boot checklist for the overlay

1. `cp .env.example .env` and set the required secrets: `OIEP_COMPANION_ADMIN_TOKEN`
   (32+ chars) and `OIEP_AI_IP_HASH_SALT` (16+ chars), plus `OIEP_COMPANION_READ_TOKEN`
   (16+ chars) if you want corpus sync. The overlay's `${VAR:?…}` guards make `docker compose
   config`/`up` fail immediately when the token or salt is missing — fix `.env` and re-run.
   The overlay defaults `OIEP_COMPANION_BASE_URL` to `http://ai:8090`; leave it alone unless you
   customize the service name.
2. For sync, set `OIEP_AI_OIEP_BASE_URL=https://your-public-oiep-host` and
   `OIEP_COMPANION_READ_API_ENABLED=true` (the overlay defaults it to `true`).
3. For the widget, set `OIEP_COMPANION_USER_HELPER_ENABLED=true` **and**
   `OIEP_COMPANION_WIDGET_URL=https://ai.your-domain/ui/`, with the public hostname routed and
   `/v1/admin/` blocked.
4. `docker compose -f compose.yaml -f compose.companion.yaml up -d --build`
5. `docker compose -f compose.yaml -f compose.companion.yaml ps` → both services `healthy`;
   `curl -sS http://127.0.0.1:8090/readyz` → `{"status":"ready",…}`.
6. Open `/admin` → sign in → `/admin/assistant`; trigger a sync and watch the corpus count.

### Soft-disable / remove the overlay

```bash
# Keep the containers but make OIEP behave exactly as before:
# set OIEP_COMPANION_ENABLED=false in .env, then
docker compose -f compose.yaml -f compose.companion.yaml up -d
```

For full removal see [Full removal](#c-full-removal). Stop the overlay, delete
`compose.companion.yaml` and the companion checkout, then re-run the base
`docker compose up -d --force-recreate` so the `app` container drops the `OIEP_COMPANION_*`
environment variables (Compose keeps the old container configuration until it recreates the
container).

---

## Read-only experiences API

The component installs `GET /api/v1/experiences` only while `OIEP_COMPANION_ENABLED=true`,
`OIEP_COMPANION_READ_API_ENABLED=true` and a usable `OIEP_COMPANION_READ_TOKEN` is configured. It
is consumed by the companion's admin corpus sync and by nothing else.

```
GET /api/v1/experiences?updated_since=<ISO8601>&cursor=<opaque>&page_size=<1..500>
Authorization: Bearer <OIEP_COMPANION_READ_TOKEN>
```

| Parameter | Required | Rule |
| --- | --- | --- |
| `updated_since` | no | ISO-8601 instant/offset/local date-time; returns component events strictly after it. Omit for a full sync. |
| `cursor` | no | opaque continuation token from the previous page; invalid → `400 {"error":{"code":"invalid_cursor"}}` |
| `page_size` | no | 1–500, default `OIEP_COMPANION_READ_PAGE_SIZE` (`200`); an explicit value is clamped (`0`/negative → `1`, `>500` → `500`), a non-integer → `400 invalid_page_size` |

Response `200`:

```json
{
  "items": [
    {
      "id": "oiep-r42",
      "submitted_at": "2026-11-05T09:12:33Z",
      "updated_at": "2026-11-09T18:00:01.123Z",
      "deleted": false,
      "schema_version": "interview-experience/v1",
      "company": "Acme",
      "role_title": "Backend Engineer",
      "location": "Berlin, Germany",
      "work_mode": "HYBRID",
      "outcome": "OFFER",
      "difficulty": "MODERATE",
      "rounds": [
        {
          "index": 1,
          "type": "TECHNICAL",
          "format": "UNKNOWN",
          "topics": ["algorithm", "systemdesign"]
        }
      ],
      "overall_experience": "…the submitted story…"
    },
    {
      "id": "oiep-r43",
      "updated_at": "2026-11-09T19:30:00Z",
      "deleted": true
    }
  ],
  "next_cursor": "MTc2Mjc…",
  "has_more": false
}
```

Semantics:

- **Published reports only.** A hidden report or a permanently deleted report is never returned as
  a full record; it is returned as a tombstone (`id`, `updated_at`, `deleted: true`).
- Items are ordered by component event time, ascending; `next_cursor` is non-null exactly when
  `has_more` is `true` and always advances past the last item of the page. Cursors are opaque
  (unpadded base64url of `<event-epoch-millis>:<report-id>`) and only meaningful against the same
  database.
- Timestamps are normalized to ISO-8601 UTC (`Instant.toString()`, `Z` suffix).
- Every response (including errors) carries `Cache-Control: no-store`, `Pragma: no-cache` and
  `X-Robots-Tag: noindex, nofollow`. There is no CORS header: the endpoint is for a server, not a
  browser.
- The endpoint never returns hidden reports, has no write methods, and no other OIEP route accepts
  the read token.

Errors use one fixed JSON envelope `{"error":{"code":"…"}}`; no request value (token, cursor,
watermark, page size) is ever echoed or logged:

| Status | Code | When |
| --- | --- | --- |
| `401` | `unauthorized` | missing or wrong bearer token (`WWW-Authenticate: Bearer` is sent) |
| `400` | `invalid_cursor` | `cursor` is malformed or from another database |
| `400` | `invalid_page_size` | `page_size` is not an integer |
| `400` | `invalid_updated_since` | `updated_since` is not a parsable ISO-8601 value |
| `404` | `not_found` | unknown path under `/api/v1/**` while the read API is enabled (JSON, not the HTML error page) |
| `500` | `internal_error` | unexpected server failure (no stack trace or message) |

Curl example:

```bash
# first page, full sync
curl -sS -H "Authorization: Bearer $OIEP_COMPANION_READ_TOKEN" \
  "http://127.0.0.1:8080/api/v1/experiences?page_size=100"

# incremental page after a watermark
curl -sS -H "Authorization: Bearer $OIEP_COMPANION_READ_TOKEN" \
  "http://127.0.0.1:8080/api/v1/experiences?updated_since=2026-11-09T00:00:00Z&page_size=100"
```

The companion's sync client (`app/admin_helper/sync.py`) validates this envelope, follows
`has_more`/`next_cursor` for up to 25 pages / 10 000 items per run, applies all pages in one
transaction, and removes tombstones from its mirror. A failed pull leaves the mirror untouched.

---

## Mapping: OIEP report → InterviewExperience v1

The OIEP form and the companion's `InterviewExperience` v1 contract are different shapes. The
adapter lives in one place (`com.oiep.companion.read.InterviewExperienceAdapter`) and is documented
here so operators and the companion maintainers agree on what the mirror contains.

| `InterviewExperience` v1 | From the OIEP report | Notes |
| --- | --- | --- |
| `id` | `"oiep-r" + reports.id` | stable, < 200 characters |
| `submitted_at` | `reports.created_at` | normalized to ISO-8601 UTC; falls back to the event time if a hand-edited `created_at` is unparsable |
| `updated_at` | component event time (`companion_report_events.event_ms`) | normalized to ISO-8601 UTC; changes when a report is created, hidden, restored or deleted |
| `deleted` | `false` for items; `true` for tombstones | see [Tombstones](#tombstones) |
| `schema_version` | constant `interview-experience/v1` | |
| `company` | `company` | required by OIEP and by the companion |
| `role_title` | `role` | required by OIEP and by the companion |
| `location` | `location` | omitted when blank |
| `work_mode` | `onsite` → `ONSITE`, `hybrid` → `HYBRID`, `remote` → `REMOTE` | omitted when blank |
| `outcome` | `offer` → `OFFER`, `rejection` → `REJECTED`, `ghosted` → `NO_RESPONSE`, `waiting` → `PENDING`, `withdrew` → `WITHDRAWN` | required by OIEP |
| `difficulty` | **not collected by OIEP** — adapter value | see the limitation below |
| `rounds` | **one** entry, `index=1`, `type` from `round` | see the single-round adaptation below |
| `rounds[0].format` | constant `UNKNOWN` | OIEP does not collect the format |
| `rounds[0].topics` | `question_types` tags (comma-separated keys) | at most 8; OIEP caps the list at 7 |
| `rounds[0].notes` | — | never emitted; the free-text account lives in `overall_experience` |
| `overall_experience` | `story` | required by OIEP and by the companion |
| `role_level` | — | **never emitted**; OIEP does not collect it |
| `employment_type` | — | **never emitted**; OIEP does not collect it |
| `interview_date` | — | **never emitted**; OIEP does not collect it |

Round type mapping:

| OIEP `round` | Companion round `type` |
| --- | --- |
| `phone` | `PHONE` |
| `technical` | `TECHNICAL` |
| `takehome` | `TAKE_HOME` |
| `systemdesign` | `SYSTEM_DESIGN` |
| `behavioral` | `BEHAVIORAL` |
| `final` | `ONSITE` |
| `other` | `OTHER` |

Free text (`company`, `role_title`, `location`, `story`, tags) is whitespace-normalized, stripped of
control characters (OIEP textareas legitimately contain newlines; the companion's validator rejects
control characters) and capped at the contract limits, so every published report yields a
mirror-acceptable item. Unknown enum values are handled conservatively rather than dropped: an
unknown/missing round becomes `OTHER`, an unknown/missing `outcome` becomes `PENDING`, an unknown
`work_mode` is omitted, and `topics` is omitted when the note has no question-type tags.

### The difficulty limitation (read this before trusting difficulty analytics)

**OIEP's public form does not ask for interview difficulty, and this component does not add a new
form field.** The companion's contract requires a `difficulty` enum, so the adapter synthesizes
one:

- `OIEP_COMPANION_READ_DIFFICULTY_MODE=constant` (default): every experience is emitted with the
  same value, `OIEP_COMPANION_READ_DIFFICULTY` (default `MODERATE`).
- `OIEP_COMPANION_READ_DIFFICULTY_MODE=vibe-proxy`: a coarse, **approximate** value is derived
  from the interviewer-vibe field: `warm` → `EASY`, `neutral` → `MODERATE`, `cold` → `HARD`,
  `rushed` → `HARD`, `unclear` → `MODERATE`, blank → `MODERATE`.

Either way, difficulty in the companion's mirror is **not a measurement**. Do not draw conclusions
about interview difficulty from mirrored OIEP data; constant mode makes the field a placeholder,
and vibe-proxy is a proxy for how the interviewer felt, not for difficulty. This is a deliberate
data-minimisation decision, stated plainly rather than hidden.

### The single-round adaptation

OIEP records **which round a note is about** — one round label per report — plus the question-type
tags that were seen. It does not record a full multi-round loop with per-round durations, formats
and notes. The adapter therefore emits exactly **one** round entry (`index=1`) whose `type` is the
report's round and whose `topics` are the question-type tags. `format` is `UNKNOWN` and `notes` is
absent.

Consequence: round-count analytics over mirrored OIEP data are not meaningful (every record has one
round), and per-round difficulty/duration comparisons are impossible. The companion documents this
same limitation for its corpus mirror.

---

## Tombstones

When a report is hidden by moderation or permanently deleted, the companion must remove it from its
corpus mirror. OIEP propagates this with **tombstones** — items that carry only `id`, `updated_at`
and `"deleted": true` (no `schema_version`, no experience fields).

They are produced by a component-owned SQLite table and triggers, created and maintained entirely
by the companion component (no core repository or service code was changed):

| Object | Purpose |
| --- | --- |
| table `companion_report_events(report_id INTEGER PRIMARY KEY, event_ms INTEGER NOT NULL, deleted INTEGER NOT NULL)` | one row per report: last event time and whether the report is currently removed from the public corpus |
| index `companion_report_events_order` on `(event_ms, report_id)` | keeps a sync page an index range scan over the event ordering instead of a full sort |
| trigger `companion_report_events_ai` (`AFTER INSERT ON reports`) | records a new published report (`deleted = 0`) or a non-published one (`deleted = 1`) |
| trigger `companion_report_events_au` (`AFTER UPDATE OF status ON reports`, only when the status actually changed) | hide → `deleted = 1`; restore → `deleted = 0` |
| trigger `companion_report_events_ad` (`AFTER DELETE ON reports`) | `deleted = 1` tombstone; the event row outlives the report row |

Behaviour:

- **Hide** a report in the admin area → the next sync returns
  `{"id":"oiep-r<id>","deleted":true,…}`; the companion removes it from its mirror.
- **Restore** a hidden report → the next sync returns the full item again; the companion re-inserts
  it.
- **Delete** a report permanently → tombstone; the companion removes it.
- **First enable of the component:** existing reports are backfilled into the event table at
  startup (`deleted = 1` for any non-published report). The backfill uses each report's
  `created_at` as the event time, and only fills *missing* events, so a restart can never move a
  companion watermark backwards.
- The event row outlives a deleted report, so a full sync also removes stale mirror entries.
- If you restore an OIEP database backup that was taken **before** the component was enabled, the
  companion's mirror can be stale: trigger a **full** resync (`POST /v1/admin/corpus/sync` with
  `{"full": true}` on the companion) after restoring.

The component's own startup line confirms the setup:
`AI companion read API event store ready: triggers installed, backfilled N existing report event(s)`.
The table, index and triggers are dropped by
[`scripts/companion-uninstall.sql`](../scripts/companion-uninstall.sql) before the component code
is removed; see [Full removal](#c-full-removal).

---

## Security notes

- **Tokens are server-side only.** `OIEP_COMPANION_ADMIN_TOKEN` is read by the OIEP process and
  sent in the `Authorization` header of the server-side proxy; it is never rendered, never put in
  a model attribute, never written to a JavaScript file and never logged.
  `OIEP_COMPANION_READ_TOKEN` is only checked by the read API; it appears only in the companion's
  environment file.
- **CSP:** the only change to the response header is an appended `frame-src 'self' <widget-origin>`
  (exact origin, no wildcard). With the user helper disabled or the component off, the policy is
  byte-identical to the base policy.
- **Admin helper:** the page is inside the existing `/admin/**` interceptor (login required,
  `no-store`); every proxy POST requires the per-session `X-OIEP-CSRF` token, a JSON content type,
  a body of at most 64 KB, an allowlisted route and allowlisted top-level JSON fields. The proxy
  never forwards cookies, never follows redirects and never echoes companion authentication
  failures (401/403 become a generic `502 companion_auth_failed`).
- **Read API:** bearer token only, constant-time comparison over SHA-256 digests, published reports
  only, no CORS, no caching, no query echo. A stolen read token can read what the public board
  already shows — no more. Revoke it by changing `OIEP_COMPANION_READ_TOKEN` on OIEP and
  `OIEP_AI_OIEP_SYNC_TOKEN` on the companion, then restart both.
- **Companion public exposure:** the operator must keep the companion's `/v1/admin/` blocked at the
  public reverse proxy (the companion's own `docs/SECURITY.md` requires a 404 on the public vhost).
  A token is not an exposure boundary. Verify from outside:
  `curl -I https://ai.example/v1/admin/health` → proxy `404`, not a JSON `401`.
- **No request-body logging:** OIEP logs no proxy bodies; the companion never logs message bodies
  either. Keep it that way if you add a reverse proxy.
- **Rotation:** change both sides of a token pair, restart both services, then review the
  companion's audit log (`/v1/admin/audit`).

---

## Privacy notes

- **OIEP does not receive or store chat messages.** The user helper is an iframe loaded directly
  from the companion origin; the conversation stays between the visitor's browser and the
  companion service. OIEP only renders the frame URL and a short explanatory teaser.
- **Retention is implemented by the companion:** chat transcript and draft are deleted after
  **30 minutes of idle** and in any case after **2 hours**; the visitor can delete the chat at any
  time in the widget (self-serve `DELETE`, with an erasure receipt). The optional improvement
  dataset is **opt-in and off by default**; when enabled it keeps only the final structured form
  (never chat messages) for up to 30 days, and deleting the chat withdraws it.
- **Consent is collected by the widget**, from the machine-readable notice at `GET /v1/privacy`;
  no session can start without `consent.form_assist: true`.
- **What OIEP exposes to the companion** is only what the public board already shows (`published`
  reports), through a read-only token. Hidden reports are never returned.
- **Operator obligation:** the companion ships a privacy-notice template (`docs/PRIVACY.md` in the
  companion repository) that **must be adapted to your jurisdiction and published**. Link it from
  the OIEP page next to the helper (or link the operator's own privacy page), replace the
  placeholders (operator name, contact, supervisory authority, backup retention) and document the
  companion's data flows in your own privacy policy.
- Do not enable the helper for visitors who have not seen a privacy notice that mentions the
  separate service, its retention and the anonymous-by-default warning.

---

## Adding and removing the component

### A. Enable

1. Start the companion service (see [Quick start](#quick-start-local) or
   [Docker Compose overlay](#docker-compose-overlay)); note its base URL and the two tokens.
2. Set the OIEP environment variables (bare metal shell/`.env`, or `.env` for the overlay):

   ```dotenv
   OIEP_COMPANION_ENABLED=true
   OIEP_COMPANION_BASE_URL=http://127.0.0.1:8090
   OIEP_COMPANION_USER_HELPER_ENABLED=true
   OIEP_COMPANION_WIDGET_URL=https://ai.example/ui/      # browser-reachable
   OIEP_COMPANION_ADMIN_HELPER_ENABLED=true
   OIEP_COMPANION_ADMIN_TOKEN=<admin token>              # = companion OIEP_AI_ADMIN_TOKEN
   OIEP_COMPANION_READ_API_ENABLED=true
   OIEP_COMPANION_READ_TOKEN=<read token>                # = companion OIEP_AI_OIEP_SYNC_TOKEN
   ```

3. Restart OIEP (or `docker compose … up -d --force-recreate`).
4. Check the startup log for the `AI companion component enabled: …` line and run the
   [verification table](#3-verify). Each sub-feature can be enabled independently; anything that is
   switched on but misconfigured fails closed and logs why.

### B. Soft-disable (the normal way to turn it off)

```dotenv
OIEP_COMPANION_ENABLED=false
```

- No component beans are created: routes are absent (404), no page fragment renders, the CSP is
  the original base policy, and nothing in the database is written. The served pages and security
  headers are identical to an install without the component.
- The component's static asset files (`/js/companion.js`, `/js/companion-admin.js`,
  `/css/companion.css`, `/css/companion-admin.css`) are still served by Spring Boot while the
  component is disabled. No page references them, so they change no markup or CSP and are
  harmless; they disappear only when the files are deleted in the full-removal procedure.
- The companion service may be stopped independently; OIEP keeps working and simply has no helper.
- To remove only one surface, switch that sub-feature off. The master switch still stops
  everything.
- The read-API triggers remain installed while the component is disabled and keep recording report
  events into `companion_report_events` (harmless, and useful if you re-enable later). They are
  removed only by the full-removal SQL.
- Restart OIEP after changing the variables.

### C. Full removal

The component is additive, but a **full removal is an ordered operation** because the read API
installs SQLite triggers inside the OIEP database. Follow this order.

#### Step 1 — Stop OIEP and take a backup

```bash
sh scripts/backup.sh          # optional but recommended; note it now also contains companion objects
docker compose stop app       # or stop the bare-metal process
```

#### Step 2 — Run the uninstall SQL **before** removing the code

```bash
# Bare metal (the DB path is OIEP_DB_PATH; default data/oiep.db):
sqlite3 data/oiep.db < scripts/companion-uninstall.sql

# Docker (Compose volume; the runtime image includes sqlite3):
docker compose exec -T app sqlite3 /data/oiep.db < scripts/companion-uninstall.sql
```

[`scripts/companion-uninstall.sql`](../scripts/companion-uninstall.sql) runs, in this order:

```sql
DROP TRIGGER IF EXISTS companion_report_events_ai;
DROP TRIGGER IF EXISTS companion_report_events_au;
DROP TRIGGER IF EXISTS companion_report_events_ad;
DROP INDEX IF EXISTS companion_report_events_order;
DROP TABLE IF EXISTS companion_report_events;
```

It is idempotent, drops all five component-owned database objects, and does not touch the
`reports` table or any report data. Order matters: a trigger whose table is gone makes every
`INSERT`/`UPDATE`/`DELETE` on `reports` fail with `no such table: companion_report_events` —
including the admin delete-report flow. Run the script **first**, while the component code still
exists and you still have its trigger definitions in the repository.

If you ever restore an OIEP backup that was taken while the component was enabled (the backup
contains the triggers/table), run the script again after the restore.

#### Step 3 — Delete the component files

Delete exactly these (nothing else in the core application refers to them):

```
src/main/java/com/oiep/companion/                                  (whole tree)
src/main/webapp/WEB-INF/jsp/companion/                            (whole tree)
src/main/resources/static/js/companion.js
src/main/resources/static/js/companion-admin.js
src/main/resources/static/css/companion.css
src/main/resources/static/css/companion-admin.css
src/main/resources/messages-companion.properties
src/main/resources/messages-companion_zh_HK.properties
src/test/java/com/oiep/companion/                                 (whole tree, incl. StubCompanionServer)
scripts/companion-uninstall.sql                                   (after it has been run; keep a copy if you restore backups)
docs/COMPANION.md
compose.companion.yaml
```

Also remove the `OIEP_COMPANION_*` and companion-only `OIEP_AI_*` lines from `.env` / your
deployment environment, and stop/undeploy the companion service itself (see the companion's
`docs/DEPLOYMENT.md` §12), revoking its tokens.

#### Step 4 — Revert the component's edits to shared files (code, config, docs)

The component also added sections to shared files that are not deleted wholesale. Remove these
additions:

1. `src/main/resources/application.properties`
   - delete the whole `# --- Optional AI companion component (oiep-backendAI) ---` block at the end
     of the file;
   - change `spring.messages.basename=messages,messages-companion` back to
     `spring.messages.basename=messages`.
2. `src/main/webapp/WEB-INF/jsp/index.jsp` — delete the generic extension loop that renders
   component page extensions (the small `<c:forEach items="${pageExtensions}" …>` after the debrief
   section).
3. `src/main/webapp/WEB-INF/jsp/fragments/admin-topbar.jspf` — delete the same generic loop inside
   the authenticated admin navigation.
4. `README.md` — delete the `### Optional AI companion (oiep-backendAI)` section (between the Admin
   area and Docker sections); remove the three companion rows from the Routes table and the
   companion entries from the Project structure block; remove the companion bullet from the
   "suite covers" list; and change the test count in the Project structure block back to the
   pre-component count. A clean removal of the component test tree leaves `141 tests` (the
   pre-component baseline); run `./mvnw test` and use the actual number.
5. `.env.example` — delete the whole `# Optional AI companion (oiep-backendAI)` block, from that
   header down to the last `# OIEP_AI_PUBLISH_PORT=8090` line. The pre-existing tunnel/admin/security
   lines stay.
6. `docs/DEPLOY.md` — delete the `### Optional AI companion (oiep-backendAI)` subsection and the
   three companion rows it added to the "What is in this repo for deployment" table
   (`compose.companion.yaml`, `scripts/companion-uninstall.sql`, `docs/COMPANION.md`).

The README/`.env.example`/`docs/DEPLOY.md` edits are **cosmetic**: leaving them in place does not
break the build, boot or tests — the pages simply describe a component that no longer exists. The
code/config edits in items 1–3 matter for cleanliness but both generic loops are inert without the
component (see Step 5), so they too can be left in place. Do not use `git checkout` to revert shared
files if the repository has other uncommitted work; make the edits by hand and review `git diff`.

#### Step 5 — What you can keep (and why)

The generic extension points are **inert without the component** and may stay:

- `com.oiep.config.CspContributor` and the CSP composition in `WebConfig` (no contributors → the
  header is byte-identical to the base policy);
- `com.oiep.web.ui.PageExtension` and the `pageExtensions` model attribute in
  `GlobalModelAdvice` (no extensions → the loops render nothing);
- tests that assert the base CSP / empty extension behaviour.

Keeping them costs one interface each and makes a future re-add (or another optional component)
trivial. Removing them is optional; if you do, also revert `WebConfig`'s CSP method and
`GlobalModelAdvice` to their pre-component forms and delete `CspContributor`/`PageExtension`.

#### Step 6 — Verify the removal

| Check | Command | Expected |
| --- | --- | --- |
| Tests | `./mvnw -B -ntp test` (Windows: `mvnw.cmd`) | passes |
| Boot | `java -jar target/oiep.war` with a temp `OIEP_DB_PATH` | starts, no `AI companion` log line |
| Public page | `curl -s http://127.0.0.1:8080/` | no helper markup, no companion static-asset links |
| CSP | `curl -sI http://127.0.0.1:8080/` | no `frame-src` directive; header equals the base policy |
| Read API | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/experiences` | `404` |
| Admin helper | sign in, open `/admin/assistant` | `404` (login redirect while signed out, as for any unknown `/admin/**` route) |
| DB | `sqlite3 data/oiep.db ".tables"` and `.indexes companion_report_events` | no `companion_report_events` table and no companion index/trigger objects |
| Static assets | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/js/companion.js` | `404` after the files are deleted (while the component is merely disabled they are still served with `200`) |
| Admin delete | log in at `/admin`, delete a report | works without `no such table` errors |

---

## Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| Startup log has no `AI companion component enabled` line | `OIEP_COMPANION_ENABLED` is not `true` (or the app was not restarted). |
| Widget iframe is blank / browser console says "Refused to frame" | CSP `frame-src` is missing → widget URL invalid, or the companion proxy sends `X-Frame-Options`/`frame-ancestors` without your OIEP origin. Check the response headers of the widget URL; if you use the companion's reference nginx, add your OIEP origin to `frame-ancestors`. |
| Widget block renders but the frame cannot connect | `OIEP_COMPANION_WIDGET_URL` is `http://` while OIEP runs on HTTPS (mixed content), or the URL points at an internal Docker name (`http://ai:8090`) that the browser cannot resolve. Use a public HTTPS widget URL. |
| `/admin/assistant` is 404 after enabling | The admin-helper condition failed: `OIEP_COMPANION_ADMIN_HELPER_ENABLED` is false, `OIEP_COMPANION_BASE_URL` is not a plain origin, or the token is empty/short/a documented placeholder. The startup log prints the reason (never the token). |
| Admin helper shows "The helper service could not be reached" (502) | OIEP cannot reach the companion at `OIEP_COMPANION_BASE_URL` (container name/port, companion down, timeout), the companion answered 5xx/redirect, or the companion rejected the token (mapped to `502 companion_auth_failed`). Check `docker compose ps`, the companion `/readyz`, and that `OIEP_COMPANION_ADMIN_TOKEN` equals `OIEP_AI_ADMIN_TOKEN`. |
| Token "rejected"/sub-feature stays disabled | The value is a documented placeholder or shorter than 16 characters. Generate a real token. |
| Read API returns 404 | The read sub-feature is off (`OIEP_COMPANION_READ_API_ENABLED`), the token is unusable, or the master switch is off. Remember there is no HTML page at that path — it is a JSON API. |
| Read API returns 401 | Missing/incorrect bearer token, or the OIEP and companion tokens differ. The header must be `Authorization: Bearer <token>`. |
| Read API returns 400 `invalid_cursor` | The cursor is malformed or belongs to a different database (e.g. after a restore). Run a full sync on the companion instead of resuming. |
| Companion sync says `disabled` | Companion `OIEP_AI_OIEP_BASE_URL` or `OIEP_AI_OIEP_SYNC_TOKEN` is empty, or the OIEP read API is off. |
| Companion sync says `error` | Check `GET /v1/admin/corpus/sync` on the companion (`last_error`) and the OIEP read-API status/token; `OIEP_AI_ENV=prod` requires an `https://` OIEP base URL. |
| Hidden/deleted report still in the companion mirror | The read-API triggers were created only while the component was running; trigger a full resync, and confirm `companion_report_events` exists. |
| Admin delete of a report fails with `no such table: companion_report_events` | Someone dropped the component table but left its triggers. Recreate the table or run the uninstall SQL (drops triggers first), or restore the component code, then clean up in the documented order. |
| Docker overlay: companion starts but every request returns 500 and the log ends with `ValueError: OIEP_AI_ADMIN_TOKEN is required in production` | `.env` is missing `OIEP_COMPANION_ADMIN_TOKEN` (the overlay maps it to `OIEP_AI_ADMIN_TOKEN`) or it is empty/weak. With `OIEP_AI_ENV=prod` the companion validates on app creation, and it creates the app lazily per request, so uvicorn stays up while `/healthz` 500s. Set a 32+ character token in `.env` (and `OIEP_AI_IP_HASH_SALT`), then `docker compose -f compose.yaml -f compose.companion.yaml up -d --force-recreate ai app`. Current overlays fail fast: run `docker compose … config` first and it names the missing variable. |
| Docker overlay: companion restarts in a loop | `OIEP_AI_ENV=prod` fail-closed checks: admin token < 32 chars, missing IP salt, non-HTTPS `OIEP_AI_OIEP_BASE_URL`/CORS origins. `docker compose logs ai` names the variable. |
| All widget visitors share one rate-limit bucket / audit hashes are identical | The companion does not trust the `cloudflared`/Compose peer, so it ignores `X-Forwarded-For`. Set `OIEP_AI_TRUSTED_PROXIES` to the Compose network CIDR (the overlay's default private ranges cover the usual Docker networks). |
| `.env` changes have no effect | Compose keeps the existing container configuration; re-run with `up -d --force-recreate` (and `--build` if images changed). |

---

## Limitations

- **Difficulty is synthesized, not measured.** OIEP does not ask for interview difficulty; see
  [the difficulty limitation](#the-difficulty-limitation-read-this-before-trusting-difficulty-analytics).
- **One round per experience.** OIEP records the round a note is about, not a full multi-round
  history; mirrored `rounds` always has exactly one entry. Round-count analytics are not
  meaningful.
- **Missing companion fields.** `role_level`, `employment_type` and `interview_date` are never
  emitted because OIEP does not collect them; mirror statistics over those fields are empty.
- **Single instance.** The component follows OIEP's assumptions: one OIEP process against one
  SQLite file, one companion uvicorn worker. The triggers and the event table are per-database.
- **Ordered removal required.** The read API's triggers live in the OIEP database; running the
  uninstall SQL before removing the code is part of the documented procedure (see
  [Full removal](#c-full-removal)).
- **Soft-disable is not deletion.** With `OIEP_COMPANION_ENABLED=false` no component beans, routes,
  page markup or CSP changes exist, but the component's static files (`/js/companion*.js`,
  `/css/companion*.css`) are still served by Spring Boot — unreferenced and harmless. Delete them
  via the full-removal procedure if you want them gone.
- **Companion-side features are the companion's responsibility:** the optional local LLM
  (`OIEP_AI_MODEL_BACKEND=ollama`) and its memory budget, rate limits, consent strings, audit log
  and privacy notice all live in the companion repository. The Docker overlay runs the default
  extractive backend; use the companion's own deployment files for the optional local model.
- **Tokens are static shared secrets.** There is no OAuth/mTLS between the two services; rotate
  both sides together, restart both, and keep the tokens in an environment/secret file, not in the
  repository.
- **The admin helper is read-only by design.** It can ask questions about the corpus, run
  allowlisted queries and trigger syncs; it cannot hide, restore, delete or edit OIEP reports.
  Moderation stays in `/admin`.
- **No offline queue.** If the companion is down, the user helper and admin helper are
  unavailable; OIEP itself (form, browse, moderation, read API when enabled) keeps working.
- **Cloudflare routing is operator configuration.** The Compose overlay adds the service; exposing
  a public widget hostname and blocking `/v1/admin/` there is done in your Cloudflare/nginx
  configuration, not by Compose. Verify the block from outside before enabling the widget.
