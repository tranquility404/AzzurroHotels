"use client";

import { FormEvent, KeyboardEvent, ReactNode, useCallback, useEffect, useRef, useState } from "react";
import {
  Bot,
  History,
  LoaderCircle,
  MessageCircle,
  RotateCcw,
  SendHorizontal,
  Sparkles,
  X,
} from "lucide-react";
import styles from "./insights-chat.module.scss";

type ChatMessage = {
  role: "user" | "assistant";
  content: string;
  toolCalls?: string[];
  isStreaming?: boolean;
};

type HistoryResponse = {
  conversationId: string;
  messages: ChatMessage[];
};

type SavedConversation = {
  id: string;
  title: string;
  updatedAt: string;
};

type ChatStreamEvent = {
  type: "tool" | "token" | "done" | "error";
  value: string;
};

const CONVERSATION_KEY = "azzurro.insights.conversation.v1";
const CONVERSATION_INDEX_KEY = "azzurro.insights.conversations.v1";
const MAX_SAVED_CONVERSATIONS = 30;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STARTER_QUESTIONS = [
  "What facilities do your hotels provide?",
  "Do any of the hotels have nearby attractions?",
  "How did one hotel's performance change this month?",
];
const BOLD_MARKDOWN_PATTERN = /(\*\*.+?\*\*|__.+?__)/g;
const UNORDERED_LIST_PATTERN = /^\s*[-*+]\s+(.+?)\s*$/;
const ORDERED_LIST_PATTERN = /^\s*\d+[.)]\s+(.+?)\s*$/;

type MarkdownAlignment = "left" | "center" | "right";

type MarkdownTextBlock = { type: "paragraph" | "unordered-list" | "ordered-list"; lines: string[] };
type MarkdownBlock = MarkdownTextBlock
  | { type: "table"; header: string[]; alignments: MarkdownAlignment[]; rows: string[][] };

function createConversationId(): string {
  return crypto.randomUUID();
}

function getOrCreateConversationId(): string {
  try {
    const existing = window.localStorage.getItem(CONVERSATION_KEY);
    if (existing && UUID_PATTERN.test(existing)) return existing;
    const created = createConversationId();
    window.localStorage.setItem(CONVERSATION_KEY, created);
    return created;
  } catch {
    return createConversationId();
  }
}

function readConversationIndex(): SavedConversation[] {
  if (typeof window === "undefined") return [];
  try {
    const serialized = window.localStorage.getItem(CONVERSATION_INDEX_KEY);
    if (!serialized) return [];
    const parsed: unknown = JSON.parse(serialized);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item): item is SavedConversation => {
        if (typeof item !== "object" || item === null || Array.isArray(item)) return false;
        const candidate = item as Record<string, unknown>;
        return typeof candidate.id === "string" && UUID_PATTERN.test(candidate.id) &&
          typeof candidate.title === "string" && typeof candidate.updatedAt === "string";
      })
      .slice(0, MAX_SAVED_CONVERSATIONS);
  } catch {
    return [];
  }
}

function readCurrentConversationId(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const saved = window.localStorage.getItem(CONVERSATION_KEY);
    return saved && UUID_PATTERN.test(saved) ? saved : null;
  } catch {
    return null;
  }
}

function saveConversationMetadata(id: string, firstQuestion?: string): SavedConversation[] {
  const existing = readConversationIndex();
  const prior = existing.find((conversation) => conversation.id === id);
  const questionTitle = firstQuestion?.replace(/\s+/g, " ").trim();
  const title = prior?.title && prior.title !== "New conversation"
    ? prior.title
    : questionTitle
      ? questionTitle.length > 76 ? `${questionTitle.slice(0, 75)}…` : questionTitle
      : prior?.title ?? "New conversation";
  const next = [
    { id, title, updatedAt: new Date().toISOString() },
    ...existing.filter((conversation) => conversation.id !== id),
  ].slice(0, MAX_SAVED_CONVERSATIONS);

  try {
    window.localStorage.setItem(CONVERSATION_KEY, id);
    window.localStorage.setItem(CONVERSATION_INDEX_KEY, JSON.stringify(next));
  } catch {
    // The conversation still works for this page session if storage is unavailable.
  }
  return next;
}

function formatConversationDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Saved conversation"
    : new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(date);
}

function readError(payload: unknown, fallback: string): string {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return fallback;
  const body = payload as Record<string, unknown>;
  for (const message of [body.detail, body.message, body.title]) {
    if (typeof message === "string" && message.trim()) return message;
  }
  return fallback;
}

