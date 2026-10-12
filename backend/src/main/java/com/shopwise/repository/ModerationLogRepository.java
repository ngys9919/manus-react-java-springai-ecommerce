package com.shopwise.repository;

import com.shopwise.domain.ModerationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    List<ModerationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant from);

    @Query("""
            select m.category, count(m) from ModerationLog m
            where m.createdAt >= :from
            group by m.category
            order by count(m) desc
            """)
    List<Object[]> countByCategorySince(@Param("from") Instant from);
}
