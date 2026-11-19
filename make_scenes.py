# Draws the scenes the pirate walks through in the Monkey Island launcher's ambient mode:
# four 320x180 pixel-art places along one coast, left to right
#   harbour (dusk) - jungle path (day) - beach (afternoon) - town street (night)
# Each has walkable ground along the bottom; the pirate's feet are on row GROUND.
# Same style as make_background.py: a small palette, ordered dithering, no smoothing.
import math
import random
import sys

from PIL import Image

W, H = 320, 180
GROUND = 168
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


class Canvas:
    def __init__(self, seed):
        self.img = Image.new("RGB", (W, H))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.px[x, y] = c

    def get(self, x, y):
        return self.px[x, y]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(H, y1 + 1)):
            for x in range(max(0, x0), min(W, x1 + 1)):
                self.px[x, y] = c

    def dither(self, x, y, a, b, t):
        return b if t * 16 > BAYER[y % 4][x % 4] + 0.5 else a

    def gradient(self, y0, y1, stops, x0=0, x1=W - 1):
        """Vertical banded gradient between (row, colour) stops, dithered."""
        for y in range(y0, y1 + 1):
            for (sa, ca), (sb, cb) in zip(stops, stops[1:]):
                if sa <= y <= sb:
                    t = (y - sa) / max(1, sb - sa)
                    break
            else:
                ca = cb = stops[-1][1]
                t = 0
            for x in range(x0, x1 + 1):
                self.put(x, y, self.dither(x, y, ca, cb, t))

    def disc(self, cx, cy, r, c, squash=1.0):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if math.hypot(x - cx, (y - cy) / squash) <= r:
                    self.put(x, y, c)

    def glow(self, cx, cy, r, c, strength=0.6):
        """A soft dithered halo that tints what is already there."""
        for y in range(int(cy - r), int(cy + r + 1)):
            for x in range(int(cx - r), int(cx + r + 1)):
                if not (0 <= x < W and 0 <= y < H):
                    continue
                d = math.hypot(x - cx, y - cy) / r
                if d < 1:
                    t = (1 - d) ** 2 * strength
                    if t * 16 > BAYER[y % 4][x % 4] + 0.5:
                        o = self.px[x, y]
                        self.px[x, y] = tuple(int(o[i] * 0.45 + c[i] * 0.55) for i in range(3))

    def stars(self, n, ymax, colours):
        for _ in range(n):
            self.put(self.rnd.randrange(W), self.rnd.randrange(ymax), self.rnd.choice(colours))

    def cloud(self, cx, cy, w, c, shade):
        for i in range(5):
            r = w * (0.22 + 0.12 * math.sin(i * 1.7 + cx))
            x = cx - w / 2 + i * w / 4.5
            self.disc(x, cy, r, c, 0.55)
        for x in range(int(cx - w * 0.6), int(cx + w * 0.6)):
            for y in range(int(cy), int(cy + w * 0.18)):
                if 0 <= x < W and 0 <= y < H and self.px[x, y] == c and (x + y) % 2 == 0:
                    self.px[x, y] = shade

    def sea(self, y0, y1, stops, sparkle, glint_x=None, glint=None):
        self.gradient(y0, y1, stops)
        for _ in range((y1 - y0) * 6):
            x, y = self.rnd.randrange(W), self.rnd.randrange(y0 + 1, y1 + 1)
            n = self.rnd.randint(2, 6)
            for i in range(n):
                self.put(x + i, y, sparkle)
        if glint_x is not None:
            for y in range(y0 + 1, y1 + 1, 2):
                spread = 3 + (y - y0) * 0.35
                for _ in range(2):
                    x = int(glint_x + self.rnd.uniform(-spread, spread))
                    for i in range(self.rnd.randint(2, 5)):
                        self.put(x + i, y, glint)

    def palm(self, base_x, base_y, height, lean, trunk, trunk_d, leaf, leaf_d):
        top = None
        for i in range(height):
            t = i / height
            x = base_x + lean * t * t * height
            y = base_y - i
            w = 3 if t < 0.7 else 2
            for dx in range(w):
                c = trunk_d if (i % 4 == 0 or dx == 0) else trunk
                self.put(int(x) + dx, y, c)
            top = (int(x) + 1, y)
        for ang in (-2.8, -2.2, -1.5, -0.9, -0.3, 0.3, 2.5):
            for j in range(26):
                a = ang
                fx = top[0] + math.cos(a) * j
                fy = top[1] + math.sin(a) * j * 0.55 + (j * j) * 0.025
                self.put(int(fx), int(fy), leaf)
                self.put(int(fx), int(fy) + 1, leaf_d)
                if j % 3 == 0 and j > 3:
                    self.put(int(fx), int(fy) + 2, leaf_d)