function parseStreamFrame(frame: string): ChatStreamEvent | null {
  const data = frame
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
  if (!data) return null;

  try {
    const payload: unknown = JSON.parse(data);
    if (typeof payload !== "object" || payload === null || Array.isArray(payload)) return null;
    const event = payload as Record<string, unknown>;
    if (
      (event.type !== "tool" && event.type !== "token" && event.type !== "done" && event.type !== "error") ||
      typeof event.value !== "string"
    ) return null;
    return { type: event.type, value: event.value };
  } catch {
    return null;
  }
}

function renderInlineMarkdown(text: string, keyPrefix: string): ReactNode[] {
  return text.split(BOLD_MARKDOWN_PATTERN).map((part, index) => {
    const isAsteriskBold = part.startsWith("**") && part.endsWith("**") && part.length > 4;
    const isUnderscoreBold = part.startsWith("__") && part.endsWith("__") && part.length > 4;
    if (isAsteriskBold || isUnderscoreBold) {
      return <strong key={`${keyPrefix}-${index}`}>{part.slice(2, -2)}</strong>;
    }
    return part;
  });
}

function splitMarkdownTableRow(line: string): string[] {
  return line.trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

function getMarkdownTableAlignments(line: string): MarkdownAlignment[] | null {
  const cells = splitMarkdownTableRow(line);
  if (cells.length < 2 || !cells.every((cell) => /^:?-{3,}:?$/.test(cell))) return null;

  return cells.map((cell) => {
    const startsWithColon = cell.startsWith(":");
    const endsWithColon = cell.endsWith(":");
    if (startsWithColon && endsWithColon) return "center";
    return endsWithColon ? "right" : "left";
  });
}

function renderAssistantMarkdown(markdown: string): ReactNode {
  const blocks: MarkdownBlock[] = [];
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  let activeBlock: MarkdownTextBlock | undefined;

  for (let lineIndex = 0; lineIndex < lines.length;) {
    const line = lines[lineIndex];
    let separatorIndex = lineIndex + 1;
    while (lines[separatorIndex]?.trim() === "|") separatorIndex += 1;
    const alignments = line.includes("|") ? getMarkdownTableAlignments(lines[separatorIndex] ?? "") : null;

    if (alignments) {
      if (activeBlock) blocks.push(activeBlock);
      activeBlock = undefined;

      const header = splitMarkdownTableRow(line);
      const rows: string[][] = [];
      lineIndex = separatorIndex + 1;
      while (lineIndex < lines.length) {
        const row = lines[lineIndex];
        if (!row.trim() || !row.includes("|") || row.trim() === "|") break;
        rows.push(splitMarkdownTableRow(row));
        lineIndex += 1;
      }

      const columnCount = Math.max(header.length, alignments.length, ...rows.map((row) => row.length));
      if (header.length === columnCount - 1) header.unshift("");
      while (header.length < columnCount) header.push("");
      blocks.push({
        type: "table",
        header,
        alignments: Array.from({ length: columnCount }, (_, index) => alignments[index] ?? "left"),
        rows: rows.map((row) => Array.from({ length: columnCount }, (_, index) => row[index] ?? "")),
      });
      continue;
    }

    lineIndex += 1;
    if (!line.trim()) {
      if (activeBlock) blocks.push(activeBlock);
      activeBlock = undefined;
      continue;
    }

    const unorderedItem = line.match(UNORDERED_LIST_PATTERN);
    const orderedItem = line.match(ORDERED_LIST_PATTERN);
    if (unorderedItem || orderedItem) {
      const type = unorderedItem ? "unordered-list" : "ordered-list";
      if (activeBlock?.type !== type) {
        if (activeBlock) blocks.push(activeBlock);
        activeBlock = { type, lines: [] };
      }
      activeBlock?.lines.push((unorderedItem ?? orderedItem)![1]);
      continue;
    }

    if (activeBlock?.type !== "paragraph") {
      if (activeBlock) blocks.push(activeBlock);
      activeBlock = { type: "paragraph", lines: [] };
    }
    activeBlock?.lines.push(line);
  }
  if (activeBlock) blocks.push(activeBlock);

  return blocks.map((block, blockIndex) => {
    if (block.type === "table") {
      return (
        <div className={styles.tableWrap} key={`table-${blockIndex}`}>
          <table>
            <thead>
              <tr>
                {block.header.map((cell, cellIndex) => (
                  <th key={`header-${cellIndex}`} scope="col" style={{ textAlign: block.alignments[cellIndex] }}>
                    {renderInlineMarkdown(cell || (cellIndex === 0 ? "Metric" : ""), `table-${blockIndex}-header-${cellIndex}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, rowIndex) => (
                <tr key={`row-${rowIndex}`}>
                  {row.map((cell, cellIndex) => (
                    <td key={`cell-${cellIndex}`} style={{ textAlign: block.alignments[cellIndex] }}>
                      {renderInlineMarkdown(cell, `table-${blockIndex}-row-${rowIndex}-cell-${cellIndex}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    }

    if (block.type === "unordered-list" || block.type === "ordered-list") {
      const List = block.type === "unordered-list" ? "ul" : "ol";
      return (
        <List key={`list-${blockIndex}`}>
          {block.lines.map((item, itemIndex) => (
            <li key={`item-${itemIndex}`}>{renderInlineMarkdown(item, `list-${blockIndex}-${itemIndex}`)}</li>
          ))}
        </List>
      );
    }

    return (
      <p key={`paragraph-${blockIndex}`}>
        {block.lines.map((line, lineIndex) => (
          <span key={`line-${lineIndex}`}>
            {lineIndex > 0 ? <br /> : null}
            {renderInlineMarkdown(line, `paragraph-${blockIndex}-${lineIndex}`)}
          </span>
        ))}
      </p>
    );
  });
}

async function consumeChatStream(
  response: Response,
  onEvent: (event: ChatStreamEvent) => void,
): Promise<void> {
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null);
    throw new Error(readError(payload, "The assistant could not answer that question."));
  }
  if (!response.body) throw new Error("The assistant stream did not start. Please try again.");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  const dispatchFrame = (frame: string) => {
    const event = parseStreamFrame(frame);
    if (event) onEvent(event);
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      buffer = buffer.replace(/\r\n/g, "\n").replace(/\r/g, "\n");

      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        dispatchFrame(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf("\n\n");
      }

      if (done) {
        if (buffer.trim()) dispatchFrame(buffer);
        return;
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function updateLastAssistant(
  messages: ChatMessage[],
  update: (message: ChatMessage) => ChatMessage,
): ChatMessage[] {
  const lastIndex = messages.length - 1;
  const lastMessage = messages[lastIndex];
  if (lastMessage?.role !== "assistant") return messages;
  const next = [...messages];
  next[lastIndex] = update(lastMessage);
  return next;
}

export default function InsightsChat() {
  const [isOpen, setIsOpen] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(readCurrentConversationId);
  const [loadedConversationId, setLoadedConversationId] = useState<string | null>(null);
  const [conversations, setConversations] = useState<SavedConversation[]>(readConversationIndex);
  const [showConversationExplorer, setShowConversationExplorer] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isLoadingHistory = isOpen && !!conversationId && loadedConversationId !== conversationId;
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen || !conversationId || loadedConversationId === conversationId) return;
    const controller = new AbortController();

    void fetch(`/api/insights/chat/${encodeURIComponent(conversationId)}`, {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        const payload: unknown = await response.json().catch(() => null);
        if (!response.ok) throw new Error(readError(payload, "Could not load this conversation."));
        const history = payload as HistoryResponse;
        setMessages(Array.isArray(history.messages) ? history.messages : []);
        setLoadedConversationId(conversationId);
      })
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === "AbortError") return;
        setError(caught instanceof Error ? caught.message : "Could not load this conversation.");
        setLoadedConversationId(conversationId);
      });
    return () => controller.abort();
  }, [conversationId, isOpen, loadedConversationId]);

  useEffect(() => {
    if (isOpen && !isLoadingHistory) inputRef.current?.focus();
  }, [isLoadingHistory, isOpen]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: isLoading ? "auto" : "smooth", block: "end" });
  }, [isLoading, isLoadingHistory, messages, pendingQuestion]);

  const closeChat = useCallback(() => {
    setIsOpen(false);
    launcherRef.current?.focus();
  }, []);

  const openChat = useCallback(() => {
    const id = conversationId ?? getOrCreateConversationId();
    if (!conversationId) setConversationId(id);
    setConversations(saveConversationMetadata(id));
    setShowConversationExplorer(false);
    setIsOpen(true);
  }, [conversationId]);

  const startNewConversation = useCallback(() => {
    const nextId = createConversationId();
    setConversationId(nextId);
    setLoadedConversationId(null);
    setMessages([]);
    setDraft("");
    setError(null);
    setPendingQuestion(null);
    setConversations(saveConversationMetadata(nextId));
    setShowConversationExplorer(false);
    inputRef.current?.focus();
  }, []);

  const selectConversation = useCallback((id: string) => {
    setConversationId(id);
    setLoadedConversationId(null);
    setMessages([]);
    setError(null);
    setShowConversationExplorer(false);
    setConversations(saveConversationMetadata(id));
  }, []);

  const sendQuestion = useCallback(
    async (rawQuestion: string) => {
      const question = rawQuestion.trim();
      if (!conversationId || !question || isLoading || question.length > 2_000) return;

      setError(null);
      setConversations(saveConversationMetadata(conversationId, question));
      setIsLoading(true);
      setPendingQuestion(question);
      setDraft("");
      setMessages((current) => [
        ...current,
        { role: "user", content: question },
        { role: "assistant", content: "", toolCalls: [], isStreaming: true },
      ]);
      const controller = new AbortController();
      let receivedAnswer = false;
      let receivedDone = false;
      try {
        const response = await fetch("/api/insights/chat/stream", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ conversationId, message: question }),
          signal: controller.signal,
        });
        await consumeChatStream(response, (event) => {
          if (event.type === "token") {
            receivedAnswer ||= event.value.length > 0;
            setMessages((current) => updateLastAssistant(current, (message) => ({
              ...message,
              content: message.content + event.value,
            })));
          } else if (event.type === "tool") {
            setMessages((current) => updateLastAssistant(current, (message) => ({
              ...message,
              toolCalls: [...(message.toolCalls ?? []), event.value],
            })));
          } else if (event.type === "done") {
            receivedDone = true;
            setMessages((current) => updateLastAssistant(current, (message) => ({ ...message, isStreaming: false })));
          } else if (event.type === "error") {
            throw new Error(event.value || "The assistant could not answer that question.");
          }
        });

        if (!receivedDone || !receivedAnswer) {
          throw new Error("The assistant stream ended before a complete answer arrived. Please try again.");
        }
        setLoadedConversationId(conversationId);
      } catch (caught) {
        if (!(caught instanceof DOMException && caught.name === "AbortError")) {
          setDraft(question);
          setError(caught instanceof Error ? caught.message : "The assistant could not answer that question.");
        }
      } finally {
        setMessages((current) => updateLastAssistant(current, (message) => ({ ...message, isStreaming: false })));
        setPendingQuestion(null);
        setIsLoading(false);
      }
    },
    [conversationId, isLoading],
  );

  function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void sendQuestion(draft);
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  const activeAssistant = messages.at(-1)?.role === "assistant" && messages.at(-1)?.isStreaming
    ? messages.at(-1)
    : null;
  const progressLabel = activeAssistant?.toolCalls?.length
    ? "Checking hotel records…"
    : activeAssistant?.content
      ? "Writing the answer…"
      : "Finding hotel information…";

  return (
    <>
      {isOpen ? (
        <section
          id="insights-chat-panel"
          className={styles.panel}
          role="dialog"
          aria-modal="false"
          aria-labelledby="insights-chat-title"
          onKeyDown={(event) => {
            if (event.key === "Escape") closeChat();
          }}
        >
          <header className={styles.header}>
            <div className={styles.identity}>
              <span className={styles.assistantMark} aria-hidden="true"><Bot size={19} /></span>
              <div>
                <h2 id="insights-chat-title">Ask Azzurro</h2>
                <p>Hotel information and guest insights</p>
              </div>
            </div>
            <div className={styles.headerActions}>
              <button
                className={styles.iconButton}
                type="button"
                onClick={() => setShowConversationExplorer((visible) => !visible)}
                disabled={isLoading}
                aria-label={showConversationExplorer ? "Back to conversation" : "Explore conversations"}
                title={showConversationExplorer ? "Back to conversation" : "Explore conversations"}
              >
                <History size={17} />
              </button>
              <button
                className={styles.iconButton}
                type="button"
                onClick={startNewConversation}
                disabled={isLoading}
                aria-label="Start a new conversation"
                title="New conversation"
              >
                <RotateCcw size={16} />
              </button>
              <button className={styles.iconButton} type="button" onClick={closeChat} aria-label="Close chat">
                <X size={19} />
              </button>
            </div>
          </header>

          <div className={styles.messages} aria-live="polite" aria-relevant="additions text" aria-busy={isLoading}>
            {showConversationExplorer ? (
              <div className={styles.conversationExplorer}>
                <div>
                  <h3>Previous conversations</h3>
                  <p>Saved in this browser. Select one to continue where you left off.</p>
                </div>
                {conversations.length ? (
                  <div className={styles.conversationList}>
                    {conversations.map((conversation) => (
                      <button
                        className={conversation.id === conversationId
                          ? `${styles.conversationItem} ${styles.activeConversation}`
                          : styles.conversationItem}
                        key={conversation.id}
                        type="button"
                        onClick={() => selectConversation(conversation.id)}
                        aria-current={conversation.id === conversationId ? "true" : undefined}
                        disabled={isLoading}
                      >
                        <span className={styles.conversationTitle}>{conversation.title}</span>
                        <time className={styles.conversationDate} dateTime={conversation.updatedAt}>
                          {formatConversationDate(conversation.updatedAt)}
                        </time>
                      </button>
                    ))}
                  </div>
                ) : (
                  <p className={styles.conversationEmpty}>Your previous conversations will appear here.</p>
                )}
              </div>
            ) : isLoadingHistory ? (
              <p className={styles.loadingHistory}><LoaderCircle size={15} className={styles.spinner} /> Loading conversation</p>
            ) : messages.length === 0 && !pendingQuestion ? (
              <div className={styles.welcome}>
                <span className={styles.welcomeIcon} aria-hidden="true"><Sparkles size={17} /></span>
                  <h3>Ask about your hotels</h3>
                  <p>Get stored property details or guest-review insights. Choose one hotel for property-specific answers; performance questions also need a date range.</p>
                <div className={styles.starters} aria-label="Example questions">
                  {STARTER_QUESTIONS.map((question) => (
                    <button
                      className={styles.starter}
                      key={question}
                      type="button"
                      onClick={() => void sendQuestion(question)}
                      disabled={!conversationId || isLoading}
                    >
                      {question}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              messages.map((message, index) => (
                <article
                  className={message.role === "user" ? styles.userMessage : styles.assistantMessage}
                  key={`${index}-${message.role}`}
                >
                  {message.role === "assistant" ? <span className={styles.answerMark} aria-hidden="true"><Bot size={15} /></span> : null}
                  {message.role === "assistant" ? (
                    <div className={styles.assistantContent}>
                      {message.toolCalls?.map((toolName, toolIndex) => (
                        <span className={styles.toolUse} key={`${toolName}-${toolIndex}`}>Used {toolName}()</span>
                      ))}
                      {message.content ? <div className={styles.answerMarkdown}>{renderAssistantMarkdown(message.content)}</div> : null}
                    </div>
                  ) : <p>{message.content}</p>}
                </article>
              ))
            )}
            {pendingQuestion ? (
              <p className={styles.thinking} role="status">
                <LoaderCircle size={15} className={styles.spinner} /> {progressLabel}
              </p>
            ) : null}
            <div ref={bottomRef} />
          </div>

          {error ? <p className={styles.error} role="alert">{error}</p> : null}

          {showConversationExplorer ? null : <form className={styles.composer} onSubmit={submitQuestion}>
            <label className={styles.srOnly} htmlFor="insights-chat-question">Ask about guest reviews</label>
            <textarea
              id="insights-chat-question"
              ref={inputRef}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleInputKeyDown}
              placeholder="Ask about your hotels…"
              maxLength={2_000}
              rows={2}
              disabled={!conversationId || isLoading || isLoadingHistory}
            />
            <button
              className={styles.sendButton}
              type="submit"
              disabled={!conversationId || !draft.trim() || isLoading || isLoadingHistory}
              aria-label="Send question"
            >
              {isLoading ? <LoaderCircle size={17} className={styles.spinner} /> : <SendHorizontal size={17} />}
            </button>
            <span className={styles.composerNote}>Review patterns can overlap · Shift+Enter for a new line</span>
          </form>}
        </section>
      ) : null}

      <button
        ref={launcherRef}
        className={styles.launcher}
        type="button"
        onClick={isOpen ? closeChat : openChat}
        aria-expanded={isOpen}
        aria-controls="insights-chat-panel"
        aria-label={isOpen ? "Close Ask Azzurro" : "Ask Azzurro about guest reviews"}
      >
        {isOpen ? <X size={19} aria-hidden="true" /> : <MessageCircle size={20} aria-hidden="true" />}
        <span>{isOpen ? "Close chat" : "Ask Azzurro"}</span>
      </button>
    </>
  );
}
