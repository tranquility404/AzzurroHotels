const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";
const FORWARDED_PARAMETERS = [
  "startDate",
  "endDate",
  "propertyId",
  "sentiment",
  "search",
  "page",
  "size",
] as const;

export async function GET(request: Request) {
  const requestUrl = new URL(request.url);
  const upstreamUrl = new URL("/api/dashboard/reviews", backendUrl);
  for (const parameter of FORWARDED_PARAMETERS) {
    const value = requestUrl.searchParams.get(parameter);
    if (value !== null) upstreamUrl.searchParams.set(parameter, value);
  }

  try {
    const upstream = await fetch(upstreamUrl, { cache: "no-store" });
    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: {
        "Content-Type": upstream.headers.get("Content-Type") ?? "application/json",
        "Cache-Control": "no-store",
      },
    });
  } catch {
    return Response.json(
      { message: "Dashboard reviews are unavailable. Start the backend and try again." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
