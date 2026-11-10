# Deploy OIEP on the internet

This guide is written for someone who has never deployed a server before. Follow it in order. You do not need to understand every command to get a working site.

> **The one confusing part first:** Cloudflare **Pages** cannot run OIEP. Pages only serves static files (HTML, CSS, JavaScript, images). OIEP is a running Java server with a live database. It needs a small computer (a VPS) with Docker.
>
> The good news: you can still put Cloudflare in front of it. You use **Cloudflare Tunnel**, not Pages. Your visitors see `https://oiep.yourdomain.com` on Cloudflare, with HTTPS and Cloudflare's network — and you never open a web port on your server. That is the setup this repo ships with.

---

## 1. The 20-second picture

```
Visitor's browser
       |
       |  https://oiep.yourdomain.com
       v
Cloudflare edge  (DNS, TLS, proxy, DDoS protection)
       |
       |  outbound-only encrypted tunnel
       v
cloudflared container  ──────────────┐
                                     │  private Docker network
                                     v
                              app container
                              Java + Spring Boot + JSP
                                     |
                                     v
                              /data/oiep.db
                              (Docker volume, survives restarts)
```

You need three things:

1. A **domain name** whose DNS is managed by Cloudflare (free account is fine).
2. A **small VPS** — a rented Linux computer. 1 vCPU and 2 GB RAM is comfortable; 1 GB works for running, but the first Docker build is happier with 2 GB. A Raspberry Pi at home also works, if it stays online.
3. **Docker** installed on that VPS. The guide installs it.

What you do **not** need:

- Cloudflare Pages (wrong product — explained above)
- A separate database server (SQLite is a file)
- An open port 80/443/8080 to the internet
- Kubernetes, a load balancer, or a paid platform

Rough running cost: a small VPS is usually US$5–10/month (a Raspberry Pi at home can be cheaper), Cloudflare's tunnel/free plan is normally US$0, and the domain is whatever you already pay per year. This setup does not need a managed database.

---

## 2. Words, in plain English

| Word | What it actually means here |
| --- | --- |
| **VPS** | A Linux computer you rent by the month. You get an IP address and SSH access. |
| **SSH** | A text terminal into that computer. On Windows, open PowerShell and type `ssh root@YOUR_SERVER_IP`. |
| **Docker image** | A sealed box containing the app, Java, and everything it needs to run. |
| **Docker container** | One running copy of that image. |
| **Docker volume** | A hard drive for a container. `oiep-data` stores the SQLite database. |
| **Docker Compose** | A text file (`compose.yaml`) that starts all the pieces together. |
| **Cloudflare Tunnel** | A private wire from a small `cloudflared` program on your server to Cloudflare. No inbound firewall holes. |
| **Cloudflare Pages** | A static-file host. It can serve a plain HTML site. It cannot run Java, JSP, or SQLite. |
| **`.env`** | A private settings file with your tunnel token. Never commit this file to git. |

---

## 3. What is in this repo for deployment

| File | What it does |
| --- | --- |
| `Dockerfile` | Builds the WAR inside Docker, then puts it in a small Java runtime image. Runs as a non-root user (UID 10001), keeps `/data` mode 0700, and includes a healthcheck. Base-image updates arrive as Dependabot pull requests. |
| `compose.yaml` | Runs the app with a persistent volume and hardened runtime (`cap_drop: ALL`, pids limits, `no-new-privileges`); optionally runs `cloudflared` with the `tunnel` profile. Binds the local app port to `127.0.0.1` only. |
| `compose.companion.yaml` | Optional overlay for the AI companion (oiep-backendAI). Adds the companion service and wires `OIEP_COMPANION_*` into the app; the base `compose.yaml` stays in charge of the OIEP container. Off unless you use the overlay explicitly. |
| `.env.example` | Copy to `.env` and paste the Cloudflare Tunnel token into it. The admin password is empty in the example, so the admin area starts disabled until you fill it in. The optional AI companion block is commented out and off by default. |
| `scripts/backup.sh` | Makes a consistent `sqlite3 .backup` while the app is running; removes its temporary in-volume copy on any failure and writes the backup with mode 0600. |
| `scripts/restore.sh` | Replaces the live database with a backup after asking you to type `yes`; validates the file name, checks the SQLite header and swaps the file into place only after a successful copy. |
| `scripts/companion-uninstall.sql` | Optional AI companion removal step: drops the read-API's SQLite triggers/table. Only needed if you enabled the companion and are removing it again. |
| `docs/DEPLOY.md` | This file. |
| `docs/COMPANION.md` | Optional AI companion reference: what it adds, configuration, tokens, read API, privacy and the add/remove procedure. |

