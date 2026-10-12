package com.shopwise.ai;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.web.dto.AssistantOnboarding;
import com.shopwise.web.dto.AssistantOnboarding.IntentCard;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single source of truth for what the assistant can do. The same catalogue
 * drives the onboarding panel in the UI, the system prompt given to the model, and
 * the rule-based classifier used for analytics and for the fallback engine.
 */
@Component
public class IntentCatalog {

    public record IntentDefinition(
            String id,
            String label,
            String description,
            String icon,
            List<String> sampleUtterances,
            String exampleResponse,
            boolean requiresSignIn) {
    }

    private final List<IntentDefinition> intents = List.of(
            new IntentDefinition("product_search", "Find a product", "Search the catalogue by need, budget or brand.",
                    "🔎",
                    List.of("Find me a laptop under $1500", "I need noise cancelling headphones", "Show me smartwatches"),
                    "I found 3 laptops under $1,500. The Aurora Book Air 13 at $1,149 has the longest battery at 20 hours.",
                    false),
            new IntentDefinition("product_compare", "Compare products", "Put two to four products side by side on the specs that matter.",
                    "⚖️",
                    List.of("Compare the Pulse 12 Pro and the Vertex Ultra", "Which of these two has better battery?"),
                    "Here is the side-by-side. The Pulse 12 Pro wins on camera and support, the Vertex Ultra wins on battery and gaming headroom.",
                    false),
            new IntentDefinition("product_recommend", "Get a recommendation", "Ask for the best pick given a budget and a use case.",
                    "🎯",
                    List.of("What is the best camera for travel?", "Best laptop for a student with a $1,200 budget"),
                    "For travel I would take the SnapPro Action 4K at $399 — it is waterproof without a housing and weighs 154 g.",
                    false),
            new IntentDefinition("product_details", "Explain the specs", "Ask what a specification actually means for you.",
                    "📋",
                    List.of("Does the Aurora Book Pro have a 120Hz screen?", "How heavy is the TitanForge X16?"),
                    "Yes — the Aurora Book Pro 14 has a 120 Hz mini-LED panel covering 100% of DCI-P3.",
                    false),
            new IntentDefinition("stock_check", "Check availability", "Confirm stock, low-stock warnings and restock timing.",
                    "📦",
                    List.of("Is the Slate Lite 15 in stock?", "When is the Deepwave 700 back?"),
                    "The Slate Lite 15 is currently out of stock. I can add you to the restock alert list.",
                    false),
            new IntentDefinition("price_check", "Explain pricing", "Current price, discounts and what changed.",
                    "🏷️",
                    List.of("Is the Halo Buds Pro on offer?", "Why did the price change?"),
                    "The Halo Buds Pro is $199, down from $229 — a 13% saving that is still live today.",
                    false),
            new IntentDefinition("cart_action", "Change my cart", "Add, remove or adjust quantities, and read back the totals.",
                    "🛒",
                    List.of("Add the Aurora Book Air to my cart", "Remove the headphones"),
                    "Added the Aurora Book Air 13. Your cart is now $1,149.00 with free shipping.",
                    false),
            new IntentDefinition("shipping_info", "Shipping and delivery", "Costs, thresholds, timelines and destinations.",
                    "🚚",
                    List.of("How much is shipping?", "How long does delivery take to Singapore?"),
                    "Shipping is free over $150, otherwise a flat $12. Standard delivery is 3-5 working days.",
                    false),
            new IntentDefinition("order_status", "Track an order", "Look up an order by reference and explain the current stage.",
                    "📮",
                    List.of("Where is my order SW-241002-0002?", "Track my last order"),
                    "SW-241002-0002 is on the way and should arrive within 2 days. Delivered orders stay returnable for 30 days.",
                    true),
            new IntentDefinition("returns_policy", "Returns and warranty", "Return windows, condition rules and warranty cover.",
                    "↩️",
                    List.of("What is the return window?", "Is the warranty international?"),
                    "You have 30 days to return anything unopened, and manufacturer warranty runs 2 years on most items.",
                    false),
            new IntentDefinition("payment_help", "Payment and invoices", "Accepted methods, invoicing and refunds.",
                    "💳",
                    List.of("Which payment methods do you accept?", "Can I get an invoice?"),
                    "This demo checkout settles with a card. Invoices are issued automatically to the checkout email.",
                    false),
            new IntentDefinition("safety_question", "Safety and suitability", "Age suitability, materials, and product safety notes.",
                    "🛡️",
                    List.of("Is this safe for a 10-year-old?", "Any safety notes for the smart bulbs?"),
                    "The AuraGlow bulbs are low-voltage and cool to the touch, but installation should still be done by an adult.",
                    false),
            new IntentDefinition("human_handoff", "Talk to a person", "Escalate to a human agent with the transcript attached.",
                    "🙋",
                    List.of("I want to talk to a human", "This is not helping, get me an agent"),
                    "I have raised ticket SW-ESC-4821 and attached this conversation. An agent replies within 4 working hours.",
                    false),
            new IntentDefinition("general_help", "What can you do?", "Orientation, capabilities and honest limits.",
                    "💬",
                    List.of("What can you help with?", "How does this work?"),
                    "I can search the catalogue, compare up to four products, adjust your cart and look up orders.",
                    false));

