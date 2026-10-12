package com.shopwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "assistant_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(length = 48)
    private String intent;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(length = 32)
    private String outcome;

    @Column(name = "value_num", precision = 12, scale = 4)
    private BigDecimal valueNum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Canonical analytics event names. */
    public static final class Type {
        public static final String CONVERSATION_STARTED = "CONVERSATION_STARTED";
        public static final String MESSAGE_SENT = "MESSAGE_SENT";
        public static final String MESSAGE_ANSWERED = "MESSAGE_ANSWERED";
        public static final String INTENT_DETECTED = "INTENT_DETECTED";
        public static final String TOOL_CALLED = "TOOL_CALLED";
        public static final String ACTION_SUGGESTED = "ACTION_SUGGESTED";
        public static final String FALLBACK_TRIGGERED = "FALLBACK_TRIGGERED";
        public static final String MODERATION_BLOCKED = "MODERATION_BLOCKED";
        public static final String RATE_LIMITED = "RATE_LIMITED";
        public static final String ESCALATION_REQUESTED = "ESCALATION_REQUESTED";
        public static final String ASSISTANT_ERROR = "ASSISTANT_ERROR";
        public static final String FEEDBACK_RECEIVED = "FEEDBACK_RECEIVED";

        private Type() {
        }
    }

    public static Map<String, Object> meta(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
