package com.shopwise.web.dto;

/**
 * A tappable suggestion that either sends a message, opens a product, or triggers
 * a client-side action such as opening the cart drawer.
 */
public record QuickReply(String label, String value, String kind) {

    /** Sends {@code value} as the next user message. */
    public static final String KIND_SEND = "SEND";
    /** Navigates the storefront to {@code value} (a storefront route). */
    public static final String KIND_NAVIGATE = "NAVIGATE";
    /** Runs a named client action, e.g. {@code ADD_TO_CART:<sku>}. */
    public static final String KIND_ACTION = "ACTION";

    public static QuickReply send(String label, String value) {
        return new QuickReply(label, value, KIND_SEND);
    }

    public static QuickReply navigate(String label, String route) {
        return new QuickReply(label, route, KIND_NAVIGATE);
    }

    public static QuickReply action(String label, String action) {
        return new QuickReply(label, action, KIND_ACTION);
    }
}