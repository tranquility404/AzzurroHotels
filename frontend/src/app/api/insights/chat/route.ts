import { proxyInsightsChat } from "./proxy";

export async function POST(request: Request) {
  return proxyInsightsChat(request, false);
}
