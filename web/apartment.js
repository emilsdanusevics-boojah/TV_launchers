// The apartment model from "Apartment 95" (three.js) as the TV launcher's background:
// the model and its outdoor weather (sky, sun by the real time in Riga, clouds, rain, snow,
// fog, thunder) are unchanged from the original page; its controls and panel are replaced
// by a camera for the launcher, and the weather is fed by the app (window.setWeather).
// Bundled into ../assets/apartment/apartment.js by web/build.sh.
//
// Two views, chosen with #window (or ?view=window) in the page address:
//   orbit   (Apartment launcher) a slow turn around the flat seen from above
//   window  (Window launcher)    from the living room out through the terrace door

const VIEW = location.hash === "#window" || new URLSearchParams(location.search).get("view") === "window" ? "window" : "orbit";
const SCENE_COLOR = "#1d1e20";   // the original's dark-mode scene colour
const ORBIT_SECONDS = 150;       // one full turn

// The ceiling only shows when looking from inside.
function applyToggles() {
  if (ceiling) ceiling.visible = VIEW === "window";
}

import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";

/* ---------- renderer / scene ---------- */
const stage = document.getElementById("stage");
const renderer = new THREE.WebGLRenderer({ antialias: true });
// Render resolution, as a width in pixels. It starts a little under 1080p and is adjusted
// while running so the TV keeps up with every screen refresh (see "smooth motion" below).
const MIN_WIDTH = 960, MAX_WIDTH = 1920;
let renderWidth = 1600;
renderer.setPixelRatio(renderWidth / innerWidth);
renderer.setSize(innerWidth, innerHeight);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFShadowMap;   // the original's PCFSoft costs far more per pixel
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.0;
stage.appendChild(renderer.domElement);


const scene = new THREE.Scene();
scene.background = new THREE.Color(SCENE_COLOR);

const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;

const camera = new THREE.PerspectiveCamera(50, innerWidth / innerHeight, 0.05, 200);

/* ---------- lights ---------- */
const hemi = new THREE.HemisphereLight(0xf4f1ec, 0xcfcac2, 1.3);
scene.add(hemi);
const sun = new THREE.DirectionalLight(0xfff3e2, 2.2);
sun.position.set(-7, 11, -2);
sun.target.position.set(6, 0, 2.6);
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
Object.assign(sun.shadow.camera, { left: -10, right: 10, top: 10, bottom: -10, near: 1, far: 40 });
sun.shadow.bias = -0.0004;
sun.shadow.normalBias = 0.02;
scene.add(sun, sun.target);

/* ---------- procedural textures ---------- */
function canvasTex(w, h, draw, repeatMeters) {
  const c = document.createElement("canvas"); c.width = w; c.height = h;
  draw(c.getContext("2d"), w, h);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  if (repeatMeters) t.repeat.set(1 / repeatMeters[0], 1 / repeatMeters[1]);
  return t;
}
function rnd(seed) { let s = seed; return () => (s = (s * 16807) % 2147483647) / 2147483647; }

function noiseFill(ctx, w, h, base, amt, seed = 7) {
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h);
  const img = ctx.getImageData(0, 0, w, h), d = img.data, r = rnd(seed);
  for (let i = 0; i < d.length; i += 4) { const n = (r() - 0.5) * amt; d[i] += n; d[i + 1] += n; d[i + 2] += n; }
  ctx.putImageData(img, 0, 0);
}
const paintTex = canvasTex(256, 256, (c, w, h) => noiseFill(c, w, h, "#cbc8c3", 6), [1.2, 1.2]);
const ceilTex = canvasTex(256, 256, (c, w, h) => noiseFill(c, w, h, "#f3f2ef", 4), [1.5, 1.5]);
const floorTex = canvasTex(512, 512, (c, w, h) => {
  noiseFill(c, w, h, "#8a8b89", 10, 11);
  const r = rnd(5);
  for (let i = 0; i < 70; i++) {
    const x = r() * w, y = r() * h, rad = 20 + r() * 70;
    const g = c.createRadialGradient(x, y, 0, x, y, rad);
    const a = 0.05 * r();
    g.addColorStop(0, r() > 0.5 ? `rgba(255,255,255,${a})` : `rgba(0,0,0,${a})`); g.addColorStop(1, "rgba(0,0,0,0)");
    c.fillStyle = g; c.fillRect(x - rad, y - rad, rad * 2, rad * 2);
  }
}, [2.5, 2.5]);

function grain(c, w, h, base, dark, seed, lines = 120, vertical = true) {
  c.fillStyle = base; c.fillRect(0, 0, w, h);
  const r = rnd(seed);
  for (let i = 0; i < lines; i++) {
    const p = r() * (vertical ? w : h), amp = 2 + r() * 6, freq = 0.004 + r() * 0.01, ph = r() * 6;
    c.strokeStyle = dark; c.globalAlpha = 0.06 + r() * 0.16; c.lineWidth = 0.6 + r() * 1.8;
    c.beginPath();
    for (let t = 0; t <= (vertical ? h : w); t += 6) {
      const o = Math.sin(t * freq + ph) * amp;
      vertical ? (t ? c.lineTo(p + o, t) : c.moveTo(p + o, t)) : (t ? c.lineTo(t, p + o) : c.moveTo(t, p + o));
    }
    c.stroke();
  }
  c.globalAlpha = 1;
}
const oakTex = canvasTex(512, 1024, (c, w, h) => grain(c, w, h, "#c79e63", "#7a5428", 3, 140), [0.6, 1.2]);
const oakDoorTex = canvasTex(512, 1024, (c, w, h) => grain(c, w, h, "#c9a26a", "#7f5a2e", 9, 160), [0.8, 2.0]);
const slatTex = canvasTex(256, 1024, (c, w, h) => grain(c, w, h, "#c9a46e", "#86602f", 21, 60), [0.2, 1.5]);
const deckTex = canvasTex(1024, 1024, (c, w, h) => {
  // weathered grooved decking; boards run away from the facade (along x), 0.145 m pitch
  c.fillStyle = "#3f3a33"; c.fillRect(0, 0, w, h);
  const r = rnd(41), boards = 7, bh = h / boards;
  for (let b = 0; b < boards; b++) {
    const y0 = b * bh, tone = 52 + r() * 22;
    c.fillStyle = `rgb(${tone + 8},${tone + 2},${tone - 6})`; c.fillRect(0, y0 + 3, w, bh - 6);
    for (let g = 1; g < 9; g++) { c.fillStyle = "rgba(0,0,0,0.18)"; c.fillRect(0, y0 + 3 + g * (bh - 6) / 9, w, 2); }
    for (let i = 0; i < 40; i++) {
      c.strokeStyle = r() > 0.5 ? "rgba(120,110,95,0.18)" : "rgba(15,12,10,0.22)"; c.lineWidth = 1 + r() * 2;
      const yy = y0 + 4 + r() * (bh - 8); c.beginPath(); c.moveTo(0, yy);
      for (let x = 0; x <= w; x += 32) c.lineTo(x, yy + Math.sin(x * 0.01 + i) * 1.5); c.stroke();
    }
    const joint = r() * w; c.fillStyle = "#1e1b18"; c.fillRect(joint, y0, 3, bh);
  }
  c.fillStyle = "#1a1714"; for (let b = 0; b <= boards; b++) c.fillRect(0, b * bh - 2, w, 5);
}, [1.015, 1.015]);
const renderTex = canvasTex(256, 256, (c, w, h) => noiseFill(c, w, h, "#d2d3d1", 9, 23), [1.0, 1.0]);
const bathFloorTex = canvasTex(1024, 1024, (c, w, h) => {
  grain(c, w, h, "#cdb48e", "#8f6f45", 33, 220, false);
  c.strokeStyle = "rgba(90,65,35,0.35)"; c.lineWidth = 2;
  for (let y = 0; y <= h; y += h / 6) { c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke(); }
  const r = rnd(4);
  for (let row = 0; row < 6; row++) { const x = r() * w; c.beginPath(); c.moveTo(x, row * h / 6); c.lineTo(x, (row + 1) * h / 6); c.stroke(); }
}, [1.2, 1.2]);
function tiles(c, w, h, cols, rows, base, grout) {
  noiseFill(c, w, h, base, 3, 2);
  c.strokeStyle = grout; c.lineWidth = 3;
  for (let i = 0; i <= cols; i++) { const x = i * w / cols; c.beginPath(); c.moveTo(x, 0); c.lineTo(x, h); c.stroke(); }
  for (let j = 0; j <= rows; j++) { const y = j * h / rows; c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke(); }
}
const stoveTex = canvasTex(512, 512, (c, w, h) => tiles(c, w, h, 2, 3, "#f4f4f1", "#d9d8d3"), [0.5, 0.5]);
const showerTex = canvasTex(512, 512, (c, w, h) => tiles(c, w, h, 2, 1, "#f5f5f3", "#d6d6d2"), [0.6, 0.6]);
const curtainTex = canvasTex(256, 256, (c, w, h) => {
  noiseFill(c, w, h, "#3e3f43", 14, 17);
  c.globalAlpha = 0.08; c.fillStyle = "#000";
  for (let y = 0; y < h; y += 3) c.fillRect(0, y, w, 1);
  c.globalAlpha = 1;
}, null);
curtainTex.repeat.set(6, 10);
const laceTex = canvasTex(256, 256, (c, w, h) => {
  c.clearRect(0, 0, w, h);
  c.fillStyle = "rgba(255,255,255,0.55)"; c.fillRect(0, 0, w, h);
  c.globalCompositeOperation = "destination-out";
  for (let y = 16; y < h; y += 32) for (let x = 16; x < w; x += 32) {
    c.beginPath(); c.arc(x + ((y / 32) % 2) * 16, y, 9, 0, 7); c.fill();
  }
  c.globalCompositeOperation = "source-over";
  c.strokeStyle = "rgba(255,255,255,0.9)"; c.lineWidth = 1.5;
  for (let y = 16; y < h; y += 32) for (let x = 16; x < w; x += 32) { c.beginPath(); c.arc(x + ((y / 32) % 2) * 16, y, 9, 0, 7); c.stroke(); }
}, null);
laceTex.repeat.set(3, 2);

