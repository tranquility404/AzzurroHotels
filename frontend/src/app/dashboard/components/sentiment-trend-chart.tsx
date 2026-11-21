"use client";

import { useId, useMemo, useState } from "react";
import {
  addDays,
  formatDayRange,
  formatShortDay,
  parseDayKey,
  toDayKey,
  type DashboardTrend,
} from "@/lib/dashboard-analytics";
import styles from "./sentiment-trend-chart.module.scss";

type Granularity = "day" | "week" | "month";
type ChartType = "line" | "bar";

type PlotPoint = {
  startDate: string;
  endDate: string;
  label: string;
  periodLabel: string;
  positive: number;
  negative: number;
  neutral: number;
  total: number;
};

const SCORE_COUNT = 10;
const DEFAULT_POSITIVE_THRESHOLD = 8;
const MIN_POSITIVE_THRESHOLD = 3;
const MAX_POSITIVE_THRESHOLD = 10;
const DEFAULT_REVIEWS_PER_POINT = 15.69;
const MIN_REVIEWS_PER_POINT = 1;
const MAX_REVIEWS_PER_POINT = 50;

function getRangeDayCount(startDate: string, endDate: string): number {
  const start = parseDayKey(startDate);
  const end = parseDayKey(endDate);
  const startUtc = Date.UTC(start.getFullYear(), start.getMonth(), start.getDate());
  const endUtc = Date.UTC(end.getFullYear(), end.getMonth(), end.getDate());
  return Math.max(1, Math.round((endUtc - startUtc) / 86_400_000) + 1);
}

function getGranularity(dayCount: number): Granularity {
  if (dayCount <= 14) return "day";
  if (dayCount <= 120) return "week";
  return "month";
}

function getDefaultPointCount(startDate: string, endDate: string, granularity: Granularity): number {
  if (granularity === "day") return getRangeDayCount(startDate, endDate);
  if (granularity === "week") return Math.ceil(getRangeDayCount(startDate, endDate) / 7);

  const start = parseDayKey(startDate);
  const end = parseDayKey(endDate);
  return (end.getFullYear() - start.getFullYear()) * 12 + end.getMonth() - start.getMonth() + 1;
}

function countPositiveReviews(
  dailyPoints: DashboardTrend["dailyPoints"],
  startDate: string,
  endDate: string,
  positiveThreshold: number,
): number {
  let total = 0;
  for (const day of dailyPoints) {
    if (day.date < startDate || day.date > endDate) continue;
    for (let index = positiveThreshold - 1; index < SCORE_COUNT; index += 1) {
      total += day.scoreCounts[index] ?? 0;
    }
  }
  return total;
}

function getInitialReviewsPerPoint(
  dailyPoints: DashboardTrend["dailyPoints"],
  startDate: string,
  endDate: string,
): number {
  const granularity = getGranularity(getRangeDayCount(startDate, endDate));
  const initialPointCount = getDefaultPointCount(startDate, endDate, granularity);
  const positiveCount = countPositiveReviews(dailyPoints, startDate, endDate, DEFAULT_POSITIVE_THRESHOLD);
  if (positiveCount === 0) return DEFAULT_REVIEWS_PER_POINT;

  const initialReviewsPerPoint = Math.round((positiveCount / initialPointCount) * 100) / 100;
  return Math.min(MAX_REVIEWS_PER_POINT, Math.max(MIN_REVIEWS_PER_POINT, initialReviewsPerPoint));
}

