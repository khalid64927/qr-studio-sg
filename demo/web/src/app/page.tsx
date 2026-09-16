"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { generatePayNowQr, type GenerateQrOutput, type ProxyTypeInput } from "./actions";
import { QrCanvas } from "./QrCanvas";

const DEBOUNCE_MS = 300;

const PROXY_TYPES: { value: ProxyTypeInput; label: string; placeholder: string; example: string }[] = [
  { value: "MOBILE", label: "Mobile number", placeholder: "9123 4567", example: "e.g. 9123 4567" },
  { value: "UEN", label: "UEN", placeholder: "201403121W", example: "e.g. 201403121W" },
];

export default function Home() {
  const [proxyType, setProxyType] = useState<ProxyTypeInput>("MOBILE");
  const [proxyValue, setProxyValue] = useState("");
  const [merchantName, setMerchantName] = useState("Demo Merchant");
  const [amount, setAmount] = useState("25.50");
  const [amountEditable, setAmountEditable] = useState(false);
  const [reference, setReference] = useState("INV-DEMO-001");

  const [result, setResult] = useState<GenerateQrOutput | null>(null);
  const [pending, startTransition] = useTransition();
  const requestId = useRef(0);

  const activeProxy = PROXY_TYPES.find((p) => p.value === proxyType)!;
  const amountBlank = amount.trim() === "";

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
        <div className="space-y-6 rounded-2xl border border-border bg-surface p-8 shadow-sm">
          <Section title="Pay to">
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

          <Section title="Payment">
            <Field label="Amount (SGD)">
              <TextInput value={amount} onChange={setAmount} placeholder="Leave blank for any amount" />
            </Field>

            <div className="flex items-center justify-between rounded-lg border border-border bg-background px-4 py-3">
              <div>
                <p className="text-sm font-medium">
                  {amountBlank ? "The payer enters the amount" : "Let the payer change the amount"}
                </p>
              </div>
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
        </div>

        <div className="md:sticky md:top-8">
          <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
            <div className="flex aspect-square items-center justify-center rounded-xl border border-border bg-white p-4">
              {proxyValue.trim() === "" ? (
                <p className="px-6 text-center text-sm text-muted">
                  Enter a {activeProxy.label.toLowerCase()} to see the QR code
                </p>
              ) : result?.matrix ? (
                <QrCanvas matrix={result.matrix} />
              ) : (
                <p className="px-6 text-center text-sm text-muted">
                  {pending ? "Generating…" : "Fix the highlighted fields to see the QR code"}
                </p>
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

            {!!result?.errors.length && (
              <Banner kind="danger" messages={result.errors} />
            )}
            {!!result?.warnings.length && (
              <Banner kind="warning" messages={result.warnings} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4 border-t border-border pt-6 first:border-t-0 first:pt-0">
      <h2 className="text-sm font-semibold tracking-wide text-muted uppercase">{title}</h2>
      <div className="space-y-4">{children}</div>
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
