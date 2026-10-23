// 앱 아이콘(홈 화면 설치, 아이폰 홈 화면, 브라우저 탭)을 그린다.
// 그림: 전투복 무늬 색으로 칠한 남한을 배경으로, 거꾸로 세운 총 위에 태극기를 그린 철모를 얹는다. 전사자를 기리는 모습이다.
// 실행: frontend 폴더에서 `npm run icons`. SVG로 그린 뒤 sharp로 PNG를 만든다.
// sharp는 Next.js가 함께 설치한다(next의 optionalDependencies). 그래서 package.json에 따로 적지 않는다.
// 디자인 시안: https://claude.ai/artifact/XfkEpyCFB3uEko4UUYFehJ (D2. 남한 + 전투복 무늬)

import { mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUTLINE = join(FRONTEND, "../data/reference/south_korea_outline.geojson");

// 아이콘은 512×512 좌표로 그린다
const SIZE = 512;

const COLORS = {
  sea: "#e9edf1", // 바탕. 앱 바다색(src/lib/map-style.ts)과 같다
  // 남한을 칠하는 무늬 색. 육군 전투복(화강암 무늬, 2010년 채택)의 다섯 색(검정, 짙은 올리브, 회녹색, 갈색, 카키)에 맞춰 고른 값이다.
  // 공식 색 값은 공개되지 않아 근사치이고, 무늬 모양은 군복 도안을 옮기지 않고 아래 camo()가 새로 만든다
  camo: {
    khaki: "#b3a985",
    seaGreen: "#6b8468",
    olive: "#454f31",
    brown: "#735639",
    black: "#22231e",
  },
  coast: "#3a4129", // 남한 해안선 (무늬보다 조금 짙은 올리브)
  helmet: "#5a6142",
  helmetLight: "#6f7856",
  brim: "#4a5036",
  wood: "#6e4b2f",
  woodDark: "#5f4029",
  metal: "#3b3f44",
  sling: "#3d3426",
  // 태극기 색: 대한민국국기법 시행령 별표의 CIE 값(빨강 x 0.5640 y 0.3194 Y 15.3, 파랑 x 0.1556 y 0.1354 Y 6.5)을 sRGB로 바꾼 값
  flagRed: "#CD2E3A",
  flagBlue: "#0047A0",
  flagBlack: "#000000",
  flagWhite: "#FFFFFF",
};

const n = (value) => Number(value.toFixed(3));

// 태극기 (깃면 300×200). 작도법은 국가기록원 "국기 제작"을 따른다.
// - 태극: 지름은 깃면 세로의 1/2, 중심은 대각선의 교점. 빨강(위)과 파랑(아래)의 경계는
//   왼쪽 위→오른쪽 아래 대각선 위의 지름을 이등분한 두 반원
// - 괘: 대각선 위, 태극에서 지름의 1/4 떨어진 곳. 길이는 지름의 1/2, 너비는 지름의 1/3.
//   효 두께는 괘 너비의 1/4, 효 사이와 끊어진 틈은 괘 너비의 1/8
// - 건(☰) 왼쪽 위, 곤(☷) 오른쪽 아래, 감(☵) 오른쪽 위, 리(☲) 왼쪽 아래
function taegukgi() {
  const W = 300;
  const H = 200;
  const cx = W / 2;
  const cy = H / 2;
  const D = H / 2;
  const r = D / 2;
  const diag = Math.hypot(W, H);
  const u = [W / diag, H / diag]; // 왼쪽 위 → 오른쪽 아래
  const v = [W / diag, -H / diag]; // 왼쪽 아래 → 오른쪽 위

  const p1 = [cx - r * u[0], cy - r * u[1]];
  const p2 = [cx + r * u[0], cy + r * u[1]];
  const red =
    `M${n(p1[0])} ${n(p1[1])}A${r} ${r} 0 0 1 ${n(p2[0])} ${n(p2[1])}` +
    `A${r / 2} ${r / 2} 0 0 0 ${cx} ${cy}A${r / 2} ${r / 2} 0 0 1 ${n(p1[0])} ${n(p1[1])}Z`;

  const length = D / 2;
  const width = D / 3;
  const bar = width / 4;
  const gap = width / 8;
  const distance = r + D / 4 + width / 2; // 태극 중심에서 괘 중심까지
  const angle = (Math.atan2(H, W) * 180) / Math.PI;

  // 1은 이어진 효, 0은 끊어진 효
  const trigram = ([x, y], rotate, lines) => {
    const rects = lines.flatMap((solid, i) => {
      const top = n(-width / 2 + i * (bar + gap));
      if (solid) {
        return [`<rect x="${n(-length / 2)}" y="${top}" width="${n(length)}" height="${n(bar)}"/>`];
      }
      const half = n((length - gap) / 2);
      return [
        `<rect x="${n(-length / 2)}" y="${top}" width="${half}" height="${n(bar)}"/>`,
        `<rect x="${n(gap / 2)}" y="${top}" width="${half}" height="${n(bar)}"/>`,
      ];
    });
    return `<g transform="translate(${n(x)} ${n(y)}) rotate(${n(rotate)})">${rects.join("")}</g>`;
  };

  const at = (dir, sign) => [cx + sign * distance * dir[0], cy + sign * distance * dir[1]];
  return [
    `<rect width="${W}" height="${H}" fill="${COLORS.flagWhite}"/>`,
    `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${COLORS.flagBlue}"/>`,
    `<path d="${red}" fill="${COLORS.flagRed}"/>`,
    `<g fill="${COLORS.flagBlack}">`,
    trigram(at(u, -1), angle - 90, [1, 1, 1]), // 건
    trigram(at(u, 1), angle - 90, [0, 0, 0]), // 곤
    trigram(at(v, 1), 90 - angle, [0, 1, 0]), // 감
    trigram(at(v, -1), 90 - angle, [1, 0, 1]), // 리
    `</g>`,
  ].join("");
}

// 배경 남한: data/reference/south_korea_outline.geojson (Natural Earth의 대한민국, 북쪽 경계는 군사분계선).
// 본토가 아이콘 높이를 거의 채우게 맞춘다. 가로는 본토 가운데 위도에서 경도 1°가 짧아지는 만큼 줄이고,
// 1px보다 작은 굴곡은 줄인다(Douglas–Peucker). 틀 밖으로 나가는 섬(제주, 울릉도, 독도)은 잘린다.
// 전투복 무늬로 칠하고, 바탕과 구분되게 해안선을 두른다.
async function southKorea() {
  const MARGIN = 8;
  const TOLERANCE = 1;

  // 남한 조각들에는 구멍이 없어 바깥 고리만 쓴다
  const rings = JSON.parse(readFileSync(OUTLINE, "utf8")).features[0].geometry.coordinates.map((polygon) => polygon[0]);
  const mainland = rings.reduce((a, b) => (b.length > a.length ? b : a));
  const lons = mainland.map(([lon]) => lon);
  const lats = mainland.map(([, lat]) => lat);
  const [latMin, latMax] = [Math.min(...lats), Math.max(...lats)];
  const lonCenter = (Math.min(...lons) + Math.max(...lons)) / 2;
  const scale = (SIZE - 2 * MARGIN) / (latMax - latMin);
  const cos = Math.cos((((latMin + latMax) / 2) * Math.PI) / 180);
  const toPx = ([lon, lat]) => [SIZE / 2 + (lon - lonCenter) * cos * scale, MARGIN + (latMax - lat) * scale];

  const d = rings
    .map((ring) => ring.map(toPx))
    .filter((points) => points.some(([x, y]) => x >= 0 && x <= SIZE && y >= 0 && y <= SIZE))
    .map((points) => {
      // 닫힌 고리는 첫 점에서 가장 먼 점에서 둘로 나눠 단순화한다
      let far = 0;
      points.forEach((p, i) => {
        if (dist(points[0], p) > dist(points[0], points[far])) far = i;
      });
      return [...simplify(points.slice(0, far + 1), TOLERANCE).slice(0, -1), ...simplify(points.slice(far), TOLERANCE).slice(0, -1)];
    })
    .filter((points) => points.length >= 3)
    .map((points) => "M" + points.map(([x, y]) => `${x.toFixed(1)} ${y.toFixed(1)}`).join("L") + "Z")
    .join("");

  return [
    `<clipPath id="south-korea"><path d="${d}"/></clipPath>`,
    `<g clip-path="url(#south-korea)">${await camo()}</g>`,
    `<path d="${d}" fill="none" stroke="${COLORS.coast}" stroke-width="3" stroke-linejoin="round"/>`,
  ].join("");
}

// 디지털 위장 무늬: 8px 칸(64×64)마다 색 하나. 성긴 격자의 난수를 부드럽게 이은 잡음으로 얼룩을 만들고,
// 칸마다 잡음을 더해 얼룩 가장자리를 잘게 부순다. 카키 위에 회녹색, 갈색, 올리브, 검정 순으로 덮는다.
// 시드를 고정해 다시 돌려도 같은 무늬가 나온다.
async function camo() {
  const N = 64;
  const CELL = SIZE / N;
  const random = mulberry32(19500625);

  const field = (grid, jitter) => {
    const lattice = Array.from({ length: (grid + 1) ** 2 }, () => random());
    const at = (i, j) => lattice[j * (grid + 1) + i];
    const smooth = (t) => t * t * (3 - 2 * t);
    return Array.from({ length: N * N }, (_, k) => {
      const gx = ((k % N) + 0.5) * (grid / N);
      const gy = (Math.floor(k / N) + 0.5) * (grid / N);
      const [i, j] = [Math.floor(gx), Math.floor(gy)];
      const [tx, ty] = [smooth(gx - i), smooth(gy - j)];
      const top = at(i, j) * (1 - tx) + at(i + 1, j) * tx;
      const bottom = at(i, j + 1) * (1 - tx) + at(i + 1, j + 1) * tx;
      return top * (1 - ty) + bottom * ty + (random() - 0.5) * jitter;
    });
  };

  // 덮는 기준값으로 색 비율을 맞춘다. 지금 값이면 카키 23%, 회녹색 28%, 올리브 18%, 갈색 19%, 검정 12% 정도다
  const { khaki, seaGreen, brown, olive, black } = COLORS.camo;
  const layers = [
    [seaGreen, field(10, 0.35), 0.48],
    [brown, field(12, 0.35), 0.74],
    [olive, field(10, 0.35), 0.72],
    [black, field(14, 0.4), 0.85],
  ];
  const colors = Array.from({ length: N * N }, (_, k) =>
    layers.reduce((color, [layerColor, values, threshold]) => (values[k] > threshold ? layerColor : color), khaki),
  );

  // 칸을 사각형으로 그리면 줄 사이에 틈이 비쳐서, 512×512 이미지 한 장으로 칠해 SVG에 넣는다
  const pixels = Buffer.alloc(SIZE * SIZE * 3);
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const hex = colors[Math.floor(y / CELL) * N + Math.floor(x / CELL)];
      pixels.set([1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)), (y * SIZE + x) * 3);
    }
  }
  const png = await sharp(pixels, { raw: { width: SIZE, height: SIZE, channels: 3 } }).png().toBuffer();
  return `<image width="${SIZE}" height="${SIZE}" href="data:image/png;base64,${png.toString("base64")}"/>`;
}

