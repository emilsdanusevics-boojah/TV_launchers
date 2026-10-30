// The apartment model from the "Apartment" page (three.js), set up to render the video the
// Apartment launcher shows: the flat turning slowly, on a transparent background, from the
// launcher's camera. The app draws the live sky around it and plays the video in a loop, so
// the TV only decodes video instead of drawing the detailed model every frame.
//
// web/render_video.js loads this page (bundled by web/build.sh) in a headless browser, asks
// for each frame through window.renderSetup / window.renderFrame, and encodes the video.
// The lighting (sun, night lamps) comes from the original's outdoor weather code.

// Seen from above: no ceiling.
function applyToggles() {
  if (ceiling) ceiling.visible = false;
}

// ==== MODEL START: generated from the apartment page by web/import_model.py ====
import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";

/* ---------- renderer / scene ---------- */
const stage = document.getElementById("stage");
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
renderer.setPixelRatio(1);   // render size is set per video by renderSetup()
renderer.setClearColor(0x000000, 0);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.0;
stage.appendChild(renderer.domElement);


const scene = new THREE.Scene();
scene.background = null;   // transparent: the app draws the sky

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
  mirror: new THREE.MeshStandardMaterial({ color: 0xc9d2d6, roughness: 0.06, metalness: 0.55, envMapIntensity: 1.4 }),
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
  deskWhite: new THREE.MeshStandardMaterial({ color: 0xf5f5f3, roughness: 0.4, envMapIntensity: 0.5 }),
  mesh: new THREE.MeshStandardMaterial({ map: canvasTex(128, 128, (c, w, h) => { c.clearRect(0, 0, w, h); c.strokeStyle = "#f4f4f2"; c.lineWidth = 3; for (let k = 0; k <= w; k += 16) { c.beginPath(); c.moveTo(k, 0); c.lineTo(k, h); c.stroke(); c.beginPath(); c.moveTo(0, k); c.lineTo(w, k); c.stroke(); } }, [0.12, 0.12]), transparent: true, alphaTest: 0.4, roughness: 0.4, side: THREE.DoubleSide }),
  deskMat: new THREE.MeshStandardMaterial({ color: 0x1e1f21, roughness: 0.95 }),
  keycap: new THREE.MeshStandardMaterial({ color: 0x2e2f33, roughness: 0.6 }),
  mousePink: new THREE.MeshStandardMaterial({ color: 0xe9d3d1, roughness: 0.45 }),
  laptop: new THREE.MeshStandardMaterial({ color: 0x55575c, roughness: 0.35, metalness: 0.6 }),
  frameWhite: new THREE.MeshStandardMaterial({ color: 0xf6f6f4, roughness: 0.4 }),
  matBoard: new THREE.MeshStandardMaterial({ color: 0xfbfaf7, roughness: 0.9 }),
  chrome: new THREE.MeshStandardMaterial({ color: 0xe6e8ea, roughness: 0.12, metalness: 1, envMapIntensity: 1.3 }),
  beech: new THREE.MeshStandardMaterial({ map: oakTex, color: 0xffd99a, roughness: 0.35, envMapIntensity: 0.6 }),
  chairFabric: new THREE.MeshStandardMaterial({ map: canvasTex(256, 256, (c, w, h) => {
    noiseFill(c, w, h, "#b9ab98", 16, 61); const r = rnd(62);
    for (let i = 0; i < 90; i++) { c.fillStyle = r() > 0.5 ? "rgba(120,112,104,0.35)" : "rgba(235,222,200,0.45)"; c.beginPath(); c.ellipse(r() * w, r() * h, 4 + r() * 16, 2 + r() * 8, r() * 3, 0, 7); c.fill(); }
  }, [0.25, 0.25]), roughness: 0.95 }),
  cavity: new THREE.MeshStandardMaterial({ color: 0x3a3a3a, roughness: 1 }),
  bamboo: new THREE.MeshStandardMaterial({ color: 0xd9b77a, roughness: 0.6 }),
  aluPanel: new THREE.MeshStandardMaterial({ color: 0xb9bcbf, roughness: 0.45, metalness: 0.5 }),
  splashDark: new THREE.MeshStandardMaterial({ color: 0x1c1d1f, roughness: 0.5 }),
  jarDark: new THREE.MeshStandardMaterial({ color: 0x2a2420, roughness: 0.3 }),
  jarLight: new THREE.MeshStandardMaterial({ color: 0xd9cbb0, roughness: 0.3 }),
  mint: new THREE.MeshStandardMaterial({ color: 0xb5d1bb, roughness: 0.4, metalness: 0.3 }),
  basinIn: new THREE.MeshStandardMaterial({ color: 0xe9ecec, roughness: 0.2 }),
  towelBlue: new THREE.MeshStandardMaterial({ color: 0x6f8ea8, roughness: 1 }),
  towelPink: new THREE.MeshStandardMaterial({ color: 0xe5c6b8, roughness: 1 }),
  marble: new THREE.MeshStandardMaterial({ map: canvasTex(512, 1024, (c, w, h) => {
    noiseFill(c, w, h, "#f1efea", 5, 71); const r = rnd(72);
    for (let i = 0; i < 26; i++) {
      c.strokeStyle = r() > 0.6 ? "rgba(150,110,70,0.75)" : "rgba(90,80,70,0.55)"; c.lineWidth = 1 + r() * 3;
      let x = r() * w, y = r() * h, a = r() * Math.PI; c.beginPath(); c.moveTo(x, y);
      for (let k = 0; k < 8; k++) { a += (r() - 0.5) * 0.6; x += Math.cos(a) * 40; y += Math.sin(a) * 40; c.lineTo(x, y); } c.stroke();
    }
    c.strokeStyle = "rgba(200,198,192,0.9)"; c.lineWidth = 2; c.strokeRect(0, 0, w, h); c.beginPath(); c.moveTo(0, h / 2); c.lineTo(w, h / 2); c.stroke();
  }, [0.6, 1.2]), roughness: 0.15, envMapIntensity: 0.8 }),
  meshBlack: new THREE.MeshStandardMaterial({ map: canvasTex(128, 128, (c, w, h) => { c.clearRect(0, 0, w, h); c.strokeStyle = "#141414"; c.lineWidth = 4; for (let k = 0; k <= w; k += 12) { c.beginPath(); c.moveTo(k, 0); c.lineTo(k, h); c.stroke(); c.beginPath(); c.moveTo(0, k); c.lineTo(w, k); c.stroke(); } }, null), transparent: true, alphaTest: 0.4, side: THREE.DoubleSide, roughness: 0.6 }),
  umbrella: new THREE.MeshStandardMaterial({ color: 0x22252b, roughness: 0.7 }),
  coatBlack: new THREE.MeshStandardMaterial({ color: 0x1d1e21, roughness: 0.75 }),
  quilted: new THREE.MeshStandardMaterial({ map: canvasTex(256, 256, (c, w, h) => {
    c.fillStyle = "#cdb595"; c.fillRect(0, 0, w, h); c.strokeStyle = "rgba(110,90,60,0.45)"; c.lineWidth = 2;
    for (let k = -h; k < w + h; k += 32) { c.beginPath(); c.moveTo(k, 0); c.lineTo(k + h, h); c.stroke(); c.beginPath(); c.moveTo(k, h); c.lineTo(k + h, 0); c.stroke(); }
  }, null), roughness: 0.6 }),
  bronze: new THREE.MeshStandardMaterial({ color: 0x7a5a3c, roughness: 0.35, metalness: 0.8 }),
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
   Living 028-4: x 0–5.84, z 0–3.60 | Hall/kitchen 028-2: A x 6.29–11.74 z 1.20–2.80, B x 8.45–11.11 z 2.80–4.74, nooks either side of the entry
   Bathroom 028-3: x 6.85–8.35, z 2.90–5.15, widening at the lower left (0.84+0.32, then 0.58 step, then 0.15 step) | Entry 028-1: x 11.20–12.30, z 2.17–3.65 */
const lightsGroup = new THREE.Group(); scene.add(lightsGroup);

