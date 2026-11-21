package com.tranquility.insights.persistence.repository;

import com.tranquility.insights.model.analytics.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ReviewInsightsRepository {

    private static final String REVIEW_TEXT_CTES = """
            with review_text as (
                select review.id,
                       review.hotel_id,
                       hotel.hotel_name,
                       review.date,
                       review.score,
                       coalesce(nullif(btrim(review.title), ''), review.source_data ->> 'title') as title_text,
                       coalesce(nullif(btrim(review.positive), ''), review.source_data ->> 'positive') as positive_text,
                       coalesce(nullif(btrim(review.negative), ''), review.source_data ->> 'negative') as negative_text,
                       case
                            when (:includeReviewerNames or cast(:reviewerName as text) is not null)
                            and not coalesce(review.guest -> 'is_anonymous' = 'true'::jsonb, false)
                            and not coalesce(review.source_data -> 'guest' -> 'is_anonymous' = 'true'::jsonb, false)
                           then coalesce(nullif(btrim(review.guest ->> 'name'), ''),
                                         nullif(btrim(review.source_data -> 'guest' ->> 'name'), ''))
                           else null
                       end as reviewer_name
                from reviews review
                join hotels hotel on hotel.hotel_id = review.hotel_id
                where review.date between :fromDate and :toDate
                  and review.score is not null
                   and review.hotel_id = :hotelId
            ), selected_text as (
                select *,
                       case
                           when score <= 6 then coalesce(negative_text, positive_text, title_text)
                           when score >= 8 then coalesce(positive_text, negative_text, title_text)
                           else coalesce(title_text, positive_text, negative_text)
                       end as sentiment_text
                from review_text
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReviewInsightsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HotelPerformance> findHotelPerformance(ReviewPeriod period, String hotelId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("currentStart", period.currentStart().toString())
                .addValue("currentEnd", period.currentEnd().toString())
                .addValue("previousStart", period.previousStart().toString())
                .addValue("previousEnd", period.previousEnd().toString())
                .addValue("hotelId", hotelId);

        String sql = """
                select hotel.hotel_id as hotel_id,
                       hotel.hotel_name as hotel_name,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd) as current_review_count,
                       coalesce(sum(review.score) filter (where review.date between :currentStart and :currentEnd), 0) as current_score_total,
                       round(avg(review.score) filter (where review.date between :currentStart and :currentEnd), 2)::double precision as current_average_score,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score >= 8) as current_positive_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score = 7) as current_neutral_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score <= 6) as current_negative_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd
                           and nullif(btrim(review.owner_reply), '') is not null) as current_reply_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd) as previous_review_count,
                       coalesce(sum(review.score) filter (where review.date between :previousStart and :previousEnd), 0) as previous_score_total,
                       round(avg(review.score) filter (where review.date between :previousStart and :previousEnd), 2)::double precision as previous_average_score,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score >= 8) as previous_positive_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score = 7) as previous_neutral_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score <= 6) as previous_negative_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd
                           and nullif(btrim(review.owner_reply), '') is not null) as previous_reply_count
                from hotels hotel
                left join reviews review
                  on review.hotel_id = hotel.hotel_id
                 and review.score is not null
                 and review.date between :previousStart and :currentEnd
                where hotel.hotel_id = :hotelId
                group by hotel.hotel_id, hotel.hotel_name
                """;

        return jdbcTemplate.query(sql, parameters, HOTEL_PERFORMANCE_ROW_MAPPER);
    }

    /** Returns compact per-property metrics to build the dashboard-style collection summary. */
    public List<HotelPerformance> findPortfolioPerformance(ReviewPeriod period) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("currentStart", period.currentStart().toString())
                .addValue("currentEnd", period.currentEnd().toString())
                .addValue("previousStart", period.previousStart().toString())
                .addValue("previousEnd", period.previousEnd().toString());

        return jdbcTemplate.query("""
                select hotel.hotel_id as hotel_id,
                       hotel.hotel_name as hotel_name,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd) as current_review_count,
                       coalesce(sum(review.score) filter (where review.date between :currentStart and :currentEnd), 0) as current_score_total,
                       round(avg(review.score) filter (where review.date between :currentStart and :currentEnd), 2)::double precision as current_average_score,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score >= 8) as current_positive_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score = 7) as current_neutral_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score <= 6) as current_negative_count,
                       count(review.id) filter (where review.date between :currentStart and :currentEnd
                           and nullif(btrim(review.owner_reply), '') is not null) as current_reply_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd) as previous_review_count,
                       coalesce(sum(review.score) filter (where review.date between :previousStart and :previousEnd), 0) as previous_score_total,
                       round(avg(review.score) filter (where review.date between :previousStart and :previousEnd), 2)::double precision as previous_average_score,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score >= 8) as previous_positive_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score = 7) as previous_neutral_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score <= 6) as previous_negative_count,
                       count(review.id) filter (where review.date between :previousStart and :previousEnd
                           and nullif(btrim(review.owner_reply), '') is not null) as previous_reply_count
                from hotels hotel
                join reviews review on review.hotel_id = hotel.hotel_id
                    and review.score is not null
                    and review.date between :previousStart and :currentEnd
                group by hotel.hotel_id, hotel.hotel_name
                having count(review.id) filter (where review.date between :currentStart and :currentEnd) > 0
                    or count(review.id) filter (where review.date between :previousStart and :previousEnd) > 0
                order by current_review_count desc, hotel.hotel_name asc
                """, parameters, HOTEL_PERFORMANCE_ROW_MAPPER);
    }

    public List<TopicTrend> findTopicTrends(ReviewPeriod period, String hotelId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("currentStart", period.currentStart().toString())
                .addValue("currentEnd", period.currentEnd().toString())
                .addValue("previousStart", period.previousStart().toString())
                .addValue("previousEnd", period.previousEnd().toString())
                .addValue("hotelId", hotelId);

        return jdbcTemplate.query(TOPIC_TREND_SQL, parameters, (resultSet, rowNumber) -> new TopicTrend(
                resultSet.getString("topic"),
                resultSet.getLong("current_positive_count"),
                resultSet.getLong("current_negative_count"),
                resultSet.getLong("previous_positive_count"),
                resultSet.getLong("previous_negative_count"),
                nullableDouble(resultSet, "current_positive_share_percent"),
                nullableDouble(resultSet, "current_negative_share_percent"),
                nullableDouble(resultSet, "previous_positive_share_percent"),
                nullableDouble(resultSet, "previous_negative_share_percent")));
    }

    public long countSuggestionReviews(
            ReviewPeriod period,
            String hotelId,
            String sentiment,
            String topicPattern,
            String reviewerName
    ) {
        MapSqlParameterSource parameters = periodParameters(period)
                .addValue("hotelId", hotelId)
                .addValue("sentiment", sentiment)
                .addValue("topicPattern", topicPattern)
                .addValue("reviewerName", reviewerName)
                .addValue("includeReviewerNames", false)
                .addValue("suggestionPattern", ReviewTopicCatalog.SUGGESTION_PATTERN);
        Long count = jdbcTemplate.queryForObject(REVIEW_TEXT_CTES + """
                select count(*)
                from selected_text
                where (:sentiment = 'all'
                       or (:sentiment = 'positive' and score >= 8)
                       or (:sentiment = 'neutral' and score = 7)
                       or (:sentiment = 'negative' and score <= 6))
                  and (cast(:topicPattern as text) is null or sentiment_text ~* :topicPattern)
                  and (cast(:reviewerName as text) is null
                       or position(lower(cast(:reviewerName as text)) in lower(reviewer_name)) > 0)
                  and sentiment_text ~* :suggestionPattern
                """, parameters, Long.class);
        return count == null ? 0 : count;
    }

    public List<ReviewExcerpt> findReviewExcerpts(
            ReviewPeriod period,
            String hotelId,
            String sentiment,
            String topicPattern,
            String reviewerName,
            boolean suggestionsOnly,
            boolean includeReviewerNames
    ) {
        MapSqlParameterSource parameters = periodParameters(period)
                .addValue("hotelId", hotelId)
                .addValue("sentiment", sentiment)
                .addValue("topicPattern", topicPattern)
                .addValue("reviewerName", reviewerName)
                .addValue("includeReviewerNames", includeReviewerNames)
                .addValue("suggestionsOnly", suggestionsOnly)
                .addValue("suggestionPattern", ReviewTopicCatalog.SUGGESTION_PATTERN);

        return jdbcTemplate.query(REVIEW_TEXT_CTES + """
                select id::text as review_id,
                       hotel_name,
                       date,
                       score,
                       reviewer_name,
                       left(btrim(sentiment_text), 480) as excerpt,
                       length(btrim(sentiment_text)) > 480 as excerpt_truncated
                from selected_text
                where (:sentiment = 'all'
                       or (:sentiment = 'positive' and score >= 8)
                       or (:sentiment = 'neutral' and score = 7)
                       or (:sentiment = 'negative' and score <= 6))
                  and (cast(:topicPattern as text) is null or sentiment_text ~* :topicPattern)
                  and (cast(:reviewerName as text) is null
                       or position(lower(cast(:reviewerName as text)) in lower(reviewer_name)) > 0)
                  and (not :suggestionsOnly or sentiment_text ~* :suggestionPattern)
                  and sentiment_text is not null
                  and btrim(sentiment_text) <> ''
                order by date desc, id desc
                limit 8
                """, parameters, (resultSet, rowNumber) -> new ReviewExcerpt(
                resultSet.getString("review_id"),
                resultSet.getString("hotel_name"),
                LocalDate.parse(resultSet.getString("date")),
                resultSet.getInt("score"),
                resultSet.getString("reviewer_name"),
                resultSet.getString("excerpt") + (resultSet.getBoolean("excerpt_truncated") ? "…" : "")));
    }

    private static MapSqlParameterSource periodParameters(ReviewPeriod period) {
        return new MapSqlParameterSource()
                .addValue("fromDate", period.currentStart().toString())
                .addValue("toDate", period.currentEnd().toString());
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static final RowMapper<HotelPerformance> HOTEL_PERFORMANCE_ROW_MAPPER = (resultSet, rowNumber) ->
            new HotelPerformance(
                    resultSet.getString("hotel_id"),
                    resultSet.getString("hotel_name"),
                    resultSet.getLong("current_review_count"),
                    resultSet.getLong("current_score_total"),
                    nullableDouble(resultSet, "current_average_score"),
                    resultSet.getLong("current_positive_count"),
                    resultSet.getLong("current_neutral_count"),
                    resultSet.getLong("current_negative_count"),
                    resultSet.getLong("current_reply_count"),
                    resultSet.getLong("previous_review_count"),
                    resultSet.getLong("previous_score_total"),
                    nullableDouble(resultSet, "previous_average_score"),
                    resultSet.getLong("previous_positive_count"),
                    resultSet.getLong("previous_neutral_count"),
                    resultSet.getLong("previous_negative_count"),
                    resultSet.getLong("previous_reply_count"));

    private static final String TOPIC_TREND_SQL = topicTrendSql();

    private static String topicTrendSql() {
        StringBuilder sql = new StringBuilder("""
                with topic_definitions(topic, regex_pattern, display_order) as (
                    values
                """);
        List<String> definitions = new ArrayList<>();
        for (int index = 0; index < ReviewTopicCatalog.TOPICS.size(); index++) {
            ReviewTopicCatalog.Topic topic = ReviewTopicCatalog.TOPICS.get(index);
            String escapedTopic = topic.name().replace("'", "''");
            String escapedPattern = topic.regex().replace("'", "''");
            definitions.add("('" + escapedTopic + "', '" + escapedPattern + "', " + (index + 1) + ")");
        }
        sql.append(String.join(",\n", definitions));
        sql.append("""
                ), review_text as (
                    select review.id,
                           review.date,
                           review.score,
                           coalesce(nullif(btrim(review.title), ''), review.source_data ->> 'title') as title_text,
                           coalesce(nullif(btrim(review.positive), ''), review.source_data ->> 'positive') as positive_text,
                           coalesce(nullif(btrim(review.negative), ''), review.source_data ->> 'negative') as negative_text
                    from reviews review
                    where review.date between :previousStart and :currentEnd
                      and review.hotel_id = :hotelId
                      and review.score is not null
                ), filtered_reviews as (
                    select id,
                           date,
                           score,
                           positive_text,
                           negative_text,
                           concat_ws(' ', title_text, positive_text, negative_text) as all_text
                    from review_text
                ), sentiment_totals as (
                    select count(*) filter (where date between :currentStart and :currentEnd and score >= 8) as current_positive_total,
                           count(*) filter (where date between :currentStart and :currentEnd and score <= 6) as current_negative_total,
                           count(*) filter (where date between :previousStart and :previousEnd and score >= 8) as previous_positive_total,
                           count(*) filter (where date between :previousStart and :previousEnd and score <= 6) as previous_negative_total
                    from filtered_reviews
                ), topic_counts as (
                    select definition.topic,
                           definition.display_order,
                           count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score >= 8) as current_positive_count,
                           count(review.id) filter (where review.date between :currentStart and :currentEnd and review.score <= 6) as current_negative_count,
                           count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score >= 8) as previous_positive_count,
                           count(review.id) filter (where review.date between :previousStart and :previousEnd and review.score <= 6) as previous_negative_count
                    from topic_definitions definition
                    left join filtered_reviews review on (
                        (review.score <= 6 and review.negative_text ~* definition.regex_pattern)
                        or (review.score >= 8 and review.positive_text ~* definition.regex_pattern)
                        or (review.score = 7 and review.all_text ~* definition.regex_pattern)
                    )
                    group by definition.topic, definition.display_order
                )
                select topic_counts.topic as topic,
                       topic_counts.current_positive_count,
                       topic_counts.current_negative_count,
                       topic_counts.previous_positive_count,
                       topic_counts.previous_negative_count,
                       case when sentiment_totals.current_positive_total = 0 then null
                            else round(100.0 * topic_counts.current_positive_count / sentiment_totals.current_positive_total, 1)::double precision end as current_positive_share_percent,
                       case when sentiment_totals.current_negative_total = 0 then null
                            else round(100.0 * topic_counts.current_negative_count / sentiment_totals.current_negative_total, 1)::double precision end as current_negative_share_percent,
                       case when sentiment_totals.previous_positive_total = 0 then null
                            else round(100.0 * topic_counts.previous_positive_count / sentiment_totals.previous_positive_total, 1)::double precision end as previous_positive_share_percent,
                       case when sentiment_totals.previous_negative_total = 0 then null
                            else round(100.0 * topic_counts.previous_negative_count / sentiment_totals.previous_negative_total, 1)::double precision end as previous_negative_share_percent
                from topic_counts
                cross join sentiment_totals
                order by topic_counts.current_negative_count desc,
                         topic_counts.current_positive_count desc,
                         topic_counts.display_order
                """);
        return sql.toString();
    }
}
