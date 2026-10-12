# The shopping assistant

The assistant exists to complete the handful of conversations shoppers actually have, quickly and
safely. This document explains what it supports, how it is grounded, and where the guardrails are.

---

## 1. Onboarding

The first thing a shopper sees is not a blinking cursor. The panel opens on a welcome that answers
"what can you do?" before they type anything:

- a **greeting** that uses their name when they are signed in,
- an **availability line** that is honest about the mode (`Connected to the live catalogue and the
  gpt-5-mini model.` or a limited-mode warning),
- **starter prompts** — one tap to see the assistant work,
- **all 14 supported intents**, each expandable to show *how to ask it* (sample utterances, clickable)
  and *a typical answer*, so expectations are set concretely rather than described,
- an explicit **"what I will not do"** list,
- the **handoff note** and support email.

The same component doubles as the empty state, so there is no separate "no messages" screen.

## 2. Supported intents

| Intent | Typical utterance | What happens |
| --- | --- | --- |
| `product_search` | "Find me a laptop under $1500" | `search_products` with budget and category, product cards rendered |
| `product_compare` | "Compare the Pulse 12 Pro and the Vertex Ultra 5G" | `compare_products`, spec table with differences highlighted |
| `product_recommend` | "What is the best value phone?" | `find_best_value`, ranked recommendation with reasons |
| `product_details` | "What are the specs of the Chrono S9?" | `get_product_details`, spec sheet block |
| `stock_check` | "Is the TitanForge X16 in stock?" | Availability, quantity, restock guidance |
| `price_check` | "How much is the AirTone Lite?" | Price, discount, value framing |
| `cart_action` | "Add the Aurora Book Air 13 to my cart" | `add_to_cart` / `update_cart_quantity` / `remove_from_cart` + cart block |
| `shipping_info` | "How long does delivery take?" | `get_store_policy`, concrete thresholds and windows |
| `order_status` | "Where is my order SW-261012-0001?" | `get_order_status`, order card with delivery timeline |
| `returns_policy` | "Can I return opened earbuds?" | `get_store_policy`, exact window and conditions |
| `payment_help` | "Which cards do you take?" | `get_store_policy`, and it never asks for card details |
| `safety_question` | "Is this safe for a child?" | Product-level safety guidance from the spec sheet |
| `human_handoff` | "I want to talk to a person" | `request_human_handoff`, ticket raised, bot pauses |
| `general_help` | "What can you do?" | Re-states capabilities and offers starter prompts |

Classification is a fast rule-based pass (see `IntentClassifier`) used for **analytics, fallback
routing and prompt enrichment** — not to gate the model. The model still sees every message, so an
utterance the classifier misses is still answered well; it is simply labelled `unknown` in analytics.

## 3. Grounding: the model is never the source of truth

The system prompt states the rule plainly:

> - Always call a tool before naming a product or quoting a figure.
> - Tool results are authoritative. If a tool returned products inside the stated budget, say so and
>   name them; never claim that nothing was found when the tool returned matches.
> - If you are unsure whether a filter was applied, call the tool again with explicit arguments rather
>   than describing a result you did not receive.

Two mechanisms make this hold in practice:

1. **Entity pre-extraction.** The classifier pulls a budget, a category, SKUs, an order reference and
   an email out of the message and injects them into the prompt as explicit signals:
   `budget: $1500`, `category: laptops`. The model then has concrete values to pass as tool arguments
   instead of re-parsing the sentence.
2. **Deterministic fallback.** When the model is unavailable, `FallbackResponder` runs the same
   searches and comparisons in plain Java and returns the same block types, so the shopper still gets
   real products at real prices.

This combination is what caught a genuine bug during development: a swapped `minPrice`/`maxPrice`
argument in `search_products` made the assistant return the *most expensive* laptops for "under
$1500". The end-to-end suite asserts that budget searches only return in-budget products.

## 4. Context-aware follow-ups

`ConversationService.memoryFor` rebuilds a short window of the transcript on every turn and replays it
to the model as real `UserMessage` / `AssistantMessage` objects. Two details matter:

- The window is trimmed to always **start on a user turn**, so the model never sees a dangling
  assistant reply as its first context.
