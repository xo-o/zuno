package com.zuno.ai.avatar.renderer

/**
 * GLSL ES 3.0 shaders for hardware-decoded `idle.mp4` + `crisp-clips` / `smooth-morph` compositing.
 *
 * Ports `smoothMorphCompositor.ts` 1:1 to Android OpenGL ES 3.0 (`#version 300 es`) with:
 * - `samplerExternalOES` + `uStMatrix` for zero-copy hardware video texture sampling
 * - `uCropRect` for distortion-free `ContentScale.Crop` + `Alignment.TopCenter` viewport framing
 */
internal object AvatarGlShaders {
    const val VERTEX_SHADER = """#version 300 es
precision mediump float;

uniform vec4 uCropRect; // (u0, v0, uScale, vScale) in top-down normalized frame coords

const vec2 POS[4] = vec2[4](
  vec2(-1.0, -1.0),
  vec2( 1.0, -1.0),
  vec2(-1.0,  1.0),
  vec2( 1.0,  1.0)
);
const vec2 RAW_UV[4] = vec2[4](
  vec2(0.0, 1.0),
  vec2(1.0, 1.0),
  vec2(0.0, 0.0),
  vec2(1.0, 0.0)
);

out vec2 vUv;

void main() {
  vec2 raw = RAW_UV[gl_VertexID];
  vUv = uCropRect.xy + raw * uCropRect.zw;
  gl_Position = vec4(POS[gl_VertexID], 0.0, 1.0);
}
"""

