package com.shopwise.ai;

import com.shopwise.ai.IntentClassifier.Classification;
import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Conversation;
import com.shopwise.exception.ApiException;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.CartService;
import com.shopwise.service.CatalogService;
import com.shopwise.service.EscalationService;
import com.shopwise.service.OrderService;
import com.shopwise.web.dto.CartView;
import com.shopwise.web.dto.ChatBlock;
import com.shopwise.web.dto.OrderView;
import com.shopwise.web.dto.ProductDetail;
import com.shopwise.web.dto.ProductSummary;
import com.shopwise.web.dto.QuickReply;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The assistant's offline brain.
 *
 * <p>Used when the model is unavailable, when a rate limit trips, or when the
 * classifier is confident enough that a model call would add latency without adding
 * value. It answers from the same database and the same policy configuration as the
 * tool-calling path, so a shopper never sees a dead end — the conversation degrades in
 * richness, not in usefulness.</p>
 */
@Component
public class FallbackResponder {

    public record Answer(
            String text,
            List<ChatBlock> blocks,
            List<QuickReply> quickReplies,
            String intent,
            boolean escalated,
            String reason) {
    }

    /**
     * Words that carry no catalogue meaning in a shopping question. Every remaining word has to
     * match a product, so passing the shopper's sentence straight through would match nothing.
     */
    private static final Set<String> FILLER = Set.of(
            "i", "we", "my", "me", "a", "an", "the", "for", "to", "of", "in", "on", "at", "is", "are",
            "am", "be", "can", "could", "would", "should", "want", "need", "needs", "looking", "look",
            "get", "got", "buy", "buying", "have", "has", "had", "do", "does", "did", "what", "which",
            "that", "this", "these", "those", "with", "and", "or", "but", "under", "over", "around",
            "about", "please", "help", "some", "any", "it", "you", "your", "good", "best", "better",
            "recommend", "recommendation", "suggest", "show", "find", "something", "anything", "home",
            "new", "really", "just", "also", "from", "than", "then", "there", "here", "how", "why");

    private final CatalogService catalogService;
    private final CartService cartService;
    private final OrderService orderService;
    private final EscalationService escalationService;
    private final IntentCatalog intentCatalog;
    private final ShopwiseProperties properties;

    public FallbackResponder(CatalogService catalogService,
                             CartService cartService,
                             OrderService orderService,
                             EscalationService escalationService,
                             IntentCatalog intentCatalog,
                             ShopwiseProperties properties) {
        this.catalogService = catalogService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.escalationService = escalationService;
        this.intentCatalog = intentCatalog;
        this.properties = properties;
    }

    public Answer respond(ActorContext actor,
                          Conversation conversation,
                          String message,
                          Classification classification,
                          String pageContext,
                          String reason) {

        List<ChatBlock> blocks = new ArrayList<>();
        List<QuickReply> replies = new ArrayList<>();

        try {
            return switch (classification.intent()) {
                case "product_search" -> search(actor, message, classification, blocks, replies, reason);
                case "product_recommend" -> recommend(actor, message, classification, blocks, replies, reason);
                case "product_compare" -> compare(actor, message, classification, blocks, replies, reason);
                case "product_details", "stock_check", "price_check" ->
                        explainProduct(message, classification, blocks, replies, reason);
                case "cart_action" -> cart(actor, message, classification, blocks, replies, reason);
                case "order_status" -> order(actor, classification, blocks, replies, reason);
                case "shipping_info", "returns_policy", "payment_help", "safety_question" ->
                        policy(classification, blocks, replies, reason);
                case "human_handoff" -> escalate(actor, conversation, message, classification, blocks, reason);
                case "general_help" -> capabilities(blocks, replies, reason);
                default -> unknown(message, blocks, replies, reason);
            };
        } catch (ApiException ex) {
            return new Answer(ex.getMessage(), List.of(ChatBlock.notice("Could not complete that", ex.getMessage(), "warning")),
                    List.of(QuickReply.send("Talk to a human", "I want to talk to a human")), classification.intent(),
                    false, reason);
        }
    }

