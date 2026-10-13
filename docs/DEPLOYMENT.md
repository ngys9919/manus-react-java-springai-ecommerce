# Deployment

Shopwise ships as three containers: PostgreSQL, the Spring Boot API, and an nginx image that serves
the built storefront and reverse-proxies `/api` to the backend. The browser therefore talks to one
origin, which means no CORS configuration, no exposed backend port, and no chance of the API being
reached directly by accident.

## Quick start

```bash
git clone https://github.com/ngys9919/manus-react-java-springai-ecommerce.git
cd manus-react-java-springai-ecommerce

cp .env.example .env
# Required: the stack refuses to start without a real signing key.
openssl rand -base64 48        # paste the output into SHOPWISE_JWT_SECRET

docker compose up -d --build
docker compose ps              # wait for all three to report healthy
```

Then open **http://localhost:8080**.

The first build downloads Maven and npm dependencies, so allow a few minutes. Later builds reuse
cached layers and take seconds when only application code changed.

To stop, run `docker compose down`. Add `-v` to also delete the database volume.

## What runs

| Service | Image | Published | Purpose |
| --- | --- | --- | --- |
| `frontend` | `shopwise/frontend` (nginx 1.29-alpine) | `${SHOPWISE_HTTP_PORT:-8080}:80` | Static bundle, SPA fallback, API reverse proxy |
| `backend` | `shopwise/backend` (JRE 21 alpine) | internal `8080` only | REST API and the Spring AI assistant |
| `postgres` | `postgres:16-alpine` | internal `5432` only | Persistence, migrated by Flyway on first start |

Flyway applies `V1__schema.sql` and `V2__seed.sql` on the first boot, so a fresh volume comes up with
the catalogue, the demo accounts and the assistant configuration already in place. No manual
migration step is needed.

## Configuration

Every value is read from `.env` (git-ignored) or from the environment. The important ones:

| Variable | Default | Notes |
| --- | --- | --- |
| `SHOPWISE_JWT_SECRET` | **none — required** | Session signing key. The stack will not start without it. |
| `SHOPWISE_HTTP_PORT` | `8080` | Host port for the storefront. |
| `SHOPWISE_DB_PASSWORD` | `shopwise` | Change this for anything reachable by others. |
| `OPENAI_API_KEY` | `not-configured` | Omit and the assistant degrades to deterministic catalogue answers. |
| `OPENAI_API_BASE` | `https://api.openai.com/v1` | Any OpenAI-compatible endpoint. |
| `SHOPWISE_AI_MODEL` | `gpt-5-mini` | Model identifier for the assistant. |
| `SHOPWISE_ASSISTANT_ENABLED` | `true` | Set `false` to ship the storefront without the assistant. |
| `SPRING_PROFILES_ACTIVE` | `prod` | `prod` is what activates the signing-key guard. |
| `SHOPWISE_JAVA_OPTS` | `-XX:MaxRAMPercentage=75 …` | Tune the JVM heap for the host. |

### The signing key is not optional

`SecurityStartupCheck` refuses to boot when `SHOPWISE_JWT_SECRET` is still the development
placeholder and a non-local profile is active. Because the Dockerfile sets
`SPRING_PROFILES_ACTIVE=prod`, a container started without a real key exits immediately with:

```
Refusing to start: shopwise.security.jwt-secret is still the development placeholder,
and the active profile is not a local one.
```

That is deliberate. The placeholder is committed to a public repository, so a deployment that kept
it would accept sessions forged by anyone who has read the source. Failing at startup is far better
than failing silently in production.

Compose enforces the same thing one step earlier, so the failure arrives before any image is built:

```
required variable SHOPWISE_JWT_SECRET is missing a value
```

### Running without a model key

The stack starts fine with no `OPENAI_API_KEY`. The assistant detects the missing key, switches to
its deterministic responder, and the UI labels affected replies as degraded. Catalogue search,
comparisons, cart actions, escalation and analytics all keep working — only free-form phrasing is
lost. This is the right behaviour for a demo or an air-gapped install, and it means a missing key
degrades one feature rather than taking down the deployment.

## Verifying a deployment

The end-to-end suite runs against the published port, because nginx proxies the API:

```bash
python3 scripts/smoke-test.py http://localhost:8080
```

Expect `67 passed, 0 failed`. Useful one-off checks:

```bash
curl -fsS http://localhost:8080/actuator/health | jq          # {"status":"UP",...}
curl -fsS http://localhost:8080/api/v1/catalog/categories     # seeded catalogue
docker compose logs -f backend                                 # follow application logs
docker compose exec postgres psql -U shopwise -d shopwise -c '\dt'
```

## A permanent deployment