/* ---------- materials ---------- */
const M = {
  paint: new THREE.MeshStandardMaterial({ map: paintTex, roughness: 0.92, envMapIntensity: 0.35 }),
  cap: new THREE.MeshStandardMaterial({ color: 0x55534f, roughness: 0.9 }),
  ceil: new THREE.MeshStandardMaterial({ map: ceilTex, roughness: 0.95, side: THREE.DoubleSide, envMapIntensity: 0.3 }),
  floor: new THREE.MeshStandardMaterial({ map: floorTex, roughness: 0.32, metalness: 0, envMapIntensity: 0.55 }),
  bathFloor: new THREE.MeshStandardMaterial({ map: bathFloorTex, roughness: 0.5, envMapIntensity: 0.4 }),
  oak: new THREE.MeshStandardMaterial({ map: oakTex, roughness: 0.55, envMapIntensity: 0.4 }),
  oakDoor: new THREE.MeshStandardMaterial({ map: oakDoorTex, roughness: 0.5, envMapIntensity: 0.4 }),
  slat: new THREE.MeshStandardMaterial({ map: slatTex, roughness: 0.6, envMapIntensity: 0.35 }),
  black: new THREE.MeshStandardMaterial({ color: 0x1b1b1b, roughness: 0.7 }),
  metalBlack: new THREE.MeshStandardMaterial({ color: 0x151515, roughness: 0.35, metalness: 0.6 }),
  tileStove: new THREE.MeshStandardMaterial({ map: stoveTex, roughness: 0.12, envMapIntensity: 0.9 }),
  tileShower: new THREE.MeshStandardMaterial({ map: showerTex, roughness: 0.15, envMapIntensity: 0.8 }),
  white: new THREE.MeshStandardMaterial({ color: 0xf1f1ee, roughness: 0.55, envMapIntensity: 0.4 }),
  skirting: new THREE.MeshStandardMaterial({ color: 0xf2f1ee, roughness: 0.5 }),
  pvc: new THREE.MeshStandardMaterial({ color: 0xf3f3f1, roughness: 0.35, envMapIntensity: 0.5 }),
  glass: new THREE.MeshPhysicalMaterial({ color: 0xdfeef0, roughness: 0.03, metalness: 0, transparent: true, opacity: 0.18, envMapIntensity: 1.2, depthWrite: false }),
  curtain: new THREE.MeshStandardMaterial({ map: curtainTex, roughness: 0.95, side: THREE.DoubleSide }),
  lace: new THREE.MeshStandardMaterial({ map: laceTex, transparent: true, side: THREE.DoubleSide, roughness: 0.9, depthWrite: false }),
  counter: new THREE.MeshStandardMaterial({ color: 0x2f3032, roughness: 0.45, envMapIntensity: 0.6 }),
  splash: new THREE.MeshStandardMaterial({ color: 0x48494c, roughness: 0.6 }),
  front: new THREE.MeshStandardMaterial({ color: 0xf2f2f0, roughness: 0.5, envMapIntensity: 0.45 }),
  plinth: new THREE.MeshStandardMaterial({ color: 0x5b5c5e, roughness: 0.6 }),
  seam: new THREE.MeshStandardMaterial({ color: 0xbebdb8, roughness: 0.8 }),
  blackGlass: new THREE.MeshStandardMaterial({ color: 0x0d0d0e, roughness: 0.08, metalness: 0.2, envMapIntensity: 1 }),
  steel: new THREE.MeshStandardMaterial({ color: 0xbfc2c4, roughness: 0.25, metalness: 0.9 }),
  led: new THREE.MeshStandardMaterial({ color: 0xffe8c4, emissive: 0xffd9a0, emissiveIntensity: 2.2 }),
  lamp: new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xfff4e2, emissiveIntensity: 1.4 }),
  porcelain: new THREE.MeshStandardMaterial({ color: 0xfbfbfa, roughness: 0.15, envMapIntensity: 0.8 }),
  mirror: new THREE.MeshStandardMaterial({ color: 0xdfe3e4, roughness: 0.03, metalness: 1, envMapIntensity: 1.2 }),
  panel: new THREE.MeshStandardMaterial({ color: 0xf0f0ee, roughness: 0.5 }),
  panelLine: new THREE.LineBasicMaterial({ color: 0xa9a8a3 }),
  plinthBase: new THREE.MeshStandardMaterial({ color: 0x9a9792, roughness: 0.95 }),
  ground: new THREE.MeshStandardMaterial({ color: 0xb3b5a8, roughness: 1 }),
  paving: new THREE.MeshStandardMaterial({ color: 0xa9a6a0, roughness: 0.95 }),
  hedge: new THREE.MeshStandardMaterial({ color: 0x6f8457, roughness: 1, flatShading: true }),
  neighbour: new THREE.MeshStandardMaterial({ color: 0xd9d3c6, roughness: 1 }),
  deck: new THREE.MeshStandardMaterial({ map: deckTex, roughness: 0.85, envMapIntensity: 0.3 }),
  render: new THREE.MeshStandardMaterial({ map: renderTex, roughness: 0.95, envMapIntensity: 0.3 }),
  frosted: new THREE.MeshPhysicalMaterial({ color: 0xe3ece9, roughness: 0.55, transparent: true, opacity: 0.62, envMapIntensity: 0.6, side: THREE.DoubleSide, depthWrite: false }),
  alu: new THREE.MeshStandardMaterial({ color: 0xc8cbcd, roughness: 0.35, metalness: 0.7 }),
  anthracite: new THREE.MeshStandardMaterial({ color: 0x2a2b2d, roughness: 0.45 }),
  planter: new THREE.MeshStandardMaterial({ color: 0x5f6164, roughness: 0.75, envMapIntensity: 0.4 }),
  soil: new THREE.MeshStandardMaterial({ color: 0x2e2620, roughness: 1 }),
  acacia: new THREE.MeshStandardMaterial({ map: oakTex, color: 0x9a7f66, roughness: 0.7, envMapIntensity: 0.3 }),
  blackWood: new THREE.MeshStandardMaterial({ color: 0x1c1b1a, roughness: 0.45, envMapIntensity: 0.6 }),
  rattan: new THREE.MeshStandardMaterial({ map: canvasTex(256, 256, (c, w, h) => {
    c.fillStyle = "#3a2c1e"; c.fillRect(0, 0, w, h);
    c.strokeStyle = "#d8b98a"; c.lineWidth = 5;
    for (let k = -h; k < w + h; k += 16) {
      c.beginPath(); c.moveTo(k, 0); c.lineTo(k + h, h); c.stroke();
      c.beginPath(); c.moveTo(k, h); c.lineTo(k + h, 0); c.stroke();
    }
    c.lineWidth = 3; for (let k = 0; k < w; k += 16) { c.beginPath(); c.moveTo(k, 0); c.lineTo(k, h); c.stroke(); }
  }, [0.12, 0.12]), roughness: 0.8 }),
  knit: new THREE.MeshStandardMaterial({ map: canvasTex(256, 256, (c, w, h) => {
    noiseFill(c, w, h, "#8b8d8f", 12, 5);
    c.strokeStyle = "rgba(40,40,40,0.25)"; c.lineWidth = 2;
    for (let x = 0; x < w; x += 16) for (let y = 0; y < h; y += 12) { c.beginPath(); c.moveTo(x, y); c.lineTo(x + 8, y + 12); c.lineTo(x + 16, y); c.stroke(); }
  }, null), roughness: 1 }),
  sofa: new THREE.MeshStandardMaterial({ map: canvasTex(256, 256, (c, w, h) => noiseFill(c, w, h, "#a8a6a1", 26, 13), [0.15, 0.15]), roughness: 1, envMapIntensity: 0.25 }),
  piping: new THREE.MeshStandardMaterial({ color: 0xe9e3d6, roughness: 0.8 }),
  rugEdge: new THREE.MeshStandardMaterial({ color: 0xd98f45, roughness: 1 }),
  radiator: new THREE.MeshStandardMaterial({ color: 0xf4f4f2, roughness: 0.4, envMapIntensity: 0.5 }),
  grille: new THREE.MeshStandardMaterial({ color: 0x9c9c98, roughness: 0.6 }),
  tvFrame: new THREE.MeshStandardMaterial({ color: 0x2b2c2e, roughness: 0.4, metalness: 0.4 }),
  tvScreen: new THREE.MeshStandardMaterial({ color: 0x050607, roughness: 0.06, metalness: 0.1, envMapIntensity: 1.2 }),
  orangeGlow: new THREE.MeshStandardMaterial({ color: 0xff4d12, emissive: 0xff3c00, emissiveIntensity: 1.1, roughness: 0.4, toneMapped: false }),
  shelfWhite: new THREE.MeshStandardMaterial({ color: 0xf2f2f0, roughness: 0.35, metalness: 0.3, envMapIntensity: 0.5 }),
  plastic: new THREE.MeshStandardMaterial({ color: 0xf6f6f4, roughness: 0.35, envMapIntensity: 0.6 }),
  zinc: new THREE.MeshStandardMaterial({ color: 0xa9adb0, roughness: 0.4, metalness: 0.8 }),
};

