import { Suspense } from "react";
import HotelReviewsView from "../hotel-reviews-view";
import styles from "../page.module.scss";

export default function HotelReviewsPage({
  params,
}: {
  params: Promise<{ hotelId: string }>;
}) {
  return (
    <Suspense fallback={<div className={styles.prerenderFallback} role="status">Loading hotel reviews…</div>}>
      <HotelReviewsRoute params={params} />
    </Suspense>
  );
}

async function HotelReviewsRoute({ params }: { params: Promise<{ hotelId: string }> }) {
  const { hotelId } = await params;
  return <HotelReviewsView key={hotelId} hotelId={hotelId} />;
}