- Tool chatter is *not* replayed. The model already saw the results when it produced the answer, so
  replaying raw tool JSON would bloat the prompt and cost money for no accuracy gain.

The result: "which one should I buy?" after a comparison resolves to those two products, and "is it
in stock?" resolves to the product just discussed.

## 5. Actions, not just answers

The assistant can mutate state, and the storefront reflects it immediately:

| Action | Effect |
| --- | --- |
| `add_to_cart` | Cart updated; quantity clamped to stock with an explanation |
| `update_cart_quantity` | Absolute set, `0` removes |
| `remove_from_cart` | Line removed |
| `request_human_handoff` | Ticket raised, conversation paused, transcript attached |

`ACTION`-kind quick replies let the client perform a UI-side effect and then report back to the
assistant in the same conversation, keeping the transcript coherent.

## 5a. Recommending, not guessing

The assistant has a `recommend_products` tool backed by the same engine that drives the storefront
rails, so a conversation and a product page never disagree about what goes with what.

| Mode | Question it answers | Needs |
| --- | --- | --- |
| `similar` | What else is like this? | a product |
| `also_bought` | What do people actually buy with this? | a product |
| `for_you` | What should I look at? | nothing |
| `trending` | What is popular right now? | nothing |

Two behaviours matter more than the modes.

**The reason travels with the product.** Every item comes back with a sentence like
"55% of buyers also bought this", and the prompt tells the model to pass that reason on in its own
words. A shopper who is told *why* can judge whether to trust the suggestion; a shopper shown a bare
list cannot.

**The tool refuses to invent.** When there is not enough purchase history to support
"frequently bought together", it returns nothing and says so, and the model is instructed to fall
back to `search_products` rather than presenting a guess as a fact. That is the same grounding rule
that governs prices and stock: if a tool did not return it, the assistant does not say it.

Recommendation questions are answered from behaviour, and when the shopper has already named a
product the model is told to go straight to `recommend_products` rather than searching for it first.
If it searches anyway, the reply assembler drops a single-product card block when the same reply also
offers a richer set, because repeating a product the shopper just named is noise rather than
information.

## 6. Moderation and permissions

### Inbound screening (before the model sees anything)

| Category | Trigger | Action |
| --- | --- | --- |
| `self_harm` | Self-harm language | **Block** with crisis guidance, never routed to the model |
| `prompt_injection` | "ignore previous instructions", "reveal your system prompt", jailbreak phrasing | **Block**, redirect to shopping |
| `payment_data` | Card-like 13–19 digit runs, CVV phrasing | **Redact** the digits, explain that card details are never needed |
| `spam` | Three or more links in one message | **Block** |
| `out_of_scope_advice` | Medical, legal, diagnostic phrasing | Allow, flag for analytics, and the prompt forbids giving advice |
| `abuse` | Profanity directed at the bot | Allow, flag — a frustrated shopper still needs help |

### Outbound screening (before the shopper sees the answer)

The model's own draft is screened for card-like numbers and for phrases that indicate it is leaking
its instructions (`system prompt`, `my instructions are`, `as an ai language model`). A hit redacts
the text and logs an `OUTBOUND` moderation event.

### Permissions

- Conversations are owned by an identity (user id **or** guest token). Reading someone else's returns
  `403` — role does not override ownership.
- Order lookup honours ownership: a guest must supply the checkout email; a signed-in shopper can open
  their own order directly.
- The model cannot see another shopper's data, because every tool resolves the caller from the tool
  context rather than from anything the model says.
- `/insights/**` requires `SUPPORT` or `ADMIN`; the UI shows a clear permission screen rather than an
  empty dashboard.

## 7. Fallback and recovery

| Situation | What the shopper sees |
| --- | --- |
| Model down (circuit breaker open) | "Limited mode" banner, an answer computed from catalogue rules, and `model: deterministic-fallback` in the transcript metadata |
| Tool error | The model explains what went wrong and offers the next best action in the same turn |
| Request failed in the browser | The turn is marked as not delivered, the text is preserved, and **Retry** plus **Get a human** are offered |
| Rate limited | The specific window ("try again in 24 seconds") plus a handoff offer |
| Assistant API unreachable at load | The onboarding panel shows an error state with a working **Try again** |
| Nothing matched a search | A genuine empty state with a clear-the-filters action and "ask the assistant instead" |