/* ---------- geometry helpers ---------- */
function worldUV(geo) {
  const p = geo.attributes.position, n = geo.attributes.normal, uv = geo.attributes.uv;
  for (let i = 0; i < p.count; i++) {
    const ax = Math.abs(n.getX(i)), ay = Math.abs(n.getY(i)), az = Math.abs(n.getZ(i));
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    if (ay >= ax && ay >= az) uv.setXY(i, x, z);
    else if (ax >= az) uv.setXY(i, z, y);
    else uv.setXY(i, x, y);
  }
  uv.needsUpdate = true;
  return geo;
}
let root = new THREE.Group(); scene.add(root);
let fixtures, ceiling;

function box(x1, x2, y1, y2, z1, z2, mat, opts = {}) {
  const g = new THREE.BoxGeometry(Math.abs(x2 - x1), Math.abs(y2 - y1), Math.abs(z2 - z1))
    .translate((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
  worldUV(g);
  const m = new THREE.Mesh(g, mat);
  m.castShadow = opts.cast !== false; m.receiveShadow = true;
  (opts.parent || root).add(m);
  return m;
}
function flat(x1, x2, z1, z2, y, mat, down = false, parent = root) {
  const g = new THREE.PlaneGeometry(x2 - x1, z2 - z1).rotateX(down ? Math.PI / 2 : -Math.PI / 2).translate((x1 + x2) / 2, y, (z1 + z2) / 2);
  worldUV(g);
  const m = new THREE.Mesh(g, mat); m.receiveShadow = true; parent.add(m); return m;
}

/* wall: axis-aligned block with rectangular openings.
   along: "x" or "z" = direction the wall runs. openings: [a, b, yBottom, yTop] in along-coordinates */
function wall(x1, x2, z1, z2, H, openings = []) {
  const along = (x2 - x1) >= (z2 - z1) ? "x" : "z";
  const [s, e] = along === "x" ? [x1, x2] : [z1, z2];
  const piece = (a, b, y1, y2) => {
    if (b - a < 1e-4 || y2 - y1 < 1e-4) return;
    if (along === "x") box(a, b, y1, y2, z1, z2, M.paint); else box(x1, x2, y1, y2, a, b, M.paint);
    if (Math.abs(y2 - H) < 1e-6) {
      if (along === "x") box(a, b, H, H + 0.012, z1, z2, M.cap, { cast: false }); else box(x1, x2, H, H + 0.012, a, b, M.cap, { cast: false });
    }
  };
  const ops = [...openings].sort((p, q) => p[0] - q[0]);
  let cur = s;
  for (const [a, b, y0, y1] of ops) {
    piece(cur, a, 0, H);
    piece(a, b, 0, y0);
    piece(a, b, y1, H);
    cur = b;
  }
  piece(cur, e, 0, H);
}

/* skirting along an interior face; dir = "x" | "z"; n = inward sign */
function skirt(along, fixed, a, b, n, gaps = []) {
  const ops = [...gaps].sort((p, q) => p[0] - q[0]);
  let cur = a; const segs = [];
  for (const [g0, g1] of ops) { segs.push([cur, g0]); cur = g1; }
  segs.push([cur, b]);
  for (const [s, e] of segs) {
    if (e - s < 0.02) continue;
    if (along === "x") box(s, e, 0, 0.07, fixed, fixed + n * 0.014, M.skirting, { cast: false });
    else box(fixed, fixed + n * 0.014, 0, 0.07, s, e, M.skirting, { cast: false });
  }
}

/* oak lining + architraves around a door opening in a wall perpendicular to `axis` */
function doorFrame(axis, f1, f2, a, b, top, casingOut = [0.015, 0.015]) {
  const L = 0.03, W = 0.07;
  if (axis === "x") { // wall spans x f1..f2 (thickness), opening along z a..b
    box(f1, f2, 0, top, a, a + L, M.oak); box(f1, f2, 0, top, b - L, b, M.oak); box(f1, f2, top - L, top, a, b, M.oak);
    [[f1, -casingOut[0]], [f2, casingOut[1]]].forEach(([f, d]) => {
      box(f, f + d, 0, top + W, a - W, a, M.oak); box(f, f + d, 0, top + W, b, b + W, M.oak); box(f, f + d, top, top + W, a - W, b + W, M.oak);
    });
  } else {
    box(a, a + L, 0, top, f1, f2, M.oak); box(b - L, b, 0, top, f1, f2, M.oak); box(a, b, top - L, top, f1, f2, M.oak);
    [[f1, -casingOut[0]], [f2, casingOut[1]]].forEach(([f, d]) => {
      box(a - W, a, 0, top + W, f, f + d, M.oak); box(b, b + W, 0, top + W, f, f + d, M.oak); box(a - W, b + W, top, top + W, f, f + d, M.oak);
    });
  }
}
/* door leaf hinged at (hx,hz); theta = yaw so local +x of the leaf points along (cos θ, -sin θ) */
function doorLeaf(hx, hz, width, height, theta, mat = M.oakDoor) {
  const g = new THREE.Group();
  const t = 0.04;
  const leaf = new THREE.Mesh(new THREE.BoxGeometry(width, height, t).translate(width / 2, height / 2 + 0.008, 0), mat);
  leaf.castShadow = leaf.receiveShadow = true; g.add(leaf);
  const groove = new THREE.Mesh(new THREE.BoxGeometry(width - 0.002, 0.004, t + 0.002).translate(width / 2, height * 0.58, 0), new THREE.MeshStandardMaterial({ color: 0x8a6537, roughness: 0.7 }));
  g.add(groove);
  [-1, 1].forEach(sd => {
    const rose = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.18, 0.01).translate(width - 0.07, 1.0, sd * (t / 2 + 0.005)), M.metalBlack);
    const lever = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.018, 0.018).translate(width - 0.12, 1.04, sd * (t / 2 + 0.03)), M.metalBlack);
    lever.castShadow = true; g.add(rose, lever);
  });
  g.position.set(hx, 0, hz); g.rotation.y = theta;
  root.add(g); return g;
}

/* PVC window/door unit in a wall perpendicular to `axis`; plane at `pos`, span a..b, y0..y1; splits = transom heights */
function windowUnit(axis, pos, a, b, y0, y1, splits = [], mullions = []) {
  const F = 0.065, D = 0.07;
  const bar = (u1, u2, v1, v2, d = D) => axis === "x" ? box(pos - d / 2, pos + d / 2, v1, v2, u1, u2, M.pvc) : box(u1, u2, v1, v2, pos - d / 2, pos + d / 2, M.pvc);
  bar(a, a + F, y0, y1); bar(b - F, b, y0, y1); bar(a, b, y0, y0 + F); bar(a, b, y1 - F, y1);
  splits.forEach(s => bar(a + F, b - F, s - F / 2, s + F / 2));
  mullions.forEach(m => bar(m - F / 2, m + F / 2, y0 + F, y1 - F));
  const g = new THREE.PlaneGeometry(b - a - 2 * F, y1 - y0 - 2 * F);
  const glass = new THREE.Mesh(g, M.glass);
  if (axis === "x") { glass.rotation.y = Math.PI / 2; glass.position.set(pos, (y0 + y1) / 2, (a + b) / 2); }
  else glass.position.set((a + b) / 2, (y0 + y1) / 2, pos);
  glass.renderOrder = 2; root.add(glass);
}

function curtainPanel(x, z1, z2, yTop, folds, parent) {
  const w = z2 - z1, h = yTop - 0.02;
  const g = new THREE.PlaneGeometry(w, h, 64, 1);
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const u = (p.getX(i) + w / 2) / w;
    p.setZ(i, Math.sin(u * Math.PI * 2 * folds) * 0.045 + Math.sin(u * Math.PI * 2 * folds * 2.3) * 0.01);
  }
  g.computeVertexNormals();
  const m = new THREE.Mesh(g, M.curtain);
  m.rotation.y = Math.PI / 2;
  m.position.set(x, 0.02 + h / 2, (z1 + z2) / 2);
  m.castShadow = true; m.receiveShadow = true;
  parent.add(m);
}

