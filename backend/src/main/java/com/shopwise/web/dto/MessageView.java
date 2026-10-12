package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MessageView(
        Long id,
        String role,
        String content,
        String intent,
        BigDecimal confidence,
        Instant createdAt,
        List<ChatBlock> blocks,
        List<ToolCallView> toolCalls,
        Map<String, Object> safety,
        String model,
        Integer latencyMs) {

    public record ToolCallView(String name, String label, String status, Map<String, Object> arguments) {
    }
}