    // ------------------------------------------------------------------

    private Answer search(ActorContext actor, String message, Classification classification,
                          List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        List<ProductSummary> products = productsFor(message, classification);

        if (products.isEmpty()) {
            return unknown(message, blocks, replies, reason);
        }
        blocks.add(productCards("Matching products", products, replies));
        return new Answer(summarise("I found these in the catalogue:", products), blocks, replies,
                "product_search", false, reason);
    }

    /**
     * Products for a question, trying the distinctive words first and then the filters alone.
     *
     * <p>The second attempt is what makes this work for a request like "what laptop for 4K video
     * editing under $2,000": no product mentions editing, but the category and the budget are
     * exactly the right filters, and answering with the laptops that fit is far better than
     * reporting that nothing matched.</p>
     *
     * <p>Both the search and the recommendation paths go through here. They did not, and the
     * omission was invisible until a question reached the recommendation path: passing the whole
     * sentence as a query matches nothing, so "what is the best camera for travel?" - one of the
     * starters the assistant itself suggests - answered with "I could not match that to something
     * I can look up".</p>
     */
    private List<ProductSummary> productsFor(String message, Classification classification) {
        String category = classification.entityString("category");
        BigDecimal budget = classification.entityDecimal("budget");

        String terms = searchTerms(message);
        List<ProductSummary> products = catalogService.searchForAssistant(
                terms, category, null, budget, true, 3);
        if (products.isEmpty() && terms != null) {
            products = catalogService.searchForAssistant(null, category, null, budget, true, 3);
        }
        return products;
    }

    /**
     * The most distinctive words in the question, longest-lived first, or {@code null} when the
     * question is entirely filler.
     */
    private String searchTerms(String message) {
        List<String> terms = new ArrayList<>();
        for (String word : message.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ").split("\\s+")) {
            if (word.length() >= 3 && !FILLER.contains(word)) {
                terms.add(word);
            }
            if (terms.size() == 3) {
                break;
            }
        }
        return terms.isEmpty() ? null : String.join(" ", terms);
    }

    private Answer recommend(ActorContext actor, String message, Classification classification,
                             List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        List<ProductSummary> products = productsFor(message, classification);

        if (products.isEmpty()) {
            return unknown(message, blocks, replies, reason);
        }
        ProductSummary pick = products.getFirst();
        blocks.add(productCards("Recommended", products, replies));
        String text = "Best fit right now is the " + pick.name() + " at $" + pick.price().toPlainString()
                + " (" + pick.rating().toPlainString() + "/5 from " + pick.reviewCount() + " reviews). "
                + (products.size() > 1 ? "Alternatives are listed below." : "");
        return new Answer(text, blocks, replies, "product_recommend", false, reason);
    }

    private Answer compare(ActorContext actor, String message, Classification classification,
                           List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        List<String> skus = skuList(classification);
        if (skus.size() < 2) {
            // Try to resolve the two products named in the message.
            List<ProductSummary> found = catalogService.searchForAssistant(message, null, null, null, null, 2);
            if (found.size() >= 2) {
                skus = found.stream().map(ProductSummary::sku).toList();
            }
        }
        if (skus.size() < 2) {
            blocks.add(ChatBlock.notice("Which two?",
                    "Name the two products and I will put them side by side.", "info"));
            replies.add(QuickReply.send("Compare the Pulse 12 Pro and the Vertex Ultra",
                    "Compare the Pulse 12 Pro and the Vertex Ultra"));
            return new Answer("Tell me which two products to compare and I will lay out the differences.",
                    blocks, replies, "product_compare", false, reason);
        }

        List<ProductDetail> details = catalogService.detailsForComparison(skus.stream().limit(4).toList());
        if (details.size() < 2) {
            return unknown(message, blocks, replies, reason);
        }
        blocks.add(comparisonBlock(details));
        String text = "Here is the side-by-side. "
                + details.getFirst().name() + " is $" + details.getFirst().price().toPlainString()
                + " and " + details.get(1).name() + " is $" + details.get(1).price().toPlainString() + ".";
        return new Answer(text, blocks, replies, "product_compare", false, reason);
    }

