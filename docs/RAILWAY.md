# Deploying to Railway

Railway does not run `docker-compose.yml`. Each service in the Compose file becomes its own Railway
service, so the three containers become three services, and the differences that actually bite are
these:

| Compose concept | Railway | What it means here |
| --- | --- | --- |
| `services:` entry | A Railway service | Three services: `Postgres`, `backend`, `frontend` |
| `build:` | Deploy from the GitHub repo with a Dockerfile | Root directory set per service; Railway picks up the `Dockerfile` it finds |
| `ports:` | Public networking | Only the **frontend** needs a domain; the backend is reached privately |
| `networks:` | Automatic private networking | No configuration; services resolve each other at `<service-name>.railway.internal` |
| `volumes:` | Railway Volumes, or a managed database | Use Railway's managed Postgres, so no volume is needed |
| `environment:` | Railway Variables | Same values, but reference variables instead of hardcoding |
| `depends_on: condition: service_healthy` | **No equivalent** | See below — this is the one that needs a code change, and it is already made |

There is no port-mapping layer on the private network: a service is reached at the port the process
actually listens on. The backend therefore stays on `8080` and the frontend must serve on whatever
port Railway routes to, which is why it honours `PORT` (see
[Ports](#ports-something-railway-handles-differently)).

## What it costs

Railway bills for usage, not for the site. Published rates are per vCPU and per GB of RAM per month,
billed per second, plus volume storage:

| Plan | Price | Notes |
| --- | --- | --- |
| Free | $1 of credit per month | Also caps services at roughly 1 vCPU / 0.5 GB RAM and 0.5 GB of volume storage |
| Trial | 30 days, $5 of credit | One-off |
| Hobby | $5/month, including $5 of usage credit | The realistic entry point |
| Pro | $20/month, including $20 of usage credit | |

This stack is not free-tier shaped. A JVM plus PostgreSQL is the expensive part — expect the backend
to settle around 0.4 GB of RAM and Postgres around 0.1–0.3 GB, which at the published rates is
roughly **$5–8 per month**. On the Free plan the 0.5 GB per-service cap is tight for the backend and
a JVM that has to fit 384 MB of heap will spend its time in garbage collection. Start on Hobby and
watch the usage page.

Check the current numbers at [railway.com/pricing](https://railway.com/pricing) — they change.

## Architecture on Railway

```
Internet ──▶ frontend            (public Railway domain, nginx, serves the SPA and proxies /api)
                 │
                 │  private network
                 ▼
             backend             (no public domain, Spring Boot on :8080)
                 │
                 ▼
             Postgres            (managed, private by default)
```

The browser only ever talks to the frontend. The frontend proxies `/api` and `/actuator` to the
backend over the private network, so there is one origin, no CORS configuration, and the API is
not reachable from the internet.

## Before you start

1. A Railway account and the repository pushed to GitHub (this repo is already there).
2. An OpenAI-compatible API key, **if** you want the model-driven assistant. Without one the stack
   still runs: the assistant answers from the deterministic catalogue rules and says so in the UI.
   See [Running without a model key](DEPLOYMENT.md#running-without-a-model-key).

## Variables

The namespace in a reference variable is the **service name**, so `${{Postgres.PGHOST}}` assumes the
database service is called `Postgres` (Railway's default). If you rename it, update every reference.

### `backend`

Set these in the service's **Variables** tab. The Raw Editor accepts the whole block at once.

```
SHOPWISE_DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SHOPWISE_DB_USER=${{Postgres.PGUSER}}
SHOPWISE_DB_PASSWORD=${{Postgres.PGPASSWORD}}
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
SHOPWISE_JWT_SECRET=<paste the output of: openssl rand -base64 48>
```

Add these only if you have a model key:

```
OPENAI_API_KEY=<your key>
OPENAI_API_BASE=https://api.openai.com/v1
SHOPWISE_AI_MODEL=gpt-5-mini
```

`SHOPWISE_JWT_SECRET` is not optional. The application refuses to start with the development
placeholder once a non-local profile is active, which is exactly what `prod` is.

### `frontend`

```
SHOPWISE_API_UPSTREAM=backend.railway.internal:8080
```

That is the only variable it needs. The upstream is a plain string rather than a reference variable
because the backend is pinned to `8080`, so there is nothing to keep in sync.

### `Postgres`

Nothing to set. Railway provisions the database and exposes `PGHOST`, `PGPORT`, `PGUSER`,
`PGPASSWORD`, `PGDATABASE` and `DATABASE_URL`.

## Deploying

### Option A — from the GitHub repository (no token needed)

This is the path to prefer. Railway deploys straight from a GitHub repository through its dashboard.
A token is only needed for the CLI, in Option B — so if you do not have one, you do not need one.

Railway requires a GitHub account to be linked and prompts you to link it if it is not already. This
repository is **public**, so once your account is linked Railway can read it without you granting it
access to a specific private repository.

Start at the [dashboard](https://railway.com/dashboard): **New Project → Empty project**. An empty
project rather than the "deploy from GitHub repo" shortcut, because this install needs *two* services
from the one repository, so a GitHub service is added from the canvas either way.

1. **Add the database first, so the references resolve.** **+ New → Database → PostgreSQL**.
   Leave the service name as `Postgres` — the backend's variables reference it by that name.

2. **Add the backend.** **+ New → GitHub Repo**, choose
   `ngys9919/manus-react-java-springai-ecommerce` and the `main` branch.
   Rename the service to `backend`, then in its **Settings** set **Root Directory** to `backend`.
   Railway finds `backend/Dockerfile` and builds from it.
   Under **Variables**, paste the backend block from the table above.
   Under **Settings → Deployments**, set the **Healthcheck Path** to `/actuator/health` with a
   generous timeout: Flyway migrates and seeds the catalogue on the first boot, which takes longer
   than a normal start.
   **Do not generate a public domain for this service** — it is reached privately.

3. **Add the frontend.** **+ New → GitHub Repo**, same repository. Rename it to `frontend`,
   set **Root Directory** to `frontend`, and add its one variable
   (`SHOPWISE_API_UPSTREAM=backend.railway.internal:8080`).
   Set its **Healthcheck Path** to `/healthz`.

4. **Give the frontend a domain.** On the frontend service, **Settings → Networking →
   Generate Domain**. That domain is the site; Railway provisions TLS for it automatically.
   If Railway asks which port to route to, the answer is `80` — or whatever the service logs as its
   listen port, since it follows `PORT` if the platform sets one.

Railway builds and deploys on every push to `main` from then on. Consider setting **Watch Paths** per
service (`backend/**` and `frontend/**`) so a change to one does not rebuild the other.

### Option B — the CLI

The CLI deploys the working directory directly, which avoids wiring up GitHub at all. It needs a
Railway token.

```bash
npm install -g @railway/cli

export RAILWAY_TOKEN=<a project token from Project Settings → Tokens>

railway init --name shopwise
railway add --database postgres
railway add --service backend
railway add --service frontend
railway variables --service backend --set 'SHOPWISE_DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}' ...
railway up --service backend
railway up --service frontend
railway domain --service frontend
```

A project token is scoped to one project and can be revoked from the same settings page. This is the
path to take if you would rather not click through the dashboard.

## Ports: something Railway handles differently

Railway routes public traffic to the port your process listens on. Both images are already set up
for that:

- **frontend** — `SHOPWISE_LISTEN_PORT` defaults to `80`, and a small entrypoint script
  (`frontend/docker-entrypoint.d/05-shopwise-ports.envsh`) overrides it with `PORT` when the platform
  sets one. The healthcheck reads the same variable, so it follows the listener rather than assuming
  `80`. Compose sets no `PORT`, so nothing changes locally.
- **backend** — pinned to `8080` by `SERVER_PORT`, because the frontend addresses it at
  `backend.railway.internal:8080`. It deliberately does not follow `PORT`: a moving internal port
  would be one more thing to keep in sync for no benefit, since it has no public domain.

## The one real trap: `depends_on` has no equivalent

Compose starts the backend only once PostgreSQL reports healthy. Railway has no equivalent — its own
documentation says applications should handle connection retries themselves.

Without that handling, the first Flyway connection fails if the database is still provisioning, the
application context dies with it, and the deploy is marked **failed** even though the database
arrives seconds later. This is the single most common way this stack would fail on Railway, and it
looks like a broken application rather than a race.

`application.yml` therefore sets:

```yaml
spring.flyway.connect-retries: 24
spring.flyway.connect-retries-interval: 5s
```

Two minutes of patience, costing nothing when the database is already up. Set
`SHOPWISE_DB_CONNECT_RETRIES` to change it.

## Verifying the deployment

Railway gives the frontend a `*.up.railway.app` domain with TLS already configured. Point the
project's own test suite at it — it needs no credentials and checks the whole API surface through
the nginx proxy:

```bash
python3 scripts/smoke-test.py https://<your-domain>.up.railway.app
SHOPWISE_TEST_ORIGIN=https://<your-domain>.up.railway.app \
  python3 scripts/smoke-test.py https://<your-domain>.up.railway.app
```

Then confirm the browser path, which is the part a curl-based suite cannot see:

```bash
python3 scripts/capture-screens.py https://<your-domain>.up.railway.app artifacts/railway-screens
```

The first run migrates and seeds the catalogue, so the screenshots should show products, not an
empty shop.

## Migrations and redeploys

Flyway runs at startup and is idempotent: an already-migrated database is left alone. Never edit an
applied migration file — add a new one, or checksum validation fails on the next boot. Because
Railway has no pre-deploy hook configured here, a failing migration stops the new container rather
than replacing a working one, which is the behaviour you want.

## Backups

Railway's managed Postgres supports native backups in the service settings; enable them. The dump
command in [DEPLOYMENT.md](DEPLOYMENT.md#backups) also works against Railway, substituting the
connection details from `DATABASE_PUBLIC_URL` (which requires enabling Public Access and is billed
as network egress), or by running it from the backend service over the private network.

## A note on the assistant

The model-driven assistant needs `OPENAI_API_KEY`. The key used during development was provided by
the sandbox that built this project and does not exist on Railway, so **a fresh deployment without a
key will run in degraded mode**: the welcome flow, catalogue search, comparison, cart actions,
escalation and analytics all work, and answers come from the deterministic rules, with the UI saying
so. That is deliberate — the degradation is a feature, not a failure — but it is not the assistant
as demonstrated. Add your own key to get the full behaviour.

## Where these details come from

Platform behaviour changes; these were checked against Railway's own documentation rather than
assumed:

- [Quick start](https://docs.railway.com/quick-start) — deploying straight from a GitHub repository
  through the dashboard, and the statement that Railway requires a linked GitHub account and prompts
  for one. This is why no project token is needed for Option A: the token belongs to the CLI path.
- [Deploying a monorepo](https://docs.railway.com/deployments/monorepo) — this repository is an
  *isolated* monorepo, the exact case that document describes as "define a root directory for the
  service". It also notes a config file does not follow the root directory, which is moot here since
  no such file is used.
- [Dockerfiles](https://docs.railway.com/builds/dockerfiles) — Railway uses a `Dockerfile` it finds
  in the source directory, and only injects variables into the build if they are declared with `ARG`.
- [Deploy a Docker Compose app to production](https://docs.railway.com/guides/docker-compose) — the
  Compose-to-Railway mapping table, private networking as `<service-name>.railway.internal`, "no port
  mapping layer", and the statement that `depends_on` has no equivalent.
- [Variables reference](https://docs.railway.com/reference/variables) — the `${{Service.VAR}}`
  template syntax and the namespace being the service name.
- [PostgreSQL](https://docs.railway.com/guides/postgresql) — the exposed variables, and that the
  database is private by default.
- [Public networking](https://docs.railway.com/guides/public-networking) and
  [pricing](https://railway.com/pricing).
- [Config as code](https://docs.railway.com/reference/config-as-code) — currently deprecated in
  favour of Infrastructure as Code, with existing files supported only until 2026-12-01. That
  deprecation is why this guide configures services in the dashboard rather than shipping a
  `railway.toml` that would expire within weeks.

Two of the changes in this repository came out of testing rather than reading, and both are
verifiable locally:

- With a statically resolved upstream, nginx refuses to start when the backend's name does not
  resolve — `host not found in upstream "backend:8080"`, container exits. The `resolve` directive
  turns that into a per-request 502 instead, so the frontend can start before the backend and
  survives a backend redeploy that changes its address. Verified by running the image with
  `SHOPWISE_API_UPSTREAM=no-such-host.invalid:8080`.
- `PORT` is honoured, and the rendered configuration was checked with `PORT=9000`: nginx listens on
  9000, `/healthz` answers there, and port 80 stops serving.