Everything above runs on any machine, but "permanent" is a property of the host, not of the
containers. Two things have to be true.

1. **The host stays up.** A Manus Sandbox hibernates when idle and its HTTP URL is temporary, so it
   is the wrong home for a site people rely on. An always-on Linux host is the right one. Check
   [Resource sizing](#resource-sizing) first — a JVM plus PostgreSQL wants more memory than a static
   site, and the first image build wants more again.
2. **The stack comes back by itself after a reboot.** `restart: unless-stopped` in
   `docker-compose.yml` only restarts *containers*, and only while the Docker daemon is running.
   Nothing brings the daemon or the stack back after the machine restarts — and the machine restarts
   without being asked when a tier is upgraded, a backup is restored, or it is restarted from the
   console. A site that silently stays down after a reboot looks broken. `deploy/shopwise.service`
   closes that gap.

### One command

Run this from a checkout on the host you intend to keep:

```bash
git clone https://github.com/ngys9919/manus-react-java-springai-ecommerce.git
cd manus-react-java-springai-ecommerce
./scripts/deploy-persistent.sh --dry-run                      # show what it would do
./scripts/deploy-persistent.sh --origin shop.example.com
```

It installs Docker if it is missing, generates `.env` with a fresh signing key and database password,
builds and starts the stack, installs and enables the systemd unit, opens the port if `ufw` is
active, and waits for the containers to report healthy. On a host without systemd, add
`--no-systemd`.

It is idempotent, and it never rewrites an existing `.env`: re-running it rebuilds and restarts the
stack while keeping the signing key and the database password, so sessions and the database
connection survive a redeploy. Deleting `.env` to "start clean" is a trap — it regenerates the
password the API presents, but PostgreSQL applies `POSTGRES_PASSWORD` only when it initialises an
*empty* data directory, so the API is then locked out of a database that already exists.

### Proving that it survives a reboot

Do not take the unit's existence as proof. Reboot and look:

```bash
systemctl is-enabled shopwise     # enabled
systemctl status shopwise         # active (exited) — a oneshot unit stays "exited" while up
sudo reboot
```

After the machine returns, all three containers should be healthy again and the site should load
without anyone logging in.

### Opening the port

A Cloud Computer allows only SSH and ICMP through `ufw`, so a service that is running perfectly still
looks unreachable until the port is opened:

```bash
sudo ufw allow 80/tcp     # and 443/tcp once TLS is in front
```

`deploy-persistent.sh` does this for the HTTP port for you. Never expose the other two containers:
only nginx publishes a port, deliberately — the API and the database are meant to be unreachable
from outside the Compose network.

### A domain and HTTPS

Plain HTTP is fine for a first look and wrong for anything people sign in to. Point an A record at
the host's public IP, then terminate TLS in front of the stack. Caddy is the least work:

```bash
sudo apt-get install -y caddy
```

```caddyfile
shop.example.com {
    reverse_proxy localhost:8080
}
```

Caddy obtains and renews the certificate itself. With certbot instead:

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo certbot --nginx -d shop.example.com --redirect -m you@example.com --agree-tos
```

Either way the stack keeps serving plain HTTP on `localhost:8080` and nothing inside the containers
changes. nginx already sets `X-Forwarded-Proto`, which the backend reads through the standard
forward-headers support, so generated links come out as `https`.

### Backups, and the one irreversible command

The database is the only stateful component. Automate the dump described under
[Backups](#backups) instead of relying on remembering it:

```bash
# /etc/cron.daily/shopwise-backup   (chmod +x)
#!/bin/sh
cd /opt/shopwise && docker compose exec -T postgres pg_dump -U shopwise shopwise \
  | gzip > /var/backups/shopwise-$(date +%F).sql.gz
find /var/backups -name 'shopwise-*.sql.gz' -mtime +14 -delete
```

`docker compose down -v` deletes the `shopwise-postgres-data` volume. Treat it as irreversible.

### Traffic is metered on some hosts

Where the host bills for outbound traffic, exhaustion shuts the machine down rather than throttling
it. On a Cloud Computer, enable auto top-up so a busy day cannot take the site offline.

## Operating the stack

```bash
docker compose ps                      # health and uptime per service
docker compose logs -f                 # all logs
docker compose restart backend         # restart one service
docker compose up -d --build backend   # rebuild one image and roll it
docker compose pull                    # refresh base images
```

### Backups

The database is the only stateful component.

```bash
docker compose exec -T postgres pg_dump -U shopwise shopwise | gzip > shopwise-$(date +%F).sql.gz
```

Restore with `gunzip -c backup.sql.gz | docker compose exec -T postgres psql -U shopwise -d shopwise`.

The `shopwise-postgres-data` named volume holds the data directory. `docker compose down -v`
destroys it, so treat that command as irreversible.

### Upgrading

```bash
git pull
docker compose up -d --build
```

New Flyway migrations apply automatically at startup. Never edit an applied migration file — add a
new one, or checksum validation will fail on the next boot.

## Exposing the API directly

By default only nginx is published. To reach the API or Swagger UI from the host, add a port mapping
to the `backend` service:

```yaml
    ports:
      - "8081:8080"
```

Swagger UI is then at `http://localhost:8081/swagger-ui.html`. Do this only when you need it, and
never on a host reachable from an untrusted network without also restricting `/actuator`.

## TLS

The stack terminates plain HTTP, so put a TLS terminator in front. With Caddy on the same host:

```caddyfile
shop.example.com {
    reverse_proxy localhost:8080
}
```

Set `SHOPWISE_ALLOWED_HOSTS` for the Vite server only if you also run the dev server in front of a
proxy; the production nginx image already accepts any Host header.

If you terminate TLS elsewhere, forward `X-Forwarded-Proto`. nginx already sets it, and the backend
reads it through the standard forward-headers support.

## Resource sizing

Measured on this project's own images:

| Service | Idle memory | Notes |
| --- | --- | --- |
| `postgres` | ~35 MB | Grows with data; the seed is under 2 MB. |
| `backend` | ~380 MB | JVM plus Spring context. `MaxRAMPercentage=75` scales the heap to the container limit, so set a memory limit and let it adapt. |
| `frontend` | ~8 MB | nginx serving static files. |

A 2 GB host is comfortable. Give the backend at least 512 MB; below that the JVM will spend its time
in garbage collection.

## Building without Compose

```bash
docker build -t shopwise/backend:latest  ./backend
docker build -t shopwise/frontend:latest ./frontend
```

Both images are multi-stage and produce runtime-only layers: the backend image contains a JRE and the
application jar with no Maven or source, and the frontend image contains nginx and the built assets
with no Node.

Both run as a non-privileged user, and both declare a `HEALTHCHECK`, so an orchestrator other than
Compose can also tell when they are ready.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `required variable SHOPWISE_JWT_SECRET is missing a value` | No `.env`, or the key is empty | `openssl rand -base64 48`, paste into `.env` |
| Backend container exits immediately, log says `Refusing to start` | Real signing key absent while `prod` is active | Set `SHOPWISE_JWT_SECRET`; the message states exactly what to do |
| `frontend` restarts in a loop, log says `host not found in upstream "backend"` | nginx started before the backend resolved | Compose waits on the backend healthcheck, so this means the backend never became healthy — check `docker compose logs backend` |
| `port is already allocated` | Something already listens on the published port | Change `SHOPWISE_HTTP_PORT`, or stop the other process |
| Assistant replies say they are degraded | `OPENAI_API_KEY` missing or rejected | Check `docker compose logs backend` for the upstream error |
| `relation "product" does not exist` | Flyway did not run | Confirm the volume is not stale from a different schema, then `docker compose down -v` and rebuild |
| Slow first build | Maven and npm downloads | Expected; subsequent builds use cached layers |

## Environments without bridge networking

Some nested or restricted environments cannot create Docker bridge networks. The symptom is a build
or `up` failing with:

```
failed to create endpoint on network bridge: Unable to enable DIRECT ACCESS FILTERING -
DROP rule: iptables: can't initialize iptables table `raw': Table does not exist
```

That is the host kernel lacking netfilter support, not a problem with these files. Switching the
daemon to the nftables backend (`dockerd --firewall-backend nftables`) fails the same way when
nftables is equally incomplete, so the working option is host networking:

```bash
docker compose -f docker-compose.yml -f docker-compose.host-network.yml up -d --build
```

Every container then shares the host network namespace, so Compose service names do not resolve and
port mappings do not apply. The override moves each service onto its own port and wires them
together explicitly:

| Service | Default port | Host-network port | Why different |
| --- | --- | --- | --- |
| `postgres` | 5432 | 5433 | A host installation usually already owns 5432 |
| `backend` | 8080 (internal) | 8081 | 8080 is left for the storefront |
| `frontend` | 8080 (published) | 8088 | No port publishing under host networking |

Open **http://localhost:8088**. The trade-offs are real: the backend is now directly reachable, and
the ports collide with anything else on the host. Use the standard `docker-compose.yml` wherever
bridge networking works.

The port moves mean the `psql` and backup commands need `-p 5433` added:

```bash
docker compose -f docker-compose.yml -f docker-compose.host-network.yml exec postgres \
    psql -p 5433 -U shopwise -d shopwise -c '\dt'
```

Everything else in this document applies unchanged.
