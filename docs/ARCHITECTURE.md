# Architecture

## System overview

```mermaid
flowchart TB
    subgraph Browser["Browser · Vite + React 19 + TypeScript"]
        UI["Storefront pages<br/>home · shop · product · cart · checkout · orders · login · insights"]
        PANEL["Assistant panel<br/>onboarding · message list · block renderer · composer"]
        STATE["Context state<br/>auth · cart · assistant · toast"]
        APICLIENT["Typed API client<br/>problem+json normalisation · guest token"]
        UI --> STATE
        PANEL --> STATE
        STATE --> APICLIENT
    end

    APICLIENT -->|"JSON over HTTP<br/>X-Guest-Token / Bearer JWT"| API

    subgraph Backend["Spring Boot 4.1.1 · Java 21"]
        API["REST controllers<br/>catalog · cart · orders · auth · assistant · insights"]
        SEC["Security<br/>JWT filter · actor resolution · role gates"]
        SVC["Services<br/>catalog · cart · order · auth · conversation · escalation · analytics"]
        ASST["Assistant core"]
        TOOLS["Tool callbacks<br/>13 catalogue / cart / order / policy tools"]
        GUARD["Moderation<br/>inbound + outbound screening"]
        RATE["Rate limiting<br/>conversation + identity windows"]
        FALLBACK["Deterministic fallback responder"]
        AI["Spring AI 2.0 ChatClient<br/>+ OpenAI-compatible model"]
        AUDIT["AuditWriter<br/>REQUIRES_NEW, flushed"]

        API --> SEC --> SVC
        API --> ASST
        ASST --> GUARD
        ASST --> RATE
        ASST --> AI
        ASST --> FALLBACK
        AI -->|tool calls| TOOLS
        TOOLS --> SVC
        ASST --> AUDIT
        SVC --> AUDIT
    end

    SVC --> REPO
    TOOLS --> REPO
    AUDIT --> REPO

    subgraph Data["PostgreSQL 16"]
        REPO["Spring Data JPA"]
        FLY["Flyway migrations<br/>V1 schema · V2 seed"]
    end
```

## Request lifecycle: one assistant turn

This is the part worth understanding, because the ordering is deliberate.

```mermaid
sequenceDiagram
    autonumber
    participant U as Shopper
    participant P as Assistant panel
    participant C as AssistantController
    participant S as AssistantService
    participant G as Moderation
    participant M as Spring AI ChatClient
    participant T as Tools
    participant DB as PostgreSQL

    U->>P: "Compare the Pulse 12 Pro and the Vertex Ultra 5G"
    P->>C: POST /api/v1/assistant/chat
    C->>S: chat(actor, request)

    S->>DB: find or create conversation
    S->>S: handle slash commands, escalated state
    S->>S: rate-limit check (conversation + identity)
    S->>G: screen inbound message
    alt blocked or redacted
        G-->>S: verdict (injection, card data, self-harm, spam)
        S-->>P: refusal with guidance, model never called
    end

    S->>S: classify intent + extract entities (budget, category, SKUs, order ref)
    S->>DB: persist the user turn
    S->>DB: load memory window (recent turns)

    S->>M: system prompt + history + tools
    M->>T: search_products / compare_products / ...
    T->>DB: catalogue queries
    DB-->>T: products
    T-->>M: tool results (+ rich blocks for the UI)
    M-->>S: final answer text

    S->>G: screen the outbound draft
    S->>DB: persist the assistant turn (blocks, tool calls, latency)
    S->>DB: write analytics events (own transaction)
    S-->>P: reply + blocks + quick replies + degraded flag
    P-->>U: rendered comparison table + verdict + next steps
```

### Why it is ordered this way

| Step | Reason |
| --- | --- |
| Commands before moderation | `/help`, `/end` and `/resume` are UI control, not shopper prose. |
| Escalated check before the model | Once a human owns the conversation, the bot must not talk over them. |
| Rate limit before moderation | Cheap, and stops a runaway client from filling the moderation log. |
| Moderation before persistence | Blocked content is logged in `moderation_log` with a redacted excerpt, never replayed into the model's memory. |
| Classification before the model call | The extracted budget/category/SKUs are injected into the system prompt as explicit signals, which makes the model's tool arguments far more reliable. |
| Outbound screening after the model | Stops the assistant echoing card numbers or leaking its instructions. |
| Analytics last, in its own transaction | A failed insert must never roll back the shopper's turn. |

## Transaction boundaries

The assistant loop is **not** wrapped in a single transaction, which is unusual and intentional.

```mermaid
flowchart LR
    A["AssistantService.chat<br/>no transaction"] --> B["ConversationService<br/>@Transactional per call"]
    A --> C["CartService<br/>@Transactional per call"]
    A --> D["ModerationService<br/>no transaction"]
    A --> E["AuditWriter<br/>REQUIRES_NEW + flush"]
    D --> E
    A --> F["EscalationService<br/>@Transactional"]
```

- Each unit of work commits on its own, so a failure recording analytics cannot undo a cart change or
  a saved message.
