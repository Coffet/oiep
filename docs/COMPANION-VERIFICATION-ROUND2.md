# Companion security round 2 — bolder penetration test, fixes and verification

Date: 2026-11-11. Scope: the newly loaded optional AI companion (`oiep-backendAI`), its
OIEP integration (user-helper embed, read-only experiences API, `/admin/assistant` proxy)
and the deployed Docker Compose system. This round deliberately went beyond the earlier
docs-level verification: live browser exploitation, raw-HTTP attacks, container/secret
inspection, and adversarial DB/moderation scenarios. Four independent adversarial auditors
worked disjoint scopes; the Lead independently attacked the live system and the emitted
traffic/proxy paths, then implemented and verified the fixes.

Test counts after this round: **OIEP app: 330 JUnit tests, 0 failures** (`./mvnw -B -ntp verify`,
`BUILD SUCCESS`); **companion: full pytest suite green** (all tests, including the new
regression tests below). Live Docker system re-verified after rebuild (see §4).

## 1. Findings and disposition

Severity is the adversarial assessment; all items below are **fixed and tested** unless the
disposition says otherwise. Companion-repository files are named relative to the sibling
checkout `../oiep-backendAI`.

| # | Sev | Finding (attack) | Fix | Regression coverage |
|---|---|---|---|---|
| R2-1 | **High** | An arbitrary web origin that frames `/ui/` could request the visitor's complete structured `InterviewExperience` over `postMessage` (proven in headless Edge; the widget replied to whoever asked, and `/ui/` had no frame protection). | Widget replies only to a configured parent-origin allowlist (`OIEP_AI_WIDGET_PARENT_ORIGINS`), only when `event.source === window.parent`; `/ui/` emits CSP `frame-ancestors` for those origins and **`frame-ancestors 'none'` + `X-Frame-Options: DENY` when unset** (fail closed). `web/chat.js`, `app/main.py`, `app/config.py`, `web/index.html`, `/ui/config.js`; overlay/.env/docs wiring. | `tests/test_widget_security.py` (CSP/config.js/fail-closed); browser re-test by the web auditor (frameguard 3/3, fail-closed 6/6, legitimate hand-off 12/12) |
| R2-2 | **High** | Improvement-consent withdrawal/erasure became impossible after the session TTL expired: the opted-in improvement sample survived (structured form incl. free text, 30-day retention) and both visitor `DELETE` and operator erasure returned 404. | Session expiry writes an erasure receipt when a sample is still retained; a later self-serve `DELETE` (or operator erasure) verifies the token against that receipt and deletes the residual sample. `app/privacy/erasure.py`, `app/privacy/retention.py`, `app/core/sessions.py`; privacy docs updated. | `tests/test_privacy.py::test_withdrawal_survives_session_expiry` |
| R2-3 | **Medium** | Request-body cap bypassed with `Transfer-Encoding: chunked` (no `Content-Length`): 300 KiB–32 MiB JSON accepted unauthenticated vs 413 for the same `Content-Length` body → memory/CPU DoS on the single worker. | New bounded pure-ASGI body-limit middleware reads chunked bodies under the cap and replays them; over-cap requests get a JSON 413 before parsing. `app/core/body_limit.py`, `app/main.py`. | `tests/test_body_limit.py` (chunked over-cap 413, under-cap replay, CL 413, 415) |
| R2-4 | **Medium** | Configuration fail-open: `OIEP_AI_ENV` values other than exactly `prod` (e.g. `production`, `staging`) silently ran the non-production posture, including the published dev admin token; invalid CIDRs/URLs/negative limits were silently ignored (e.g. a typo in the admin IP allowlist disabled it). | `Settings.validate()` accepts only `dev`/`prod`, validates every CIDR/URL/numeric bound, requires sync credentials when sync is enabled; production requires strong admin token/salt and https origins (loopback http allowed for the local Docker demo). `app/config.py`. | `tests/test_config_validation.py` |
| R2-5 | **Medium** | Trusted-proxy defaults trusted broad RFC1918 ranges + loopback, so a peer that can reach the published port could spoof `X-Forwarded-For`: reset per-IP rate limits, bypass the admin IP allowlist/auth-failure throttle and forge audit IP hashes; rotating identities could also grow the limiter map. | Secure default: the overlay now trusts **no** proxy headers; operators set the exact cloudflared/proxy address/subnet. The resolver prefers `CF-Connecting-IP` from a trusted peer and only walks XFF right-to-left from trusted peers. The rate-limiter window map is hard-bounded with eviction (global window protected). A production warning fires for broad trusted ranges. `compose.companion.yaml`, `.env.example`, `app/core/security.py`, `app/core/rate_limit.py`, `app/main.py`, docs. | `tests/test_client_ip.py`; `tests/test_rate_limit.py` (bounded map); live XFF probe shows no fresh bucket |
| R2-6 | **Medium** | Unauthenticated audit-log flood/poisoning: every `/v1/admin/*` request wrote an audit row before authentication, so 404/405 probes created rows at request rate and could bury real operator rows. | Audit rows are written only for requests that reach the admin auth dependency (successful or failed auth); unknown admin paths produce no row. `app/api/deps.py`, `app/main.py`. | `tests/test_audit_privacy.py` |
| R2-7 | **Medium** | Read-API sync watermark was wall-clock/non-monotonic: a future `created_at` (import/clock skew) seeded a companion watermark above later events, so incremental sync silently missed hide/delete tombstones and the companion kept serving hidden content. | Trigger event times are strictly monotonic (`MAX(now, max(event_ms)+1)`); backfill clamps future `created_at` to boot time. `ReadApiEventStore.java`; docs require full resync after restore/uninstall gaps. | `ReadApiMonotonicEventTests` |
| R2-8 | **Medium** | Known Starlette CVE-2026-48710 ("BadHost", starlette 0.41.3 < 1.0.1) let a malformed `Host` header desynchronise admin-path classification (audit/no-store/noindex) in the proxy path. | Dependencies bumped to `fastapi==0.162.3` / `starlette==1.10.0`; admin classification now uses a normalised ASGI scope path, not the Host-influenced URL object; `/openapi.json` is disabled in production. `requirements.txt`, `pyproject.toml`, `app/main.py`; companion repo docker/nginx/systemd docs updated. | Full companion suite on the upgraded stack; `tests/test_request_hardening.py` (malformed Host, double slash, prod OpenAPI 404) |
| R2-9 | **Medium** | Data-at-rest permissions were 0755 directories / 0644 SQLite/WAL files despite the documented 0700/0600 privacy control; backups inherited the loose mode. | Data dir 0700, DB/WAL/SHM and backups 0600 (POSIX), container entrypoint `umask 077`; documented in SECURITY/DEPLOYMENT. `app/db.py`, `app/config.py`, `deploy/docker/Dockerfile`. | `tests/test_request_hardening.py` (POSIX modes); live container check `/data` 700, DB/WAL/SHM 600 |
| R2-10 | **Medium** | Embedded "Delete my chat" was silently dead: the OIEP iframe sandbox lacked `allow-modals`, so the widget's `window.confirm()` was ignored — the privacy/consent-withdrawal control no-opped without feedback. | `sandbox="allow-scripts allow-forms allow-same-origin allow-modals"` in `user-helper.jsp`; fragment/end-to-end tests updated. | `CompanionUserHelperFragmentContractTests`, `CompanionUserHelperEnabledEndToEndTests` |
| R2-11 | Low | Companion response bodies were fully buffered before the 2 MiB ceiling (character count, not bytes) — a compromised/misbehaving companion could pressure OIEP memory. | Responses are streamed and read up to the byte ceiling; a response containing the operator token is refused (defence against credential reflection into the browser). `HttpCompanionClient.java`. | `HttpCompanionClientTests`, existing `CompanionHttpClientTests` |
| R2-12 | Low | Read API had no throttle/lockout: 120 failed bearer attempts completed in 0.37 s with no 429, and the public endpoint is reachable through OIEP's public ingress. | Per-client sliding-window throttle (120/min, counting failed auth) returning JSON `429 rate_limited` + `Retry-After`; bounded bucket map. `ReadApiController.java`, `ReadApiRateLimiter.java`, `application.properties`, compose overlay/env docs. | `ReadApiRateLimitTests`; live 429 at attempt 120 |
| R2-13 | Low | `/openapi.json` was public and advertised the full admin surface; uvicorn kept a server banner and an access log with raw peer IPs (contradicting "hashed IPs only"). | OpenAPI disabled in production; container entrypoint uses `--no-access-log --no-server-header`; app JSON logs keep salted IP hashes. | prod test for 404; live header/log checks |
| R2-14 | Low | Message-cap race: concurrent turns for the last slot could overshoot the per-session cap (31/30). | Atomic conditional `UPDATE ... WHERE message_count < cap` reservation with release on failure. `app/core/sessions.py`, `app/api/user.py`. | `tests/test_privacy.py::test_reserve_turn_is_atomic` |
| R2-15 | Low | Receipt docs claimed a "keyed" subject hash while the code used an unkeyed SHA-256 of a random 96-bit session id; docs also overstated read-token scope ("published reports only, no more") while tombstones expose moderation metadata (ids/timestamps). | Docs corrected in `docs/PRIVACY.md`, `docs/SPEC.md`, OIEP `docs/COMPANION.md` (including full-resync guidance after restore/uninstall gaps). | Doc-reality acceptance tests |
| R2-16 | Info | Duplicate `Cache-Control`/`Pragma`/`X-Robots-Tag` headers on proxy responses; container-level (oversized header) rejections return Tomcat HTML rather than the JSON envelope. | Documented as accepted residual (no security effect); JSON contract holds for all application-level responses. | — |
| R2-17 | Info | Read-API event table had no schema version and a foreign/incompatible pre-existing table aborts startup with a raw SQL error. | Documented in the companion docs (restore/uninstall); startup failure is fail-closed. | — |

