"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";

export type VoiceOrbState = "idle" | "listening" | "thinking" | "speaking";

type VoiceOrbProps = {
  state?: VoiceOrbState;
  /** Canvas edge length in px. The visible sphere is 64% of it. */
  size?: number;
  dots?: number;
  className?: string;
};

type RGB = [number, number, number];

const STATES: VoiceOrbState[] = ["idle", "listening", "thinking", "speaking"];

const SPIN_SPEED: Record<VoiceOrbState, number> = {
  idle: 0.16,
  listening: 0.3,
  thinking: 1.9,
  speaking: 0.42,
};

/** Resting dots match Figma glow/orb-bottom; active dots brighten to white. */
const INK: RGB = [168, 168, 168];
const ACCENT: RGB = [255, 255, 255];

const clamp = (v: number, min = 0, max = 1) => Math.min(max, Math.max(min, v));

/** Port of the app's dot-sphere Voice Orb (orb/src/components/VoiceOrb.tsx). */
export function VoiceOrb({
  state = "idle",
  size = 296,
  dots = 620,
  className,
}: VoiceOrbProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    const count = Math.max(60, Math.round(dots));
    const points = new Float32Array(count * 3);
    const golden = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < count; i++) {
      const y = 1 - (i / (count - 1)) * 2;
      const r = Math.sqrt(1 - y * y);
      points[i * 3] = Math.cos(golden * i) * r;
      points[i * 3 + 1] = y;
      points[i * 3 + 2] = Math.sin(golden * i) * r;
    }

    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    canvas.width = Math.round(size * dpr);
    canvas.height = Math.round(size * dpr);

    const weights: Record<VoiceOrbState, number> = {
      idle: 0,
      listening: 0,
      thinking: 0,
      speaking: 0,
    };
    weights[stateRef.current] = 1;

    const reduced = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    let angle = 0;
    let level = 0;
    let last = performance.now();
    let raf = 0;
    let visible = true;

    const draw = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const t = now / 1000;
      const current = stateRef.current;

      let speed = 0;
      for (const s of STATES) {
        weights[s] += (+(s === current) - weights[s]) * Math.min(1, dt * 5);
        speed += weights[s] * SPIN_SPEED[s];
      }
      angle += speed * dt;

      const target =
        current === "listening"
          ? clamp(
              0.35 +
                0.35 * Math.sin(t * 2.1) * Math.sin(t * 5.3) +
                0.3 * Math.sin(t * 9.7 + 1) * Math.sin(t * 0.9),
            )
          : clamp(
              0.3 +
                0.7 *
                  Math.abs(Math.sin(t * 4.1)) *
                  (0.5 + 0.5 * Math.sin(t * 1.3 + 2)),
            );
      level += (target - level) * Math.min(1, dt * 12);

      const c = size / 2;
      const radius = size * 0.32;
      const tilt = 0.32 + weights.thinking * 0.55;
      const sinTilt = Math.sin(tilt);
      const cosTilt = Math.cos(tilt);
      const sinA = Math.sin(angle);
      const cosA = Math.cos(angle);
      const baseDot = Math.max(0.9, size / 190);

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, size, size);

      for (let i = 0; i < count; i++) {
        const px = points[i * 3];
        const py = points[i * 3 + 1];
        const pz = points[i * 3 + 2];

        const noise =
          (Math.sin(px * 3.1 + t * 2.3) +
            Math.sin(py * 4.3 - t * 1.7) +
            Math.sin(pz * 5.2 + t * 2.9)) /
          3;
        const idleWave = weights.idle * 0.022 * Math.sin(t * 1.3 + py * 2);
        const listenBump =
          weights.listening * level * 0.3 * Math.max(0, noise) ** 1.4;
        const speakWave =
          weights.speaking * level * 0.13 * Math.sin(py * 7.5 - t * 7);
        const disp = idleWave + listenBump + speakWave;

        let x = px * (1 + disp);
        let y = py * (1 + disp);
        let z = pz * (1 + disp);
        let highlight = 0;
        let fade = 1;

        if (weights.thinking > 0.001) {
          const len = Math.hypot(px, pz) || 1;
          const wobble = 1 + 0.035 * Math.sin(t * 3 + i * 0.7);
          const w = weights.thinking;
          x += ((px / len) * wobble - x) * w;
          y += (py * 0.2 - y) * w;
          z += ((pz / len) * wobble - z) * w;
          fade = 1 - w * (1 - clamp((Math.hypot(px, pz) - 0.25) * 2.2));
          const a = Math.atan2(pz, px) + angle;
          highlight = w * Math.max(0, Math.cos(a - t * 2.2)) ** 6;
        }

        const rx = x * cosA + z * sinA;
        const rz = -x * sinA + z * cosA;
        const ry = y * cosTilt - rz * sinTilt;
        const depth = y * sinTilt + rz * cosTilt;
        const persp = 3.4 / (3.4 - depth);
        const sx = c + rx * radius * persp;
        const sy = c + ry * radius * persp;

        const near = (depth + 1.3) / 2.6;
        const energy = clamp(Math.abs(listenBump + speakWave) * 9 + highlight);
        const r = baseDot * (0.55 + 0.75 * near) * persp * (1 + energy * 0.5);
        const alpha = clamp(0.12 + 0.88 * near ** 1.6 + energy * 0.3) * fade;
        if (alpha < 0.01) continue;

        const cr = Math.round(INK[0] + (ACCENT[0] - INK[0]) * energy);
        const cg = Math.round(INK[1] + (ACCENT[1] - INK[1]) * energy);
        const cb = Math.round(INK[2] + (ACCENT[2] - INK[2]) * energy);
        ctx.fillStyle = `rgba(${cr}, ${cg}, ${cb}, ${alpha.toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(sx, sy, r, 0, Math.PI * 2);
        ctx.fill();
      }

      if (!reduced && visible) raf = requestAnimationFrame(draw);
    };

    const start = () => {
      cancelAnimationFrame(raf);
      last = performance.now();
      raf = requestAnimationFrame(draw);
    };

    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting && !document.hidden;
      if (visible) start();
    });
    observer.observe(canvas);
    start();

    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
    };
  }, [size, dots]);

  return (
    <div aria-hidden="true" className={cn("shrink-0", className)}>
      <canvas
        ref={canvasRef}
        className="block"
        style={{ width: size, height: size }}
      />
    </div>
  );
}