function build(H) {
  scene.remove(root);
  root.traverse(o => { if (o.geometry) o.geometry.dispose(); });
  root = new THREE.Group(); scene.add(root);
  fixtures = new THREE.Group(); root.add(fixtures);
  ceiling = new THREE.Group(); root.add(ceiling);
  lightsGroup.clear();

  // floors
  const rooms = [[0, 5.84, 0, 3.6], [6.29, 11.11, 1.2, 2.8], [8.45, 11.11, 2.8, 4.74], [11.11, 11.54, 3.75, 4.74],
    [11.11, 11.65, 1.2, 1.64], [11.11, 11.74, 1.64, 2.07], [11.54, 11.74, 3.75, 4.23], [11.20, 12.30, 2.17, 3.65],
    [5.32, 6.29, 1.3, 2.1], [11.11, 11.20, 2.3, 3.1], [12.30, 12.45, 2.17, 3.15], [-0.15, 0, 1.25, 2.35]];
  rooms.forEach(r => flat(r[0], r[1], r[2], r[3], 0, M.floor));
  const bath = [[6.85, 8.35, 2.90, 4.06], [6.67, 8.35, 4.06, 4.64], [6.52, 8.35, 4.64, 5.15]];
  bath.forEach(r => flat(r[0], r[1], r[2], r[3], 0, M.bathFloor));
  flat(8.35, 8.45, 3.05, 3.80, 0, M.bathFloor);
  [...rooms.slice(0, 8), ...bath].forEach(r => flat(r[0], r[1], r[2], r[3], H, M.ceil, true, ceiling));

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
  wall(5.84, 11.65, 1.05, 1.2, H);                                // hall outer wall (table side)
  wall(6.70, 8.45, 2.8, 2.9, H);                                  // hall ↔ bathroom
  wall(8.35, 8.45, 2.9, 4.74, H, [[3.05, 3.8, 0, 2.05]]);         // bathroom door wall
  wall(8.35, 8.50, 4.74, 5.30, H);                                // bathroom, right (outer)
  wall(6.37, 8.35, 5.15, 5.30, H);                                // bathroom, bottom
  wall(8.50, 11.69, 4.74, 4.89, H);                               // kitchen wall
  // nook above the entry (0.44 + 0.43 deep, 0.09 jog, 0.63 wide)
  wall(11.65, 11.89, 1.05, 1.64, H);
  wall(11.74, 11.89, 1.64, 2.17, H);
  // entry box 1.10 × 1.48, 0.09 partitions
  wall(11.11, 12.45, 2.07, 2.17, H);                              // entry, top
  wall(11.11, 12.45, 3.65, 3.75, H);                              // entry, bottom
  wall(11.11, 11.20, 2.07, 3.75, H, [[2.3, 3.1, 0, 2.85]]);       // hall ↔ entry: door + transom window above
  box(11.11, 11.20, 2.05, 2.25, 2.3, 3.1, M.paint);
  box(11.11, 11.20, 2.25, 2.85, 2.3, 2.35, M.paint); box(11.11, 11.20, 2.25, 2.85, 3.05, 3.1, M.paint);
  wall(12.30, 12.45, 2.07, 3.75, H, [[2.17, 3.15, 0, 2.72]]);     // glazed entrance: door + side light + transoms
  // nook below the entry (0.48 deep, 0.63 wide), then the wall steps 0.20 back for 0.51
  wall(11.74, 11.89, 3.65, 4.38, H);
  wall(11.54, 11.89, 4.23, 4.38, H);
  wall(11.54, 11.69, 4.23, 4.89, H);

  // skirting
  skirt("z", 0, 0, 3.6, 1, [[1.25, 2.35]]);
  skirt("x", 0, 0, 5.84, 1);
  skirt("z", 5.84, 0, 0.63, -1);
  skirt("x", 0.63, 5.32, 5.84, -1);
  skirt("x", 3.6, 0, 4.38, -1);
  skirt("x", 1.2, 6.29, 11.65, 1);
  skirt("z", 11.65, 1.2, 1.64, -1);
  skirt("x", 1.64, 11.65, 11.74, 1);
  skirt("x", 2.07, 11.11, 11.74, -1);
  skirt("z", 11.11, 2.07, 3.75, -1, [[2.23, 3.17]]);
  skirt("x", 3.75, 11.11, 11.74, 1);
  skirt("z", 11.74, 3.75, 4.23, -1);
  skirt("x", 4.23, 11.54, 11.74, -1);
  skirt("z", 6.29, 1.2, 2.8, 1, [[1.23, 2.17]]);
  skirt("x", 2.8, 6.29, 8.45, -1);
  skirt("z", 8.45, 2.8, 4.74, 1, [[2.98, 3.87]]);
  skirt("z", 11.20, 2.17, 3.65, 1, [[2.23, 3.17]]);
  skirt("z", 12.30, 3.15, 3.65, -1);
  skirt("x", 2.17, 11.20, 12.30, 1);

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
    box(x - 0.01, x + 0.048, 0, slatTop, 3.635, 3.65, M.black, { cast: false });
    box(x, x + 0.036, 0, slatTop, 3.605, 3.635, M.slat);
  }

  // doors
  doorFrame("x", 5.32, 6.29, 1.3, 2.1, 2.05, [0.06, 0.015]);
  doorLeaf(5.34, 2.07, 0.74, 2.0, Math.PI / 2 + 1.25);             // living-room door, open into the room
  doorFrame("x", 8.35, 8.45, 3.05, 3.8, 2.05);
  doorLeaf(8.47, 3.08, 0.69, 2.0, -Math.PI / 2 + 1.1);             // bathroom door, ajar
  doorFrame("x", 11.11, 11.20, 2.3, 3.1, 2.05);
  doorLeaf(11.09, 3.07, 0.74, 2.0, Math.PI / 2 + 1.3);             // inner oak door, swung open into the hall
  windowUnit("x", 11.155, 2.35, 3.05, 2.25, 2.85, [], []);          // white-framed window above it

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

  /* ----- work corner on the same wall (z = 0), between the shelf and the slatted nook; placed from the photo ----- */
  const DX0 = 2.85, DX1 = 4.25, DD = 0.6, DH = 0.75;
  box(DX0, DX1, DH - 0.025, DH, 0.0, DD, M.deskWhite);                            // white desk top, 140 × 60 cm
  // two white metal trestle units with mesh shelves and small castors
  [[DX0 + 0.06, DX0 + 0.44], [DX1 - 0.44, DX1 - 0.06]].forEach(([a, b], k) => {
    [[a, 0.06], [b, 0.06], [a, DD - 0.06], [b, DD - 0.06]].forEach(([x, z]) => {
      box(x - 0.012, x + 0.012, 0.04, DH - 0.025, z - 0.012, z + 0.012, M.shelfWhite);
      const w = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.022, 0.035, 12).rotateZ(Math.PI / 2), M.black);
      w.position.set(x, 0.02, z); root.add(w);
    });
    (k === 0 ? [0.12, 0.45] : [0.12, 0.33, 0.52]).forEach(y => {
      box(a, b, y, y + 0.012, 0.06, DD - 0.06, M.mesh, { cast: false });
      box(a, b, y + 0.012, y + 0.035, DD - 0.072, DD - 0.06, M.shelfWhite, { cast: false });   // front rail
    });
  });
  // desk mat, keyboard, mouse
  box(3.06, 3.96, DH, DH + 0.004, 0.2, 0.56, M.deskMat, { cast: false });
  box(3.34, 3.70, DH + 0.004, DH + 0.024, 0.31, 0.44, M.black);
  for (let r = 0; r < 5; r++) for (let c = 0; c < 15; c++) box(3.35 + c * 0.0225, 3.35 + c * 0.0225 + 0.018, DH + 0.024, DH + 0.032, 0.318 + r * 0.023, 0.318 + r * 0.023 + 0.018, M.keycap, { cast: false });
  const mouse = new THREE.Mesh(new THREE.SphereGeometry(0.035, 20, 12).scale(0.9, 0.5, 1.45), M.mousePink);
  mouse.position.set(3.82, DH + 0.012, 0.42); mouse.castShadow = true; root.add(mouse);
  // 27" monitor on a V-shaped foot
  const monTex = canvasTex(512, 300, (c, w, h) => {
    c.fillStyle = "#1d2025"; c.fillRect(0, 0, w, h); c.fillStyle = "#2a2e35"; c.fillRect(0, 0, w, 14); c.fillRect(0, 14, 70, h);
    const r = rnd(8); c.fillStyle = "#8a93a3";
    for (let y = 30; y < h - 20; y += 9) { const x = 150 + r() * 20; c.globalAlpha = 0.35 + r() * 0.4; c.fillRect(x, y, 120 + r() * 160, 3); }
    c.globalAlpha = 0.5; for (let y = 24; y < h - 20; y += 12) c.fillRect(10, y, 40 + r() * 15, 3);
    c.globalAlpha = 1;
  }, null);
  box(3.07, 3.69, 0.855, 1.225, 0.165, 0.19, M.black);
  const scr = new THREE.Mesh(new THREE.PlaneGeometry(0.6, 0.345), new THREE.MeshStandardMaterial({ map: monTex, emissive: 0xffffff, emissiveMap: monTex, emissiveIntensity: 0.9, roughness: 0.3 }));
  scr.position.set(3.38, 1.04, 0.1905); root.add(scr);
  box(3.36, 3.40, DH, 0.9, 0.12, 0.15, M.black);                                 // neck
  const footL = box(3.15, 3.40, DH, DH + 0.012, 0.12, 0.15, M.black); footL.geometry.translate(-3.275, 0, -0.135); footL.position.set(3.275, 0, 0.135); footL.rotation.y = -0.45;
  const footR = box(3.36, 3.61, DH, DH + 0.012, 0.12, 0.15, M.black); footR.geometry.translate(-3.485, 0, -0.135); footR.position.set(3.485, 0, 0.135); footR.rotation.y = 0.45;
  // clamp-on pole with an arm holding a ventilated laptop tray
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.018, 0.5, 14), M.black);
  pole.position.set(3.74, DH + 0.25, 0.06); pole.castShadow = true; root.add(pole);
  box(3.72, 3.98, 0.9, 0.93, 0.05, 0.08, M.black);
  const tray = new THREE.Group(); tray.position.set(4.02, 0.88, 0.3); tray.rotation.x = -0.22; root.add(tray);
  rb(0.38, 0.012, 0.28, 0.004, M.black, 0, 0, 0, tray);
  rb(0.38, 0.03, 0.012, 0.004, M.black, 0, 0.015, 0.135, tray);                  // front lip
  rb(0.32, 0.016, 0.22, 0.006, M.laptop, 0, 0.014, 0.01, tray);                    // laptop base
  const lid = new THREE.Group(); lid.position.set(0, 0.022, -0.1); lid.rotation.x = -1.85 + 1.57 - 0.25; tray.add(lid);
  rb(0.32, 0.21, 0.01, 0.006, M.laptop, 0, 0.105, 0, lid);
  const lscr = new THREE.Mesh(new THREE.PlaneGeometry(0.29, 0.18), new THREE.MeshStandardMaterial({ map: monTex, emissive: 0xffffff, emissiveMap: monTex, emissiveIntensity: 0.8 }));
  lscr.position.set(0, 0.105, 0.0055); lid.add(lscr);

  // wall art: 50 × 70 print and 30 × 40 print in white frames with mats
  function framed(cx, y0, w, h, art, mat = 0.06) {
    const g = new THREE.Group(); g.position.set(cx, y0 + h / 2, 0); root.add(g);
    const fw = 0.025, d = 0.025;
    [[0, h / 2 - fw / 2, w, fw], [0, -h / 2 + fw / 2, w, fw], [-w / 2 + fw / 2, 0, fw, h], [w / 2 - fw / 2, 0, fw, h]].forEach(([x, y, a, b]) => rb(a, b, d, 0.004, M.frameWhite, x, y, d / 2, g));
    const mt = new THREE.Mesh(new THREE.PlaneGeometry(w - 2 * fw, h - 2 * fw), M.matBoard); mt.position.z = 0.008; g.add(mt);
    const pic = new THREE.Mesh(new THREE.PlaneGeometry(w - 2 * fw - 2 * mat, h - 2 * fw - 2 * mat), new THREE.MeshStandardMaterial({ map: art, roughness: 0.6 }));
    pic.position.z = 0.009; g.add(pic);
    const gl = new THREE.Mesh(new THREE.PlaneGeometry(w - 2 * fw, h - 2 * fw), M.glass); gl.position.z = 0.02; g.add(gl);
  }
  const bigArt = canvasTex(400, 560, (c, w, h) => {
    const bg = c.createLinearGradient(0, 0, w, h); bg.addColorStop(0, "#4a5a6a"); bg.addColorStop(1, "#2f3440");
    c.fillStyle = bg; c.fillRect(0, 0, w, h);
    c.fillStyle = "#c98c6a"; c.fillRect(0, h * 0.68, w, h * 0.32);                        // table top
    c.fillStyle = "#e9a6b8"; c.fillRect(w * 0.42, h * 0.1, w * 0.18, h * 0.32);         // pink panel
    c.fillStyle = "#5fb0c9"; c.fillRect(w * 0.6, h * 0.04, w * 0.16, h * 0.4);         // blue panel
    c.fillStyle = "#f08a2c"; c.beginPath(); c.moveTo(w * 0.12, h * 0.72); c.lineTo(w * 0.12, h * 0.3); c.lineTo(w * 0.2, h * 0.2); c.lineTo(w * 0.2, h * 0.08); c.lineTo(w * 0.3, h * 0.08); c.lineTo(w * 0.3, h * 0.2); c.lineTo(w * 0.4, h * 0.3); c.lineTo(w * 0.4, h * 0.72); c.fill();
    c.fillStyle = "#f6c26b"; c.globalAlpha = 0.7; c.fillRect(w * 0.05, h * 0.42, w * 0.14, h * 0.18); c.globalAlpha = 1;
    c.strokeStyle = "rgba(220,240,240,0.8)"; c.lineWidth = 3; c.fillStyle = "rgba(170,210,215,0.35)";
    c.beginPath(); c.ellipse(w * 0.66, h * 0.62, w * 0.2, h * 0.13, 0, 0, Math.PI * 2); c.fill(); c.stroke();
    c.fillRect(w * 0.56, h * 0.3, w * 0.2, h * 0.2); c.strokeRect(w * 0.56, h * 0.3, w * 0.2, h * 0.2);
    c.fillStyle = "#6f9a3e"; [[0.6, 0.6], [0.68, 0.64], [0.74, 0.58], [0.64, 0.68]].forEach(([x, y]) => { c.beginPath(); c.arc(w * x, h * y, w * 0.045, 0, 7); c.fill(); });
    c.fillStyle = "#d9e04f"; c.beginPath(); c.arc(w * 0.86, h * 0.7, w * 0.05, 0, 7); c.fill();
    c.fillStyle = "#efe8dc"; c.fillRect(w * 0.08, h * 0.88, w * 0.1, h * 0.08);
  }, null);
  const smallArt = canvasTex(240, 320, (c, w, h) => {
    c.fillStyle = "#d9cdbb"; c.fillRect(0, 0, w, h);
    c.fillStyle = "#8a8f86"; c.fillRect(0, h * 0.55, w, h * 0.12);
    c.fillStyle = "#7a9a52"; c.fillRect(0, h * 0.67, w, h * 0.33);
    c.fillStyle = "#5e7a3e"; for (let i = 0; i < 40; i++) { c.beginPath(); c.arc(Math.random() * w, h * 0.7 + Math.random() * h * 0.3, 6 + Math.random() * 8, 0, 7); c.fill(); }
    c.fillStyle = "#a9a39a"; c.beginPath(); c.arc(w * 0.5, h * 0.22, w * 0.07, 0, 7); c.fill();
    c.fillRect(w * 0.42, h * 0.27, w * 0.16, h * 0.32); c.fillStyle = "#8f877c"; c.fillRect(w * 0.38, h * 0.58, w * 0.24, h * 0.06);
  }, null);
  framed(3.06, 1.08, 0.5, 0.7, bigArt);
  framed(3.53, 1.08, 0.3, 0.4, smallArt, 0.035);
  // six polaroids in two rows above the small frame
  const borders = ["#f2f0ea", "#f2f0ea", "#e9718f", "#e88a2e", "#d9473a", "#e88a2e"];
  borders.forEach((col, i) => {
    const t = canvasTex(86, 100, (c, w, h) => {
      c.fillStyle = col; c.fillRect(0, 0, w, h);
      const r = rnd(30 + i * 7), g = c.createLinearGradient(0, 0, 0, 72);
      g.addColorStop(0, ["#3a4a5a", "#9aa7b8", "#5a4636", "#2c3d2c", "#3f6a3a", "#2a2f38"][i]); g.addColorStop(1, "#1d1c1b");
      c.fillStyle = g; c.fillRect(7, 7, 72, 70);
      c.fillStyle = "rgba(230,200,170,0.65)"; c.beginPath(); c.arc(25 + r() * 36, 35 + r() * 20, 8 + r() * 6, 0, 7); c.fill();
    }, null);
    const q = new THREE.Mesh(new THREE.PlaneGeometry(0.086, 0.1), new THREE.MeshStandardMaterial({ map: t, roughness: 0.5 }));
    q.position.set(3.42 + (i % 3) * 0.108, i < 3 ? 1.69 : 1.56, 0.003); root.add(q);
  });
  // round VARMBLIXT-style LED wall lamp, glowing pink-orange, cable down to the desk
  const lampTex = canvasTex(256, 256, (c, w, h) => {
    const g = c.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
    g.addColorStop(0, "#ff6a2a"); g.addColorStop(0.7, "#f04e12"); g.addColorStop(1, "#c93c08");
    c.fillStyle = g; c.beginPath(); c.arc(w / 2, h / 2, w / 2 - 1, 0, 7); c.fill();
  }, null);
  // doughnut: a puffy glowing ring (Ø30 cm) around a recessed centre with the orange dot
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.105, 0.046, 32, 72),
    M.orangeGlow);   // same glowing orange as the floor lamp by the TV
  ring.scale.z = 0.85; ring.position.set(3.9, 1.28, 0.042); root.add(ring);
  const hub = new THREE.Mesh(new THREE.CircleGeometry(0.07, 48), new THREE.MeshBasicMaterial({ map: lampTex, toneMapped: false }));
  hub.position.set(3.9, 1.28, 0.02); root.add(hub);
  const dot = new THREE.Mesh(new THREE.SphereGeometry(0.016, 20, 12), new THREE.MeshBasicMaterial({ color: 0xffb070, toneMapped: false }));
  dot.scale.z = 0.5; dot.position.set(3.9, 1.28, 0.026); root.add(dot);
  const lampLight = new THREE.PointLight(0xff6a2a, 2.2, 3, 2); lampLight.position.set(3.9, 1.28, 0.25); lightsGroup.add(lampLight);
  box(3.915, 3.922, DH, 1.13, 0.008, 0.015, M.plastic, { cast: false });

  /* ----- cantilever desk chair: chrome tube, honey-beech seat and back rims, patterned beige fabric ----- */
  const chair = new THREE.Group(); chair.position.set(3.45, 0, 0.88); chair.rotation.y = Math.PI + 0.12; root.add(chair);
  const ctube = (pts) => {
    const g = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts.map(p => new THREE.Vector3(...p)), false, "catmullrom", 0.2), 80, 0.0115, 10, false);
    const m = new THREE.Mesh(g, M.chrome); m.castShadow = true; chair.add(m);
  };
  [-0.215, 0.215].forEach(x => ctube([[x, 0.92, -0.25], [x, 0.6, -0.235], [x, 0.47, -0.21], [x, 0.43, -0.15], [x, 0.43, 0.17], [x, 0.4, 0.235], [x, 0.24, 0.265], [x, 0.06, 0.245], [x, 0.015, 0.18], [x, 0.015, -0.15], [x * 0.9, 0.015, -0.205]]));
  ctube([[-0.19, 0.015, -0.205], [-0.12, 0.015, -0.225], [0, 0.015, -0.23], [0.12, 0.015, -0.225], [0.19, 0.015, -0.205]]);
  rb(0.47, 0.045, 0.45, 0.02, M.beech, 0, 0.465, 0.01, chair);                    // seat rim
  rb(0.41, 0.012, 0.39, 0.005, M.chairFabric, 0, 0.489, 0.01, chair);             // seat fabric
  const back = rb(0.45, 0.33, 0.035, 0.03, M.beech, 0, 0.76, -0.245, chair); back.rotation.x = -0.12;
  const backF = rb(0.39, 0.27, 0.012, 0.005, M.chairFabric, 0, 0.76, -0.225, chair); backF.rotation.x = -0.12;

  /* ----- opposite the desk: vertical double Murphy bed cabinet (folded up) and a bamboo floor lamp ----- */
  const BX0 = 2.3, BX1 = 4.05, BZ1 = 3.6, BZ0 = 3.15, BH = 2.08;
  box(BX0, BX0 + 0.03, 0, BH, BZ0, BZ1, M.deskWhite);                              // side panels
  box(BX1 - 0.03, BX1, 0, BH, BZ0, BZ1, M.deskWhite);
  box(BX0, BX1, BH - 0.06, BH, BZ0, BZ1, M.deskWhite);                             // top
  box(BX0, BX1, 0, 0.04, BZ0, BZ1, M.deskWhite);                                   // plinth
  box(BX0 + 0.03, BX1 - 0.03, 0.04, BH - 0.06, BZ0 + 0.06, BZ0 + 0.08, M.cavity, { cast: false });  // shadow gap behind the bed front
  box(BX0 + 0.05, BX1 - 0.05, 0.06, BH - 0.1, BZ0 + 0.01, BZ0 + 0.06, M.deskWhite);  // bed front (two panels)
  box((BX0 + BX1) / 2 - 0.002, (BX0 + BX1) / 2 + 0.002, 0.07, BH - 0.11, BZ0 + 0.008, BZ0 + 0.011, M.seam, { cast: false });
  // bamboo cylinder lamp, glowing orange from inside
  const bl = new THREE.Group(); bl.position.set(4.2, 0, 3.33); root.add(bl);
  const core = new THREE.Mesh(new THREE.CylinderGeometry(0.085, 0.085, 1.0, 24), M.orangeGlow); core.position.y = 0.56; bl.add(core);
  for (let k = 0; k < 26; k++) {
    const a = k / 26 * Math.PI * 2, s = new THREE.Mesh(new THREE.BoxGeometry(0.014, 1.08, 0.006), M.bamboo);
    s.position.set(Math.cos(a) * 0.105, 0.58, Math.sin(a) * 0.105); s.rotation.y = -a + Math.PI / 2; s.castShadow = true; bl.add(s);
  }
  [0.06, 0.34, 0.62, 0.9, 1.11].forEach(y => { const r = new THREE.Mesh(new THREE.TorusGeometry(0.108, 0.006, 6, 40).rotateX(Math.PI / 2), M.bamboo); r.position.y = y; bl.add(r); });
  const blBase = new THREE.Mesh(new THREE.CylinderGeometry(0.11, 0.11, 0.03, 32), M.bamboo); blBase.position.y = 0.015; bl.add(blBase);
  const blLight = new THREE.PointLight(0xff6a2a, 2.4, 3.2, 2); blLight.position.set(4.2, 0.8, 3.15); lightsGroup.add(blLight);

  /* ===== entry: glazed aluminium entrance (door, side light, transoms) and oak-rimmed mirror ===== */
  const ax = (z0, z1, y0, y1, d = 0.07, m = M.alu) => box(12.375 - d / 2, 12.375 + d / 2, y0, y1, z0, z1, m);
  const pane = (z0, z1, y0, y1, m = M.glass) => { const g = new THREE.Mesh(new THREE.PlaneGeometry(z1 - z0, y1 - y0), m); g.rotation.y = Math.PI / 2; g.position.set(12.375, (y0 + y1) / 2, (z0 + z1) / 2); g.renderOrder = 2; root.add(g); };
  ax(2.17, 2.22, 0, 2.72); ax(3.10, 3.15, 0, 2.72); ax(2.17, 3.15, 2.67, 2.72); ax(2.17, 3.15, 2.1, 2.16);   // outer frame + transom bar
  ax(2.93, 2.97, 0, 2.72);                                                                                    // door / side-light mullion
  // door leaf z 2.22–2.93: stiles, rails, centre bar; glass above, grey panels below
  ax(2.22, 2.29, 0, 2.1, 0.06); ax(2.86, 2.93, 0, 2.1, 0.06); ax(2.22, 2.93, 2.03, 2.1, 0.06); ax(2.22, 2.93, 0, 0.1, 0.06);
  ax(2.22, 2.93, 0.92, 1.04, 0.06); ax(2.555, 2.595, 0.1, 2.03, 0.06);
  pane(2.29, 2.555, 1.04, 2.03); pane(2.595, 2.86, 1.04, 2.03);
  ax(2.29, 2.555, 0.1, 0.92, 0.02, M.aluPanel); ax(2.595, 2.86, 0.1, 0.92, 0.02, M.aluPanel);
  pane(2.97, 3.10, 0, 2.1);                                                                                   // narrow side light
  pane(2.22, 2.575, 2.16, 2.67); pane(2.575, 2.93, 2.16, 2.67); pane(2.97, 3.10, 2.16, 2.67);
  ax(2.555, 2.595, 2.16, 2.67);
  const lever = new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.011, 0.13, 10).rotateX(Math.PI / 2), M.steel);
  lever.position.set(12.30, 1.05, 2.83); root.add(lever);
  box(12.31, 12.33, 0.95, 1.12, 2.86, 2.9, M.steel, { cast: false });
  box(12.25, 12.33, 2.11, 2.18, 2.3, 2.62, M.alu);                                                           // door closer
  box(12.27, 12.29, 2.0, 2.12, 2.36, 2.39, M.alu);
  // mirror: deep oak rim, rounded corners, 50 × 60 cm, on the wall to the right when coming in
  function rrect(w, h, r) {
    const s = new THREE.Shape(), x = -w / 2, y = -h / 2;
    s.moveTo(x + r, y); s.lineTo(x + w - r, y); s.quadraticCurveTo(x + w, y, x + w, y + r); s.lineTo(x + w, y + h - r);
    s.quadraticCurveTo(x + w, y + h, x + w - r, y + h); s.lineTo(x + r, y + h); s.quadraticCurveTo(x, y + h, x, y + h - r);
    s.lineTo(x, y + r); s.quadraticCurveTo(x, y, x + r, y); return s;
  }
  const rimShape = rrect(0.5, 0.6, 0.09); rimShape.holes.push(new THREE.Path(rrect(0.45, 0.55, 0.07).getPoints(24)));
  const rim = new THREE.Mesh(new THREE.ExtrudeGeometry(rimShape, { depth: 0.1, bevelEnabled: true, bevelThickness: 0.004, bevelSize: 0.004, bevelSegments: 2, curveSegments: 16 }), M.oak);
  rim.position.set(11.72, 1.55, 2.17); rim.castShadow = true; root.add(rim);
  const mir = new THREE.Mesh(new THREE.ShapeGeometry(rrect(0.45, 0.55, 0.07)), M.mirror); mir.position.set(11.72, 1.55, 2.175); root.add(mir);
  const sill = new THREE.Mesh(new THREE.ShapeGeometry(rrect(0.45, 0.55, 0.07)), M.oak); sill.visible = false; root.add(sill);

  /* ===== kitchen run on the left when coming in (south wall, z = 4.74), from the photos ===== */
  const KZ = 4.74, KF = 4.14;
  // tall fridge-freezer column by the bathroom door, three fronts with long black bar handles
  box(8.47, 9.07, 0, 0.08, 4.16, KZ, M.plinth);
  box(8.47, 9.07, 0.08, 2.25, KF, KZ, M.front);
  [1.3, 1.82].forEach(y => box(8.47, 9.07, y - 0.003, y + 0.003, KF - 0.003, KF, M.seam, { cast: false }));
  [[0.8, 1.25], [1.42, 1.76], [1.9, 2.18]].forEach(([a, b]) => box(9.0, 9.016, a, b, KF - 0.03, KF - 0.012, M.metalBlack));
  // base run: three doors (sink in the last), built-in oven, washing machine
  box(9.07, 10.27, 0, 0.1, 4.18, KZ, M.plinth);
  box(9.07, 10.27, 0.1, 0.88, KF, KZ, M.front);
  [9.47, 9.87].forEach(x => box(x - 0.003, x + 0.003, 0.1, 0.88, KF - 0.003, KF, M.seam, { cast: false }));
  box(10.27, 10.87, 0, 0.1, 4.18, KZ, M.front);
  box(10.27, 10.87, 0.1, 0.88, KF + 0.02, KZ, M.steel);
  box(10.31, 10.83, 0.14, 0.7, KF + 0.012, KF + 0.02, M.blackGlass, { cast: false });
  box(10.34, 10.8, 0.66, 0.675, KF - 0.01, KF + 0.012, M.steel);
  box(10.45, 10.62, 0.76, 0.82, KF + 0.015, KF + 0.02, M.blackGlass, { cast: false });
  box(10.87, 11.47, 0, 0.86, KF + 0.02, KZ, M.white);                                   // washing machine
  const wd = new THREE.Mesh(new THREE.TorusGeometry(0.17, 0.03, 12, 40), M.steel); wd.position.set(11.17, 0.42, KF + 0.01); root.add(wd);
  const wg = new THREE.Mesh(new THREE.CircleGeometry(0.155, 40), M.blackGlass); wg.position.set(11.17, 0.42, KF + 0.015); root.add(wg);
  box(10.92, 11.42, 0.74, 0.82, KF + 0.015, KF + 0.022, M.seam, { cast: false });
  box(9.07, 11.54, 0.88, 0.92, KF - 0.02, KZ, M.counter);                               // dark stone worktop
  box(9.07, 11.54, 0.92, 1.4, KZ - 0.015, KZ, M.splashDark, { cast: false });            // black splashback
  box(9.57, 10.07, 0.905, 0.922, 4.24, 4.62, M.steel, { cast: false });                 // sink
  const ktap = new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.016, 0.32, 12), M.metalBlack); ktap.position.set(9.82, 1.08, 4.68); root.add(ktap);
  const spout = new THREE.Mesh(new THREE.TorusGeometry(0.09, 0.012, 8, 20, Math.PI), M.metalBlack); spout.rotation.y = Math.PI / 2; spout.position.set(9.82, 1.24, 4.6); root.add(spout);
  box(10.27, 10.87, 0.92, 0.926, 4.2, 4.7, M.blackGlass, { cast: false });                // induction hob
  // wall cabinets, handleless, plus built-in microwave under a short cabinet
  box(9.07, 10.87, 1.4, 2.2, 4.39, KZ, M.front);
  [9.52, 9.97, 10.42].forEach(x => box(x - 0.003, x + 0.003, 1.4, 2.2, 4.387, 4.39, M.seam, { cast: false }));
  box(10.87, 11.47, 1.42, 1.82, 4.39, KZ, M.steel);
  box(10.9, 11.44, 1.44, 1.76, 4.385, 4.39, M.blackGlass, { cast: false });
  box(10.87, 11.47, 1.82, 2.2, 4.39, KZ, M.front);
  box(9.07, 11.47, 1.398, 1.402, 4.4, 4.72, M.led, { cast: false });
  // the nook at the end of the run: worktop carried in, small two-tier spice shelf
  box(11.11, 11.74, 0.88, 0.92, 3.75, 4.23, M.counter);
  box(11.4, 11.72, 0.92, 0.94, 3.8, 4.18, M.oak); box(11.4, 11.72, 1.1, 1.12, 3.8, 4.18, M.oak);
  [3.81, 4.15].forEach(z => box(11.4, 11.72, 0.94, 1.1, z, z + 0.02, M.oak));
  [[11.5, 3.9], [11.5, 4.0], [11.5, 4.1], [11.62, 3.95], [11.62, 4.07]].forEach(([x, z], i) => {
    const j = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 0.08, 14), i % 2 ? M.jarDark : M.jarLight); j.position.set(x, 1.16, z); root.add(j);
  });

  /* ===== to the right when coming in: closet in the nook, drop-leaf table, three card-deck ledges ===== */
  box(11.19, 11.74, 0, 0.05, 1.66, 2.07, M.plinth);
  box(11.19, 11.74, 0.05, 2.2, 1.64, 2.07, M.front);
  [1.0, 1.55].forEach(y => box(11.187, 11.19, y - 0.004, y + 0.004, 1.64, 2.07, M.seam, { cast: false }));
  // white drop-leaf table, leaf folded down (table + ledges shifted 0.8 m towards the entry)
  const tableStart = root.children.length;
  box(8.75, 9.75, 0.72, 0.75, 1.2, 1.5, M.deskWhite);
  box(8.75, 9.75, 0.15, 0.715, 1.5, 1.52, M.deskWhite);
  box(8.77, 8.79, 0, 0.72, 1.22, 1.48, M.deskWhite); box(9.71, 9.73, 0, 0.72, 1.22, 1.48, M.deskWhite);
  [8.9, 9.6].forEach(x => box(x - 0.02, x + 0.02, 0.705, 0.725, 1.515, 1.522, M.steel, { cast: false }));
  // black picture ledges filled with playing-card decks (spines out)
  const deckCols = [0xb3262a, 0x1f3f8a, 0x111111, 0xf2efe6, 0xd96aa0, 0xc9a24a, 0x2a7a5a, 0x7a1f3d, 0x3a3f8f, 0xe8e2d4, 0xd14b2c, 0x5a2a6a, 0x0f5c7a, 0xf0c419, 0x2b2b2b];
  const deckMats = deckCols.map(c => new THREE.MeshStandardMaterial({ color: c, roughness: 0.5 }));
  const dr = rnd(321);
  function ledge(x0, x1, y, fills) {
    box(x0, x1, y, y + 0.015, 1.2, 1.3, M.metalBlack);
    box(x0, x1, y, y + 0.04, 1.29, 1.3, M.metalBlack);
    box(x0, x1, y - 0.03, y + 0.015, 1.2, 1.215, M.metalBlack);
    fills.forEach(([a, b]) => { for (let x = a; x + 0.019 < b; x += 0.0205) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.019, 0.088, 0.063), deckMats[Math.floor(dr() * deckMats.length)]);
      m.position.set(x + 0.0095, y + 0.015 + 0.044, 1.245); m.castShadow = true; root.add(m);
    } });
  }
  ledge(8.61, 9.65, 1.32, [[8.66, 8.86], [9.02, 9.52]]);
  ledge(8.52, 9.07, 1.6, [[8.54, 8.98]]);
  ledge(9.2, 9.72, 1.79, [[9.22, 9.62]]);
  root.children.slice(tableStart).forEach(o => { o.position.x += 0.8; });

  /* ===== bathroom ===== */
  // corner shower opposite the door: marble-look tiles, white tray, glass on two sides
  box(6.85, 6.865, 0, 2.2, 2.9, 3.7, M.marble, { cast: false });
  box(6.85, 7.65, 0, 2.2, 2.9, 2.915, M.marble, { cast: false });
  box(6.865, 7.65, 0, 0.05, 2.915, 3.7, M.porcelain);
  const drain = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.004, 20), M.steel); drain.position.set(7.25, 0.052, 3.3); root.add(drain);
  box(7.64, 7.66, 0.05, 1.95, 2.92, 3.7, M.glass, { cast: false });
  box(6.865, 7.66, 0.05, 1.95, 3.69, 3.71, M.glass, { cast: false });
  [[7.64, 7.66, 2.915, 2.935], [7.64, 7.665, 3.69, 3.715], [6.865, 6.885, 3.69, 3.71]].forEach(([a, b, c, d]) => box(a, b, 0.05, 1.95, c, d, M.steel));
  box(7.64, 7.66, 1.94, 1.96, 2.92, 3.71, M.steel); box(6.87, 7.66, 1.94, 1.96, 3.69, 3.71, M.steel);
  const rail = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.9, 10), M.steel); rail.position.set(6.89, 1.55, 3.3); root.add(rail);
  const head = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.035, 0.03, 20), M.steel); head.rotation.z = -1.0; head.position.set(6.95, 2.02, 3.3); root.add(head);
  box(6.865, 6.91, 1.0, 1.06, 3.22, 3.38, M.steel);
  // close-coupled toilet in the wall nook, next to the shower
  box(6.68, 6.86, 0.4, 0.8, 4.18, 4.52, M.porcelain);
  const bowl = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.15, 0.4, 28).scale(1.3, 1, 1), M.porcelain);
  bowl.position.set(7.05, 0.2, 4.35); bowl.castShadow = true; root.add(bowl);
  const seat = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.19, 0.03, 28).scale(1.3, 1, 1), M.porcelain); seat.position.set(7.05, 0.415, 4.35); root.add(seat);
  // mint metal rolling cart, three trays
  const cx0 = 6.62, cx1 = 7.07, cz0 = 4.7, cz1 = 5.12;
  [[cx0, cz0], [cx1, cz0], [cx0, cz1], [cx1, cz1]].forEach(([x, z]) => {
    box(x - 0.01, x + 0.01, 0.05, 0.78, z - 0.01, z + 0.01, M.mint);
    const w = new THREE.Mesh(new THREE.SphereGeometry(0.025, 10, 8), M.black); w.position.set(x, 0.025, z); root.add(w);
  });
  [0.12, 0.42, 0.72].forEach(y => {
    box(cx0, cx1, y, y + 0.01, cz0, cz1, M.mint);
    box(cx0, cx1, y, y + 0.06, cz0, cz0 + 0.008, M.mint); box(cx0, cx1, y, y + 0.06, cz1 - 0.008, cz1, M.mint);
    box(cx0, cx0 + 0.008, y, y + 0.06, cz0, cz1, M.mint); box(cx1 - 0.008, cx1, y, y + 0.06, cz0, cz1, M.mint);
  });
  // wall-hung washbasin with chrome tap, three-door mirror cabinet with a light bar above
  rb(0.52, 0.15, 0.42, 0.05, M.porcelain, 7.78, 0.79, 4.94, root);
  const basin = new THREE.Mesh(new THREE.CircleGeometry(0.17, 32).scale(1.2, 0.85, 1), M.basinIn); basin.rotation.x = -Math.PI / 2; basin.position.set(7.78, 0.866, 4.9); root.add(basin);
  const trap = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.025, 0.4, 12), M.porcelain); trap.position.set(7.78, 0.5, 5.05); root.add(trap);
  box(7.76, 7.8, 0.865, 0.96, 5.07, 5.11, M.steel); box(7.765, 7.795, 0.94, 0.955, 4.98, 5.1, M.steel);
  box(7.4, 8.16, 1.35, 1.95, 5.0, 5.15, M.white);
  [[7.4, 7.65], [7.655, 7.905], [7.91, 8.16]].forEach(([a, b]) => box(a + 0.003, b - 0.003, 1.355, 1.945, 4.995, 5.0, M.mirror, { cast: false }));
  box(7.55, 8.0, 2.0, 2.025, 5.02, 5.13, M.steel);
  box(7.56, 7.99, 1.995, 2.0, 5.03, 5.12, M.led, { cast: false });
  // two hooks on the wall opposite the toilet, hand towels hanging from them
  [[4.3, M.towelBlue, 0.62], [4.47, M.towelPink, 0.7]].forEach(([z, mat, len]) => {
    const hk = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.05, 10).rotateZ(Math.PI / 2), M.steel); hk.position.set(8.325, 1.55, z); root.add(hk);
    rb(0.025, len, 0.16, 0.01, mat, 8.31, 1.55 - len / 2 + 0.02, z, root);
  });

  /* ===== entry: black shoe bench with chrome rails, mesh umbrella stand, two jackets on hooks (against the slatted wall) ===== */
  const SB = { x0: 11.22, x1: 12.03, z0: 3.27, z1: 3.6 };
  box(SB.x0, SB.x1, 0.47, 0.5, SB.z0, SB.z1, M.blackWood);                               // bench top
  [[SB.x0, SB.x0 + 0.04], [SB.x1 - 0.04, SB.x1]].forEach(([a, b]) => box(a, b, 0, 0.47, SB.z0, SB.z1, M.blackWood));
  [0.12, 0.25, 0.37].forEach(y => [SB.z0 + 0.05, SB.z1 - 0.06].forEach(z => {
    const r = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, SB.x1 - SB.x0 - 0.08, 10).rotateZ(Math.PI / 2), M.chrome);
    r.position.set((SB.x0 + SB.x1) / 2, y + (z > 3.4 ? 0.04 : 0), z); root.add(r);
  }));
  // umbrella stand by the glass door
  const us = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.09, 0.5, 24, 1, true), M.meshBlack);
  us.position.set(12.17, 0.25, 3.48); us.castShadow = true; root.add(us);
  const usb = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.09, 0.02, 24), M.metalBlack); usb.position.set(12.17, 0.01, 3.48); root.add(usb);
  const umb = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.012, 0.75, 10), M.umbrella); umb.position.set(12.15, 0.42, 3.47); umb.rotation.z = 0.08; root.add(umb);
  const crook = new THREE.Mesh(new THREE.TorusGeometry(0.035, 0.008, 6, 14, Math.PI), M.metalBlack); crook.position.set(12.12, 0.82, 3.47); root.add(crook);
  // hook rail and two hanging jackets
  box(11.3, 11.95, 1.74, 1.78, 3.585, 3.605, M.metalBlack);
  function jacket(x, mat) {
    const g = new THREE.Group(); g.position.set(x, 0, 3.5); root.add(g);
    const hk = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.07, 8).rotateX(Math.PI / 2), M.metalBlack); hk.position.set(0, 1.76, 0.07); g.add(hk);
    const body = new THREE.Mesh(new RoundedBoxGeometry(0.4, 0.78, 0.12, 4, 0.05), mat);
    body.geometry.translate(0, -0.4, 0); const pos = body.geometry.attributes.position;
    for (let i = 0; i < pos.count; i++) { const t = -pos.getY(i) / 0.8; pos.setX(i, pos.getX(i) * (t < 0.12 ? 0.6 + t * 3.3 : 1 - 0.08 * Math.sin(t * Math.PI))); }
    body.geometry.computeVertexNormals(); body.position.set(0, 1.74, 0.02); body.castShadow = true; g.add(body);
    [-1, 1].forEach(sd => { const sl = rb(0.08, 0.6, 0.09, 0.035, mat, sd * 0.195, 1.36, 0.035, g); sl.rotation.z = sd * 0.08; });
    rb(0.16, 0.06, 0.1, 0.025, mat, 0, 1.73, 0.04, g);                                   // collar
  }
  jacket(11.47, M.coatBlack);
  jacket(11.83, M.quilted);

  /* ===== corridor to the living room: white panel-door wardrobe with a mirror door on the left, two framed posters on the right ===== */
  const WX0 = 6.42, WX1 = 8.42, WZ0 = 2.22, WZ1 = 2.8, WH = 2.36;
  box(WX0, 7.92, 0, WH, WZ0, WZ1, M.deskWhite);
  const MZ0 = 2.45;                                                     // mirror unit is shallower (35 cm)
  box(7.92, WX1, 0, WH, MZ0, WZ1, M.deskWhite);
  const doorsW = [[7.92, 8.42, true], [7.42, 7.92, false], [6.92, 7.42, false], [6.42, 6.92, false]];
  doorsW.forEach(([a, b, mirror], i) => {
    const FZ = mirror ? MZ0 : WZ0;
    box(a + 0.003, b - 0.003, 0.06, WH - 0.04, FZ - 0.022, FZ, M.white);               // door slab
    if (mirror) box(a + 0.04, b - 0.04, 0.12, WH - 0.1, FZ - 0.024, FZ - 0.022, M.mirror, { cast: false });
    else {
      // frame-and-panel face: stiles plus three panels
      box(a + 0.003, a + 0.075, 0.06, WH - 0.04, WZ0 - 0.034, WZ0 - 0.022, M.white); box(b - 0.075, b - 0.003, 0.06, WH - 0.04, WZ0 - 0.034, WZ0 - 0.022, M.white);
      [0.06, 0.82, 1.56, WH - 0.11].forEach(y => box(a + 0.003, b - 0.003, y, y + 0.07, WZ0 - 0.034, WZ0 - 0.022, M.white));
    }
    const kx = i % 2 === 0 ? a + 0.05 : b - 0.05;
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.018, 14, 10), M.bronze); knob.position.set(kx, 1.05, FZ - 0.05); knob.castShadow = true; root.add(knob);
    const kstem = new THREE.Mesh(new THREE.CylinderGeometry(0.007, 0.007, 0.03, 8).rotateX(Math.PI / 2), M.bronze); kstem.position.set(kx, 1.05, FZ - 0.03); root.add(kstem);
  });
  // two posters in thin silver frames (neutral placeholder prints)
  function poster(cx, y0, w, h, art) {
    const g = new THREE.Group(); g.position.set(cx, y0 + h / 2, 1.2); root.add(g);
    const f = 0.015, d = 0.02;
    [[0, h / 2 - f / 2, w, f], [0, -h / 2 + f / 2, w, f], [-w / 2 + f / 2, 0, f, h], [w / 2 - f / 2, 0, f, h]].forEach(([x, y, a, b]) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(a, b, d), M.alu); m.position.set(x, y, d / 2); g.add(m);
    });
    const pic = new THREE.Mesh(new THREE.PlaneGeometry(w - 2 * f, h - 2 * f), new THREE.MeshStandardMaterial({ map: art, roughness: 0.4 })); pic.position.z = 0.006; g.add(pic);
    const gl = new THREE.Mesh(new THREE.PlaneGeometry(w - 2 * f, h - 2 * f), M.glass); gl.position.z = 0.016; g.add(gl);
  }
  const printA = canvasTex(300, 450, (c, w, h) => {
    const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#e9e8e4"); g.addColorStop(1, "#9a9893");
    c.fillStyle = g; c.fillRect(0, 0, w, h); const r = rnd(5150);
    for (let i = 0; i < 14; i++) { c.fillStyle = `rgba(40,40,40,${0.1 + r() * 0.35})`; c.fillRect(r() * w, h * (0.2 + r() * 0.5), 10 + r() * 60, h * (0.1 + r() * 0.5)); }
    c.fillStyle = "rgba(30,30,30,0.6)"; c.fillRect(0, h * 0.86, w, h * 0.14);
  }, null);
  const printB = canvasTex(350, 500, (c, w, h) => {
    const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#c9d6e0"); g.addColorStop(0.55, "#e8e4da"); g.addColorStop(1, "#8e8a84");
    c.fillStyle = g; c.fillRect(0, 0, w, h); const r = rnd(5151);
    c.fillStyle = "#d8d2c6"; c.fillRect(0, h * 0.25, w * 0.22, h * 0.45); c.fillRect(w * 0.78, h * 0.18, w * 0.22, h * 0.52);
    for (let i = 0; i < 10; i++) { c.fillStyle = `rgba(${150 + r() * 60},${130 + r() * 40},${110 + r() * 30},0.5)`; c.beginPath(); c.arc(r() * w, h * (0.3 + r() * 0.5), 8 + r() * 30, 0, 7); c.fill(); }
    c.fillStyle = "rgba(60,58,55,0.55)"; c.fillRect(0, h * 0.7, w, h * 0.3);
  }, null);
  poster(6.88, 1.06, 0.61, 0.91, printA);
  poster(7.85, 0.86, 0.7, 1.0, printB);

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
  // herbs and flowers for the planters: lavender + rosemary, basil + rosemary, chilli + flowers
  const pr = rnd(808);
  const leafMat = (c) => new THREE.MeshStandardMaterial({ color: c, roughness: 0.9, flatShading: true });
  const PM = { sage: leafMat(0x8ea38c), rosemary: leafMat(0x5f7d5a), basil: leafMat(0x5c9a3a), leaf: leafMat(0x4a7a36),
    lav: leafMat(0x8a72c4), red: leafMat(0xd4352a), pink: leafMat(0xe07aa8), yellow: leafMat(0xf2c84b), white: leafMat(0xf4f1ea), stem: leafMat(0x5a7a40) };
  function bush(g, mat, n, r0, r1, h0, h1) {
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.IcosahedronGeometry(r0 + pr() * (r1 - r0), 0), mat);
      m.position.set((pr() - 0.5) * 0.26, h0 + pr() * (h1 - h0), (pr() - 0.5) * 0.74); m.rotation.set(pr() * 3, pr() * 3, pr() * 3);
      m.scale.y = 0.8 + pr() * 0.5; m.castShadow = true; g.add(m);
    }
  }
  function spikes(g, stemMat, tipMat, n, len, tipLen, zr = 0.36) {
    for (let i = 0; i < n; i++) {
      const x = (pr() - 0.5) * 0.24, z = (pr() - 0.5) * 2 * zr, l = len * (0.75 + pr() * 0.5), tilt = (pr() - 0.5) * 0.5;
      const st = new THREE.Mesh(new THREE.CylinderGeometry(0.003, 0.004, l, 5), stemMat); st.position.set(x, 0.39 + l / 2, z); st.rotation.set(tilt, 0, (pr() - 0.5) * 0.5); g.add(st);
      const tip = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.006, tipLen, 6), tipMat); tip.position.set(x + st.rotation.z * -l * 0.5, 0.39 + l + tipLen / 2 - 0.02, z + tilt * l * 0.5); g.add(tip);
    }
  }
  function blooms(g, mats, n, hmin, hmax) {
    for (let i = 0; i < n; i++) {
      const x = (pr() - 0.5) * 0.24, z = (pr() - 0.5) * 0.72, h = hmin + pr() * (hmax - hmin);
      const st = new THREE.Mesh(new THREE.CylinderGeometry(0.003, 0.003, h - 0.39, 5), PM.stem); st.position.set(x, (h + 0.39) / 2, z); g.add(st);
      const f = new THREE.Mesh(new THREE.IcosahedronGeometry(0.018 + pr() * 0.012, 0), mats[i % mats.length]); f.position.set(x, h, z); f.scale.y = 0.6; g.add(f);
    }
  }
  function plantsFor(g, k) {
    if (k === 1) { bush(g, PM.sage, 70, 0.025, 0.05, 0.4, 0.6); spikes(g, PM.sage, PM.lav, 40, 0.3, 0.07); blooms(g, [PM.pink, PM.white], 6, 0.5, 0.6); }
    else if (k === 3) { bush(g, PM.basil, 60, 0.028, 0.055, 0.42, 0.68); spikes(g, PM.rosemary, PM.rosemary, 24, 0.32, 0.12); bush(g, PM.leaf, 20, 0.025, 0.045, 0.6, 0.78); }
    else { bush(g, PM.leaf, 70, 0.028, 0.055, 0.42, 0.72); blooms(g, [PM.red, PM.red, PM.yellow], 18, 0.55, 0.78); blooms(g, [PM.pink, PM.white, PM.red], 10, 0.62, 0.85); }
  }
  // ÄSPAREN planters, 89 × 38 × 40 cm, evenly spaced along the far edge
  const TL = TZ1 - TZ0;
  [1, 3, 5].forEach(k => {
    const g = new THREE.Group(), zc = TZ0 + TL * k / 6;
    g.position.set(TX + 0.27, DY, zc); root.add(g);
    rb(0.38, 0.30, 0.89, 0.05, M.planter, 0, 0.25, 0, g);              // tub
    [[-0.13, -0.38], [0.13, -0.38], [-0.13, 0.38], [0.13, 0.38]].forEach(([x, z]) => rb(0.1, 0.12, 0.11, 0.03, M.planter, x, 0.06, z, g));
    rb(0.34, 0.02, 0.83, 0.008, M.soil, 0, 0.375, 0, g);              // soil line
    plantsFor(g, k);
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



  // ceiling lights (flush discs)
  [[2.9, 1.8], [8.9, 2.0], [10.1, 3.6], [7.5, 4.0], [11.75, 2.9]].forEach(([x, z], i) => {
    const d = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.14, 0.03, 32), M.lamp);
    d.position.set(x, H - 0.015, z); ceiling.add(d);
    const L = new THREE.PointLight(0xffe7c8, i === 0 ? 6 : 3.5, 7, 2);
    L.position.set(x, H - 0.25, z); lightsGroup.add(L);
  });

  applyToggles();
}
// ==== MODEL END ====

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