- `AuditWriter` uses `REQUIRES_NEW` **and an explicit flush**, so a database problem surfaces as a
  catchable exception inside the method rather than as a commit-time `UnexpectedRollbackException`
  escaping the caller.
- `ConversationService` reloads the managed row before every mutation and copies the result back onto
  the caller's detached instance. Without this, a stale in-memory conversation would overwrite the
  `ESCALATED` status set moments earlier by a tool.

## Data model

```mermaid
erDiagram
    APP_USER ||--o{ CART : owns
    APP_USER ||--o{ SHOP_ORDER : places
    APP_USER ||--o{ CONVERSATION : starts
    APP_USER ||--o{ ASSISTANT_EVENT : generates

    CATEGORY ||--o{ PRODUCT : contains
    PRODUCT ||--o{ PRODUCT_INTERACTION : "is the subject of"
    PRODUCT ||--o{ CART_ITEM : "added as"
    PRODUCT ||--o{ ORDER_ITEM : "sold as"

    CART ||--o{ CART_ITEM : holds
    SHOP_ORDER ||--o{ ORDER_ITEM : contains
    CONVERSATION ||--o{ CHAT_MESSAGE : contains
    CONVERSATION ||--o{ ESCALATION_TICKET : raises
    CONVERSATION ||--o{ ASSISTANT_EVENT : records
    CONVERSATION ||--o{ MODERATION_LOG : screens

    APP_USER {
        bigint id PK
        string email UK
        string password_hash
        string full_name
        string role "CUSTOMER | SUPPORT | ADMIN"
    }
    PRODUCT {
        bigint id PK
        string sku UK
        string slug UK
        string name
        string brand
        numeric price
        numeric compare_at_price
        int stock_quantity
        numeric rating
        int review_count
        jsonb attributes "spec sheet"
        text_array highlights
        text_array tags
        string image_url
        string accent_color
        boolean active
    }
    CONVERSATION {
        bigint id PK
        uuid public_id UK
        bigint user_id FK
        string guest_token
        string title
        string status "ACTIVE | ESCALATED | CLOSED"
        string last_intent
        int message_count
    }
    CHAT_MESSAGE {
        bigint id PK
        bigint conversation_id FK
        string role "USER | ASSISTANT | SYSTEM"
        text content
        string intent
        numeric confidence
        string model
        int latency_ms
        jsonb blocks "rich UI payload"
        jsonb tool_calls
        jsonb safety_flags
    }
    ASSISTANT_EVENT {
        bigint id PK
        bigint conversation_id FK
        string event_type
        string intent
        string tool_name
        string outcome
        numeric value_num
        jsonb metadata
    }
    MODERATION_LOG {
        bigint id PK
        bigint conversation_id FK
        string direction "INBOUND | OUTBOUND"
        string category
        string severity
        string action
        text excerpt
    }
```

Notes:

- **`product.attributes` is JSONB** — a flexible spec sheet is what makes cross-product comparison
  possible without a table per category.
- **`conversation.public_id` is a UUID** exposed to clients; the numeric primary key never leaves the
  server. This is what ownership checks key on.
- **`chat_message.blocks` stores the rich UI payload** as it was rendered, so a transcript replays
  identically after a reload.
- **`assistant_event` is append-only** and indexed on `(event_type, created_at)`,
  `(intent)`, `(tool_name)` and `(conversation_id)`, which is what makes the analytics queries cheap.
- **`product_interaction` is append-only and denormalised on purpose.** `actor_key` holds
  `user:<id>` or `guest:<token>` as text rather than a foreign key, because guest browsing has no user
  row to point at and anonymous visits are most of the traffic. `weight` is stored rather than derived
  so the aggregation queries stay plain sums, with the enum remaining the source of truth for the
  values. `recommendation_id` groups one rail's impressions with the clicks that followed, which is
  what makes click-through measurable per placement rather than per product.

## Recommendation engine

Rails are built from two signals that fail in opposite directions, which is why both are kept.

| | Collaborative | Content |
| --- | --- | --- |
| **Source** | The interaction log | Category, brand, price, spec tokens |
| **Strength** | Captures relationships nobody wrote down | Works for a product with no history at all |
| **Weakness** | Useless for a new product | Only knows what was already recorded about it |

A pair is scored by behaviour where behaviour exists and by attributes where it does not. The blend is
weighted by the *weaker* of the two products' confidence, where confidence is interaction count
against a saturation point. So a pair of well-understood products is described by what shoppers did,
and a pair involving a cold product falls back to what the products are. Without that, a new arrival
is either invisible or recommended on the basis of two accidental clicks.

```
        interaction log                    product table
   (views, carts, purchases)        (category, brand, price, specs)
              │                                  │
              ▼                                  ▼
     item-item cosine over              weighted attribute
     co-occurrence (per actor)          similarity per pair
              │                                  │
              └──────────────┬───────────────────┘
                             ▼
                  blended similarity
        (trust = min(confidence_a, confidence_b))
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
  neighbours()        personalised        purchase affinity
  similar / trending  for-you             also-bought / cart
                             │
                             ▼
                   greedy MMR diversity pass
              (focused λ for similar/bought rails,
               broad λ for for-you and trending)
                             │
                             ▼
                  products + reason per item
```

