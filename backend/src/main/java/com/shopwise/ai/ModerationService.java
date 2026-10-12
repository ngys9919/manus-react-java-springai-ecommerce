package com.shopwise.ai;

import com.shopwise.domain.ModerationLog;
import com.shopwise.service.AuditWriter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic guardrails around the model.
 *
 * <p>Inbound screening protects the assistant from prompt injection, credential
 * leakage and out-of-scope requests. Outbound screening stops the assistant from
 * echoing card numbers, dumping its instructions, or promising things the shop
 * cannot deliver. Every verdict is persisted so the behaviour is auditable.</p>
 */
@Service
public class ModerationService {

    public enum Action {
        ALLOW,
        REDACT,
        BLOCK
    }

    public record Verdict(
            Action action,
            String category,
            String severity,
            String safeText,
            String userMessage,
            Map<String, Object> flags) {

        public boolean blocked() {
            return action == Action.BLOCK;
        }

        public boolean changed() {
            return action != Action.ALLOW;
        }
    }

    private static final Pattern CARD_LIKE = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern CVV_LIKE = Pattern.compile("\\b(?:cvv|cvc|security code)\\D{0,6}\\d{3,4}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INJECTION = Pattern.compile(
            "(ignore|disregard|forget)\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier)\\s+(instructions|prompts|rules)"
                    + "|you\\s+are\\s+now\\s+"
                    + "|(reveal|show|print|repeat)\\s+(me\\s+)?(your|the)\\s+(system\\s+)?(prompt|instructions|rules)"
                    + "|developer\\s+mode|jailbreak|dan\\s+mode|do\\s+anything\\s+now"
                    + "|pretend\\s+(to\\s+be|you\\s+are)\\s+(an?\\s+)?(unrestricted|unfiltered|different)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELF_HARM = Pattern.compile(
            "\\b(kill myself|suicide|end my life|self[- ]harm|hurt myself)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDICAL_LEGAL = Pattern.compile(
            "\\b(diagnose|diagnosis|prescribe|dosage|is it cancer|legal advice|sue|lawsuit|lawyer)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ABUSE = Pattern.compile(
            "\\b(f+u+c+k+|s+h+i+t+|bastard|idiot|stupid bot|useless bot)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAM_LINKS = Pattern.compile("(https?://\\S+){3,}", Pattern.CASE_INSENSITIVE);

    private static final String[] LEAK_MARKERS = {
            "system prompt", "my instructions are", "i was instructed to", "as an ai language model",
            "here is my system message"};

    private final AuditWriter auditWriter;

    public ModerationService(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    /** Screens a shopper message before it reaches the model. */
    public Verdict screenInbound(Long conversationId, String rawMessage) {
        String text = rawMessage == null ? "" : rawMessage.trim();
        Map<String, Object> flags = new LinkedHashMap<>();

        if (SELF_HARM.matcher(text).find()) {
            flags.put("category", "self_harm");
            record(conversationId, ModerationLog.Direction.INBOUND, "self_harm",
                    ModerationLog.Severity.HIGH, ModerationLog.Action.BLOCK, text);
            return new Verdict(Action.BLOCK, "self_harm", "HIGH", text,
                    "I am not able to help with that, and I am not a crisis service. If you are in danger please contact "
                            + "your local emergency number right now. I can also pass you to a human colleague.",
                    flags);
        }

        if (INJECTION.matcher(text).find()) {
            flags.put("category", "prompt_injection");
            record(conversationId, ModerationLog.Direction.INBOUND, "prompt_injection",
                    ModerationLog.Severity.HIGH, ModerationLog.Action.BLOCK, text);
            return new Verdict(Action.BLOCK, "prompt_injection", "HIGH", text,
                    "I will stay on shopping questions and keep my configuration private. "
                            + "Happy to help you find, compare or track something.",
                    flags);
        }

        if (CARD_LIKE.matcher(text).find() || CVV_LIKE.matcher(text).find()) {
            flags.put("category", "payment_data");
            String redacted = CARD_LIKE.matcher(text).replaceAll("[card number removed]");
            record(conversationId, ModerationLog.Direction.INBOUND, "payment_data",
                    ModerationLog.Severity.HIGH, ModerationLog.Action.REDACT, text);
            return new Verdict(Action.REDACT, "payment_data", "HIGH", redacted,
                    "I removed what looked like card details from your message. I never need them — "
                            + "payment happens on the secure checkout page.",
                    flags);
        }

        if (MEDICAL_LEGAL.matcher(text).find()) {
            flags.put("category", "out_of_scope_advice");
            record(conversationId, ModerationLog.Direction.INBOUND, "out_of_scope_advice",
                    ModerationLog.Severity.MEDIUM, ModerationLog.Action.ALLOW, text);
            return new Verdict(Action.ALLOW, "out_of_scope_advice", "MEDIUM", text, null, flags);
        }

        if (SPAM_LINKS.matcher(text).find()) {
            flags.put("category", "spam");
            record(conversationId, ModerationLog.Direction.INBOUND, "spam",
                    ModerationLog.Severity.MEDIUM, ModerationLog.Action.BLOCK, text);
            return new Verdict(Action.BLOCK, "spam", "MEDIUM", text,
                    "I cannot follow links you paste. Tell me what you are looking for and I will search the catalogue.",
                    flags);
        }

        if (ABUSE.matcher(text).find()) {
            flags.put("category", "abuse");
            record(conversationId, ModerationLog.Direction.INBOUND, "abuse",
                    ModerationLog.Severity.LOW, ModerationLog.Action.ALLOW, text);
            return new Verdict(Action.ALLOW, "abuse", "LOW", text, null, flags);
        }

        return new Verdict(Action.ALLOW, "clean", "NONE", text, null, flags);
    }

    /** Screens the assistant's own draft before it is shown to the shopper. */
    public Verdict screenOutbound(Long conversationId, String draft) {
        String text = draft == null ? "" : draft;
        Map<String, Object> flags = new LinkedHashMap<>();
        List<String> hits = new ArrayList<>();

        if (CARD_LIKE.matcher(text).find()) {
            hits.add("payment_data");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : LEAK_MARKERS) {
            if (lower.contains(marker)) {
                hits.add("prompt_leak");
                break;
            }
        }
        if (hits.isEmpty()) {
            return new Verdict(Action.ALLOW, "clean", "NONE", text, null, flags);
        }

        String cleaned = CARD_LIKE.matcher(text).replaceAll("[card number removed]");
        flags.put("categories", hits);
        record(conversationId, ModerationLog.Direction.OUTBOUND, String.join(",", hits),
                ModerationLog.Severity.MEDIUM, ModerationLog.Action.REDACT, text);
        return new Verdict(Action.REDACT, String.join(",", hits), "MEDIUM", cleaned, null, flags);
    }

    private void record(Long conversationId, ModerationLog.Direction direction, String category,
                        ModerationLog.Severity severity, ModerationLog.Action action, String excerpt) {
        try {
            auditWriter.writeModerationLog(ModerationLog.builder()
                    .conversationId(conversationId)
                    .direction(direction.name())
                    .category(category)
                    .severity(severity.name())
                    .action(action.name())
                    .excerpt(excerpt == null ? null : excerpt.substring(0, Math.min(excerpt.length(), 400)))
                    .build());
        } catch (RuntimeException ignored) {
            // Auditing must never take the conversation down.
        }
    }
}