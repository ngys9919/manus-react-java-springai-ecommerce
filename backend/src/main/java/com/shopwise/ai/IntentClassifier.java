package com.shopwise.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic intent classifier.
 *
 * <p>The model does the talking, but this classifier does three jobs the model cannot
 * be trusted with: it labels every turn for analytics, it supplies the routing key for
 * the offline fallback engine, and it extracts entities (budget, category, SKUs, order
 * references) that the fallback can act on without any LLM call.</p>
 */
@Component
public class IntentClassifier {

    public record Classification(String intent, double confidence, Map<String, Object> entities) {

        public String entityString(String key) {
            Object value = entities.get(key);
            return value == null ? null : String.valueOf(value);
        }

        public BigDecimal entityDecimal(String key) {
            Object value = entities.get(key);
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            return null;
        }
    }

    private static final Pattern ORDER_REFERENCE = Pattern.compile("\\bSW-\\d{6}-\\d{4}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU = Pattern.compile("\\bSKU-\\d{4}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}");
    private static final Pattern DOLLAR = Pattern.compile("\\$\\s?([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern GREETING = Pattern.compile("hi|hiya|hey|hello|yo|thanks|thank you");

    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|less than|max(?:imum)?(?: of)?|budget(?: of)?|up to|around|about)\\s*\\$?\\s*([\\d,]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("laptops", List.of("laptop", "notebook", "ultrabook", "macbook", "computer for"));
        CATEGORY_KEYWORDS.put("smartphones", List.of("phone", "smartphone", "iphone", "android", "mobile phone"));
        CATEGORY_KEYWORDS.put("headphones", List.of("headphone", "earbud", "earbuds", "earphone", "noise cancelling",
                "noise canceling", "anc ", "headset"));
        CATEGORY_KEYWORDS.put("smartwatches", List.of("smartwatch", "smart watch", "fitness band", "fitness tracker",
                "wearable"));
        CATEGORY_KEYWORDS.put("cameras", List.of("camera", "mirrorless", "dslr", "action cam", "lens", "photography"));
        CATEGORY_KEYWORDS.put("smart-home", List.of("smart bulb", "smart light", "smart home", "air quality", "hub",
                "security camera", "doorbell", "speaker"));
    }

    public Classification classify(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT).trim();
        Map<String, Object> entities = new LinkedHashMap<>();

        Matcher orderMatcher = ORDER_REFERENCE.matcher(rawMessage == null ? "" : rawMessage);
        if (orderMatcher.find()) {
            entities.put("orderReference", orderMatcher.group().toUpperCase(Locale.ROOT));
        }
        Matcher skuMatcher = SKU.matcher(rawMessage == null ? "" : rawMessage);
        List<String> skus = new java.util.ArrayList<>();
        while (skuMatcher.find()) {
            skus.add(skuMatcher.group().toUpperCase(Locale.ROOT));
        }
        if (!skus.isEmpty()) {
            entities.put("skus", skus);
        }
        Matcher emailMatcher = EMAIL.matcher(rawMessage == null ? "" : rawMessage);
        if (emailMatcher.find()) {
            entities.put("email", emailMatcher.group());
        }
        BigDecimal budget = extractBudget(rawMessage);
        if (budget != null) {
            entities.put("budget", budget);
        }
        String category = extractCategory(message);
        if (category != null) {
            entities.put("category", category);
        }

        // Ordering matters: the most specific signal wins.
        if (containsAny(message, "human", "agent", "real person", "someone real", "talk to someone", "speak to a person",
                "customer service", "representative")) {
            return new Classification("human_handoff", 0.95, entities);
        }
        if (entities.containsKey("orderReference")
                || containsAny(message, "where is my order", "track my order", "order status", "my order", "delivery status")) {
            return new Classification("order_status", 0.9, entities);
        }
        if (containsAny(message, "compare", "versus", " vs ", "vs.", "difference between", "side by side",
                "which is better")) {
            if (skus.size() >= 2 || containsAny(message, " and ")) {
                return new Classification("product_compare", 0.85, entities);
            }
            return new Classification("product_compare", 0.7, entities);
        }
        if (containsAny(message, "return", "refund", "warranty", "guarantee", "exchange")) {
            return new Classification("returns_policy", 0.85, entities);
        }
        if (containsAny(message, "shipping", "delivery cost", "deliver to", "how long does delivery", "postage",
                "free shipping")) {
            return new Classification("shipping_info", 0.85, entities);
        }
        if (containsAny(message, "pay", "payment", "card", "invoice", "installment", "voucher", "coupon code")) {
            return new Classification("payment_help", 0.8, entities);
        }
        if (containsAny(message, "safe", "safety", "suitable for", "age", "child", "kid", "allergy", "choking")) {
            return new Classification("safety_question", 0.8, entities);
        }
        if (containsAny(message, "cart", "basket", "add ", "remove ", "delete from", "checkout", "buy it",
                "buy the", "order it")) {
            return new Classification("cart_action", 0.8, entities);
        }
        if (containsAny(message, "in stock", "available", "availability", "out of stock", "restock", "back in")) {
            return new Classification("stock_check", 0.85, entities);
        }
        if (containsAny(message, "discount", "on sale", "offer", "cheaper", "price drop", "deal", "promo")) {
            return new Classification("price_check", 0.8, entities);
        }
        // "What do people usually buy together with X", "what goes with X" and "what else should I
        // consider" are recommendation questions, but they share no wording with "which is best",
        // so they used to fall through to "unknown" and lose the intent signal the prompt uses.
        if (containsAny(message, "best", "recommend", "suggest", "which should i", "what should i buy", "good for",
                "ideal for", "looking for", "buy together", "buy with", "bought together", "goes with",
                "go well with", "go with", "pair with", "pairs with", "accessor", "complement",
                "what else", "anything else", "other people buy", "people buy", "usually buy")) {
            return new Classification("product_recommend", 0.8, entities);
        }
        if (containsAny(message, "how much", "price of", "cost of", "what does it cost")) {
            return new Classification("price_check", 0.7, entities);
        }
        if (containsAny(message, "spec", "specification", "how heavy", "battery life", "does it have", "is it",
                "tell me about")) {
            return new Classification("product_details", 0.7, entities);
        }
        if (containsAny(message, "find", "search", "show me", "do you sell", "i need", "i want", "looking at",
                "browse", "catalogue", "catalog")) {
            return new Classification("product_search", 0.75, entities);
        }
        // A bare category or budget ("laptop under 1500") is still a catalogue search.
        if (entities.containsKey("category") || entities.containsKey("budget")) {
            return new Classification("product_search", 0.7, entities);
        }
        if (containsAny(message, "what can you", "help me", "how does this work", "who are you", "hello",
                "hey", "good morning", "good afternoon", "thanks", "thank you")
                || GREETING.matcher(message).matches()) {
            return new Classification("general_help", 0.8, entities);
        }
        return new Classification("unknown", 0.35, entities);
    }

    private BigDecimal extractBudget(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        Matcher budgetMatcher = BUDGET.matcher(rawMessage);
        if (budgetMatcher.find()) {
            return parse(budgetMatcher.group(1));
        }
        Matcher dollarMatcher = DOLLAR.matcher(rawMessage);
        if (dollarMatcher.find()) {
            return parse(dollarMatcher.group(1));
        }
        return null;
    }

    private BigDecimal parse(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractCategory(String message) {
        if (message.contains("security camera") || message.contains("indoor camera")
                || message.contains("surveillance")) {
            return "smart-home";
        }
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (message.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean containsAny(String message, String... needles) {
        for (String needle : needles) {
            if (message.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
