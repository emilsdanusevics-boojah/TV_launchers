# Makes the Fireplace launcher's two background sounds, both seamless 60-second loops:
#   fire.ogg  a quiet crackling fire: a low rumble, a soft hiss, crackles and the odd snap
#   rain.ogg  gentle rain: a soft rush with droplets ticking now and then
# Everything is synthesised here (no recordings), shaped in the frequency domain so the
# noise wraps round seamlessly, with crackles and drops placed round the loop too.
#
#   python3 web/make_sounds.py assets/fireplace    (needs numpy and ffmpeg with libvorbis)
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 44100
SECONDS = 60
N = RATE * SECONDS
rng = np.random.default_rng(95)


def shaped_noise(lo, hi, tilt=0.0, channels=2):
    """Noise with energy between lo and hi Hz, periodic over the loop."""
    freqs = np.fft.rfftfreq(N, 1 / RATE)
    gain = ((freqs > lo) & (freqs < hi)).astype(float)
    # soft edges, and a slope (negative: darker, like brown/pink noise)
    gain *= np.clip((freqs - lo) / (lo * 0.5 + 1), 0, 1) * np.clip((hi - freqs) / (hi * 0.3), 0, 1)
    gain *= np.where(freqs > 0, (np.maximum(freqs, 1) / 1000.0) ** tilt, 0)
    out = []
    for _ in range(channels):
        spectrum = (rng.standard_normal(len(freqs)) + 1j * rng.standard_normal(len(freqs))) * gain
        x = np.fft.irfft(spectrum, N)
        out.append(x / (np.abs(x).max() + 1e-9))
    return np.stack(out)


def slow_wobble(depth, periods=(3, 7, 11)):
    """A slow loudness change that repeats exactly over the loop."""
    t = np.arange(N) / N
    w = sum(np.sin(2 * np.pi * k * t + rng.uniform(0, 6.3)) for k in periods) / len(periods)
    return 1 + depth * w


def burst(length, decay, lo, hi):
    """One short burst of filtered noise that dies away."""
    n = max(8, int(length * RATE))
    x = rng.standard_normal(n)
    spectrum = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / RATE)
    spectrum *= ((f > lo) & (f < hi))
    x = np.fft.irfft(spectrum, n)
    x *= np.exp(-np.arange(n) / (decay * RATE))
    return x / (np.abs(x).max() + 1e-9)


def place(track, sound, at, gain, pan):
    """Adds a sound at sample `at`, wrapping round the end of the loop; pan -1..1."""
    idx = (at + np.arange(len(sound))) % N
    track[0, idx] += sound * gain * (1 - pan) / 2 * 1.4
    track[1, idx] += sound * gain * (1 + pan) / 2 * 1.4


def fire():
    out = 0.32 * shaped_noise(30, 400, tilt=-1.2) * slow_wobble(0.3)     # rumble of the flames
    out += 0.05 * shaped_noise(1500, 7000, tilt=-0.4) * slow_wobble(0.5, (5, 13, 17))  # hiss
    t = 0
    while t < N:   # crackles: a few a second, some loud
        t += int(rng.exponential(0.28) * RATE) + 1
        size = rng.pareto(2.2) + 0.3
        place(out, burst(rng.uniform(0.004, 0.02), rng.uniform(0.002, 0.008), 900, 9000),
              t, min(0.9, 0.12 * size), rng.uniform(-0.6, 0.6))
        if rng.random() < 0.15:   # a quick double crackle
            place(out, burst(0.006, 0.003, 1500, 9000), t + int(rng.uniform(0.01, 0.04) * RATE),
                  0.08 * size, rng.uniform(-0.6, 0.6))
    for _ in range(9):            # the odd louder snap of a log
        place(out, burst(0.05, 0.015, 400, 6000), int(rng.uniform(0, N)), 0.55, rng.uniform(-0.4, 0.4))
    return out


def rain():
    out = 0.35 * shaped_noise(250, 9000, tilt=-0.6) * slow_wobble(0.15)   # the soft rush
    out += 0.08 * shaped_noise(40, 250, tilt=-1.0)                        # a little body
    t = 0
    while t < N:   # droplets ticking on sills and leaves
        t += int(rng.exponential(0.05) * RATE) + 1
        place(out, burst(0.012, 0.004, 2500, 10000), t, 0.05 * (rng.pareto(3) + 0.4),
              rng.uniform(-0.9, 0.9))
    return out


def write(samples, path):
    samples = samples / np.abs(samples).max() * 0.6     # quiet, with headroom
    pcm = (samples.T * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "2", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "3", "-metadata", "ANDROID_LOOP=true", str(path)],
                   input=pcm, check=True)


if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    write(fire(), out / "fire.ogg")
    write(rain(), out / "rain.ogg")
    print("wrote", out / "fire.ogg", out / "rain.ogg")