/* ---------- video frames ---------- */

// Only the flat goes in the video: the original's sky dome, clouds, stars, fog, rain and
// snow stay hidden (the app draws its own sky, rain and snow).
function afterAtmosphere() {
  scene.fog = null;
  scene.background = null;
  sky.visible = false;
  starMat.visible = false;
  clouds.forEach(s => { s.visible = false; });
  rain.visible = false;
  snow.visible = false;
  renderer.shadowMap.needsUpdate = true;
}

// The model is a few hundred small meshes, and nothing in it moves: bake every mesh into
// world space and merge them per material, which renders the same picture much faster.
function mergeStatic() {
  root.remove(ceiling);
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
}
mergeStatic();

// The launcher's camera: a steep view from above, turning around the middle of the flat and
// terrace. fov is the vertical angle of the whole TV screen; the video holds only the part
// of the screen the turning flat ever covers (VIEW, as fractions of the screen height,
// centred on the flat), so it's wider than tall.
const bounds = new THREE.Box3().setFromObject(root);
const target = new THREE.Vector3((bounds.min.x + bounds.max.x) / 2, 0, (bounds.min.z + bounds.max.z) / 2);
const RADIUS = 12.2, HEIGHT = 21.2, FOV = 42;
const VIEW = { w: 1.04, h: 1.04 * 880 / 960 };   // matches the video's 960×880 picture