function createPlotPoints(
  dailyPoints: DashboardTrend["dailyPoints"],
  startDate: string,
  endDate: string,
  requestedPointCount: number,
  positiveThreshold: number,
): PlotPoint[] {
  const scoreCountsByDate = new Map<string, number[]>();
  for (const day of dailyPoints) {
    scoreCountsByDate.set(day.date, day.scoreCounts);
  }

  const dayCount = getRangeDayCount(startDate, endDate);
  const pointCount = Math.min(dayCount, Math.max(1, Math.round(requestedPointCount)));
  const daysPerBucket = Math.floor(dayCount / pointCount);
  const extraDays = dayCount % pointCount;
  const points: PlotPoint[] = [];
  let bucketStart = parseDayKey(startDate);

  for (let pointIndex = 0; pointIndex < pointCount; pointIndex += 1) {
    const bucketDayCount = daysPerBucket + (pointIndex < extraDays ? 1 : 0);
    const startKey = toDayKey(bucketStart);
    const bucketEnd = addDays(bucketStart, bucketDayCount - 1);
    const scoreCounts = Array.from({ length: SCORE_COUNT }, () => 0);

    for (let date = bucketStart; date <= bucketEnd; date = addDays(date, 1)) {
      const dayCounts = scoreCountsByDate.get(toDayKey(date));
      for (let index = 0; index < SCORE_COUNT; index += 1) {
        scoreCounts[index] = (scoreCounts[index] ?? 0) + (dayCounts?.[index] ?? 0);
      }
    }

    let positive = 0;
    let negative = 0;
    let total = 0;
    for (let index = 0; index < SCORE_COUNT; index += 1) {
      const score = index + 1;
      const count = scoreCounts[index] ?? 0;
      total += count;
      if (score >= positiveThreshold) positive += count;
      else if (score <= positiveThreshold - 2) negative += count;
    }

    const endKey = toDayKey(bucketEnd);
    const label = formatShortDay(startKey);
    points.push({
      startDate: startKey,
      endDate: endKey,
      label,
      periodLabel: formatDayRange(startKey, endKey),
      positive,
      negative,
      neutral: total - positive - negative,
      total,
    });
    bucketStart = addDays(bucketEnd, 1);
  }

  return points;
}

function linePath(points: PlotPoint[], key: "positive" | "negative", x: (index: number) => number, y: (value: number) => number): string {
  return points
    .map((point, index) => `${index === 0 ? "M" : "L"} ${x(index).toFixed(1)} ${y(point[key]).toFixed(1)}`)
    .join(" ");
}

