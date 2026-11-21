package com.tranquility.dashboard.model;

import com.tranquility.dashboard.persistence.entity.Review;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public record HotelSyncReviewIdentity(
        String date,
        int score,
        String title,
        String positive,
        String negative,
        String guestName
) {

    public static Optional<HotelSyncReviewIdentity> from(JsonNode sourceData) {
        if (sourceData == null || !sourceData.isObject()) {
            return Optional.empty();
        }

        JsonNode guest = sourceData.get("guest");
        return create(
                text(sourceData, "date"),
                integer(sourceData, "score"),
                text(sourceData, "title"),
                text(sourceData, "positive"),
                text(sourceData, "negative"),
                text(guest, "name"));
    }

    public static Optional<HotelSyncReviewIdentity> from(Review review) {
        if (review == null) {
            return Optional.empty();
        }
        HotelJsonModel.Guest guest = review.getGuest();
        return create(
                review.getDate(),
                review.getScore(),
                review.getTitle(),
                review.getPositive(),
                review.getNegative(),
                guest == null ? null : guest.name());
    }

    private static Optional<HotelSyncReviewIdentity> create(
            String date,
            Integer score,
            String title,
            String positive,
            String negative,
            String guestName
    ) {
        String normalizedDate = normalize(date);
        String normalizedTitle = normalize(title);
        String normalizedPositive = normalize(positive);
        String normalizedNegative = normalize(negative);
        String normalizedGuestName = normalize(guestName);
        if (normalizedDate.isEmpty()
                || score == null
                || normalizedGuestName.isEmpty()
                || (normalizedTitle.isEmpty() && normalizedPositive.isEmpty() && normalizedNegative.isEmpty())) {
            return Optional.empty();
        }

        return Optional.of(new HotelSyncReviewIdentity(
                normalizedDate,
                score,
                normalizedTitle,
                normalizedPositive,
                normalizedNegative,
                normalizedGuestName));
    }

    private static String text(JsonNode node, String property) {
        JsonNode value = node == null ? null : node.get(property);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static Integer integer(JsonNode node, String property) {
        JsonNode value = node == null ? null : node.get(property);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT);
    }
}
