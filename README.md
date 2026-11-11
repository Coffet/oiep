# OIEP

[![CI](https://github.com/Coffet/oiep/actions/workflows/ci.yml/badge.svg)](https://github.com/Coffet/oiep/actions/workflows/ci.yml)

**Open Interview Experience Platform** — a non-profit, self-hosted, solo coded work and community-run collection of real interview experiences.

No polished marketing copy. No paywalls. No corporate spin. Just what actually happened, from the people who were in the room.

> “I want to hear your experience. Not mine.”

---

## Preview

**Desktop** — the debrief form is the page; the lookup section waits quietly below it.

[![OIEP index on desktop](docs/index-desktop.png)](docs/index-desktop.png)

**Mobile** — same hierarchy, single column, generous tap targets.

[![OIEP index on mobile](docs/index-mobile.png)](docs/index-mobile.png)

**Chinese (zh-HK)** — human-written standard written Traditional Chinese, not colloquial Cantonese and not machine-translated.

[![OIEP index in Traditional Chinese](docs/index-zh-HK.png)](docs/index-zh-HK.png)

Screenshots use the optional demo reports (`--oiep.demo-data=true`); a fresh install starts empty.

---

## What is built

The first working slice:

- **Index** — one sentence, one large anonymous debrief form, one quiet trust line, then a subdued search/recent-notes section.
- **Submission** — company, role, round, where, outcome, heard-back timing, question types, salary, interviewer vibe, and the story itself. Progressively disclosed extras, client-side validation with friendly errors, and full server-side validation as the no-JS fallback.
- **Search / browse** — free-text search across company, role, location, story and question tags; filters for round, outcome and work mode; sort by newest or most useful; pagination.
- **Report detail** — published note, metadata, anonymous signoff, and a useful vote (once per browser session).
- **Bilingual UI** — English first, human-written standard written Traditional Chinese (`zh-HK`) available through the header toggle. Persisted in an `OIEP_LANG` cookie.
- **About** — the full “why this exists”, roadmap and tech-stack copy lives on `/about`, keeping the index focused on the form.
- **Admin / moderation** — password-protected `/admin` dashboard with note counts, search and status filters, hide/restore, delete-with-confirmation, per-session CSRF tokens, login throttling plus a login-page throttle, session rotation, a hard server-side session cap and no-store admin responses. Failed sign-ins, throttling and moderation actions are written to the security log with the resolved client address.
- **Optional AI companion (oiep-backendAI)** — a self-contained, disabled-by-default component that plugs the separate local-first AI helper service in: a floating user-helper chat widget on the form page, a read-only experiences API for the companion's corpus sync, and an operator AI helper page at `/admin/assistant`. See [Optional AI companion](#optional-ai-companion-oiep-backendai).
- **Ops basics** — SQLite out of the box, custom 404/500 pages, XSS-safe JSP output escaping, hash-based CSP with HSTS-on-HTTPS/COOP/CORP/X-Frame-Options, honeypot, per-client rate limiting keyed on the real visitor IP (Cloudflare `CF-Connecting-IP`; client-supplied `X-Forwarded-For` is never trusted), same-origin checks on anonymous POSTs, and cookie-only sessions with `Secure`/`HttpOnly`/`SameSite=Lax`.
- **Deployment** — multi-stage Docker image, hardened Compose runtime (`cap_drop: ALL`, pids limits, no-new-privileges), persistent SQLite volume, Cloudflare Tunnel service, safe backup/restore scripts, Dependabot updates and a CI Docker smoke test that asserts the security headers, relative admin redirect and session-cookie flags.

## Run it

Requirements: **JDK 17+**. Maven is not required; the Maven wrapper is included.

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
./mvnw spring-boot:run
```

Then open [http://localhost:8080](http://localhost:8080).

The SQLite database is created automatically at `data/oiep.db` (WAL mode). To use another location, set `OIEP_DB_PATH` or override the datasource URL:

```bash
OIEP_DB_PATH=/var/lib/oiep/oiep.db java -jar target/oiep.war
java -jar target/oiep.war --spring.datasource.url="jdbc:sqlite:/absolute/path/oiep.db"
```

### Package and serve

```bash
./mvnw package
java -jar target/oiep.war
```

The executable WAR embeds Tomcat and serves the JSP views directly. No external servlet container is needed.

### Demo content (optional)

A fresh install is empty by design. For screenshots or a local walkthrough you can seed six short fictional reports:

```bash
java -jar target/oiep.war --oiep.demo-data=true
```

The seeder only runs when the reports table is empty, and the company names are deliberately fictional. Do not treat the demo notes as real experiences.

### Admin area

The admin area is **disabled until you set a password**. There is deliberately no default admin password in the repository. Use a unique long random secret (16+ characters recommended); a placeholder copied verbatim from this repository's docs or `.env.example` is detected at startup and the admin area stays disabled, fail-closed.

Set both variables before starting the app:

```powershell
$env:OIEP_ADMIN_USERNAME = "admin"
# Replace the placeholder with your own long random secret; this exact placeholder is rejected at startup.
$env:OIEP_ADMIN_PASSWORD = "<paste-your-own-long-random-secret>"
java -jar target/oiep.war
```

```bash
OIEP_ADMIN_USERNAME=admin \
OIEP_ADMIN_PASSWORD='<paste-your-own-long-random-secret>' \
java -jar target/oiep.war
```

Then open **[http://localhost:8080/admin](http://localhost:8080/admin)** directly. The admin route is intentionally not linked from the public interface.

[![OIEP admin dashboard](docs/admin.png)](docs/admin.png)

The admin login is throttled, CSRF-protected and session-based. It only lets you hide, restore or delete notes; it never exposes the password. For Docker, put `OIEP_ADMIN_PASSWORD` in `.env` before `docker compose up -d --build`.

### Optional AI companion (oiep-backendAI)

A separate, optional service — [oiep-backendAI](https://github.com/Coffet/oiep-backendAI), a
local-first FastAPI process — can be plugged in next to OIEP. It is **off by default**: leave
`OIEP_COMPANION_ENABLED` unset (or `false`) and OIEP serves the same pages and headers as before,
with no new routes, page fragments or CSP changes. (The component's static JS/CSS files stay in
the built artifact and are still served while the component is disabled; nothing references them,
and the full-removal procedure deletes them.)

The companion is deliberately *not* part of this repository: no model, prompt or chat code lives in
the Spring app. The two projects speak two versioned REST contracts (a public user-helper API and a
read-only OIEP experiences API) and stay independently deployable.

#### What the component adds

| Surface | Where | Enabled by |
| --- | --- | --- |
| **User helper** | Round chat button (bottom-right) on the public form page `/`; opens a closable chat overlay with the companion's own chat widget | `OIEP_COMPANION_ENABLED` + `OIEP_COMPANION_USER_HELPER_ENABLED` |
| **Read-only experiences API** | `GET /api/v1/experiences` (`oiep-read-api/v1`), pulled by the companion's operator corpus sync | `+ OIEP_COMPANION_READ_API_ENABLED` |
| **Operator admin helper** | `/admin/assistant` inside the existing admin area, server-side proxy to the companion's `/v1/admin/*` | `+ OIEP_COMPANION_ADMIN_HELPER_ENABLED` (see the configuration table below) |

**User helper UX.** A round chat button floats in the bottom-right corner of the landing page and
opens a closable chat overlay: a dark chat header (bot avatar, “Form helper”, close button), the
companion's chat widget filling the body, and a compact action bar with the “Use the helper’s
answers” button. The button swaps to a close icon while open; `Esc`, the header close button or a
click outside closes it and focus returns to the button. Without JavaScript the button is a plain
link that opens the standalone widget (`/ui/`) in a new tab, and the overlay stays hidden. The
visitor's browser loads the widget directly from the companion's origin, and OIEP adds **exactly
that origin** to the CSP `frame-src` (never a wildcard). The prefill hand-off is an origin-checked
`postMessage` conversation; it only fills empty fields, never overwrites answers and **never
submits**. The questions are reviewed form-field prompts: the dialogue engine decides which field
is asked next and the visitor normally sees the reviewed template wording. With the companion's
optional local model active (`OIEP_AI_MODEL_BACKEND=ollama`, not included in the Docker overlay)
and `OIEP_AI_LLM_PHRASE_QUESTIONS` at its default `true`, the model may phrase the next question
conversationally using only the field, its template question, its options and a few draft answers;
the wording is validated, any failure falls back to the reviewed template, and OIEP still only
embeds the widget — it sends no prompts and sees no model output beyond the question text the
visitor sees. Every string is bilingual (English + human-written zh-HK).

**Admin helper.** `/admin/assistant` is protected by the normal admin session; mutations require the
per-session CSRF token, and the companion bearer token never reaches the browser. Only fixed
companion routes are proxied (health, corpus stats/sync/audit, chat, query, summarise), request
bodies are capped at 64 KB and reduced to an allowlist, and failures become a friendly `502`.

**Read-only experiences API.** The companion pulls published notes with a dedicated read-only bearer
token (`GET /api/v1/experiences?updated_since=&cursor=&page_size=`), keyset-paginated. Hidden and
deleted notes are propagated as `deleted: true` tombstones through component-owned SQLite triggers,
so the companion's mirror converges on the next sync; restoring a note brings it back. The API never
exposes hidden notes, has no CORS, and answers every `/api/v1/**` response as JSON.

#### Local walkthrough (Docker)

Requirements: Docker Desktop/Engine, and a sibling checkout of the companion
(`git clone https://github.com/Coffet/oiep-backendAI ../oiep-backendAI`). Start from this repository
root.

1. **Create `.env`** from `.env.example` and set the four secrets below. `.env` is gitignored;
   never commit it. The overlay now *refuses to start* (`docker compose config` names the missing
   variable) when a required secret is empty or a documented placeholder.

   | Variable in `.env` | What it is | Requirement | Consumed by |
   | --- | --- | --- | --- |
   | `OIEP_ADMIN_USERNAME` / `OIEP_ADMIN_PASSWORD` | Your OIEP `/admin` login | password 16+ chars, not a documented placeholder | OIEP |
   | `OIEP_COMPANION_ADMIN_TOKEN` | Companion operator bearer token (the overlay maps it to the companion's `OIEP_AI_ADMIN_TOKEN`) | **32+ chars** | both sides |
   | `OIEP_COMPANION_READ_TOKEN` | Read-only token for the corpus-read API (mapped to `OIEP_AI_OIEP_SYNC_TOKEN`) | 16+ chars | both sides |
   | `OIEP_AI_IP_HASH_SALT` | Salt the companion uses to hash visitor IPs | **16+ chars** | companion |

   Generate them with:
   `python -c "import secrets; print(secrets.token_urlsafe(48))"` for each token and
   `python -c "import secrets; print(secrets.token_urlsafe(32))"` for the salt. The companion runs
   with `OIEP_AI_ENV=prod` in the overlay, so it fails closed on weak/missing secrets.

2. **Build and start both services:**

   ```bash
   docker compose -f compose.yaml -f compose.companion.yaml config   # validates .env + overlay
   docker compose -f compose.yaml -f compose.companion.yaml up -d --build
   docker compose -f compose.yaml -f compose.companion.yaml ps       # app + ai both healthy
   docker compose -f compose.yaml -f compose.companion.yaml logs -f ai
   ```

3. **Open it:** public form and floating helper at [http://127.0.0.1:8080/](http://127.0.0.1:8080/),
   admin login at [http://127.0.0.1:8080/admin](http://127.0.0.1:8080/admin) (credentials from
   `.env`), AI operator page at `/admin/assistant`, the standalone widget at
   [http://127.0.0.1:8090/ui/](http://127.0.0.1:8090/ui/), and the companion health check at
   [http://127.0.0.1:8090/healthz](http://127.0.0.1:8090/healthz).

   Local plain-HTTP caveat: the shipped Compose file marks the session cookie `Secure` for the HTTPS
   deployment, so add `OIEP_SESSION_COOKIE_SECURE=false` to `.env` for an `http://127.0.0.1` admin
   walkthrough. Never use `false` on a public HTTPS deployment.

4. **Optional extras:**
   - *Show the floating helper locally:* set `OIEP_COMPANION_USER_HELPER_ENABLED=true` and
     `OIEP_COMPANION_WIDGET_URL=http://127.0.0.1:8090/ui/`, then
     `docker compose … up -d --force-recreate app`.
   - *Try corpus sync locally:* prod mode requires an HTTPS OIEP URL, so for a local demo set
     `OIEP_AI_ENV=dev` and `OIEP_AI_OIEP_BASE_URL=http://app:8080`, recreate `ai`, then use
     “Sync now” on `/admin/assistant`. For any public deployment keep `OIEP_AI_ENV=prod` with an
     `https://` OIEP URL.

   After upgrading the image, hard-refresh the page (`Ctrl+F5`): the companion CSS/JS filenames are
   stable and served with normal cache headers.

#### Bare metal / development

```bash
# companion (separate repo, separate process)
cd ../oiep-backendAI
python -m venv .venv && . .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
export OIEP_AI_ENV=dev
export OIEP_AI_ADMIN_TOKEN='<same value as OIEP_COMPANION_ADMIN_TOKEN>'
uvicorn app.main:app --host 127.0.0.1 --port 8090 --workers 1   # exactly one worker
```

Then start OIEP (`./mvnw spring-boot:run`) with the `OIEP_COMPANION_*` variables described in
[docs/COMPANION.md](docs/COMPANION.md). The two token pairs are the only credentials that must
match across the two processes:

- `OIEP_COMPANION_ADMIN_TOKEN` ↔ companion `OIEP_AI_ADMIN_TOKEN`
- `OIEP_COMPANION_READ_TOKEN` ↔ companion `OIEP_AI_OIEP_SYNC_TOKEN`

#### Configuration at a glance

All settings live under `oiep.companion.*`; each is exposed as an environment variable and the full
reference (including companion-side variables and the add/remove procedure) is in
**[docs/COMPANION.md](docs/COMPANION.md)**.

| Property | Environment variable | Default |
| --- | --- | --- |
| `oiep.companion.enabled` | `OIEP_COMPANION_ENABLED` | `false` |
| `oiep.companion.base-url` | `OIEP_COMPANION_BASE_URL` | *(empty)* — companion origin used server-side, e.g. `http://ai:8090` |
| `oiep.companion.user-helper.enabled` | `OIEP_COMPANION_USER_HELPER_ENABLED` | `true` |
| `oiep.companion.user-helper.widget-url` | `OIEP_COMPANION_WIDGET_URL` | `<base-url>/ui/` |
| `oiep.companion.admin-helper.enabled` | `OIEP_COMPANION_ADMIN_HELPER_ENABLED` | `false` |
| `oiep.companion.admin-helper.token` | `OIEP_COMPANION_ADMIN_TOKEN` | *(empty)* |
| `oiep.companion.read-api.enabled` | `OIEP_COMPANION_READ_API_ENABLED` | `false` |
| `oiep.companion.read-api.token` | `OIEP_COMPANION_READ_TOKEN` | *(empty)* |
| `oiep.companion.read-api.page-size` | `OIEP_COMPANION_READ_PAGE_SIZE` | `200` (clamped 1–500) |
| `oiep.companion.read-api.difficulty` | `OIEP_COMPANION_READ_DIFFICULTY` | `MODERATE` |
| `oiep.companion.read-api.difficulty-mode` | `OIEP_COMPANION_READ_DIFFICULTY_MODE` | `constant` (or `vibe-proxy`) |
| `oiep.companion.http.connect-timeout-ms` | `OIEP_COMPANION_CONNECT_TIMEOUT_MS` | `3000` |
| `oiep.companion.http.request-timeout-ms` | `OIEP_COMPANION_REQUEST_TIMEOUT_MS` | `15000` |

#### Security and privacy

- Each sub-feature fails closed: a misconfigured token, base URL or widget URL registers **no** beans
  (no route, no fragment, no CSP directive), and the startup log says why without ever printing a
  secret.
- The companion operator token is server-side only (proxy + startup config); the read token is
  compared in constant time over SHA-256 digests and only unlocks the read-only experiences API. No
  CORS on that API.
- The user helper chat happens on the companion origin; OIEP receives no messages and stores no
  transcript. The companion’s own policy applies: explicit consent before chatting, retention of
  30 minutes idle / 2 hours hard cap, self-serve “delete my chat” with a receipt, optional improvement
  dataset off by default, hashed IPs and no message bodies in logs. Adapt and publish the companion’s
  privacy notice for your instance.
- The admin proxy enforces the admin session + CSRF, forwards only fixed routes/allowlisted fields,
  caps bodies, sets `no-store`/`noindex`, and never echoes the companion token.
- Soft-disabling the component leaves its static JS/CSS files served (unreferenced), and — if the read
  API was ever enabled — its SQLite triggers installed. The full-removal procedure deletes both.

#### Data-mapping limits (read this before trusting corpus analytics)

OIEP records one note per submission (company, role, the round the note is about, location, work mode,
outcome, question-type tags, free text); it does not ask for an overall difficulty rating or a full
multi-round history. The read API therefore emits a **synthesized `difficulty`** (constant
`MODERATE` by default, or the documented `vibe-proxy` mapping) and exactly **one round entry** per
note, with topics from the question-type tags. Treat difficulty/round-count analytics over
OIEP-sourced rows as approximate; the mapping table lives in
[docs/COMPANION.md](docs/COMPANION.md).

#### Enable, disable, remove

- **Enable:** set the env vars above (or use the overlay `.env`) and restart. The startup log prints
  `AI companion component enabled: … userHelper=… adminHelper=… readApi=…`.
- **Soft-disable:** `OIEP_COMPANION_ENABLED=false` and restart. The app behaves as before (only the
  unreferenced static assets and, if installed, the inert DB triggers remain). The companion service
  can keep running or be stopped independently.
- **Full removal:** follow [docs/COMPANION.md → Adding and removing the component](docs/COMPANION.md).
  The one ordering rule: run **[scripts/companion-uninstall.sql](scripts/companion-uninstall.sql)**
  **before** deleting the component code — a trigger whose table is gone makes every `INSERT`/
  `UPDATE`/`DELETE` on `reports` fail. Then delete the component files/assets/bundles/docs, revert the
  small shared edits (`application.properties` block + messages basename; the two generic JSP extension
  loops are inert and may stay), and remove the `OIEP_COMPANION_*` lines from `.env`.
- **Verification after changes:** `./mvnw test` plus the checklists in the companion doc. The
  independent report — including a full clean-removal simulation (141 pre-component tests still pass,
  app boots with no companion log/routes/markup/DB objects) — is
  [docs/COMPANION-VERIFICATION-ROUND2.md](docs/COMPANION-VERIFICATION-ROUND2.md).

> CI runs the same suite on every push; the Docker smoke test still asserts the baseline security
> headers/cookie flags for a default (component-off) deployment.

### Docker (local)

With Docker Desktop (or Docker Engine) running:

```bash
docker compose up -d --build
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080). The database lives in the `oiep-data` Docker volume, so `docker compose down` keeps it; `docker compose down -v` deletes it.

`compose.yaml` sets `OIEP_SESSION_COOKIE_SECURE=true` because the shipped deployment is HTTPS (TLS terminates at Cloudflare), so the session cookie is marked `Secure`. For a local plain-HTTP walkthrough of the admin area add `OIEP_SESSION_COOKIE_SECURE=false` to `.env` before `docker compose up` — otherwise the browser will not send the `Secure` session cookie over `http://` and login cannot complete. Never set it to `false` on a public HTTPS deployment.

### Deploy to the internet (Docker + Cloudflare)

The short honest version: **Cloudflare Pages cannot host this app.** Pages is a static-file host; OIEP is a Java/JSP server with a live SQLite database. The shipped deployment uses a small Docker server and **Cloudflare Tunnel**, so Cloudflare still provides DNS, HTTPS and its edge network without opening any inbound web ports.

The repo includes a multi-stage `Dockerfile`, `compose.yaml` with an optional `tunnel` profile, `.env.example`, and backup/restore scripts. Follow the step-by-step guide:

* **[Deploy OIEP on a VPS with Docker + Cloudflare Tunnel → docs/DEPLOY.md](docs/DEPLOY.md)**

## Routes


| Method        | Path                          | Purpose                                      |
| ------------- | ----------------------------- | -------------------------------------------- |
| `GET`         | `/`                           | Index: anonymous debrief form + quiet lookup (plus the optional AI helper when enabled) |
| `POST`        | `/reports`                    | Validate and publish a note                  |
| `GET`         | `/browse`                     | Search, filter and sort notes                |
| `GET`         | `/reports/{id}`               | Read a note                                  |
| `POST`        | `/reports/{id}/helpful`       | Mark a note useful (once per session)        |
| `GET`         | `/about`                      | The longer story, roadmap and stack          |
| `GET`         | `/language/{en|zh-HK}`        | Switch locale, safely redirect back          |
| `GET`         | `/admin`                      | Admin dashboard (login required)             |
| `GET`, `POST` | `/admin/login`                | Admin sign-in with CSRF token                |
| `POST`        | `/admin/logout`               | End the admin session                        |
| `GET`         | `/admin/reports/{id}`         | Review a note inside the admin area          |
| `POST`        | `/admin/reports/{id}/hide`    | Hide a note from the public board            |
| `POST`        | `/admin/reports/{id}/restore` | Restore a hidden note                        |
| `GET`, `POST` | `/admin/reports/{id}/delete`  | Confirmation page and permanent deletion     |
| `GET`         | `/admin/assistant`            | Optional AI companion: operator helper page (admin session) |
| `GET`, `POST` | `/admin/assistant/api/**`     | Optional AI companion: same-origin proxy to the companion admin API (admin session + CSRF) |
| `GET`         | `/api/v1/experiences`         | Optional AI companion: read-only experiences API for the companion's corpus sync (bearer token) |

## Index design contract

The index follows the brief literally rather than treating it as a landing-page template:

1. **Top bar** — wordmark, language switch, Browse.
2. **Hero** — one sentence, nothing else. The title is Instrument Sans at `64px` on desktop; Y1CandyCore is reserved exclusively for the OIEP wordmarks in the header and footer.
3. **The debrief form** — the page’s primary object: a three-part field note (`the room`, `the answer`, `the note`), bare underlines, selectable chips, an optional details disclosure, and generous whitespace.
4. **Trust line** — “You can post anonymously.” directly under the form.
5. **Quiet secondary section** — a small search entry and up to three recent notes, visually subordinate and far down the page.

Body copy is Instrument Sans at `24px`; all headings, labels and UI copy also use Instrument Sans. The palette comes from the supplied brand reference: warm paper `#F5EBD5`, ink `#111820`, and a single correction-red `#A92F1D`. No gradients, no cards, no SaaS chrome.

## Accessibility and i18n

- Skip link, visible high-contrast focus rings, keyboard-operable chips/disclosures, 44px-class touch targets.
- Semantic headings, fieldsets/legends, accessible names, described-by errors, live status region, and an alert on server validation failure.
- Native form submission and native `<details>` still work without JavaScript; client-side validation and auto-applied browse filters are progressive enhancements.
- `prefers-reduced-motion` support, high-contrast preference support, print styles, and no horizontal overflow at 320px.
- Axe checks (WCAG 2.0/2.1/2.2 A/AA + best practice) are clean for the index, browse, about, report and error pages in both locales.
- Message bundles: `messages.properties` (English root) and `messages_zh_HK.properties` (human-written standard written Traditional Chinese, zh-HK; no colloquial Cantonese). `spring.messages.fallback-to-system-locale` is intentionally **false** so a server JVM running under `zh_HK` does not override the English default; zh-HK still falls back to the English root for any missing key.

## Privacy and security posture

- No accounts, names or emails are collected to post or search. (A short-lived server-side session is used to hold the admin/login CSRF token and the once-per-session useful-vote marker only.)
- The form is anonymous; the UI explicitly warns contributors not to include names or contact details.
- All output is escaped in JSP (`c:out` / `fn:escapeXml`); SQL uses prepared statements and escaped LIKE wildcards.
- Abuse friction: a honeypot field plus in-memory per-client limits — 8 submissions / 10 minutes and 30 useful votes / hour — keyed on the resolved client address. Anonymous `POST`s are also checked for same-origin browser provenance (`Origin`, else `Sec-Fetch-Site`, else `Referer`); non-browser clients that send none of those headers are allowed (the endpoints are anonymous and carry no ambient browser credentials).
- Client address: behind the shipped Cloudflare Tunnel the real visitor IP arrives in `CF-Connecting-IP` (`OIEP_TRUST_CLOUDFLARE_CONNECTING_IP=true`) and Tomcat's native `RemoteIpValve` only honours it from the trusted `cloudflared` hop. Client-supplied `X-Forwarded-For` / `Forwarded` headers are never used for limits or logs, so rotating them cannot bypass a throttle. If you front the app with a different proxy, configure its trusted hop instead of trusting client headers. Keep `OIEP_TRUST_CLOUDFLARE_CONNECTING_IP` enabled only while the app port stays bound to `127.0.0.1` behind Cloudflare Tunnel; the app applies `CF-Connecting-IP` only when Tomcat's trusted-proxy handling has already rewritten the peer address to the same value.
- Sessions are cookie-only (no `;jsessionid` in URLs). The session cookie is `HttpOnly`, `SameSite=Lax`, and `Secure` when `OIEP_SESSION_COOKIE_SECURE=true` (Compose default for the HTTPS deployment). The `OIEP_LANG` locale cookie uses the same `HttpOnly`/`SameSite=Lax`/`Secure` policy, tied to the same setting. A session is allocated only for a posted note, a useful vote, or the admin login form (and not at all while admin is disabled or when CSRF validation fails). The login page is throttled per client and a hard global cap (`OIEP_SESSIONS_MAX_ACTIVE`, default 10000) bounds server-side sessions.
- Admin credentials are deployment environment variables, compared in constant time over SHA-256 digests. Successful login rotates the session; admin mutations are POST-only and require a per-session CSRF token; the login throttle is per client and fails closed; admin responses are `no-store`. A failed sign-in, a throttled sign-in, a logout and every hide/restore/delete are logged with the resolved client address.
- Hidden notes are excluded from the public browse/search, recent list and direct `/reports/{id}` route. Only an authenticated admin session can see or restore them.
- Response headers: `Content-Security-Policy` with per-inline SHA-256 hashes (no `'unsafe-inline'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin` and `X-Permitted-Cross-Domain-Policies: none`. `Strict-Transport-Security` is sent only on HTTPS responses. Dynamic HTML is `Cache-Control: no-cache` with `Vary: Cookie`, while static assets stay cacheable.
- Redirects are emitted as relative `Location` headers, so a forged `Host`/`X-Forwarded-Host` header cannot turn an app redirect into a cross-host open redirect.
- Custom error pages never expose stack traces or internal messages.

Current limitations before a larger public launch: useful votes are tracked per browser session (now additionally throttled per client, but still not per-person); the rate limiters, login throttle and session cap are single-instance in-memory structures that reset on restart (the app is designed for one container against one SQLite file); the login throttle fails closed, so repeated failures from a shared egress IP — including the admin's own — can lock the admin out for the 15-minute window; the single admin account has no MFA; and the app-level limiters are abuse friction, not a substitute for an external WAF (Cloudflare's edge protection is the network-level layer). The session cap admits new sessions atomically, so a concurrent burst cannot overshoot it; however a client able to hold the cap open (for example by rotating addresses) can make new sessions receive `503` until slots expire, and a successful sign-in can still be blocked by the login-page throttle for the same window. Security events go to the container's stdout log only — ship them off-host if you need retention or alerting. Docker and Cloudflare wiring are verified locally (CI smoke test plus a local Docker stack); a real named Cloudflare Tunnel needs your own account, as described in [docs/DEPLOY.md](docs/DEPLOY.md).

## Project structure

```
.github/workflows/ci.yml               tests + Docker smoke with security-header/cookie assertions
.github/dependabot.yml                 weekly Maven, GitHub Actions and Docker update PRs
pom.xml
mvnw, mvnw.cmd, .mvn/                  Maven wrapper
Dockerfile                             multi-stage image, non-root runtime, healthcheck, hardened /data
compose.yaml                           app + persistent volume + hardened runtime + optional Cloudflare Tunnel
compose.companion.yaml                 optional AI companion Docker Compose overlay (adds the `ai` service)
.env.example                           copy to .env for production secrets
scripts/backup.sh, scripts/restore.sh  SQLite backup/restore helpers (0600 backups, guarded restore)
scripts/companion-uninstall.sql        drops the optional companion's read-API triggers/table (removal step)
docs/DEPLOY.md                         hand-holding deployment guide
docs/COMPANION.md                      optional AI companion reference, config and add/remove procedure
docs/COMPANION-VERIFICATION.md         independent verification report (incl. clean-removal simulation)
src/main/java/com/oiep/
  companion/                           optional AI companion component: user helper, admin proxy, read API
  config/                              JDBC/SQLite + locale + CSP configuration
  domain/                              form, read model, allowed-value registry
  repository/                          prepared SQLite queries
  service/                             demo seeder, useful-vote tracker, rate limiters, admin auth
  web/                                 MVC controllers, error controller, admin auth interceptor
src/main/resources/
  application.properties
  schema.sql
  messages.properties                  English root
  messages_zh_HK.properties            human-written standard written Traditional Chinese
  messages-companion.properties        optional AI companion, English
  messages-companion_zh_HK.properties  optional AI companion, zh-HK
  static/css/site.css                  the design system + page styles
  static/css/admin.css                 back-office layout
  static/css/companion.css             optional user-helper styles
  static/css/companion-admin.css       optional admin-helper styles
  static/js/site.js                    minimal vanilla progressive enhancement
  static/js/companion.js               optional user-helper floating launcher + prefill bridge
  static/js/companion-admin.js         optional admin-helper page script
  static/fonts/                        webfonts + OFL for Instrument Sans
src/main/webapp/WEB-INF/jsp/           JSP views and shared fragments
src/main/webapp/WEB-INF/jsp/admin/     admin login, dashboard, detail, delete pages
src/main/webapp/WEB-INF/jsp/companion/ optional user-helper and admin-helper views
src/test/java/com/oiep/                330 tests
```

## Security hardening (second adversarial round)

The optional companion and its OIEP integration were pen-tested again with live browser,
raw-HTTP and container-level attacks. Findings and fixes are consolidated in
[docs/COMPANION-VERIFICATION-ROUND2.md](docs/COMPANION-VERIFICATION-ROUND2.md) and the
security/ reports; the app-side additions are covered by the tests listed below
(sync-watermark monotonicity, read-API throttling, bounded companion responses).

## Tests

```bash
./mvnw test
```

The suite covers:

- repository search/filter/pagination and helpful counters
- validation and persistence of submissions
- server-side error rendering (`422`) and rate-limited rendering (`429`)
- clean POST redirects with no session id in the URL
- locale switching, safe return URLs and message-bundle parity
- demo seeding only when explicitly enabled
- home/browse/report/about routes and the styled error path
- admin login redirects, CSRF rejection, login throttling, hide/restore/delete and hidden-report 404s
- the optional AI companion: disabled-by-default behaviour (no routes, page snippets or CSP changes), the user-helper fragment and prefill bridge, the read-API contract/auth/pagination/tombstones, and the admin proxy (admin session, CSRF, allowlists, `502` on failure)

GitHub Actions runs the same `./mvnw -B -ntp verify` suite on every push and pull request, then builds the Docker image and smoke-tests `/`, the security headers (CSP SHA-256 hashes with no `'unsafe-inline'`, `nosniff`, `X-Frame-Options: DENY`, COOP), the relative `/admin` redirect and the session-cookie flags (`Secure`/`HttpOnly`/`SameSite=Lax`). Dependabot opens weekly Maven, GitHub Actions and Docker base-image update pull requests.

## Fonts and licensing

- **Instrument Sans** is licensed under the SIL Open Font License 1.1; the license text is included at `src/main/resources/static/fonts/OFL-InstrumentSans.txt`.
- **Y1CandyCore** is the brand face supplied by the project owner and is used **only** for the `oiep` wordmarks in the header and footer. It is a commercial font; confirm the license covers redistribution before publishing the repository or a public instance. The code does not grant rights to it. Every other heading, label, paragraph and user-generated report title uses Instrument Sans, with the system CJK stack for Chinese glyphs.
- No project-wide code/content license file has been added yet. Choose one (for example MIT, Apache-2.0, or AGPL-3.0) before calling the repository “open source” publicly, so forks and self-hosters know their rights.

## Roadmap

- [X]  Core submission and search forms
- [X]  Round/location/mode/outcome tagging and filtering
- [X]  Lightweight usefulness rating
- [X]  Admin moderation dashboard with hide/restore/delete and login protection
- [ ]  Optional lightweight accounts for contributors/moderators
- [ ]  Screenshot/attachment storage with local + optional S3/MinIO dual-write and an outbox reconciler
- [ ]  Export and backup tools so the data stays portable

The platform is designed to run on a single VPS, Docker, or a Raspberry Pi. Cloudflare can front static assets if desired.