// 시드로 정해지는 난수 (mulberry32)
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function dist([x1, y1], [x2, y2]) {
  return Math.hypot(x2 - x1, y2 - y1);
}

function simplify(points, tolerance) {
  if (points.length < 3) return points;
  const [x1, y1] = points[0];
  const [x2, y2] = points[points.length - 1];
  const length = Math.hypot(x2 - x1, y2 - y1) || 1e-9;
  let best = -1;
  let index = 0;
  for (let i = 1; i < points.length - 1; i++) {
    const [x0, y0] = points[i];
    const d = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1) / length;
    if (d > best) {
      best = d;
      index = i;
    }
  }
  if (best > tolerance) {
    return [...simplify(points.slice(0, index + 1), tolerance).slice(0, -1), ...simplify(points.slice(index), tolerance)];
  }
  return [points[0], points[points.length - 1]];
}

// 거꾸로 세운 총(개머리판이 위)과 멜빵. 총구 쪽은 아이콘 아래로 이어진다
function rifle() {
  return [
    `<path d="M244 340C222 400 214 460 220 620" fill="none" stroke="${COLORS.sling}" stroke-width="5"/>`,
    `<polygon points="230,284 282,284 270,344 242,344" fill="${COLORS.wood}"/>`,
    `<rect x="242" y="344" width="28" height="48" fill="${COLORS.metal}"/>`,
    `<path d="M270 356h12a12 12 0 0 1 0 24h-12" fill="none" stroke="${COLORS.metal}" stroke-width="6"/>`,
    `<rect x="244" y="392" width="24" height="230" fill="${COLORS.woodDark}"/>`,
    `<rect x="240" y="392" width="5" height="230" fill="${COLORS.metal}"/>`,
  ].join("");
}

