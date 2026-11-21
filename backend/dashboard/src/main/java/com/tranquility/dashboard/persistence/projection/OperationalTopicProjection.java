package com.tranquility.dashboard.persistence.projection;

public interface OperationalTopicProjection {
    String getTopic();

    Long getReviewCount();

    Long getPositiveReviewCount();

    Long getNeutralReviewCount();

    Long getNegativeReviewCount();

    Double getPositiveReviewSharePercent();

    Double getNegativeReviewSharePercent();
}
