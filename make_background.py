# Draws the default background: a 320x180 pixel-art night view from a clifftop
# lookout over a moonlit bay, in the style of early-90s VGA adventure games.
# The launcher scales it up with nearest-neighbour filtering to keep pixels crisp.
import math
import random
import sys

from PIL import Image

W, H = 320, 180
HORIZON = 112
random.seed(7)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

img = Image.new("RGB", (W, H))
px = img.load()


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


def band(y, stops):
    """Pick a palette colour for row y from banded stops, dithering between neighbours."""
    for (y0, c0), (y1, c1) in zip(stops, stops[1:]):
        if y0 <= y <= y1:
            return c0, c1, (y - y0) / max(1, y1 - y0)
    return stops[-1][1], stops[-1][1], 0


def dither(x, y, a, b, t):
    return b if t * 16 > BAYER[y % 4][x % 4] + 0.5 else a


# Sky
sky = [(0, hexc("#070620")), (35, hexc("#120f3c")), (70, hexc("#1f2463")),
       (98, hexc("#33408a")), (HORIZON, hexc("#4d5aa6"))]
for y in range(HORIZON):
    a, b, t = band(y, sky)
    for x in range(W):
        px[x, y] = dither(x, y, a, b, t)

# Stars
for _ in range(110):
    x, y = random.randrange(W), random.randrange(80)
    px[x, y] = random.choice([hexc("#ffffff"), hexc("#c8d0ff"), hexc("#8890d0"), hexc("#8890d0")])
for x, y in [(40, 14), (170, 22), (300, 10), (120, 40)]:  # a few bright twinkles
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        px[x + dx, y + dy] = hexc("#ffffff") if (dx, dy) == (0, 0) else hexc("#9aa4ff")

# Moon with halo
MX, MY, MR = 238, 38, 13
for y in range(MY - 34, MY + 34):
    for x in range(MX - 34, MX + 34):
        d = math.hypot(x - MX, y - MY)
        if d <= MR:
            crater = (x - MX + 4) ** 2 + (y - MY - 3) ** 2 < 10 or (x - MX - 5) ** 2 + (y - MY + 4) ** 2 < 6
            px[x, y] = hexc("#d8d2a8") if crater else hexc("#f6f1cf")
        elif d <= MR + 1:
            px[x, y] = hexc("#c9c39a")
        elif d < MR + 20:
            t = 1 - (d - MR) / 20
            px[x, y] = dither(x, y, px[x, y], hexc("#5a63b0") if d > MR + 8 else hexc("#7d84c4"), t)

# Thin clouds drifting across the moon
for cy, x0, x1 in [(46, 196, 286), (50, 210, 300), (30, 150, 214), (60, 60, 150), (64, 80, 170)]:
    for x in range(x0, x1):
        edge = min(x - x0, x1 - x) / 10
        for dy in (0, 1):
            if dither(x, cy + dy, 0, 1, min(1, edge)) == 1:
                lit = abs(x - MX) < 40 and abs(cy - MY) < 20
                px[x, cy + dy] = hexc("#8a8fc8") if lit else hexc("#2a2f70")

# Distant island with a little harbour town
for x in range(W):
    h = 5 + 3 * math.sin(x / 23) + 2 * math.sin(x / 7 + 1)
    if 120 < x < 318:
        top = HORIZON - int(h + 9 * math.exp(-((x - 205) / 30) ** 2))
        for y in range(top, HORIZON + 1):
            px[x, y] = hexc("#161a44") if y > top else hexc("#262c62")
for x in range(176, 236, 3):  # rooftops and lit windows
    roof = HORIZON - 9 - int(4 * math.exp(-((x - 205) / 18) ** 2)) - random.randint(0, 3)
    for y in range(roof, HORIZON):
        px[x, y] = hexc("#0e1132")
        px[x + 1, y] = hexc("#0e1132")
    if random.random() < 0.75:
        wy = random.randint(roof + 2, HORIZON - 2)
        px[x, wy] = hexc("#ffcc55")
for x, y in [(150, HORIZON - 4), (262, HORIZON - 5), (271, HORIZON - 4)]:
    px[x, y] = hexc("#ffb040")

# Sea
sea = [(HORIZON + 1, hexc("#22306e")), (140, hexc("#121b4c")), (H - 1, hexc("#070c2c"))]
for y in range(HORIZON + 1, H):
    a, b, t = band(y, sea)
    for x in range(W):
        px[x, y] = dither(x, y, a, b, t)
for y in range(HORIZON + 2, H, 2):  # wave glints
    for _ in range(int(6 + (y - HORIZON) / 5)):
        x, L = random.randrange(W), random.randint(2, 6)
        for i in range(L):
            if x + i < W:
                px[x + i, y] = hexc("#3a4c9a")
