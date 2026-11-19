// Renders one of the Window launcher's scenes from web/window.html: a seamless 20-second loop
// at 30 frames per second, 1280×720, plus a still of the first frame.
// Needs Node.js, Playwright's Chromium and ffmpeg (with libx264).
//
//   node web/render_window.js <scene 0-4> <out.mp4> <still.jpg>
const { chromium } = require("playwright");
const { spawn, execFileSync } = require("child_process");
const path = require("path");

const FPS = 30, W = 1280, H = 720;
const [, , scene, outFile, stillFile] = process.argv;

(async () => {
  const browser = await chromium.launch({ args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  page.on("pageerror", e => console.error("page error:", String(e)));
  await page.goto("file://" + path.resolve(__dirname, "window.html") + "?scene=" + scene);
  await page.waitForFunction(() => document.documentElement.classList.contains("ready"), null, { timeout: 60000 });
  const loop = await page.evaluate(() => window.LOOP);
  const total = Math.round(loop * FPS);

  const ffmpeg = spawn("ffmpeg", ["-loglevel", "error", "-y",
    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", `${W}x${H}`, "-r", String(FPS), "-i", "-",
    "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
    "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-profile:v", "high", "-level", "4.1",
    "-bf", "0", "-g", String(FPS * 5), "-keyint_min", String(FPS * 5), "-sc_threshold", "0",
    "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv",
    "-movflags", "+faststart", outFile], { stdio: ["pipe", "inherit", "inherit"] });
  const done = new Promise((ok, fail) => ffmpeg.on("exit", c => (c === 0 ? ok() : fail(new Error("ffmpeg " + c)))));
  for (let i = 0; i < total; i++) {
    const px = Buffer.from(await page.evaluate((t) => window.renderFrame(t), i / FPS), "base64");
    if (!ffmpeg.stdin.write(px)) await new Promise(r => ffmpeg.stdin.once("drain", r));
    if (i % 100 === 0) console.log(`window ${scene}: frame ${i} of ${total}`);
  }
  ffmpeg.stdin.end();
  await done;
  await browser.close();
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-i", outFile, "-vframes", "1", "-q:v", "3", stillFile]);
  console.log("wrote", outFile, stillFile);
})().catch(e => { console.error(e); process.exit(1); });
