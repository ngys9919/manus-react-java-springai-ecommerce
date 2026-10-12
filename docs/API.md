# Shopwise REST API

Base path: `/api/v1` · Media type: `application/json` · Interactive docs: `/swagger-ui.html` ·
OpenAPI document: `/v3/api-docs`

---

## Conventions

### Identity

Two headers drive everything.

| Header | Purpose |
| --- | --- |
| `X-Guest-Token` | Stable anonymous identity. Send any opaque string (the storefront generates a UUID) and the cart and conversations follow it across reloads. Optional but recommended. |
| `Authorization: Bearer <jwt>` | Signed-in identity. When present, it takes precedence and the guest cart is merged into the account on sign-in. |

A conversation belongs to exactly one identity. Reading someone else's conversation returns `403`,
even for staff — ownership is checked separately from role.

### Errors

Every failure returns **RFC 9457 `application/problem+json`**:

```json
{
  "type": "https://shopwise.dev/problems/not-found",
  "title": "Not found",
  "status": 404,
  "detail": "Product 'NOPE-404' does not exist.",
  "instance": "/api/v1/catalog/products/NOPE-404",
  "code": "NOT_FOUND",
  "path": "/api/v1/catalog/products/NOPE-404"
}
```

`code` is the stable machine-readable value; `detail` is safe to show to a shopper. Validation
failures add an `errors` map of `field -> message`.

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Bean validation rejected the body |
| 400 | `BAD_REQUEST` | Malformed or contradictory request |
| 401 | `UNAUTHENTICATED` | Missing or invalid bearer token |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| 403 | `FORBIDDEN` | Authenticated but not allowed (wrong role, wrong owner) |
| 404 | `NOT_FOUND` | Unknown product, order, conversation or escalation |
| 409 | `CONFLICT` | Stock or state conflict (e.g. item not purchasable) |
| 409 | `EMAIL_IN_USE` | Registration with an existing email |
| 429 | `RATE_LIMITED` | Assistant turn budget exceeded; see `retryAfterSeconds` |
| 503 | `ASSISTANT_UNAVAILABLE` | Model unreachable **and** the deterministic fallback declined |
| 500 | `INTERNAL_ERROR` | Unexpected server fault; safe to retry |

---

## Catalogue

### `GET /catalog/products`

Search with filters, sorting and facets.

| Query | Type | Default | Notes |
| --- | --- | --- | --- |
| `q` | string | — | Free text over name, brand, description, tags and category name |
| `category` | string | — | Category slug, or `all` |
| `minPrice` / `maxPrice` | decimal | — | Inclusive bounds |
| `inStock` | boolean | `false` | Only purchasable products |
| `sort` | enum | `relevance` | `relevance`, `price-asc`, `price-desc`, `rating`, `newest`, `name` |
| `page` | int | `0` | Zero-based |
| `size` | int | `12` | Clamped to 1–48 |

```json
{
  "items": [
    {
      "id": 2, "sku": "SKU-1002", "slug": "aurora-book-air-13",
      "name": "Aurora Book Air 13", "brand": "Nimbus",
      "categorySlug": "laptops", "categoryName": "Laptops",
      "price": 1149.00, "compareAtPrice": null, "discountPercent": null,
      "currency": "USD", "stockQuantity": 32, "inStock": true, "lowStock": false,
      "rating": 4.5, "reviewCount": 412,
      "imageUrl": "/media/products/SKU-1002.svg", "accentColor": "#4f46e5",
      "shortDescription": "Ultra-light 13\" laptop that stays cool and silent.",
      "tags": ["laptop", "ultrabook", "portable"]
    }
  ],
  "page": 0, "size": 12, "totalElements": 24, "totalPages": 2,
  "hasNext": true, "hasPrevious": false, "sort": "relevance", "appliedQuery": "laptop",
  "facets": {
    "categories": [{ "slug": "laptops", "name": "Laptops", "icon": "💻", "count": 4 }],
    "minPrice": 49.00, "maxPrice": 2299.00
  }
}
```

### `GET /catalog/products/{slugOrSku}`

Accepts either the slug or the SKU. Adds `description`, `highlights[]` and a full
`attributes` map (the spec sheet the assistant compares against). `404` when unknown.

