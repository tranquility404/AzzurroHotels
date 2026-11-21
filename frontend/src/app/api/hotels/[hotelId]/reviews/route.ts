const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";

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

  const requestUrl = new URL(request.url);
  const upstreamUrl = new URL(`/api/hotels/${encodeURIComponent(hotelId)}/reviews`, backendUrl);
  for (const parameter of ["cursor", "size"]) {
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
      { message: "The reviews are unavailable. Start the backend and try again." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