function prism(points, y1, y2, mat, parent = root) {
  const sh = new THREE.Shape(points.map(([x, z]) => new THREE.Vector2(x, -z)));
  const g = new THREE.ExtrudeGeometry(sh, { depth: y2 - y1, bevelEnabled: false });
  g.rotateX(-Math.PI / 2).translate(0, y1, 0);
  g.computeVertexNormals(); worldUV(g);
  const m = new THREE.Mesh(g, mat); m.castShadow = m.receiveShadow = true; parent.add(m); return m;
}


/* ---------- the apartment ---------- */
/* Plan coordinates in metres: x → right along plan, z → down the plan, y up.
   Living 028-4: x 0–5.84, z 0–3.60 | Hall/kitchen 028-2: A x 6.29–11.74 z 1.20–2.80, B x 8.45–11.54 z 2.80–4.57, nooks either side of the entry
   Bathroom 028-3: x 6.85–8.35, z 2.90–5.15, widening at the lower left (0.84+0.32, then 0.58 step, then 0.15 step) | Entry 028-1: x 11.20–12.30, z 1.76–3.24 */
const lightsGroup = new THREE.Group(); scene.add(lightsGroup);

function build(H) {
  scene.remove(root);
  root.traverse(o => { if (o.geometry) o.geometry.dispose(); });
  root = new THREE.Group(); scene.add(root);
  fixtures = new THREE.Group(); root.add(fixtures);
  ceiling = new THREE.Group(); root.add(ceiling);
  lightsGroup.clear();

  // floors
  const rooms = [[0, 5.84, 0, 3.6], [6.29, 11.74, 1.2, 2.8], [8.45, 11.54, 2.8, 4.57], [11.20, 12.30, 1.76, 3.24],
    [11.54, 11.74, 2.8, 3.84], [5.32, 6.29, 1.3, 2.1], [11.11, 11.20, 2.1, 2.9], [12.30, 12.45, 2.05, 2.95], [-0.15, 0, 1.25, 2.35]];
  rooms.forEach(r => flat(r[0], r[1], r[2], r[3], 0, M.floor));
  const bath = [[6.85, 8.35, 2.90, 4.06], [6.67, 8.35, 4.06, 4.64], [6.52, 8.35, 4.64, 5.15]];
  bath.forEach(r => flat(r[0], r[1], r[2], r[3], 0, M.bathFloor));
  flat(8.35, 8.45, 3.05, 3.80, 0, M.bathFloor);
  [...rooms.slice(0, 5), ...bath].forEach(r => flat(r[0], r[1], r[2], r[3], H, M.ceil, true, ceiling));

  // walls: exterior 0.15 m, partitions as on the plan
  wall(-0.15, 0, -0.15, 3.75, H, [[1.25, 2.35, 0, 2.45]]);        // living, terrace side
  wall(0, 5.99, -0.15, 0, H);                                     // living, top
  wall(0, 5.32, 3.6, 3.75, H);                                    // living, bottom
  wall(5.84, 5.99, 0, 0.63, H);                                   // back of the 0.52 × 0.63 nook
  wall(5.32, 5.99, 0.63, 1.2, H);                                 // door wall, beside the nook
  wall(5.32, 6.29, 1.2, 2.8, H, [[1.3, 2.1, 0, 2.05]]);           // living ↔ hall
  wall(5.32, 6.85, 2.8, 3.75, H);                                 // corner mass behind the stove
  wall(6.52, 6.85, 3.75, 4.06, H);                                // bathroom, left, upper 0.84 + 0.32
  wall(6.52, 6.67, 4.06, 4.64, H);                                // bathroom nook, 0.58 long, 0.18 deep
  wall(6.37, 6.52, 4.49, 5.30, H);                                // bathroom, left, lower part after the 0.15 step
  wall(5.84, 11.89, 1.05, 1.2, H);                                // hall/kitchen outer wall
  wall(6.70, 8.45, 2.8, 2.9, H);                                  // hall ↔ bathroom
  wall(8.35, 8.45, 2.9, 4.57, H, [[3.05, 3.8, 0, 2.05]]);         // bathroom door wall
  wall(8.35, 8.50, 4.57, 5.30, H);                                // bathroom, right (outer)
  wall(6.37, 8.35, 5.15, 5.30, H);                                // bathroom, bottom
  wall(8.50, 11.69, 4.57, 4.72, H);                               // hall, bottom
  wall(11.54, 11.69, 3.84, 4.72, H);                              // hall, right below the lower nook
  wall(11.54, 11.89, 3.84, 3.99, H);                              // lower nook, end
  wall(11.74, 11.89, 1.05, 1.76, H);                              // upper nook, back
  wall(11.74, 11.89, 3.24, 3.99, H);                              // lower nook, back
  wall(11.11, 12.45, 1.64, 1.76, H);                              // entry, top (upper nook ends here)
  wall(11.11, 12.45, 3.24, 3.36, H);                              // entry, bottom (lower nook starts here)
  wall(11.11, 11.20, 1.64, 3.36, H, [[2.1, 2.9, 0, 2.05]]);       // hall ↔ entry
  wall(12.30, 12.45, 1.64, 3.36, H, [[2.05, 2.95, 0, 2.1]]);      // entrance door

  // skirting
  skirt("z", 0, 0, 3.6, 1, [[1.25, 2.35]]);
  skirt("x", 0, 0, 5.84, 1);
  skirt("z", 5.84, 0, 0.63, -1);
  skirt("x", 0.63, 5.32, 5.84, -1);
  skirt("x", 3.6, 0, 4.38, -1);
  skirt("x", 1.2, 6.29, 11.74, 1);
  skirt("z", 11.74, 1.2, 1.64, -1);
  skirt("x", 1.64, 11.11, 11.74, -1);
  skirt("z", 11.11, 1.64, 3.36, -1, [[2.03, 2.97]]);
  skirt("x", 3.36, 11.11, 11.74, 1);
  skirt("z", 11.74, 3.36, 3.84, -1);
  skirt("x", 3.84, 11.54, 11.74, -1);
  skirt("z", 11.54, 3.84, 4.57, -1);
  skirt("z", 6.29, 1.2, 2.8, 1, [[1.23, 2.17]]);
  skirt("x", 2.8, 6.29, 8.45, -1);
  skirt("z", 8.45, 2.8, 4.57, 1, [[2.98, 3.87]]);
  skirt("x", 4.57, 8.45, 11.54, -1);
  skirt("z", 11.20, 1.76, 3.24, 1, [[2.03, 2.97]]);
  skirt("z", 12.30, 1.76, 3.24, -1, [[1.98, 3.02]]);
  skirt("x", 1.76, 11.20, 12.30, 1);

  // oak slat wall on the living-room side of the door wall
  const slatTop = H - 0.004;
  for (let z = 0.65; z < 2.62; z += 0.058) {
    const inDoor = z > 1.21 && z < 2.17;
    const y0 = inDoor ? 2.125 : 0;
    box(5.30, 5.315, y0, slatTop, z - 0.01, z + 0.048, M.black, { cast: false });
    box(5.27, 5.30, y0, slatTop, z, z + 0.036, M.slat);
  }

  // same oak slat finish on the entry wall to the left when coming in
  for (let x = 11.21; x < 12.27; x += 0.058) {
    box(x - 0.01, x + 0.048, 0, slatTop, 3.225, 3.24, M.black, { cast: false });
    box(x, x + 0.036, 0, slatTop, 3.195, 3.225, M.slat);
  }

  // doors
  doorFrame("x", 5.32, 6.29, 1.3, 2.1, 2.05, [0.06, 0.015]);
  doorLeaf(5.34, 2.07, 0.74, 2.0, Math.PI / 2 + 1.25);             // living-room door, open into the room
  doorFrame("x", 8.35, 8.45, 3.05, 3.8, 2.05);
  doorLeaf(8.47, 3.08, 0.69, 2.0, -Math.PI / 2 + 1.1);             // bathroom door, ajar
  doorFrame("x", 11.11, 11.20, 2.1, 2.9, 2.05);
  doorLeaf(11.22, 2.87, 0.74, 2.0, Math.PI / 2 - 1.2);             // entry door, open into the entry
  doorFrame("x", 12.30, 12.45, 2.05, 2.95, 2.1);
  doorLeaf(12.34, 2.92, 0.84, 2.05, Math.PI / 2);                  // entrance door, closed

  // glazed terrace door + curtains
  windowUnit("x", -0.075, 1.25, 2.35, 0, 2.45, [1.95], []);

  /* ----- living room, wall beside the terrace door (z = 0), measured from the photo ----- */
  // panel radiator, type-22 style, 1.40 × 0.60 m, 15 cm off the floor
  box(0.15, 1.55, 0.15, 0.75, 0.05, 0.15, M.radiator);
  for (let x = 0.17; x < 1.54; x += 0.025) box(x, x + 0.012, 0.17, 0.73, 0.15, 0.158, M.radiator, { cast: false });
  box(0.16, 1.54, 0.745, 0.752, 0.06, 0.14, M.grille, { cast: false });
  [0.3, 1.4].forEach(x => box(x - 0.02, x + 0.02, 0.6, 0.66, 0, 0.05, M.radiator, { cast: false }));   // wall brackets
  const valve = new THREE.Mesh(new THREE.CylinderGeometry(0.028, 0.028, 0.1, 20), M.radiator);
  valve.position.set(1.6, 0.68, 0.1); valve.castShadow = true; root.add(valve);
  // irregular ombré rug (outline traced from the product photo): a rectangle with a tilted
  // second panel behind it; about 1.90 × 2.02 m overall
  const RW = 1.9, RD = 2.02;
  const rugTex = canvasTex(800, 850, (c, w, h) => {
    c.clearRect(0, 0, w, h);
    const P = (pts) => { c.beginPath(); pts.forEach(([x, y], k) => k ? c.lineTo(x - 50, y - 20) : c.moveTo(x - 50, y - 20)); c.closePath(); };
    let g = c.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, "#e7b27a"); g.addColorStop(0.5, "#df9446"); g.addColorStop(1, "#cf7428");
    P([[405, 20], [850, 235], [545, 870], [300, 870], [300, 160]]); c.fillStyle = g; c.fill();
    g = c.createLinearGradient(0, 163, 0, h);
    g.addColorStop(0, "#ead2b0"); g.addColorStop(0.45, "#e3a660"); g.addColorStop(1, "#c8661f");
    P([[50, 183], [540, 173], [540, 870], [50, 870]]); c.fillStyle = g; c.fill();
    const img = c.getImageData(0, 0, w, h), d = img.data, r = rnd(77);
    for (let k = 0; k < d.length; k += 4) { if (!d[k + 3]) continue; const n = (r() - 0.5) * 14; d[k] += n; d[k + 1] += n; d[k + 2] += n; }
    c.putImageData(img, 0, 0);
  }, null);
  const rug = new THREE.Mesh(new THREE.PlaneGeometry(RW, RD).rotateX(-Math.PI / 2),
    new THREE.MeshStandardMaterial({ map: rugTex, roughness: 1, envMapIntensity: 0.2, alphaTest: 0.5 }));
  // turned so the rectangle's straight left edge runs along the TV wall (z = 0), under the radiator
  rug.rotation.y = -Math.PI / 2;
  rug.position.set(0.05 + RD / 2, 0.012, 0.04 + RW / 2); rug.receiveShadow = true; root.add(rug);

  // oval coffee table opposite the TV: 100.5 × 43.3 cm, 45.8 cm high, black wood, rattan lower shelf
  function stadium(len, wid, inset = 0) {
    const r = wid / 2 - inset, a = len / 2 - wid / 2, sh = new THREE.Shape();
    sh.moveTo(-a, -r); sh.lineTo(a, -r); sh.absarc(a, 0, r, -Math.PI / 2, Math.PI / 2, false);
    sh.lineTo(-a, r); sh.absarc(-a, 0, r, Math.PI / 2, Math.PI * 1.5, false); return sh;
  }
  function slab(shape, y0, y1, mat, parent, holes) {
    if (holes) shape.holes.push(holes);
    const g = new THREE.ExtrudeGeometry(shape, { depth: y1 - y0, bevelEnabled: false, curveSegments: 32 });
    g.rotateX(-Math.PI / 2).translate(0, y0, 0);
    const m = new THREE.Mesh(g, mat); m.castShadow = m.receiveShadow = true; parent.add(m); return m;
  }
  const ct = new THREE.Group(); ct.position.set(0.89, 0, 2.38); root.add(ct);
  slab(stadium(1.005, 0.433), 0.43, 0.445, M.blackWood, ct);                                         // top
  slab(stadium(1.005, 0.433), 0.445, 0.458, M.blackWood, ct, new THREE.Path(stadium(0.975, 0.403).getPoints(48))); // raised rim
  slab(stadium(0.98, 0.36), 0.105, 0.115, M.rattan, ct);                                             // rattan shelf
  slab(stadium(1.0, 0.38), 0.10, 0.125, M.blackWood, ct, new THREE.Path(stadium(0.97, 0.35).getPoints(48)));  // shelf frame
  [[-0.32, -0.12], [0.32, -0.12], [-0.32, 0.12], [0.32, 0.12]].forEach(([x, z]) => {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.015, 0.43, 16), M.blackWood);
    leg.position.set(x, 0.215, z); leg.castShadow = true; ct.add(leg);
  });

  // SANDARED pouffe, grey, Ø56 cm × 41 cm, beside the coffee table
  const pf = new THREE.LatheGeometry([[0, 0], [0.2, 0], [0.255, 0.04], [0.28, 0.13], [0.275, 0.25], [0.24, 0.35], [0.16, 0.4], [0, 0.41]].map(([x, y]) => new THREE.Vector2(x, y)), 48);
  const pouffe = new THREE.Mesh(pf, M.knit); pouffe.position.set(1.85, 0.012, 2.4); pouffe.castShadow = pouffe.receiveShadow = true; root.add(pouffe);

  // two-seat sofa bed against the opposite wall, ~188 × 90 cm, light grey melange with cream piping
  const sofa = new THREE.Group(); sofa.position.set(1.14, 0, 3.58); root.add(sofa);   // local -z faces the TV
  const S = (w, h, d, r, mat, x, y, z, rx = 0, ry = 0, rz = 0) => { const m = rb(w, h, d, r, mat, x, y, z, sofa); m.rotation.set(rx, ry, rz); return m; };
  const pipe2 = (x0, x1, y, z) => S(x1 - x0, 0.012, 0.012, 0.005, M.piping, (x0 + x1) / 2, y, z);
  S(1.76, 0.30, 0.82, 0.04, M.sofa, 0, 0.21, -0.43);                       // base / bed box
  [-0.9, 0.9].forEach(x => {                                                  // slim arms
    S(0.08, 0.56, 0.86, 0.035, M.sofa, x, 0.33, -0.45);
    S(0.09, 0.012, 0.87, 0.005, M.piping, x, 0.61, -0.45);
  });
  [-0.43, 0.43].forEach(x => {
    S(0.85, 0.13, 0.64, 0.05, M.sofa, x, 0.425, -0.5);                       // seat cushions
    pipe2(x - 0.42, x + 0.42, 0.49, -0.82);
    S(0.85, 0.58, 0.2, 0.07, M.sofa, x, 0.78, -0.15, -0.12);                 // back cushions, leaning back
    S(0.012, 0.56, 0.012, 0.005, M.piping, x + (x < 0 ? 0.425 : -0.425), 0.78, -0.25, -0.12);
  });
  [[-0.82, -0.08], [0.82, -0.08], [-0.82, -0.8], [0.82, -0.8]].forEach(([x, z]) => S(0.05, 0.06, 0.05, 0.01, M.black, x, 0.03, z));

  // wall-mounted 50" TV above it
  box(0.33, 1.44, 1.03, 1.68, 0.02, 0.055, M.tvFrame);
  box(0.345, 1.425, 1.045, 1.665, 0.055, 0.057, M.tvScreen, { cast: false });
  // orange LED floor lamp: slim glowing tube on a white disc base
  const lampBase = new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.14, 0.02, 32), M.plastic);
  lampBase.position.set(1.66, 0.01, 0.3); lampBase.castShadow = true; root.add(lampBase);
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.12, 12), M.plastic);
  stem.position.set(1.66, 0.08, 0.3); root.add(stem);
  const tube = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.022, 1.36, 20), M.orangeGlow);
  tube.position.set(1.66, 0.82, 0.3); root.add(tube);
  const glow = new THREE.PointLight(0xff6a2a, 1.6, 2.2, 2); glow.position.set(1.66, 1.0, 0.36); lightsGroup.add(glow);
  // white metal shelving unit, 0.80 × 0.40 × 2.00 m, seven shelves
  const SX0 = 1.78, SX1 = 2.58, SZ0 = 0.02, SZ1 = 0.42;
  [[SX0, SZ0], [SX1, SZ0], [SX0, SZ1], [SX1, SZ1]].forEach(([x, z]) => box(x - 0.012, x + 0.012, 0, 2.0, z - 0.012, z + 0.012, M.shelfWhite));
  [0.06, 0.43, 0.79, 1.13, 1.45, 1.76, 1.99].forEach(y => {
    box(SX0, SX1, y - 0.012, y, SZ0, SZ1, M.shelfWhite);
    box(SX0, SX1, y - 0.035, y - 0.012, SZ1 - 0.012, SZ1, M.shelfWhite, { cast: false });  // front lip
  });
  [[SX0, 0.9], [SX1, 0.9]].forEach(([x]) => box(x - 0.004, x + 0.004, 0.06, 1.99, SZ0 + 0.02, SZ0 + 0.03, M.shelfWhite, { cast: false }));

  /* ----- outdoor terrace: 3.6 m out from the facade × 4.0 m along it ----- */
  const TZ0 = 1.05, TZ1 = 5.05, TX = -3.75;   // starts 0.20 m past the door's right-hand jamb (seen from inside)
  box(TX, -0.15, -0.15, -0.03, TZ0, TZ1, M.deck);                              // decking
  box(TX - 0.02, TX, -0.17, -0.02, TZ0, TZ1, M.deck);                          // front edge board
  // facade: light-grey render on the outside face, anthracite surround to the door
  box(-0.17, -0.15, -0.03, H, -0.15, 1.2, M.render, { cast: false });
  box(-0.17, -0.15, -0.03, H, 2.4, 3.75, M.render, { cast: false });
  box(-0.17, 0, -0.03, H, 3.75, TZ1 + 0.3, M.render);             // facade continues past the flat
  box(-0.17, -0.15, 2.5, H, 1.2, 2.4, M.render, { cast: false });
  box(-0.19, -0.15, -0.03, 2.5, 1.2, 1.25, M.anthracite);
  box(-0.19, -0.15, -0.03, 2.5, 2.35, 2.4, M.anthracite);
  box(-0.19, -0.15, 2.45, 2.5, 1.2, 2.4, M.anthracite);
  /* ----- terrace furniture (deck top at y = -0.03) ----- */
  const DY = -0.03;
  function rb(w, h, d, r, mat, x, y, z, parent) {
    const m = new THREE.Mesh(new RoundedBoxGeometry(w, h, d, 3, r), mat);
    m.position.set(x, y, z); m.castShadow = m.receiveShadow = true; parent.add(m); return m;
  }
  // ÄSPAREN planters, 89 × 38 × 40 cm, evenly spaced along the far edge
  const TL = TZ1 - TZ0;
  [1, 3, 5].forEach(k => {
    const g = new THREE.Group(), zc = TZ0 + TL * k / 6;
    g.position.set(TX + 0.27, DY, zc); root.add(g);
    rb(0.38, 0.30, 0.89, 0.05, M.planter, 0, 0.25, 0, g);              // tub
    [[-0.13, -0.38], [0.13, -0.38], [-0.13, 0.38], [0.13, 0.38]].forEach(([x, z]) => rb(0.1, 0.12, 0.11, 0.03, M.planter, x, 0.06, z, g));
    rb(0.34, 0.02, 0.83, 0.008, M.soil, 0, 0.375, 0, g);              // soil line
  });
  const tx = (TX + -0.15) / 2, tz = 3.35;   // point the chairs look towards
  // SKARPÖ armchairs, 81 × 79 × 71 cm, side by side right of the door, backs to the wall
  function skarpo(x, z) {
    const g = new THREE.Group(); g.position.set(x, DY, z);
    g.rotation.y = Math.atan2(-(tz - z), tx - x); root.add(g);
    const sh = new THREE.Shape();
    sh.moveTo(0.36, 0.405); sh.lineTo(0, 0.405); sh.absarc(0, 0, 0.405, Math.PI / 2, Math.PI * 1.5, false);
    sh.lineTo(0.36, -0.405); sh.lineTo(0.36, -0.335); sh.lineTo(0, -0.335);
    sh.absarc(0, 0, 0.335, Math.PI * 1.5, Math.PI / 2, true); sh.lineTo(0.36, 0.335); sh.closePath();
    const shell = new THREE.ExtrudeGeometry(sh, { depth: 0.47, bevelEnabled: true, bevelThickness: 0.025, bevelSize: 0.02, bevelSegments: 3, curveSegments: 24 });
    shell.rotateX(-Math.PI / 2).translate(0, 0.2, 0);
    const m = new THREE.Mesh(shell, M.plastic); m.castShadow = m.receiveShadow = true; g.add(m);
    rb(0.72, 0.1, 0.7, 0.04, M.plastic, 0.01, 0.33, 0, g).rotation.y = Math.PI / 2;
    rb(0.7, 0.1, 0.7, 0.04, M.plastic, 0.01, 0.33, 0, g);
    [[-0.27, -0.29], [0.27, -0.29], [-0.27, 0.29], [0.27, 0.29]].forEach(([lx, lz]) => rb(0.09, 0.3, 0.09, 0.03, M.plastic, lx, 0.15, lz, g));
  }
  skarpo(-0.62, 2.85);
  skarpo(-0.62, 3.85);

  // downpipe at the corner of the facade
  const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, H + 0.15, 16), M.zinc);
  pipe.position.set(-0.24, (H + 0.15) / 2 - 0.15, TZ0 - 0.15); pipe.castShadow = true; root.add(pipe);

  box(-0.04, -0.01, 1.08, 1.2, 2.18, 2.22, M.steel, { cast: false });
  const rodY = H - 0.14;
  const rod = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 3.58, 12).rotateX(Math.PI / 2), M.metalBlack);
  rod.position.set(0.1, rodY, 1.8); root.add(rod);
  [0.25, 1.8, 3.35].forEach(z => box(0, 0.1, rodY - 0.012, rodY + 0.012, z - 0.005, z + 0.005, M.metalBlack, { cast: false }));
  // wall-to-wall: each panel runs from a side wall to just over the door frame
  curtainPanel(0.13, 0.015, 1.4, rodY + 0.02, 10, root);
  curtainPanel(0.13, 2.2, 3.585, rodY + 0.02, 10, root);

  // white tiled stove in the corner (chamfered face)
  // plan: 0.20 return off the 4.38 wall, 1.04 chamfer, 0.20 return to the door wall
  const st = [[4.38, 3.6], [4.38, 3.4], [5.115, 2.665], [5.32, 2.665], [5.32, 3.6]];
  const stO = [[4.35, 3.6], [4.35, 3.39], [5.103, 2.635], [5.32, 2.635], [5.32, 3.6]];
  prism(stO, 0, 0.12, M.tileStove);
  prism(st, 0.12, H - 0.14, M.tileStove);
  prism(stO, H - 0.14, H, M.tileStove);                          // cornice band meets the ceiling


  // shower tiles (finish)
  box(8.335, 8.35, 0, 2.1, 4.35, 5.15, M.tileShower, { cast: false });
  box(7.55, 8.35, 0, 2.1, 5.135, 5.15, M.tileShower, { cast: false });

  // ceiling lights (flush discs)
  [[2.9, 1.8], [8.9, 2.0], [10.1, 3.7], [7.5, 4.0], [11.75, 2.5]].forEach(([x, z], i) => {
    const d = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.14, 0.03, 32), M.lamp);
    d.position.set(x, H - 0.015, z); ceiling.add(d);
    const L = new THREE.PointLight(0xffe7c8, i === 0 ? 6 : 3.5, 7, 2);
    L.position.set(x, H - 0.25, z); lightsGroup.add(L);
  });

  applyToggles();
}


