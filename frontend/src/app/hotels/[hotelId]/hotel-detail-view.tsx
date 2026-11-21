"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  ArrowLeft,
  ArrowRight,
  BedDouble,
  BriefcaseBusiness,
  Baby,
  Building2,
  CalendarClock,
  Check,
  CircleAlert,
  Clock3,
  CreditCard,
  ExternalLink,
  Languages,
  Leaf,
  MapPin,
  Maximize2,
  PawPrint,
  RefreshCw,
  Star,
  Users,
} from "lucide-react";
import SectionToggle from "./section-toggle";
import styles from "./page.module.scss";

type HotelLocation = {
  address?: string;
  city?: string;
  region?: string;
  country?: string;
  country_code?: string;
  latitude?: number;
  longitude?: number;
};

type RatingSubscore = { name?: string; score?: number; count?: number };

type HotelRating = {
  score?: number;
  count?: number;
  stars?: number;
  subscores?: RatingSubscore[];
};

type TimeWindow = { from?: string; until?: string | null };

type HotelPolicies = {
  checkin?: TimeWindow;
  checkout?: TimeWindow;
  pets?: string;
  allows_children?: boolean;
  accepted_payments?: string[];
};

type HotelPhoto = { id?: number | string; link?: string; caption?: string; category?: string };
type BedInfo = { type?: string; count?: number };

type HotelRoom = {
  id?: number | string;
  name?: string;
  description?: string;
  size_m2?: number;
  max_occupancy?: number;
  beds?: BedInfo | BedInfo[];
  facility_highlights?: Array<string | { name?: string }>;
  photos?: string[];
};

type HotelPlace = {
  name?: string;
  type?: string;
  distance?: string;
  walk_time?: string;
  drive_time?: string;
};

type HotelDetails = {
  name?: string;
  link?: string;
  accommodation_type?: string;
  description?: string;
  fine_print?: string;
  image?: string;
  location?: HotelLocation;
  rating?: HotelRating;
  policies?: HotelPolicies;
  languages_spoken?: string[];
  is_sustainable?: boolean;
  is_work_friendly?: boolean;
  facilities?: Array<{ id?: number; name?: string }>;
  photos?: HotelPhoto[];
  rooms?: HotelRoom[];
  surroundings?: Partial<Record<"landmarks" | "restaurants" | "public_transport" | "airports" | "beaches" | "natural_beauty", HotelPlace[]>>;
};

type HotelSnapshot = {
  source?: string;
  collectedAt?: string;
  requestedUrl?: string;
  hotelId?: string;
  hotelName?: string;
  reviewCount?: number;
  hotelDetails?: HotelDetails;
};

type HotelSyncReviewPreview = {
  sourcePosition: number;
  sourceReviewId: string | null;
  classification: string;
  review: Record<string, unknown>;
};

type HotelSyncResponse = {
  hotelId: string;
  hotelName: string;
  nextAllowedAt: string | null;
  hotelDetailsUpdated: boolean;
  currentPage: number;
  totalPages: number;
  nextPage: number | null;
  pagesFetched: number;
  reviewsReceived: number;
  reviewsAdded: number;
  reviewsSkipped: number;
  totalReviewsAdded: number;
  totalReviewsSkipped: number;
  hasMore: boolean;
  unrestrictedSyncRecommended: boolean;
  continuationToken: string | null;
  message: string;
  awaitingConfirmation: boolean;
  pendingBatchId: string | null;
  snapshotPreview: HotelSnapshot | null;
  reviewPreviews: HotelSyncReviewPreview[];
  previewNewReviews: number;
  previewAlreadyStoredReviews: number;
  previewDuplicateReviews: number;
  previewBeyondBoundaryReviews: number;
};

type PersistedHotelSyncState = {
  version: 2;
  continuationToken: unknown;
  nextAllowedAt: unknown;
};

class HotelSyncRequestError extends Error {
  constructor(message: string, readonly nextAllowedAt: string | null = null) {
    super(message);
    this.name = "HotelSyncRequestError";
  }
}

type GalleryPhoto = { src: string; alt: string };

const ratingLabels: Record<string, string> = {
  hotel_staff: "Staff",
  hotel_services: "Facilities",
  hotel_clean: "Cleanliness",
  hotel_comfort: "Comfort",
  hotel_value: "Value for money",
  hotel_location: "Location",
  hotel_free_wifi: "Free Wi-Fi",
  hotel_paid_wifi: "Paid Wi-Fi",
};

const languageNames: Record<string, string> = {
  en: "English",
  es: "Spanish",
  fr: "French",
  ja: "Japanese",
  ko: "Korean",
};

function getErrorMessage(payload: unknown, fallback = "Hotel details could not be loaded. Check that the property exists and try again."): string {
  if (typeof payload === "object" && payload !== null) {
    const body = payload as { message?: unknown; detail?: unknown };
    if (typeof body.message === "string" && body.message.trim()) return body.message;
    if (typeof body.detail === "string" && body.detail.trim()) return body.detail;
  }
  return fallback;
}

