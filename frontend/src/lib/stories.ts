// GET /api/stories 응답 (백엔드 StoryResponse와 같은 모양)
// 전쟁 속 이야기: 시기와 상관없이 헤더의 "이야기" 버튼으로 언제든 읽는 글 (CLAUDE.md 5장)
import type { Block, PhotoMeta, SourceRef } from "./events";

export type Story = {
  id: string;
  title: string;
  summary: string; // 2~3문장. 목록에도 나온다
  photoFile: string | null; // 대표 사진 (카드 위쪽)
  photoCaption: string | null;
  body: Block[];
  sources: SourceRef[];
  photos: PhotoMeta[]; // 대표 사진과 본문 사진의 메타데이터
  status: "draft" | "reviewed"; // 화면에는 보이지 않는다
};

export async function fetchStories(signal?: AbortSignal): Promise<Story[]> {
  const res = await fetch("/api/stories", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/stories ${res.status}`);
  }
  return res.json();
}
