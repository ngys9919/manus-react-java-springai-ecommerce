package com.shopwise.web.controller;

import com.shopwise.service.AnalyticsService;
import com.shopwise.service.EscalationService;
import com.shopwise.web.dto.AnalyticsSummary;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assistant usage analytics and the escalation queue. Guarded by
 * {@code hasAnyRole('SUPPORT','ADMIN')} in the security configuration.
 */
@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights (staff)", description = "Assistant usage analytics and escalation queue. Requires SUPPORT or ADMIN.")
public class InsightsController {

    private final AnalyticsService analyticsService;
    private final EscalationService escalationService;

    public InsightsController(AnalyticsService analyticsService, EscalationService escalationService) {
        this.analyticsService = analyticsService;
        this.escalationService = escalationService;
    }

    @GetMapping("/assistant/analytics")
    @Operation(summary = "Assistant usage analytics over a rolling window")
    public AnalyticsSummary analytics(@RequestParam(defaultValue = "14") int days) {
        return analyticsService.summary(days);
    }

    @GetMapping("/assistant/escalations")
    @Operation(summary = "Recent human escalations")
    public List<EscalationView> escalations(@RequestParam(defaultValue = "20") int limit) {
        return escalationService.recent(limit);
    }

    @PostMapping("/assistant/escalations/{reference}/resolve")
    @Operation(summary = "Mark an escalation resolved")
    public EscalationView resolve(@PathVariable String reference) {
        return escalationService.resolve(reference);
    }
}