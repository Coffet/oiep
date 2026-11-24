# Optional AI companion — independent verification report

**Component:** optional `oiep-backendAI` companion integration in this repository
(`com.oiep.companion/**`, the two generic core extension points, JSPs/assets, read API,
admin proxy, docs/overlay).
**Verifier:** independent adversarial review (teammate `verifier`, task-6).
**Date:** 2026-11-10, Asia/Hong_Kong.
**Verdict:** **PASS.** No open defect. All five defects found by the first pass (D1–D5) were
fixed by their owners and **re-verified on the fixed tree**; the additional stale test count I
found (D6) was also fixed. On the final tree the full test suite is **321 tests, 0 failures,
0 errors, 0 skipped, BUILD SUCCESS**, the WAR packages and boots, the real end-to-end path
(OIEP + packaged uvicorn companion) works, every misconfiguration tested fails closed, and a
simulated full removal leaves a building, booting, green application.

---

## 1. Environment and inputs

| Item | Value |
| --- | --- |
| Host | Windows (PowerShell 5.1.26100), workspace `<workspace>\oiep` |
| JDK | `openjdk 17.0.21 2026-10-20 LTS` |
| Build | Maven wrapper (`mvnw.cmd`) via team helper `target\team\mvn.cmd` (`-o -B -ntp`, offline; injects the sandbox Mockito workaround and a build lock) |
| Framework | Spring Boot `3.5.16` (executable WAR `target\oiep.war`), JSP views, SQLite |
| SQLite engine | `3.53.1` (reported by the Python CLI used for DB inspection) |
| Companion checkout | `<companion-checkout>` @ `52f7b47` (2026-11-10T21:02:31+08:00), **read-only, unmodified** |
| Companion runtime | its venv Python `3.12.14`, `fastapi 0.115.6`, `uvicorn 0.32.1`, exactly one worker |
| Node | `v24.22.1` (independent DOM harness for `companion.js`) |
| OIEP repo | HEAD `6233e1e` plus the uncommitted working tree. That tree contains the companion work **and a separate pre-existing security-hardening change set** (rate limiters, trusted-proxy handling, CSP hashes, backup/restore hardening); a real removal PR must be diffed against the clean base. |
| Final fixed tree | fixes landed 22:02–22:03 (all owners); final verification executed against that state |
| Docker | client available and used for `docker compose config` validation only (containers were not run) |
| Test data | fresh SQLite files under `target\verify-removal\e2e\*`, demo data `OIEP_DEMO_DATA=true` where stated |
| Ports used | OIEP `18082`–`18087`, companion `8091`, stub `8092` |
| Secrets | fresh random 32–48 char values in `target\verify-removal\evidence\env.json` (gitignored); pairs matched exactly as documented |
| Final docs hashes | `docs/COMPANION.md` `0E591C70…`, `README.md` `15C9D7C7…`, `.env.example` `531394DF…`, `compose.companion.yaml` `064C4233…` |