// The light only moves when the sun does (once a minute), so shadows are drawn on demand.
renderer.shadowMap.autoUpdate = false;
build(3.0);

/* ---------- outdoor weather ambiance: Riga, Brīvības iela 95 ----------
   Sun position is computed live from the viewer's clock for Riga (56.958 N, 24.124 E),
   with the plan's top taken as north. Conditions come from the TV launcher, which fetches
   them from Open-Meteo and calls window.setWeather(); the snapshot below is used until then. */
const RIGA = { lat: 56.958, lon: 24.124 };
let weather = { code: "cloudy", tempC: 10, windKmh: 22, windFrom: "S", summary: "Mostly cloudy", observedAt: "2026-10-24T15:52:00+03:00" };
const WX = {
  clear: { cloud: 0.05 }, partly: { cloud: 0.4 }, cloudy: { cloud: 0.75 }, overcast: { cloud: 1 },
  fog: { cloud: 0.9, fog: 1 }, drizzle: { cloud: 0.95, rain: 0.25 }, rain: { cloud: 0.95, rain: 0.6 },
  heavy_rain: { cloud: 1, rain: 1 }, snow: { cloud: 0.95, snow: 0.7 }, sleet: { cloud: 0.95, rain: 0.35, snow: 0.35 },
  thunder: { cloud: 1, rain: 1, thunder: 1 },
};
const DIRS = { N: 0, NNE: 22.5, NE: 45, ENE: 67.5, E: 90, ESE: 112.5, SE: 135, SSE: 157.5, S: 180, SSW: 202.5, SW: 225, WSW: 247.5, W: 270, WNW: 292.5, NW: 315, NNW: 337.5 };

