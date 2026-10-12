package com.shopwise.ai;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Conversation;
import com.shopwise.exception.ApiException;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.CartService;
import com.shopwise.service.CatalogService;
import com.shopwise.service.ConversationService;
import com.shopwise.service.EscalationService;
import com.shopwise.service.OrderService;
import com.shopwise.web.dto.CartView;
import com.shopwise.web.dto.ChatBlock;
import com.shopwise.web.dto.OrderView;
import com.shopwise.web.dto.ProductDetail;
import com.shopwise.web.dto.ProductSummary;
import com.shopwise.web.dto.QuickReply;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The assistant's hands. Every capability the model can actually exercise lives here
 * as a {@code @Tool} method, so the set of things the assistant can do is auditable
 * in one file.
 *
 * <p>Two rules hold for every tool:
 * <ol>
 *   <li>Results come from the database, never from the model's imagination.</li>
 *   <li>Anything personal is gated on the {@link ActorContext} carried in the
 *       {@link ToolContext}: a guest cannot read someone else's order.</li>
 * </ol>
 */
@Component
public class ShoppingAssistantTools {

    private final CatalogService catalogService;
    private final CartService cartService;
    private final OrderService orderService;
    private final EscalationService escalationService;
    private final ConversationService conversationService;
    private final ShopwiseProperties properties;

