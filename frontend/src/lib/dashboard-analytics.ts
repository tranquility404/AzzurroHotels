export type PeriodPreset = "week" | "7d" | "30d" | "90d" | "custom";

export type DashboardProperty = {
  id: string;
  name: string;
  city: string;
  country: string;
};

export type DashboardPeriod = {
  preset: PeriodPreset;
  startDate: string;
  endDate: string;
  previousStartDate: string;
  previousEndDate: string;
  dayCount: number;
  comparisonLabel: string;
};

export type DashboardStats = {
  count: number;
  average: number | null;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  positiveShare: number;
  replyCount: number;
  replyRate: number;
};

export type DashboardDeltas = {
  averageRating: number | null;
  reviewCountPercent: number | null;
  positiveSharePoints: number | null;
  replyRatePoints: number | null;
};

export type PropertyBreakdown = {
  property: DashboardProperty;
  reviewCount: number;
  average: number | null;
  delta: number | null;
  previousReviewCount: number;
};

export type WeeklyPoint = {
  startDate: string;
  endDate: string;
  label: string;
  positive: number;
  neutral: number;
  negative: number;
  total: number;
};

export type DashboardTrend = {
  points: WeeklyPoint[];
  dailyPoints: Array<{
    date: string;
    /** Index 0 corresponds to score 1, up to index 9 for score 10. */
    scoreCounts: number[];
    total: number;
  }>;
  positiveTotal: number;
  negativeTotal: number;
  positiveVsNegativePercent: number | null;
  averagePositivePerWeek: number;
  bestWeekLabel: string | null;
  accessibleSummary: string;
};

export type OperationalTopic = {
  topic: string;
  reviewCount: number;
  positiveReviewCount: number;
  neutralReviewCount: number;
  negativeReviewCount: number;
  positiveReviewSharePercent: number | null;
  negativeReviewSharePercent: number | null;
};

export type DashboardOverview = {
  asOfDate: string;
  period: DashboardPeriod;
  properties: DashboardProperty[];
  current: DashboardStats;
  previous: DashboardStats;
  deltas: DashboardDeltas;
  reportingProperties: number;
  propertyPerformance: PropertyBreakdown[];
  trend: DashboardTrend;
  operationalTopics: OperationalTopic[];
};

export type DashboardReview = {
  id: string;
  propertyId: string;
  propertyName: string;
  propertyCity: string;
  score: number;
  sentiment: "positive" | "neutral" | "negative";
  title: string;
  date: string;
  positive: string | null;
  negative: string | null;
  guestName: string;
  guestCountry: string;
  guestType: string;
  roomName: string;
  nights: number | null;
  language: string;
  ownerReply: string | null;
  positiveTopics: string[];
  negativeTopics: string[];
};

export type DashboardReviewPage = {
  data: DashboardReview[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export function toDayKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function parseDayKey(key: string): Date {
  const [year, month, day] = key.split("-").map(Number);
  return new Date(year, (month ?? 1) - 1, day ?? 1);
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  next.setDate(next.getDate() + days);
  return next;
}

const MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function formatDayRange(startKey: string, endKey: string): string {
  const start = parseDayKey(startKey);
  const end = parseDayKey(endKey);
  const startMonth = MONTHS_SHORT[start.getMonth()];
  const endMonth = MONTHS_SHORT[end.getMonth()];

  if (start.getFullYear() !== end.getFullYear()) {
    return `${start.getDate()} ${startMonth} ${start.getFullYear()} – ${end.getDate()} ${endMonth} ${end.getFullYear()}`;
  }
  if (start.getMonth() === end.getMonth()) {
    return `${start.getDate()}–${end.getDate()} ${endMonth} ${end.getFullYear()}`;
  }
  return `${start.getDate()} ${startMonth} – ${end.getDate()} ${endMonth} ${end.getFullYear()}`;
}

export function formatShortDay(key: string): string {
  const date = parseDayKey(key);
  return `${date.getDate()} ${MONTHS_SHORT[date.getMonth()]}`;
}

export function formatLongDay(key: string): string {
  const date = parseDayKey(key);
  const weekday = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][date.getDay()];
  return `${weekday}, ${date.getDate()} ${MONTHS_SHORT[date.getMonth()]} ${date.getFullYear()}`;
}