function sunPos(date) {
  const rad = Math.PI / 180, d = date.getTime() / 86400000 - 10957.5;
  const g = (357.529 + 0.98560028 * d) * rad, q = 280.459 + 0.98564736 * d;
  const L = (q + 1.915 * Math.sin(g) + 0.02 * Math.sin(2 * g)) * rad, e = (23.439 - 0.00000036 * d) * rad;
  const ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)), dec = Math.asin(Math.sin(e) * Math.sin(L));
  const gmst = ((18.697374558 + 24.06570982441908 * d) % 24 + 24) % 24;
  const H = (gmst * 15 + RIGA.lon) * rad - ra, la = RIGA.lat * rad;
  const alt = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(H));
  const az = Math.atan2(-Math.sin(H), Math.tan(dec) * Math.cos(la) - Math.sin(la) * Math.cos(H));
  return { alt, dir: new THREE.Vector3(Math.cos(alt) * Math.sin(az), Math.sin(alt), -Math.cos(alt) * Math.cos(az)) };
}

// sky dome
const SKY_C = new THREE.Vector3(5, 0, 2.5);
const skyMat = new THREE.ShaderMaterial({
  uniforms: { top: { value: new THREE.Color() }, horizon: { value: new THREE.Color() }, ground: { value: new THREE.Color(0x3c3f3a) },
    sunDir: { value: new THREE.Vector3(0, 1, 0) }, sunVis: { value: 0 }, center: { value: SKY_C } },
  vertexShader: "varying vec3 vW; void main(){ vec4 w = modelMatrix * vec4(position,1.0); vW = w.xyz; gl_Position = projectionMatrix * viewMatrix * w; }",
  fragmentShader: `uniform vec3 top; uniform vec3 horizon; uniform vec3 ground; uniform vec3 sunDir; uniform float sunVis; uniform vec3 center; varying vec3 vW;
    void main(){ vec3 d = normalize(vW - center); float h = d.y;
      vec3 c = h > 0.0 ? mix(horizon, top, pow(clamp(h, 0.0, 1.0), 0.55)) : mix(horizon, ground, clamp(-h * 6.0, 0.0, 1.0));
      float s = max(dot(d, normalize(sunDir)), 0.0);
      c += sunVis * (vec3(1.0, 0.92, 0.75) * pow(s, 600.0) * 2.0 + vec3(1.0, 0.8, 0.55) * pow(s, 12.0) * 0.25);
      gl_FragColor = vec4(c, 1.0); }`,
  side: THREE.BackSide, depthWrite: false, fog: false,
});
const sky = new THREE.Mesh(new THREE.SphereGeometry(90, 48, 24), skyMat);
sky.position.copy(SKY_C); sky.renderOrder = -1; scene.add(sky);
scene.background = null; window.__sky = true;

