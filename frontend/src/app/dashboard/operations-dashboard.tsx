"use client";

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  ArrowDownRight,
  ArrowUpRight,
  Building2,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  MessageSquare,
  Minus,
  Reply,
  RotateCcw,
  Search,
  Star,
  ThumbsUp,
  X,
} from "lucide-react";
import {
  addDays,
  formatDayRange,
  formatLongDay,
  formatShortDay,
  parseDayKey,
  toDayKey,
  type DashboardOverview,
  type DashboardReview,
  type PeriodPreset,
  type PropertyBreakdown,
} from "@/lib/dashboard-analytics";
import { fetchDashboardOverview, fetchDashboardReviews, type DashboardReviewSort } from "@/lib/dashboard-api";
import { OperationalTopics } from "./components/operational-topics";
import { SentimentTrendChart } from "./components/sentiment-trend-chart";
import styles from "./dashboard.module.scss";

type SentimentFilter = "all" | "positive" | "neutral" | "negative";

const PRESETS: Array<{ id: PeriodPreset; label: string }> = [
  { id: "week", label: "This week" },
  { id: "7d", label: "7 days" },
  { id: "30d", label: "30 days" },
  { id: "90d", label: "90 days" },
  { id: "custom", label: "Custom" },
];

const SENTIMENT_FILTERS: Array<{ id: SentimentFilter; label: string }> = [
  { id: "all", label: "All" },
  { id: "positive", label: "Praise" },
  { id: "neutral", label: "Mixed" },
  { id: "negative", label: "Concerns" },
];

const REVIEW_SORTS: Array<{ id: DashboardReviewSort; label: string }> = [
  { id: "date_desc", label: "Newest first" },
  { id: "date_asc", label: "Oldest first" },
  { id: "score_desc", label: "Highest score" },
  { id: "score_asc", label: "Lowest score" },
  { id: "reviewer_asc", label: "Reviewer A–Z" },
];

type LoadState =
  | { status: "loading" }
  | { status: "error"; key: string; message: string }
  | { status: "ready"; key: string; overview: DashboardOverview };

type FeedLoadState = {
  key: string;
  status: "loading" | "error" | "ready";
  reviews: DashboardReview[];
  page: number;
  totalElements: number;
  hasNext: boolean;
  loadingMore: boolean;
  message?: string;
};

const LANGUAGE_NAMES: Record<string, string> = {
  en: "English",
  de: "German",
  fr: "French",
  es: "Spanish",
  it: "Italian",
};

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function sentimentLabel(sentiment: "positive" | "neutral" | "negative"): string {
  if (sentiment === "positive") return "Positive";
  if (sentiment === "negative") return "Concern";
  return "Mixed";
}

function Delta({
  value,
  suffix = "",
  precision = 1,
  emptyLabel = "No prior data",
}: {
  value: number | null;
  suffix?: string;
  precision?: number;
  emptyLabel?: string;
}) {
  if (value === null || !Number.isFinite(value)) {
    return <span className={`${styles.delta} ${styles.deltaFlat}`}>{emptyLabel}</span>;
  }

  const rounded = Number(value.toFixed(precision));
  if (rounded === 0) {
    return (
      <span className={`${styles.delta} ${styles.deltaFlat}`}>
        <Minus size={13} aria-hidden="true" /> No change
      </span>
    );
  }

  const isUp = rounded > 0;
  const Icon = isUp ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={`${styles.delta} ${isUp ? styles.deltaGood : styles.deltaBad}`}>
      <Icon size={13} aria-hidden="true" />
      {isUp ? "+" : ""}
      {rounded.toFixed(precision)}
      {suffix}
    </span>
  );
}