    private final Map<String, IntentDefinition> byId = new LinkedHashMap<>();
    private final ShopwiseProperties properties;

    public IntentCatalog(ShopwiseProperties properties) {
        this.properties = properties;
        intents.forEach(intent -> byId.put(intent.id(), intent));
    }

    public List<IntentDefinition> all() {
        return intents;
    }

    public Optional<IntentDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public String labelFor(String id) {
        return find(id).map(IntentDefinition::label).orElse("General question");
    }

    /** Compact catalogue injected into the system prompt. */
    public String promptSummary() {
        StringBuilder builder = new StringBuilder();
        for (IntentDefinition intent : intents) {
            builder.append("- ").append(intent.id())
                    .append(" (").append(intent.label()).append("): ")
                    .append(intent.description());
            if (intent.requiresSignIn()) {
                builder.append(" [requires sign-in]");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public AssistantOnboarding onboarding(String greetingName, boolean degraded, String degradedReason) {
        List<IntentCard> cards = intents.stream()
                .map(intent -> new IntentCard(
                        intent.id(),
                        intent.label(),
                        intent.description(),
                        intent.icon(),
                        intent.sampleUtterances(),
                        intent.exampleResponse(),
                        intent.requiresSignIn()))
                .toList();

        List<String> starters = List.of(
                "Find me a laptop under $1500",
                "Compare the Pulse 12 Pro and the Vertex Ultra",
                "What is the best camera for travel?",
                "Is the Halo Buds Pro on offer?",
                "Where is my order SW-241002-0002?");

        return new AssistantOnboarding(
                "Hi " + greetingName + " — I am the Shopwise assistant.",
                "I can find, compare and explain products, adjust your cart, and check orders. Ask in your own words.",
                degraded
                        ? "Running in offline mode: answers come from the built-in catalogue rules."
                        : "Connected to the live catalogue and the " + properties.getAssistant().getModel() + " model.",
                degraded,
                degradedReason,
                cards,
                starters,
                List.of(
                        "Search the catalogue by need, budget or brand",
                        "Compare two to four products on the specs that matter",
                        "Explain what a specification means in practice",
                        "Add or remove items and read back cart totals",
                        "Track an order and explain the current stage",
                        "Hand the conversation to a human with full context"),
                List.of(
                        "I never invent stock, prices or delivery dates — I read them from the catalogue",
                        "Order details are only shown to the account or email that placed the order",
                        "I do not take payment details; checkout happens on the secure checkout page",
                        "If I cannot help, I will say so and offer a human agent"),
                "If I cannot help, ask for a human and I will attach this conversation to a support ticket.",
                properties.getCommerce().getSupportEmail());
    }
}