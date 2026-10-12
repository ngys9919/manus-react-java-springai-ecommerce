package com.shopwise.repository;

import com.shopwise.domain.AssistantEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AssistantEventRepository extends JpaRepository<AssistantEvent, Long> {

    @Query("""
            select e.eventType, count(e) from AssistantEvent e
            where e.createdAt >= :from
            group by e.eventType
            """)
    List<Object[]> countByEventTypeSince(@Param("from") Instant from);

    @Query("""
            select e.intent, count(e) from AssistantEvent e
            where e.eventType = 'INTENT_DETECTED' and e.intent is not null and e.createdAt >= :from
            group by e.intent
            order by count(e) desc
            """)
    List<Object[]> topIntentsSince(@Param("from") Instant from, Pageable pageable);

    @Query("""
            select e.toolName, count(e) from AssistantEvent e
            where e.eventType = 'TOOL_CALLED' and e.toolName is not null and e.createdAt >= :from
            group by e.toolName
            order by count(e) desc
            """)
    List<Object[]> topToolsSince(@Param("from") Instant from, Pageable pageable);

    @Query("""
            select avg(e.valueNum) from AssistantEvent e
            where e.eventType = 'MESSAGE_ANSWERED' and e.valueNum is not null and e.createdAt >= :from
            """)
    Double averageLatencySince(@Param("from") Instant from);

    @Query(value = """
            select to_char(date_trunc('day', created_at), 'YYYY-MM-DD') as day, count(*)
            from assistant_event
            where event_type = 'MESSAGE_SENT' and created_at >= :from
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> dailyMessageVolumeSince(@Param("from") Instant from);

    @Query(value = """
            select to_char(date_trunc('day', created_at), 'YYYY-MM-DD') as day, count(*)
            from assistant_event
            where event_type = 'FALLBACK_TRIGGERED' and created_at >= :from
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> dailyFallbackVolumeSince(@Param("from") Instant from);

    long countByEventTypeAndCreatedAtAfter(String eventType, Instant from);

    long countByConversationId(Long conversationId);
}
