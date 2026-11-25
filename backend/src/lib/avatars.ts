import { randomInt } from "node:crypto";

export const PRESET_AVATAR_COUNT = 30;
const DEFAULT_R2_PUBLIC_DOMAIN = "https://cdn.usezuno.rest";

export function r2PublicDomain(): string {
  return (process.env.R2_PUBLIC_DOMAIN || DEFAULT_R2_PUBLIC_DOMAIN).replace(/\/+$/, "");
}

export function presetAvatarKey(index: number): string {
  const clamped = Math.max(1, Math.min(PRESET_AVATAR_COUNT, Math.trunc(index)));
  return `avatars/presets/buddy-${String(clamped).padStart(2, "0")}.png`;
}

export function presetAvatarUrl(index: number): string {
  return `${r2PublicDomain()}/${presetAvatarKey(index)}`;
}

export function randomPresetAvatarUrl(
  pickIndex: () => number = () => randomInt(1, PRESET_AVATAR_COUNT + 1),
): string {
  return presetAvatarUrl(pickIndex());
}