### Optional AI companion (oiep-backendAI)

OIEP can optionally be paired with [oiep-backendAI](https://github.com/Coffet/oiep-backendAI), a separate local-first AI helper service. This is **off by default** and the base `compose.yaml` never starts it.

If you want it, read **[docs/COMPANION.md](COMPANION.md)** first: it covers the trust model, the two token pairs, the privacy obligations and the exact add/remove procedure. Then use the optional overlay alongside the base file:

```bash
docker compose -f compose.yaml -f compose.companion.yaml up -d --build
```

The overlay builds the companion from a sibling `../oiep-backendAI` checkout (or an `OIEP_AI_IMAGE`), publishes it on `127.0.0.1` only, and wires the `OIEP_COMPANION_*` variables into the existing app container. It does **not** expose the companion publicly: if you enable the user-helper widget, route a public hostname to `ai:8090` through the tunnel and block the companion's `/v1/admin/` path there, as described in `docs/COMPANION.md` and the companion's own `docs/SECURITY.md`. The admin helper page (`/admin/assistant`) works entirely over the private Docker network and needs no public companion hostname.

To remove it again, follow the full-removal procedure in [docs/COMPANION.md](COMPANION.md) — run `scripts/companion-uninstall.sql` **before** deleting the component code, because the read API's database triggers otherwise make deletes on `reports` fail.

---

## 4. Try it locally first (optional, 5 minutes)

This step proves Docker works before you rent anything.

**On Windows**, install **Docker Desktop**, open it, and wait until it says the engine is running.

In the project folder (the folder containing `compose.yaml`):

```powershell
docker compose up -d --build
```

Wait a bit, then open:

```
http://127.0.0.1:8080
```

You should see the OIEP index page.

The moderation page is at **<http://127.0.0.1:8080/admin>**. It is disabled until you set an admin password. For a local test, create `.env` from the example and edit it:

```powershell
copy .env.example .env
notepad .env
```

Set `OIEP_ADMIN_PASSWORD` to a long random value, save, and run `docker compose up -d --build` again. The default username is `admin`; change `OIEP_ADMIN_USERNAME` too if you prefer.

> The Docker Compose deployment marks the session cookie `Secure` (`OIEP_SESSION_COOKIE_SECURE=true`) because production is HTTPS. Browsers refuse to send a `Secure` cookie over plain `http://`, so for local admin testing at `http://127.0.0.1:8080` add `OIEP_SESSION_COOKIE_SECURE=false` to `.env`. That is for local Docker only — never on the public site.

Useful local commands:

```powershell
docker compose ps                  # is the app healthy?
docker compose logs -f --tail=80 app
docker compose down                # stop, keep the database volume
docker compose down -v             # stop and DELETE ALL DATA — do not do this on a real site
```

If port 8080 is already used by another program, create a `.env` file in the project folder containing:

```dotenv
OIEP_PORT=18080
```

Then run `docker compose up -d --build` again and open `http://127.0.0.1:18080`.

The local database lives in a Docker volume named `oiep-data`, not in the project folder.

---

## 5. Set up the server (the real deployment)

These commands assume you are logged in as `root` on a fresh Ubuntu 22.04/24.04 VPS. If your provider gives you a non-root user, put `sudo` in front of the system commands.

### Step 5.1 — Connect

```bash
ssh root@YOUR_SERVER_IP
```

### Step 5.2 — Update the machine and install Docker

```bash
apt update && apt upgrade -y
apt install -y git curl ca-certificates
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker --version
docker compose version
```

You want to see a Docker version and a Compose version. If `docker compose` is unknown, run the install command again and check the [official Docker install guide](https://docs.docker.com/engine/install/ubuntu/).

Optional firewall (recommended): allow SSH only, because Cloudflare Tunnel needs **no** inbound web ports.

```bash
apt install -y ufw
ufw allow OpenSSH
ufw enable
```

### Step 5.3 — Put the project on the server

If the project is in a git repository you can access:

```bash
mkdir -p /opt/oiep
git clone YOUR_REPOSITORY_URL /opt/oiep
cd /opt/oiep
```

Or copy it from your Windows computer. Run this in the folder that contains your local `oiep` folder:

```powershell
scp -r .\oiep root@YOUR_SERVER_IP:/opt/
ssh root@YOUR_SERVER_IP
cd /opt/oiep
```

Do **not** copy a local `.env` to production. Create a fresh one on the server.

### Step 5.4 — Create the Cloudflare Tunnel

1. Go to **<https://dash.cloudflare.com>** and click your domain.
2. In the left sidebar, open **Zero Trust**. (This is a separate Cloudflare dashboard; it is free to start.)
3. Go to **Networks → Tunnels**.
4. Click **Create a tunnel**.
5. Choose **Cloudflared**, give it a name like `oiep`, and click **Save tunnel**.
6. On the "Install connector" screen, choose **Docker**. You will see a long command. Do **not** run that whole command. Find the long token after `--token` and copy just the token.
7. Go to the **Public Hostname** tab and click **Add a public hostname**:
   - **Subdomain:** `oiep`
   - **Domain:** pick your domain
   - **Path:** leave blank
   - **Service Type:** `HTTP`
   - **URL:** `http://app:8080`
8. Save. Cloudflare should create the DNS record automatically. Do not add a second DNS record by hand.

> `app:8080` is not a typo. Inside Docker Compose, `app` is the service name in `compose.yaml`; Docker resolves that name only inside the private network. The outside world never sees port 8080.

If you are asked to install a certificate or "route traffic", skip the manual route — the Public Hostname above does it.

### Step 5.5 — Put the token in `.env`

Back in the server terminal:

```bash
cd /opt/oiep
cp .env.example .env
chmod 600 .env
nano .env
```

Set the line:

```dotenv
CLOUDFLARE_TUNNEL_TOKEN=PASTE_THE_LONG_TOKEN_HERE
```

Save and exit `nano`: `Ctrl+O`, `Enter`, `Ctrl+X`.

Set the admin credentials too:

```dotenv
OIEP_ADMIN_USERNAME=admin
OIEP_ADMIN_PASSWORD=<paste-your-own-long-random-secret>
```

Replace the password value with a real secret from your password manager (the placeholders used in this repository are rejected at startup, fail-closed).

Use a password manager to generate at least 16 random characters. The admin area stays disabled if this value is empty. Do not reuse your tunnel token or SSH password.

A password copied verbatim from this repository (the placeholder examples in the README, this guide or `.env.example`, including `<paste-your-own-long-random-secret>`) is detected at startup and the admin area stays **disabled** — fail-closed — so a forgotten placeholder can never become a working password. If the app log says the admin area is disabled after you set a password, that password was empty or one of the documented placeholders.

Leave the other security defaults alone: `OIEP_TRUST_CLOUDFLARE_CONNECTING_IP=true` makes the app read the real visitor IP from Cloudflare's `CF-Connecting-IP` header (client-supplied `X-Forwarded-For` is never trusted), `OIEP_SESSION_COOKIE_SECURE=true` marks the session cookie Secure over HTTPS, and `OIEP_SESSIONS_MAX_ACTIVE` caps concurrent server-side sessions. `compose.yaml` sets them; change them only if you are not using Cloudflare Tunnel.

If port 8080 on the server is already used by something else, add:

```dotenv
OIEP_PORT=18080
```

That only changes the local loopback port. Cloudflare still talks to `app:8080` inside Docker.

### Step 5.6 — Start it

```bash
docker compose --profile tunnel up -d --build
```

The first build takes a few minutes (it downloads Maven dependencies once). Then check:

```bash
docker compose ps
```

You want `oiep-app-1` to say `healthy`, and `cloudflared` to be `running`. Watch the startup logs:

```bash
docker compose logs --tail=100 app
docker compose logs --tail=100 cloudflared
```

Good signs:

- app log ends with `Started OiepApplication ...`
- app log says `Tomcat started on port 8080`
- cloudflared log says `Registered tunnel connection`
- `docker compose ps` shows the app as `healthy`

Then open:

```
https://oiep.YOURDOMAIN.com
```

The moderation page is:

```
https://oiep.YOURDOMAIN.com/admin
```

Sign in with the `OIEP_ADMIN_USERNAME` and `OIEP_ADMIN_PASSWORD` from `.env`. Go directly to `/admin`; it is intentionally not linked from the public pages. If the page says admin access is disabled, the password variable was empty when the container started; fix `.env` and run `docker compose --profile tunnel up -d --force-recreate`.

### Step 5.7 — Reboot test

Reboot the VPS once to make sure it comes back by itself:

```bash
reboot
```

Wait 1–2 minutes, then open the site again. Docker's `restart: unless-stopped` and the Cloudflare tunnel should both return automatically.

---

## 6. If you do not have a domain on Cloudflare yet

1. Create a free Cloudflare account at <https://dash.cloudflare.com/sign-up>.
2. Click **Add a site**, enter your domain.
3. Cloudflare gives you two nameserver addresses. Go to the company where you bought the domain and replace its nameservers with Cloudflare's.
4. Wait until Cloudflare says the site is **Active**. This can take minutes to a few hours.
5. Continue with the tunnel steps above.

You do not need to buy a new domain. You only need the domain's nameservers to point at Cloudflare.

---

## 7. "But I wanted Cloudflare Pages"

This is the part that trips people up, so here it is in very plain language.

- **Cloudflare Pages** is a web host for files. You upload an `index.html`, some CSS, some images, and Pages serves those files very fast. There is no Java process, no Spring Boot, no JSP compilation, no writable disk for SQLite.
- **OIEP** is a server: it compiles JSP pages per request, validates form posts, writes to SQLite, tracks sessions, and returns dynamic HTML.
- If you connect this repository to Cloudflare Pages, Pages will not start the app. There is nothing for it to serve correctly. It is the wrong machine.

The correct Cloudflare product for a running server is **Cloudflare Tunnel**, which is what `compose.yaml` uses. You still get:

- your domain on Cloudflare
- Cloudflare TLS/HTTPS
- Cloudflare DNS and proxy protection
- no open inbound ports on your server

**Pages is not needed at all for this app.** If you want a separate static "coming soon" or "about the project" page on Pages later, that is fine, but it is a separate website.

---

## 8. Everyday operations

Run these from `/opt/oiep`.

### Check status

```bash
docker compose ps
```

### Watch logs

```bash
docker compose logs -f --tail=100 app
# Ctrl+C to stop watching (it does not stop the site)

docker compose logs -f --tail=100 cloudflared
```

### Restart

```bash
docker compose restart app
```

### Security events in the log

Failed and throttled admin sign-ins, sign-outs and every moderation action (hide/restore/delete) are written to the app log with the resolved client address. Look for them with:

```bash
docker compose logs app | grep -Ei 'sign-in|sign-out|Moderation|throttled|Refusing to allocate'
```

Logs use Docker's local `json-file` driver (rotated at 10 MB x 3 in `compose.yaml`) and are not shipped anywhere by default. If you need retention or alerting, forward the app log to an external system (for example a small vector/syslog container pointed at your logging service).

### Stop the site without deleting data

```bash
docker compose --profile tunnel down
```

Start it again with:

```bash
docker compose --profile tunnel up -d
```

### Update to newer code

```bash
cd /opt/oiep
sh scripts/backup.sh
git pull
docker compose --profile tunnel up -d --build
```

If you copied files instead of using git, copy the new files over, then run the same `docker compose ... up -d --build` command.

### See how big the data is

```bash
docker compose exec -T app du -h /data/oiep.db
```

### Go inside the container (for debugging)

```bash
docker compose exec -T app sh
```

Inside, you can run `ls -la /data` or `sqlite3 /data/oiep.db ".tables"`. Type `exit` to leave.

---

## 9. Backups (do not skip this)

The database is a single file inside the `oiep-data` Docker volume. A backup on the same server is not a real backup; copy backups somewhere else (your laptop, another disk, a backup service).

### Make a backup

While the site is running:

```bash
cd /opt/oiep
sh scripts/backup.sh
```

It writes something like:

```
backups/oiep-20261109T155636Z.db
```

The script uses SQLite's `.backup` command, so it is safe while people are using the site. Backups contain every submitted note (including moderation-hidden ones), so the script writes them with mode `0600` and always removes its temporary copy inside the volume — on success and on failure.

### Download the backup to your computer

From your Windows computer:

```powershell
scp root@YOUR_SERVER_IP:/opt/oiep/backups/oiep-*.db .
```

The `*` may not expand in PowerShell; use the exact filename shown by the backup script.

### Automatic daily backup

Edit root's crontab (`crontab -e`) and add one line:

```cron
0 3 * * * cd /opt/oiep && sh scripts/backup.sh >> backups/backup.log 2>&1
```

That means: every day at 03:00, make a backup. Copy those files off the server periodically.

### Restore a backup

**This replaces the live database. Make sure you really want to do it.**

```bash
cd /opt/oiep
sh scripts/restore.sh backups/oiep-20261109T155636Z.db
```

It asks you to type `yes`. After that it stops the app, copies the backup into the volume under a temporary name, sets ownership to UID/GID 10001 and mode `0600`, and only then swaps it in and restarts the app. The script also rejects any file name that is not made of letters, digits, `.`, `_` and `-`, and refuses a file that does not start with the SQLite header, so a mistyped or malicious path cannot become a shell command or replace the database with the wrong file. If a step fails, the previous database is left untouched and the app stays stopped (the error message tells you how to start it). Check the site afterwards.

### Windows local Docker

The backup/restore scripts are written for a Linux server. On Windows, use **Git Bash** or **WSL** to run them, or use Docker Desktop and the manual commands in the script. On a Windows filesystem (`/mnt/c` in WSL or Git Bash) the `chmod 600` in the script may be emulated by the filesystem and the backup can inherit Windows ACLs instead — check the file's permissions or copy it to an encrypted location; on the Linux VPS the mode is enforced. For production, use the Linux VPS — that is what the guide is for.

---

## 10. Troubleshooting

### `docker compose ps` is empty or the app keeps restarting

```bash
docker compose ps
docker compose logs --tail=200 app
```

Look for the first real error in the log. Common causes: a bad `.env`, no disk space, or a database file permission problem.

### `Port 8080 is already allocated`

Another program on the server (or your Windows machine) is using port 8080. Add this to `.env`:

```dotenv
OIEP_PORT=18080
```

Then:

```bash
docker compose --profile tunnel up -d
```

Open the local check at `http://127.0.0.1:18080`. Cloudflare's `app:8080` setting does **not** change.

### Cloudflare shows "Error 1033" or "tunnel not found"

The `cloudflared` container is not connected. Check:

```bash
docker compose ps
docker compose logs --tail=100 cloudflared
```

Usually the token in `.env` is missing, has a typo, or has a line break. Copy it again from **Zero Trust → Networks → Tunnels → your tunnel → Docker**. The token is one long line.

### Cloudflare shows "502 Bad Gateway"

Cloudflare reached the tunnel, but the tunnel could not reach the app. Check that:

- `docker compose ps` says the app is `healthy`
- the Public Hostname service URL is `http://app:8080` (not `localhost`, not `127.0.0.1`)
- the app is running: `curl -I http://127.0.0.1:8080`

Then check `docker compose logs --tail=100 app`.

### The site loads but has no styling

Hard-refresh the page (`Ctrl+F5` / `Cmd+Shift+R`). Then check:

```bash
curl -I https://oiep.YOURDOMAIN.com/css/site.css
```

If that is a 404, the WAR/build did not include the static files; rebuild with `docker compose --profile tunnel up -d --build`.

### "I changed `.env` but nothing happened"

Compose reads `.env` when it creates/recreates containers. Re-run:

```bash
docker compose --profile tunnel up -d --force-recreate
```

### Rate limits, visitor IPs and forwarded headers

The compose deployment binds the app port to `127.0.0.1`, and only `cloudflared` can reach it. The app uses `server.forward-headers-strategy=native`: Tomcat's `RemoteIpValve` accepts the real visitor address from Cloudflare's `CF-Connecting-IP` header only when the immediate peer is the trusted tunnel hop (`OIEP_TRUST_CLOUDFLARE_CONNECTING_IP=true`). Client-supplied `X-Forwarded-For` / `Forwarded` headers are never used for rate limits or logs, so rotating them cannot bypass a throttle.

Do **not** replace this with `SERVER_FORWARD_HEADERS_STRATEGY=framework`: that Spring strategy trusts the client-controllable `X-Forwarded-For` header, which would let anyone bypass the per-client limits and the admin login throttle. If you front the site with a different proxy (Nginx, Caddy, another CDN), configure that proxy's trusted hop for Tomcat (or keep the app reachable only through Cloudflare) — never trust client-supplied forwarding headers on an internet-facing instance.

### "Admin sign-in is throttled" or the admin page is locked out

The admin login throttle is per resolved client address: 6 sign-in attempts per 15 minutes (successful sign-ins count toward the budget too), then a fail-closed lockout for the rest of the window (HTTP 429). The counter lives in memory, so restarting the app container clears it (`docker compose restart app`). A shared egress IP — an office NAT or the admin's own VPN — can hit the limit for everyone behind it; that is deliberate (fail closed) and is the cost of online guessing protection. Double-check you are using the real password and not a documented placeholder (the admin area stays disabled for those).

### Admin login fails over plain HTTP in local Docker

The Compose deployment sets `OIEP_SESSION_COOKIE_SECURE=true` for the production HTTPS setup. A browser will not send a `Secure` cookie over `http://`, so the admin login page cannot keep its session locally. For local testing only, add `OIEP_SESSION_COOKIE_SECURE=false` to `.env` and run `docker compose up -d --force-recreate`. Never set it to `false` on the public HTTPS site.

### Data disappeared

Most likely someone ran `docker compose down -v`. The `-v` deletes the named volume. Never use `-v` on the production stack. Restore the latest backup.

### `permission denied` for `/data/oiep.db`

The normal Compose setup uses the `oiep-data` named volume and the app runs as UID 10001, which works. If you changed `compose.yaml` to use a bind mount, make sure the host folder is writable by UID 10001. The simplest fix is to switch back to the named volume.

### The first build runs out of memory on a 1 GB VPS

The build stage uses Maven and needs more memory than the running app. Use a 2 GB VPS, or add 2 GB of swap first:

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h
```

Then run the `docker compose ... up -d --build` command again. The running app itself is small; this is only the one-time build.

### It says "Cloudflare Pages build failed"

You connected the repo to Pages. Delete that Pages project. Use the Docker + Tunnel steps above. Pages cannot run this app.

---

## 11. Security and upkeep checklist

- [ ] Never commit `.env`. It contains your tunnel token and admin password. Keep it `chmod 600`.
- [ ] Use a long, unique `OIEP_ADMIN_PASSWORD` (16+ random characters from a password manager). The admin area is disabled while it is empty, and repository-documented placeholders are rejected fail-closed. The single admin account has no MFA.
- [ ] Keep the app port bound to `127.0.0.1`; do not change it to `0.0.0.0`.
- [ ] Keep the shipped security defaults: `OIEP_TRUST_CLOUDFLARE_CONNECTING_IP=true` reads the real visitor IP from `CF-Connecting-IP` (never trust client `X-Forwarded-For`), `OIEP_SESSION_COOKIE_SECURE=true`, and the session cap. Keep `OIEP_TRUST_CLOUDFLARE_CONNECTING_IP=true` **only while the app port stays loopback-bound behind Cloudflare Tunnel**: the app honours `CF-Connecting-IP` only because Tomcat's trusted-proxy handling has already rewritten the peer address to that value; on a directly exposed instance, leave it `false`. Do not switch to `server.forward-headers-strategy=framework`.
- [ ] Turn on **SSL/TLS → Edge Certificates → Always Use HTTPS** in Cloudflare for the public hostname, so visitors cannot start over plain HTTP. The app sends HSTS only when a request reached it over HTTPS.
- [ ] Keep demo data off on a real site: `OIEP_DEMO_DATA=false` (the default).
- [ ] Ship the app log off the host, or at least alert on the `sign-in`/`Moderation` lines; structured security events exist but are local stdout only by default.
- [ ] Run `apt upgrade` and apply the Dependabot update PRs (Maven, GitHub Actions, Docker base images). CI's Docker smoke test re-checks the security headers, relative admin redirect and session-cookie flags on every pull request.
- [ ] Keep backups off the server. They contain all notes (including hidden ones): the script writes them `0600`; keep it that way and/or encrypt them.
- [ ] Test a restore before you need it (the script validates the file and swaps it in safely).
- [ ] Do not run more than one app container against the same SQLite file. SQLite + this app is designed for one small instance; the rate limiters, login throttle and session cap are per-instance and reset on restart.
- [ ] If you later put the site behind a different proxy (Nginx, Caddy), configure that proxy's trusted hop explicitly (see "Rate limits, visitor IPs and forwarded headers").
- [ ] Accept and understand the residual risks: helpful votes are per browser session (throttled per client), the fail-closed login throttle can lock out a shared egress IP, there is no MFA on the single admin account, and the app-level limits are abuse friction rather than a WAF.

---

## 12. Command cheat sheet

```bash
# Start / update everything, including the tunnel
cd /opt/oiep
docker compose --profile tunnel up -d --build

# Status
docker compose ps

# Logs
docker compose logs -f --tail=100 app
docker compose logs -f --tail=100 cloudflared

# Security events (failed/throttled sign-ins, moderation actions)
docker compose logs app | grep -Ei 'sign-in|sign-out|Moderation|throttled'

# Restart the app only
docker compose restart app

# Stop, keeping all data
docker compose --profile tunnel down

# Backup (while running)
sh scripts/backup.sh

# Restore (asks for confirmation, destructive)
sh scripts/restore.sh backups/oiep-YYYYMMDDTHHMMSSZ.db

# The public URL
https://oiep.YOURDOMAIN.com
```

---

## 13. What was verified for this repository

- `docker compose build app` succeeds.
- `docker compose up -d app` starts the app as UID 10001, the healthcheck reports `healthy`, and the site returns HTTP 200.
- `docker compose config` validates the hardened compose file (capabilities dropped, pids limits, the security environment defaults).
- The `oiep-data` volume persists reports across `docker compose restart`.
- `scripts/backup.sh` produces a valid SQLite backup (mode `0600`, temporary in-volume copy always removed); `scripts/restore.sh` validates and restores it and the app comes back healthy. The guard rails (file-name validation, SQLite header check, copy-then-swap into a scratch volume) were tested against a scratch volume, not the live production one.
- CI (`.github/workflows/ci.yml`) builds the image and smoke-tests the patched build: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy: same-origin`, CSP SHA-256 hashes with **no** `'unsafe-inline'`, a relative `Location: /admin/login` redirect, and `Secure`/`HttpOnly`/`SameSite=Lax` on the session cookie.
- The same app container was reached through a temporary Cloudflare quick tunnel and returned the OIEP page with `Server: cloudflare` and a real `CF-RAY` header. The permanent named-tunnel setup in this guide uses the same `cloudflared` image and `TUNNEL_TOKEN` mechanism; you complete the named tunnel in your own Cloudflare account.

The permanent public hostname cannot be tested without your Cloudflare account and domain. Everything on the server and tunnel container side is included and tested locally; the app-level controls are abuse friction, not a substitute for Cloudflare's edge protection.
