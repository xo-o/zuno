import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeExtractedPhrase } from "./vocabulary.js";

test("sanitizeExtractedPhrase rejects empty or punctuation-only strings", () => {
  assert.equal(sanitizeExtractedPhrase(""), null);
  assert.equal(sanitizeExtractedPhrase("   "), null);
  assert.equal(sanitizeExtractedPhrase("..."), null);
  assert.equal(sanitizeExtractedPhrase("“—?!”"), null);
});

test("sanitizeExtractedPhrase trims surrounding quotes and preserves all scripts", () => {
  assert.equal(sanitizeExtractedPhrase("“pedir un café con leche”"), "pedir un café con leche");
  assert.equal(sanitizeExtractedPhrase("  «echar una mano»  "), "echar una mano");
  assert.equal(sanitizeExtractedPhrase("  お会計をお願いします  "), "お会計をお願いします");
  assert.equal(sanitizeExtractedPhrase("  من فضلك  "), "من فضلك");
  assert.equal(sanitizeExtractedPhrase("  잠시만요  "), "잠시만요");
});
