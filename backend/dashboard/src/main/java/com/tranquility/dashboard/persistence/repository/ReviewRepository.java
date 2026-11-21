package com.tranquility.dashboard.persistence.repository;

import com.tranquility.dashboard.persistence.entity.Review;
import com.tranquility.dashboard.persistence.projection.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query("select count(review.id) from Review review where review.hotel.hotelId = :hotelId")
    long countForHotel(@Param("hotelId") String hotelId);

    @Query("""
            select review.hotel.hotelId as hotelId,
                   count(review.id) as reviewCount
            from Review review
            where review.hotel.hotelId in :hotelIds
            group by review.hotel.hotelId
            """)
    List<HotelReviewCountProjection> findReviewCountsByHotelIds(@Param("hotelIds") List<String> hotelIds);

    @Query("""
            select review.hotel.hotelId as hotelId,
                   review.date as reviewDate,
                   count(review.score) as reviewCount,
                   sum(review.score) as scoreTotal,
                   count(case when review.score >= 8 then review.id else null end) as positiveCount,
                   count(case when review.score = 7 then review.id else null end) as neutralCount,
                   count(case when review.score <= 6 then review.id else null end) as negativeCount,
                   count(case when review.score is not null and review.ownerReply is not null
                                   and trim(review.ownerReply) <> '' then review.id else null end) as replyCount
            from Review review
            where review.date >= :fromDate and review.date <= :toDate
              and (:hotelId is null or review.hotel.hotelId = :hotelId)
            group by review.hotel.hotelId, review.date
            """)
    List<DashboardDailyReviewStatsProjection> findDashboardDailyStats(
            @Param("fromDate") String fromDate,
            @Param("toDate") String toDate,
            @Param("hotelId") String hotelId
    );

    @Query("""
            select review.date as reviewDate,
                   review.score as score,
                   count(review.id) as reviewCount
            from Review review
            where review.date >= :fromDate and review.date <= :toDate
              and review.score between 1 and 10
              and (:hotelId is null or review.hotel.hotelId = :hotelId)
            group by review.date, review.score
            """)
    List<DashboardTrendScoreProjection> findDashboardTrendScoreCounts(
            @Param("fromDate") String fromDate,
            @Param("toDate") String toDate,
            @Param("hotelId") String hotelId
    );

    @Query("select max(review.date) from Review review where review.date <= :today")
    String findLatestReviewDate(@Param("today") String today);

    @Query(value = """
            with topic_definitions(topic, regex_pattern, display_order) as (
                values
                    ('Cleanliness', '\\m(cleanliness|clean(ing|ed|er)?|dirty|dirt|dust|stain(ed|s)?|smell|odor|odour|mold|mould|hygiene|housekeeping|filthy|spotless|limpieza|sucio|sucia|polvo|sauberkeit|schmutzig|staub|propreté|sale|poussière)\\M', 1),
                    ('Check-in experience', '\\m(check[ -]?in|check[ -]?out|arrival|arrived|queue|registration|einchecken|anreise|enregistrement|registro|entrada)\\M', 2),
                    ('Staff / receptionist behaviour', '\\m(staff|reception(ist|ists)?|front desk|employee(s)?|rude|friendly|helpful|unhelpful|polite|attentive|professional|manager|personnel|réception|personal|recepción|mitarbeiter|rezeption)\\M', 3),
                    ('Noise', '\\m(noise|noisy|loud|quiet|sound(s)?|neighbor(s)?|neighbour(s)?|traffic|construction|music|party|thin wall(s)?|ruido|bruit|lärm|laut)\\M', 4),
                    ('Facilities', '\\m(facilit(y|ies)|amenit(y|ies)|pool|gym|spa|parking|elevator|lift|wifi|wi-fi|internet|laundry|kitchen|restaurant|bar|sauna|instalaciones|servicios|ausstattung|einrichtung)\\M', 5),
                    ('Location', '\\m(location|located|walk(able|ing)?|distance|near(by)?|close|far|transport|station|airport|beach|center|centre|area|neighborhood|neighbourhood|accessible|convenient|ubicación|emplacement|lage|standort)\\M', 6),
                    ('Room condition', '\\m(room(s)?|bed(s)?|mattress|bathroom(s)?|shower(s)?|toilet(s)?|furniture|decor|spacious|cramped|comfortable|comfort|balcony|view|window(s)?|door(s)?|habitación|cama|baño|chambre|lit|zimmer|bett)\\M', 7),
                    ('Value for money', '\\m(value|price(s)?|cost(s)?|expensive|cheap|overpriced|worth|money|rate(s)?|fee(s)?|charge(d|s)?|affordable|budget|refund|precio|caro|barato|prix|cher|preis|teuer|günstig)\\M', 8)
            ),
            filtered_reviews as (
                select review.id,
                       review.score,
                       coalesce(nullif(btrim(review.positive), ''), review.source_data ->> 'positive') as positive_text,
                       coalesce(nullif(btrim(review.negative), ''), review.source_data ->> 'negative') as negative_text,
                       concat_ws(' ',
                           review.title,
                           review.positive,
                           review.negative,
                           review.source_data ->> 'title',
                           review.source_data ->> 'positive',
                           review.source_data ->> 'negative'
                       ) as review_text
                from reviews review
                where review.date between :startDate and :endDate
                  and (:hotelId is null or review.hotel_id = :hotelId)
            ),
            sentiment_totals as (
                select count(*) filter (where score <= 6) as negative_review_count,
                       count(*) filter (where score >= 8) as positive_review_count
                from filtered_reviews
            ),
            topic_counts as (
                select definition.topic,
                       definition.display_order,
                       count(review.id) as review_count,
                       count(review.id) filter (where review.score >= 8) as positive_review_count,
                       count(review.id) filter (where review.score = 7) as neutral_review_count,
                       count(review.id) filter (where review.score <= 6) as negative_review_count
                from topic_definitions definition
                left join filtered_reviews review on (
                    (review.score <= 6 and review.negative_text ~* definition.regex_pattern)
                    or (review.score >= 8 and review.positive_text ~* definition.regex_pattern)
                    or (review.score = 7 and review.review_text ~* definition.regex_pattern)
                )
                group by definition.topic, definition.display_order
            )
            select topic_counts.topic as "topic",
                   topic_counts.review_count as "reviewCount",
                   topic_counts.positive_review_count as "positiveReviewCount",
                   topic_counts.neutral_review_count as "neutralReviewCount",
                   topic_counts.negative_review_count as "negativeReviewCount",
                   case
                       when sentiment_totals.positive_review_count = 0 then null::double precision
                       else round(100.0 * topic_counts.positive_review_count / sentiment_totals.positive_review_count, 1)::double precision
                   end as "positiveReviewSharePercent",
                   case
                       when sentiment_totals.negative_review_count = 0 then null::double precision
                       else round(100.0 * topic_counts.negative_review_count / sentiment_totals.negative_review_count, 1)::double precision
                   end as "negativeReviewSharePercent"
            from topic_counts
            cross join sentiment_totals
            order by topic_counts.negative_review_count desc,
                     topic_counts.review_count desc,
                     topic_counts.display_order
            """, nativeQuery = true)
    List<OperationalTopicProjection> findDashboardOperationalTopicCounts(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("hotelId") String hotelId
    );

    @Query(value = """
            select review.id as "id",
                   review.hotel_id as "hotelId",
                   hotel.hotel_name as "hotelName",
                   coalesce(nullif(hotel.hotel_details #>> '{location,city}', ''),
                            nullif(hotel.hotel_details #>> '{location,region}', ''),
                            nullif(hotel.hotel_details #>> '{location,country}', ''),
                            'Location unavailable') as "city",
                   coalesce(nullif(hotel.hotel_details #>> '{location,country}', ''),
                            'Location unavailable') as "country",
                   review.date as "reviewDate",
                   review.score as "score",
                   review.source_data::text as "sourceDataJson"
            from reviews review
            join hotels hotel on hotel.hotel_id = review.hotel_id
            where review.date between :startDate and :endDate
              and review.score is not null
              and (:hotelId is null or review.hotel_id = :hotelId)
              and (
                    :sentiment = 'all'
                    or (:sentiment = 'positive' and review.score >= 8)
                    or (:sentiment = 'neutral' and review.score = 7)
                    or (:sentiment = 'negative' and review.score <= 6)
              )
               and (
                     :searchText is null
                     or position(lower(:searchText) in lower(coalesce(nullif(btrim(review.guest ->> 'name'), ''),
                         nullif(btrim(review.source_data #>> '{guest,name}'), ''), 'Guest'))) > 0
                     or position(lower(:searchText) in lower(review.date)) > 0
                     or position(lower(:searchText) in lower(concat_ws(' ', review.title, review.positive,
                         review.negative, review.owner_reply, review.source_data ->> 'title',
                         review.source_data ->> 'positive', review.source_data ->> 'negative'))) > 0
                     or position(lower(:searchText) in lower(hotel.hotel_name)) > 0
                     or position(lower(:searchText) in lower(review.id::text)) > 0
               )
             order by
               case when :sort = 'date_asc' then review.date end asc,
               case when :sort = 'date_desc' then review.date end desc,
               case when :sort = 'score_asc' then review.score end asc,
               case when :sort = 'score_desc' then review.score end desc,
               case when :sort = 'reviewer_asc' then lower(coalesce(nullif(btrim(review.guest ->> 'name'), ''),
                   nullif(btrim(review.source_data #>> '{guest,name}'), ''), 'Guest')) end asc,
               review.date desc, review.id desc
            """, countQuery = """
            select count(*)
            from reviews review
            join hotels hotel on hotel.hotel_id = review.hotel_id
            where review.date between :startDate and :endDate
              and review.score is not null
              and (:hotelId is null or review.hotel_id = :hotelId)
              and (
                    :sentiment = 'all'
                    or (:sentiment = 'positive' and review.score >= 8)
                    or (:sentiment = 'neutral' and review.score = 7)
                    or (:sentiment = 'negative' and review.score <= 6)
              )
               and (
                     :searchText is null
                     or position(lower(:searchText) in lower(coalesce(nullif(btrim(review.guest ->> 'name'), ''),
                         nullif(btrim(review.source_data #>> '{guest,name}'), ''), 'Guest'))) > 0
                     or position(lower(:searchText) in lower(review.date)) > 0
                     or position(lower(:searchText) in lower(concat_ws(' ', review.title, review.positive,
                         review.negative, review.owner_reply, review.source_data ->> 'title',
                         review.source_data ->> 'positive', review.source_data ->> 'negative'))) > 0
                     or position(lower(:searchText) in lower(hotel.hotel_name)) > 0
                     or position(lower(:searchText) in lower(review.id::text)) > 0
               )
            """, nativeQuery = true)
    Page<DashboardReviewProjection> findDashboardReviewFeed(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("hotelId") String hotelId,
            @Param("sentiment") String sentiment,
            @Param("searchText") String searchText,
            @Param("sort") String sort,
            Pageable pageable
    );

    @Query(value = """
            select review.*
            from reviews review
            where review.hotel_id = :hotelId
              and (:startDate = '' or review.date >= :startDate)
              and (:endDate = '' or review.date <= :endDate)
              and (
                    :sentiment = 'all'
                    or (:sentiment = 'positive' and review.score >= 8)
                    or (:sentiment = 'neutral' and review.score = 7)
                    or (:sentiment = 'negative' and review.score <= 6)
              )
              and (
                    :searchText = ''
                    or position(lower(:searchText) in lower(coalesce(
                        nullif(btrim(review.guest ->> 'name'), ''),
                        nullif(btrim(review.source_data #>> '{guest,name}'), ''),
                        'Guest'))) > 0
                    or position(lower(:searchText) in lower(coalesce(review.date, ''))) > 0
                    or position(lower(:searchText) in lower(concat_ws(' ',
                        review.source_review_id,
                        review.title,
                        review.positive,
                        review.negative,
                        review.owner_reply,
                        review.source_data ->> 'id',
                        review.source_data ->> 'title',
                        review.source_data ->> 'positive',
                        review.source_data ->> 'negative',
                        review.source_data ->> 'owner_reply'))) > 0
                    or position(lower(:searchText) in lower(review.id::text)) > 0
              )
            order by
                case when :sort = 'date_asc' then review.date end asc,
                case when :sort = 'date_desc' then review.date end desc,
                case when :sort = 'score_asc' then review.score end asc,
                case when :sort = 'score_desc' then review.score end desc,
                case when :sort = 'reviewer_asc' then lower(coalesce(
                    nullif(btrim(review.guest ->> 'name'), ''),
                    nullif(btrim(review.source_data #>> '{guest,name}'), ''),
                    'Guest')) end asc,
                review.date desc,
                review.id desc
            """, countQuery = """
            select count(*)
            from reviews review
            where review.hotel_id = :hotelId
              and (:startDate = '' or review.date >= :startDate)
              and (:endDate = '' or review.date <= :endDate)
              and (
                    :sentiment = 'all'
                    or (:sentiment = 'positive' and review.score >= 8)
                    or (:sentiment = 'neutral' and review.score = 7)
                    or (:sentiment = 'negative' and review.score <= 6)
              )
              and (
                    :searchText = ''
                    or position(lower(:searchText) in lower(coalesce(
                        nullif(btrim(review.guest ->> 'name'), ''),
                        nullif(btrim(review.source_data #>> '{guest,name}'), ''),
                        'Guest'))) > 0
                    or position(lower(:searchText) in lower(coalesce(review.date, ''))) > 0
                    or position(lower(:searchText) in lower(concat_ws(' ',
                        review.source_review_id,
                        review.title,
                        review.positive,
                        review.negative,
                        review.owner_reply,
                        review.source_data ->> 'id',
                        review.source_data ->> 'title',
                        review.source_data ->> 'positive',
                        review.source_data ->> 'negative',
                        review.source_data ->> 'owner_reply'))) > 0
                    or position(lower(:searchText) in lower(review.id::text)) > 0
              )
            """, nativeQuery = true)
    Page<Review> searchHotelReviews(
            @Param("hotelId") String hotelId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("sentiment") String sentiment,
            @Param("searchText") String searchText,
            @Param("sort") String sort,
            Pageable pageable
    );

    @Query("""
            select review.sourceReviewId
            from Review review
            where review.hotel.hotelId = :hotelId
              and review.sourceReviewId in :sourceReviewIds
            """)
    List<String> findExistingSourceReviewIds(
            @Param("hotelId") String hotelId,
            @Param("sourceReviewIds") List<String> sourceReviewIds
    );

    @Query("""
            select review
            from Review review
            where review.hotel.hotelId = :hotelId
              and review.date in :reviewDates
              and review.score in :scores
            """)
    List<Review> findReviewIdentityCandidates(
            @Param("hotelId") String hotelId,
            @Param("reviewDates") List<String> reviewDates,
            @Param("scores") List<Integer> scores
    );

    @Query("select min(review.sourcePageNumber) from Review review where review.hotel.hotelId = :hotelId")
    Integer findMinimumSourcePageNumberForHotel(@Param("hotelId") String hotelId);

    @Query("""
            select review
            from Review review
            where review.hotel.hotelId = :hotelId
            order by review.sourcePageNumber asc, review.position asc
            """)
    List<Review> findFirstPageForHotel(@Param("hotelId") String hotelId, Pageable pageable);

    @Query("""
            select review
            from Review review
            where review.hotel.hotelId = :hotelId
              and (review.sourcePageNumber > :sourcePageNumber
                   or (review.sourcePageNumber = :sourcePageNumber and review.position > :position))
            order by review.sourcePageNumber asc, review.position asc
            """)
    List<Review> findNextPageForHotel(
            @Param("hotelId") String hotelId,
            @Param("sourcePageNumber") int sourcePageNumber,
            @Param("position") int position,
            Pageable pageable
    );

}
