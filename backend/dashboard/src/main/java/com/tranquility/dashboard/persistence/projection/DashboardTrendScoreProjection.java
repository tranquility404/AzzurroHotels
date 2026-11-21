package com.tranquility.dashboard.persistence.projection;

public interface DashboardTrendScoreProjection {
    String getReviewDate();

    Integer getScore();

    Long getReviewCount();
}
