// Genera fixtures propios (CC0) para las pruebas de viabilidad de la Fase 0.
// Uso (en una carpeta temporal con `npm install sharp`):
//   node gen-fixtures.mjs <repo>/src/test/resources/fixtures
// y copiar el resultado a <repo>/src/gametest/resources/notifymod-poc/ (lo usa el test de cliente).
// sample_lottie.json está escrito a mano y no lo genera este script.
import sharp from "sharp";
import fs from "node:fs";
import path from "node:path";

const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const W = 32, H = 32;
const colors = [[230, 40, 40], [40, 200, 70], [40, 90, 230]];

function frame(i) {
  const buf = Buffer.alloc(W * H * 4);
  const [r, g, b] = colors[i];
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const o = (y * W + x) * 4;
      // Cuadrado de 16x16 que se mueve en diagonal; el resto, transparente
      const inside = x >= i * 8 && x < i * 8 + 16 && y >= i * 8 && y < i * 8 + 16;
      buf[o] = r; buf[o + 1] = g; buf[o + 2] = b; buf[o + 3] = inside ? 255 : 0;
    }
  }
  return buf;
}

const frames = [0, 1, 2].map(frame);
const delays = [100, 200, 300];
const strip = Buffer.concat(frames);
const raw = { width: W, height: H * frames.length, channels: 4, pageHeight: H };

async function save(name, fn) {
  try {
    await fn(path.join(out, name));
    console.log("OK", name, fs.statSync(path.join(out, name)).size, "bytes");
  } catch (e) {
    console.log("FAIL", name, e.message);
  }
}

await save("sample.gif", f => sharp(strip, { raw, animated: true }).gif({ loop: 0, delay: delays }).toFile(f));
await save("sample_lossless.webp", f => sharp(strip, { raw, animated: true }).webp({ lossless: true, loop: 0, delay: delays }).toFile(f));
await save("sample_lossy_alpha.webp", f => sharp(strip, { raw, animated: true }).webp({ quality: 90, alphaQuality: 100, loop: 0, delay: delays }).toFile(f));
await save("sample_still.webp", f => sharp(frames[1], { raw: { width: W, height: H, channels: 4 } }).webp({ lossless: true }).toFile(f));
for (const n of ["sample.gif", "sample_lossless.webp", "sample_lossy_alpha.webp"]) {
  try {
    const m = await sharp(path.join(out, n), { animated: true }).metadata();
    console.log("META", n, JSON.stringify({ w: m.width, h: m.height, pages: m.pages, pageHeight: m.pageHeight, delay: m.delay }));
  } catch (e) { console.log("META FAIL", n, e.message); }
}