Nothing in `src/` or the companion checkout was modified by this verification. All artefacts are
under `target\verify-removal\` (gitignored) except this report.

## 2. What was verified and how

All claims were reproduced from a freshly packaged WAR and the **real** companion service; a
controllable stub was used only where a specific failure had to be induced deterministically
(401/403/500, redirect, timeout, connection refused, request/header capture). HTTP probes used
`curl.exe` 8.21 and the Python standard library; the companion's own `app.admin_helper.sync`
code was executed for the consumer-side pagination/resume test.

Commands (representative):

```bat
target\team\mvn.cmd -o -B -ntp test                                  :: full suite (main tree and clean copy)
target\team\mvn.cmd -o -B -ntp -DskipTests package                   :: WAR
java -jar target\oiep.war                                            :: boot (env-configured, temp OIEP_DB_PATH)
.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8091 --workers 1
node target\verify-removal\evidence\companion_js_harness.mjs        :: real companion.js in a fake DOM
docker compose -f compose.yaml -f compose.companion.yaml config      :: overlay validation
```

Every probe script and its captured output is listed in [Appendix A](#appendix-a--evidence-files).

## 3. Results

### A. Default-off (no `OIEP_COMPANION_*` environment)

Boot: packaged WAR, fresh DB, `OIEP_DEMO_DATA=true`, `OIEP_ADMIN_PASSWORD` set, nothing else.
**PASS 12/12.**

- `GET /` → `200`, HTML contains no `companion`, no `<iframe`, no companion asset links.
- `Content-Security-Policy` is **byte-identical** (case-sensitive string comparison) to
  `WebConfig.contentSecurityPolicy()`; no `frame-src`.
- `GET /api/v1/experiences` → `404 text/html`; `GET /api/v1/anything` → `404 text/html` (the
  namespace is unclaimed: the component's JSON fallback controller does not exist).
- `/admin/assistant` → `302 Location: /admin/login` (only the core `/admin/**` interceptor; no
  component handler). After a real admin login: `/admin/assistant` → `404`, proxy
  `/admin/assistant/api/health` → `404`, admin dashboard has no "AI helper" nav link.
- SQLite (read-only inspection): tables `reports, sqlite_sequence`; indexes only the three core
  ones; **0 triggers**; no object whose name contains `companion`.
- Startup log contains neither `companion` nor `AI companion` (case-insensitive).
- Other security headers unchanged (`X-Frame-Options: DENY`, etc.).

### B. Fail-closed configuration matrix (5 boots)

All expected-absent surfaces are really absent: no iframe/markup, CSP byte-identical to the base
policy, no registered route (404 even when authenticated), no nav link, no companion DB objects.
**PASS 58/58** (47 pre-fix + the post-fix D3 boot).

| Case | Config under test | Expected | Observed |
| --- | --- | --- | --- |
| B1 | master on, `BASE_URL` valid, user helper valid, **admin token and read token set to documented placeholders** `"<paste-the-operator-token>"` | user helper on; admin + read API fail closed | iframe + CSP `frame-src 'self' http://127.0.0.1:8091`; read API `404` with a valid-shaped token; `/admin/assistant` `404`, no nav link; log `userHelper=true adminHelper=false readApi=false`, warnings `set to a documented placeholder, not a secret` (value never logged) |
| B2 | master on only (no base URL, no tokens) | everything off | CSP identical to base, no iframe; read API `404`; `/admin/assistant` `404` after login; log `baseUrlConfigured=false … userHelper=false adminHelper=false readApi=false` |
| B3 | `BASE_URL` valid, `WIDGET_URL=javascript:alert(1)`, admin + read API valid | user helper off; admin/read on | no iframe, base CSP; read API `200`; `/admin/assistant` `200` (proxy `502` while the companion was stopped, proving the route exists); log `userHelper=false adminHelper=true readApi=true` |
| B4 | master on, everything valid **except** `OIEP_COMPANION_USER_HELPER_ENABLED=false` | helper off, other surfaces on | no iframe/CSP change; read API `200`; admin route `200`; log `user helper is switched off (OIEP_COMPANION_USER_HELPER_ENABLED=false)` |
| B5 (post-fix) | B1 repeat on the fixed tree, specifically re-checking the D3 log key | as B1 | identical fail-closed result; log now says `tokenProblem=set to a documented placeholder, not a secret` (no `resourceProblem`), `adminHelper=false readApi=false` |

### C. Read-only experiences API contract (against the companion's own consumer code)

Contract probe **54/54 PASS** (pre-fix and again post-fix); consumer-side probe with the
companion's real `CorpusSync` **13/13 PASS**; end-to-end sync/tombstone probe **40/40 PASS**
(post-fix re-run).

- Auth: missing/wrong/malformed scheme → `401` JSON `{"error":{"code":"unauthorized"}}` with
  `WWW-Authenticate: Bearer`; the admin token is rejected by the read API; no token echo.
- Headers on success and errors: `Cache-Control: no-store`, `Pragma: no-cache`,
  `X-Robots-Tag: noindex, nofollow`, no CORS headers (also for `Origin: http://evil.example`
  and preflight `OPTIONS`); every 400/401/404 body is JSON.
- Full sync returns all 6 demo reports with the documented fields: `id` `oiep-r<id>`,
  `schema_version interview-experience/v1`, ISO-8601 UTC timestamps, `deleted:false`, one round
  entry with `format:"UNKNOWN"`, constant `MODERATE` difficulty, mapped `work_mode`/`outcome`,
  no hidden-report fields. **Full items expose exactly the documented keys (13 fields including
  `deleted`); the undocumented `tombstone` field is gone (D1 fix).**
- Tombstones expose **exactly** `{id, updated_at, deleted}` (asserted post-fix; see D1).
- Pagination: `page_size=2` → exactly 3 pages, `has_more`/`next_cursor` consistent
  (`next_cursor` non-null iff `has_more`), no duplicate ids, ascending `updated_at`, all 6 ids.
- `page_size` clamp: `0` → 1, `10000` → 500; non-integer → `400 invalid_page_size`.
  `cursor` malformed → `400 invalid_cursor` (never echoed). `updated_since` accepts `Z`,
  offset and zone-less local forms, rejects a date-only value with `400 invalid_updated_since`.
- Unknown `/api/v1/**` path → `404` JSON `not_found`; `POST/PUT/DELETE` to the endpoint →
  JSON fallback (not HTML). **Browser-style `Accept: text/html`, `application/xml` and
  `*/*;q=0.1` now return JSON with `Content-Type: application/json` (200/401/400/404 as
  appropriate) — never the OIEP HTML error page and never a 406 (D2 fix).**
- Consumer-side: the companion's real sync code pulls the live endpoint with `page_size=2`
  (3 pages) inserting 6/6 and clearing its cursor; a bounded run (`max_pages=1`) persists a
  resume cursor and completes 2+2+2 across runs, inserting each item exactly once; an immediate
  incremental re-run is a no-op.

### D. End-to-end with the real companion service (uvicorn, one worker)

**PASS 40/40** (checked pre-fix and re-run post-fix on fresh DBs).

Full sync over the companion's HTTP admin API: `POST /v1/admin/corpus/sync {"full":true}` →
`200 {"status":"ok","inserted":6,"updated":0,"deleted":0}`; companion corpus `6`. Mapped record
verified inside the companion (`/v1/admin/experiences/oiep-r1`): company, role_title,
`difficulty=MODERATE`, `rounds_count=1`, `schema_version`.

Moderation propagation (driven through the real OIEP admin endpoints with session + CSRF):

| Action on OIEP | Read API next full sync | Companion incremental sync | Companion corpus |
| --- | --- | --- | --- |
| hide report 3 | item 3 = tombstone, no full fields | `deleted:1` | 6 → 5, `oiep-r3` → 404 |
| restore report 3 | full item again | `inserted:1` | 5 → 6, `oiep-r3` → 200 |
| hard delete report 5 | item 5 = tombstone | `deleted:1` | 6 → 5, `oiep-r5` → 404 |

Tombstones survive later syncs; replaying the incremental window is a no-op
(`deleted:0 inserted:0`). Companion audit entries contain no token and no message bodies. No
OIEP or companion log line contains either token or the probe chat marker (direct substring
search of both logs).

### E. Admin-helper page and server-side proxy

**PASS 49/49** (security matrix, re-run post-fix against the real companion) + **21/21** (every
documented route against the real companion, post-fix) + **37/37** (boundary matrix against the
controllable stub, pre-fix; the admin package was not touched by the D1/D2 fixes) + **7/7**
(companion down).

- Login → `/admin/assistant` `200`; page contains the same-origin API base and per-session CSRF
  but **no token, no companion origin**; admin dashboard gains the "AI helper" nav link.
- All documented routes work against the real companion: `GET health`, `GET corpus/stats`,
  `GET corpus/sync`, `POST corpus/sync`, `GET audit?limit`, `POST chat` (real answer from
  `deterministic-templates/v1`, conversation-id continuity), `POST query` (real rows; companion
  `400 validation_error` passed through as JSON), `POST summarise` (real headline; an
  out-of-allowlist focus is rejected locally with `400 invalid_request`).
- Proxy JSON responses: `Content-Type: application/json`,
  `Cache-Control: no-store, no-cache, must-revalidate`, `X-Robots-Tag: noindex, nofollow`.
- CSRF: POST without or with a wrong `X-OIEP-CSRF` → `403 {"error":{"code":"csrf_invalid"}}`;
  a valid token placed in the **query string** is rejected (`403`).
- Body handling: non-JSON content type → `415 unsupported_media_type`; body > 64 KiB →
  `413 body_too_large`; invalid JSON / JSON array / > 4000-char chat message → `400`.
- Failure mapping: companion `401`/`403` → `502 companion_auth_failed` with a generic message and
  the companion body never echoed; `5xx`, redirects (not followed) and non-JSON bodies →
  `502 companion_unavailable`; transport failures/timeouts → `502 companion_unavailable`
  (configured 800 ms timeout observed at 0.81 s). Companion down (real service stopped):
  proxy `502 JSON`, admin page still renders, public site and the read API keep working.
- SSRF: path traversal attempts (`../`, `%2e%2e`) and unknown paths → `404`; every upstream
  request observed by the stub was on the fixed `/v1/admin/*` allowlist; redirects never
  followed.
- Transport hygiene (stub-observed request): `HTTP/1.1`, **no h2c `Upgrade`/`HTTP2-Settings`**
  (regression check for the JDK-client fix), `Authorization: Bearer <configured token>` present,
  **no `Cookie`, no OIEP session/CSRF/Referer/Origin header forwarded**; POST bodies reduced to
  allowlisted top-level fields and no query string injected by the browser.
- Token/body logging: neither token nor a unique probe chat marker appears in the OIEP or
  companion logs; warnings contain only the server-constructed route and a fixed reason.

### F. User-helper bridge (public form page)

**PASS.**

- Enabled boot with `OIEP_COMPANION_WIDGET_URL=http://127.0.0.1:8091/ui/`: `GET /` contains
  exactly one server-rendered `<iframe>` with the configured URL,
  `sandbox="allow-scripts allow-forms allow-same-origin"`, `loading="lazy"`,
  `referrerpolicy="no-referrer"`, an accessible title and an `aria-describedby` hint; the
  `data-companion-*` strings and stylesheet/script links are present. No JS is needed for the
  iframe to render.
- CSP gains **exactly one** `frame-src 'self' http://127.0.0.1:8091` directive; no wildcard, no
  `unsafe-inline`, the rest of the base policy unchanged.
- `GET /js/companion.js` `200 text/javascript`, `/css/companion.css` `200 text/css` (same for the
  admin assets).
- zh-HK (`Cookie: OIEP_LANG=zh-HK`): `<html lang="zh-HK">`, translated helper copy from
  `messages-companion_zh_HK.properties`, English copy absent, iframe still present.
- Companion `/ui/` → `200` with **no** `X-Frame-Options`/`frame-ancestors` (so the cross-origin
  frame is embeddable), `Cache-Control: no-store`.
- Real `companion.js` audited by source and by an independent fake-DOM harness (**36/36 PASS**):
  no `innerHTML`/`outerHTML`/`document.write`/`eval`/`new Function`; only `textContent`; request
  posted to the exact origin; messages ignored unless `event.origin === configured origin`
  **and** `event.source === iframe.contentWindow`; only empty text fields filled (never
  overwritten), radio groups only when unanswered, checkboxes additive only; over-`maxlength`
  values skipped; nothing submitted (no submit handler and no `form.submit` call); 12 s timeout
  shows the fallback message; script inert when no origin is configured.

### G. Component isolation and full-removal simulation

**PASS** (pre-fix and again on the fixed tree).

- Isolation grep (source of truth: `git status` + recursive search):
  - `com.oiep.companion` appears **nowhere** in core Java (outside the component package).
  - No core JSP/fragment references the component; only the two generic `pageExtensions` loops.
  - No core message bundle, static asset or `site.js` reference.
  - The only `companion` strings in core Java are doc-comment examples on the two generic
    extension points (`CspContributor.java:10`, `PageExtension.java:31`); they are inert.
- Full removal simulated in a **copy** (`target\verify-removal\repo`, 161 `src` files, copied
  without `.git`/`target`/`data`/`backups`/`security`), following `docs/COMPANION.md`
  §"C. Full removal": delete the documented component paths, revert the documented core edits
  (application.properties companion block + messages basename, the `index.jsp` loop, the
  `admin-topbar.jspf` loop), leave the documented-to-keep generic hooks.
  - Final post-fix run: 15 items deleted (12 component paths + 3 core edit groups); fixed
    isolation grep: no leftover component-named file, **0** `com.oiep.companion` imports, all 12
    documented paths absent.
  - `mvnw.cmd -o -B -ntp test` → **141 tests, 0 failures, 0 errors, BUILD SUCCESS**
    (321 − 180 component tests).
  - `mvnw.cmd -o -B -ntp -DskipTests package` → WAR built (BUILD SUCCESS).
  - WAR boot with **stale companion environment variables deliberately still set** (master on,
    base URL, widget URL, both tokens): `/` `200` with no marker/iframe, CSP byte-identical to
    base, `/api/v1/experiences` `404`, `/admin/assistant` `302` unauthenticated / `404` after
    login, `/js/companion.js` and `/css/companion.css` `404`, DB has no companion objects, log
    has no companion line. The generic hooks remain and render nothing.

### H. Database removal artefacts (uninstall SQL)

**PASS 17/17** (checked pre-fix and re-run post-fix).

- `scripts/companion-uninstall.sql` drops exactly the five component objects (3 triggers, the
  ordering index, the table) and leaves `reports` and its data untouched; running it twice is
  idempotent; core `DELETE` works afterwards.
- **Ordering risk is real and was reproduced, not just reasoned about:** dropping only the table
  while leaving the triggers makes every core `DELETE`/`INSERT` on `reports` fail with
  `sqlite3.OperationalError: no such table: main.companion_report_events`; running the
  documented script afterwards recovers the database. This matches the documented reason to run
  the SQL **before** removing the code.
- Trigger behaviour directly (SQL on a DB copy): INSERT records `deleted=0`; status change
  records `deleted=1`; non-status updates do not fire; DELETE keeps a tombstone after the report
  row is gone (the event row outlives the report, so a later full sync can still delete the
  mirror row).

### I. Packaging, full test suite, docs and deployment artefacts

- **Final full suite on the fixed tree: 321 tests, 0 failures, 0 errors, 0 skipped, BUILD
  SUCCESS.** This equals the pre-fix 314 + 7 net new read-API tests (read-API tests 75 → 82);
  the 29 companion test classes now contain 180 tests (321 − 141 non-component tests).
  The same number is reported by the Lead's own final run on the main tree. A late,
  companion-unrelated translation polish to `messages_zh_HK.properties` landed after the first
  final run; the suite was re-run on that exact working tree and is still **321 green**
  (`target\verify\final2-test.log`).
- `mvnw.cmd -o -B -ntp -DskipTests package`: **BUILD SUCCESS** on the fixed tree in the main
  repository (post-fix, 22:07) and again from a clean copy for the final run (WAR builds and
  boots; `target\verify-removal\repo-full\target\oiep.war`).
  *Environment note:* at the very end of the session a process from another agent session
  remained on `127.0.0.1:18092` (PID 19896) and kept `target\oiep.war` locked; it is **not** one
  of this verifier's jobs and could not be stopped from this session (`Access denied` on
  `taskkill`). The final suite/package evidence was therefore produced from a byte-identical
  clean copy (`target\verify-removal\repo-full`), and the main-tree WAR from the post-fix build
  at 22:07 is intact.
- Docs accuracy (cross-checked against code/config on the final docs):
  - All **13** `OIEP_COMPANION_*` variables in `application.properties` appear in
    `docs/COMPANION.md`, `.env.example` and `compose.companion.yaml`; no documented
    `OIEP_COMPANION_*` name is unknown to the code.
  - Every documented default matches `application.properties`
    (false/empty/true/`<base-url>/ui/`/false/empty/false/empty/200/MODERATE/constant/3000/15000).
  - All 8 documented `/admin/assistant*` routes and all 8 companion `/v1/admin/*` routes match
    `AdminAssistantController`; the read-API path matches
    `ReadApiController.EXPERIENCES_PATH`.
  - Documented companion-side variables all exist in the companion's `app/config.py` (except the
    three Compose-only interpolation variables `OIEP_AI_IMAGE`, `OIEP_AI_BUILD_CONTEXT`,
    `OIEP_AI_PUBLISH_PORT`, correctly described as overlay settings).
  - The documented uninstall SQL snippet contains `DROP TRIGGER` ×3 + `DROP INDEX` + `DROP TABLE`
    in the correct order; the documented removal file list covers every component-named file.
  - All relative Markdown links in `docs/COMPANION.md`, `README.md` and `docs/DEPLOY.md` resolve.
  - **D4 fix confirmed:** soft-disable/README sections now state that the companion static asset
    URLs remain served (inert) while the component is disabled and disappear only on removal.
  - **D5 fix confirmed:** the full-removal procedure now also tells the operator to delete the
    companion sections from `README.md`, `.env.example` and `docs/DEPLOY.md` and to restore the
    README test count (it names the 141-test removal baseline).
  - **D6 fix confirmed:** README's test count is now `321 tests`, matching the final suite.
  - `docker compose -f compose.yaml -f compose.companion.yaml config` validates; the rendered
    config contains all 13 `OIEP_COMPANION_*` app variables, the token-pair mappings
    (`OIEP_AI_ADMIN_TOKEN` ← `OIEP_COMPANION_ADMIN_TOKEN`,
    `OIEP_AI_OIEP_SYNC_TOKEN` ← `OIEP_COMPANION_READ_TOKEN`), a loopback-only published companion
    port and the hardened `ai` service (`cap_drop: ALL`, `read_only`, pids limit, tmpfs).
- No new dependency was added to `pom.xml` (the proxy uses JDK `java.net.http`).

## 4. Defects

| # | Severity | Defect | Evidence / location | Final status |
| --- | --- | --- | --- | --- |
| D1 | Low–Medium | Read API emitted an **undocumented extra JSON field `tombstone`** on every item (`"tombstone":false` on full items, `"tombstone":true` on tombstones), while `docs/COMPANION.md` said tombstones carry only `id`, `updated_at`, `deleted`. Caused by the `isTombstone()` record getter being serialized by Jackson. | `src/main/java/com/oiep/companion/read/ExperienceItem.java:54` (pre-fix); observed response `target/verify-removal/evidence/readitem.json`; doc claim `docs/COMPANION.md:578-579` (pre-fix) | **FIXED** (`@JsonIgnore`), **re-verified**: full items now expose exactly the documented keys (no `tombstone`); tombstones exactly `{id, updated_at, deleted}`; real companion full sync still 6/6; suite green. Evidence: `postfix-read-api-fixes.txt`, `postfix-e2e-sync-check.txt`. |
| D2 | Low | Read API returned an **HTML error page** for content-negotiation failure: `Accept: text/html` (or `application/xml`) on `GET /api/v1/experiences` → `406` with the full OIEP HTML error page. `/api/v1` error responses must be JSON. | `src/main/java/com/oiep/companion/read/ReadApiController.java:42-54`, `ReadApiExceptionHandler.java:34-39` (pre-fix); evidence `readapi-406.hdr/.body` | **FIXED** (JSON content type preset in the controller, fallback controller and exception handler), **re-verified**: `Accept: text/html`, `application/xml`, `*/*;q=0.1` → JSON (`200`/`401`/`400`/`404`); no HTML; 400/401/404 JSON contract unchanged. Evidence: `postfix-read-api-fixes.txt`, `postfix-read-api-fixes2.txt`. |
| D3 | Cosmetic | Startup log key typo `resourceProblem=` in the admin-helper misconfiguration warning (should read as a token/secret problem). | `src/main/java/com/oiep/companion/CompanionBootstrap.java:51` (pre-fix) | **FIXED** (`tokenProblem=`), **re-verified** in a fail-closed boot log (B5). |
| D4 | Low (docs) | Soft-disable claim "no component routes … behaves exactly as if the component did not exist" was not literally true: the component's static assets stay on the classpath and are served (`GET /js/companion.js` → `200`) while the component is disabled or fail-closed; they are `404` only after the files are deleted. Harmless (inert files, no data) but observable. | `docs/COMPANION.md:7-9`, `:700-708`, `README.md` (pre-fix) | **FIXED** by docs owner: `docs/COMPANION.md:705` and `README.md:116` now state the static asset URLs remain served (inert) while disabled and disappear only on removal. |
| D5 | Low (docs) | Full-removal procedure did not tell the operator to also clean the companion sections in `README.md`, `.env.example`, `docs/DEPLOY.md` (and to fix the README test count after the component's tests disappear), leaving stale documentation. | `docs/COMPANION.md` pre-fix "Step 3/Step 4" | **FIXED** by docs owner: Step 4 now lists the three doc cleanups and the 141-test removal baseline (`docs/COMPANION.md:796-805`). |
| D6 | Cosmetic (docs) | After the fixes the suite grew to 321 tests while `README.md` still claimed 314 in the project structure block. Found by this verifier during the fix regression. | `README.md:254` (pre-fix count) | **FIXED** by Lead: README now says `321 tests` (verified by hash + grep on the final docs). |

No defects were found in: default-off isolation, fail-closed conditions, read-token
constant-time comparison, hidden-report filtering, tombstone propagation, the admin proxy's
CSRF/allowlist/size/content-type checks, token/body logging, cookie forwarding, path control
(SSRF), or the DB uninstall script.

### Post-fix regression (final tree)

Re-run after all fixes landed:

- [x] Full suite: **321 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (clean copy of the
  final tree; Lead's own run on the main tree reports the same) — `target\verify\final-full-test.log`.
- [x] Read API contract: 54/54 PASS again — `postfix-read-api-check.txt`.
- [x] D1: no `tombstone` field; full items exactly the documented key set; tombstones exactly
  `{id, updated_at, deleted}` — `postfix-read-api-fixes.txt` (13/13 PASS).
- [x] D2: `Accept: text/html` / `application/xml` / `*/*;q=0.1` return JSON (no 406, no HTML);
  unauthenticated `Accept: text/html` → JSON 401 with `WWW-Authenticate` — `postfix-read-api-fixes.txt`.
- [x] Tombstones end-to-end: real companion full sync 6/6, hide → `deleted:1` corpus 5, restore →
  `inserted:1` corpus 6, hard delete → `deleted:1` corpus 5 — `postfix-e2e-sync-check.txt` (40/40 PASS).
- [x] Admin proxy against the real uvicorn companion: 49/49 PASS on the fixed build
  (`postfix-admin-proxy-check.txt`), all documented routes 21/21 PASS (`postfix-proxy-routes.txt`),
  user-helper iframe/CSP unchanged on the fixed build.
- [x] Fail-closed + D3: placeholder-token boot still disables admin helper/read API and logs
  `tokenProblem=` — `postfix-case-d3.txt` (11/11 PASS).
- [x] DB uninstall/ordering: 17/17 PASS re-run — `postfix-db-removal-check.txt`.
- [x] Removal simulation on the fixed tree: 141 tests green, WAR built, boots with no companion
  surface — `postfix-removal-test2.log`, `postfix-removal-package.log`.
- [x] Docs D4/D5 present; README count 321; all relative links resolve — `postfix-doc-links.txt`.

## 5. Residual risks and verification limits

Things that are true of the component/setup but were not fully exercised here, or that an
operator must keep in mind:

1. **No real browser was used.** The iframe/CSP/postMessage flow was verified by response
   headers, server-rendered HTML, source audit and an independent DOM harness, not by a real
   browser rendering. In particular the companion's public `/ui/` page must stay frameable (it
   has no `X-Frame-Options`/`frame-ancestors` today) and the operator must not add such a header
   without allowing the OIEP origin (`docs/COMPANION.md` troubleshooting says this).
2. **Docker containers were not run.** `docker compose config` validates the overlay and the
   rendered environment/token-pair mapping, but the companion image build/run, the `read_only`
   root filesystem and the `cloudflared` routing were not exercised. The companion's
   `/v1/admin/` public-exposure requirement remains operator configuration.
3. **Companion runtime was `OIEP_AI_ENV=dev`.** The companion's prod fail-closed checks
   (32+ char admin token, IP-hash salt, `https://` sync/CORS URLs) are its own and were not
   re-run. OIEP now requires HTTPS for non-loopback admin connections unless insecure HTTP is
   explicitly enabled; that opt-in leaves the operator bearer token exposed on the network.
4. **Browser-level CSRF/session semantics** were exercised over plain HTTP with `Secure` cookies
   off (dev), as in the documented local quick start.
5. **Response-body cap is checked after buffering.** `HttpCompanionClient` reads the full
   companion response with `BodyHandlers.ofString` and only then rejects bodies > 2 MiB
   (`HttpCompanionClient.java:91-101`). The companion is a trusted local service, so this is a
   robustness nit, not an exploit; a compromised companion could still force a large allocation.
6. **Pagination consistency under concurrent moderation.** A full sync pages over a live event
   log; an update that moves an event between requested pages can make a full pass see it later
   than expected (keyset order is stable per row and the companion's upserts are idempotent, so
   the mirror converges on the next sync). Inherent to cursoring over a mutable log; not
   observed as a failure.
7. **Same-origin widget misconfiguration.** The iframe sandbox is
   `allow-scripts allow-forms allow-same-origin`. For a widget on a **different** origin this is
   safe (cross-origin content cannot reach the parent DOM), but an operator who points
   `OIEP_COMPANION_WIDGET_URL` at an OIEP-owned same-origin path would let the framed document
   escape the sandbox. The docs correctly specify a separate widget origin; configuration-
   dependent residual risk, not a defect in the shipped shape.
8. **Static companion assets are always on the classpath** (see D4); "disabled" means "inert",
   not "404", until the files are deleted.
9. **Hard-deleted reports keep a tombstone row forever** (`companion_report_events`) — one row
   per report id, bounded by report count, with no pruning step documented as a maintenance item.
10. The removal simulation edits the same files (`application.properties`, `index.jsp`,
    `admin-topbar.jspf`) that carry unrelated pre-existing uncommitted changes in this working
    tree; the copies were made from the full working tree, so a real removal PR must be diffed
    against the clean base.
11. **Environment interference at the very end:** another agent session's OIEP process on
    `127.0.0.1:18092` (PID 19896) could not be stopped from this session and kept
    `target\oiep.war` locked; final suite/package evidence was therefore produced from a
    byte-identical clean copy. A maintainer should confirm no process holds the WAR before
    committing/packaging on this machine.

## 6. Cleanup

All of this verifier's service jobs (OIEP `java -jar` instances on 18082–18087, the companion
uvicorn on 8091, the stub on 8092) were stopped; none of this verifier's processes/listening
ports remain. Scratch databases, scripts and captured evidence are under
`target\verify-removal\` (gitignored), including the removal copy and the clean copy
(`repo-full`). No implementation file was modified by this review; this report is the only
tracked file it added.

## Appendix A — evidence files

All under `target\verify-removal\evidence\` (gitignored); key files:

| File | Contents |
| --- | --- |
| `read-api-check.txt`, `postfix-read-api-check.txt` | 54-check read-API contract probe (pre-fix / post-fix) |
| `postfix-read-api-fixes.txt`, `postfix-read-api-fixes2.txt` | D1 exact-key + D2 Accept-matrix focused checks (13/13, post-fix) |
| `companion-sync-probe.txt` | companion's own `CorpusSync` multi-page/resume probe (13 checks) |
| `e2e-sync-check.txt`, `postfix-e2e-sync-check.txt` | real-service full sync + hide/restore/delete E2E (40 checks, pre/post-fix) |
| `admin-proxy-check.txt`, `postfix-admin-proxy-check.txt` | admin page/proxy security matrix (49 checks, pre/post-fix) |
| `proxy-routes-check.txt`, `postfix-proxy-routes.txt` | every documented proxy route against the real companion (21 checks) |
| `proxy-stub-check.txt` | proxy boundary matrix against the controllable stub (37 checks) |
| `proxy-down-18082.txt`, `proxy-down-18083.txt` | proxy 502 when the companion is down |
| `case-b1.txt` … `case-b4.txt`, `postfix-case-d3.txt` | fail-closed configuration matrix boots |
| `defoff_*`, `home.headers`, `home.html` | disabled-by-default HTTP evidence |
| `readitem.json`, `readapi-406.*`, `postfix-home.html/.hdr` | D1/D2 pre-fix evidence; post-fix page/CSP |
| `db-removal-check.txt`, `postfix-db-removal-check.txt` | uninstall SQL + ordering-risk + trigger behaviour (17 checks, pre/post-fix) |
| `js-harness.txt`, `companion_js_harness.mjs` | independent DOM harness for `companion.js` (36 checks) |
| `removal-script.log`, `postfix-removal-script.log`, `postfix-removal-test2.log`, `postfix-removal-package.log` | simulated full removal (pre-fix and fixed tree) in `target\verify-removal\repo` |
| `final-full-test.log`, `final-package.log`, `final2-test.log` | final fixed tree: 321-test suite (clean copy and the main tree after the late translation edit) + package from a clean copy |
| `compose-config.yaml`, `doc-links-check.txt`, `postfix-doc-links.txt` | overlay validation, doc link checks |
| `env.json` | per-run test tokens (scratch only, never committed) |

Probe scripts (same directory): `check_read_api.py`, `check_read_api_fixes.py`,
`check_e2e_sync.py`, `companion_sync_probe.py`, `check_admin_proxy.py`,
`check_proxy_routes.py`, `check_proxy_stub.py`, `check_proxy_down.py`,
`check_config_case.py`, `check_db.py`, `check_db_removal.py`, `check_doc_links.py`,
`companion_js_harness.mjs`; removal artefacts: `target\verify-removal\remove_component.ps1`,
`target\verify-removal\repo\` (removed-component copy), `target\verify-removal\repo-full\`
(clean copy of the final tree used for the final suite/package).
