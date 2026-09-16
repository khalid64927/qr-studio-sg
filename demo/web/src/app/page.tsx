"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import {
  checkAppearance,
  generatePayNowQr,
  getLogoSizeBounds,
  renderQrSvg,
  type AppearanceCheckOutput,
  type EmbeddedLogoImageInput,
  type GenerateQrOutput,
  type LogoSizeBoundsOutput,
  type ProxyTypeInput,
  type RgbInput,
} from "./actions";
import { QrSvgView } from "./QrSvgView";

const DEBOUNCE_MS = 300;
const APPEARANCE_DEBOUNCE_MS = 150;
const SVG_DEBOUNCE_MS = 100;
const PREVIEW_SIZE = 280;

type ModuleShapeValue = "SQUARE" | "ROUNDED" | "DOT";
type LogoShapeValue = "SQUARE" | "ROUNDED" | "CIRCLE";

const PROXY_TYPES: { value: ProxyTypeInput; label: string; placeholder: string; example: string }[] = [
  { value: "MOBILE", label: "Mobile number", placeholder: "9123 4567", example: "e.g. 9123 4567" },
  { value: "UEN", label: "UEN", placeholder: "201403121W", example: "e.g. 201403121W" },
];

const MODULE_SHAPES: { value: ModuleShapeValue; label: string }[] = [
  { value: "SQUARE", label: "Square" },
  { value: "ROUNDED", label: "Rounded" },
  { value: "DOT", label: "Dot" },
];

const LOGO_SHAPES: { value: LogoShapeValue; label: string }[] = [
  { value: "SQUARE", label: "Square" },
  { value: "ROUNDED", label: "Rounded" },
  { value: "CIRCLE", label: "Circle" },
];

const BLACK: RgbInput = { r: 0, g: 0, b: 0 };
const WHITE: RgbInput = { r: 1, g: 1, b: 1 };

function hexToRgb01(hex: string): RgbInput {
  const n = parseInt(hex.slice(1), 16);
  return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 };
}

function rgb01ToHex({ r, g, b }: RgbInput): string {
  const c = (v: number) => Math.round(v * 255).toString(16).padStart(2, "0");
  return `#${c(r)}${c(g)}${c(b)}`;
}

