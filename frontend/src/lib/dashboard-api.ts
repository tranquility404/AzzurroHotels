import type {
  DashboardOverview,
  DashboardReviewPage,
  PeriodPreset,
} from "@/lib/dashboard-analytics";

export type DashboardOverviewFilters = {
  period: PeriodPreset;
  startDate?: string;
  endDate?: string;
  propertyId: string;
};

export type DashboardReviewFilters = {
  startDate: string;
  endDate: string;
  propertyId: string;
  sentiment: string;
  search: string;
  sort: DashboardReviewSort;
  page: number;
};

export type DashboardReviewSort = "date_desc" | "date_asc" | "score_desc" | "score_asc" | "reviewer_asc";

function errorMessage(payload: unknown, fallback: string): string {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return fallback;
  const fields = payload as Record<string, unknown>;
  for (const candidate of [fields.message, fields.detail, fields.title]) {
    if (typeof candidate === "string" && candidate.trim()) return candidate;
  }
  return fallback;
}

async function fetchJson<T>(url: string, signal: AbortSignal, fallback: string): Promise<T> {
  const response = await fetch(url, { cache: "no-store", signal });
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw new Error(errorMessage(payload, fallback));
  return payload as T;
}

export function fetchDashboardOverview(
  filters: DashboardOverviewFilters,
  signal: AbortSignal,
): Promise<DashboardOverview> {
  const query = new URLSearchParams({ period: filters.period });
  if (filters.startDate) query.set("startDate", filters.startDate);
  if (filters.endDate) query.set("endDate", filters.endDate);
  if (filters.propertyId !== "all") query.set("propertyId", filters.propertyId);
  return fetchJson(
    `/api/dashboard?${query}`,
    signal,
    "Dashboard statistics are unavailable. Start the backend and try again.",
  );
}

export function fetchDashboardReviews(
  filters: DashboardReviewFilters,
  signal: AbortSignal,
): Promise<DashboardReviewPage> {
  const query = new URLSearchParams({
    startDate: filters.startDate,
    endDate: filters.endDate,
    sentiment: filters.sentiment,
    sort: filters.sort,
    page: String(filters.page),
    size: "20",
  });
  if (filters.propertyId !== "all") query.set("propertyId", filters.propertyId);
  if (filters.search.trim()) query.set("search", filters.search.trim());
  return fetchJson(
    `/api/dashboard/reviews?${query}`,
    signal,
    "Dashboard reviews are unavailable. Start the backend and try again.",
  );
}
