"use client";

import { useEffect, useRef } from "react";
import type { QrMatrixData } from "./actions";

const CANVAS_SIZE = 280;

export function QrCanvas({ matrix }: { matrix: QrMatrixData | null }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

    if (!matrix) return;

    const moduleSize = CANVAS_SIZE / matrix.size;
    ctx.fillStyle = "#00112c";
    for (let y = 0; y < matrix.size; y++) {
      for (let x = 0; x < matrix.size; x++) {
        if (matrix.dark[y * matrix.size + x]) {
          ctx.fillRect(x * moduleSize, y * moduleSize, moduleSize + 0.5, moduleSize + 0.5);
        }
      }
    }
  }, [matrix]);

  return (
    <canvas
      ref={canvasRef}
      width={CANVAS_SIZE}
      height={CANVAS_SIZE}
      className="h-full w-full rounded-lg"
    />
  );
}
