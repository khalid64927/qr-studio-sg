"use client";

import { useEffect, useRef } from "react";
import type { QrMatrixData, RgbInput } from "./actions";

const CANVAS_SIZE = 280;

export type ModuleShapeValue = "SQUARE" | "ROUNDED" | "DOT";
export type LogoShapeValue = "SQUARE" | "ROUNDED" | "CIRCLE";

export interface AppearanceState {
  foreground: RgbInput;
  background: RgbInput;
  /** null means "use the foreground colour" — same default as Kotlin's EyeStyle.colour. */
  eyeColor: RgbInput | null;
  moduleShape: ModuleShapeValue;
  eyeShape: ModuleShapeValue;
}

export interface LogoState {
  enabled: boolean;
  sizeFraction: number;
  shape: LogoShapeValue;
}

function rgbToCss({ r, g, b }: RgbInput): string {
  return `rgb(${Math.round(r * 255)}, ${Math.round(g * 255)}, ${Math.round(b * 255)})`;
}

function fillShape(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  size: number,
  shape: ModuleShapeValue,
) {
  switch (shape) {
    case "SQUARE":
      ctx.fillRect(x, y, size + 0.5, size + 0.5);
      break;
    case "ROUNDED":
      ctx.beginPath();
      ctx.roundRect(x, y, size, size, size * 0.3);
      ctx.fill();
      break;
    case "DOT":
      ctx.beginPath();
      ctx.arc(x + size / 2, y + size / 2, size / 2.2, 0, Math.PI * 2);
      ctx.fill();
      break;
  }
}

function fillLogoPlate(
  ctx: CanvasRenderingContext2D,
  left: number,
  top: number,
  size: number,
  shape: LogoShapeValue,
) {
  switch (shape) {
    case "CIRCLE":
      ctx.beginPath();
      ctx.arc(left + size / 2, top + size / 2, size / 2, 0, Math.PI * 2);
      ctx.fill();
      break;
    case "SQUARE":
      ctx.fillRect(left, top, size, size);
      break;
    case "ROUNDED":
      ctx.beginPath();
      ctx.roundRect(left, top, size, size, size * 0.2);
      ctx.fill();
      break;
  }
}

function withinLogoArea(
  moduleX: number,
  moduleY: number,
  moduleSize: number,
  logoLeft: number,
  logoTop: number,
  logoSize: number,
  padding: number,
): boolean {
  const cx = moduleX + moduleSize / 2;
  const cy = moduleY + moduleSize / 2;
  return (
    cx >= logoLeft - padding &&
    cx <= logoLeft + logoSize + padding &&
    cy >= logoTop - padding &&
    cy <= logoTop + logoSize + padding
  );
}

export interface LogoGeometry {
  left: number;
  top: number;
  size: number;
}

/**
 * Ports sg.qrstudio.app.ui.drawQrMatrix (the Compose renderer) to Canvas 2D — same
 * flooring-to-whole-pixels rule (FR-603: a QR module rendered across a fractional pixel
 * boundary anti-aliases into a grey edge a scanner's binarisation loses contrast at),
 * same logo-exclusion-area formula, same per-shape geometry (0.3 corner radius for a
 * rounded module, size/2.2 dot radius, 0.2 corner radius for a rounded logo plate).
 */
export function QrCanvas({
  matrix,
  appearance,
  logo,
  onLogoGeometry,
}: {
  matrix: QrMatrixData | null;
  appearance: AppearanceState;
  logo: LogoState;
  /** Reports the logo's on-canvas pixel box, so a caller can overlay a placeholder icon. */
  onLogoGeometry?: (geometry: LogoGeometry | null) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

    if (!matrix) {
      onLogoGeometry?.(null);
      return;
    }

    const modulePixels = Math.floor(CANVAS_SIZE / matrix.size);
    if (modulePixels < 1) {
      onLogoGeometry?.(null);
      return;
    }

    const rendered = modulePixels * matrix.size;
    const originX = Math.floor((CANVAS_SIZE - rendered) / 2);
    const originY = Math.floor((CANVAS_SIZE - rendered) / 2);

    const foreground = rgbToCss(appearance.foreground);
    const background = rgbToCss(appearance.background);
    const eyeColour = appearance.eyeColor ? rgbToCss(appearance.eyeColor) : foreground;

    ctx.fillStyle = background;
    ctx.fillRect(originX, originY, rendered, rendered);

    const logoSize = logo.enabled ? rendered * logo.sizeFraction : 0;
    const logoLeft = originX + (rendered - logoSize) / 2;
    const logoTop = originY + (rendered - logoSize) / 2;
    const padding = 5;

    for (let row = 0; row < matrix.size; row++) {
      for (let col = 0; col < matrix.size; col++) {
        const i = row * matrix.size + col;
        if (!matrix.dark[i]) continue;
        const x = originX + col * modulePixels;
        const y = originY + row * modulePixels;

        if (logo.enabled && withinLogoArea(x, y, modulePixels, logoLeft, logoTop, logoSize, padding)) {
          continue;
        }

        const isEye = matrix.finder[i];
        ctx.fillStyle = isEye ? eyeColour : foreground;
        fillShape(ctx, x, y, modulePixels, isEye ? appearance.eyeShape : appearance.moduleShape);
      }
    }

    if (logo.enabled) {
      ctx.fillStyle = background;
      fillLogoPlate(ctx, logoLeft, logoTop, logoSize, logo.shape);
      onLogoGeometry?.({ left: logoLeft, top: logoTop, size: logoSize });
    } else {
      onLogoGeometry?.(null);
    }
  }, [matrix, appearance, logo, onLogoGeometry]);

  return (
    <canvas
      ref={canvasRef}
      width={CANVAS_SIZE}
      height={CANVAS_SIZE}
      className="h-full w-full rounded-lg"
    />
  );
}