function getSyncError(payload: unknown): HotelSyncRequestError {
  if (typeof payload === "object" && payload !== null) {
    const body = payload as {
      message?: unknown;
      detail?: unknown;
      nextAllowedAt?: unknown;
      properties?: { nextAllowedAt?: unknown };
    };
    const message = typeof body.message === "string" && body.message.trim()
      ? body.message
      : typeof body.detail === "string" && body.detail.trim()
        ? body.detail
        : "Hotel sync could not be completed. Please try again.";
    const nextAllowedAt = typeof body.nextAllowedAt === "string"
      ? body.nextAllowedAt
      : typeof body.properties?.nextAllowedAt === "string"
        ? body.properties.nextAllowedAt
        : null;
    return new HotelSyncRequestError(message, nextAllowedAt);
  }
  return new HotelSyncRequestError("Hotel sync could not be completed. Please try again.");
}

async function fetchHotelSnapshot(hotelId: string, signal?: AbortSignal): Promise<HotelSnapshot> {
  const response = await fetch(`/api/hotels/${encodeURIComponent(hotelId)}`, {
    cache: "no-store",
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw new Error(getErrorMessage(payload));
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    throw new Error("Hotel details could not be loaded in the expected format.");
  }
  return payload as HotelSnapshot;
}

async function postHotelSync(url: string, hotelId: string, body?: unknown): Promise<HotelSyncResponse> {
  const response = await fetch(url, {
    method: "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw getSyncError(payload);
  if (!isHotelSyncResponse(payload, hotelId)) {
    throw new Error("The sync service returned an unexpected response.");
  }
  return payload;
}

async function fetchPendingHotelSync(hotelId: string, continuationToken: string): Promise<HotelSyncResponse> {
  const response = await fetch(
    `/api/hotels/${encodeURIComponent(hotelId)}/sync/pending?continuationToken=${encodeURIComponent(continuationToken)}`,
    { cache: "no-store" },
  );
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw getSyncError(payload);
  if (!isHotelSyncResponse(payload, hotelId)) {
    throw new Error("The pending sync preview could not be restored in the expected format.");
  }
  return payload;
}

function isHotelSyncResponse(payload: unknown, hotelId: string): payload is HotelSyncResponse {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return false;
  const syncResponse = payload as Partial<HotelSyncResponse>;
  const counts = [
    syncResponse.currentPage,
    syncResponse.totalPages,
    syncResponse.pagesFetched,
    syncResponse.reviewsReceived,
    syncResponse.reviewsAdded,
    syncResponse.reviewsSkipped,
    syncResponse.totalReviewsAdded,
    syncResponse.totalReviewsSkipped,
    syncResponse.previewNewReviews,
    syncResponse.previewAlreadyStoredReviews,
    syncResponse.previewDuplicateReviews,
    syncResponse.previewBeyondBoundaryReviews,
  ];
  const validReviewPreviews = Array.isArray(syncResponse.reviewPreviews)
    && syncResponse.reviewPreviews.every((preview) => typeof preview === "object"
      && preview !== null
      && typeof preview.sourcePosition === "number"
      && Number.isInteger(preview.sourcePosition)
      && (preview.sourceReviewId === null || typeof preview.sourceReviewId === "string")
      && typeof preview.classification === "string"
      && typeof preview.review === "object"
      && preview.review !== null
      && !Array.isArray(preview.review));
  const validSnapshotPreview = syncResponse.snapshotPreview === null
    || (typeof syncResponse.snapshotPreview === "object"
      && syncResponse.snapshotPreview !== null
      && !Array.isArray(syncResponse.snapshotPreview));
  return typeof syncResponse.hotelId === "string"
    && syncResponse.hotelId === hotelId
    && typeof syncResponse.hotelName === "string"
    && typeof syncResponse.message === "string"
    && typeof syncResponse.hasMore === "boolean"
    && typeof syncResponse.hotelDetailsUpdated === "boolean"
    && typeof syncResponse.unrestrictedSyncRecommended === "boolean"
    && typeof syncResponse.awaitingConfirmation === "boolean"
    && (syncResponse.pendingBatchId === null || typeof syncResponse.pendingBatchId === "string")
    && validSnapshotPreview
    && validReviewPreviews
    && counts.every((value) => typeof value === "number" && Number.isInteger(value) && value >= 0)
    && (syncResponse.nextPage === null
      || (typeof syncResponse.nextPage === "number" && Number.isInteger(syncResponse.nextPage) && syncResponse.nextPage >= 1))
    && (syncResponse.continuationToken === null || typeof syncResponse.continuationToken === "string")
    && (syncResponse.nextAllowedAt === null
      || (typeof syncResponse.nextAllowedAt === "string" && Number.isFinite(Date.parse(syncResponse.nextAllowedAt))))
    && (!syncResponse.hasMore || Boolean(syncResponse.continuationToken && syncResponse.nextPage))
    && (syncResponse.hasMore || syncResponse.nextPage === null)
    && (syncResponse.awaitingConfirmation
      ? Boolean(syncResponse.pendingBatchId && syncResponse.continuationToken)
      : syncResponse.pendingBatchId === null && syncResponse.snapshotPreview === null)
    && (syncResponse.awaitingConfirmation || syncResponse.hasMore || syncResponse.continuationToken === null);
}

function hotelSyncStorageKey(hotelId: string): string {
  return `azzurro:hotel-sync:${encodeURIComponent(hotelId)}`;
}

function formatDate(value?: string): string {
  if (!value) return "Collection date unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Collection date unavailable";
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function formatLabel(value?: string): string {
  if (!value) return "";
  return value.replace(/[_-]+/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function getRatingLabel(value?: string): string {
  if (!value) return "Guest rating";
  return ratingLabels[value] ?? formatLabel(value);
}

function safeImageUrl(value?: string): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    const isBookingImageHost = url.hostname === "bstatic.com" || url.hostname.endsWith(".bstatic.com");
    return url.protocol === "https:" && isBookingImageHost ? url.href : null;
  } catch {
    return null;
  }
}

function safeBookingUrl(value?: string): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    const isBookingHost = url.hostname === "booking.com" || url.hostname.endsWith(".booking.com");
    return url.protocol === "https:" && isBookingHost ? url.href : null;
  } catch {
    return null;
  }
}

function reviewText(review: Record<string, unknown>, field: string): string | null {
  const value = review[field];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function reviewNumber(review: Record<string, unknown>, field: string): number | null {
  const value = review[field];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function formatTimeWindow(value?: TimeWindow): string | null {
  if (!value?.from && !value?.until) return null;
  if (value.from && value.until) return `${value.from} – ${value.until}`;
  return value.from ? `From ${value.from}` : `Until ${value.until}`;
}

function formatBeds(value?: BedInfo | BedInfo[]): string | null {
  const beds = value ? (Array.isArray(value) ? value : [value]) : [];
  const labels = beds.flatMap((bed) => {
    if (!bed.type) return [];
    const count = bed.count ?? 1;
    const bedType = formatLabel(bed.type);
    return [`${count} ${bedType}${count === 1 ? "" : "s"}`];
  });
  return labels.length > 0 ? labels.join(" · ") : null;
}

export default function HotelDetailView({ hotelId }: { hotelId: string }) {
  const [snapshot, setSnapshot] = useState<HotelSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [roomOptionsExpanded, setRoomOptionsExpanded] = useState(true);
  const [facilitiesExpanded, setFacilitiesExpanded] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncingContinuation, setSyncingContinuation] = useState(false);
  const [syncResult, setSyncResult] = useState<HotelSyncResponse | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [syncAllowedAt, setSyncAllowedAt] = useState<string | null>(null);
  const [syncCooldownExpired, setSyncCooldownExpired] = useState(true);
  const [syncStorageReady, setSyncStorageReady] = useState(false);

  function updateSyncCooldown(nextAllowedAt: string | null) {
    setSyncAllowedAt(nextAllowedAt);
    const availableAt = nextAllowedAt ? Date.parse(nextAllowedAt) : Number.NaN;
    setSyncCooldownExpired(!nextAllowedAt || !Number.isFinite(availableAt) || availableAt <= Date.now());
  }

  useEffect(() => {
    const controller = new AbortController();
    void fetchHotelSnapshot(hotelId, controller.signal)
      .then((loadedSnapshot) => setSnapshot(loadedSnapshot))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : "Hotel details could not be loaded.");
        }
      });

    return () => controller.abort();
  }, [hotelId]);

  useEffect(() => {
    let cancelled = false;
    const timeout = window.setTimeout(() => {
      let continuationToken: string | null = null;
      try {
        const storedValue = window.sessionStorage.getItem(hotelSyncStorageKey(hotelId));
        if (storedValue) {
          const stored: unknown = JSON.parse(storedValue);
          if (typeof stored === "object" && stored !== null && !Array.isArray(stored)) {
            const savedSync = stored as Partial<PersistedHotelSyncState>;
            continuationToken = savedSync.version === 2 && typeof savedSync.continuationToken === "string"
              ? savedSync.continuationToken
              : null;
            const savedAllowedAt = savedSync.version === 2 && typeof savedSync.nextAllowedAt === "string"
              && Number.isFinite(Date.parse(savedSync.nextAllowedAt))
              ? savedSync.nextAllowedAt
              : null;

            if (savedAllowedAt) {
              setSyncAllowedAt(savedAllowedAt);
              const availableAt = Date.parse(savedAllowedAt);
              setSyncCooldownExpired(availableAt <= Date.now());
            }
          }
        }
      } catch {
        // Session storage may be unavailable; sync state remains usable for this page visit.
      }
      if (continuationToken) {
        void fetchPendingHotelSync(hotelId, continuationToken)
          .then((pending) => {
            if (cancelled) return;
            setSyncResult(pending);
            if (pending.nextAllowedAt) updateSyncCooldown(pending.nextAllowedAt);
          })
          .catch(() => {
            if (!cancelled) setSyncResult(null);
          })
          .finally(() => {
            if (!cancelled) setSyncStorageReady(true);
          });
      } else {
        setSyncStorageReady(true);
      }
    }, 0);

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
    };
  }, [hotelId]);

  useEffect(() => {
    if (!syncStorageReady) return;
    try {
      const key = hotelSyncStorageKey(hotelId);
      if (syncResult === null && syncAllowedAt === null) {
        window.sessionStorage.removeItem(key);
      } else {
        window.sessionStorage.setItem(key, JSON.stringify({
          version: 2,
          continuationToken: syncResult?.continuationToken ?? null,
          nextAllowedAt: syncAllowedAt,
        } satisfies PersistedHotelSyncState));
      }
    } catch {
      // Keep the current page functional if session storage is unavailable or full.
    }
  }, [hotelId, syncAllowedAt, syncResult, syncStorageReady]);

  useEffect(() => {
    if (!syncAllowedAt) return;
    const availableAt = Date.parse(syncAllowedAt);
    if (!Number.isFinite(availableAt) || availableAt <= Date.now()) return;
    const timeout = window.setTimeout(() => setSyncCooldownExpired(true), availableAt - Date.now());
    return () => window.clearTimeout(timeout);
  }, [syncAllowedAt]);

  const details = snapshot?.hotelDetails;
  const hotelName = snapshot?.hotelName ?? details?.name ?? "Hotel details";
  const location = details?.location;
  const addressText = location?.address || [location?.city, location?.region, location?.country].filter(Boolean).join(", ") || "Location unavailable";
  const rating = details?.rating;
  const score = rating?.score;
  const reviewCount = snapshot?.reviewCount ?? 0;
  const ratingBreakdownByName = new Map<string, RatingSubscore>();
  for (const subscore of rating?.subscores ?? []) {
    if (subscore.name && subscore.name !== "total" && typeof subscore.score === "number") {
      ratingBreakdownByName.set(subscore.name, subscore);
    }
  }
  const ratingBreakdown = Array.from(ratingBreakdownByName.values());
  const bookingUrl = safeBookingUrl(snapshot?.requestedUrl) ?? safeBookingUrl(details?.link);

  const photoCandidates: GalleryPhoto[] = [];
  const heroPhoto = safeImageUrl(details?.image);
  if (heroPhoto) photoCandidates.push({ src: heroPhoto, alt: `${hotelName} property photo` });
  for (const photo of details?.photos ?? []) {
    const src = safeImageUrl(photo.link);
    if (src) photoCandidates.push({ src, alt: photo.caption?.trim() || `${hotelName} room and property` });
  }
  const seenPhotoUrls = new Set<string>();
  const galleryPhotos = photoCandidates
    .filter((photo) => {
      if (seenPhotoUrls.has(photo.src)) return false;
      seenPhotoUrls.add(photo.src);
      return true;
    })
    .slice(0, 4);

  const nearbySections = [
    { key: "landmarks", label: "Landmarks", limit: 3 },
    { key: "restaurants", label: "Places to eat", limit: 2 },
    { key: "public_transport", label: "Public transport", limit: 2 },
    { key: "airports", label: "Airports", limit: 1 },
  ] as const;
  const petsPolicy = details?.policies?.pets;
  const petsLabel = petsPolicy === "NO" ? "Not allowed" : petsPolicy ? formatLabel(petsPolicy) : "Not specified";
  const checkinTime = formatTimeWindow(details?.policies?.checkin);
  const checkoutTime = formatTimeWindow(details?.policies?.checkout);
  const hasSyncContinuation = Boolean(
    syncResult?.hasMore && syncResult.continuationToken && !syncResult.awaitingConfirmation,
  );
  const previewSnapshot = syncResult?.snapshotPreview;
  const previewDetails = previewSnapshot?.hotelDetails;
  const previewLocation = previewDetails?.location;
  const previewAddress = previewLocation?.address
    || [previewLocation?.city, previewLocation?.region, previewLocation?.country].filter(Boolean).join(", ")
    || "Location unavailable";

  async function handleHotelSync() {
    const continuing = hasSyncContinuation && syncResult !== null;
    setSyncing(true);
    setSyncingContinuation(continuing);
    setSyncError(null);

    try {
      let result: HotelSyncResponse;
      if (!continuing) {
        setSyncResult(null);
        result = await postHotelSync(`/api/hotels/${encodeURIComponent(hotelId)}/sync`, hotelId);
      } else {
        result = await postHotelSync(
          `/api/hotels/${encodeURIComponent(hotelId)}/sync/continue`,
          hotelId,
          { continuationToken: syncResult.continuationToken },
        );
      }
      setSyncResult(result);
      updateSyncCooldown(result.nextAllowedAt);
      if (result.hotelDetailsUpdated) {
        try {
          setSnapshot(await fetchHotelSnapshot(hotelId));
          setError(null);
        } catch {
          setSyncError("The hotel details were saved, but the page could not reload them.");
        }
      }
    } catch (cause: unknown) {
      if (cause instanceof HotelSyncRequestError && cause.nextAllowedAt) {
        updateSyncCooldown(cause.nextAllowedAt);
        try {
          setSnapshot(await fetchHotelSnapshot(hotelId));
          setError(null);
        } catch {
          // Keep the sync error visible if the refreshed snapshot is unavailable too.
        }
      }
      setSyncError(cause instanceof Error ? cause.message : "Hotel sync could not be completed.");
    } finally {
      setSyncing(false);
      setSyncingContinuation(false);
    }
  }

  async function handleConfirmSyncPreview() {
    const pending = syncResult;
    if (!pending?.awaitingConfirmation || !pending.continuationToken || !pending.pendingBatchId) return;
    setSyncing(true);
    setSyncError(null);
    try {
      const result = await postHotelSync(
        `/api/hotels/${encodeURIComponent(hotelId)}/sync/confirm`,
        hotelId,
        { continuationToken: pending.continuationToken, batchId: pending.pendingBatchId },
      );
      setSyncResult(result);
      updateSyncCooldown(result.nextAllowedAt);
      if (result.hotelDetailsUpdated) {
        try {
          setSnapshot(await fetchHotelSnapshot(hotelId));
          setError(null);
        } catch {
          setSyncError("The confirmed details were saved, but the hotel page could not reload them.");
        }
      }
    } catch (cause: unknown) {
      setSyncError(cause instanceof Error ? cause.message : "The sync preview could not be confirmed.");
    } finally {
      setSyncing(false);
    }
  }

  async function handleRejectSyncPreview() {
    const pending = syncResult;
    if (!pending?.awaitingConfirmation || !pending.continuationToken || !pending.pendingBatchId) return;
    setSyncing(true);
    setSyncError(null);
    try {
      const result = await postHotelSync(
        `/api/hotels/${encodeURIComponent(hotelId)}/sync/reject`,
        hotelId,
        { continuationToken: pending.continuationToken, batchId: pending.pendingBatchId },
      );
      setSyncResult(result);
      updateSyncCooldown(result.nextAllowedAt);
    } catch (cause: unknown) {
      setSyncError(cause instanceof Error ? cause.message : "The sync preview could not be rejected.");
    } finally {
      setSyncing(false);
    }
  }

  const syncButtonLabel = !syncStorageReady
    ? "Restoring sync…"
    : syncing
      ? syncingContinuation ? "Previewing next page…" : "Fetching sync preview…"
      : syncResult?.awaitingConfirmation
        ? "Preview awaiting your decision"
      : hasSyncContinuation
        ? "Preview next review page"
        : !syncCooldownExpired && syncAllowedAt
          ? `Sync available ${formatDate(syncAllowedAt)}`
          : "Sync latest details & reviews";

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <Link className={styles.backLink} href="/hotels"><ArrowLeft size={15} />All hotels</Link>

        {!snapshot && !error && (
          <div className={styles.messageCard} role="status">Loading hotel details…</div>
        )}

        {error && (
          <div className={`${styles.messageCard} ${styles.errorCard}`} role="alert">
            <CircleAlert size={18} />
            <span>{error}</span>
            <Link href="/hotels">Back to hotels</Link>
          </div>
        )}

        {snapshot && (
          <>
            <section className={styles.propertyProfile} aria-labelledby="page-title">
              {galleryPhotos.length > 0 ? (
                <div className={styles.photoGallery} role="group" aria-label={`${hotelName} photos`}>
                  {galleryPhotos.map((photo, index) => (
                    <div className={`${styles.photoTile} ${index === 0 ? styles.photoLead : ""}`} key={photo.src}>
                      <Image
                        src={photo.src}
                        alt={photo.alt}
                        fill
                        sizes="(max-width: 760px) 100vw, (max-width: 1200px) 70vw, 900px"
                        unoptimized
                      />
                      {index === 0 && (details?.photos?.length ?? 0) > 0 && (
                        <span className={styles.photoCount}>{details?.photos?.length} photos</span>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className={styles.photoPlaceholder}>
                  <Building2 size={34} strokeWidth={1.4} />
                  <span>Property photo unavailable</span>
                </div>
              )}

              <div className={styles.propertyHeading}>
                <div className={styles.propertyIdentity}>
                  <div className={styles.propertyType}>
                    {Array.from({ length: Math.min(5, Math.max(0, rating?.stars ?? 0)) }, (_, index) => (
                      <Star key={index} size={13} fill="currentColor" aria-hidden="true" />
                    ))}
                    <span>{formatLabel(details?.accommodation_type) || "Hotel"}</span>
                  </div>
                  <h1 id="page-title">{hotelName}</h1>
                  <p className={styles.addressLine}>
                    <MapPin size={16} aria-hidden="true" />
                    <span>{addressText}</span>
                  </p>
                  <div className={styles.propertyBadges}>
                    {details?.is_sustainable && <span><Leaf size={13} />Sustainable stay</span>}
                    {details?.is_work_friendly && <span><BriefcaseBusiness size={13} />Work-friendly</span>}
                    {bookingUrl && (
                      <a href={bookingUrl} target="_blank" rel="noreferrer">
                        View on Booking.com <ExternalLink size={13} />
                      </a>
                    )}
                  </div>
                </div>

                <div className={styles.scoreCard}>
                  <div className={styles.scoreValue}>{typeof score === "number" ? score.toFixed(1) : "—"}</div>
                  <div>
                    <strong>{typeof score === "number" ? "Guest rating" : "Rating unavailable"}</strong>
                    <span>{reviewCount.toLocaleString()} guest reviews</span>
                  </div>
                </div>
              </div>
              <p className={styles.collectedAt}>
                <CalendarClock size={14} /> Collected {formatDate(snapshot.collectedAt)}
              </p>
              <div className={styles.syncPanel}>
                <div className={styles.syncPanelHeader}>
                  <div>
                    <strong>Keep this hotel up to date</strong>
                    <span>Refresh property details and the latest guest reviews.</span>
                  </div>
                  <button
                    className={styles.syncButton}
                    type="button"
                    onClick={() => void handleHotelSync()}
                    disabled={!syncStorageReady || syncing || syncResult?.awaitingConfirmation || (!syncCooldownExpired && !hasSyncContinuation)}
                  >
                    <RefreshCw size={15} aria-hidden="true" /> {syncButtonLabel}
                  </button>
                </div>
                {syncResult && (
                  <div className={styles.syncResult} role="status" aria-live="polite">
                    <p>{syncResult.message}</p>
                    {syncResult.awaitingConfirmation ? (
                      <span>
                        Preview: {syncResult.previewNewReviews} new · {syncResult.previewAlreadyStoredReviews} already stored
                        {syncResult.previewDuplicateReviews > 0 ? ` · ${syncResult.previewDuplicateReviews} duplicate` : ""}
                        {syncResult.previewBeyondBoundaryReviews > 0 ? ` · ${syncResult.previewBeyondBoundaryReviews} beyond the existing-review boundary` : ""}.
                        Nothing has been saved yet.
                      </span>
                    ) : (
                      <span>
                        {syncResult.totalReviewsAdded.toLocaleString()} new reviews saved · {syncResult.totalPages > 0
                          ? `${syncResult.pagesFetched} of ${syncResult.totalPages} review pages checked`
                          : `${syncResult.pagesFetched} review pages checked`}
                      </span>
                    )}
                  </div>
                )}
                {syncResult?.unrestrictedSyncRecommended && syncResult.hasMore && (
                  <p className={styles.syncWarning}>
                    All 25 reviews on this page appear new. Each next page will be previewed separately to find the existing-review boundary.
                  </p>
                )}
                {syncResult?.awaitingConfirmation && (
                  <section className={styles.syncPreview} aria-labelledby="sync-preview-heading">
                    <div className={styles.syncPreviewHeading}>
                      <div>
                        <span>Page {syncResult.currentPage} of {syncResult.totalPages}</span>
                        <h3 id="sync-preview-heading">Review before saving</h3>
                      </div>
                      <span className={styles.syncPreviewNotSaved}>Not saved</span>
                    </div>

                    {previewSnapshot && (
                      <div className={styles.syncPreviewDetails}>
                        <h4>Refreshed hotel details</h4>
                        <dl>
                          <div><dt>Name</dt><dd>{previewSnapshot.hotelName ?? previewDetails?.name ?? "Name unavailable"}</dd></div>
                          <div><dt>Location</dt><dd>{previewAddress}</dd></div>
                          <div><dt>Rating</dt><dd>{typeof previewDetails?.rating?.score === "number" ? previewDetails.rating.score.toFixed(1) : "Not provided"}</dd></div>
                          <div><dt>Provider review count</dt><dd>{(previewDetails?.rating?.count ?? previewSnapshot.reviewCount ?? 0).toLocaleString()}</dd></div>
                          <div><dt>Collected</dt><dd>{formatDate(previewSnapshot.collectedAt)}</dd></div>
                        </dl>
                        {previewDetails?.description && <p>{previewDetails.description}</p>}
                        <details className={styles.syncPreviewRawDetails}>
                          <summary>View all refreshed hotel data</summary>
                          <pre>{JSON.stringify(previewSnapshot, null, 2)}</pre>
                        </details>
                      </div>
                    )}

                    <div className={styles.syncPreviewReviews}>
                      <div className={styles.syncPreviewReviewsHeading}>
                        <h4>Review candidates</h4>
                        <span>{syncResult.reviewPreviews.length} fetched</span>
                      </div>
                      {syncResult.reviewPreviews.length === 0 ? (
                        <p>No review records were returned on this page.</p>
                      ) : (
                        <ul>
                          {syncResult.reviewPreviews.map((item, index) => {
                            const title = reviewText(item.review, "title");
                            const positive = reviewText(item.review, "positive");
                            const negative = reviewText(item.review, "negative");
                            const guestValue = item.review.guest;
                            const guest = typeof guestValue === "object" && guestValue !== null && !Array.isArray(guestValue)
                              ? guestValue as Record<string, unknown>
                              : null;
                            const guestName = guest && typeof guest.name === "string" ? guest.name : null;
                            const scoreValue = reviewNumber(item.review, "score");
                            const statusLabel = item.classification === "new"
                              ? "New — no matching ID or review details in database"
                              : item.classification === "already_stored"
                                ? "Already stored — matching ID or review details"
                                : item.classification === "duplicate"
                                  ? "Duplicate in fetched page"
                                  : item.classification === "beyond_boundary"
                                    ? "After existing-review boundary — not imported"
                                    : "Skipped — missing review ID";
                            return (
                              <li className={styles.syncPreviewReview} key={`${item.sourceReviewId ?? "missing"}-${index}`}>
                                <div className={styles.syncPreviewReviewMeta}>
                                  <span className={styles.syncReviewClassification} data-classification={item.classification}>
                                    {statusLabel}
                                  </span>
                                  <span>{item.sourceReviewId ? `ID ${item.sourceReviewId}` : "ID unavailable"}</span>
                                </div>
                                <strong>{title || (guestName ? `Review by ${guestName}` : "Guest review")}</strong>
                                <span className={styles.syncPreviewReviewFacts}>
                                  {scoreValue === null ? "Score unavailable" : `Score ${scoreValue}`}
                                  {reviewText(item.review, "date") ? ` · ${reviewText(item.review, "date")}` : ""}
                                  {guestName ? ` · ${guestName}` : ""}
                                </span>
                                {positive && <p><b>Positive:</b> {positive}</p>}
                                {negative && <p><b>Negative:</b> {negative}</p>}
                                <details className={styles.syncPreviewReviewDetails}>
                                  <summary>View complete fetched review data</summary>
                                  <pre>{JSON.stringify(item.review, null, 2)}</pre>
                                </details>
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </div>

                    <div className={styles.syncPreviewActions}>
                      <button
                        className={styles.syncConfirmButton}
                        type="button"
                        onClick={() => void handleConfirmSyncPreview()}
                        disabled={syncing}
                      >
                        Confirm and save this page
                      </button>
                      <button
                        className={styles.syncRejectButton}
                        type="button"
                        onClick={() => void handleRejectSyncPreview()}
                        disabled={syncing}
                      >
                        Reject and stop sync
                      </button>
                    </div>
                    <p className={styles.syncPreviewNote}>
                      Confirm saves only this page. Reject discards it and stops continuation; previously confirmed pages stay saved.
                    </p>
                  </section>
                )}
                {syncError && <p className={styles.syncError} role="alert">{syncError}</p>}
                {syncAllowedAt && !syncCooldownExpired && (
                  <p className={styles.syncCooldown}>
                    A new sync is available {formatDate(syncAllowedAt)}. You can continue this sync run in the meantime.
                  </p>
                )}
              </div>
            </section>

            <div className={styles.detailGrid}>
              <div className={styles.primaryColumn}>
                <section className={styles.section} aria-labelledby="about-heading">
                  <div className={styles.sectionHeading}>
                    <div><p>At a glance</p><h2 id="about-heading">About this stay</h2></div>
                  </div>
                  <p className={styles.description}>
                    {details?.description || "No property description is available."}
                  </p>
                  {details?.fine_print && (
                    <details className={styles.propertyNotes}>
                      <summary>Property notes and conditions</summary>
                      <p>{details.fine_print}</p>
                    </details>
                  )}
                </section>

                {!!details?.rooms?.length && (
                  <section className={styles.section} aria-labelledby="rooms-heading">
                    <div className={styles.sectionHeading}>
                      <div><p>Where you’ll sleep</p><h2 id="rooms-heading">Room options</h2></div>
                      <div className={styles.sectionHeadingActions}>
                        <span className={styles.sectionCount}>{details.rooms.length} room types</span>
                        <SectionToggle
                          label="room options"
                          controls="room-options-content"
                          expanded={roomOptionsExpanded}
                          onToggle={() => setRoomOptionsExpanded((expanded) => !expanded)}
                        />
                      </div>
                    </div>
                    <div id="room-options-content" hidden={!roomOptionsExpanded}>
                      <div className={styles.roomGrid}>
                        {details.rooms.map((room, index) => {
                          const roomImage = safeImageUrl(room.photos?.[0]);
                          const bedDescription = formatBeds(room.beds);
                          const highlights = (room.facility_highlights ?? [])
                            .map((item) => typeof item === "string" ? item : item.name)
                            .filter((item): item is string => Boolean(item));

                          return (
                            <article className={styles.roomCard} key={room.id ?? `${room.name}-${index}`}>
                              {roomImage && (
                                <div className={styles.roomImage}>
                                  <Image src={roomImage} alt={room.name ? `${room.name} room` : "Hotel room"} fill sizes="(max-width: 760px) 100vw, 36vw" unoptimized />
                                </div>
                              )}
                              <div className={styles.roomInfo}>
                                <h3>{room.name || `Room option ${index + 1}`}</h3>
                                {room.description && <p>{room.description}</p>}
                                <div className={styles.roomFacts}>
                                  {typeof room.size_m2 === "number" && <span><Maximize2 size={14} />{room.size_m2} m²</span>}
                                  {typeof room.max_occupancy === "number" && <span><Users size={14} />Up to {room.max_occupancy}</span>}
                                  {bedDescription && <span><BedDouble size={14} />{bedDescription}</span>}
                                </div>
                                {highlights.length > 0 && <p className={styles.roomHighlights}>{highlights.join(" · ")}</p>}
                              </div>
                            </article>
                          );
                        })}
                      </div>
                    </div>
                  </section>
                )}

                {!!details?.facilities?.length && (
                  <section className={styles.section} aria-labelledby="facilities-heading">
                    <div className={styles.sectionHeading}>
                      <div><p>On the property</p><h2 id="facilities-heading">Facilities</h2></div>
                      <SectionToggle
                        label="facilities"
                        controls="facilities-content"
                        expanded={facilitiesExpanded}
                        onToggle={() => setFacilitiesExpanded((expanded) => !expanded)}
                      />
                    </div>
                    <div id="facilities-content" hidden={!facilitiesExpanded}>
                      <ul className={styles.facilityList}>
                        {details.facilities.map((facility, index) => (
                          <li key={`${facility.id ?? facility.name ?? "facility"}-${index}`}><Check size={15} />{facility.name || "Facility"}</li>
                        ))}
                      </ul>
                    </div>
                  </section>
                )}

                <section className={styles.section} aria-labelledby="reviews-heading">
                  <div className={styles.sectionHeading}>
                    <div><p>Guest experiences</p><h2 id="reviews-heading">Reviews</h2></div>
                    <div className={styles.sectionHeadingActions}>
                      <span className={styles.sectionCount}>{reviewCount.toLocaleString()} guest reviews</span>
                      <Link
                        className={styles.viewReviewsLink}
                        href={`/hotels/${encodeURIComponent(hotelId)}/reviews`}
                      >
                        View reviews <ArrowRight size={15} aria-hidden="true" />
                      </Link>
                    </div>
                  </div>
                </section>
              </div>

              <aside className={styles.sidebar} aria-label="Hotel information">
                <section className={styles.sideCard} aria-labelledby="location-heading">
                  <div className={styles.sideHeading}><MapPin size={17} /><h2 id="location-heading">Location</h2></div>
                  <p className={styles.address}>{addressText}</p>
                  {location?.city && <p className={styles.locationSubline}>{[location.city, location.region, location.country].filter(Boolean).join(", ")}</p>}
                  {typeof location?.latitude === "number" && typeof location.longitude === "number" && (
                    <a className={styles.mapLink} href={`https://www.google.com/maps?q=${location.latitude},${location.longitude}`} target="_blank" rel="noreferrer">
                      Open map <ExternalLink size={13} />
                    </a>
                  )}
                </section>

                {(checkinTime || checkoutTime || details?.policies) && (
                  <section className={styles.sideCard} aria-labelledby="policies-heading">
                    <div className={styles.sideHeading}><CalendarClock size={17} /><h2 id="policies-heading">Before you book</h2></div>
                    <dl className={styles.policyList}>
                      {checkinTime && <div><dt><Clock3 size={14} />Check-in</dt><dd>{checkinTime}</dd></div>}
                      {checkoutTime && <div><dt><Clock3 size={14} />Check-out</dt><dd>{checkoutTime}</dd></div>}
                      {details?.policies?.allows_children !== undefined && (
                        <div><dt><Baby size={14} />Children</dt><dd>{details.policies.allows_children ? "Welcome" : "Not allowed"}</dd></div>
                      )}
                      {details?.policies?.pets && <div><dt><PawPrint size={14} />Pets</dt><dd>{petsLabel}</dd></div>}
                    </dl>
                    {!!details?.policies?.accepted_payments?.length && (
                      <div className={styles.paymentMethods}>
                        <p><CreditCard size={14} />Accepted payment</p>
                        <span>{details.policies.accepted_payments.join(" · ")}</span>
                      </div>
                    )}
                  </section>
                )}

                {!!details?.languages_spoken?.length && (
                  <section className={styles.sideCard} aria-labelledby="languages-heading">
                    <div className={styles.sideHeading}><Languages size={17} /><h2 id="languages-heading">Languages spoken</h2></div>
                    <p className={styles.languageList}>
                      {details.languages_spoken.map((language) => languageNames[language] ?? language.toUpperCase()).join(", ")}
                    </p>
                  </section>
                )}

                {!!ratingBreakdown.length && (
                  <section className={styles.sideCard} aria-labelledby="rating-heading">
                    <div className={styles.sideHeading}><Star size={17} /><h2 id="rating-heading">Guest ratings</h2></div>
                    <div className={styles.ratingBreakdown}>
                      {ratingBreakdown.map((item) => (
                        <div className={styles.ratingRow} key={item.name}>
                          <div className={styles.ratingRowLabel}>
                            <span>{getRatingLabel(item.name)}</span>
                            <strong>{item.score?.toFixed(1)}</strong>
                          </div>
                          <div className={styles.ratingTrack} aria-hidden="true"><span style={{ width: `${Math.min(100, Math.max(0, (item.score ?? 0) * 10))}%` }} /></div>
                          {typeof item.count === "number" && <small>{item.count} ratings</small>}
                        </div>
                      ))}
                    </div>
                  </section>
                )}

                {!!details?.surroundings && (
                  <section className={styles.sideCard} aria-labelledby="nearby-heading">
                    <div className={styles.sideHeading}><MapPin size={17} /><h2 id="nearby-heading">Nearby</h2></div>
                    <div className={styles.nearbyGroups}>
                      {nearbySections.map(({ key, label, limit }) => {
                        const places = details.surroundings?.[key]?.filter((place) => place.name).slice(0, limit) ?? [];
                        if (places.length === 0) return null;
                        return (
                          <div className={styles.nearbyGroup} key={key}>
                            <h3>{label}</h3>
                            <ul>
                              {places.map((place, index) => (
                                <li key={`${place.name}-${index}`}>
                                  <span>{place.name}</span>
                                  <small>{[place.distance, place.walk_time].filter(Boolean).join(" · ")}</small>
                                </li>
                              ))}
                            </ul>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                )}
              </aside>
            </div>

            <footer className={styles.footer}>
              <span>{snapshot.source ?? "Hotel collection"}</span>
              <span>Collected {formatDate(snapshot.collectedAt)}</span>
            </footer>
          </>
        )}
      </div>
    </main>
  );
}