// stars
const starGeo = new THREE.BufferGeometry(), starPts = [];
const sr = rnd(99);
for (let i = 0; i < 700; i++) { const t = sr() * Math.PI * 2, u = 0.08 + sr() * 0.92, r = Math.sqrt(1 - u * u); starPts.push(SKY_C.x + 85 * r * Math.cos(t), 85 * u, SKY_C.z + 85 * r * Math.sin(t)); }
starGeo.setAttribute("position", new THREE.Float32BufferAttribute(starPts, 3));
const starMat = new THREE.PointsMaterial({ color: 0xffffff, size: 1.6, sizeAttenuation: false, transparent: true, opacity: 0, depthWrite: false, fog: false });
scene.add(new THREE.Points(starGeo, starMat));

// clouds
const cloudTex = canvasTex(256, 128, (c, w, h) => {
  c.clearRect(0, 0, w, h); const r = rnd(5);
  for (let i = 0; i < 26; i++) {
    const x = 40 + r() * (w - 80), y = 50 + r() * 40, rad = 18 + r() * 34;
    const g = c.createRadialGradient(x, y, 0, x, y, rad);
    g.addColorStop(0, "rgba(255,255,255,0.55)"); g.addColorStop(1, "rgba(255,255,255,0)");
    c.fillStyle = g; c.fillRect(x - rad, y - rad, rad * 2, rad * 2);
  }
}, null);
const clouds = [];
const cr = rnd(12);
for (let i = 0; i < 22; i++) {
  const m = new THREE.SpriteMaterial({ map: cloudTex, transparent: true, depthWrite: false, fog: false, opacity: 0 });
  const s = new THREE.Sprite(m); const sc = 14 + cr() * 16; s.scale.set(sc, sc * 0.45, 1);
  s.position.set(-40 + cr() * 90, 16 + cr() * 14, -40 + cr() * 85); s.userData.rank = cr(); scene.add(s); clouds.push(s);
}

// rain and snow, kept to the outdoors around the flat
const RAIN_N = 2600, SNOW_N = 1600;
const AREA = { x0: -10, x1: 20, z0: -9, z1: 13, top: 14 };
const insideBuilding = (x, z) => x > -0.2 && x < 13.2 && z > -0.2 && z < 5.35;
function spawn(arr, i, anyY) {
  let x, z; do { x = AREA.x0 + Math.random() * (AREA.x1 - AREA.x0); z = AREA.z0 + Math.random() * (AREA.z1 - AREA.z0); } while (insideBuilding(x, z));
  arr[i * 3] = x; arr[i * 3 + 1] = anyY ? Math.random() * AREA.top : AREA.top; arr[i * 3 + 2] = z;
}
const drops = new Float32Array(RAIN_N * 3); for (let i = 0; i < RAIN_N; i++) spawn(drops, i, true);
const rainPos = new Float32Array(RAIN_N * 6);
const rainGeo = new THREE.BufferGeometry(); rainGeo.setAttribute("position", new THREE.BufferAttribute(rainPos, 3));
const rain = new THREE.LineSegments(rainGeo, new THREE.LineBasicMaterial({ color: 0xb9c6d3, transparent: true, opacity: 0.5, depthWrite: false }));
rain.frustumCulled = false; scene.add(rain);
const flakes = new Float32Array(SNOW_N * 3); for (let i = 0; i < SNOW_N; i++) spawn(flakes, i, true);
const snowGeo = new THREE.BufferGeometry(); snowGeo.setAttribute("position", new THREE.BufferAttribute(flakes, 3));
const snow = new THREE.Points(snowGeo, new THREE.PointsMaterial({ color: 0xffffff, size: 0.06, transparent: true, opacity: 0.9, depthWrite: false }));
snow.frustumCulled = false; scene.add(snow);

const moon = new THREE.DirectionalLight(0x9fb4d8, 0); moon.position.set(8, 14, 10); moon.target.position.set(4, 0, 2.5); scene.add(moon, moon.target);
let fx = { cloud: 0.75, rain: 0, snow: 0, fog: 0, thunder: 0 }, wind = new THREE.Vector3(), day = 1, flash = 0, nextFlash = 5;
const deckBase = { rough: 0.85, color: new THREE.Color(0xffffff) };

const C = (h) => new THREE.Color(h);
function applyAtmosphere() {
  const p = WX[weather.code] || WX.cloudy;
  fx = { cloud: p.cloud ?? 0.5, rain: p.rain ?? 0, snow: p.snow ?? 0, fog: p.fog ?? 0, thunder: p.thunder ?? 0 };
  const from = (DIRS[String(weather.windFrom || "").toUpperCase()] ?? 180) * Math.PI / 180;
  const speed = Math.max(0, Number(weather.windKmh) || 0) / 3.6;
  wind.set(-Math.sin(from), 0, Math.cos(from)).multiplyScalar(speed);   // blows away from where it comes from (north = -z)

  const { alt, dir } = sunPos(window.launcherNow ? new Date(window.launcherNow) : new Date());
  day = THREE.MathUtils.smoothstep(alt, -0.12, 0.12);
  const dusk = Math.max(0, 1 - Math.abs(alt) / 0.2) * (1 - fx.cloud * 0.7);
  const c = fx.cloud;
  const topDay = C(0x3f7fc6).lerp(C(0x8d949b), c), horDay = C(0xcfe2f1).lerp(C(0xc4c8cb), c);
  const topNight = C(0x070b17).lerp(C(0x15181d), c), horNight = C(0x18203a).lerp(C(0x23262b), c);
  const top = topNight.clone().lerp(topDay, day), hor = horNight.clone().lerp(horDay, day).lerp(C(0xf0a46a), dusk * 0.55);
  skyMat.uniforms.top.value.copy(top); skyMat.uniforms.horizon.value.copy(hor);
  skyMat.uniforms.ground.value.copy(C(0x2a2c2e).lerp(C(0x9b9e97).lerp(C(0x8a8d8a), c), day));
  skyMat.uniforms.sunDir.value.copy(dir); skyMat.uniforms.sunVis.value = Math.max(0, 1 - c * 1.1) * THREE.MathUtils.smoothstep(alt, -0.03, 0.05);
  starMat.opacity = (1 - day) * (1 - c) * 0.9;

  sun.position.copy(sun.target.position).addScaledVector(dir, 25);
  sun.intensity = 2.6 * day * (1 - 0.82 * c) * THREE.MathUtils.smoothstep(alt, -0.02, 0.08);
  sun.color.copy(C(0xfff3e2).lerp(C(0xffb27a), dusk));
  sun.castShadow = sun.intensity > 0.15;
  moon.intensity = (1 - day) * 0.25;
  hemi.intensity = 0.35 + 1.0 * day * (1 - 0.15 * c);
  hemi.color.copy(C(0x8a9bc0).lerp(C(0xf4f1ec).lerp(C(0xdfe3e6), c), day));
  hemi.groundColor.copy(C(0x2c2f36).lerp(C(0xcfcac2), day));
  renderer.toneMappingExposure = 0.85 + 0.15 * day;

  scene.fog = fx.fog > 0 ? new THREE.Fog(hor.getHex(), 6, 45) : (fx.rain > 0.5 ? new THREE.Fog(hor.getHex(), 25, 110) : null);
  clouds.forEach(s => {
    const show = s.userData.rank < Math.min(1, c * 1.15 + 0.05);
    s.material.opacity = show ? 0.55 + 0.4 * c : 0;
    s.material.color.copy(C(0xffffff).lerp(C(0x9a9fa5), c)).multiplyScalar(0.25 + 0.75 * day);
  });
  rain.visible = fx.rain > 0; snow.visible = fx.snow > 0;
  rainGeo.setDrawRange(0, Math.round(RAIN_N * fx.rain) * 2);
  snowGeo.setDrawRange(0, Math.round(SNOW_N * fx.snow));
  if (M.deck) { const wet = fx.rain > 0 || fx.snow > 0; M.deck.roughness = wet ? 0.35 : deckBase.rough; M.deck.color.set(wet ? 0xbdbdbd : 0xffffff); }
  renderBadge();
  afterAtmosphere();
}

