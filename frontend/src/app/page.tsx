import type { Metadata } from "next";
import InsightsChat from "./dashboard/components/insights-chat";
import OperationsDashboard from "./dashboard/operations-dashboard";

export const metadata: Metadata = {
  title: "Operations dashboard | Azzurro",
  description: "Guest review operations dashboard for the Azzurro hotel collection.",
};

export default function Home() {
  return (
    <>
      <OperationsDashboard />
      <InsightsChat />
    </>
  );
}