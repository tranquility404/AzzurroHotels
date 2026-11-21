"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Building2,
  CalendarDays,
  ChevronRight,
  CircleAlert,
  MessageSquare,
  Star,
} from "lucide-react";
import styles from "./page.module.scss";

type HotelSummary = {
  hotelId: string;
  hotelName: string;
  rating: number | null;
  reviewCount: number;
  collectedAt: string | null;
  fileName: string;
};

function getErrorMessage(payload: unknown): string {
  if (typeof payload === "object" && payload !== null) {
    const body = payload as { message?: unknown; detail?: unknown };
    if (typeof body.message === "string" && body.message.trim()) return body.message;
    if (typeof body.detail === "string" && body.detail.trim()) return body.detail;
  }
  return "The saved hotels could not be loaded. Check that the backend is running and try again.";
}

function formatDate(value: string | null): string {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Date unavailable";
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" }).format(date);
}

export default function HotelsPage() {
  const [hotels, setHotels] = useState<HotelSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadHotels() {
      try {
        const response = await fetch("/api/hotels", { cache: "no-store", signal: controller.signal });
        const payload: unknown = await response.json().catch(() => null);
        if (!response.ok) throw new Error(getErrorMessage(payload));
        if (!Array.isArray(payload)) throw new Error("The hotel catalog returned an unexpected response.");
        setHotels(payload as HotelSummary[]);
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : "The saved hotels could not be loaded.");
        }
      }
    }

    void loadHotels();
    return () => controller.abort();
  }, []);

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <section className={styles.hero} aria-labelledby="page-title">
          <div className={styles.heroCopy}>
            <h1 id="page-title">Hotels</h1>
            <p className={styles.lede}>Property details, ratings and guest reviews.</p>
          </div>
        </section>

        <section className={styles.collection} aria-labelledby="collection-title">
          <div className={styles.sectionHeading}>
            <div>
              <h2 id="collection-title">Saved properties</h2>
            </div>
            {hotels && <span className={styles.totalCount}>{hotels.length} {hotels.length === 1 ? "hotel" : "hotels"}</span>}
          </div>

          {hotels === null && !error && (
            <div className={styles.messageCard} role="status">Loading hotels…</div>
          )}

          {error && (
            <div className={`${styles.messageCard} ${styles.errorCard}`} role="alert">
              <CircleAlert size={18} />
              <span>{error}</span>
            </div>
          )}

          {hotels?.length === 0 && (
            <div className={`${styles.messageCard} ${styles.emptyCard}`}>
              <span className={styles.emptyIcon}><Building2 size={21} /></span>
              <div>
                <h3>No hotels saved yet</h3>
                <p>Property details and guest reviews will appear here when available.</p>
              </div>
            </div>
          )}

          {!!hotels?.length && (
            <ul className={styles.hotelGrid}>
              {hotels.map((hotel) => (
                <li key={hotel.hotelId}>
                  <Link className={styles.hotelCard} href={`/hotels/${encodeURIComponent(hotel.hotelId)}`}>
                    <div className={styles.cardTopline}>
                      <span className={styles.hotelGlyph}><Building2 size={19} strokeWidth={1.6} /></span>
                      <span className={styles.cardArrow} aria-hidden="true"><ChevronRight size={17} /></span>
                    </div>
                    <h3>{hotel.hotelName}</h3>
                    <p className={styles.hotelId}>ID <code>{hotel.hotelId}</code></p>
                    <div className={styles.cardStats}>
                      <span><Star size={14} fill="currentColor" />{hotel.rating === null ? "Not rated" : hotel.rating.toFixed(1)}</span>
                      <span><MessageSquare size={14} />{hotel.reviewCount.toLocaleString()} reviews</span>
                    </div>
                    <div className={styles.cardFooter}>
                      <span><CalendarDays size={13} />{formatDate(hotel.collectedAt)}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

      </div>
    </main>
  );
}