### `GET /catalog/categories`

Returns `[{ id, slug, name, description, icon, productCount, priceFrom }]`.

---

## Cart

Guests get a real, persisted cart. All endpoints return the **whole cart** so the client never has to
guess at server-side rules.

### `GET /cart`

```json
{
  "id": "9f0e…", "guestToken": "guest-…",
  "items": [
    { "productId": 2, "sku": "SKU-1002", "slug": "aurora-book-air-13",
      "name": "Aurora Book Air 13", "brand": "Nimbus",
      "imageUrl": "/media/products/SKU-1002.svg", "accentColor": "#4f46e5",
      "unitPrice": 1149.00, "quantity": 1, "lineTotal": 1149.00,
      "stockQuantity": 32, "inStock": true, "quantityAdjusted": false }
  ],
  "itemCount": 1, "distinctItemCount": 1,
  "subtotal": 1149.00, "shippingFee": 0.00, "taxTotal": 103.41, "grandTotal": 1252.41,
  "freeShippingThreshold": 99.00, "freeShippingRemaining": 0.00,
  "currency": "USD", "empty": false, "warnings": []
}
```

### `POST /cart/items`

Body: `{ "productId": 2, "quantity": 1 }` **or** `{ "sku": "SKU-1002", "quantity": 1 }`.

Server-side rules: quantity is clamped to available stock (`quantityAdjusted: true` and a warning
when clamped); out-of-stock products return `409 CONFLICT`; unknown products return `404`.

### `PATCH /cart/items/{productId}` · `DELETE /cart/items/{productId}` · `DELETE /cart`

Set an absolute quantity (`0` removes the line), remove one line, or empty the cart.

---

## Orders

### `POST /orders`

```json
{
  "fullName": "Ada Lovelace", "email": "ada@example.com",
  "line1": "1 Analytical Way", "line2": "Unit 4",
  "city": "Singapore", "postcode": "018989", "country": "Singapore",
  "note": "Leave at the door"
}
```

Creates the order from the current cart in one transaction: re-prices every line against the live
catalogue, decrements stock, writes the order and clears the cart. Returns the full order (see below).
`409` if an item became unavailable mid-checkout; `400` on validation failure.

### `GET /orders/mine`

Signed-in only (`403` for guests). Most recent first.

### `GET /orders/{reference}/track?email=…`

- Signed-in owner: works without `email`.
- Guest: `email` must match the checkout email, otherwise `403`.
- Unknown reference: `404`.

```json
{
  "reference": "SW-261012-0001", "status": "SHIPPED", "statusLabel": "Shipped",
  "placedAt": "2026-10-12T04:10:00Z",
  "subtotal": 1149.00, "shippingFee": 0.00, "taxTotal": 103.41, "grandTotal": 1252.41,
  "currency": "USD", "paymentMethod": "Demo card (no charge)",
  "shippingAddress": { "fullName": "Ada Lovelace", "line1": "1 Analytical Way",
    "line2": "Unit 4", "city": "Singapore", "postcode": "018989", "country": "Singapore" },
  "items": [{ "productId": 2, "sku": "SKU-1002", "productName": "Aurora Book Air 13",
    "imageUrl": "/media/products/SKU-1002.svg", "slug": "aurora-book-air-13",
    "unitPrice": 1149.00, "quantity": 1, "lineTotal": 1149.00 }],
  "itemCount": 1, "estimatedDelivery": "Arriving Tue 15 Oct",
  "trackingNote": "Handed to the carrier."
}
```

---

## Authentication

### `POST /auth/register`

Body: `{ "fullName", "email", "password" }`. Password minimum 8 characters. `409 EMAIL_IN_USE` when
taken. Any guest cart on the same `X-Guest-Token` is merged into the new account.

### `POST /auth/login`

Body: `{ "email", "password" }`. `401 INVALID_CREDENTIALS` on failure. Merges the guest cart.

