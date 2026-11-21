const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";

export async function POST(request: Request) {
  try {
    const upstream = await fetch(new URL("/api/hotels/collect", backendUrl), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: await request.text(),
      cache: "no-store",
      signal: AbortSignal.timeout(15 * 60 * 1000),
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
      { message: "The collection service is unavailable. Start the backend and try again." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
