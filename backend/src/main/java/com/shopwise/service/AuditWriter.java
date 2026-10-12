package com.shopwise.service;

import com.shopwise.domain.AssistantEvent;
import com.shopwise.domain.ModerationLog;
import com.shopwise.repository.AssistantEventRepository;
import com.shopwise.repository.ModerationLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows in their own transaction.
 *
 * <p>Two properties matter here. The write is isolated, so a failure to record
 * analytics can never roll back a shopper's cart or conversation. And the write is
 * flushed inside the method, so a database problem surfaces as a catchable exception
 * instead of a commit-time {@code UnexpectedRollbackException} on the way out.</p>
 */
@Component
public class AuditWriter {

    private final AssistantEventRepository eventRepository;
    private final ModerationLogRepository moderationLogRepository;

    public AuditWriter(AssistantEventRepository eventRepository,
                       ModerationLogRepository moderationLogRepository) {
        this.eventRepository = eventRepository;
        this.moderationLogRepository = moderationLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeEvent(AssistantEvent event) {
        eventRepository.saveAndFlush(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeModerationLog(ModerationLog log) {
        moderationLogRepository.saveAndFlush(log);
    }
}