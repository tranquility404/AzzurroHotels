package com.tranquility.insights.tools;

import com.tranquility.insights.model.analytics.ReviewInsightsService;
import com.tranquility.insights.persistence.service.HotelProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ReviewInsightsTools {

    private final ReviewInsightsService reviewInsightsService;
    private final HotelProfileService hotelProfileService;

    public ReviewInsightsTools(
            ReviewInsightsService reviewInsightsService,
            HotelProfileService hotelProfileService
    ) {
        this.reviewInsightsService = reviewInsightsService;
        this.hotelProfileService = hotelProfileService;
    }

    @Tool(name = "summarize_portfolio_performance", description = "Return compact dashboard-style review performance across the hotel portfolio for one explicit period, including overall totals/comparison and a few per-property snapshots. Use this when the guest asks generally about overall or this week's/month's hotel performance without naming a property. Do not call for a broad facilities/rooms/policy question. Before using a relative period, call get_current_time.")
    public String summarizePortfolioPerformance(
            @ToolParam(description = "Required period: this_week, last_week, last_two_weeks, this_month, last_month, last_30_days, last_90_days, or YYYY-MM-DD to YYYY-MM-DD.") String period
    ) {
        return reviewInsightsService.summarizePortfolioPerformance(period);
    }

    @Tool(name = "summarize_performance", description = "Return review performance for exactly one identified hotel and an explicitly requested period. Use this only when the guest names or selects a specific hotel. Do not call if either hotel or period is missing; ask the user first. Before using a relative period, call get_current_time. Includes that hotel's stored category ratings.")
    public String summarizePerformance(
            @ToolParam(description = "One exact hotel id returned by find_hotels. Never use blank, all, or multiple ids.") String hotelId,
            @ToolParam(description = "Required period: this_week, last_week, last_two_weeks, this_month, last_month, last_30_days, last_90_days, or YYYY-MM-DD to YYYY-MM-DD.") String period
    ) {
        return reviewInsightsService.summarizePerformance(hotelId, period)
                + "\n" + hotelProfileService.getRatings(hotelId);
    }

    @Tool(name = "compare_topic_trends", description = "Return aggregate praise and complaint topic counts/shares for one identified hotel and an explicitly requested period. Do not call if either hotel or period is missing; ask the user first. Before using a relative period, call get_current_time. Use these aggregates—not sampled excerpts—to determine what is most common.")
    public String compareTopicTrends(
            @ToolParam(description = "One exact hotel id returned by find_hotels. Never use blank, all, or multiple ids.") String hotelId,
            @ToolParam(description = "Required period: this_week, last_week, last_two_weeks, this_month, last_month, last_30_days, last_90_days, or YYYY-MM-DD to YYYY-MM-DD.") String period
    ) {
        return reviewInsightsService.compareTopicTrends(hotelId, period);
    }

    @Tool(name = "find_review_evidence", description = "Return at most eight review examples, including each stored review id, for one identified hotel and an explicitly requested period. Use an optional reviewer-name filter and an exact one-day period to find a particular review. Do not call if hotel or period is missing; ask the user first. Before using a relative period, call get_current_time. Not a full review list or prevalence count. Reviewer names are included only when explicitly requested or used as the requested filter.")
    public String findReviewEvidence(
            @ToolParam(description = "One exact hotel id returned by find_hotels. Never use blank, all, or multiple ids.") String hotelId,
            @ToolParam(description = "Required period: this_week, last_week, last_two_weeks, this_month, last_month, last_30_days, last_90_days, or YYYY-MM-DD to YYYY-MM-DD.") String period,
            @ToolParam(description = "all, positive, negative, or neutral.") String sentiment,
            @ToolParam(description = "A topic such as Cleanliness, Location, or Food and dining, or empty for no topic filter.") String topic,
            @ToolParam(description = "Optional exact or partial reviewer-name filter, such as Jessie. Empty when no reviewer is specified.") String reviewerName,
            @ToolParam(description = "True to keep only reviews containing suggestion-language patterns.") boolean suggestionsOnly,
            @ToolParam(description = "True only when the user explicitly asks for reviewer names.") boolean includeReviewerNames
    ) {
        return reviewInsightsService.findReviewEvidence(
                hotelId, period, sentiment, topic, reviewerName, suggestionsOnly, includeReviewerNames);
    }
}
