import test from "node:test";
import assert from "node:assert/strict";
import {
  PRESET_AVATAR_COUNT,
  presetAvatarKey,
  presetAvatarUrl,
  randomPresetAvatarUrl,
} from "./avatars.js";

test("avatars: presetAvatarKey formats two-digit indices within 1..30", () => {
  assert.equal(PRESET_AVATAR_COUNT, 30);
  assert.equal(presetAvatarKey(1), "avatars/presets/buddy-01.png");
  assert.equal(presetAvatarKey(9), "avatars/presets/buddy-09.png");
  assert.equal(presetAvatarKey(30), "avatars/presets/buddy-30.png");
  assert.equal(presetAvatarKey(0), "avatars/presets/buddy-01.png");
  assert.equal(presetAvatarKey(99), "avatars/presets/buddy-30.png");
});

test("avatars: presetAvatarUrl uses R2_PUBLIC_DOMAIN without trailing slash", () => {
  const prev = process.env.R2_PUBLIC_DOMAIN;
  process.env.R2_PUBLIC_DOMAIN = "https://cdn.usezuno.rest/";
  try {
    assert.equal(presetAvatarUrl(7), "https://cdn.usezuno.rest/avatars/presets/buddy-07.png");
    assert.equal(
      randomPresetAvatarUrl(() => 14),
      "https://cdn.usezuno.rest/avatars/presets/buddy-14.png",
    );
  } finally {
    if (prev === undefined) delete process.env.R2_PUBLIC_DOMAIN;
    else process.env.R2_PUBLIC_DOMAIN = prev;
  }
});

test("avatars: randomPresetAvatarUrl picks across 1..30", () => {
  for (let i = 0; i < 40; i++) {
    const url = randomPresetAvatarUrl();
    assert.match(url, /\/avatars\/presets\/buddy-(0[1-9]|[12]\d|30)\.png$/);
  }
});
