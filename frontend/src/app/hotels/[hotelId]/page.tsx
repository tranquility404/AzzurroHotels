import { Suspense } from "react";
import HotelDetailView from "./hotel-detail-view";
import styles from "./page.module.scss";

export default function HotelDetailPage({
  params,
}: {
  params: Promise<{ hotelId: string }>;
}) {
  return (
    <Suspense fallback={<div className={styles.prerenderFallback} role="status">Loading hotel details…</div>}>
      <HotelDetailRoute params={params} />
    </Suspense>
  );
}

async function HotelDetailRoute({ params }: { params: Promise<{ hotelId: string }> }) {
  const { hotelId } = await params;
  return <HotelDetailView key={hotelId} hotelId={hotelId} />;
}