Additional OIEP-side hygiene in this round: the read API now throttles before token
comparison; the proxy refuses credential reflection; the companion `postMessage` bridge
requires the exact request id (`pendingRequestId`), so late/unsolicited messages cannot
change the visitor's form; the read API event times remain monotonic; and the deployment
documentation covers the new trusted-proxy, allowlist and resync requirements.

## 2. Deliberately verified as holding (not assumed)

Independent auditors re-attacked the core boundaries and recorded them as verified
claims: constant-time token/credential checks and SHA-256-only session-token storage;
session IDOR/cross-session token rejection; erasure idempotency and content-free receipts
while the session is alive; admin Bearer-only (Basic/query-token/cookies rejected, tokens
never echoed, failed-auth throttling); query-DSL injection resistance (allowlisted
fields/operators, bound parameters, LIKE escaping); read-API auth variants incl. duplicate
`Authorization`/header/cookie/query smuggling, JSON-only contract, no CORS, no token echo;
hidden/deleted report **content** never returned (exact tombstones only); proxy CSRF
header-only enforcement, body/content-type/route allowlists, no SSRF, 502 mapping with no
body/token leakage; DOM-XSS absence in the widget (hostile API responses rendered with
`textContent` only); autofill is non-forgeable, empty-only, honeypot-safe and never submits;
no secret in git history/worktree/logs/image layers; container non-root + read-only +
capability-free + loopback publish; retention sweeper and backups behave as documented.