# ---- Harbour at dusk: a pier along the bottom, a ship at anchor, lanterns ----

def harbour(out):
    c = Canvas(11)
    c.gradient(0, 118, [(0, hexc("#2a1b4a")), (40, hexc("#5a2c5e")), (78, hexc("#b4485a")),
                        (102, hexc("#e8834e")), (118, hexc("#f6c06a"))])
    c.stars(30, 35, [hexc("#d8c8ff"), hexc("#9a88c8")])
    c.disc(92, 112, 11, hexc("#ffe7a0"))
    c.disc(92, 112, 8, hexc("#fff6d0"))
    for y, w in ((60, 70), (72, 50), (88, 90)):
        x = c.rnd.randrange(20, 280)
        for i in range(w):
            c.put(x + i, y, hexc("#f09a6a"))
            if i % 3:
                c.put(x + i + 4, y + 1, hexc("#c8606a"))
    c.sea(118, 150, [(118, hexc("#d07a5a")), (130, hexc("#7a3c5a")), (150, hexc("#3a2448"))],
          hexc("#e89a7a"), glint_x=92, glint=hexc("#ffd890"))
    # Far shore.
    for x in range(W):
        h = int(4 + 3 * math.sin(x * 0.05) + 2 * math.sin(x * 0.13))
        if x > 150:
            for y in range(118 - h, 118):
                c.put(x, y, hexc("#3a2240"))
    # The ship: hull, masts, rigging, furled sails, a lit stern window.
    sx, sy = 196, 128
    for y in range(sy - 10, sy + 1):
        inset = (sy - y) // 2
        c.rect(sx - 46 + (10 - inset) // 2, y, sx + 40 - (10 - inset) // 3, y, hexc("#24142a"))
    c.rect(sx - 40, sy - 12, sx + 38, sy - 11, hexc("#24142a"))
    c.rect(sx + 30, sy - 18, sx + 40, sy - 12, hexc("#24142a"))  # stern castle
    c.put(sx + 34, sy - 15, hexc("#ffd060"))
    c.put(sx + 35, sy - 15, hexc("#ffd060"))
    c.glow(sx + 34, sy - 15, 7, hexc("#ffb050"), 0.5)
    for mx, mh in ((sx - 22, 70), (sx + 2, 82), (sx + 24, 60)):
        c.rect(mx, sy - 12 - mh, mx, sy - 12, hexc("#24142a"))
        for k, yy in enumerate(range(sy - 12 - mh + 8, sy - 20, 20)):
            span = 14 - k * 2
            c.rect(mx - span, yy, mx + span, yy, hexc("#24142a"))
            c.rect(mx - span + 2, yy + 1, mx + span - 2, yy + 2, hexc("#3c2436"))
    for i in range(60):  # rigging lines
        t = i / 60
        c.put(int(sx - 22 + (sx - 46 - (sx - 22)) * t), int(sy - 82 + 70 * t), hexc("#3c2436"))
        c.put(int(sx + 2 + 44 * t), int(sy - 94 + 82 * t), hexc("#3c2436"))
    c.rect(sx - 46, sy - 13, sx - 60, sy - 13, hexc("#24142a"))  # bowsprit
    for i in range(14):
        c.put(sx - 46 - i, sy - 13 - i // 3, hexc("#24142a"))
    # Pier: planks along the bottom with posts.
    c.gradient(150, 179, [(150, hexc("#6a4430")), (179, hexc("#3a2418"))])
    for y in range(150, 180):
        for x in range(W):
            if x % 16 == (y // 30) * 8 % 16:
                c.put(x, y, hexc("#2a180f"))
            elif y in (151, 162, 175) or c.rnd.random() < 0.04:
                c.put(x, y, hexc("#4a2e1e"))
    c.rect(0, 149, W, 149, hexc("#8a5c3e"))
    for px_ in range(10, W, 64):
        c.rect(px_, 136, px_ + 4, 149, hexc("#3a2418"))
        c.rect(px_, 136, px_ + 4, 136, hexc("#6a4430"))
    # Barrels and a coil of rope.
    for bx in (26, 40):
        c.rect(bx, 150, bx + 11, 166, hexc("#7a4a2a"))
        for yy in (152, 158, 164):
            c.rect(bx, yy, bx + 11, yy, hexc("#3a2418"))
        c.rect(bx + 1, 150, bx + 2, 166, hexc("#9a6a42"))
    c.disc(300, 165, 6, hexc("#b89a62"), 0.5)
    c.disc(300, 165, 3, hexc("#6a5432"), 0.5)
    # Lantern post.
    c.rect(258, 108, 259, 150, hexc("#2a180f"))
    c.rect(255, 104, 262, 108, hexc("#2a180f"))
    c.rect(256, 105, 261, 107, hexc("#ffd060"))
    c.glow(258, 106, 22, hexc("#ffb050"), 0.55)
    c.img.save(out)


# ---- Jungle path at midday ----

def jungle(out):
    c = Canvas(23)
    c.gradient(0, 179, [(0, hexc("#9ad070")), (60, hexc("#4a8a3a")), (120, hexc("#2a5a2a")),
                        (179, hexc("#1a3a1e"))])
    # Far trees: pale trunks in the haze.
    for _ in range(18):
        x = c.rnd.randrange(W)
        w = c.rnd.randint(3, 6)
        c.rect(x, 0, x + w, 150, hexc("#5a8a4a"))
        c.rect(x, 0, x, 150, hexc("#6a9a56"))
    # Canopy blobs.
    for _ in range(80):
        x, y = c.rnd.randrange(-10, W + 10), c.rnd.randrange(-10, 40)
        c.disc(x, y, c.rnd.randint(6, 16), c.rnd.choice([hexc("#2a6a2a"), hexc("#1e5422"), hexc("#3a7a32")]), 0.7)
    # Near trees with roots.
    for x, w in ((18, 14), (128, 10), (232, 16), (300, 11)):
        c.rect(x, 0, x + w, 156, hexc("#4a3020"))
        c.rect(x + 2, 0, x + 3, 156, hexc("#6a4a30"))
        c.rect(x + w - 2, 0, x + w, 156, hexc("#2e1c12"))
        for k in range(4):
            for i in range(10):
                c.put(x - i + k * (w // 3), 156 + i // 3, hexc("#3a2418"))
                c.put(x + w + i - k * 2, 156 + i // 3, hexc("#3a2418"))
    # Vines.
    for _ in range(14):
        x = c.rnd.randrange(W)
        ln = c.rnd.randint(40, 120)
        for y in range(ln):
            xx = int(x + 2 * math.sin(y * 0.15 + x))
            c.put(xx, y, hexc("#2a6a22"))
            if y % 7 == 0:
                c.put(xx + 1, y, hexc("#4a9a3a"))
                c.put(xx - 1, y + 1, hexc("#4a9a3a"))
    # Sun shafts.
    for sx in (70, 180):
        for y in range(0, 150):
            for x in range(sx + y // 3, sx + y // 3 + 14):
                if (x + y) % 3 == 0 and 0 <= x < W:
                    o = c.get(x, y)
                    c.put(x, y, tuple(min(255, int(v * 1.25 + 18)) for v in o))
    # Dirt path and ferns.
    c.gradient(150, 179, [(150, hexc("#8a6a3e")), (179, hexc("#5a4226"))])
    for _ in range(400):
        c.put(c.rnd.randrange(W), c.rnd.randrange(152, 180), c.rnd.choice([hexc("#a8865a"), hexc("#4a3420")]))
    for x in range(0, W, 3):
        h = c.rnd.randint(2, 7)
        for y in range(150 - h, 152):
            c.put(x + c.rnd.randint(-1, 1), y, hexc("#3a7a2a"))
    for fx in (60, 104, 200, 276):
        for i in range(-12, 13):
            y = 172 - int(10 - abs(i) * 0.7 - (i * i) * 0.02)
            for yy in range(y, 176):
                c.put(fx + i, yy, hexc("#2a6a22") if (i + yy) % 3 else hexc("#4a9a3a"))
    # A parrot on a branch.
    c.rect(150, 70, 190, 72, hexc("#3a2418"))
    c.rect(165, 60, 169, 69, hexc("#d03a2a"))
    c.rect(166, 58, 169, 61, hexc("#d03a2a"))
    c.put(169, 59, hexc("#f0e070"))
    c.put(170, 60, hexc("#2a2a2a"))
    c.rect(164, 64, 165, 70, hexc("#2a5ad0"))
    c.rect(166, 70, 167, 74, hexc("#f0c020"))
    c.img.save(out)


# ---- Beach in the afternoon ----

def beach(out):
    c = Canvas(37)
    c.gradient(0, 108, [(0, hexc("#3a7ad0")), (60, hexc("#6aa8e8")), (108, hexc("#b8dcf0"))])
    c.disc(270, 30, 10, hexc("#fff8d8"))
    c.glow(270, 30, 30, hexc("#fff0c0"), 0.4)
    for cx, cy, w in ((60, 30, 50), (150, 48, 36), (210, 18, 44)):
        c.cloud(cx, cy, w, hexc("#f4f8ff"), hexc("#c8d8f0"))
    # An island on the horizon.
    for x in range(30, 110):
        h = int(10 * math.sin((x - 30) / 80 * math.pi) + 2 * math.sin(x * 0.3))
        for y in range(108 - h, 108):
            c.put(x, y, hexc("#5a8aa8"))
    c.sea(108, 140, [(108, hexc("#3a8ac0")), (140, hexc("#2a6aa0"))], hexc("#9ad0f0"))
    # Sand, then the surf line and wet sand over its top edge.
    c.gradient(138, 179, [(138, hexc("#e8cc8a")), (179, hexc("#d0ac6a"))])
    for x in range(W):
        y = 140 + int(2 * math.sin(x * 0.08))
        c.put(x, y, hexc("#f0f8ff"))
        c.put(x, y + 1, hexc("#d8eef8") if x % 2 else hexc("#f0f8ff"))
        for yy in range(y + 2, y + 6):
            c.put(x, yy, hexc("#c8a870"))
    for _ in range(500):
        c.put(c.rnd.randrange(W), c.rnd.randrange(146, 180), c.rnd.choice([hexc("#f4dca0"), hexc("#b8945a")]))
    c.palm(250, 170, 92, -0.25, hexc("#a07040"), hexc("#6a4a2a"), hexc("#3a9a3a"), hexc("#226a26"))
    c.palm(296, 172, 70, -0.35, hexc("#a07040"), hexc("#6a4a2a"), hexc("#3a9a3a"), hexc("#226a26"))
    # A rowboat pulled up the sand.
    for i in range(36):
        d = abs(i - 18) / 18
        top = 152 + int(d * d * 4)
        c.rect(40 + i, top, 40 + i, 160, hexc("#8a4a2a"))
        c.put(40 + i, top, hexc("#c07a4a"))
        c.put(40 + i, 160, hexc("#5a2a18"))
    c.rect(48, 154, 49, 158, hexc("#5a2a18"))
    c.rect(66, 154, 67, 158, hexc("#5a2a18"))
    for i in range(20):  # an oar
        c.put(78 + i, 162 - i // 4, hexc("#c09a6a"))
    # A crab, a shell, a starfish.
    c.rect(150, 172, 155, 174, hexc("#d04a2a"))
    for dx in (-1, 6):
        c.put(150 + dx, 171, hexc("#d04a2a"))
    c.put(151, 171, hexc("#202020"))
    c.put(154, 171, hexc("#202020"))
    c.disc(200, 176, 2, hexc("#f4e0e0"))
    for a in range(5):
        for r in range(4):
            c.put(110 + int(math.cos(a * 1.2566) * r), 175 + int(math.sin(a * 1.2566) * r), hexc("#e08a3a"))
    c.img.save(out)


# ---- Town street at night ----

def town(out):
    c = Canvas(41)
    c.gradient(0, 120, [(0, hexc("#070818")), (70, hexc("#141a40")), (120, hexc("#26305a"))])
    c.stars(90, 70, [hexc("#ffffff"), hexc("#a8b0e0"), hexc("#6870a8")])
    c.disc(60, 26, 8, hexc("#f4efd0"))
    c.disc(63, 24, 7, hexc("#141a40"))   # a crescent
    # Houses: a row of timber-framed fronts with lit windows.
    x = -6
    houses = []
    while x < W:
        w = c.rnd.randint(44, 62)
        h = c.rnd.randint(62, 92)
        houses.append((x, w, h))
        x += w + c.rnd.randint(0, 4)
    walls = [hexc("#4a3a4a"), hexc("#3e3446"), hexc("#52404a")]
    for i, (hx, w, h) in enumerate(houses):
        top = 150 - h
        wall = walls[i % 3]
        c.rect(hx, top, hx + w, 150, wall)
        for k in range(w // 2 + 3):  # roof
            c.rect(hx - 3 + k, top - k // 2 - 1, hx + w + 3 - k, top - k // 2 - 1, hexc("#2a1e2a"))
        c.rect(hx + w - 10, top - 20, hx + w - 5, top - 6, hexc("#2a1e2a"))  # chimney
        for yy in range(top + 2, 150, 20):
            c.rect(hx, yy, hx + w, yy, hexc("#2a1e22"))
        for xx in range(hx, hx + w, 14):
            c.rect(xx, top, xx + 1, 150, hexc("#2a1e22"))
        for wy in range(top + 8, 136, 20):
            for wx in range(hx + 5, hx + w - 8, 14):
                if c.rnd.random() < 0.6:
                    c.rect(wx, wy, wx + 5, wy + 7, hexc("#ffcc60"))
                    c.rect(wx + 2, wy, wx + 2, wy + 7, hexc("#a06a2a"))
                    c.glow(wx + 3, wy + 4, 9, hexc("#ffb050"), 0.35)
                else:
                    c.rect(wx, wy, wx + 5, wy + 7, hexc("#1a1424"))
        c.rect(hx + w // 2 - 4, 136, hx + w // 2 + 4, 150, hexc("#2a1a14"))  # door
    # The tavern sign.
    c.rect(150, 104, 172, 105, hexc("#2a1e22"))
    c.rect(154, 106, 168, 118, hexc("#6a4426"))
    c.rect(155, 107, 167, 117, hexc("#8a5a32"))
    c.disc(161, 112, 3, hexc("#e0c070"))   # a tankard
    c.rect(164, 110, 165, 114, hexc("#e0c070"))
    # Cobbles.
    c.gradient(150, 179, [(150, hexc("#4a4656")), (179, hexc("#2e2a36"))])
    for y in range(150, 180, 4):
        off = (y // 4) % 2 * 5
        for x in range(-off, W, 10):
            c.rect(x, y, x, y + 3, hexc("#26222e"))
        c.rect(0, y, W, y, hexc("#26222e"))
        for x in range(-off + 2, W, 10):
            c.put(x, y + 1, hexc("#5e5a6a"))
    # A street lamp.
    c.rect(238, 104, 239, 150, hexc("#1a1620"))
    c.rect(234, 98, 243, 104, hexc("#1a1620"))
    c.rect(235, 99, 242, 103, hexc("#ffe08a"))
    c.glow(238, 101, 34, hexc("#ffc060"), 0.5)
    c.glow(238, 160, 26, hexc("#a08060"), 0.35)
    # A black cat on a step.
    c.rect(96, 146, 102, 149, hexc("#0e0c12"))
    c.rect(100, 142, 103, 146, hexc("#0e0c12"))
    c.put(100, 141, hexc("#0e0c12"))
    c.put(103, 141, hexc("#0e0c12"))
    c.put(101, 143, hexc("#c0e040"))
    c.put(103, 143, hexc("#c0e040"))
    for i in range(5):
        c.put(95 - i // 2, 148 - i, hexc("#0e0c12"))
    c.img.save(out)


if __name__ == "__main__":
    folder = sys.argv[1] if len(sys.argv) > 1 else "res/drawable-nodpi"
    harbour(folder + "/scene_harbour.png")
    jungle(folder + "/scene_jungle.png")
    beach(folder + "/scene_beach.png")
    town(folder + "/scene_town.png")
