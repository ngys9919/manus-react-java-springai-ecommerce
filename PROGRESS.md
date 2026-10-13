# Shopwise — build status

Working notes kept in the repo so the state of the build is recoverable.

**Status: complete.** Backend, frontend, assistant, database, media, documentation and screenshots are
all in place and verified. End-to-end suite: **88 / 88 passing**.

## Verified environment

| Component | Version | Notes |
| --- | --- | --- |
| Java | OpenJDK 21.0.12 | `openjdk-21-jdk-headless` installed for `javac`/`javap` |
| Maven | 3.9.9 | `/opt/apache-maven-3.9.9`, symlinked to `/usr/local/bin/mvn` |
| PostgreSQL | 16.15 | db `shopwise`, user `shopwise` / `shopwise`, plus `shopwise_test` |
| Node | 22.13.0 | npm 10.9.2 |
| Spring Boot | 4.1.1 | parent POM |
| Spring AI | 2.0.0 | `spring-ai-starter-model-openai`, built on the official `com.openai:openai-java` SDK |
| springdoc | 3.1.1 | Swagger UI at `/swagger-ui.html` |
| Chromium | system | headless driver for `scripts/capture-screens.py` |

Model access goes through an OpenAI-compatible endpoint (`OPENAI_API_KEY` / `OPENAI_BASE_URL`), model
`gpt-5-mini`. Backend on `:8080`; Vite preview on `:4173` proxying `/api` → `:8080`.

## Making it permanent

The application was developed and verified inside a Manus Sandbox. That is not a home for a site
anyone relies on: the sandbox hibernates when idle and the HTTP URL it exposes is explicitly
temporary, so "permanent" is a decision about the host rather than about the code.

What was missing for a host that stays up, and is now in place:

| Gap | Fix |
| --- | --- |
| Nothing restarted the stack after a reboot. `restart: unless-stopped` restarts *containers*, and only while the Docker daemon runs; the machine reboots without being asked when a tier is upgraded or a backup is restored | `deploy/shopwise.service`, a systemd oneshot unit that comes up after `docker.service` and runs the stack from the checkout |
| Bringing a fresh host up was a manual sequence of eight steps with two secrets to generate by hand | `scripts/deploy-persistent.sh` — idempotent, with `--dry-run`, `--origin` and `--no-systemd`. It never rewrites an existing `.env` |
| A host that meters outbound traffic shuts *down* when the allowance is spent, so the site does not degrade, it stops | Called out in `docs/DEPLOYMENT.md`, with the auto top-up link |
| The README's tail was structurally broken: the screenshot table had lost two rows to the end of the file, and "With Docker" / "From source" were orphaned under "Design decisions" with the second having no body | Repaired; heading levels now nest cleanly |

The Docker Compose stack itself needed no changes to become deployable — it already had restart
policies, healthchecks, a named data volume, an internal network and only one published port — which
is the payoff for containerising it properly in the first place.

