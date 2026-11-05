// 카드에 대한 AI 질문. 백엔드 ChatController와 같은 모양
// 질문과 답은 서버에 저장되지 않는다. 시트를 닫으면 대화도 사라진다

import { apiFetch } from "@/lib/api";

export type ChatRole = "user" | "model"; // user 회원, model AI (Gemini가 쓰는 이름)

export type ChatTurn = { role: ChatRole; text: string };

// 무엇에 대한 질문인지. 카드 내용은 서버가 DB에서 직접 읽는다
export type ChatTarget = { type: "event" | "story"; id: string };

export type ChatAnswer = { answer: string; remaining: number };

// 백엔드 ChatService와 같은 한도. 서버도 다시 검사한다
export const MAX_QUESTION = 300;

// 실패하면 화면에 보일 문장으로 던진다. 서버가 우리 문장(detail)을 주면 그것을 쓴다
export async function askChat(target: ChatTarget, history: ChatTurn[], question: string): Promise<ChatAnswer> {
  const res = await apiFetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...target, history, question }),
  });
  if (res.ok) return res.json();
  if (res.status === 401) throw new Error("로그인이 끊겼습니다. 다시 로그인해 주세요");
  const problem: { detail?: string } | null = await res.json().catch(() => null);
  throw new Error(problem?.detail ?? "답변을 받지 못했습니다. 잠시 뒤 다시 해 주세요");
}
