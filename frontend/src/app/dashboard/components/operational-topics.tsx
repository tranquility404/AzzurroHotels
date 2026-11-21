import type { CSSProperties } from "react";
import type { OperationalTopic } from "@/lib/dashboard-analytics";
import styles from "./operational-topics.module.scss";

type OperationalTopicsProps = {
  topics: OperationalTopic[] | null | undefined;
  positiveReviewCount: number;
  negativeReviewCount: number;
  rangeLabel: string;
};

type TopicSentimentStatProps = {
  topic: string;
  sentiment: "positive" | "negative";
  count: number;
  total: number;
  share: number | null;
};

function TopicSentimentStat({ topic, sentiment, count, total, share }: TopicSentimentStatProps) {
  const percentage = share === null
    ? "—"
    : `${share.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;
  const progress = share !== null && Number.isFinite(share)
    ? Math.min(100, Math.max(0, share))
    : 0;
  const ringClass = sentiment === "positive" ? styles.progressRingPositive : styles.progressRingNegative;

  return (
    <div className={styles.sentimentStat}>
      <span className={sentiment === "positive" ? styles.positiveLabel : styles.negativeLabel}>
        {sentiment === "positive" ? "Positive" : "Negative"}
      </span>
      <div
        className={`${styles.progressRing} ${ringClass}`}
        role="progressbar"
        aria-label={`${topic}: share of ${sentiment} reviews`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={progress}
        aria-valuetext={share === null ? `No ${sentiment} reviews in this date range` : `${percentage}; ${count} of ${total} reviews`}
        style={{ "--progress": `${progress}%` } as CSSProperties}
      >
        <span>{percentage}</span>
      </div>
      <span className={styles.sentimentDetail}>
        {total === 0 ? "No reviews" : `${count} of ${total} reviews`}
      </span>
    </div>
  );
}

export function OperationalTopics({
  topics,
  positiveReviewCount,
  negativeReviewCount,
  rangeLabel,
}: OperationalTopicsProps) {
  const topicItems = Array.isArray(topics) ? topics : null;

  return (
    <section className={styles.section} aria-labelledby="operational-topics-title">
      <header className={styles.header}>
        <h2 id="operational-topics-title">Operational topics</h2>
        <p className={styles.dateRange}>{rangeLabel}</p>
      </header>

      <p className={styles.methodNote}>
        A review may mention more than one topic.
      </p>

      {topicItems === null ? (
        <p className={styles.emptyState} role="status">
          Topic data was not returned by the backend. Restart the backend to load the operational-topic update.
        </p>
      ) : topicItems.length === 0 ? (
        <p className={styles.emptyState} role="status">
          No reviews matched these topics for this date range.
        </p>
      ) : (
        <ul className={styles.topicList}>
          {topicItems.map((topic) => {
            return (
              <li className={styles.topic} key={topic.topic}>
                <div className={styles.topicHeading}>
                  <h3>{topic.topic}</h3>
                  <span>{topic.reviewCount} {topic.reviewCount === 1 ? "review" : "reviews"}</span>
                </div>

                <div className={styles.sentimentStats}>
                  <TopicSentimentStat
                    topic={topic.topic}
                    sentiment="positive"
                    count={topic.positiveReviewCount}
                    total={positiveReviewCount}
                    share={topic.positiveReviewSharePercent ?? null}
                  />
                  <TopicSentimentStat
                    topic={topic.topic}
                    sentiment="negative"
                    count={topic.negativeReviewCount}
                    total={negativeReviewCount}
                    share={topic.negativeReviewSharePercent ?? null}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
