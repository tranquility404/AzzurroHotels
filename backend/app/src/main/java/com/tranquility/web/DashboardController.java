package com.tranquility.web;

import com.tranquility.dashboard.persistence.service.DashboardAnalyticsService;
import com.tranquility.dashboard.dto.DashboardOverviewResponse;
import com.tranquility.dashboard.dto.DashboardReviewPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardAnalyticsService dashboardAnalyticsService;

    @GetMapping
    public DashboardOverviewResponse getOverview(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String propertyId
    ) {
        return dashboardAnalyticsService.getOverview(period, startDate, endDate, propertyId);
    }

    @GetMapping("/reviews")
    public DashboardReviewPageResponse getReviews(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String propertyId,
            @RequestParam(defaultValue = "all") String sentiment,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "date_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return dashboardAnalyticsService.getReviewFeed(
                startDate, endDate, propertyId, sentiment, search, sort, page, size);
    }
}
