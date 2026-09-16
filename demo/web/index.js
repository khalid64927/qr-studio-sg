// Skeleton native-JS consumer: builds a PayNow QR the same way a React/Node UI would —
// no Compose Multiplatform, no :ui or :composeApp, just the two published npm packages'
// generated CommonJS API (see payload/qr's jsMain facades for why it's shaped this way).
//
// Both packages export under their Kotlin package path
// (sg.qrstudio.payload.js / sg.qrstudio.qr.js) rather than at the module root — that's
// how Kotlin/JS's CommonJS output namespaces everything by default.

const { sg: paySg } = require('@khalid64927/qr-studio-sg-payload');
const { sg: qrSg } = require('@khalid64927/qr-studio-sg-qr');

const { buildPayNowQr } = paySg.qrstudio.payload.js;
const { encodeQr } = qrSg.qrstudio.qr.js;

const result = buildPayNowQr(
  'MOBILE',
  '91234567',
  '25.50',
  false,
  null,
  'INV-DEMO-001',
  'Demo Merchant',
  '2026-09-16',
);

if (!result.success) {
  console.error('Invalid config:', result.errors);
  process.exit(1);
}

console.log('Built payload:', result.raw);
console.log('Normalised proxy:', result.proxyDisplay);

const matrix = encodeQr(result.raw, 'M');
let darkCount = 0;
for (let y = 0; y < matrix.size; y++) {
  for (let x = 0; x < matrix.size; x++) {
    if (matrix.isDark(x, y)) darkCount++;
  }
}

console.log(`QR module matrix: ${matrix.size}x${matrix.size}, mask pattern ${matrix.maskPattern}`);
console.log('Dark modules:', darkCount);
