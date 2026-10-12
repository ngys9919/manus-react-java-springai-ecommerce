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

import java.time.Instant;

@Entity
@Table(name = "moderation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 48)
    private String category;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 24)
    private String action;

    @Column(columnDefinition = "text")
    private String excerpt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public enum Action {
        ALLOW,
        REDACT,
        BLOCK
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }
}