function KpiCard({
  icon,
  label,
  value,
  unit,
  delta,
  comparison,
  footnote,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  unit?: string;
  delta: ReactNode;
  comparison: string;
  footnote: string;
}) {
  return (
    <article className={styles.kpiCard}>
      <div className={styles.kpiHead}>
        <span className={styles.kpiIcon} aria-hidden="true">{icon}</span>
        <span>{label}</span>
      </div>
      <div className={styles.kpiValue}>
        {value}
        {unit && <span>{unit}</span>}
      </div>
      <div className={styles.kpiMeta}>
        {delta}
        <span>{comparison}</span>
      </div>
      <p className={styles.kpiFootnote}>{footnote}</p>
    </article>
  );
}

function PropertyTable({ rows }: { rows: PropertyBreakdown[] }) {
  return (
    <div className={styles.tableScroll}>
      <table className={styles.propertyTable}>
        <caption className={styles.srOnly}>Average guest rating and review volume by property</caption>
        <thead>
          <tr>
            <th scope="col">Property</th>
            <th scope="col">Average rating</th>
            <th scope="col">Change</th>
            <th scope="col">Reviews</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.property.id}>
              <th scope="row">
                <span className={styles.rank}>{String(index + 1).padStart(2, "0")}</span>
                <span className={styles.propertyIdentity}>
                  <strong>{row.property.name}</strong>
                  <small>{row.property.city}</small>
                </span>
              </th>
              <td>
                <div className={styles.ratingCell}>
                  <span className={styles.ratingTrack} aria-hidden="true">
                    <span style={{ width: `${Math.min(100, (row.average ?? 0) * 10)}%` }} />
                  </span>
                  <strong>{row.average === null ? "—" : row.average.toFixed(1)}</strong>
                </div>
              </td>
              <td><Delta value={row.delta} /></td>
              <td className={styles.reviewCountCell}>
                {row.reviewCount === 0 ? "No reviews" : row.reviewCount.toLocaleString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ReviewDetail({
  review,
  index,
  total,
  onPrevious,
  onNext,
  onClose,
  showClose,
}: {
  review: DashboardReview;
  index: number;
  total: number;
  onPrevious: () => void;
  onNext: () => void;
  onClose?: () => void;
  showClose: boolean;
}) {
  const sentiment = review.sentiment;
  const scoreClass = sentiment === "positive" ? styles.scorePositive : sentiment === "negative" ? styles.scoreNegative : styles.scoreNeutral;
  const topics = Array.from(new Set([...review.positiveTopics, ...review.negativeTopics]));

  return (
    <div className={styles.detailInner}>
      <div className={styles.detailTop}>
        <div className={styles.detailGuest}>
          <span className={styles.guestAvatar} aria-hidden="true">{initials(review.guestName)}</span>
          <div>
            <strong>{review.guestName}</strong>
            <span>{review.guestCountry} · {review.guestType}</span>
          </div>
        </div>
        <div className={styles.detailTopActions}>
          <span className={`${styles.scorePill} ${scoreClass}`} aria-label={`${review.score} out of 10`}>
            {review.score}<small>/10</small>
          </span>
          {showClose && (
            <button className={styles.closeButton} type="button" onClick={onClose} aria-label="Close review detail">
              <X size={17} />
            </button>
          )}
        </div>
      </div>

      <p className={styles.detailProperty}>
        <Building2 size={14} aria-hidden="true" />
        {review.propertyName} · {review.propertyCity}
      </p>

      <h3 className={styles.detailTitle}>{review.title}</h3>
      <p className={styles.detailDate}>
        {formatLongDay(review.date)} · {LANGUAGE_NAMES[review.language] ?? review.language.toUpperCase()}
      </p>
      <p className={styles.reviewId}>Review ID: <code>{review.id}</code></p>

      {review.positive && (
        <div className={`${styles.quote} ${styles.quotePositive}`}>
          <span>Liked</span>
          <p>{review.positive}</p>
        </div>
      )}
      {review.negative && (
        <div className={`${styles.quote} ${styles.quoteNegative}`}>
          <span>Could be better</span>
          <p>{review.negative}</p>
        </div>
      )}

      <dl className={styles.stayGrid}>
        <div><dt>Room</dt><dd>{review.roomName}</dd></div>
        <div><dt>Length of stay</dt><dd>{review.nights === null ? "Not provided" : `${review.nights} ${review.nights === 1 ? "night" : "nights"}`}</dd></div>
        <div><dt>Guest type</dt><dd>{review.guestType}</dd></div>
        <div><dt>Language</dt><dd>{LANGUAGE_NAMES[review.language] ?? review.language.toUpperCase()}</dd></div>
      </dl>

      {topics.length > 0 && (
        <div className={styles.topicChips}>
          {topics.map((topic) => <span key={topic}>{topic}</span>)}
        </div>
      )}

      {review.ownerReply && (
        <div className={styles.ownerReply}>
          <span>Property response</span>
          <p>{review.ownerReply}</p>
        </div>
      )}

      <div className={styles.detailFooter}>
        <button type="button" onClick={onPrevious} disabled={index <= 0} aria-label="View newer review">
          <ChevronLeft size={15} /> Newer
        </button>
        <span>{index + 1} of {total}</span>
        <button type="button" onClick={onNext} disabled={index >= total - 1} aria-label="View older review">
          Older <ChevronRight size={15} />
        </button>
      </div>
    </div>
  );
}

export default function OperationsDashboard() {
  const [loadState, setLoadState] = useState<LoadState>({ status: "loading" });
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [feedAttempt, setFeedAttempt] = useState(0);
  const [preset, setPreset] = useState<PeriodPreset>("week");
  const [propertyId, setPropertyId] = useState("all");
  const [customStart, setCustomStart] = useState("");
  const [customEnd, setCustomEnd] = useState("");
  const [search, setSearch] = useState("");
  const [sentiment, setSentiment] = useState<SentimentFilter>("all");
  const [sort, setSort] = useState<DashboardReviewSort>("date_desc");
  const [feedUi, setFeedUi] = useState<{
    key: string;
    visibleCount: number;
    selectedId: string | null;
    mobileOpen: boolean;
  }>({ key: "", visibleCount: 10, selectedId: null, mobileOpen: false });
  const [feedState, setFeedState] = useState<FeedLoadState>({
    key: "",
    status: "loading",
    reviews: [],
    page: 0,
    totalElements: 0,
    hasNext: false,
    loadingMore: false,
  });
  const feedRequestController = useRef<AbortController | null>(null);

  const overviewFilters = useMemo(() => ({
    period: preset,
    ...(preset === "custom" ? { startDate: customStart, endDate: customEnd } : {}),
    propertyId,
  }), [preset, customStart, customEnd, propertyId]);
  const overviewRequestKey = JSON.stringify(overviewFilters);

  useEffect(() => {
    if (overviewFilters.period === "custom" && (!overviewFilters.startDate || !overviewFilters.endDate)) return;

    const controller = new AbortController();
    void fetchDashboardOverview(overviewFilters, controller.signal)
      .then((overview) => setLoadState({ status: "ready", key: overviewRequestKey, overview }))
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setLoadState({
          status: "error",
          key: overviewRequestKey,
          message: cause instanceof Error ? cause.message : "Dashboard statistics could not be loaded.",
        });
      });

    return () => controller.abort();
  }, [overviewFilters, overviewRequestKey, loadAttempt]);

  const overview = loadState.status === "ready" && loadState.key === overviewRequestKey
    ? loadState.overview
    : null;
  const properties = overview?.properties ?? [];
  const selectedProperty = properties.find((property) => property.id === propertyId) ?? null;
  const feedQueryKey = overview
    ? JSON.stringify([
        overview.period.startDate,
        overview.period.endDate,
        propertyId,
        sentiment,
        search.trim(),
        sort,
      ])
    : "";

  const currentFeedUi = feedUi.key === feedQueryKey
    ? feedUi
    : { key: feedQueryKey, visibleCount: 10, selectedId: null, mobileOpen: false };

  useEffect(() => {
    if (!overview) {
      feedRequestController.current?.abort();
      feedRequestController.current = null;
      return;
    }

    const key = JSON.stringify([
      overview.period.startDate,
      overview.period.endDate,
      propertyId,
      sentiment,
      search.trim(),
      sort,
    ]);
    feedRequestController.current?.abort();
    const controller = new AbortController();
    feedRequestController.current = controller;

    void fetchDashboardReviews({
      startDate: overview.period.startDate,
      endDate: overview.period.endDate,
      propertyId,
      sentiment,
      search,
      sort,
      page: 0,
    }, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) return;
        setFeedState({
          key,
          status: "ready",
          reviews: page.data,
          page: page.page,
          totalElements: page.totalElements,
          hasNext: page.hasNext,
          loadingMore: false,
        });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setFeedState({
          key,
          status: "error",
          reviews: [],
          page: 0,
          totalElements: 0,
          hasNext: false,
          loadingMore: false,
          message: cause instanceof Error ? cause.message : "Dashboard reviews could not be loaded.",
        });
      });

    return () => {
      feedRequestController.current?.abort();
      feedRequestController.current = null;
    };
  }, [overview, propertyId, sentiment, search, sort, feedAttempt]);

  const currentFeed = feedState.key === feedQueryKey
    ? feedState
    : {
        key: feedQueryKey,
        status: "loading" as const,
        reviews: [],
        page: 0,
        totalElements: 0,
        hasNext: false,
        loadingMore: false,
      };
  const feed = currentFeed.reviews;
  const visibleFeed = feed.slice(0, currentFeedUi.visibleCount);
  const activeReview = feed.find((review) => review.id === currentFeedUi.selectedId) ?? feed[0] ?? null;
  const activeIndex = activeReview ? feed.findIndex((review) => review.id === activeReview.id) : -1;

  const sentimentCounts = {
    all: overview?.current.count ?? 0,
    positive: overview?.current.positiveCount ?? 0,
    neutral: overview?.current.neutralCount ?? 0,
    negative: overview?.current.negativeCount ?? 0,
  };
  const rangeLabel = overview
    ? formatDayRange(overview.period.startDate, overview.period.endDate)
    : "";
  const isFiltered = preset !== "week" || propertyId !== "all" || search.trim().length > 0
    || sentiment !== "all" || sort !== "date_desc";

  function selectPreset(next: PeriodPreset) {
    setPreset(next);
    if (next === "custom" && overview) {
      setCustomStart(toDayKey(addDays(parseDayKey(overview.asOfDate), -13)));
      setCustomEnd(overview.asOfDate);
    }
  }

  function updateCustomStart(value: string) {
    setCustomStart(value);
    if (value && customEnd && value > customEnd) setCustomEnd(value);
  }

  function updateCustomEnd(value: string) {
    setCustomEnd(value);
    if (value && customStart && value < customStart) setCustomStart(value);
  }

  function resetFilters() {
    setPreset("week");
    setPropertyId("all");
    setSearch("");
    setSentiment("all");
    setSort("date_desc");
    if (overview) {
      setCustomEnd(overview.asOfDate);
      setCustomStart(toDayKey(addDays(parseDayKey(overview.asOfDate), -13)));
    }
  }

  function updateFeedUi(next: Partial<{ visibleCount: number; selectedId: string | null; mobileOpen: boolean }>) {
    setFeedUi({ ...currentFeedUi, ...next, key: feedQueryKey });
  }

  function moveSelection(offset: number) {
    if (activeIndex < 0) return;
    const next = feed[activeIndex + offset];
    if (next) updateFeedUi({ selectedId: next.id });
  }

  async function loadNextFeedPage() {
    if (!overview || currentFeed.status !== "ready" || !currentFeed.hasNext || currentFeed.loadingMore) return;

    const key = currentFeed.key;
    const nextPage = currentFeed.page + 1;
    feedRequestController.current?.abort();
    const controller = new AbortController();
    feedRequestController.current = controller;
    setFeedState((current) => current.key === key ? { ...current, loadingMore: true, message: undefined } : current);

    try {
      const page = await fetchDashboardReviews({
        startDate: overview.period.startDate,
        endDate: overview.period.endDate,
        propertyId,
        sentiment,
        search,
        sort,
        page: nextPage,
      }, controller.signal);
      if (controller.signal.aborted) return;
      setFeedState((current) => current.key === key
        ? {
            ...current,
            status: "ready",
            reviews: [...current.reviews, ...page.data],
            page: page.page,
            totalElements: page.totalElements,
            hasNext: page.hasNext,
            loadingMore: false,
          }
        : current);
    } catch (cause: unknown) {
      if (controller.signal.aborted) return;
      setFeedState((current) => current.key === key
        ? {
            ...current,
            loadingMore: false,
            message: cause instanceof Error ? cause.message : "More reviews could not be loaded.",
          }
        : current);
    }
  }

  if (loadState.status === "error" && loadState.key === overviewRequestKey) {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <div className={styles.emptyFeed} role="alert">
            <CircleAlert size={22} aria-hidden="true" />
            <strong>Dashboard data could not be loaded</strong>
            <p>{loadState.message}</p>
            <button
              type="button"
              className={styles.loadMore}
              onClick={() => {
                setLoadState({ status: "loading" });
                setLoadAttempt((attempt) => attempt + 1);
              }}
            >
              Try again
            </button>
          </div>
        </div>
      </main>
    );
  }

  if (!overview) {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <div className={styles.loading} role="status">
            <span className={styles.loadingMark} aria-hidden="true">AZ</span>
            <p>Loading dashboard summary…</p>
          </div>
        </div>
      </main>
    );
  }

  if (overview.properties.length === 0) {
    return (
      <main className={styles.page}>
        <div className={styles.content}>
          <div className={styles.emptyFeed} role="status">
            <Building2 size={22} aria-hidden="true" />
            <strong>No saved hotels yet</strong>
            <p>Property performance and guest feedback will appear here when hotel data is available.</p>
          </div>
        </div>
      </main>
    );
  }

  const earliestCustomDate = toDayKey(addDays(parseDayKey(overview.asOfDate), -365));
  const period = overview.period;
  const currentStats = overview.current;
  const deltas = overview.deltas;

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.pageHeader}>
          <div className={styles.pageHeadingCopy}>
            <h1>Guest experience</h1>
          </div>
          <div className={styles.asOf}>
            <CalendarDays size={18} aria-hidden="true" />
            <span>Data as of</span>
            <strong>{formatLongDay(overview.asOfDate)}</strong>
          </div>
        </header>

        <section className={styles.filters} aria-label="Dashboard filters">
          <div className={styles.filterGroup}>
            <span className={styles.filterLabel}>Date range</span>
            <div className={styles.segmented} role="group" aria-label="Date range">
              {PRESETS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  className={`${styles.segment} ${preset === option.id ? styles.segmentActive : ""}`}
                  onClick={() => selectPreset(option.id)}
                  aria-pressed={preset === option.id}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          <div className={styles.filterGroup}>
            <label className={styles.filterLabel} htmlFor="property-filter">Property</label>
            <select
              id="property-filter"
              className={styles.propertySelect}
              value={propertyId}
              onChange={(event) => setPropertyId(event.target.value)}
            >
              <option value="all">All properties</option>
              {properties.map((property) => (
                <option key={property.id} value={property.id}>
                  {property.name} — {property.city}
                </option>
              ))}
            </select>
          </div>

          {preset === "custom" && (
            <div className={styles.customDates}>
              <label className={styles.dateField}>
                <span>From</span>
                <input
                  type="date"
                  value={customStart}
                  min={earliestCustomDate}
                  max={overview.asOfDate}
                  onChange={(event) => updateCustomStart(event.target.value)}
                />
              </label>
              <label className={styles.dateField}>
                <span>To</span>
                <input
                  type="date"
                  value={customEnd}
                  min={earliestCustomDate}
                  max={overview.asOfDate}
                  onChange={(event) => updateCustomEnd(event.target.value)}
                />
              </label>
            </div>
          )}

          <div className={styles.activePeriod}>
            <span>{rangeLabel}</span>
            <small>{period.dayCount} {period.dayCount === 1 ? "day" : "days"}</small>
          </div>

          {isFiltered && (
            <button type="button" className={styles.resetButton} onClick={resetFilters}>
              <RotateCcw size={14} aria-hidden="true" /> Reset
            </button>
          )}
        </section>

        <section className={styles.kpiGrid} aria-label="Key performance indicators">
          <KpiCard
            icon={<Star size={17} />}
            label="Average rating"
            value={currentStats.average === null ? "—" : currentStats.average.toFixed(1)}
            unit="/ 10"
            delta={<Delta value={deltas.averageRating} />}
            comparison={period.comparisonLabel}
            footnote={`${rangeLabel} · ${currentStats.count.toLocaleString()} ${currentStats.count === 1 ? "review" : "reviews"}`}
          />
          <KpiCard
            icon={<MessageSquare size={17} />}
            label="Reviews received"
            value={currentStats.count.toLocaleString()}
            unit={currentStats.count === 1 ? "review" : "reviews"}
            delta={<Delta value={deltas.reviewCountPercent} suffix="%" precision={0} />}
            comparison={period.comparisonLabel}
            footnote={`${overview.reportingProperties} ${overview.reportingProperties === 1 ? "property" : "properties"} reporting`}
          />
          <KpiCard
            icon={<ThumbsUp size={17} />}
            label="Positive share"
            value={currentStats.count === 0 ? "—" : currentStats.positiveShare.toFixed(0)}
            unit="%"
            delta={<Delta value={deltas.positiveSharePoints} suffix=" pts" precision={1} />}
            comparison={period.comparisonLabel}
            footnote={`${currentStats.positiveCount} of ${currentStats.count} reviews scored 8+`}
          />
          <KpiCard
            icon={<Reply size={17} />}
            label="Response rate"
            value={currentStats.count === 0 ? "—" : currentStats.replyRate.toFixed(0)}
            unit="%"
            delta={<Delta value={deltas.replyRatePoints} suffix=" pts" precision={1} />}
            comparison={period.comparisonLabel}
            footnote={`${currentStats.replyCount} of ${currentStats.count} reviews answered`}
          />
        </section>

        <div className={styles.panelGrid}>
          <section className={styles.panel} aria-labelledby="breakdown-title">
            <div className={styles.panelHead}>
              <div>
                <p className={styles.panelLabel}>Property performance</p>
                <h2 id="breakdown-title">Rating by property</h2>
              </div>
              <span className={styles.panelMeta}>{rangeLabel}</span>
            </div>
            <PropertyTable rows={overview.propertyPerformance} />
          </section>

          <section className={styles.panel} aria-labelledby="trend-title">
            <div className={styles.panelHead}>
              <div>
                <p className={styles.panelLabel}>Sentiment over time</p>
                <h2 id="trend-title">Positive &amp; negative trend</h2>
              </div>
              <span className={styles.panelMeta}>{rangeLabel}</span>
            </div>
            <SentimentTrendChart
              trend={overview.trend}
              startDate={overview.period.startDate}
              endDate={overview.period.endDate}
            />
          </section>
        </div>

        <OperationalTopics
          topics={overview.operationalTopics}
          positiveReviewCount={currentStats.positiveCount}
          negativeReviewCount={currentStats.negativeCount}
          rangeLabel={rangeLabel}
        />

        <section className={styles.reviewSection} aria-labelledby="reviews-title">
          <div className={styles.reviewHeader}>
            <div>
              <p className={styles.panelLabel}>Review feed</p>
              <h2 id="reviews-title">Read the reviews</h2>
            </div>
            <div className={styles.reviewToolbar}>
              <label className={styles.searchField}>
                <Search size={16} aria-hidden="true" />
                <span className={styles.srOnly}>Search reviews</span>
                <input
                  type="search"
                  placeholder="Search reviewer, date, review text, or ID…"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </label>
              <label className={styles.sortField}>
                <span className={styles.srOnly}>Sort reviews</span>
                <select value={sort} onChange={(event) => setSort(event.target.value as DashboardReviewSort)}>
                  {REVIEW_SORTS.map((option) => (
                    <option key={option.id} value={option.id}>{option.label}</option>
                  ))}
                </select>
              </label>
              <div className={styles.sentimentTabs} role="group" aria-label="Filter by sentiment">
                {SENTIMENT_FILTERS.map((option) => (
                  <button
                    key={option.id}
                    type="button"
                    className={`${styles.sentimentTab} ${sentiment === option.id ? styles.sentimentTabActive : ""}`}
                    onClick={() => setSentiment(option.id)}
                    aria-pressed={sentiment === option.id}
                  >
                    {option.label} <span>{sentimentCounts[option.id]}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <p className={styles.reviewSummary}>
            {currentFeed.status === "ready"
              ? `${currentFeed.totalElements.toLocaleString()} matching reviews`
              : currentFeed.status === "error" ? "Review feed unavailable" : "Loading review feed…"} ·{" "}
            {selectedProperty ? selectedProperty.name : "All properties"} · {rangeLabel}
          </p>
          <p className={styles.reviewFilterNote}>
            Date range and property follow the dashboard filters above. Search covers reviewer, date, review text, and review ID.
          </p>

          <div className={styles.reviewWorkspace}>
            <div className={styles.feedColumn}>
              {currentFeed.status === "loading" ? (
                <div className={styles.emptyFeed} role="status">
                  <MessageSquare size={22} aria-hidden="true" />
                  <strong>Loading reviews</strong>
                  <p>Fetching a page of matching reviews…</p>
                </div>
              ) : currentFeed.status === "error" ? (
                <div className={styles.emptyFeed} role="alert">
                  <CircleAlert size={22} aria-hidden="true" />
                  <strong>Reviews could not be loaded</strong>
                  <p>{currentFeed.message}</p>
                  <button
                    type="button"
                    className={styles.loadMore}
                    onClick={() => {
                      setFeedState((current) => current.key === feedQueryKey
                        ? { ...current, status: "loading", message: undefined }
                        : current);
                      setFeedAttempt((attempt) => attempt + 1);
                    }}
                  >
                    Try again
                  </button>
                </div>
              ) : feed.length === 0 ? (
                <div className={styles.emptyFeed}>
                  <MessageSquare size={22} aria-hidden="true" />
                  <strong>{overview.current.count === 0 ? "No reviews in this date range" : "No reviews match these filters"}</strong>
                  <p>{overview.current.count === 0
                    ? "Try another date range to see stored guest reviews."
                    : "Try a different sentiment or clear the search."}</p>
                </div>
              ) : (
                <>
                  <div className={styles.feedList}>
                    {visibleFeed.map((review) => {
                      const reviewSentiment = review.sentiment;
                      const tagClass = reviewSentiment === "positive"
                        ? styles.tagPositive
                        : reviewSentiment === "negative"
                          ? styles.tagNegative
                          : styles.tagNeutral;
                      const scoreClass = reviewSentiment === "positive"
                        ? styles.scorePositive
                        : reviewSentiment === "negative"
                          ? styles.scoreNegative
                          : styles.scoreNeutral;

                      return (
                        <button
                          type="button"
                          key={review.id}
                          className={`${styles.feedItem} ${activeReview?.id === review.id ? styles.feedItemActive : ""}`}
                          onClick={() => {
                            updateFeedUi({ selectedId: review.id, mobileOpen: true });
                          }}
                          aria-pressed={activeReview?.id === review.id}
                        >
                          <span className={styles.feedTop}>
                            <span className={styles.guestAvatar} aria-hidden="true">{initials(review.guestName)}</span>
                            <span className={styles.feedGuest}>
                              <strong>{review.guestName}</strong>
                              <span>{review.propertyName} · {formatShortDay(review.date)}</span>
                            </span>
                            <span className={`${styles.scorePill} ${scoreClass}`} aria-label={`${review.score} out of 10`}>
                              {review.score}<small>/10</small>
                            </span>
                          </span>
                          <span className={styles.feedTitle}>{review.title}</span>
                          <span className={styles.feedSnippet}>{review.positive ?? review.negative}</span>
                          <span className={styles.feedFoot}>
                            <span className={`${styles.sentimentTag} ${tagClass}`}>{sentimentLabel(reviewSentiment)}</span>
                            {review.negative && <span className={styles.concernTag}>Concern noted</span>}
                            <span className={styles.feedHint}>Read review <ArrowUpRight size={12} aria-hidden="true" /></span>
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  <div className={styles.feedPaging}>
                    {currentFeedUi.visibleCount < feed.length ? (
                      <button
                        type="button"
                        className={styles.loadMore}
                        onClick={() => updateFeedUi({ visibleCount: currentFeedUi.visibleCount + 10 })}
                      >
                        Show more reviews
                      </button>
                    ) : currentFeed.hasNext ? (
                      <button
                        type="button"
                        className={styles.loadMore}
                        onClick={() => void loadNextFeedPage()}
                        disabled={currentFeed.loadingMore}
                      >
                        {currentFeed.loadingMore ? "Loading reviews…" : "Load more reviews"}
                      </button>
                    ) : (
                      <span>You&apos;ve reached the end of this selection.</span>
                    )}
                    <span>Showing {visibleFeed.length} of {currentFeed.totalElements.toLocaleString()}</span>
                  </div>
                  {currentFeed.message && <p className={styles.emptyText} role="alert">{currentFeed.message}</p>}
                </>
              )}
            </div>

            <aside className={styles.detailColumn} aria-label="Selected review">
              {activeReview ? (
                <ReviewDetail
                  review={activeReview}
                  index={activeIndex}
                  total={feed.length}
                  onPrevious={() => moveSelection(-1)}
                  onNext={() => moveSelection(1)}
                  showClose={false}
                />
              ) : (
                <div className={styles.emptyDetail}>
                  <Star size={22} aria-hidden="true" />
                  <p>Select a review to read the full story.</p>
                </div>
              )}
            </aside>
          </div>
        </section>

        <footer className={styles.footer}>
          <span>AZZURRO · OPERATIONS</span>
          <span>API data · {feed.length.toLocaleString()} reviews loaded · {properties.length} properties</span>
        </footer>
      </div>

      {currentFeedUi.mobileOpen && activeReview && (
        <div className={styles.mobileOverlay} role="dialog" aria-modal="true" aria-label="Review detail">
          <button
            type="button"
            className={styles.overlayBackdrop}
            onClick={() => updateFeedUi({ mobileOpen: false })}
            aria-label="Close review detail"
          />
          <div className={styles.mobileSheet}>
            <ReviewDetail
              review={activeReview}
              index={activeIndex}
              total={feed.length}
              onPrevious={() => moveSelection(-1)}
              onNext={() => moveSelection(1)}
              onClose={() => updateFeedUi({ mobileOpen: false })}
              showClose
            />
          </div>
        </div>
      )}
    </main>
  );
}