// 철모와 총이 무늬에 묻히지 않게 둘레에 바탕색 테두리를 두른다 (모양은 rifle(), helmet()과 같다)
function halo(width) {
  const c = COLORS.sea;
  return [
    `<g fill="${c}" stroke="${c}" stroke-width="${width}" stroke-linejoin="round" stroke-linecap="round">`,
    `<path d="M244 340C222 400 214 460 220 620" fill="none" stroke-width="${5 + width}"/>`,
    `<polygon points="230,284 282,284 270,344 242,344"/>`,
    `<rect x="240" y="344" width="30" height="278"/>`,
    `<path d="M270 356h12a12 12 0 0 1 0 24h-12" fill="none" stroke-width="${6 + width}"/>`,
    `<path d="M106 274A150 170 0 0 1 406 274Z"/>`,
    `<rect x="94" y="262" width="324" height="24" rx="12"/>`,
    `</g>`,
  ].join("");
}

// 앞에서 본 M1 철모. 앞면에 태극기(150×100)를 그린다
function helmet() {
  return [
    `<path d="M106 274A150 170 0 0 1 406 274Z" fill="${COLORS.helmet}"/>`,
    `<path d="M132.8 243.6A126 146 0 0 1 171.7 165.5" fill="none" stroke="${COLORS.helmetLight}" stroke-width="10" stroke-linecap="round"/>`,
    `<g transform="translate(181 150) scale(0.5)">${taegukgi()}</g>`,
    `<rect x="94" y="262" width="324" height="24" rx="12" fill="${COLORS.brim}"/>`,
  ].join("");
}