    public ShoppingAssistantTools(CatalogService catalogService,
                                  CartService cartService,
                                  OrderService orderService,
                                  EscalationService escalationService,
                                  ConversationService conversationService,
                                  ShopwiseProperties properties) {
        this.catalogService = catalogService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.escalationService = escalationService;
        this.conversationService = conversationService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------

    @Tool(name = "search_products",
            description = """
                    Search the live product catalogue. Use this whenever the shopper describes a need, a budget,
                    a category or a brand. Returns up to 8 matching products with sku, name, price, stock and rating.
                    Always call this before naming a product or quoting a price.""")
    public Map<String, Object> searchProducts(
            @ToolParam(description = "Free-text search terms, e.g. 'noise cancelling headphones'. Leave empty to browse.")
            String query,
            @ToolParam(description = "Category slug: laptops, smartphones, headphones, smartwatches, cameras, smart-home. Optional.")
            String category,
            @ToolParam(description = "Maximum price in USD, e.g. 1500. Optional.")
            Double maxPrice,
            @ToolParam(description = "Minimum price in USD. Optional.")
            Double minPrice,
            @ToolParam(description = "Set true to only return products that are in stock.")
            Boolean inStockOnly,
            ToolContext toolContext) {

        List<ProductSummary> products = catalogService.searchForAssistant(
                query,
                category,
                minPrice == null ? null : BigDecimal.valueOf(minPrice),
                maxPrice == null ? null : BigDecimal.valueOf(maxPrice),
                inStockOnly,
                null);

        note(toolContext, "search_products", Map.of(
                "query", Objects.toString(query, ""),
                "category", Objects.toString(category, ""),
                "results", products.size()));

        if (products.isEmpty()) {
            return Map.of(
                    "results", List.of(),
                    "message", "No products matched. Try a broader query or drop the price limit.");
        }

        blocks(toolContext).add(ChatBlock.of(
                ChatBlock.TYPE_PRODUCT_CARDS,
                "Matching products",
                products.size() + " result" + (products.size() == 1 ? "" : "s"),
                null,
                Map.of("products", products),
                products.stream()
                        .limit(3)
                        .map(product -> QuickReply.send("Compare " + shortName(product), "Compare " + product.name()))
                        .toList()));

        return Map.of("results", products, "count", products.size());
    }

    @Tool(name = "get_product_details",
            description = "Fetch the full specification sheet, highlights and stock level for one product by SKU or slug.")
    public Map<String, Object> getProductDetails(
            @ToolParam(description = "Product SKU such as SKU-1001, or the product slug.") String sku,
            ToolContext toolContext) {

        try {
            ProductDetail detail = catalogService.detail(sku);
            note(toolContext, "get_product_details", Map.of("sku", detail.sku()));
            blocks(toolContext).add(ChatBlock.of(
                    ChatBlock.TYPE_PRODUCT_CARDS,
                    detail.name(),
                    "Specifications",
                    null,
                    Map.of("products", List.of(detail), "expanded", true),
                    List.of(QuickReply.navigate("Open product page", "/product/" + detail.slug()))));
            return Map.of("product", detail);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    @Tool(name = "compare_products",
            description = """
                    Compare two to four products side by side. Call this as soon as the shopper names the products;
                    do not search first. Accepts SKUs, slugs or plain product names, so pass exactly what the shopper
                    said (e.g. ["Pulse 12 Pro", "Vertex Ultra 5G"]). The result contains a spec table and the list of
                    attributes where the products actually differ, which is what the shopper usually cares about.""")
    public Map<String, Object> compareProducts(
            @ToolParam(description = """
                    The 2 to 4 products to compare. Any of a SKU, a slug or a product name is accepted, e.g.
                    ["Pulse 12 Pro", "Vertex Ultra 5G"] or ["SKU-2001", "SKU-2002"].""")
            List<String> products,
            ToolContext toolContext) {

        List<String> requested = products == null ? List.of()
                : products.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        int max = properties.getAssistant().getMaxComparisonSize();
        if (requested.size() < 2) {
            return Map.of("error", "comparison_needs_two_products",
                    "message", "I need at least two products to compare. Which two should I put side by side?");
        }
        if (requested.size() > max) {
            return Map.of("error", "comparison_too_large",
                    "message", "I can compare up to " + max + " products at once. Which " + max + " matter most?");
        }

        CatalogService.ComparisonResolution resolution = catalogService.resolveForComparison(requested);
        List<ProductDetail> details = resolution.products();
        if (details.size() < 2) {
            return Map.of("error", "unknown_products",
                    "message", "I could not match these products: " + String.join(", ", resolution.unresolved())
                            + ". Tell me the exact product names or let me search the catalogue for you.");
        }

        List<String> specKeys = new ArrayList<>();
        for (ProductDetail detail : details) {
            for (String key : detail.attributes().keySet()) {
                if (!specKeys.contains(key)) {
                    specKeys.add(key);
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> differences = new ArrayList<>();
        for (String key : specKeys) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attribute", key);
            Set<String> values = new LinkedHashSet<>();
            for (ProductDetail detail : details) {
                String value = detail.attributes().getOrDefault(key, "—");
                row.put(detail.sku(), value);
                values.add(value);
            }
            row.put("differs", values.size() > 1);
            if (values.size() > 1) {
                differences.add(key);
            }
            rows.add(row);
        }

        List<Map<String, Object>> priceRows = new ArrayList<>();
        BigDecimal cheapest = details.stream().map(ProductDetail::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal bestRated = details.stream().map(ProductDetail::rating).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        for (ProductDetail detail : details) {
            Map<String, Object> price = new LinkedHashMap<>();
            price.put("sku", detail.sku());
            price.put("name", detail.name());
            price.put("price", detail.price());
            price.put("inStock", detail.inStock());
            price.put("rating", detail.rating());
            price.put("cheapest", detail.price().compareTo(cheapest) == 0);
            price.put("bestRated", detail.rating().compareTo(bestRated) == 0);
            priceRows.add(price);
        }

        note(toolContext, "compare_products", Map.of("skus", details.stream().map(ProductDetail::sku).toList()));
        blocks(toolContext).add(ChatBlock.of(
                ChatBlock.TYPE_COMPARISON,
                "Side by side",
                differences.isEmpty() ? "These two are very close" : differences.size() + " specs differ",
                null,
                Map.of(
                        "products", details,
                        "rows", rows,
                        "pricing", priceRows,
                        "differentiatingAttributes", differences),
                List.of(
                        QuickReply.send("Which one should I buy?", "Which of these should I buy and why?"),
                        QuickReply.navigate("Open the cheapest", "/product/" + cheapestSlug(details, cheapest)))));

        return Map.of(
                "products", details,
                "specificationRows", rows,
                "differentiatingAttributes", differences,
                "pricing", priceRows);
    }

    @Tool(name = "find_best_value",
            description = """
                    Recommend the best product for a stated budget and use case. Use this for 'what should I buy'
                    questions. Returns a primary pick plus two alternatives, with the reasoning left to you.""")
    public Map<String, Object> findBestValue(
            @ToolParam(description = "Budget ceiling in USD, e.g. 1200. Optional.")
            Double budget,
            @ToolParam(description = "Category slug to search within. Optional.")
            String category,
            @ToolParam(description = "What the shopper will use it for, e.g. 'university', 'travel photography'.")
            String useCase,
            ToolContext toolContext) {

        List<ProductSummary> candidates = catalogService.searchForAssistant(
                useCase, category, null, budget == null ? null : BigDecimal.valueOf(budget), true, 6);

        if (candidates.isEmpty()) {
            return Map.of("results", List.of(),
                    "message", "Nothing in stock fits that budget. Ask whether a higher budget is possible, or search another category.");
        }

        note(toolContext, "find_best_value", Map.of(
                "budget", Objects.toString(budget, "any"),
                "category", Objects.toString(category, "any"),
                "candidates", candidates.size()));

        List<ProductSummary> ranked = new ArrayList<>(candidates);
        ranked.sort((left, right) -> right.rating().compareTo(left.rating()));

        blocks(toolContext).add(ChatBlock.of(
                ChatBlock.TYPE_PRODUCT_CARDS,
                "Best fits" + (budget == null ? "" : " under $" + budget.intValue()),
                useCase == null || useCase.isBlank() ? "Ranked by rating within budget" : useCase,
                null,
                Map.of("products", ranked),
                ranked.stream().limit(2)
                        .map(product -> QuickReply.send("Compare top two", "Compare " + ranked.get(0).name()
                                + " and " + ranked.get(1).name()))
                        .distinct()
                        .toList()));

        return Map.of("recommended", ranked.get(0), "alternatives", ranked.stream().skip(1).limit(2).toList());
    }

    @Tool(name = "list_categories",
            description = "List the store categories with product counts and the lowest price in each.")
    public Map<String, Object> listCategories(ToolContext toolContext) {
        var categories = catalogService.categories();
        note(toolContext, "list_categories", Map.of("count", categories.size()));
        blocks(toolContext).add(ChatBlock.of(
                ChatBlock.TYPE_QUICK_REPLIES,
                "Browse a category",
                null,
                null,
                null,
                categories.stream()
                        .map(category -> QuickReply.navigate(category.icon() + " " + category.name(),
                                "/shop?category=" + category.slug()))
                        .toList()));
        return Map.of("categories", categories);
    }

    // ------------------------------------------------------------------
    // Cart
    // ------------------------------------------------------------------

    @Tool(name = "get_cart",
            description = "Read the shopper's current cart, including totals, shipping and any stock warnings.")
    public Map<String, Object> getCart(ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        try {
            CartView cart = cartService.view(actor);
            note(toolContext, "get_cart", Map.of("items", cart.itemCount()));
            if (!cart.empty()) {
                blocks(toolContext).add(cartBlock(cart));
            }
            return Map.of("cart", cart);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    @Tool(name = "add_to_cart",
            description = "Add a product to the cart by SKU. Confirm the resulting cart contents and total back to the shopper.")
    public Map<String, Object> addToCart(
            @ToolParam(description = "Product SKU, e.g. SKU-1002") String sku,
            @ToolParam(description = "Quantity, default 1.") Integer quantity,
            ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        try {
            ProductSummary product = catalogService.bySku(sku)
                    .orElseThrow(() -> ApiException.notFound("SKU " + sku));
            CartView cart = cartService.addItem(actor, product.id(), sku, quantity == null ? 1 : quantity);
            note(toolContext, "add_to_cart", Map.of("sku", sku, "quantity", quantity == null ? 1 : quantity));
            blocks(toolContext).add(cartBlock(cart));
            return Map.of("added", product, "cart", cart);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    @Tool(name = "update_cart_quantity",
            description = "Change the quantity of a product already in the cart. Set quantity to 0 to remove it.")
    public Map<String, Object> updateCartQuantity(
            @ToolParam(description = "Product SKU in the cart.") String sku,
            @ToolParam(description = "New quantity. 0 removes the line.") Integer quantity,
            ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        try {
            ProductSummary product = catalogService.bySku(sku)
                    .orElseThrow(() -> ApiException.notFound("SKU " + sku));
            CartView cart = cartService.updateQuantity(actor, product.id(), quantity == null ? 1 : quantity);
            note(toolContext, "update_cart_quantity", Map.of("sku", sku, "quantity", quantity == null ? 1 : quantity));
            blocks(toolContext).add(cartBlock(cart));
            return Map.of("cart", cart);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    @Tool(name = "remove_from_cart",
            description = "Remove a product from the cart by SKU.")
    public Map<String, Object> removeFromCart(
            @ToolParam(description = "Product SKU to remove.") String sku,
            ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        try {
            ProductSummary product = catalogService.bySku(sku)
                    .orElseThrow(() -> ApiException.notFound("SKU " + sku));
            CartView cart = cartService.removeItem(actor, product.id());
            note(toolContext, "remove_from_cart", Map.of("sku", sku));
            blocks(toolContext).add(cartBlock(cart));
            return Map.of("cart", cart);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    // ------------------------------------------------------------------
    // Orders (permission gated)
    // ------------------------------------------------------------------

    @Tool(name = "get_order_status",
            description = """
                    Look up one order by reference. A signed-in shopper can only read their own orders; for a guest the
                    email used at checkout is required. If the tool returns a permission error, ask for the email rather
                    than guessing.""")
    public Map<String, Object> getOrderStatus(
            @ToolParam(description = "Order reference such as SW-241002-0002.") String reference,
            @ToolParam(description = "Email used at checkout, required for guest orders.") String email,
            ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        try {
            OrderView order = orderService.lookupForAssistant(actor, reference, email);
            note(toolContext, "get_order_status", Map.of("reference", reference));
            blocks(toolContext).add(ChatBlock.of(
                    ChatBlock.TYPE_ORDER_CARD,
                    "Order " + order.reference(),
                    order.statusLabel(),
                    null,
                    Map.of("order", order),
                    List.of(
                            QuickReply.send("What if it is late?", "What happens if order " + order.reference() + " is late?"),
                            QuickReply.send("I need to return this", "I want to return order " + order.reference()))));
            return Map.of("order", order);
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    @Tool(name = "list_recent_orders",
            description = "List the signed-in shopper's recent orders. Requires an authenticated session.")
    public Map<String, Object> listRecentOrders(ToolContext toolContext) {
        ActorContext actor = AssistantToolContext.actor(toolContext);
        if (!actor.authenticated()) {
            return Map.of("error", "sign_in_required",
                    "message", "The shopper is not signed in, so I cannot list orders. Offer to look one up by reference "
                            + "and email instead, or invite them to sign in.");
        }
        List<OrderView> orders = orderService.recentForAssistant(actor, 5);
        note(toolContext, "list_recent_orders", Map.of("count", orders.size()));
        if (!orders.isEmpty()) {
            blocks(toolContext).add(ChatBlock.of(
                    ChatBlock.TYPE_ORDER_CARD,
                    "Your recent orders",
                    orders.size() + " order" + (orders.size() == 1 ? "" : "s"),
                    null,
                    Map.of("orders", orders),
                    List.of(QuickReply.navigate("Open order history", "/orders"))));
        }
        return Map.of("orders", orders);
    }

    // ------------------------------------------------------------------
    // Policy, safety and handoff
    // ------------------------------------------------------------------

    @Tool(name = "get_store_policy",
            description = "Answer policy questions from the shop's own configuration: shipping, returns, warranty, payment, support.")
    public Map<String, Object> getStorePolicy(
            @ToolParam(description = "One of: shipping, returns, warranty, payment, support, privacy")
            String topic,
            ToolContext toolContext) {
        String key = topic == null ? "shipping" : topic.trim().toLowerCase();
        String answer = switch (key) {
            case "shipping" -> "Shipping is free on orders over $"
                    + properties.getCommerce().getFreeShippingThreshold().toPlainString()
                    + ", otherwise a flat $" + properties.getCommerce().getFlatShippingFee().toPlainString()
                    + ". Standard delivery is 3-5 working days.";
            case "returns" -> "Unopened items can be returned within 30 days of delivery for a full refund. "
                    + "Opened items are accepted within 14 days if faulty.";
            case "warranty" -> "Manufacturer warranty is 2 years on most electronics and covers defects, not accidental damage. "
                    + "Keep the order reference to claim.";
            case "payment" -> "Checkout in this environment settles with a demo card. No real payment is taken and "
                    + "the assistant never handles card details.";
            case "privacy" -> "The assistant stores the conversation so support can pick it up. Order lookups are "
                    + "restricted to the account or email that placed the order.";
            case "support" -> "You can reach a person at " + properties.getCommerce().getSupportEmail()
                    + ", or ask me to raise a ticket with this transcript attached.";
            default -> "I can explain shipping, returns, warranty, payment and privacy. Which one do you need?";
        };
        note(toolContext, "get_store_policy", Map.of("topic", key));
        return Map.of("topic", key, "answer", answer);
    }

    @Tool(name = "request_human_handoff",
            description = """
                    Escalate to a human agent. Use this when the shopper explicitly asks for a person, when they are
                    clearly frustrated, or when the request is outside what these tools can do. Always tell the shopper
                    the ticket reference the tool returns.""")
    public Map<String, Object> requestHumanHandoff(
            @ToolParam(description = "One of: explicit_request, unresolved, order_issue, payment, complaint, safety, policy")
            String reason,
            @ToolParam(description = "Short summary of what the shopper needs, in your own words.") String note,
            @ToolParam(description = "Contact email if the shopper gave one.") String email,
            @ToolParam(description = "True if the situation is urgent.") Boolean urgent,
            ToolContext toolContext) {

        ActorContext actor = AssistantToolContext.actor(toolContext);
        Long conversationId = AssistantToolContext.conversationId(toolContext);
        try {
            Conversation target = conversationService.findOrCreate(
                    actor, AssistantToolContext.conversationPublicId(toolContext));
            EscalationView ticket = escalationService.escalate(actor, target, reason, note, email,
                    Boolean.TRUE.equals(urgent));

            note(toolContext, "request_human_handoff", Map.of("reference", ticket.reference(), "reason",
                    ticket.reason()));
            blocks(toolContext).add(ChatBlock.of(
                    ChatBlock.TYPE_ESCALATION,
                    "Handed to a human",
                    ticket.reference(),
                    ticket.expectedResponse(),
                    Map.of("ticket", ticket),
                    List.of(
                            QuickReply.send("What happens next?", "What happens next with ticket " + ticket.reference() + "?"),
                            QuickReply.navigate("Back to shopping", "/shop"))));
            return Map.of("ticket", ticket, "message", "A human will pick this up. Quote reference " + ticket.reference() + ".");
        } catch (ApiException ex) {
            return failure(ex);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ChatBlock cartBlock(CartView cart) {
        return ChatBlock.of(
                ChatBlock.TYPE_CART_SUMMARY,
                cart.empty() ? "Your cart is empty" : cart.itemCount() + " item" + (cart.itemCount() == 1 ? "" : "s") + " in your cart",
                cart.empty() ? null : "Subtotal $" + cart.subtotal().toPlainString(),
                null,
                Map.of("cart", cart),
                cart.empty()
                        ? List.of(QuickReply.navigate("Browse the shop", "/shop"))
                        : List.of(
                                QuickReply.navigate("Go to cart", "/cart"),
                                QuickReply.navigate("Checkout", "/checkout")));
    }

    private Map<String, Object> failure(ApiException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", ex.getCode());
        payload.put("message", ex.getMessage());
        payload.put("guidance", switch (ex.getCode()) {
            case "FORBIDDEN" -> "Explain the permission rule plainly and offer the alternative (sign in, or provide the email).";
            case "NOT_FOUND" -> "Tell the shopper it was not found and offer to search the catalogue instead.";
            case "CONFLICT" -> "Explain the stock or state problem and offer the nearest alternative product.";
            default -> "Explain what happened in plain language and offer a next step.";
        });
        return payload;
    }

    private String cheapestSlug(List<ProductDetail> details, BigDecimal cheapest) {
        return details.stream()
                .filter(detail -> detail.price().compareTo(cheapest) == 0)
                .map(ProductDetail::slug)
                .findFirst()
                .orElse(details.getFirst().slug());
    }

    private String shortName(ProductSummary product) {
        return product.name().length() > 22 ? product.name().substring(0, 22) : product.name();
    }

    private List<ChatBlock> blocks(ToolContext toolContext) {
        return AssistantToolContext.blocks(toolContext);
    }

    private void note(ToolContext toolContext, String toolName, Map<String, Object> arguments) {
        try {
            AssistantToolContext.toolLog(toolContext).add(toolName);
        } catch (UnsupportedOperationException ignored) {
            // Immutable sink: analytics simply lose this entry.
        }
    }
}
