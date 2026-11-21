const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function GET(
  request: Request,
  context: { params: Promise<{ hotelId: string }> },
) {
  const { hotelId } = await context.params;
  if (!/^[A-Za-z0-9_-]+$/.test(hotelId)) {
    return Response.json(
      { message: "The hotel id is invalid." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }

  const continuationToken = new URL(request.url).searchParams.get("continuationToken");
  if (!continuationToken || !uuidPattern.test(continuationToken)) {
    return Response.json(
      { message: "The hotel sync token is invalid." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }

  try {
    const upstream = await fetch(new URL(
      `/api/hotels/${encodeURIComponent(hotelId)}/sync/pending?continuationToken=${encodeURIComponent(continuationToken)}`,
      backendUrl,
    ), {
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
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
      { message: "The hotel sync preview could not be restored. Try reloading the hotel." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