    private Answer explainProduct(String message, Classification classification,
                                  List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        List<String> skus = skuList(classification);
        ProductDetail detail = null;
        if (!skus.isEmpty()) {
            detail = catalogService.detailsForComparison(skus).stream().findFirst().orElse(null);
        }
        if (detail == null) {
            List<ProductSummary> found = catalogService.searchForAssistant(message, null, null, null, null, 1);
            if (!found.isEmpty()) {
                detail = catalogService.detail(found.getFirst().sku());
            }
        }
        if (detail == null) {
            return unknown(message, blocks, replies, reason);
        }

        blocks.add(ChatBlock.of(ChatBlock.TYPE_PRODUCT_CARDS, detail.name(), "Specifications", null,
                Map.of("products", List.of(detail), "expanded", true),
                List.of(QuickReply.navigate("Open product page", "/product/" + detail.slug()))));

        String stock = detail.inStock()
                ? "In stock — " + detail.stockQuantity() + " units."
                : "Currently out of stock.";
        String text = detail.name() + ": " + detail.shortDescription() + " " + stock
                + " Price $" + detail.price().toPlainString() + ".";
        return new Answer(text, blocks, replies, classification.intent(), false, reason);
    }

    private Answer cart(ActorContext actor, String message, Classification classification,
                        List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        String lower = message.toLowerCase();
        List<String> skus = skuList(classification);
        String sku = skus.isEmpty() ? null : skus.getFirst();

        if (sku == null) {
            List<ProductSummary> found = catalogService.searchForAssistant(message, null, null, null, null, 1);
            if (!found.isEmpty() && (lower.contains("add") || lower.contains("buy") || lower.contains("order it"))) {
                sku = found.getFirst().sku();
            }
        }

        if (sku != null && (lower.contains("remove") || lower.contains("delete"))) {
            ProductSummary product = catalogService.bySku(sku).orElse(null);
            if (product != null) {
                CartView updated = cartService.removeItem(actor, product.id());
                blocks.add(cartBlock(updated, replies));
                return new Answer("Removed " + product.name() + " from your cart.", blocks, replies,
                        "cart_action", false, reason);
            }
        }
        if (sku != null && (lower.contains("add") || lower.contains("buy") || lower.contains("order it")
                || lower.contains("put it"))) {
            ProductSummary product = catalogService.bySku(sku).orElse(null);
            if (product != null) {
                CartView updated = cartService.addItem(actor, product.id(), sku, 1);
                blocks.add(cartBlock(updated, replies));
                return new Answer("Added " + product.name() + ". Your cart total is $"
                        + updated.grandTotal().toPlainString() + ".", blocks, replies, "cart_action", false, reason);
            }
        }

        CartView cart = cartService.view(actor);
        blocks.add(cartBlock(cart, replies));
        String text = cart.empty()
                ? "Your cart is empty. Tell me what you are looking for and I will find something."
                : "You have " + cart.itemCount() + " item(s) totalling $" + cart.grandTotal().toPlainString() + ".";
        return new Answer(text, blocks, replies, "cart_action", false, reason);
    }