**Purchase affinity is computed separately** from general co-occurrence. "People who looked at this
also looked at that" is a browsing claim; "people who bought this also bought that" needs both items
to have left the shop together. It is expressed as the share of the less popular item's buyers who
took both, which makes the number symmetric and worth printing in the UI.

**Diversity is applied after ranking, not instead of it.** Greedy maximal marginal relevance subtracts
a penalty for similarity to what has already been selected. The weight differs by rail: a "similar to
this" rail must stay on topic, so its penalty is small, while a home-page rail benefits from breadth.

**Every recommendation carries a reason**, generated from whichever signal actually drove it. A rail
that cannot explain itself cannot be debugged, and a shopper cannot judge whether to trust it.

### The interaction log

One append-only table, `product_interaction`, with a `weight` per interaction type and an `actor_key`
of `user:<id>` or `guest:<token>`. Keeping guests in the same column means anonymous browsing still
contributes to co-occurrence, which matters because most first visits are anonymous.

Two rules govern how it is written:

- **The server records behaviour; the browser records attention.** Views, cart adds and purchases are
  written by the services that handle them, because the server is the only party that knows they
  happened. Impressions and clicks come from the browser, because only the browser knows what was on
  screen. `POST /recommendations/events` rejects behavioural types outright, so a crafted request
  cannot inflate the signal that drives everyone else's recommendations.
- **Telemetry never breaks a request.** Each write runs in its own `REQUIRES_NEW` transaction and
  failures are logged, not propagated. A recommendation system that can fail a checkout is worse than
  no recommendation system.

The model is rebuilt lazily and cached, with the cache keyed on a TTL. The full pairwise computation
is trivial at this catalogue size; a larger one would move the same computation into a scheduled job
writing a similarity table, without changing any of the call sites.

## Assistant internals

```mermaid
flowchart TB
    IN["Inbound message"] --> CMD{"Slash command?"}
    CMD -->|yes| CMDH["/help · /end · /resume"]
    CMD -->|no| ESC{"Conversation escalated?"}
    ESC -->|yes| HOLD["Hold: a human owns this"]
    ESC -->|no| RL{"Within rate limits?"}
    RL -->|no| THROTTLE["429-style notice + handoff offer"]
    RL -->|yes| MOD{"Inbound moderation"}
    MOD -->|block| REFUSE["Refusal + what it can do instead"]
    MOD -->|redact| SCRUB["Continue with card data removed"]
    MOD -->|allow| CLASS["Intent + entity extraction"]
    SCRUB --> CLASS
    CLASS --> AVAIL{"Model available?"}
    AVAIL -->|yes| LLM["ChatClient + 13 tools"]
    AVAIL -->|no| FB["FallbackResponder"]
    LLM --> OUTMOD{"Outbound moderation"}
    LLM -->|failure| FB
    FB --> OUTMOD
    OUTMOD --> PERSIST["Persist turn + blocks + tool calls"]
    PERSIST --> ANALYTICS["Analytics events"]
    ANALYTICS --> OUT["Response"]
```

### Tool surface

| Tool | Purpose | Writes |
| --- | --- | --- |
| `search_products` | Catalogue search with budget, category and availability filters | no |
| `get_product_details` | Full spec sheet by SKU or slug | no |
| `compare_products` | 2–4 products, spec rows with differences marked, pricing verdict | no |
| `find_best_value` | Ranks options against a stated budget and use case | no |
| `recommend_products` | Similar, bought-together, personalised or trending rails, each item with its reason | no |
| `list_categories` | Categories with counts and entry prices | no |
| `get_cart` | Current cart with totals and shipping progress | no |
| `add_to_cart` | Adds a product, clamping to stock | yes |
| `update_cart_quantity` | Sets an absolute quantity | yes |
| `remove_from_cart` | Removes a line | yes |
| `get_order_status` | Order lookup honouring ownership and guest email rules | no |
| `list_recent_orders` | Signed-in order history | no |
| `get_store_policy` | Shipping, returns, warranty, payment, support answers | no |
| `request_human_handoff` | Raises an escalation ticket with the transcript | yes |

Every tool reports back through a shared `AssistantToolContext`, which carries the caller's identity,
the conversation, a sink for rich blocks and a log of tool names. The model gets structured results;
the shopper gets rendered components — from the same call.

### Failure handling

| Failure | Behaviour |
| --- | --- |
| Model unreachable or erroring | Three consecutive failures open a circuit breaker for five minutes; the assistant answers from catalogue rules and the UI shows a "limited mode" banner. |
| Tool throws | The tool returns a structured `{ "error": …, "message": … }` describing what to tell the shopper; the model recovers in the same turn. |
| Network failure in the browser | The failed turn is marked in the transcript with the original text retained, and a one-tap **Retry** plus a **Get a human** escape hatch. |
| Rate limit hit | A specific message with the retry window, not a generic error. |
| Conversation owned by someone else | `403`, with no detail leaked about the other conversation. |
