import type { conversation, message, roleplay, topic, topicLesson } from "../db/app-schema.js";

type TopicRow = typeof topic.$inferSelect;
type LessonRow = typeof topicLesson.$inferSelect;

export type TopicStatus = "not_started" | "in_progress" | "completed";
export type LessonStatus = "done" | "current" | "upcoming";

function progressOf(lessons: LessonRow[]) {
  const total = lessons.length;
  const completed = lessons.filter((lesson) => lesson.completedAt).length;
  const status: TopicStatus = completed === 0 ? "not_started" : completed >= total ? "completed" : "in_progress";
  return { total, completed, percent: total ? Math.round((completed / total) * 100) : 0, status };
}

export function topicProgressLabel(lessons: LessonRow[]) {
  const { status, percent } = progressOf(lessons);
  return status === "completed" ? "Done" : `${percent}%`;
}

export function serializeTopicSummary(row: TopicRow, lessons: LessonRow[]) {
  const progress = progressOf(lessons);
  return {
    id: row.id,
    title: row.title,
    translation: row.translation,
    language: row.language,
    totalLessons: progress.total,
    completedLessons: progress.completed,
    progress: progress.percent,
    status: progress.status,
    updatedAt: row.updatedAt.toISOString(),
  };
}

export function serializeTopicDetail(row: TopicRow, lessons: LessonRow[]) {
  const sorted = [...lessons].sort((a, b) => a.position - b.position);
  const currentId = sorted.find((lesson) => !lesson.completedAt)?.id;
  return {
    ...serializeTopicSummary(row, sorted),
    lessons: sorted.map((lesson) => ({
      id: lesson.id,
      position: lesson.position,
      title: lesson.title,
      translation: lesson.translation,
      objective: lesson.objective,
      status: (lesson.completedAt ? "done" : lesson.id === currentId ? "current" : "upcoming") as LessonStatus,
    })),
  };
}

export function serializeRoleplay(row: typeof roleplay.$inferSelect) {
  return {
    id: row.id,
    title: row.title,
    scenario: row.scenario,
    userRole: row.userRole,
    aiRole: row.aiRole,
    setting: row.setting,
    language: row.language,
    updatedAt: row.updatedAt.toISOString(),
  };
}

export function serializeConversation(row: typeof conversation.$inferSelect) {
  return {
    id: row.id,
    kind: row.kind,
    title: row.title,
    language: row.language,
    roleplayId: row.roleplayId,
    topicId: row.topicId,
    lessonId: row.lessonId,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
  };
}

export function serializeMessage(row: typeof message.$inferSelect) {
  return {
    id: row.id,
    role: row.role,
    text: row.text,
    translation: row.translation,
    feedback: row.feedback,
    glossary: row.glossary ?? [],
    viaVoice: row.viaVoice,
    createdAt: row.createdAt.toISOString(),
  };
}