export function SentimentTrendChart({
  trend,
  startDate,
  endDate,
}: {
  trend: DashboardTrend;
  startDate: string;
  endDate: string;
}) {
  const [positiveThreshold, setPositiveThreshold] = useState(DEFAULT_POSITIVE_THRESHOLD);
  const [chartType, setChartType] = useState<ChartType>("line");
  const [reviewsPerPoint, setReviewsPerPoint] = useState(() =>
    getInitialReviewsPerPoint(trend.dailyPoints, startDate, endDate),
  );
  const inputId = useId();
  const densityInputId = useId();
  const gradientId = `positive-area-${inputId.replace(/:/g, "")}`;
  const dayCount = getRangeDayCount(startDate, endDate);
  const defaultPointCount = getDefaultPointCount(startDate, endDate, getGranularity(dayCount));
  const positiveReviewCount = countPositiveReviews(trend.dailyPoints, startDate, endDate, positiveThreshold);
  const requestedPointCount = positiveReviewCount > 0
    ? Math.round(positiveReviewCount / reviewsPerPoint)
    : defaultPointCount;
  const pointCount = Math.min(dayCount, Math.max(1, requestedPointCount));
  const points = useMemo(
    () => createPlotPoints(trend.dailyPoints, startDate, endDate, pointCount, positiveThreshold),
    [trend.dailyPoints, startDate, endDate, pointCount, positiveThreshold],
  );

  const positiveTotal = points.reduce((total, point) => total + point.positive, 0);
  const negativeTotal = points.reduce((total, point) => total + point.negative, 0);
  const classifiedTotal = positiveTotal + negativeTotal;
  const positiveShare = classifiedTotal === 0 ? "—" : `${Math.round((positiveTotal / classifiedTotal) * 100)}%`;
  const hasScoredReviews = points.some((point) => point.total > 0);
  const averageDaysPerPoint = dayCount / points.length;
  const averagePositive = points.length === 0 || !hasScoredReviews
    ? "—"
    : (positiveTotal / points.length).toLocaleString("en", { maximumFractionDigits: 1 });
  const peakPoint = points.reduce<PlotPoint | null>(
    (peak, point) => point.positive > (peak?.positive ?? 0) ? point : peak,
    null,
  );
  const negativeThreshold = positiveThreshold - 2;
  const neutralScore = positiveThreshold - 1;

  const width = 680;
  const height = 280;
  const padding = { top: 18, right: 14, bottom: 34, left: 34 };
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;
  const maxValue = Math.max(4, ...points.map((point) => Math.max(point.positive, point.negative)));
  const yMax = Math.ceil(maxValue / 4) * 4;
  const x = (index: number) => points.length > 1
    ? padding.left + (index * innerWidth) / (points.length - 1)
    : padding.left + innerWidth / 2;
  const barBandWidth = innerWidth / points.length;
  const barGap = Math.min(4, barBandWidth * 0.08);
  const barWidth = Math.max(1, Math.min(16, (barBandWidth - barGap) / 2));
  const barCenter = (index: number) => padding.left + (index + 0.5) * barBandWidth;
  const y = (value: number) => padding.top + innerHeight - (value / yMax) * innerHeight;
  const positiveLine = linePath(points, "positive", x, y);
  const negativeLine = linePath(points, "negative", x, y);
  const positiveArea = points.length > 1
    ? `${positiveLine} L ${x(points.length - 1)} ${padding.top + innerHeight} L ${x(0)} ${padding.top + innerHeight} Z`
    : "";
  const ticks = Array.from({ length: 5 }, (_, index) => (yMax / 4) * index);
  const xAxisLabelStride = Math.max(1, Math.ceil(points.length / 18));

  return (
    <div className={styles.chartWrap}>
      <div className={styles.chartToolbar}>
        <div className={styles.chartLegend}>
          <span><i className={styles.legendPositive} aria-hidden="true" />Positive reviews <strong>{positiveTotal.toLocaleString()}</strong></span>
          <span><i className={styles.legendNegative} aria-hidden="true" />Negative reviews <strong>{negativeTotal.toLocaleString()}</strong></span>
        </div>
        <div className={styles.chartTypeToggle} role="group" aria-label="Chart display">
          <button
            className={chartType === "line" ? styles.chartTypeActive : styles.chartTypeButton}
            type="button"
            aria-pressed={chartType === "line"}
            onClick={() => setChartType("line")}
          >
            Line
          </button>
          <button
            className={chartType === "bar" ? styles.chartTypeActive : styles.chartTypeButton}
            type="button"
            aria-pressed={chartType === "bar"}
            onClick={() => setChartType("bar")}
          >
            Bars
          </button>
        </div>
      </div>

      {hasScoredReviews ? (
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className={styles.chart}
          role="img"
          aria-label={`${formatDayRange(startDate, endDate)} sentiment ${chartType === "line" ? "line graph" : "bar chart"}, plotted as ${points.length} points at about ${averageDaysPerPoint.toFixed(1)} days per point. Positive scores are ${positiveThreshold} and above; negative scores are ${negativeThreshold} and below. ${points.map((point) => `${point.periodLabel}: ${point.positive} positive, ${point.negative} negative`).join("; ")}`}
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#668579" stopOpacity="0.24" />
              <stop offset="100%" stopColor="#668579" stopOpacity="0" />
            </linearGradient>
          </defs>
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={padding.left} y1={y(tick)} x2={width - padding.right} y2={y(tick)} className={styles.gridLine} />
              <text x={padding.left - 10} y={y(tick) + 4} textAnchor="end" className={styles.axisText}>
                {Math.round(tick)}
              </text>
            </g>
          ))}
          {chartType === "line" ? (
            <>
              {positiveArea && <path d={positiveArea} fill={`url(#${gradientId})`} />}
              <path d={positiveLine} fill="none" className={styles.linePositive} />
              <path d={negativeLine} fill="none" className={styles.lineNegative} />
              {points.map((point, index) => (
                <g key={point.startDate}>
                  <circle cx={x(index)} cy={y(point.positive)} r="4" className={styles.dotPositive}>
                    <title>{`${point.periodLabel}: ${point.positive} positive reviews`}</title>
                  </circle>
                  <circle cx={x(index)} cy={y(point.negative)} r="4" className={styles.dotNegative}>
                    <title>{`${point.periodLabel}: ${point.negative} negative reviews`}</title>
                  </circle>
                  {(index % xAxisLabelStride === 0 || index === points.length - 1) && (
                    <text x={x(index)} y={height - 12} textAnchor="middle" className={styles.axisText}>
                      {point.label}
                    </text>
                  )}
                </g>
              ))}
            </>
          ) : (
            points.map((point, index) => {
              const center = barCenter(index);
              const chartBottom = padding.top + innerHeight;

              return (
                <g key={point.startDate}>
                  <rect
                    x={center - barGap / 2 - barWidth}
                    y={y(point.positive)}
                    width={barWidth}
                    height={chartBottom - y(point.positive)}
                    className={styles.barPositive}
                    rx="2"
                  >
                    <title>{`${point.periodLabel}: ${point.positive} positive reviews`}</title>
                  </rect>
                  <rect
                    x={center + barGap / 2}
                    y={y(point.negative)}
                    width={barWidth}
                    height={chartBottom - y(point.negative)}
                    className={styles.barNegative}
                    rx="2"
                  >
                    <title>{`${point.periodLabel}: ${point.negative} negative reviews`}</title>
                  </rect>
                  {(index % xAxisLabelStride === 0 || index === points.length - 1) && (
                    <text x={center} y={height - 12} textAnchor="middle" className={styles.axisText}>
                      {point.label}
                    </text>
                  )}
                </g>
              );
            })
          )}
        </svg>
      ) : (
        <div className={styles.chartEmpty} role="status">No scored reviews in this date range.</div>
      )}

      <p className={styles.chartNote}>
        Plotting {points.length} points across {dayCount} days ({averageDaysPerPoint.toLocaleString("en", { maximumFractionDigits: 1 })} days per point).
      </p>
      <div className={styles.chartStats}>
        <div>
          <span>Positive share</span>
          <strong>{positiveShare}</strong>
        </div>
        <div>
          <span>Avg positive / point</span>
          <strong>{averagePositive}</strong>
        </div>
        <div>
          <span>Peak period</span>
          <strong>{peakPoint?.periodLabel ?? "—"}</strong>
        </div>
      </div>

      <div className={styles.chartControls}>
        <div className={styles.thresholdControl}>
          <div className={styles.thresholdHeading}>
            <label htmlFor={inputId}>Positive score cutoff</label>
            <output htmlFor={inputId}>≥ {positiveThreshold} / ≤ {negativeThreshold}</output>
          </div>
          <input
            id={inputId}
            type="range"
            min={MIN_POSITIVE_THRESHOLD}
            max={MAX_POSITIVE_THRESHOLD}
            step={1}
            value={positiveThreshold}
            aria-valuetext={`Positive ${positiveThreshold} and above; negative ${negativeThreshold} and below; score ${neutralScore} neutral`}
            onChange={(event) => setPositiveThreshold(Number(event.currentTarget.value))}
            disabled={!hasScoredReviews}
          />
          <div className={styles.thresholdEnds} aria-hidden="true"><span>3</span><span>10</span></div>
          <p>Score {neutralScore} stays neutral. Adjust the cutoff to compare stricter or broader sentiment.</p>
        </div>

        <div className={styles.pointControl}>
          <div className={styles.pointHeading}>
            <label htmlFor={densityInputId}>Positive reviews per plotted point (x)</label>
            <output htmlFor={densityInputId}>
              x {reviewsPerPoint.toFixed(2)} · {points.length} points
            </output>
          </div>
          <input
            id={densityInputId}
            type="range"
            min={MIN_REVIEWS_PER_POINT}
            max={MAX_REVIEWS_PER_POINT}
            step={0.01}
            value={reviewsPerPoint}
            aria-valuetext={`${reviewsPerPoint.toFixed(2)} positive reviews per plotted point; ${points.length} points`}
            onChange={(event) => setReviewsPerPoint(Number(event.currentTarget.value))}
            disabled={positiveReviewCount === 0}
          />
          <div className={styles.pointEnds} aria-hidden="true">
            <span>{MIN_REVIEWS_PER_POINT} · more points</span>
            <span>{MAX_REVIEWS_PER_POINT} · fewer points</span>
          </div>
          <p>
            {positiveReviewCount > 0
              ? `Point count ≈ ${positiveReviewCount.toLocaleString()} positive reviews ÷ x, capped at one point per day.`
              : "No positive reviews in this range; point density follows the selected date range."}
          </p>
        </div>
      </div>
    </div>
  );
}
