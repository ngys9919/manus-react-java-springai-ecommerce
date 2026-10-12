package com.shopwise.web.dto;

import java.util.List;
import java.util.Map;

/**
 * A rich, renderable segment of an assistant reply. The client switches on
 * {@code type} and renders the matching component, so new block types can be
 * introduced without breaking older clients.
 */
public record ChatBlock(
        String type,
        String title,
        String subtitle,
        String body,
        Map<String, Object> data,
        List<QuickReply> actions) {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_PRODUCT_CARDS = "PRODUCT_CARDS";
    public static final String TYPE_COMPARISON = "COMPARISON";
    public static final String TYPE_CART_SUMMARY = "CART_SUMMARY";
    public static final String TYPE_ORDER_CARD = "ORDER_CARD";
    public static final String TYPE_QUICK_REPLIES = "QUICK_REPLIES";
    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_ESCALATION = "ESCALATION";
    public static final String TYPE_RECOVERY = "RECOVERY";

    public static ChatBlock text(String body) {
        return new ChatBlock(TYPE_TEXT, null, null, body, null, null);
    }

    public static ChatBlock notice(String title, String body, String tone) {
        return new ChatBlock(TYPE_NOTICE, title, tone, body, null, null);
    }

    public static ChatBlock of(String type, String title, String subtitle, String body,
                               Map<String, Object> data, List<QuickReply> actions) {
        return new ChatBlock(type, title, subtitle, body, data, actions);
    }
}