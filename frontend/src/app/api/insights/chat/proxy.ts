const backendUrl = process.env.AZZURRO_API_URL ?? "http://127.0.0.1:8080";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_BODY_BYTES = 16_384;
const MAX_MESSAGE_LENGTH = 2_000;

function errorResponse(message: string, status: number) {
  return Response.json(
    { title: "Guest insights assistant", detail: message },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}

export async function proxyInsightsChat(request: Request, streaming: boolean): Promise<Response> {
  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > MAX_BODY_BYTES) {
    return errorResponse("The chat request is too large.", 413);
  }

  let rawBody: string;
  try {
    rawBody = await request.text();
  } catch {
    return errorResponse("Send a valid JSON chat request.", 400);
  }

  if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
    return errorResponse("The chat request is too large.", 413);
  }

  let payload: unknown;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return errorResponse("Send a valid JSON chat request.", 400);
  }

  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    return errorResponse("Send a conversation ID and a question.", 400);
  }

  const body = payload as Record<string, unknown>;
  const conversationId = body.conversationId;
  const message = body.message;
  if (typeof conversationId !== "string" || !UUID_PATTERN.test(conversationId)) {
    return errorResponse("Start a valid chat conversation before sending a question.", 400);
  }
  if (typeof message !== "string" || !message.trim() || message.trim().length > MAX_MESSAGE_LENGTH) {
    return errorResponse("Questions must contain 1 to 2,000 characters.", 400);
  }

  try {
    const upstream = await fetch(new URL(`/api/insights/chat${streaming ? "/stream" : ""}`, backendUrl), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: streaming ? "text/event-stream" : "application/json",
      },
      body: JSON.stringify({ conversationId, message: message.trim() }),
      cache: "no-store",
      signal: AbortSignal.timeout(120_000),
    });

    const headers = new Headers({
      "Content-Type": upstream.headers.get("Content-Type") ?? (streaming ? "text/event-stream" : "application/json"),
      "Cache-Control": streaming ? "no-cache, no-transform" : "no-store",
    });
    if (streaming && upstream.ok) headers.set("X-Accel-Buffering", "no");

    return new Response(upstream.body, { status: upstream.status, headers });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") {
      return errorResponse("The answer took too long. Please try a shorter question.", 504);
    }
    return errorResponse("The guest insights assistant is unavailable. Check that the backend is running.", 502);
  }
}