Both return:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresInSeconds": 43200,
  "user": { "id": 1, "email": "ada@example.com", "fullName": "Ada Lovelace",
            "role": "CUSTOMER", "staff": false }
}
```

### `GET /me`

Current identity. `403` for guests.

---

## Assistant

### `GET /assistant/onboarding`

The welcome payload. Drives the onboarding screen and the empty state.

```json
{
  "greeting": "Hi Ada — I am the Shopwise assistant.",
  "subGreeting": "I search the live catalogue, compare products side by side, and can act on your cart.",
  "availability": "Connected to the live catalogue and the gpt-5-mini model.",
  "degraded": false,
  "degradedReason": null,
  "intents": [
    { "id": "product_compare", "label": "Compare products", "description": "…",
      "icon": "⚖️",
      "sampleUtterances": ["Compare the Pulse 12 Pro and the Vertex Ultra 5G", "Which of these two is better?"],
      "exampleResponse": "Side by side: the Pulse 12 Pro wins on imaging…",
      "requiresSignIn": false }
  ],
  "starterPrompts": ["Find me a laptop under $1500", "Compare the Pulse 12 Pro and the Vertex Ultra 5G"],
  "capabilities": ["Search the catalogue with budget and category filters", "…"],
  "limitations": ["I do not give medical, legal or financial advice", "…"],
  "handoffNote": "Ask for a human at any point and I will raise a ticket with this conversation attached.",
  "supportEmail": "support@shopwise.dev"
}
```

### `GET /assistant/status`

```json
{ "mode": "MODEL", "model": "gpt-5-mini", "available": true, "reason": "" }
```

`mode` flips to `FALLBACK` when the model is unreachable, and `reason` explains why. The UI surfaces
this rather than hiding it.

### `POST /assistant/chat`

The main loop.

```json
{
  "message": "Find me a laptop under $1500",
  "conversationId": "0b0c…",
  "pageContext": "shop"
}
```

| Field | Notes |
| --- | --- |
| `message` | Required, 1–1200 characters. Leading `/` is treated as a command (`/help`, `/end`, `/resume`). |
| `conversationId` | Omit to start a new conversation. |
| `pageContext` | `home`, `shop`, `product`, `cart`, `checkout`, `orders` — lets the assistant resolve "this one". |

Response:

```json
{
  "conversationId": "0b0c…",
  "title": "Find me a laptop under $1500",
  "status": "ACTIVE",
  "reply": {
    "id": 41, "role": "ASSISTANT",
    "content": "I found one in-stock laptop under your $1500 budget: Aurora Book Air 13…",
    "intent": "product_search", "confidence": 0.75,
    "createdAt": "2026-10-12T04:12:33Z",
    "blocks": [
      { "type": "PRODUCT_CARDS", "title": "Matching products", "subtitle": "1 result",
        "data": { "products": [ /* ProductSummary */ ] },
        "actions": [ { "label": "Open the cheapest", "value": "/product/aurora-book-air-13", "kind": "NAVIGATE" } ] }
    ],
    "toolCalls": [ { "name": "search_products", "label": "Searched the catalogue", "status": "ok" } ],
    "model": "gpt-5-mini", "latencyMs": 3120
  },
  "quickReplies": [ { "label": "Which one should I buy?", "value": "Which of these should I buy and why?", "kind": "SEND" } ],
  "intent": "product_search",
  "confidence": 0.75,
  "degraded": false,
  "degradedReason": null,
  "model": "gpt-5-mini",
  "latencyMs": 3120,
  "remainingMessagesThisMinute": 17,
  "escalated": false
}
```

**Block types** the client renders:

| `type` | `data` payload |
| --- | --- |
| `TEXT` | inline in `body` |
| `PRODUCT_CARDS` | `products: ProductSummary[]` |
| `COMPARISON` | `products: ProductDetail[]`, `rows: [{ attribute, differs, <SKU>: value }]`, `pricing: [{ sku, name, price, rating, inStock, cheapest?, bestRated? }]`, `differentiatingAttributes: string[]` |
| `CART_SUMMARY` | `cart: CartView` |
| `ORDER_CARD` | `order: OrderView` or `orders: OrderView[]` |
| `ESCALATION` | `ticket: EscalationView` |
| `NOTICE` | `title`, `subtitle` (severity tone), `body` |
| `QUICK_REPLIES` | `actions: QuickReply[]` |

**Quick reply kinds:** `SEND` (posts `value` as the next message), `NAVIGATE` (router push to
`value`), `ACTION` (client-side effect, then reports back).

`degraded: true` means the model was unavailable and the answer came from the deterministic
catalogue rules; `model` is then `deterministic-fallback`.

### `GET /assistant/conversations` · `GET /assistant/conversations/{id}`

Recent conversations for the current identity, and the full transcript with blocks and tool calls.
`403` if the conversation belongs to another identity.

### `POST /assistant/conversations/{id}/escalate`

```json
{ "reason": "order_issue", "note": "Arrived damaged", "email": "ada@example.com", "urgent": true }
```

`reason` ∈ `order_issue`, `payment`, `product_question`, `other`. Marks the conversation `ESCALATED`
(the bot pauses until `/resume`), attaches a transcript excerpt, and returns:

```json
{
  "reference": "SW-ESC-0007", "reason": "order_issue", "reasonLabel": "An order problem",
  "priority": "URGENT", "status": "OPEN",
  "summary": "Order problem — shopper reports: Arrived damaged (13 messages, last intent order_status)",
  "transcriptExcerpt": "Shopper: my parcel arrived damaged\nAssistant: …",
  "contactEmail": "ada@example.com", "createdAt": "2026-10-12T04:20:00Z",
  "expectedResponse": "A colleague replies within 2 working hours.",
  "nextStep": "Watch your inbox for a reply quoting SW-ESC-0007."
}
```

`summary` is a single-line headline (capped at 200 characters) for the staff queue. The
conversation itself travels in `transcriptExcerpt` and is shown on demand, so an agent never has to
ask the shopper to repeat themselves and the queue stays scannable.


### `POST /assistant/conversations/{id}/feedback`

```json
{ "rating": "up", "comment": "helpful", "messageId": 41 }
```

`rating` accepts `up` / `down` (also `good` / `bad`). Feeds the satisfaction metric.

---

## Recommendations

Rails of products derived from real shopper behaviour. Every rail is safe to call anonymously: guests
get a personalised answer from their own guest token, and a shopper with no history gets a labelled
popularity fallback rather than an empty list or an error.

Every rail returns the same envelope:

```json
{
  "strategy": "also_bought",
  "title": "Frequently bought together",
  "subtitle": "Pairs well with Aurora Book Pro 14",
  "anchor": "Aurora Book Pro 14",
  "recommendationId": "5f0c1c62-6f4b-4f0a-9d1e-2c2b6f0c9a11",
  "fallback": false,
  "note": null,
  "items": [
    {
      "product": { "sku": "SKU-3004", "name": "AirTone Lite", "price": 99.0, "...": "…" },
      "score": 0.5481,
      "reason": "55% of buyers also bought this",
      "strategy": "also_bought"
    }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `reason` | Why this product is here, written to be shown to the shopper. Never empty. |
| `strategy` | Which rule produced the rail. Useful for attributing performance per placement. |
| `recommendationId` | Pass this back with impressions and clicks so the rail's performance can be measured. |
| `fallback` | `true` when the rail could not use its intended signal and answered with a weaker, honest one. |
| `note` | Plain-language explanation of a fallback. `null` when there is none. |

`items` may be empty. A rail with nothing to say returns `200` with an empty list and a `note`, not an
error: a storefront slot that fails loudly is worse than one that quietly does not appear.

### `GET /recommendations/for-you?limit=8`

Personalised to the caller's own browsing, using a two-week half-life so recent interest outweighs
older interest. Products the shopper already bought are excluded; products they have already viewed
are down-weighted rather than hidden. Falls back to trending when there is no history.

### `GET /recommendations/trending?limit=8`

Time-decayed popularity. The cold-start answer, and the fallback for `for-you`.

### `GET /recommendations/cart?limit=4`

What goes with what is already in the basket. Excludes the basket's own contents. Prefers proven
co-purchase, and falls back to same-category similarity when nothing is proven yet.

### `GET /recommendations/products/{slugOrSku}/similar?limit=4`

Comparable alternatives. Same-category products are preferred; a different-category product is
discounted but not banned, so it can still surface when the behavioural evidence is much stronger.

### `GET /recommendations/products/{slugOrSku}/also-viewed?limit=4`

Browsing co-occurrence. Falls back to `similar` when too few shoppers have browsed the pair together.

### `GET /recommendations/products/{slugOrSku}/also-bought?limit=4`

Purchase co-occurrence, reported as the share of buyers who took both. Returns an empty rail until
there is real evidence, because "frequently bought together" is a claim that should not be invented.
The `reason` field carries the percentage.

### `POST /recommendations/events`

Records what the browser actually showed and clicked.

```json
{
  "events": [
    { "type": "RECO_IMPRESSION", "productId": 1, "recommendationId": "5f0c…", "surface": "home_for_you" },
    { "type": "RECO_CLICK",      "productId": 1, "recommendationId": "5f0c…", "surface": "home_for_you" }
  ]
}
```

Returns `{"recorded": 2, "rejected": 0}`.

**Only measurement events are accepted.** `PURCHASE`, `CART_ADD`, `VIEW` and `WISHLIST` are written
server-side by the services that handle them, so a crafted request cannot inflate the behavioural
signal that drives everyone else's recommendations. A rejected event is counted, not an error.
Unknown product ids and unrecognised types are also rejected.

### `GET /recommendations/status` · `GET /recommendations/performance?days=14`

Staff only (`SUPPORT` or `ADMIN`). Model size and freshness, and click-through per placement surface.

## Insights (staff)

Requires `SUPPORT` or `ADMIN`; a `CUSTOMER` token gets `403`.

### `GET /insights/assistant/analytics?days=14`

```json
{
  "windowStart": "2026-09-28T00:00:00Z", "windowEnd": "2026-10-12T05:00:00Z", "windowDays": 14,
  "conversationsStarted": 12, "activeConversations": 3,
  "messagesSent": 84, "messagesAnswered": 84, "toolInvocations": 96,
  "fallbacks": 4, "moderationBlocks": 3, "escalations": 2, "escalationsOpen": 1, "errors": 1,
  "positiveFeedback": 5, "negativeFeedback": 1,
  "resolutionRate": 95.2, "fallbackRate": 4.8, "escalationRate": 2.4,
  "moderationBlockRate": 3.6, "errorRate": 1.2, "averageLatencyMs": 3380.0,
  "satisfactionScore": 83.3,
  "topIntents": [{ "key": "product_search", "label": "Product search", "count": 31 }],
  "topTools": [{ "key": "search_products", "label": "Searched the catalogue", "count": 28 }],
  "moderationCategories": [{ "key": "prompt_injection", "label": "prompt_injection", "count": 2 }],
  "dailyMessages": [{ "day": "2026-10-12", "count": 42 }],
  "dailyFallbacks": [{ "day": "2026-10-12", "count": 2 }],
  "recentEscalations": [{ "reference": "SW-ESC-0007", "reason": "order_issue", "priority": "URGENT",
    "status": "OPEN", "summary": "…", "createdAt": "2026-10-12T04:20:00Z" }],
  "recentModeration": [{ "direction": "INBOUND", "category": "prompt_injection", "severity": "HIGH",
    "action": "BLOCK", "excerpt": "ignore all previous instructions…", "createdAt": "2026-10-12T04:22:00Z" }]
}
```

**Rate definitions.** Counters come from independent event types, so the denominators are chosen
per metric and every rate is clamped to 0–100:

| Field | Numerator / denominator |
| --- | --- |
| `resolutionRate` | Answered turns without a fallback / answered turns |
| `fallbackRate` | Fallbacks / answered turns |
| `escalationRate` | Escalations / conversations started |
| `moderationBlockRate` | Blocked or redacted messages / inbound messages |
| `errorRate` | Model or tool errors / answered turns |
| `satisfactionScore` | Positive feedback / all feedback |

`messagesAnswered` reports the answered total, which includes fallbacks and turns that were refused,
held for a human or rate limited — those are answers too, and they are not counted in
`messagesSent`.

### `GET /insights/assistant/escalations?limit=20`

The escalation queue, newest first.

### `POST /insights/assistant/escalations/{reference}/resolve`

Marks a ticket `RESOLVED` and stamps `resolvedAt`.

---

## Operational

### `GET /actuator/health`

Database and disk probes. Used by the smoke test as a readiness gate.