    private Answer order(ActorContext actor, Classification classification,
                         List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        String reference = classification.entityString("orderReference");
        String email = classification.entityString("email");

        if (reference == null) {
            if (actor.authenticated()) {
                List<OrderView> orders = orderService.recentForAssistant(actor, 3);
                if (!orders.isEmpty()) {
                    blocks.add(ChatBlock.of(ChatBlock.TYPE_ORDER_CARD, "Your recent orders",
                            orders.size() + " order(s)", null, Map.of("orders", orders),
                            List.of(QuickReply.navigate("Open order history", "/orders"))));
                    OrderView latest = orders.getFirst();
                    return new Answer("Your latest order " + latest.reference() + " is " + latest.statusLabel()
                            + ". Expected " + latest.estimatedDelivery() + ".", blocks, replies, "order_status", false, reason);
                }
            }
            blocks.add(ChatBlock.notice("I need an order reference",
                    "Share the reference (it looks like SW-241002-0002) and I will check where it is.", "info"));
            replies.add(QuickReply.send("Track SW-241002-0002", "Where is my order SW-241002-0002?"));
            return new Answer("Give me the order reference and I will look it up.", blocks, replies,
                    "order_status", false, reason);
        }

        if (!actor.authenticated() && email == null) {
            blocks.add(ChatBlock.notice("Confirm the email",
                    "For a guest order I need the email address used at checkout.", "info"));
            return new Answer("Order " + reference + " needs the email address used at checkout before I can show it.",
                    blocks, replies, "order_status", false, reason);
        }

        OrderView order = orderService.lookupForAssistant(actor, reference, email);
        blocks.add(ChatBlock.of(ChatBlock.TYPE_ORDER_CARD, "Order " + order.reference(), order.statusLabel(),
                null, Map.of("order", order),
                List.of(QuickReply.send("It is late", "What happens if order " + order.reference() + " is late?"))));
        return new Answer(order.reference() + " is " + order.statusLabel() + ". " + order.trackingNote(),
                blocks, replies, "order_status", false, reason);
    }

    private Answer policy(Classification classification, List<ChatBlock> blocks,
                          List<QuickReply> replies, String reason) {
        String topic = switch (classification.intent()) {
            case "returns_policy" -> "returns";
            case "payment_help" -> "payment";
            case "safety_question" -> "support";
            default -> "shipping";
        };
        String answer = policyText(topic);
        return new Answer(answer, blocks, replies, classification.intent(), false, reason);
    }

    private Answer escalate(ActorContext actor, Conversation conversation, String message,
                            Classification classification, List<ChatBlock> blocks, String reason) {
        EscalationView ticket = escalationService.escalate(actor, conversation, "explicit_request",
                "Shopper asked for a person: " + message, classification.entityString("email"), false);
        blocks.add(ChatBlock.of(ChatBlock.TYPE_ESCALATION, "Handed to a human", ticket.reference(),
                ticket.expectedResponse(), Map.of("ticket", ticket),
                List.of(QuickReply.navigate("Back to shopping", "/shop"))));
        return new Answer("I have raised " + ticket.reference() + " and attached this conversation. "
                + ticket.expectedResponse(), blocks, List.of(), "human_handoff", true, reason);
    }

    private Answer capabilities(List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        List<QuickReply> starters = intentCatalog.all().stream()
                .filter(intent -> !intent.requiresSignIn())
                .limit(4)
                .map(intent -> QuickReply.send(intent.sampleUtterances().getFirst(),
                        intent.sampleUtterances().getFirst()))
                .toList();
        replies.addAll(starters);
        return new Answer("""
                I can search the catalogue, compare up to four products on the specs that matter, explain what a
                specification means in practice, manage your cart, and track orders. I can also hand you to a human
                with this conversation attached.""", blocks, replies, "general_help", false, reason);
    }

    private Answer unknown(String message, List<ChatBlock> blocks, List<QuickReply> replies, String reason) {
        blocks.add(ChatBlock.notice("I am not sure I got that",
                "I did not find a product or an order that matches. Try naming a category, a budget or an order reference.",
                "info"));
        replies.addAll(List.of(
                QuickReply.send("Find me a laptop under $1500", "Find me a laptop under $1500"),
                QuickReply.send("What can you help with?", "What can you help with?"),
                QuickReply.send("Talk to a human", "I want to talk to a human")));
        return new Answer("""
                I could not match that to something I can look up. I can search products by need or budget, compare
                products, manage your cart, and check an order if you give me the reference.""",
                blocks, replies, "unknown", false, reason);
    }

