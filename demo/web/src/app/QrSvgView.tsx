"use client";

/**
 * Renders SVG markup returned by the renderQrSvg Server Action, which itself is just
 * QrModuleMatrixJs.toSvg — QrSvgRenderer.kt's output, the exact renderer every Compose
 * platform's SVG export calls. This component draws nothing itself; there is no
 * hand-ported Canvas drawing algorithm here (there used to be — see git history — before
 * :qr grew a shared SVG renderer for every platform, including this one, to call).
 */
export function QrSvgView({ svg }: { svg: string | null }) {
  if (!svg) return null;
  return <div className="[&>svg]:h-full [&>svg]:w-full" dangerouslySetInnerHTML={{ __html: svg }} />;
}
