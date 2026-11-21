const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function POST(
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

  const payload: unknown = await request.json().catch(() => null);
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    return Response.json(
      { message: "The hotel sync rejection is invalid." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }
  const requestBody = payload as { continuationToken?: unknown; batchId?: unknown };
  if (typeof requestBody.continuationToken !== "string" || !uuidPattern.test(requestBody.continuationToken)
    || typeof requestBody.batchId !== "string" || !uuidPattern.test(requestBody.batchId)) {
    return Response.json(
      { message: "The hotel sync token or batch id is invalid." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }

  try {
    const upstream = await fetch(new URL(`/api/hotels/${encodeURIComponent(hotelId)}/sync/reject`, backendUrl), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        continuationToken: requestBody.continuationToken,
        batchId: requestBody.batchId,
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(30_000),
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
      { message: "The hotel sync rejection service is unavailable. Try rejecting again." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