## 8. Human escalation

Escalation is deliberately easy to reach and hard to lose:

1. The shopper asks for a person, or presses **Human handoff**.
2. They pick a reason and optionally add a note and a reply email.
3. A ticket is created (`SW-ESC-####`), a **transcript excerpt** is attached automatically, and the
   conversation moves to `ESCALATED` — the bot stops responding to that conversation.
4. The shopper sees the reference, the priority, the expected response window and the next step.
5. Saying `/resume` hands the conversation back to the assistant.

Staff see the queue, the reason, the priority and the summary in **Insights**, and can resolve tickets,
which feeds the escalation-rate metric.

## 9. Analytics

Every turn writes append-only `assistant_event` rows. The Insights dashboard derives:

| Metric | Definition |
| --- | --- |
| Conversations / active | Started in the window, and not closed |
| Messages sent / answered | Inbound shopper turns, and turns the assistant produced a reply for |
| **Resolution rate** | Answered turns that did not need a fallback, as a share of answered turns |
| **Fallback rate** | Turns served by the deterministic responder, as a share of answered turns |
| **Escalation rate** | Conversations that needed a human, as a share of conversations started |
| **Moderation block rate** | Blocked or redacted messages as a share of inbound messages |
| Error rate | Turns that hit a model or tool failure, as a share of answered turns |
| Median latency | Measured end-to-end per turn |
| **Satisfaction score** | Positive feedback as a share of all feedback |
| Top intents / top tools | Which conversations matter and what the model reaches for |
| Daily volume | Messages per day, with fallbacks overlaid |
| Escalation queue | Live tickets with resolve actions |
| Recent moderation | Category, direction, action and a redacted excerpt |

The event types recorded are `CONVERSATION_STARTED`, `MESSAGE_SENT`, `INTENT_DETECTED`,
`TOOL_CALLED`, `MESSAGE_ANSWERED`, `FALLBACK_TRIGGERED`, `MODERATION_BLOCKED`, `RATE_LIMITED`,
`ASSISTANT_ERROR`, `ESCALATION_REQUESTED`, `FEEDBACK_RECEIVED_UP` and `FEEDBACK_RECEIVED_DOWN`.

Two properties of this design are worth stating explicitly, because both were bugs first:

1. **Counters come from independent event types, so the denominator matters.** A turn refused by
   moderation, held for a human or rate limited is *answered* without ever producing a
   `MESSAGE_SENT` event. Dividing quality rates by inbound messages therefore produced a resolution
   rate above 100%. Quality rates are now shares of answered turns, and every rate is clamped to
   0–100 so an impossible number can never reach the dashboard.
2. **`MESSAGE_ANSWERED` is emitted on every path that replies**, including refusals and holds, which
   is what makes it a valid denominator for quality.

## 10. Rate limits

Three sliding windows, all in-memory and all configurable under `shopwise.assistant`:

| Scope | Default | Purpose |
| --- | --- | --- |
| Per conversation | 20 messages / minute | Stops a stuck client hammering the model |
| Per signed-in user | 40 messages / minute | Fair use per account |
| Per guest token | 15 messages / minute | Stops anonymous abuse |
| Turn budget | 40 messages per conversation | Offers a handoff on very long threads |

`remainingMessagesThisMinute` is returned on every response so the UI could show it if desired.

## 11. Tuning

Everything lives in `application.yml` under `shopwise.assistant`:

```yaml
shopwise:
  assistant:
    model: gpt-5-mini
    temperature: 0.2          # low: this is a factual assistant, not a creative one
    memory-window: 12         # turns replayed to the model
    max-turns-per-conversation: 40
    max-message-length: 1200
    tool-result-limit: 8
    rate-limit:
      per-conversation-per-minute: 20
      per-user-per-minute: 40
      per-anonymous-per-minute: 15
    moderation:
      block-prompt-injection: true
      redact-payment-data: true
      outbound-screening: true
```