function weatherTick(dt) {
  if (rain.visible) {
    const vy = 9, n = Math.round(RAIN_N * fx.rain), sx = wind.x * 0.06, sz = wind.z * 0.06;
    for (let i = 0; i < n; i++) {
      drops[i * 3] += wind.x * 0.5 * dt; drops[i * 3 + 1] -= vy * dt; drops[i * 3 + 2] += wind.z * 0.5 * dt;
      if (drops[i * 3 + 1] < -0.15) spawn(drops, i, false);
      const x = drops[i * 3], y = drops[i * 3 + 1], z = drops[i * 3 + 2], j = i * 6;
      rainPos[j] = x; rainPos[j + 1] = y; rainPos[j + 2] = z; rainPos[j + 3] = x - sx; rainPos[j + 4] = y + 0.32; rainPos[j + 5] = z - sz;
    }
    rainGeo.attributes.position.needsUpdate = true;
  }
  if (snow.visible) {
    const n = Math.round(SNOW_N * fx.snow), t = performance.now() * 0.001;
    for (let i = 0; i < n; i++) {
      flakes[i * 3] += (wind.x * 0.25 + Math.sin(t + i) * 0.15) * dt; flakes[i * 3 + 1] -= 0.8 * dt; flakes[i * 3 + 2] += (wind.z * 0.25 + Math.cos(t * 0.7 + i) * 0.15) * dt;
      if (flakes[i * 3 + 1] < -0.15) spawn(flakes, i, false);
    }
    snowGeo.attributes.position.needsUpdate = true;
  }
  clouds.forEach(s => {
    s.position.x += wind.x * 0.12 * dt; s.position.z += wind.z * 0.12 * dt;
    if (s.position.x > 55) s.position.x -= 100; if (s.position.x < -45) s.position.x += 100;
    if (s.position.z > 50) s.position.z -= 95; if (s.position.z < -45) s.position.z += 95;
  });
  if (fx.thunder) {
    nextFlash -= dt;
    if (nextFlash <= 0) { flash = 0.18; nextFlash = 6 + Math.random() * 14; }
    if (flash > 0) { flash -= dt; hemi.intensity += flash > 0.09 || flash < 0.04 ? 2.5 * dt * 60 : 0; } else applyAtmosphereLight();
  }
}
function applyAtmosphereLight() { hemi.intensity = 0.35 + 1.0 * day * (1 - 0.15 * fx.cloud); }

const wxEl = document.getElementById("wx");
function renderBadge() {
  if (!wxEl) return;
  let t = "";
  try { t = new Date(weather.observedAt).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Riga" }); } catch (e) {}
  wxEl.querySelector(".wx-main").textContent = `${Math.round(Number(weather.tempC))} °C · ${weather.summary || weather.code}`;
  wxEl.querySelector(".wx-sub").textContent = `Riga, Brīvības 95 · wind ${Math.round(Number(weather.windKmh) || 0)} km/h ${weather.windFrom || ""}${t ? " · updated " + t : ""}`;
}

applyAtmosphere();
setInterval(applyAtmosphere, 60000);


/* ---------- launcher ---------- */

// After every weather/sun update: redraw the shadows (the sun has moved). From above,
// sky, clouds, stars and fog would sit between the camera and the flat, so they're off.
function afterAtmosphere() {
  if (VIEW === "orbit") {
    scene.fog = null;
    scene.background = new THREE.Color(SCENE_COLOR);
    sky.visible = false;
    starMat.visible = false;
    clouds.forEach(s => { s.visible = false; });
  }
  renderer.shadowMap.needsUpdate = true;
}

/** Called by the app with live conditions: { code, tempC, windKmh, windFrom, summary, observedAt }. */
window.setWeather = (w) => {
  if (w && WX[w.code]) {
    weather = { ...weather, ...w };
    applyAtmosphere();
  }
};

// The model is a few hundred small meshes; drawing each separately is the main cost on a TV.
// Nothing in it moves, so bake every mesh into world space and merge them per material:
// the picture is identical but each frame needs a few dozen draw calls instead of hundreds.
function mergeStatic() {
  if (VIEW === "orbit") root.remove(ceiling);
  root.updateMatrixWorld(true);
  const byMaterial = new Map();
  root.traverse(o => {
    if (!o.isMesh || !o.visible) return;
    const g = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
    g.applyMatrix4(o.matrixWorld);
    for (const name of Object.keys(g.attributes)) if (!["position", "normal", "uv"].includes(name)) g.deleteAttribute(name);
    if (!g.attributes.uv) g.setAttribute("uv", new THREE.Float32BufferAttribute(new Float32Array(g.attributes.position.count * 2), 2));
    g.clearGroups();
    if (!byMaterial.has(o.material)) byMaterial.set(o.material, { geos: [], cast: false });
    const entry = byMaterial.get(o.material);
    entry.geos.push(g);
    entry.cast = entry.cast || o.castShadow;
  });
  const merged = new THREE.Group();
  for (const [material, { geos, cast }] of byMaterial) {
    const mesh = new THREE.Mesh(mergeGeometries(geos), material);
    mesh.castShadow = cast;
    mesh.receiveShadow = true;
    mesh.renderOrder = material.transparent ? 2 : 0;
    merged.add(mesh);
    geos.forEach(g => g.dispose());
  }
  scene.remove(root);
  root = merged;
  scene.add(root);
  renderer.shadowMap.needsUpdate = true;
}
mergeStatic();

// Orbit: a steep view from above, like the original "Overview", turning slowly.
const target = new THREE.Vector3(4.3, 0, 2.6);   // middle of flat + terrace
const RADIUS = 11.5, HEIGHT = 20;
// Window: standing in the living room, looking out of the glazed terrace door.
const EYE = new THREE.Vector3(0.62, 1.38, 1.8), LOOK = new THREE.Vector3(-6, 2.45, 1.8);

camera.fov = VIEW === "window" ? 74 : 42;
camera.updateProjectionMatrix();

// The angle comes from the clock, not a frame count, so the motion speed is always the same.
let paused = false, angle = 0.6, last = 0, time = 0;
window.launcherPause = (p) => { paused = p; last = 0; slow = fast = 0; };
// For previews and checks.
window.launcherStats = () => ({ calls: renderer.info.render.calls, triangles: renderer.info.render.triangles, renderWidth, view: VIEW, weather });
// For previews: pretend it's this time (an ISO date string) when placing the sun.
window.launcherNow = null;
window.launcherAngle = (a) => { angle = a; time = a * 20; place(); renderer.render(scene, camera); };

const look = new THREE.Vector3();
function place() {
  if (VIEW === "window") {
    // A slow, slight drift so the room feels lived in rather than frozen.
    const yaw = Math.sin(time / 23) * 0.025, rise = Math.sin(time / 31) * 0.015;
    camera.position.set(EYE.x, EYE.y + rise * 0.5, EYE.z + Math.sin(time / 41) * 0.025);
    look.set(LOOK.x, LOOK.y + rise * 3, LOOK.z + Math.sin(yaw) * (EYE.x - LOOK.x));
    camera.lookAt(look);
  } else {
    camera.position.set(target.x + Math.sin(angle) * RADIUS, HEIGHT, target.z + Math.cos(angle) * RADIUS);
    camera.lookAt(target);
  }
}

// Orbit view: nudge the model up and right, into the space between the clock and the app row.
function frame() {
  camera.aspect = innerWidth / innerHeight;
  if (VIEW === "orbit") {
    camera.setViewOffset(innerWidth, innerHeight, -innerWidth * 0.06, innerHeight * 0.06, innerWidth, innerHeight);
  } else {
    camera.updateProjectionMatrix();
  }
  renderer.setPixelRatio(renderWidth / innerWidth);
  renderer.setSize(innerWidth, innerHeight);
}
frame();
addEventListener("resize", frame);

/* Smooth motion: draw on every screen refresh. If frames start arriving late (the TV's
   GPU can't keep up), lower the render resolution a step; if there's plenty of headroom
   for a while, raise it again. A few late frames in a row are what reads as judder. */
let slow = 0, fast = 0;
function adapt(frameMs) {
  if (frameMs > 22) { slow++; fast = 0; } else if (frameMs < 17.5) { fast++; slow = Math.max(0, slow - 1); }
  if (slow >= 20 && renderWidth > MIN_WIDTH) {
    renderWidth = Math.max(MIN_WIDTH, Math.round(renderWidth * 0.85));
    slow = fast = 0;
    frame();
  } else if (fast >= 600 && renderWidth < MAX_WIDTH) {   // ten calm seconds
    renderWidth = Math.min(MAX_WIDTH, Math.round(renderWidth * 1.1));
    slow = fast = 0;
    frame();
  }
}

renderer.setAnimationLoop((now) => {
  if (paused) return;
  if (last) {
    const ms = now - last;
    const dt = Math.min(ms, 100) / 1000;
    angle += dt * Math.PI * 2 / ORBIT_SECONDS;
    time += dt;
    weatherTick(Math.min(dt, 0.05));
    adapt(ms);
  }
  last = now;
  place();
  renderer.render(scene, camera);
});
place();
renderer.render(scene, camera);
document.documentElement.classList.add("ready");