for y in range(HORIZON + 2, H):  # moon reflection
    spread = 4 + (y - HORIZON) // 3
    if y % 2 == 0:
        for _ in range(3):
            x0 = MX + random.randint(-spread, spread)
            for i in range(random.randint(2, 7)):
                if 0 <= x0 + i < W:
                    px[x0 + i, y] = hexc("#f6f1cf") if y < 140 else hexc("#c9c39a")

# Foreground cliff with the lookout ledge
def cliff_top(x):
    if x < 30:
        return 52 + int(4 * math.sin(x / 5))  # tall rock pillar
    if x < 40:
        return 52 + (x - 30) * 6
    if x < 150:
        return 116 + int(3 * math.sin(x / 9)) + max(0, (x - 120)) // 2
    return 999

for x in range(170):
    top = cliff_top(x)
    if x >= 150:
        top = 131 + (x - 150) * 2  # cliff face drops into the sea
    for y in range(max(0, top), H):
        depth = y - top
        if depth < 1:
            c = hexc("#6a5a86")  # moonlit rim
        elif depth < 4:
            c = hexc("#3e3052")
        else:
            c = dither(x, y, hexc("#251a33"), hexc("#140d1e"), min(1, depth / 50))
        # vertical cracks and ledges in the rock face
        if (x * 7 + y // 9) % 23 == 0 and depth > 3:
            c = hexc("#0c0814")
        if x < 32 and (x + y // 4) % 9 == 0:
            c = hexc("#3e3052")
        px[x, y] = c

# Rocks around the campfire
for cx, cy in [(68, 120), (88, 121), (74, 123), (84, 123)]:
    for dx in range(-2, 3):
        for dy in range(-1, 2):
            px[cx + dx, cy + dy] = hexc("#5a4a6a") if dy < 0 else hexc("#2e2240")

# Firelight on the rock
FX, FY = 78, 116
for y in range(FY - 20, H):
    for x in range(FX - 40, FX + 40):
        if 0 <= x < W and y >= cliff_top(x) + 1:
            d = math.hypot((x - FX) / 1.4, y - FY)
            if d < 26:
                t = 1 - d / 26
                px[x, y] = dither(x, y, px[x, y], hexc("#6e2c26") if t < 0.5 else hexc("#a2482a"), t * 1.4)

# Flames
flame = [
    "....y....",
    "...yy....",
    "...yYy.y.",
    "..yYYyyy.",
    ".yoYWYyo.",
    ".oyYWWYo.",
    "ooyWWWyoo",
    "rooyYyoor",
    ".rrooorr.",
    "..LLLLL..",
]
cols = {"y": "#ffb030", "Y": "#ffe060", "W": "#fff8d0", "o": "#e86018", "r": "#a02810", "L": "#3a2018"}
for j, row in enumerate(flame):
    for i, ch in enumerate(row):
        if ch != ".":
            px[FX - 4 + i, FY - 9 + j] = hexc(cols[ch])
for sx, sy in [(76, 101), (82, 98), (79, 94), (84, 90)]:  # sparks
    px[sx, sy] = hexc("#ffb030")

# Palm tree silhouette on the right
DARK = hexc("#05040e")
for t in range(0, 95):
    x = 304 - int(t * 0.28 + 8 * math.sin(t / 40))
    y = H - 1 - t
    for w in range(3 if t > 60 else 4):
        px[x + w, y] = DARK
TX, TY = 304 - int(94 * 0.28 + 8 * math.sin(94 / 40)), H - 95
for ang, length, droop in [(-170, 40, 0.9), (-140, 34, 0.7), (-100, 22, 0.4), (-60, 30, 0.7),
                           (-20, 38, 0.9), (10, 30, 1.1), (160, 30, 1.1), (200, 26, 1.3)]:
    a = math.radians(ang)
    for s in range(length):
        fx = TX + 1 + math.cos(a) * s
        fy = TY + math.sin(a) * s + droop * (s ** 2) / 40
        for k in range(-1, 2):
            if s % 3 == 0:
                lx, ly = int(fx + k), int(fy + 2 + abs(k))
                if 0 <= lx < W and 0 <= ly < H:
                    px[lx, ly] = DARK
            if 0 <= int(fx) < W and 0 <= int(fy) < H:
                px[int(fx), int(fy)] = DARK
                if s < length * 0.6 and int(fy) + 1 < H:
                    px[int(fx), int(fy) + 1] = DARK

img.save(sys.argv[1])
img.resize((W * 4, H * 4), Image.NEAREST).save(sys.argv[2]) if len(sys.argv) > 2 else None
