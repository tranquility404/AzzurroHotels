"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, CircleAlert, Search } from "lucide-react";
import SectionToggle from "./section-toggle";
import styles from "./page.module.scss";

type HotelReview = {
  id?: string | number;
  reviewId?: string;
  score?: number;
  title?: string;
  positive?: string;
  negative?: string;
  language?: string;
  date?: string;
  helpful_votes?: number;
  guest?: { name?: string; country?: string; is_anonymous?: boolean };
  stay?: { nights?: number; room_name?: string; status?: string };
  owner_reply?: string | null;
};

type ReviewSentiment = "all" | "positive" | "neutral" | "negative";
type ReviewSort = "date_desc" | "date_asc" | "score_desc" | "score_asc" | "reviewer_asc";

type ReviewSearchFilters = {
  search: string;
  startDate: string;
  endDate: string;
  sentiment: ReviewSentiment;
  sort: ReviewSort;
  page: number;
};

type HotelReviewPage = {
  data: HotelReview[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

type ReviewLoadState = HotelReviewPage & {
  key: string;
  status: "loading" | "ready" | "error";
  message?: string;
};

type HotelMetadata = {
  hotelName?: string;
  hotelDetails?: { name?: string };
};

const REVIEWS_PER_PAGE = 20;
const REVIEW_TIME_UNITS = [
  { seconds: 365 * 24 * 60 * 60, singular: "yr", plural: "yrs" },
  { seconds: 30 * 24 * 60 * 60, singular: "mo", plural: "mos" },
  { seconds: 24 * 60 * 60, singular: "day", plural: "days" },
  { seconds: 60 * 60, singular: "hr", plural: "hrs" },
  { seconds: 60, singular: "min", plural: "mins" },
  { seconds: 1, singular: "sec", plural: "secs" },
] as const;

const languageNames: Record<string, string> = {
  en: "English",
  es: "Spanish",
  fr: "French",
  ja: "Japanese",
  ko: "Korean",
};

function getReviewErrorMessage(payload: unknown, fallback = "These reviews could not be loaded. Please try again."): string {
  if (typeof payload === "object" && payload !== null) {
    const body = payload as { message?: unknown; detail?: unknown };
    if (typeof body.message === "string" && body.message.trim()) return body.message;
    if (typeof body.detail === "string" && body.detail.trim()) return body.detail;
  }
  return fallback;
}

async function fetchHotelReviewPage(
  hotelId: string,
  filters: ReviewSearchFilters,
  signal: AbortSignal,
): Promise<HotelReviewPage> {
  const query = new URLSearchParams({ page: String(filters.page), size: String(REVIEWS_PER_PAGE) });
  if (filters.search.trim()) query.set("search", filters.search.trim());
  if (filters.startDate) query.set("startDate", filters.startDate);
  if (filters.endDate) query.set("endDate", filters.endDate);
  query.set("sentiment", filters.sentiment);
  query.set("sort", filters.sort);

  const response = await fetch(`/api/hotels/${encodeURIComponent(hotelId)}/reviews/search?${query}`, {
    cache: "no-store",
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw new Error(getReviewErrorMessage(payload));
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    throw new Error("The reviews endpoint returned an unexpected response.");
  }

  const body = payload as {
    data?: unknown;
    page?: unknown;
    size?: unknown;
    totalElements?: unknown;
    totalPages?: unknown;
    hasNext?: unknown;
  };
  if (!Array.isArray(body.data)
    || typeof body.page !== "number"
    || typeof body.size !== "number"
    || typeof body.totalElements !== "number"
    || typeof body.totalPages !== "number"
    || typeof body.hasNext !== "boolean"
  ) {
    throw new Error("The reviews endpoint returned an unexpected response.");
  }

  return {
    data: body.data as HotelReview[],
    page: body.page,
    size: body.size,
    totalElements: body.totalElements,
    totalPages: body.totalPages,
    hasNext: body.hasNext,
  };
}

function formatReviewDate(value?: string): string {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Date unavailable";
  return new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function formatReviewTimeAgo(value?: string): string {
  if (!value) return "Date unavailable";
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) return "Date unavailable";

  let remainingSeconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
  const parts: string[] = [];
  for (const unit of REVIEW_TIME_UNITS) {
    const amount = Math.floor(remainingSeconds / unit.seconds);
    if (amount === 0) continue;

    parts.push(`${amount} ${amount === 1 ? unit.singular : unit.plural}`);
    remainingSeconds -= amount * unit.seconds;
    if (parts.length === 2) break;
  }

  return `${parts.length > 0 ? parts.join(" ") : "0 secs"} ago`;
}

function getInitials(name?: string): string {
  if (!name) return "G";
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

export default function HotelReviewsView({ hotelId }: { hotelId: string }) {
  const [hotelName, setHotelName] = useState(() => `Hotel ${hotelId}`);
  const [reviewSearch, setReviewSearch] = useState("");
  const [reviewStartDate, setReviewStartDate] = useState("");
  const [reviewEndDate, setReviewEndDate] = useState("");
  const [reviewSentiment, setReviewSentiment] = useState<ReviewSentiment>("all");
  const [reviewSort, setReviewSort] = useState<ReviewSort>("date_desc");
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewsExpanded, setReviewsExpanded] = useState(true);
  const [reviewAttempt, setReviewAttempt] = useState(0);
  const [reviewState, setReviewState] = useState<ReviewLoadState>({
    key: "",
    status: "loading",
    data: [],
    page: 0,
    size: REVIEWS_PER_PAGE,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  });

  useEffect(() => {
    const controller = new AbortController();
    void fetch(`/api/hotels/${encodeURIComponent(hotelId)}`, { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        const payload: unknown = await response.json().catch(() => null);
        if (!response.ok || typeof payload !== "object" || payload === null || Array.isArray(payload)) return;
        const metadata = payload as HotelMetadata;
        setHotelName(metadata.hotelName ?? metadata.hotelDetails?.name ?? `Hotel ${hotelId}`);
      })
      .catch(() => {
        // Keep the route ID as the fallback title if hotel metadata is unavailable.
      });
    return () => controller.abort();
  }, [hotelId]);

  const reviewRequestKey = JSON.stringify([
    hotelId,
    reviewSearch.trim(),
    reviewStartDate,
    reviewEndDate,
    reviewSentiment,
    reviewSort,
    reviewPage,
    reviewAttempt,
  ]);

  useEffect(() => {
    const controller = new AbortController();
    const filters: ReviewSearchFilters = {
      search: reviewSearch.trim(),
      startDate: reviewStartDate,
      endDate: reviewEndDate,
      sentiment: reviewSentiment,
      sort: reviewSort,
      page: reviewPage,
    };
    const key = reviewRequestKey;
    const timeout = window.setTimeout(() => {
      void fetchHotelReviewPage(hotelId, filters, controller.signal)
        .then((page) => {
          if (!controller.signal.aborted) setReviewState({ key, status: "ready", ...page });
        })
        .catch((cause: unknown) => {
          if (controller.signal.aborted) return;
          setReviewState({
            key,
            status: "error",
            data: [],
            page: reviewPage,
            size: REVIEWS_PER_PAGE,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            message: cause instanceof Error ? cause.message : "These reviews could not be loaded.",
          });
        });
    }, reviewSearch.trim() ? 250 : 0);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [
    hotelId,
    reviewSearch,
    reviewStartDate,
    reviewEndDate,
    reviewSentiment,
    reviewSort,
    reviewPage,
    reviewAttempt,
    reviewRequestKey,
  ]);

  const currentReviewState = reviewState.key === reviewRequestKey
    ? reviewState
    : {
        key: reviewRequestKey,
        status: "loading" as const,
        data: [],
        page: reviewPage,
        size: REVIEWS_PER_PAGE,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      };
  const reviews = currentReviewState.data;
  const reviewsLoading = currentReviewState.status === "loading";
  const reviewsError = currentReviewState.status === "error"
    ? currentReviewState.message ?? "These reviews could not be loaded."
    : null;

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <Link className={styles.backLink} href={`/hotels/${encodeURIComponent(hotelId)}`}>
          <ArrowLeft size={15} aria-hidden="true" /> Back to {hotelName}
        </Link>

        <header className={styles.reviewsPageHeader}>
          <div>
            <p>Guest experiences</p>
            <h1 id="reviews-heading">Reviews</h1>
            <span>For <strong>{hotelName}</strong></span>
          </div>
          <div className={styles.reviewsPageActions}>
            <span className={styles.sectionCount}>
              {currentReviewState.totalElements.toLocaleString()} matching reviews
            </span>
            <SectionToggle
              label="reviews"
              controls="review-page-content"
              expanded={reviewsExpanded}
              onToggle={() => setReviewsExpanded((expanded) => !expanded)}
            />
          </div>
        </header>

        <section
          className={styles.reviewPageSection}
          id="review-page-content"
          aria-labelledby="reviews-heading"
          hidden={!reviewsExpanded}
        >
          <div className={styles.reviewFilters} role="group" aria-label="Search and filter hotel reviews">
            <label className={`${styles.reviewFilter} ${styles.reviewSearchField}`} htmlFor="review-search">
              <span>Search reviews</span>
              <div className={styles.reviewSearchInput}>
                <Search size={16} aria-hidden="true" />
                <input
                  id="review-search"
                  type="search"
                  value={reviewSearch}
                  onChange={(event) => {
                    setReviewSearch(event.target.value);
                    setReviewPage(0);
                  }}
                  maxLength={120}
                  placeholder="Name, text, date, or review ID"
                />
              </div>
            </label>
            <label className={styles.reviewFilter} htmlFor="review-start-date">
              <span>From</span>
              <input
                id="review-start-date"
                type="date"
                value={reviewStartDate}
                max={reviewEndDate || undefined}
                onChange={(event) => {
                  setReviewStartDate(event.target.value);
                  setReviewPage(0);
                }}
              />
            </label>
            <label className={styles.reviewFilter} htmlFor="review-end-date">
              <span>To</span>
              <input
                id="review-end-date"
                type="date"
                value={reviewEndDate}
                min={reviewStartDate || undefined}
                onChange={(event) => {
                  setReviewEndDate(event.target.value);
                  setReviewPage(0);
                }}
              />
            </label>
            <label className={styles.reviewFilter} htmlFor="review-sentiment">
              <span>Rating</span>
              <select
                id="review-sentiment"
                value={reviewSentiment}
                onChange={(event) => {
                  setReviewSentiment(event.target.value as ReviewSentiment);
                  setReviewPage(0);
                }}
              >
                <option value="all">All ratings</option>
                <option value="positive">Positive · 8–10</option>
                <option value="neutral">Neutral · 7</option>
                <option value="negative">Negative · 1–6</option>
              </select>
            </label>
            <label className={styles.reviewFilter} htmlFor="review-sort">
              <span>Sort by</span>
              <select
                id="review-sort"
                value={reviewSort}
                onChange={(event) => {
                  setReviewSort(event.target.value as ReviewSort);
                  setReviewPage(0);
                }}
              >
                <option value="date_desc">Newest first</option>
                <option value="date_asc">Oldest first</option>
                <option value="score_desc">Highest score</option>
                <option value="score_asc">Lowest score</option>
                <option value="reviewer_asc">Reviewer A–Z</option>
              </select>
            </label>
            <button
              className={styles.resetReviewFilters}
              type="button"
              onClick={() => {
                setReviewSearch("");
                setReviewStartDate("");
                setReviewEndDate("");
                setReviewSentiment("all");
                setReviewSort("date_desc");
                setReviewPage(0);
              }}
            >
              Clear filters
            </button>
          </div>

          {reviews.length === 0 && reviewsLoading ? (
            <p className={styles.emptyReviews} role="status">Loading guest reviews…</p>
          ) : reviews.length === 0 && reviewsError ? (
            <div className={styles.reviewLoadError} role="alert">
              <CircleAlert size={16} />
              <span>{reviewsError}</span>
              <button type="button" onClick={() => setReviewAttempt((attempt) => attempt + 1)}>Try again</button>
            </div>
          ) : reviews.length === 0 ? (
            <p className={styles.emptyReviews}>No reviews match these filters. Try a wider date range or clear the search.</p>
          ) : (
            <>
              <div className={styles.reviewList}>
                {reviews.map((review, index) => {
                  const guestName = review.guest?.is_anonymous ? "Anonymous guest" : review.guest?.name || "Guest";
                  const reviewDate = review.date ? new Date(review.date) : null;
                  const stayDetails = [
                    review.stay?.room_name,
                    typeof review.stay?.nights === "number" ? `${review.stay.nights} ${review.stay.nights === 1 ? "night" : "nights"}` : null,
                  ].filter(Boolean);

                  return (
                    <article className={styles.reviewCard} key={review.reviewId ?? review.id ?? `${review.date}-${index}`}>
                      <div className={styles.reviewHeader}>
                        <span className={styles.guestInitials} aria-hidden="true">{getInitials(guestName)}</span>
                        <div className={styles.guestDetails}>
                          <strong>{guestName}</strong>
                          <span>
                            {review.guest?.country && `${review.guest.country} · `}
                            {reviewDate && !Number.isNaN(reviewDate.getTime()) ? (
                              <time dateTime={reviewDate.toISOString()} title={formatReviewDate(review.date)}>
                                {formatReviewTimeAgo(review.date)}
                              </time>
                            ) : (
                              "Date unavailable"
                            )}
                            {review.language && ` · ${languageNames[review.language] ?? review.language.toUpperCase()}`}
                          </span>
                        </div>
                        {typeof review.score === "number" && (
                          <div className={styles.reviewScore} aria-label={`${review.score} out of 10`}>
                            <strong>{review.score}</strong><span>/ 10</span>
                          </div>
                        )}
                      </div>
                      {(review.id !== undefined || review.reviewId) && (
                        <div className={styles.reviewIdentifiers}>
                          {review.id !== undefined && (
                            <span><span>Source ID</span><code>{review.id}</code></span>
                          )}
                          {review.reviewId && (
                            <span><span>Review ID</span><code>{review.reviewId}</code></span>
                          )}
                        </div>
                      )}
                      {review.title && <h3 className={styles.reviewTitle}>{review.title}</h3>}
                      {(review.positive || review.negative) && (
                        <div className={styles.reviewQuotes}>
                          {review.positive && <div className={styles.positiveQuote}><span>Liked</span><p>{review.positive}</p></div>}
                          {review.negative && <div className={styles.negativeQuote}><span>Could be better</span><p>{review.negative}</p></div>}
                        </div>
                      )}
                      {(stayDetails.length > 0 || (review.helpful_votes ?? 0) > 0) && (
                        <div className={styles.reviewFooter}>
                          {stayDetails.length > 0 && <span>{stayDetails.join(" · ")}</span>}
                          {(review.helpful_votes ?? 0) > 0 && <span>{review.helpful_votes} found this helpful</span>}
                        </div>
                      )}
                      {review.owner_reply && (
                        <details className={styles.ownerReply}>
                          <summary>Property response</summary>
                          <p>{review.owner_reply}</p>
                        </details>
                      )}
                    </article>
                  );
                })}
              </div>
              <nav className={styles.pagination} aria-label="Review pagination">
                <span>
                  Showing {(reviewPage * REVIEWS_PER_PAGE + 1).toLocaleString()}–
                  {Math.min((reviewPage + 1) * REVIEWS_PER_PAGE, currentReviewState.totalElements).toLocaleString()}
                  {` of ${currentReviewState.totalElements.toLocaleString()} reviews`}
                </span>
                <div className={styles.paginationControls}>
                  <button
                    type="button"
                    onClick={() => setReviewPage((page) => Math.max(0, page - 1))}
                    disabled={reviewsLoading || reviewPage === 0}
                  >
                    Previous
                  </button>
                  <span>Page {reviewPage + 1} of {currentReviewState.totalPages}</span>
                  <button
                    type="button"
                    onClick={() => setReviewPage((page) => page + 1)}
                    disabled={reviewsLoading || !currentReviewState.hasNext}
                  >
                    Next
                  </button>
                </div>
                {reviewsLoading && (
                  <span className={styles.paginationLoading} role="status">
                    Updating…
                  </span>
                )}
                {reviewsError && (
                  <button
                    type="button"
                    onClick={() => setReviewAttempt((attempt) => attempt + 1)}
                    disabled={reviewsLoading}
                  >
                    Retry
                  </button>
                )}
              </nav>
            </>
          )}
        </section>
      </div>
    </main>
  );
}
