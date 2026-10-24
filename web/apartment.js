// The apartment model from "Apartment 028" (three.js), shown as a slowly orbiting
// overview for the TV launcher's background. The model code is unchanged from the
// original page; the controls and panel are replaced by an automatic orbit.
// Bundled into ../assets/apartment/apartment.js by web/build.sh.

const SCENE_COLOR = "#1d1e20";   // the original's dark-mode scene colour
const ORBIT_SECONDS = 150;       // one full turn

import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";

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
scene.add(new THREE.HemisphereLight(0xf4f1ec, 0xcfcac2, 1.3));
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

  /* ----- outdoor terrace: 3.6 m out from the facade × 4.0 m along it ----- */
  const TZ0 = -0.15, TZ1 = 3.85, TX = -3.75;
  box(TX, -0.15, -0.15, -0.03, TZ0, TZ1, M.deck);                              // decking
  box(TX - 0.02, TX, -0.17, -0.02, TZ0, TZ1, M.deck);                          // front edge board
  // facade: light-grey render on the outside face, anthracite surround to the door
  box(-0.17, -0.15, -0.03, H, -0.15, 1.2, M.render, { cast: false });
  box(-0.17, -0.15, -0.03, H, 2.4, 3.85, M.render, { cast: false });
  box(-0.17, -0.15, 2.5, H, 1.2, 2.4, M.render, { cast: false });
  box(-0.19, -0.15, -0.03, 2.5, 1.2, 1.25, M.anthracite);
  box(-0.19, -0.15, -0.03, 2.5, 2.35, 2.4, M.anthracite);
  box(-0.19, -0.15, 2.45, 2.5, 1.2, 2.4, M.anthracite);
  // downpipe at the corner of the facade
  const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, H + 0.15, 16), M.zinc);
  pipe.position.set(-0.24, (H + 0.15) / 2 - 0.15, -0.08); pipe.castShadow = true; root.add(pipe);

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
  prism(st, 0.12, 2.38, M.tileStove);
  prism(stO, 2.38, 2.48, M.tileStove);
  prism(st, 2.48, 2.56, M.tileStove);


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

  ceiling.visible = false;   // seen from above, like the original "Overview"
  renderer.shadowMap.needsUpdate = true;
}

/* ---------- launcher: slow orbit ---------- */
// The light and the model never move, so the shadow map is drawn once and reused.
renderer.shadowMap.autoUpdate = false;
build(3.0);

// The model is ~300 small meshes; drawing each separately is the main cost on a TV.
// Nothing moves, so bake every mesh into world space and merge them per material:
// the picture is identical but each frame needs a few dozen draw calls instead of 300.
function mergeStatic() {
  root.remove(ceiling);
  root.updateMatrixWorld(true);
  const byMaterial = new Map();
  root.traverse(o => {
    if (!o.isMesh) return;
    const g = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
    g.applyMatrix4(o.matrixWorld);
    for (const name of Object.keys(g.attributes)) if (!["position", "normal", "uv"].includes(name)) g.deleteAttribute(name);
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

const target = new THREE.Vector3(4.3, 0, 2.6);   // middle of flat + terrace
const RADIUS = 11.5, HEIGHT = 20;   // steep, like the original "Overview"
camera.fov = 42;
camera.updateProjectionMatrix();

// The angle comes from the clock, not a frame count, so the turn speed is always the same.
let paused = false, angle = 0.6, last = 0;
window.launcherPause = (p) => { paused = p; last = 0; slow = fast = 0; };
// For previews and checks.
window.launcherStats = () => ({ calls: renderer.info.render.calls, triangles: renderer.info.render.triangles, renderWidth });
window.launcherAngle = (a) => { angle = a; place(); renderer.render(scene, camera); };

function place() {
  camera.position.set(target.x + Math.sin(angle) * RADIUS, HEIGHT, target.z + Math.cos(angle) * RADIUS);
  camera.lookAt(target);
}

// Nudge the model up and right, into the space between the clock and the app row.
function frame() {
  camera.aspect = innerWidth / innerHeight;
  camera.setViewOffset(innerWidth, innerHeight, -innerWidth * 0.06, innerHeight * 0.06, innerWidth, innerHeight);
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
    angle += Math.min(ms, 100) / 1000 * Math.PI * 2 / ORBIT_SECONDS;
    adapt(ms);
  }
  last = now;
  place();
  renderer.render(scene, camera);
});
place();
renderer.render(scene, camera);
document.documentElement.classList.add("ready");
