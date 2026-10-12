package com.shopwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 48)
    private String intent;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(length = 64)
    private String model;

    /** Tool invocations the model made while producing this message. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<Map<String, Object>> toolCalls;

    /** Rich UI payloads (product cards, comparison tables, quick replies, notices). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> blocks;

    /** Moderation verdict attached to the message, when anything was flagged. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_flags", columnDefinition = "jsonb")
    private Map<String, Object> safetyFlags;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum MessageRole {
        USER,
        ASSISTANT,
        SYSTEM
    }

    public static List<Map<String, Object>> emptyBlocks() {
        return new ArrayList<>(0);
    }

    public static Map<String, Object> emptyMap() {
        return new LinkedHashMap<>(0);
    }
}
