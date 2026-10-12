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

## Verified behaviour

`scripts/smoke-test.py` covers catalogue search and facets, guest cart rules, stock guards, guest
checkout, order-tracking permissions, authentication, role-gated analytics, the assistant tool loop,
comparison rendering, context-aware follow-up, prompt-injection blocking, card redaction, escalation,
`/resume`, feedback, transcript ownership, analytics aggregation and the OpenAPI document.

**Latest run: 88 passed, 0 failed.**

`scripts/capture-screens.py` drives headless Chromium through 22 screens and interactions across
desktop and mobile viewports, asserts the expected elements render, and reports console errors
(currently none). Output is in `artifacts/screenshots/`.

## Verification commands

```bash
python3 scripts/smoke-test.py http://localhost:8080                 # 88/88
cd frontend && npx tsc -b && npm run build                          # clean
python3 scripts/capture-screens.py http://localhost:4173 artifacts/screenshots   # 22/22, 0 console errors
python3 scripts/generate-product-media.py                           # regenerate artwork
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
