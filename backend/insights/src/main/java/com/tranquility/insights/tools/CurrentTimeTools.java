package com.tranquility.insights.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class CurrentTimeTools {

    private final Clock clock;

    public CurrentTimeTools(Clock clock) {
        this.clock = clock;
    }

    @Tool(name = "get_current_time", description = "Return the current UTC instant and calendar date. Call this before resolving relative review periods such as this week, last week, this month, or last month.")
    public String getCurrentTime() {
        Instant now = clock.instant();
        return "Current UTC instant: " + now + "; current UTC date: " + LocalDate.ofInstant(now, ZoneOffset.UTC) + ".";
    }
}
