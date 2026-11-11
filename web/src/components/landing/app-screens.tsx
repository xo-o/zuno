"use client";

import {
  ArrowLeft,
  BatteryFull,
  Captions,
  EllipsisVertical,
  Keyboard,
  Menu,
  Mic,
  Signal,
  Wifi,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { VoiceOrb, type VoiceOrbState } from "./voice-orb";

/* Native 402×874 app screens from the Figma "02 · Home" section, rendered inside <ScaledScreen>. */

const ORB_CANVAS = 296;

function StatusBar() {
  return (
    <div className="absolute inset-x-0 top-0 flex h-11 items-center justify-between px-6 pt-1">
      <span className="text-[15px] leading-5 font-semibold tracking-[-0.2px]">
        9:41
      </span>
      <span className="flex items-center gap-1.5 text-foreground">
        <Signal className="size-4" strokeWidth={2.5} />
        <Wifi className="size-4" strokeWidth={2.5} />
        <BatteryFull className="size-6" strokeWidth={1.75} />
      </span>
    </div>
  );
}

function HomeIndicator() {
  return (
    <div className="absolute inset-x-0 bottom-0 flex h-[34px] items-center justify-center">
      <div className="h-[5px] w-[134px] rounded-full bg-foreground" />
    </div>
  );
}

/** Orb centered on (x, y) in screen coordinates; `sphere` is the visible diameter. */
function PlacedOrb({
  state,
  centerY,
}: {
  state: VoiceOrbState;
  centerY: number;
}) {
  return (
    <div
      className="pointer-events-none absolute left-1/2 -translate-x-1/2 -translate-y-1/2"
      style={{ top: centerY }}
    >
      <VoiceOrb state={state} size={ORB_CANVAS} />
    </div>
  );
}

const CAPTION: Record<VoiceOrbState, string> = {
  idle: "Hold the orb to talk",
  listening: "Listening…",
  thinking: "Sending…",
  speaking: "Speaking…",
};

/** Home — V3 · Orb. Press and hold the orb to preview listening. */
export function HomeScreen() {
  const [state, setState] = useState<VoiceOrbState>("idle");
  const timer = useRef<number | undefined>(undefined);
  const holding = useRef(false);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const start = () => {
    window.clearTimeout(timer.current);
    holding.current = true;
    setState("listening");
  };
  const end = () => {
    if (!holding.current) return;
    holding.current = false;
    setState("thinking");
    timer.current = window.setTimeout(() => setState("idle"), 1400);
  };

  return (
    <div className="relative size-full bg-background text-foreground select-none">
      <StatusBar />
      <div className="absolute inset-x-0 top-11 flex h-14 items-center justify-between pr-4 pl-2">
        <span className="flex size-11 items-center justify-center">
          <Menu className="size-6" strokeWidth={1.75} />
        </span>
        <span className="flex h-9 items-center rounded-full border border-input bg-card px-3.5 text-[13px] leading-4 font-medium">
          Spanish · B1
        </span>
      </div>
      <PlacedOrb state={state} centerY={350} />
      <button
        type="button"
        aria-label="Hold to talk"
        className="absolute top-[258px] left-[106px] size-[190px] cursor-pointer touch-none rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId);
          start();
        }}
        onPointerUp={end}
        onPointerCancel={end}
        onKeyDown={(e) => {
          if ((e.key === " " || e.key === "Enter") && !e.repeat) {
            e.preventDefault();
            start();
          }
        }}
        onKeyUp={(e) => {
          if (e.key === " " || e.key === "Enter") end();
        }}
      />
      <div className="absolute top-[490px] left-[51px] flex w-[300px] flex-col items-center gap-2.5 text-center">
        <p className="text-[28px] leading-[34px] font-semibold tracking-[-1px]">
          Hola Noah, ¿qué practicamos hoy?
        </p>
        <p aria-live="polite" className="text-[13px] leading-[18px] text-faint">
          {CAPTION[state]}
        </p>
      </div>
      <div className="absolute top-[790px] left-1/2 flex h-9 -translate-x-1/2 items-center gap-1.5 px-3 text-[13px] leading-4 font-medium">
        <Keyboard className="size-4" strokeWidth={1.75} />
        Type instead
      </div>
      <HomeIndicator />
    </div>
  );
}

/** Home — Speaking · Captions: a live Free Talk call. */
export function SpeakingScreen() {
  return (
    <div className="relative size-full bg-background text-foreground">
      <StatusBar />
      <div className="absolute inset-x-0 top-11 flex h-14 items-center gap-2 px-3">
        <span className="flex size-11 items-center justify-center">
          <ArrowLeft className="size-6" strokeWidth={1.75} />
        </span>
        <span className="flex-1 text-center text-[17px] leading-6 font-medium tracking-[-0.2px]">
          Live · 00:42
        </span>
        <span className="flex size-11 items-center justify-center">
          <EllipsisVertical className="size-6" strokeWidth={1.75} />
        </span>
      </div>
      <PlacedOrb state="speaking" centerY={350} />
      <div className="absolute top-[480px] left-[46px] flex w-[310px] flex-col items-center gap-2.5 text-center">
        <p className="text-[15px] leading-[1.4] text-faint">
          Imagina que estás en un café en Madrid.
        </p>
        <p className="text-[24px] leading-[30px] font-semibold">
          ¿Qué vas a pedir?
        </p>
      </div>
      <div className="absolute top-[778px] left-6 flex h-[52px] w-[354px] items-center justify-between">
        <span className="flex size-11 items-center justify-center rounded-full bg-muted">
          <Mic className="size-6" strokeWidth={1.75} />
        </span>
        <span className="flex h-11 items-center rounded-full border border-input bg-card px-5 text-[15px] leading-5 font-semibold tracking-[-0.2px]">
          End call
        </span>
        <span className="flex size-11 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Captions className="size-6" strokeWidth={1.75} />
        </span>
      </div>
      <HomeIndicator />
    </div>
  );
}