export default function Home() {
  const [proxyType, setProxyType] = useState<ProxyTypeInput>("MOBILE");
  const [proxyValue, setProxyValue] = useState("");
  const [merchantName, setMerchantName] = useState("Demo Merchant");
  const [amount, setAmount] = useState("25.50");
  const [amountEditable, setAmountEditable] = useState(false);
  const [reference, setReference] = useState("INV-DEMO-001");

  const [foreground, setForeground] = useState<RgbInput>(BLACK);
  const [background, setBackground] = useState<RgbInput>(WHITE);
  const [customEyeColor, setCustomEyeColor] = useState(false);
  const [eyeColorValue, setEyeColorValue] = useState<RgbInput>(BLACK);
  const [moduleShape, setModuleShape] = useState<ModuleShapeValue>("SQUARE");
  const [eyeShape, setEyeShape] = useState<ModuleShapeValue>("SQUARE");
  const eyeColor = customEyeColor ? eyeColorValue : null;

  const [logoBounds, setLogoBounds] = useState<LogoSizeBoundsOutput | null>(null);
  const [logoEnabled, setLogoEnabled] = useState(false);
  const [logoSizeFraction, setLogoSizeFraction] = useState(0.2);
  const [logoShape, setLogoShape] = useState<LogoShapeValue>("ROUNDED");
  const [logoImage, setLogoImage] = useState<EmbeddedLogoImageInput | null>(null);
  const [logoFileName, setLogoFileName] = useState<string | null>(null);
  const [logoFileError, setLogoFileError] = useState<string | null>(null);

  const [result, setResult] = useState<GenerateQrOutput | null>(null);
  const [svg, setSvg] = useState<string | null>(null);
  const [appearanceCheck, setAppearanceCheck] = useState<AppearanceCheckOutput | null>(null);
  const [pending, startTransition] = useTransition();
  const requestId = useRef(0);
  const appearanceRequestId = useRef(0);
  const svgRequestId = useRef(0);

  const activeProxy = PROXY_TYPES.find((p) => p.value === proxyType)!;
  const amountBlank = amount.trim() === "";

  useEffect(() => {
    getLogoSizeBounds().then((bounds) => {
      setLogoBounds(bounds);
      setLogoSizeFraction(bounds.defaultFraction);
    });
  }, []);

  useEffect(() => {
    // Blank proxy is the starting state, not a mistake — the JSX below checks
    // proxyValue directly and ignores any stale `result`, so there is nothing to
    // reset here.
    if (proxyValue.trim() === "") return;

    const id = ++requestId.current;
    const timer = setTimeout(() => {
      startTransition(async () => {
        const next = await generatePayNowQr({
          proxyType,
          proxyValue,
          amount,
          amountEditable: amountBlank ? true : amountEditable,
          reference,
          merchantName,
        });
        if (id === requestId.current) setResult(next);
      });
    }, DEBOUNCE_MS);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [proxyType, proxyValue, amount, amountEditable, reference, merchantName]);

  useEffect(() => {
    const id = ++appearanceRequestId.current;
    const timer = setTimeout(() => {
      startTransition(async () => {
        const next = await checkAppearance(foreground, background, eyeColor ?? foreground);
        if (id === appearanceRequestId.current) setAppearanceCheck(next);
      });
    }, APPEARANCE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [foreground, background, eyeColor]);

  // Re-renders the SVG whenever the payload or any Branding/Appearance value changes —
  // this is the only place the preview gets drawn, and it never draws anything itself;
  // it just displays whatever renderQrSvg (i.e. :qr's QrSvgRenderer) returns.
  useEffect(() => {
    // A failed/empty result is handled directly by `showQr` below, derived from
    // `result` rather than by resetting `svg` here — nothing to synchronize this effect
    // needs to do for that case.
    if (!result?.success || !result.raw) return;

    const id = ++svgRequestId.current;
    const timer = setTimeout(() => {
      startTransition(async () => {
        const next = await renderQrSvg({
          payload: result.raw!,
          foreground,
          background,
          eyeColor,
          moduleShape,
          eyeShape,
          logoEnabled,
          logoSizeFraction,
          logoShape,
          logoImage,
        });
        if (id === svgRequestId.current) setSvg(next);
      });
    }, SVG_DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [result, foreground, background, eyeColor, moduleShape, eyeShape, logoEnabled, logoSizeFraction, logoShape, logoImage]);

  const MAX_LOGO_BYTES = 5 * 1024 * 1024;

  function handleLogoFileSelected(file: File | undefined) {
    setLogoFileError(null);
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setLogoFileError("Choose an image file (PNG, JPG, etc).");
      return;
    }
    if (file.size > MAX_LOGO_BYTES) {
      setLogoFileError("Image is larger than 5MB.");
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const dataUri = reader.result as string;
      const probe = new Image();
      probe.onload = () => {
        setLogoImage({ dataUri, width: probe.naturalWidth, height: probe.naturalHeight });
        setLogoFileName(file.name);
      };
      probe.onerror = () => setLogoFileError("Couldn't read that image file.");
      probe.src = dataUri;
    };
    reader.onerror = () => setLogoFileError("Couldn't read that image file.");
    reader.readAsDataURL(file);
  }

  function handleLogoImageRemoved() {
    setLogoImage(null);
    setLogoFileName(null);
    setLogoFileError(null);
  }

  const showQr = proxyValue.trim() !== "" && result?.success === true && !!svg;

  return (
    <div className="mx-auto w-full max-w-5xl flex-1 px-6 py-12">
      <header className="mb-10 flex items-center gap-3">
        <div className="h-8 w-2 rounded-full bg-accent" />
        <div>
          <h1 className="text-xl font-semibold tracking-tight">PayNow QR Demo</h1>
          <p className="text-sm text-muted">
            Native JS — <code className="font-mono text-xs">@khalid64927/qr-studio-sg-payload</code>{" "}
            + <code className="font-mono text-xs">qr-studio-sg-qr</code>, zero Compose Multiplatform.
          </p>
        </div>
      </header>

      <div className="grid gap-6 md:grid-cols-[1fr_320px] md:items-start">
        <div className="space-y-2 rounded-2xl border border-border bg-surface p-8 shadow-sm">
          <Section title="Pay to" defaultOpen>
            <Field label="Recipient type">
              <SegmentedControl
                options={PROXY_TYPES}
                value={proxyType}
                onChange={(v) => {
                  setProxyType(v);
                  setProxyValue("");
                }}
              />
            </Field>

            <Field label={activeProxy.label}>
              <TextInput
                value={proxyValue}
                onChange={setProxyValue}
                placeholder={activeProxy.placeholder}
                error={result?.errors.length ? result.errors[0] : undefined}
              />
              <p className="mt-1.5 text-xs text-muted">{activeProxy.example}</p>
            </Field>

            <Field label="Name payers will see">
              <TextInput value={merchantName} onChange={setMerchantName} placeholder="Shown to the payer" />
            </Field>
          </Section>

          <Section title="Payment" defaultOpen>
            <Field label="Amount (SGD)">
              <TextInput value={amount} onChange={setAmount} placeholder="Leave blank for any amount" />
            </Field>

            <div className="flex items-center justify-between rounded-lg border border-border bg-background px-4 py-3">
              <p className="text-sm font-medium">
                {amountBlank ? "The payer enters the amount" : "Let the payer change the amount"}
              </p>
              <Toggle
                checked={amountBlank || amountEditable}
                disabled={amountBlank}
                onChange={setAmountEditable}
              />
            </div>

            <Field label="Reference">
              <TextInput value={reference} onChange={setReference} placeholder="INV-2026-001" />
            </Field>
          </Section>

          <Section title="Branding">
            <div className="flex items-center justify-between rounded-lg border border-border bg-background px-4 py-3">
              <p className="text-sm font-medium">Centre logo</p>
              <Toggle checked={logoEnabled} onChange={setLogoEnabled} />
            </div>

            {logoEnabled && logoBounds && (
              <>
                <Field label="Logo image">
                  <div className="flex items-center gap-3">
                    <label className="cursor-pointer rounded-lg border border-border bg-surface px-3.5 py-2 text-sm font-medium transition-colors hover:border-accent">
                      Choose file
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleLogoFileSelected(e.target.files?.[0])}
                      />
                    </label>
                    {logoFileName && (
                      <>
                        <span className="truncate text-sm text-muted">{logoFileName}</span>
                        <button
                          type="button"
                          onClick={handleLogoImageRemoved}
                          className="text-sm font-medium text-danger hover:underline"
                        >
                          Remove
                        </button>
                      </>
                    )}
                  </div>
                  {logoFileError && <p className="mt-1.5 text-xs text-danger">{logoFileError}</p>}
                  {!logoFileName && (
                    <p className="mt-1.5 text-xs text-muted">No image chosen — a placeholder mark is shown.</p>
                  )}
                </Field>

                <Field label={`Logo size — ${Math.round(logoSizeFraction * 100)}%`}>
                  <input
                    type="range"
                    min={logoBounds.minFraction}
                    max={logoBounds.maxFraction}
                    step={0.01}
                    value={logoSizeFraction}
                    onChange={(e) => setLogoSizeFraction(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                  {logoSizeFraction > logoBounds.warningFraction && (
                    <p className="mt-1.5 text-xs text-warning">
                      Above {Math.round(logoBounds.warningFraction * 100)}% risks covering too much of the
                      symbol for reliable scanning.
                    </p>
                  )}
                </Field>

                <Field label="Logo shape">
                  <SegmentedControl options={LOGO_SHAPES} value={logoShape} onChange={setLogoShape} />
                </Field>
              </>
            )}
          </Section>

          <Section title="Appearance">
            <Field label="Foreground colour">
              <ColorInput value={foreground} onChange={setForeground} />
            </Field>
            <Field label="Background colour">
              <ColorInput value={background} onChange={setBackground} />
            </Field>

            <div className="flex items-center justify-between rounded-lg border border-border bg-background px-4 py-3">
              <p className="text-sm font-medium">Custom eye colour</p>
              <Toggle checked={customEyeColor} onChange={setCustomEyeColor} />
            </div>
            {customEyeColor && (
              <Field label="Eye colour">
                <ColorInput value={eyeColorValue} onChange={setEyeColorValue} />
              </Field>
            )}

            <Field label="Module shape">
              <SegmentedControl options={MODULE_SHAPES} value={moduleShape} onChange={setModuleShape} />
            </Field>
            <Field label="Eye style">
              <SegmentedControl options={MODULE_SHAPES} value={eyeShape} onChange={setEyeShape} />
            </Field>

            {appearanceCheck && <ContrastBanner check={appearanceCheck} />}
          </Section>
        </div>

        <div className="md:sticky md:top-8">
          <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
            <div
              className="relative flex aspect-square items-center justify-center rounded-xl border border-border bg-white p-4"
              style={{ width: PREVIEW_SIZE + 32, height: PREVIEW_SIZE + 32 }}
            >
              {!showQr ? (
                <p className="px-6 text-center text-sm text-muted">
                  {proxyValue.trim() === ""
                    ? `Enter a ${activeProxy.label.toLowerCase()} to see the QR code`
                    : pending
                      ? "Generating…"
                      : "Fix the highlighted fields to see the QR code"}
                </p>
              ) : (
                <div className="relative h-full w-full">
                  <QrSvgView svg={svg} />
                  {logoEnabled && !logoImage && <LogoPlaceholderIcon sizeFraction={logoSizeFraction} background={background} />}
                </div>
              )}
            </div>

            {result?.success && (
              <div className="mt-4 space-y-2">
                <p className="text-xs font-medium tracking-wide text-muted uppercase">Proxy</p>
                <p className="font-mono text-sm">{result.proxyDisplay}</p>
                <p className="text-xs font-medium tracking-wide text-muted uppercase">Raw payload</p>
                <p className="break-all rounded-lg bg-background p-3 font-mono text-[11px] leading-relaxed text-muted">
                  {result.raw}
                </p>
              </div>
            )}

            {!!result?.errors.length && <Banner kind="danger" messages={result.errors} />}
            {!!result?.warnings.length && <Banner kind="warning" messages={result.warnings} />}
          </div>
        </div>
      </div>
    </div>
  );
}

function Section({
  title,
  defaultOpen = false,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className="border-t border-border py-4 first:border-t-0 first:pt-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between py-2 text-left"
      >
        <h2 className="text-sm font-semibold tracking-wide text-muted uppercase">{title}</h2>
        <svg
          viewBox="0 0 20 20"
          fill="none"
          className={`h-4 w-4 text-muted transition-transform ${open ? "rotate-180" : ""}`}
        >
          <path d="M5 7.5L10 12.5L15 7.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </button>
      {open && <div className="space-y-4 pt-2">{children}</div>}
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium">{label}</span>
      {children}
    </label>
  );
}

function TextInput({
  value,
  onChange,
  placeholder,
  error,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  error?: string;
}) {
  return (
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className={`w-full rounded-lg border bg-surface px-3.5 py-2.5 text-sm outline-none transition-colors placeholder:text-muted/70 focus:ring-2 focus:ring-accent/30 ${
        error ? "border-danger focus:border-danger" : "border-border focus:border-accent"
      }`}
    />
  );
}

function ColorInput({ value, onChange }: { value: RgbInput; onChange: (value: RgbInput) => void }) {
  const hex = rgb01ToHex(value);
  return (
    <div className="flex items-center gap-3 rounded-lg border border-border bg-surface px-3 py-2">
      <input
        type="color"
        value={hex}
        onChange={(e) => onChange(hexToRgb01(e.target.value))}
        className="h-8 w-8 shrink-0 cursor-pointer rounded-md border border-border"
      />
      <span className="font-mono text-sm text-muted uppercase">{hex}</span>
    </div>
  );
}

function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="inline-flex rounded-full bg-background p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
            value === option.value
              ? "bg-surface text-foreground shadow-sm"
              : "text-muted hover:text-foreground"
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function Toggle({
  checked,
  disabled,
  onChange,
}: {
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
        checked ? "bg-accent" : "bg-border"
      }`}
    >
      <span
        className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
          checked ? "translate-x-5" : "translate-x-0"
        }`}
      />
    </button>
  );
}

function Banner({ kind, messages }: { kind: "danger" | "warning"; messages: string[] }) {
  const styles =
    kind === "danger"
      ? "bg-danger-bg border-danger-border text-danger"
      : "bg-warning-bg border-warning-border text-warning";
  return (
    <div className={`mt-4 space-y-1 rounded-lg border px-3.5 py-3 text-xs ${styles}`}>
      {messages.map((message, i) => (
        <p key={i}>{message}</p>
      ))}
    </div>
  );
}

/** FR-402: BLOCKED must read as unambiguously blocking export, not just "a bit off". */
function ContrastBanner({ check }: { check: AppearanceCheckOutput }) {
  const verdictStyle: Record<AppearanceCheckOutput["contrastVerdict"], string> = {
    OK: "bg-accent/10 border-accent/30 text-[#087f3f]",
    WARNING: "bg-warning-bg border-warning-border text-warning",
    BLOCKED: "bg-danger-bg border-danger-border text-danger",
  };
  const verdictLabel: Record<AppearanceCheckOutput["contrastVerdict"], string> = {
    OK: "✓ Contrast OK",
    WARNING: "⚠ Contrast warning",
    BLOCKED: "✕ Contrast blocked",
  };
  return (
    <div className={`space-y-1 rounded-lg border px-3.5 py-3 text-xs ${verdictStyle[check.contrastVerdict]}`}>
      <p className="font-medium">
        {verdictLabel[check.contrastVerdict]} — {check.contrastRatio.toFixed(2)}:1
      </p>
      {check.contrastVerdict === "BLOCKED" && (
        <p>Below 3:1 — a scanner is unlikely to binarise these two colours apart reliably.</p>
      )}
      {check.contrastVerdict === "WARNING" && <p>Below 4.5:1 — scannable, but with less margin.</p>}
      {check.eyeMatchesBackground && <p>The eye colour matches the background exactly — the eyes will vanish.</p>}
      {check.backgroundDarkerThanForeground && (
        <p>Background reads darker than foreground — check this is intentional.</p>
      )}
    </div>
  );
}

/**
 * The logo backing plate itself comes from the SVG (:qr's QrSvgRenderer draws it); this
 * is only the "no image yet" glyph on top, sized as a plain percentage of the preview —
 * the plate is always centred at `logoSizeFraction` of the whole symbol, so no pixel
 * geometry needs to come back from the renderer for this to line up.
 */
function LogoPlaceholderIcon({
  sizeFraction,
  background,
}: {
  sizeFraction: number;
  background: RgbInput;
}) {
  const luminance = 0.2126 * background.r + 0.7152 * background.g + 0.0722 * background.b;
  const iconColor = luminance > 0.5 ? "#5b6572" : "#ffffff";
  const pct = `${sizeFraction * 100}%`;
  return (
    <div
      className="pointer-events-none absolute top-1/2 left-1/2 flex -translate-x-1/2 -translate-y-1/2 items-center justify-center"
      style={{ width: pct, height: pct }}
    >
      <svg viewBox="0 0 24 24" fill="none" className="h-1/2 w-1/2" style={{ color: iconColor }}>
        <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="8.5" cy="9.5" r="1.5" stroke="currentColor" strokeWidth="1.6" />
        <path
          d="M21 16L15.5 10.5C15.1 10.1 14.5 10.1 14.1 10.5L6 18.5"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}
