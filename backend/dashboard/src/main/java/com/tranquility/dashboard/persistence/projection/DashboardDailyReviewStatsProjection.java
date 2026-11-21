package com.tranquility.dashboard.persistence.projection;

public interface DashboardDailyReviewStatsProjection {
    String getHotelId();

    String getReviewDate();

    Long getReviewCount();

    Long getScoreTotal();

    Long getPositiveCount();

    Long getNeutralCount();

    Long getNegativeCount();

    Long getReplyCount();
}
