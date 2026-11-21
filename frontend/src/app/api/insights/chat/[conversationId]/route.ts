const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type RouteContext = {
  params: Promise<{ conversationId: string }>;
};

export async function GET(_request: Request, context: RouteContext) {
  const { conversationId } = await context.params;
  if (!UUID_PATTERN.test(conversationId)) {
    return Response.json(
      { title: "Guest insights assistant", detail: "Use a valid conversation ID." },
      { status: 400, headers: { "Cache-Control": "no-store" } },
    );
  }

  try {
    const upstream = await fetch(
      new URL(`/api/insights/chat/${encodeURIComponent(conversationId)}`, backendUrl),
      { cache: "no-store", signal: AbortSignal.timeout(15_000) },
    );
    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: {
        "Content-Type": upstream.headers.get("Content-Type") ?? "application/json",
        "Cache-Control": "no-store",
      },
    });
  } catch {
    return Response.json(
      { title: "Guest insights assistant", detail: "Conversation history is unavailable." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