**Still open:** the host has not been chosen. The code is deployable to any always-on Linux host
with one command, so the remaining decision is which host to run it on and at which size — see
[A permanent deployment](docs/DEPLOYMENT.md#a-permanent-deployment) and
[Resource sizing](docs/DEPLOYMENT.md#resource-sizing).

## Deploying to Railway

Railway does not run `docker-compose.yml`; each Compose service becomes its own Railway service.
The mapping is in [docs/RAILWAY.md](docs/RAILWAY.md), and three things about it needed code or
configuration rather than just documentation.

| Railway trait | Consequence | Fix |
| --- | --- | --- |
| `depends_on: condition: service_healthy` has no equivalent — their documentation says so outright | The backend can boot before PostgreSQL accepts connections. The first Flyway connection fails, the context dies with it, and the deploy is marked failed even though the database arrives seconds later. This is the single most likely way to fail | `spring.flyway.connect-retries: 24` at 5s intervals. Verified by pointing the backend at a dead port: it retried with a 1-5s backoff instead of exiting |
| Public traffic is routed to whatever port the process listens on | A fixed nginx listener would sit on 80 while the platform routes elsewhere, and the service would look unreachable while healthy | The frontend honours `PORT`. Verified by rendering the config with `PORT=9000` |
| Private networking hands a service a new address on redeploy, and nginx resolves an upstream once at startup and caches it | Two failures: nginx refuses to start at all if the backend's name does not resolve *during* startup, and it keeps proxying to a stale address after a backend redeploy | `resolver` + `zone` + `resolve` on the upstream block, with the resolver read from /etc/resolv.conf at startup so one image works on Docker and on Railway. Verified by running the image with an upstream that cannot resolve: nginx now starts and returns a per-request 502 |

The entrypoint shim is named `.envsh`, not `.sh`, because the nginx image *sources* `.envsh` files
and *executes* `.sh` files — an executed script's exports never reach
`20-envsubst-on-templates.sh`, so the listen port would silently stay at 80.
Re-ran the full suite against the rebuilt container stack afterwards: **95 passed, 0 failed**.

Checked against Railway's documentation rather than assumed, because the deployment path matters: a
**project token is not required**. Railway's quick start describes deploying directly from a GitHub
repository through the dashboard, and states that Railway requires a linked GitHub account and
prompts for one. The token belongs to the CLI path only. The repository is public, so the Railway
GitHub App can read it once the account is linked, with no per-repository grant. Two services are
still needed from the one repository, which their monorepo guide covers as the "isolated monorepo"
case: set a root directory per service. docs/RAILWAY.md now leads with that path.

## Findings that shaped the code

1. **Spring Boot 4 uses Jackson 3** (`tools.jackson.*`). `com.fasterxml.jackson.databind.ObjectMapper`
   is not auto-configured. The message mapper therefore does explicit map <-> DTO conversion and
   needs no `ObjectMapper` bean.
2. **Spring Boot 4 modularised auto-configuration.** Flyway only runs when
   `spring-boot-starter-flyway` is on the classpath; `flyway-core` alone is not enough.
   The starter plus `flyway-database-postgresql` are both declared.
3. **`spring.jackson.serialization.write-dates-as-timestamps` no longer exists** in Jackson 3 and
   fails property binding at startup. Removed.
4. **Spring AI rejects a tool context containing null values** ("context values cannot contain null
   elements"). `AssistantToolContext` omits absent keys instead of storing nulls.
5. **Transaction boundaries:** the assistant loop is deliberately *not* wrapped in one transaction.
   Audit rows are written by `AuditWriter` in their own `REQUIRES_NEW` transaction with an explicit
   flush, so a failed analytics insert can never roll back a shopper's cart or conversation.
6. **Detached entity safety:** `ConversationService` reloads the managed row before mutating and
   copies state back, so a stale in-memory conversation cannot overwrite an escalation.

## Bugs found and fixed during the build

All confirmed fixed by re-testing.

| # | Bug | Fix |
| --- | --- | --- |
| 1 | `search_products` passed `maxPrice` into the `minPrice` slot, so "under $1500" returned the most expensive laptops | Correct argument order; asserted by the suite |
| 2 | Audit inserts ran inside the assistant's outer transaction and could roll back the shopper's turn (`UnexpectedRollbackException`) | Removed the outer transaction; all audit writes go through `AuditWriter` (`REQUIRES_NEW` + flush) |
| 3 | A detached `Conversation` was merged after a tool set `ESCALATED`, reverting it to `ACTIVE` | Reload the managed row before mutation and copy state back |
| 4 | Every assistant turn threw on null tool-context values | Omit absent keys |
| 5 | Empty model answer discarded the whole turn, including a correct comparison table | Keep the tool blocks, narrate deterministically, flag `degraded` honestly |
| 6 | `compare_products` accepted only SKUs, so the model had to search first and sometimes gave up | It now resolves SKUs, slugs, exact names and partial names in one call |
| 7 | Quick replies rendered inside a block were repeated below the message | Trailing replies exclude labels already shown in a block |
| 8 | Product artwork used the wrong glyph and was not centred | Corrected category keys and the glyph transform; `dist/` rebuilt afterwards |
| 9 | Resolution rate reported **131.9%** because quality rates were divided by inbound messages while the numerator counted turns that were refused, held or rate limited | Quality rates are now shares of answered turns, and every rate is clamped to 0–100 |
| 10 | Escalation `summary` embedded 1,200 characters of transcript, so the staff queue showed a wall of text | `summary` is a one-line headline capped at 200 characters; the transcript travels in its own `transcriptExcerpt` field and is shown on demand |
| 11 | Daily volume bars collapsed to a 3px line | A percentage height needs a parent with a resolved height; added a `.bar-chart__track` and capped the bar width so a single day reads as a bar |
| 12 | A catalogue question answered without any tool call put ungrounded prices in front of the shopper | Added a grounding guard: one retry with an explicit nudge when a catalogue intent produces no tool call. The suite went from flaky (65–66/67) to three consecutive 67/67 runs |
| 13 | Seeded co-purchase was too sparse to support a "frequently bought together" rail: 81 purchases across 24 products produced 26 pairs, and the product on the page had one | Rewrote the V3 seed around deliberate bundles. Shoppers now take exactly one complementary product from a small per-product bundle set, so the same pairings recur across many independent baskets. 160 shoppers produce 255 purchases and 256 pairs, and the rail reports real shares (55%, 38%, 28%) |
| 14 | Letting a buyer take both of a product's bundle partners produced baskets with two laptops and one pair of headphones, which is not what a basket looks like | Restricted the bundle step to one complement per buyer with `DISTINCT ON (actor_key)`. Baskets are now 2 lines, and no laptop-laptop pair survives |
| 15 | "Similar products" returned a camera for a laptop, because the diversity penalty pushed genuine matches below an unrelated one | Made diversity strategy-aware (0.08 for similar/bought rails, 0.25 for for-you/trending) and added a similarity floor plus a cross-category discount, so a different-category product can surface only when the behavioural evidence is much stronger |
| 16 | Three rails on a product page showed almost the same headphones three times | Dropped the "customers also viewed" rail from the page; the endpoint stays for the assistant. Two rails that say different things read as deliberate, three near-identical ones read as broken |
| 17 | The cart rail showed "Not personalised yet", which is a claim about personalisation, not about thin cross-sell evidence | The fallback pill now appears only on the personalised rail; the others explain themselves in the note instead |
| 18 | Asked what goes with a named product, the model searched for that product first and put a one-card block above the useful results | Added a prompt rule, and a reply-assembly rule that drops a single-product card block when the same reply also offers a richer set. A single-result search on its own is still shown, because there the card is the answer |
| 19 | Free-text search matched the query as one phrase, so `TitanForge X16 gaming laptop` and `gaming laptop` both returned nothing while `TitanForge X16` worked. The assistant then told a shopper the store did not stock a product it did | Every word must match somewhere in the product; the words need not be adjacent |
| 20 | The grounding guard was keyed on the classifier's intent, and the classifier returns `unknown` for "what do people buy with X". The guard was skipped and the model reported a stocked product as absent | A reply that makes a catalogue claim — a negative finding, a stock claim, a price, a SKU — is guarded on its own merits whatever the intent says |
| 21 | When the guard's retry also declined to call a tool, the ungrounded prose was shown anyway. Asked for a laptop under $2,000 the model said nothing in stock fitted, while two laptops did, and it had called no tool | An ungrounded turn is no longer shown; the reply is built from the catalogue with a notice saying so |
| 22 | The deterministic responder passed the whole sentence as a search query, so once the guard routed turns to it a plain catalogue question became "I could not match that to something I can look up" | It searches the distinctive words first, then the filters alone |
| 23 | An empty recommendation was invisible: the tool told the model there was no history and added no block, so a turn with no model prose showed a bare search result and never explained itself | An empty rail emits a notice block |
| 24 | A recommendation rail was narrated with the search-result wording ("here is what matched"), discarding the reason that makes a rail worth showing | Recommendation rails narrate their own reasons |
| 25 | The model described a rail with real 55%/38%/28% co-purchase shares as "not much purchase history yet" | Prompt rule forbidding invented thin-evidence hedges |
| 26 | `hi` and `thanks` classified as `unknown`, because the greeting list contained `"hi "` with a trailing space | Greeting and recommendation phrasing both matched properly |
| 27 | **Reported by the user.** Clicking a conversation starter in the assistant failed with "That did not go through. The request could not be completed." The backend answered the same message correctly over curl, so the suite was green while the browser was broken | See 28 and 29 - two independent causes, and the visible one was the second |
| 28 | Every mutating request from a browser returned `403 Invalid CORS request`. The backend only accepted the browser's `Origin` when it matched the request's own authority, but the Vite preview proxy sets `changeOrigin: true` and rewrites `Host` to its target, so the backend saw `localhost:8080` while the browser sent the public hostname and the two could never match. GETs are unaffected (browsers omit `Origin` on same-origin GETs), which is why the storefront loaded and only sending failed | Added `shopwise.security.allowed-origin-patterns` (wildcards, `SHOPWISE_ALLOWED_ORIGIN_PATTERNS`), wired through `setAllowedOriginPatterns`, and logged every rejection with the origin, `Host` and `X-Forwarded-Host` so the next occurrence is a log line rather than a browser-side mystery |
| 29 | `FallbackResponder.recommend` passed the shopper's whole sentence as the search query. `search` had been fixed for this; `recommend` had not, so "What is the best camera for travel?" - a starter the assistant itself offers - answered "I could not match that to something I can look up" whenever the model declined to call a tool | Both paths now share one `productsFor` helper: distinctive words first, then the filters alone |
| 30 | A new visitor's first page view could return `500` with `duplicate key value violates unique constraint "uq_cart_guest_active"`. `CartService.resolveCart` read then inserted, and the cart view, the assistant and the recommendation rails all fire together | `CartRepository.insertActiveGuestCartIfAbsent` / `insertActiveUserCartIfAbsent` create the cart as a single `INSERT ... ON CONFLICT DO NOTHING`, which removes the race rather than catching it (a caught violation would already have marked the transaction rollback-only). Verified with 16 simultaneous first-time requests on one token |

## Verified behaviour

`scripts/smoke-test.py` covers catalogue search and facets, guest cart rules, stock guards, guest
checkout, order-tracking permissions, authentication, role-gated analytics, the assistant tool loop,
comparison rendering, context-aware follow-up, prompt-injection blocking, card redaction, escalation,
`/resume`, feedback, transcript ownership, analytics aggregation and the OpenAPI document.

**Latest run: 95 passed, 0 failed** (88 + 7 CORS checks added by bug 28), and 94/94 against the
container stack, where the opt-in deployed-origin check does not run.

The CORS fix was completed by trusting `Sec-Fetch-Site: same-origin`. Comparing the Origin against
the request's own authority needs the original `Host`, which a Host-rewriting proxy destroys, so any
deployment at a hostname the backend cannot know would have needed hand configuration - and would
have broken again on the next restart that lost it. `Sec-Fetch-Site` is a forbidden header name: a
page cannot write it and the browser derives it from the real relationship between the two origins,
so it keeps the check meaningful while making configuration unnecessary. Verified by running the
whole suite and the browser flow with `SHOPWISE_ALLOWED_ORIGIN_PATTERNS` unset, with cross-site
origins still rejected.

`scripts/assistant-reco-scenario.py` drives one eight-turn shopper conversation through the
assistant and reads which tools fired back from the `assistant_event` audit log after every turn,
rather than trusting the reply text. That is what surfaced bugs 19-26. **Latest run: 28/28**, and
five of six runs after the fixes were 28/28; the sixth was 22/28, all six failures being the same
tool-choice variance (documented as a known limitation in `docs/ASSISTANT.md`).

`scripts/capture-screens.py` drives headless Chromium through 24 screens and interactions across
desktop and mobile viewports, asserts the expected elements render, and reports console errors
(currently none). Output is in `artifacts/screenshots/`.

## Verification commands

```bash
python3 scripts/smoke-test.py http://localhost:8080                 # 94/94
# ...and with the deployment's own hostname, which is the check that reproduces bug 28:
SHOPWISE_TEST_ORIGIN=https://your-host.example.com python3 scripts/smoke-test.py http://localhost:8080
python3 scripts/assistant-reco-scenario.py http://localhost:8080    # 28/28
cd frontend && npx tsc -b && npm run build                          # clean
python3 scripts/capture-screens.py http://localhost:4173 artifacts/screenshots   # 24/24, 0 console errors
python3 scripts/generate-product-media.py                           # regenerate artwork

# The same suites against the containerised stack (host-network variant: frontend on 8088)
python3 scripts/smoke-test.py http://127.0.0.1:8088                 # 88/88
PGPORT=5433 python3 scripts/assistant-reco-scenario.py http://127.0.0.1:8088
```

## Recommendations
Built from behaviour, with attributes as the safety net for products nobody has bought yet. Two
signals are blended by the weaker of the two products' confidence, so a well-understood pair is
described by what shoppers did and a cold pair by what the products are.

| Rail | Question | Placement |
| --- | --- | --- |
| `for_you` | What should I look at? | Home, above the catalogue |
| `similar` | What else is like this? | Product page |
| `also_bought` | What do people actually buy with this? | Product page |
| `cart_cross_sell` | What am I missing? | Cart, keyed on basket contents |
| `trending` | What is popular right now? | Cold-start fallback for `for_you` |

Every item carries a reason, every rail that could not use its intended signal says so, and
impressions are counted only when a rail is scrolled into view. Behaviour is recorded server-side;
the browser may only report impressions and clicks, and `POST /recommendations/events` rejects
anything else, so a crafted request cannot inflate the signal that drives other shoppers' rails.

## Notes for whoever picks this up

- `frontend/scripts/.tools/` holds the local `puppeteer-core` install used by the capture script; it is
  a build-time convenience, not part of the app.
- The smoke test's assistant section asserts on real model behaviour, so it is intentionally tolerant
  of wording while strict about structure (block types, permissions, moderation outcomes).
- `artifacts/architecture.d2` and `artifacts/architecture.mmd` are the same diagram in two formats; the
  Mermaid version lays out more cleanly for nested containers.
- Regenerating product media requires a rebuild before it is visible in `npm run preview`, because
  `public/` is copied into `dist/` at build time.
- The recommendation model is rebuilt lazily and cached on a TTL (`shopwise.recommendations.model-ttl-seconds`).
  It invalidates on restart. At this catalogue size the full pairwise computation costs milliseconds;
  a much larger one would move the same computation into a scheduled job writing a similarity table,
  leaving every call site unchanged.
- V3 was edited in place while the recommendation feature was being built, which is why a database
  created before that point needs recreating (`DROP SCHEMA public CASCADE` or `docker compose down -v`).
  Flyway checksums a migration once it has been applied.
