package com.shopwise.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    @Builder.Default
    private UUID publicId = UUID.randomUUID();

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_token", length = 64)
    private String guestToken;

    @Column(nullable = false, length = 200)
    @Builder.Default
    private String title = "New conversation";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(nullable = false, length = 24)
    @Builder.Default
    private String channel = "WEB";

    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private int messageCount = 0;

    @Column(name = "last_intent", length = 48)
    private String lastIntent;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "last_message_at", nullable = false)
    @Builder.Default
    private Instant lastMessageAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum ConversationStatus {
        /** Normal conversation, the assistant answers. */
        ACTIVE,
        /** Handed to a human agent; the bot stays quiet until it is resumed. */
        ESCALATED,
        /** Closed by the shopper or by retention. */
        CLOSED
    }
}
