package com.shopwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "escalation_ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(nullable = false, length = 64)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private EscalationPriority priority = EscalationPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private EscalationStatus status = EscalationStatus.OPEN;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "transcript_excerpt", columnDefinition = "text")
    private String transcriptExcerpt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum EscalationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum EscalationStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED
    }
}