    // ------------------------------------------------------------------
    // Shared rendering helpers
    // ------------------------------------------------------------------

    public ChatBlock productCards(String title, List<ProductSummary> products, List<QuickReply> replies) {
        if (!products.isEmpty()) {
            replies.add(QuickReply.send("Compare the top two",
                    "Compare " + products.getFirst().name()
                            + (products.size() > 1 ? " and " + products.get(1).name() : "")));
        }
        return ChatBlock.of(ChatBlock.TYPE_PRODUCT_CARDS, title,
                products.size() + " result" + (products.size() == 1 ? "" : "s"), null,
                Map.of("products", products), null);
    }

    public ChatBlock cartBlock(CartView cart, List<QuickReply> replies) {
        replies.add(cart.empty()
                ? QuickReply.navigate("Browse the shop", "/shop")
                : QuickReply.navigate("Go to cart", "/cart"));
        if (!cart.empty()) {
            replies.add(QuickReply.navigate("Checkout", "/checkout"));
        }
        return ChatBlock.of(ChatBlock.TYPE_CART_SUMMARY,
                cart.empty() ? "Your cart is empty" : cart.itemCount() + " item(s) in your cart",
                cart.empty() ? null : "Total $" + cart.grandTotal().toPlainString(), null,
                Map.of("cart", cart), null);
    }

    public ChatBlock comparisonBlock(List<ProductDetail> details) {
        List<String> keys = details.stream()
                .flatMap(detail -> detail.attributes().keySet().stream())
                .distinct()
                .toList();
        List<Map<String, Object>> rows = keys.stream().map(key -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attribute", key);
            details.forEach(detail -> row.put(detail.sku(), detail.attributes().getOrDefault(key, "—")));
            row.put("differs", details.stream().map(detail -> detail.attributes().getOrDefault(key, "—"))
                    .distinct().count() > 1);
            return row;
        }).toList();
        List<Map<String, Object>> pricing = details.stream().map(detail -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sku", detail.sku());
            entry.put("name", detail.name());
            entry.put("price", detail.price());
            entry.put("rating", detail.rating());
            entry.put("inStock", detail.inStock());
            return entry;
        }).toList();
        return ChatBlock.of(ChatBlock.TYPE_COMPARISON, "Side by side",
                details.size() + " products", null,
                Map.of("products", details, "rows", rows, "pricing", pricing), null);
    }

    public String policyText(String topic) {
        return switch (topic) {
            case "returns" -> "Unopened items can be returned within 30 days of delivery for a full refund. "
                    + "Opened items are accepted within 14 days if faulty.";
            case "warranty" -> "Manufacturer warranty is 2 years on most electronics and covers defects, not "
                    + "accidental damage.";
            case "payment" -> "Checkout in this environment settles with a demo card. No real payment is taken, and I "
                    + "never handle card details.";
            case "support" -> "You can reach a person at " + properties.getCommerce().getSupportEmail()
                    + ", or ask me to raise a ticket with this conversation attached.";
            default -> "Shipping is free on orders over $"
                    + properties.getCommerce().getFreeShippingThreshold().toPlainString()
                    + ", otherwise a flat $" + properties.getCommerce().getFlatShippingFee().toPlainString()
                    + ". Standard delivery is 3-5 working days.";
        };
    }

    private String summarise(String prefix, List<ProductSummary> products) {
        String list = products.stream()
                .map(product -> product.name() + " at $" + product.price().toPlainString())
                .collect(Collectors.joining("; "));
        return prefix + " " + list + ".";
    }

    @SuppressWarnings("unchecked")
    private List<String> skuList(Classification classification) {
        Object value = classification.entities().get("skus");
        return value instanceof List<?> list
                ? list.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                : List.of();
    }

    public BigDecimal budgetOf(Classification classification) {
        return classification.entityDecimal("budget");
    }
}