    const val FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

in vec2 vUv;
out vec4 outColor;

uniform samplerExternalOES uVideoTex;
uniform sampler2D uMaskTex;
uniform sampler2D uStripTex;

uniform mat4 uStMatrix;
uniform vec4 uRect;         // (x1/W, y1/H, (x2-x1)/W, (y2-y1)/H)
uniform vec2 uGrid;         // (gridCols, gridRows)
uniform vec2 uTileTexel;    // (1 / stripWidth, 1 / stripHeight)
uniform vec2 uFrameSize;    // (width, height) in pixels
uniform float uOpen;        // 0..1 continuous openness (legacy smooth-morph fallback)
uniform vec4 uRowWeights;   // normalized weights for rows 0..3
uniform vec2 uDiscreteCell; // x >= 0: zero-blend exact (col, row)
uniform float uDualMask;    // 1.0 when mask.png has R=butterfly, G=oral interior
uniform float uPresence;    // 0..1 smooth speaking presence

// Prosody-driven full-face muscle rig (eyebrows, mid-cheeks, lower eyelids)
uniform vec4 uFaceExpr;     // (browRaise, cheekLift, eyeEngage, hasRig)
uniform vec2 uFaceShift;    // (dx/W, dy/H) live head-tracking offset from idle.mp4
uniform vec4 uLeftBrow;     // (cx, cy, rx, ry) normalized UV
uniform vec4 uRightBrow;
uniform vec4 uLeftCheek;
uniform vec4 uRightCheek;
uniform vec4 uLeftLid;
uniform vec4 uRightLid;
uniform vec3 uFaceLimits;   // (eyeTopY, eyeCenterY, noseTipY) normalized UV

float gaussZone(vec2 p, vec4 z, vec2 shift) {
  vec2 d = (p - (z.xy + shift)) / max(z.zw, vec2(0.001));
  float r2 = dot(d, d);
  if (r2 >= 4.0) return 0.0;
  float c = 1.0 - r2 * 0.25;
  return exp(-r2 * 1.25) * c * c;
}

vec2 computeMuscleSampleUv(vec2 uv) {
  if (uFaceExpr.w < 0.5 || (uFaceExpr.x + uFaceExpr.y + uFaceExpr.z) <= 0.002) {
    return uv;
  }

  vec2 disp = vec2(0.0);
  float eyeTop = uFaceLimits.x + uFaceShift.y;
  float eyeMid = uFaceLimits.y + uFaceShift.y;

  // 1. Frontalis eyebrow elevation (clamped strictly above eyelids -> eyes stay crisp)
  if (uFaceExpr.x > 0.002) {
    float lbY = uLeftBrow.y + uFaceShift.y;
    float rbY = uRightBrow.y + uFaceShift.y;
    float lb = gaussZone(uv, uLeftBrow, uFaceShift) * (1.0 - smoothstep(lbY, max(lbY + 0.002, eyeTop), uv.y));
    float rb = gaussZone(uv, uRightBrow, uFaceShift) * (1.0 - smoothstep(rbY, max(rbY + 0.002, eyeTop), uv.y));
    disp.y += (lb + rb) * uFaceExpr.x * (3.4 / uFrameSize.y);
  }

  // 2. Zygomaticus mid-cheek lift + subtle fullness
  if (uFaceExpr.y > 0.002) {
    float lcY = uLeftCheek.y + uFaceShift.y;
    float rcY = uRightCheek.y + uFaceShift.y;
    float lc = gaussZone(uv, uLeftCheek, uFaceShift) * smoothstep(eyeMid, max(eyeMid + 0.002, lcY), uv.y);
    float rc = gaussZone(uv, uRightCheek, uFaceShift) * smoothstep(eyeMid, max(eyeMid + 0.002, rcY), uv.y);
    disp.y += (lc + rc) * uFaceExpr.y * (2.4 / uFrameSize.y);
    disp.x += (lc - rc) * uFaceExpr.y * (0.8 / uFrameSize.x);
  }

  // 3. Orbicularis oculi lower-eyelid warmth (strictly below eye center)
  if (uFaceExpr.z > 0.002) {
    float llY = uLeftLid.y + uFaceShift.y;
    float rlY = uRightLid.y + uFaceShift.y;
    float ll = gaussZone(uv, uLeftLid, uFaceShift) * smoothstep(eyeMid, max(eyeMid + 0.002, llY), uv.y);
    float rl = gaussZone(uv, uRightLid, uFaceShift) * smoothstep(eyeMid, max(eyeMid + 0.002, rlY), uv.y);
    disp.y += (ll + rl) * uFaceExpr.z * (1.2 / uFrameSize.y);
  }

  return clamp(uv + disp, vec2(0.0), vec2(1.0));
}

vec3 sampleTile(float col, float row, vec2 localUv) {
  vec2 halfTexel = 0.5 * uTileTexel * uGrid;
  vec2 safeUv = clamp(localUv, halfTexel, vec2(1.0) - halfTexel);
  vec2 tileUv = (vec2(col, row) + safeUv) / uGrid;
  return texture(uStripTex, tileUv).rgb;
}

vec3 sampleRowSharp(float row, float c0, float c1, float fc, vec2 localUv, float sharpenAmt) {
  vec3 c = mix(sampleTile(c0, row, localUv), sampleTile(c1, row, localUv), fc);
  if (sharpenAmt <= 0.001) {
    return c;
  }
  vec2 d = uTileTexel * uGrid;
  vec3 n = mix(sampleTile(c0, row, localUv + vec2(0.0, -d.y)), sampleTile(c1, row, localUv + vec2(0.0, -d.y)), fc);
  vec3 s = mix(sampleTile(c0, row, localUv + vec2(0.0,  d.y)), sampleTile(c1, row, localUv + vec2(0.0,  d.y)), fc);
  vec3 e = mix(sampleTile(c0, row, localUv + vec2( d.x, 0.0)), sampleTile(c1, row, localUv + vec2( d.x, 0.0)), fc);
  vec3 w = mix(sampleTile(c0, row, localUv + vec2(-d.x, 0.0)), sampleTile(c1, row, localUv + vec2(-d.x, 0.0)), fc);
  vec3 blur = (n + s + e + w) * 0.25;
  return clamp(c + (c - blur) * sharpenAmt, 0.0, 1.0);
}

void main() {
  vec2 sampleUv = computeMuscleSampleUv(vUv);
  vec2 videoUv = (uStMatrix * vec4(sampleUv.x, 1.0 - sampleUv.y, 0.0, 1.0)).xy;
  vec3 baseRgb = texture(uVideoTex, videoUv).rgb;

  // --- ZERO-BLEND CRISP CLIPS MODE ---
  if (uDiscreteCell.x >= -0.5) {
    if ((uDiscreteCell.x < 0.5 && uDiscreteCell.y < 0.5) || uPresence <= 0.002) {
      outColor = vec4(baseRgb, 1.0);
      return;
    }

    vec2 localUv = (sampleUv - uRect.xy) / uRect.zw;
    if (localUv.x < 0.0 || localUv.x > 1.0 || localUv.y < 0.0 || localUv.y > 1.0) {
      outColor = vec4(baseRgb, 1.0);
      return;
    }

    vec2 maskRg = texture(uMaskTex, localUv).rg;
    float maskVal = maskRg.r;
    float alpha = maskVal * uPresence;
    if (alpha <= 0.002) {
      outColor = vec4(baseRgb, 1.0);
      return;
    }

    vec3 mouthRgb = sampleTile(uDiscreteCell.x, uDiscreteCell.y, localUv);
    vec3 restRgb = sampleTile(0.0, 0.0, localUv);

    vec3 deltaMatched = clamp(baseRgb + (mouthRgb - restRgb), 0.0, 1.0);
    float oralWeight = uDualMask > 0.5
      ? smoothstep(0.15, 0.75, maskRg.g) * 0.85
      : smoothstep(0.18, 0.68, maskVal);
    vec3 blendedMouth = mix(deltaMatched, mouthRgb, oralWeight);
    outColor = vec4(mix(baseRgb, blendedMouth, alpha), 1.0);
    return;
  }

  // --- LEGACY SMOOTH-MORPH MODE ---
  float envelope = smoothstep(0.0, 0.032, uOpen);
  if (envelope <= 0.002) {
    outColor = vec4(baseRgb, 1.0);
    return;
  }

  vec2 localUv = (sampleUv - uRect.xy) / uRect.zw;
  if (localUv.x < 0.0 || localUv.x > 1.0 || localUv.y < 0.0 || localUv.y > 1.0) {
    outColor = vec4(baseRgb, 1.0);
    return;
  }

  float maskVal = texture(uMaskTex, localUv).r;
  float alpha = maskVal * envelope;
  if (alpha <= 0.002) {
    outColor = vec4(baseRgb, 1.0);
    return;
  }

  float maxCol = max(1.0, uGrid.x - 1.0);
  float colPos = clamp(uOpen, 0.0, 1.0) * maxCol;
  float c0 = floor(colPos);
  float c1 = min(maxCol, c0 + 1.0);
  float fc = colPos - c0;
  float sharpenAmt = 0.26 * smoothstep(0.30, 0.85, maskVal);

  vec3 mouthRgb = vec3(0.0);
  float wSum = 0.0;
  if (uRowWeights.x > 0.004) {
    mouthRgb += uRowWeights.x * sampleRowSharp(0.0, c0, c1, fc, localUv, sharpenAmt);
    wSum += uRowWeights.x;
  }
  if (uGrid.y >= 2.0 && uRowWeights.y > 0.004) {
    mouthRgb += uRowWeights.y * sampleRowSharp(1.0, c0, c1, fc, localUv, sharpenAmt);
    wSum += uRowWeights.y;
  }
  if (uGrid.y >= 3.0 && uRowWeights.z > 0.004) {
    mouthRgb += uRowWeights.z * sampleRowSharp(2.0, c0, c1, fc, localUv, sharpenAmt);
    wSum += uRowWeights.z;
  }
  if (uGrid.y >= 4.0 && uRowWeights.w > 0.004) {
    mouthRgb += uRowWeights.w * sampleRowSharp(3.0, c0, c1, fc, localUv, sharpenAmt);
    wSum += uRowWeights.w;
  }
  if (wSum > 0.001) {
    mouthRgb /= wSum;
  } else {
    mouthRgb = sampleTile(0.0, 0.0, localUv);
  }

  vec3 restRgb = sampleTile(0.0, 0.0, localUv);
  vec3 deltaMatched = clamp(baseRgb + (mouthRgb - restRgb), 0.0, 1.0);
  vec3 blendedMouth = mix(deltaMatched, mouthRgb, smoothstep(0.22, 0.78, maskVal));
  outColor = vec4(mix(baseRgb, blendedMouth, alpha), 1.0);
}
"""
}
