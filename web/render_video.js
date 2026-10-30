// Renders the Apartment launcher's video: one full turn of the flat, in a loop, with the
// flat's transparency packed beside it. Run by web/build.sh; needs Node.js, Playwright's
// Chromium and ffmpeg (with libx264).
//
//   node web/render_video.js <bundled render page dir> <day|night> <out.mp4> [frames]
//
// Each frame is drawn by the page (web/render.html + web/apartment.js) in headless
// Chromium's software renderer at SUPERSAMPLE × the video size, scaled down, then:
//   left half   the flat's colour, premultiplied by its transparency (black around it)
//   right half  the transparency itself, as grey
// The app recombines the halves over its live sky. Frames are shared out to WORKERS
// browsers, each encoding its own stretch; the stretches are joined at the end.
const { chromium } = require("playwright");
const { spawn, execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const FPS = 30, SECONDS = 150;                 // one turn every 2½ minutes, as before
const W = 960, H = 880, SUPERSAMPLE = 1.5;     // each half of the picture
const ANGLE0 = 0.6;                            // the angle of frame 0 (the app uses the same)
const GOP = 5 * FPS;                           // a keyframe every 5 seconds
const WORKERS = Math.max(1, Math.min(4, os.cpus().length));
const LIGHTING = {
  day: { when: "2026-06-21T10:00:00Z", code: "partly" },     // early afternoon, a few clouds
  night: { when: "2026-10-24T20:30:00Z", code: "clear" },    // late evening, lamps on
};

const [, , pageDir, variant, outFile, framesArg] = process.argv;
const total = framesArg ? Number(framesArg) : FPS * SECONDS;

async function renderStretch(from, to, segFile) {
  const browser = await chromium.launch({ args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
  const page = await browser.newPage({ viewport: { width: 400, height: 400 } });
  page.on("pageerror", e => console.error("page error:", String(e)));
  await page.goto("file://" + path.resolve(pageDir, "index.html"));
  await page.waitForFunction(() => document.documentElement.classList.contains("ready"), null, { timeout: 600000 });
  await page.evaluate(([l, w, h]) => window.renderSetup({ ...l, width: w, height: h }),
    [LIGHTING[variant], Math.round(W * SUPERSAMPLE), Math.round(H * SUPERSAMPLE)]);

  const ffmpeg = spawn("ffmpeg", ["-loglevel", "error", "-y",
    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", `${W}x${H}`, "-r", String(FPS), "-i", "-",
    "-filter_complex", "[0:v]split[c][a];[a]alphaextract,format=rgb24[al];[c]format=rgb24[cc];" +
      "[cc][al]hstack=inputs=2,scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
    "-c:v", "libx264", "-preset", "slow", "-crf", "22", "-profile:v", "high", "-level", "4.1",
    "-bf", "0", "-g", String(GOP), "-keyint_min", String(GOP), "-sc_threshold", "0",
    "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv",
    segFile], { stdio: ["pipe", "inherit", "inherit"] });
  const done = new Promise((ok, fail) => ffmpeg.on("exit", c => (c === 0 ? ok() : fail(new Error("ffmpeg " + c)))));

  for (let i = from; i < to; i++) {
    const angle = ANGLE0 + 2 * Math.PI * i / total;
    const px = Buffer.from(await page.evaluate(([a, w, h]) => window.renderFrame(a, w, h), [angle, W, H]), "base64");
    for (let j = 0; j < px.length; j += 4) {   // premultiply
      const a = px[j + 3];
      if (a !== 255) { px[j] = px[j] * a / 255; px[j + 1] = px[j + 1] * a / 255; px[j + 2] = px[j + 2] * a / 255; }
    }
    if (!ffmpeg.stdin.write(px)) await new Promise(r => ffmpeg.stdin.once("drain", r));
    if ((i - from) % 50 === 0) console.log(`${variant}: frame ${i} (${from}–${to - 1})`);
  }
  ffmpeg.stdin.end();
  await done;
  await browser.close();
}

(async () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "orbit-"));
  // Each stretch starts on a keyframe, so they join cleanly.
  const per = Math.ceil(total / GOP / WORKERS) * GOP;
  const jobs = [];
  for (let k = 0; k * per < total; k++) {
    jobs.push({ from: k * per, to: Math.min(total, (k + 1) * per), file: path.join(tmp, `seg${k}.mp4`) });
  }
  await Promise.all(jobs.map(j => renderStretch(j.from, j.to, j.file)));
  fs.writeFileSync(path.join(tmp, "list.txt"), jobs.map(j => `file '${j.file}'`).join("\n"));
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", path.join(tmp, "list.txt"),
    "-c", "copy", "-movflags", "+faststart", outFile]);
  fs.rmSync(tmp, { recursive: true });
  console.log("wrote", outFile);
})().catch(e => { console.error(e); process.exit(1); });
