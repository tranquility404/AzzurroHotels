const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";

export async function POST(
  _request: Request,
  context: { params: Promise<{ hotelId: string }> },
) {
  const { hotelId } = await context.params;
  if (!/^[A-Za-z0-9_-]+$/.test(hotelId)) {
    return Response.json(
      { message: "The hotel id is invalid." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }

  try {
    const upstream = await fetch(new URL(`/api/hotels/${encodeURIComponent(hotelId)}/sync`, backendUrl), {
      method: "POST",
      cache: "no-store",
      signal: AbortSignal.timeout(120_000),
    });

    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: {
        "Content-Type": upstream.headers.get("Content-Type") ?? "application/json",
        "Cache-Control": "no-store",
      },
    });
  } catch {
    return Response.json(
      { message: "The hotel sync service is unavailable. Start the backend and try again." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