// maskable: 안드로이드가 원·둥근 사각형으로 잘라 쓰는 아이콘. 그림을 90%로 줄여 가운데 원(지름 80%) 안에 철모가 들게 한다
function iconSvg(background, { maskable }) {
  const art = background + halo(12) + rifle() + helmet();
  const body = maskable ? `<g transform="translate(${SIZE * 0.05} ${SIZE * 0.05}) scale(0.9)">${art}</g>` : art;
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}" viewBox="0 0 ${SIZE} ${SIZE}">` +
    `<rect width="${SIZE}" height="${SIZE}" fill="${COLORS.sea}"/>${body}</svg>`
  );
}

const outputs = [
  ["public/icons/icon-192.png", 192, false], // manifest 일반 아이콘
  ["public/icons/icon-512.png", 512, false], // manifest 일반 아이콘
  ["public/icons/icon-maskable-512.png", 512, true], // manifest 마스크 아이콘
  ["src/app/apple-icon.png", 180, false], // 아이폰 홈 화면 (모서리는 아이폰이 둥글게 자른다)
  ["src/app/icon.png", 64, false], // 브라우저 탭
];

const background = await southKorea();
for (const [path, size, maskable] of outputs) {
  const out = join(FRONTEND, path);
  mkdirSync(dirname(out), { recursive: true });
  // 2배 크기로 그린 뒤 줄여서 가장자리를 부드럽게 한다
  await sharp(Buffer.from(iconSvg(background, { maskable })), { density: (72 * 2 * size) / SIZE })
    .resize(size, size)
    .png({ compressionLevel: 9 })
    .toFile(out);
  console.log(`${path} (${size}x${size})`);
}
