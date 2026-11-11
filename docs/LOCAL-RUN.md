# Run OIEP locally

OIEP is two independent processes and an optional model:

| Piece | What it is | Runs | Needed for |
| --- | --- | --- | --- |
| **OIEP app** | Java 17 / Spring Boot + JSP, SQLite | Docker **or** bare metal (`mvnw` / `java -jar`) | the site itself |
| **AI companion** (`oiep-backendAI`) | FastAPI user-helper widget, read-only experiences API, admin-helper proxy | Docker **or** Python source (`uvicorn`) | the floating chat helper and `/admin/assistant` |
| **LLM** | optional local [Ollama](https://ollama.com) model used by the companion | host process | conversational question wording + LLM slot extraction |

The LLM is a **companion-side** setting (`OIEP_AI_MODEL_BACKEND=ollama`); the OIEP app never runs a
model. `structured-extractive` (the default) needs no model at all. The `OIEP_COMPANION_*` variables
only decide whether/how the app embeds the companion, so switching the LLM on or off never changes
the OIEP app's configuration.

> The OIEP Docker overlay deliberately does **not** include Ollama: the companion validates that
> `OIEP_AI_LLM_URL` is a loopback address, which a bridge-network container cannot provide
> (`127.0.0.1` inside the container is the container itself). For the LLM locally, run the
> companion from source next to the host's Ollama and keep the app in Docker (the hybrid in §2.3),
> or use the companion's own `--profile ollama` deployment on a Linux host.

---

## 0. Prerequisites

| Need | Requirement |
| --- | --- |
| Docker paths | Docker Desktop (or Docker Engine) running |
| Bare-metal app | JDK 17+; Maven is not needed (wrapper included) |
| Companion | Python 3.11+ with its `.venv` (`pip install -r requirements-dev.txt` in the companion repo), or its Docker image |
| LLM only | Ollama + a local model, e.g. `qwen2.5:1.5b-instruct-q4_K_M` |

The companion is a **sibling checkout** of [`oiep-backendAI`](https://github.com/Coffet/oiep-backendAI):
`git clone https://github.com/Coffet/oiep-backendAI ../oiep-backendAI`.

---

## 1. Local shapes at a glance

| Shape | OIEP app | Companion | LLM | Section |
| --- | --- | --- | --- | --- |
| Site only | Docker | — | — | §2.1 |
| Site + helper, fixed wording | Docker | Docker (extractive) | off | §2.2 |
| Site + helper, conversational wording | Docker | **source** + host Ollama | on | §2.3 |
| Site only, no Docker | bare metal | — | — | §3.1 |
| Site + helper | bare metal | source | off / on | §3.2 / §3.3 |

Every mode serves the form at <http://127.0.0.1:8080/> (Docker and bare metal both default to 8080).

---

## 2. Docker

### 2.1 OIEP only

```bash
cp .env.example .env        # optional; set OIEP_ADMIN_PASSWORD to enable /admin
docker compose up -d --build
```

The database lives in the `oiep-data` volume; the app port is bound to `127.0.0.1` only. For a
plain-HTTP local admin walkthrough add `OIEP_SESSION_COOKIE_SECURE=false` to `.env`, otherwise the
browser refuses to send the `Secure` session cookie.

### 2.2 OIEP + companion, LLM off (containerised extractive backend)

Fill in `.env` from `.env.example` — the companion block needs, at minimum:

```dotenv
OIEP_COMPANION_ENABLED=true
OIEP_COMPANION_BASE_URL=http://ai:8090
OIEP_COMPANION_USER_HELPER_ENABLED=true
OIEP_COMPANION_WIDGET_URL=http://127.0.0.1:8090/ui/
OIEP_COMPANION_ADMIN_HELPER_ENABLED=true
OIEP_COMPANION_ADMIN_TOKEN=<32+ random characters>
OIEP_COMPANION_READ_TOKEN=<16+ random characters>
OIEP_AI_IP_HASH_SALT=<16+ random characters>
```

Then:

```bash
docker compose -f compose.yaml -f compose.companion.yaml up -d --build
docker compose -f compose.yaml -f compose.companion.yaml ps     # app + ai healthy
```

The companion runs the deterministic `structured-extractive` backend — no model, no download.
Details, tokens and the removal procedure are in [COMPANION.md](COMPANION.md).

### 2.3 OIEP in Docker + companion from source, LLM on (hybrid)

The app stays in Docker; the companion runs on the host so it can reach Ollama on loopback.

**One-time — Ollama and the model** (Windows: use the Ollama installer or the portable
`ollama-windows-amd64.zip` from the Ollama releases; macOS/Linux: `curl -fsSL https://ollama.com/install.sh | sh`):

```bash
ollama serve                      # 127.0.0.1:11434
ollama pull qwen2.5:1.5b-instruct-q4_K_M
```

**Point the app at the host companion** — in `.env`:

```dotenv
OIEP_COMPANION_BASE_URL=http://host.docker.internal:8090
```

(`host.docker.internal` is resolved automatically by Docker Desktop. On Linux Docker add
`extra_hosts: ["host.docker.internal:host-gateway"]` to the `app` service, bind the companion with
`OIEP_AI_HOST=0.0.0.0` behind the host firewall — or front it with a reverse proxy — and review
[SECURITY.md](https://github.com/Coffet/oiep-backendAI/blob/main/docs/SECURITY.md).)

**Start the companion from source** with the loopback LLM settings, reusing the same token
pair as the app's `.env`:

```powershell
cd ..\oiep-backendAI
$env:OIEP_AI_ENV = "prod"
$env:OIEP_AI_ADMIN_TOKEN = "<same value as OIEP_COMPANION_ADMIN_TOKEN>"
$env:OIEP_AI_IP_HASH_SALT = "<same value as OIEP_AI_IP_HASH_SALT>"
$env:OIEP_AI_WIDGET_PARENT_ORIGINS = "http://127.0.0.1:8080,http://localhost:8080"
$env:OIEP_AI_MODEL_BACKEND = "ollama"
$env:OIEP_AI_LLM_URL = "http://127.0.0.1:11434"
$env:OIEP_AI_LLM_MODEL = "qwen2.5:1.5b-instruct-q4_K_M"
$env:OIEP_AI_LLM_TIMEOUT_MS = "20000"
$env:OIEP_AI_LLM_NUM_CTX = "4096"
$env:OIEP_AI_LLM_NUM_PREDICT = "1024"
$env:OIEP_AI_LLM_PHRASE_QUESTIONS = "true"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090 --workers 1
```

On macOS/Linux use `export NAME=value` instead of `$env:NAME = "value"` and
`.venv/bin/python -m uvicorn ...`.

**Switch the stack to the host companion** — the containerised `ai` service must be stopped (it
would hold port 8090), then recreate the app so it picks up the new base URL:

```bash
docker compose -f compose.yaml -f compose.companion.yaml stop ai
docker compose -f compose.yaml -f compose.companion.yaml up -d --no-deps --force-recreate app
```

While this hybrid is active, do **not** start the `ai` service (`... up -d --no-deps app` for
app-only restarts). To go back to §2.2: stop the source companion and Ollama, restore
`OIEP_COMPANION_BASE_URL=http://ai:8090`, then
`docker compose -f compose.yaml -f compose.companion.yaml up -d --force-recreate ai app`.

---

## 3. Bare metal (no Docker for OIEP)

The app reads plain environment variables — PowerShell shown; on macOS/Linux use `export`.

### 3.1 OIEP only

```powershell
$env:OIEP_ADMIN_USERNAME = "admin"
$env:OIEP_ADMIN_PASSWORD = "<long random secret>"   # /admin stays disabled without it
.\mvnw.cmd spring-boot:run
# or: .\mvnw.cmd package ; java -jar target\oiep.war
```

Open <http://localhost:8080/>. The SQLite file is created at `data/oiep.db` (override with
`OIEP_DB_PATH`). Optional demo content: `java -jar target\oiep.war --oiep.demo-data=true`.

### 3.2 OIEP + companion, LLM off (extractive)

Terminal 1 — companion:

```powershell
cd C:\path\to\oiep-backendAI
$env:OIEP_AI_ENV = "dev"
$env:OIEP_AI_ADMIN_TOKEN = "<32+ random characters>"
$env:OIEP_AI_IP_HASH_SALT = "<16+ random characters>"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090 --workers 1
```

Run exactly **one** worker: sessions and rate limits are in-process in v1. The admin token above
must match `OIEP_COMPANION_ADMIN_TOKEN` below; `OIEP_AI_ENV=dev` also works for a purely local run,
while `prod` requires 32+ character tokens, an IP-hash salt and HTTPS-facing URLs (the companion's
README and [COMPANION.md](COMPANION.md#quick-start-local) list the full companion-side environment).

Terminal 2 — OIEP app:

```powershell
$env:OIEP_ADMIN_USERNAME = "admin"
$env:OIEP_ADMIN_PASSWORD = "<long random secret>"
$env:OIEP_COMPANION_ENABLED = "true"
$env:OIEP_COMPANION_BASE_URL = "http://127.0.0.1:8090"
$env:OIEP_COMPANION_USER_HELPER_ENABLED = "true"
$env:OIEP_COMPANION_WIDGET_URL = "http://127.0.0.1:8090/ui/"
$env:OIEP_COMPANION_ADMIN_HELPER_ENABLED = "true"
$env:OIEP_COMPANION_ADMIN_TOKEN = "<the companion's OIEP_AI_ADMIN_TOKEN>"
.\mvnw.cmd spring-boot:run
```

The floating helper appears on <http://localhost:8080/> and `/admin/assistant` works for the signed-in
admin. The widget's fixed wording comes from the reviewed templates.

### 3.3 Same, with the local LLM on

Start Ollama (`ollama serve`) and pull the model once, then add to the **companion** terminal before
`uvicorn`:

```powershell
$env:OIEP_AI_MODEL_BACKEND = "ollama"
$env:OIEP_AI_LLM_URL = "http://127.0.0.1:11434"
$env:OIEP_AI_LLM_MODEL = "qwen2.5:1.5b-instruct-q4_K_M"
$env:OIEP_AI_LLM_TIMEOUT_MS = "20000"
$env:OIEP_AI_LLM_NUM_CTX = "4096"
$env:OIEP_AI_LLM_NUM_PREDICT = "1024"
$env:OIEP_AI_LLM_PHRASE_QUESTIONS = "true"
```

The OIEP shell is unchanged from §3.2. The companion's `docs/MODEL.md` explains the backend,
validation and fallbacks; the model also assists slot extraction in this mode, bounded by the
timeout and the fallback/circuit breaker.

---

## 4. Toggle the LLM without touching OIEP

| Goal | Change (companion side) | Restart |
| --- | --- | --- |
| LLM on | `OIEP_AI_MODEL_BACKEND=ollama`, Ollama on `127.0.0.1:11434` | companion |
| LLM off | `OIEP_AI_MODEL_BACKEND=structured-extractive` (the default) | companion |
| Keep LLM extraction, fixed question wording | `OIEP_AI_LLM_PHRASE_QUESTIONS=false` | companion |
| Disable the helper entirely | OIEP: `OIEP_COMPANION_ENABLED=false` | OIEP app |

Nothing about the OIEP app's build or its other environment variables changes between these modes.

---

## 5. Verify

| Check | Command / page | Expected |
| --- | --- | --- |
| Companion backend | `curl -s http://127.0.0.1:8090/v1/meta` | `"model":{"backend":"structured-extractive"…}` (LLM off) or `"backend":"ollama"` (LLM on) |
| Companion health | `curl -s http://127.0.0.1:8090/healthz` (and `/readyz`) | `{"status":"ok",…}` |
| OIEP app | <http://127.0.0.1:8080/> | the form page loads; a round chat button appears bottom-right when the user helper is on |
| Widget | open the chat, answer one question | LLM on: varied, conversational wording (may reuse earlier answers); LLM off: fixed reviewed templates |
| Admin helper | sign in at `/admin` → `/admin/assistant` | status card loads (LLM on: backend `ollama`) |
| App wiring | OIEP startup log / `docker compose logs app` | `AI companion component enabled: … userHelper=true …` |

## 6. Notes

- The local LLM is **loopback only**: visitor text never leaves the machine, no cloud model. Keep it
  that way — the companion rejects non-loopback `OIEP_AI_LLM_URL` values.
- A cold Ollama model load can take tens of seconds; the companion falls back to the reviewed
  template wording until the model is warm (it keeps the model loaded for a few minutes).
- Question phrasing is validated (one short question, no links/markdown/interview-question content);
  invalid output is silently replaced by the template — occasional template wording is expected.
- The two services share two secrets only:
  `OIEP_COMPANION_ADMIN_TOKEN` = companion `OIEP_AI_ADMIN_TOKEN`, and
  `OIEP_COMPANION_READ_TOKEN` = companion `OIEP_AI_OIEP_SYNC_TOKEN`. Generate them with
  `python -c "import secrets; print(secrets.token_urlsafe(48))"`.
- Bare-metal OIEP does **not** read `.env`; set the variables in the shell that starts it.
- Docker and bare metal can both run at once only if you change one port (`OIEP_PORT`/`server.port`
  or the companion port); the defaults collide on 8080/8090.

See also: [README](../README.md) (bare-metal run), [COMPANION.md](COMPANION.md) (companion integration,
tokens, Docker overlay, removal), [DEPLOY.md](DEPLOY.md) (public deployment) and the companion's own
`README.md` / `docs/MODEL.md`.