## 3. Residual risks (accepted, not exploitable in the shipped shape)

- In-process rate limits/session state remain single-worker by design; a distributed
  botnet (many real IPs) can still consume the global public bucket (bounded DoS, no data
  exposure).
- The proxy's last-resort guard maps unexpected failures to JSON 500; container-level
  rejections (e.g. oversized headers) are Tomcat HTML (no secrets).
- `OIEP_AI_ENV=prod` requires the operator to set the exact proxy/cloudflared address and
  the public OIEP origin for the widget hand-off; unset values fail closed (widget not
  frameable, hand-off disabled) but break the embed until configured — documented.
- Read-token rotation requires a full companion resync; moderation taken while the read
  API was uninstalled cannot be reconstructed (documented).
- Backups inherit the companion's retention; operator must re-apply erasures after a
  restore (documented; online `.backup` verified).
- Third-party dependencies remain a supply-chain surface; the CVE fixed here is pinned and
  `pip-audit` in CI is recommended in the companion docs.

## 4. Live verification (after rebuild and redeploy)

Rebuilt with `docker compose -f compose.yaml -f compose.companion.yaml up -d --build`;
both `oiep-app-1` and `oiep-ai-1` healthy. Verified (`target/audit/round2-live-verify.txt`
and the admin/perms checks):

- Companion: prod mode; `/openapi.json` 404; `/ui/` CSP `frame-ancestors
  http://127.0.0.1:8080` + HSTS/Permissions-Policy/nosniff; `/ui/config.js` allowlist
  without secrets; widget `event.source === window.parent` guard; admin API 401 without a
  token; unknown admin path 404 without audit rows; chunked and Content-Length over-cap
  bodies both 413; spoofed `X-Forwarded-For` does not create a fresh rate-limit bucket;
  session create/turn/erase flow works; container `/data` 700 and DB/WAL/SHM 600;
  `fastapi 0.162.3`, `starlette 1.10.0`; no server banner/access-log lines.
- OIEP: form page CSP contains the widget `frame-src` and the helper iframe carries the
  `allow-modals` sandbox; read API returns JSON, 429+`Retry-After` at attempt 120, token
  never echoed; admin login + `/admin/assistant` page + CSRF-protected chat proxy work,
  responses carry no companion token; app container `/data` 700 and DB files 600.
- Test suites: app `./mvnw -B -ntp verify` → `BUILD SUCCESS`, 330 tests, 0 failures;
  companion full pytest suite green (includes the new regression tests).

## 5. Evidence index

- `security/companion-api.md`, `security/integration.md`, `security/companion-infra.md`,
  `security/companion-web.md` — independent adversarial reports (severity, PoCs, fixes,
  verified-claims sections, evidence indexes).
- `security/harness/companion-*`, `security/harness/integration-*` — re-runnable harnesses.
- `target/audit/ai-api/`, `target/audit/integration/`, `target/audit/companion-web/` —
  raw captures, screenshots and fake-server logs.
- `target/audit/lead-pentest-*.py`, `target/audit/round2-live-verify.py`,
  `target/audit/round2-admin-verify.txt` — Lead live-attack and post-fix verification.