/** Lighting for one video: when (ISO time, sets the sun) and weather code, and frame size. */
window.renderSetup = ({ when, code, width, height, view }) => {
  if (view) Object.assign(VIEW, view);   // for measuring
  window.launcherNow = when;
  weather = { ...weather, code };
  applyAtmosphere();
  renderer.setSize(width, height, false);
  // The screen's camera, cropped to VIEW around its centre.
  camera.fov = THREE.MathUtils.radToDeg(2 * Math.atan(Math.tan(THREE.MathUtils.degToRad(FOV / 2)) * VIEW.h));
  camera.aspect = VIEW.w / VIEW.h;
  camera.updateProjectionMatrix();
};
window.renderInfo = () => ({ target: target.toArray(), bounds: [bounds.min.toArray(), bounds.max.toArray()], view: VIEW, fov: FOV });

/**
 * Renders the flat at one angle and returns it scaled down to ow × oh pixels, as base64
 * RGBA (not premultiplied). Rendering larger than the output smooths edges and fine detail.
 */
const outCanvas = document.createElement("canvas");
const out2d = outCanvas.getContext("2d", { willReadFrequently: true });
window.renderFrame = (angle, ow, oh) => {
  camera.position.set(target.x + Math.sin(angle) * RADIUS, HEIGHT, target.z + Math.cos(angle) * RADIUS);
  camera.lookAt(target);
  renderer.render(scene, camera);
  if (outCanvas.width !== ow || outCanvas.height !== oh) { outCanvas.width = ow; outCanvas.height = oh; }
  out2d.clearRect(0, 0, ow, oh);
  out2d.imageSmoothingEnabled = true;
  out2d.imageSmoothingQuality = "high";
  out2d.drawImage(renderer.domElement, 0, 0, ow, oh);
  const px = out2d.getImageData(0, 0, ow, oh).data;
  let bin = "";
  for (let i = 0; i < px.length; i += 0x8000) bin += String.fromCharCode.apply(null, px.subarray(i, i + 0x8000));
  return btoa(bin);
};
document.documentElement.classList.add("ready");
