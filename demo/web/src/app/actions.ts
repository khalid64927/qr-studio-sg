"use server";

// Server Action — runs in the Next.js Node.js runtime, never in the browser bundle.
// Calls straight into the published @khalid64927/qr-studio-sg-payload and
// qr-studio-sg-qr npm packages (their jsMain facades — see the repo root README's
// Publishing section for why those exist). This is the same architecture a real
// merchant dashboard would use: PayNow proxy values and amounts never need to reach
// client JavaScript unvalidated.

import { sg as paySg } from "@khalid64927/qr-studio-sg-payload";
import { sg as qrSg } from "@khalid64927/qr-studio-sg-qr";

const { buildPayNowQr } = paySg.qrstudio.payload.js;
const { encodeQr, checkContrast, logoSizeBounds } = qrSg.qrstudio.qr.js;

export type ProxyTypeInput = "MOBILE" | "UEN";

export interface GenerateQrInput {
  proxyType: ProxyTypeInput;
  proxyValue: string;
  amount: string;
  amountEditable: boolean;
  reference: string;
  merchantName: string;
}

export interface QrMatrixData {
  size: number;
  /** Row-major, quiet zone included — same layout as ModuleMatrix/QrModuleMatrixJs. */
  dark: boolean[];
  /** True where a module belongs to a finder pattern — styled via "eye style", not moduleShape. */
  finder: boolean[];
}

export interface GenerateQrOutput {
  success: boolean;
  raw: string | null;
  proxyDisplay: string | null;
  amount: string | null;
  errors: string[];
  warnings: string[];
  matrix: QrMatrixData | null;
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

/** ISO date for the "today" the payload validates expiry against. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export async function generatePayNowQr(input: GenerateQrInput): Promise<GenerateQrOutput> {
  const result = buildPayNowQr(
    input.proxyType,
    input.proxyValue,
    blankToNull(input.amount),
    input.amountEditable,
    null, // expiry — defaults to today + 5 years
    blankToNull(input.reference),
    blankToNull(input.merchantName),
    todayIso(),
  );

  if (!result.success || result.raw == null) {
    return {
      success: false,
      raw: null,
      proxyDisplay: null,
      amount: null,
      errors: [...result.errors],
      warnings: [...result.warnings],
      matrix: null,
    };
  }

  const matrix = encodeQr(result.raw, "M");
  const dark: boolean[] = new Array(matrix.size * matrix.size);
  const finder: boolean[] = new Array(matrix.size * matrix.size);
  for (let y = 0; y < matrix.size; y++) {
    for (let x = 0; x < matrix.size; x++) {
      const i = y * matrix.size + x;
      dark[i] = matrix.isDark(x, y);
      finder[i] = matrix.isFinder(x, y);
    }
  }

  return {
    success: true,
    raw: result.raw,
    proxyDisplay: result.proxyDisplay ?? null,
    amount: result.amount ?? null,
    errors: [],
    warnings: [...result.warnings],
    matrix: { size: matrix.size, dark, finder },
  };
}

export interface RgbInput {
  r: number;
  g: number;
  b: number;
}

export interface AppearanceCheckOutput {
  contrastRatio: number;
  contrastVerdict: "OK" | "WARNING" | "BLOCKED";
  eyeContrastRatio: number;
  eyeVerdict: "OK" | "WARNING" | "BLOCKED";
  eyeMatchesBackground: boolean;
  backgroundDarkerThanForeground: boolean;
}

/** FR-402/FR-407 — the same WCAG contrast floor the Compose app's export gate enforces. */
export async function checkAppearance(
  foreground: RgbInput,
  background: RgbInput,
  eye: RgbInput,
): Promise<AppearanceCheckOutput> {
  const result = checkContrast(
    foreground.r,
    foreground.g,
    foreground.b,
    background.r,
    background.g,
    background.b,
    eye.r,
    eye.g,
    eye.b,
  );
  return {
    contrastRatio: result.contrastRatio,
    contrastVerdict: result.contrastVerdict as AppearanceCheckOutput["contrastVerdict"],
    eyeContrastRatio: result.eyeContrastRatio,
    eyeVerdict: result.eyeVerdict as AppearanceCheckOutput["eyeVerdict"],
    eyeMatchesBackground: result.eyeMatchesBackground,
    backgroundDarkerThanForeground: result.backgroundDarkerThanForeground,
  };
}

export interface LogoSizeBoundsOutput {
  minFraction: number;
  maxFraction: number;
  defaultFraction: number;
  warningFraction: number;
}

export async function getLogoSizeBounds(): Promise<LogoSizeBoundsOutput> {
  const bounds = logoSizeBounds();
  return {
    minFraction: bounds.minFraction,
    maxFraction: bounds.maxFraction,
    defaultFraction: bounds.defaultFraction,
    warningFraction: bounds.warningFraction,
  };
}
