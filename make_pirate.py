# Draws the walking pirate for the Monkey Island launcher's ambient mode: an original
# deckhand (red bandana, striped shirt, brown trousers, boots), seen from the side and
# facing right, on a strip of 24x40 frames:
#   frames 0-5  walk cycle
#   frame  6    standing
#   frame  7    standing, blinking
# The launcher scales it up with nearest-neighbour filtering, like the background art.
import math
import sys

from PIL import Image

FW, FH = 24, 40
FRAMES = 8

SKIN = (224, 168, 120)
SKIN_D = (176, 116, 76)
HAIR = (58, 36, 20)
BEARD = (92, 60, 38)
BAND = (196, 44, 40)
BAND_D = (130, 26, 28)
WHITE = (232, 228, 214)
NAVY = (40, 56, 108)
NAVY_D = (26, 36, 72)
WHITE_D = (170, 166, 160)
BELT = (44, 28, 18)
BUCKLE = (228, 180, 64)
TROUS = (110, 70, 40)
TROUS_D = (76, 46, 28)
BOOT = (30, 22, 18)
BOOT_H = (70, 56, 46)
EYE = (24, 16, 16)
OUTLINE = (20, 12, 16)


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / l2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def limb(img, a, b, width, color):
    """A thick line between two points, without smoothing."""
    x0, x1 = int(min(a[0], b[0]) - width - 1), int(max(a[0], b[0]) + width + 1)
    y0, y1 = int(min(a[1], b[1]) - width - 1), int(max(a[1], b[1]) + width + 1)
    for y in range(max(0, y0), min(FH, y1 + 1)):
        for x in range(max(0, x0), min(FW, x1 + 1)):
            if seg_dist(x + 0.5, y + 0.5, a[0], a[1], b[0], b[1]) <= width / 2:
                img[(x, y)] = color


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img[(x, y)] = color


def leg(img, hip, swing, lift, near):
    """swing: thigh angle (radians, + = forward); lift: knee bend."""
    trous, boot = (TROUS, BOOT) if near else (TROUS_D, (22, 16, 14))
    knee = (hip[0] + 5.6 * math.sin(swing), hip[1] + 5.6 * math.cos(swing))
    shin = swing - lift
    ankle = (knee[0] + 5.6 * math.sin(shin), knee[1] + 5.6 * math.cos(shin))
    limb(img, hip, knee, 3.6, trous)
    limb(img, knee, ankle, 3.2, trous)
    # The boot: from the ankle down to the ground, with a toe pointing forward.
    ax, ay = int(round(ankle[0])), min(37, int(round(ankle[1])))
    rect(img, ax - 2, ay - 1, ax + 1, ay + 1, boot)
    rect(img, ax - 2, ay + 1, ax + 3, ay + 2, boot)
    if near:
        img[(ax - 1, ay - 1)] = BOOT_H


def arm(img, shoulder, swing, near):
    sleeve = WHITE if near else WHITE_D
    hand = SKIN if near else SKIN_D
    elbow = (shoulder[0] + 5 * math.sin(swing), shoulder[1] + 5 * math.cos(swing))
    fore = swing + 0.35 + max(0, swing) * 0.6   # elbows bend more on the forward swing
    wrist = (elbow[0] + 5 * math.sin(fore), elbow[1] + 5 * math.cos(fore))
    limb(img, shoulder, elbow, 3.2, sleeve)
    if near:  # a navy cuff stripe
        mid = ((shoulder[0] + elbow[0]) / 2, (shoulder[1] + elbow[1]) / 2)
        limb(img, mid, (mid[0] + 0.4, mid[1] + 0.6), 3.0, NAVY)
    limb(img, elbow, wrist, 2.8, hand)


def frame(i):
    img = {}
    walking = i < 6
    phase = i / 6 * 2 * math.pi
    s = math.sin(phase) if walking else 0
    bob = 0 if not walking else (-1 if abs(math.cos(phase)) > 0.8 else 0)
    hip = (11.5, 26.5 + bob)
    shoulder = (11.5, 16.5 + bob)

    # Far limbs first, then the body, then the near limbs on top.
    leg(img, hip, -0.6 * s, (0.7 if s < 0 else 0.15) if walking else 0, False)
    arm(img, (shoulder[0] + 1, shoulder[1]), 0.65 * s if walking else 0.05, False)

    top = 15 + bob
    # Shirt: navy and white stripes, two pixels each.
    for y in range(top, top + 11):
        for x in range(8, 16):
            stripe = NAVY if ((y - top) // 2) % 2 == 0 else WHITE
            if x == 8:
                stripe = NAVY_D if stripe == NAVY else WHITE_D
            img[(x, y)] = stripe
    rect(img, 8, top + 11, 15, top + 11, BELT)
    img[(14, top + 11)] = BUCKLE
    rect(img, 8, top + 12, 15, top + 13, TROUS)

    # Head.
    h = 3 + bob
    rect(img, 9, h + 2, 16, h + 10, SKIN)
    img[(17, h + 6)] = SKIN          # nose
    img[(17, h + 7)] = SKIN_D
    rect(img, 9, h + 2, 10, h + 8, HAIR)
    rect(img, 11, h + 10, 12, h + 11, SKIN_D)   # neck
    for x in range(12, 17):
        img[(x, h + 9)] = BEARD
        if x > 12:
            img[(x, h + 10)] = BEARD
    img[(15, h + 8)] = SKIN          # mouth gap in the stubble
    img[(14, h + 8)] = BEARD
    img[(14, h + 5)] = SKIN_D if i == 7 else EYE
    img[(14, h + 4)] = HAIR          # brow
    img[(15, h + 4)] = HAIR
    # Bandana over the top of the head, knotted at the back with two tails.
    rect(img, 9, h, 15, h + 2, BAND)
    rect(img, 10, h - 1, 14, h - 1, BAND)
    img[(16, h + 2)] = BAND
    rect(img, 9, h + 3, 16, h + 3, BAND_D)
    flap = int(round(math.sin(phase + 1) * 1)) if walking else 0
    img[(8, h + 2)] = BAND
    img[(7, h + 3)] = BAND
    img[(6, h + 4 + flap)] = BAND_D
    img[(7, h + 4)] = BAND_D
    img[(6, h + 5 + flap)] = BAND
    # A gold earring.
    img[(10, h + 7)] = BUCKLE

    leg(img, hip, 0.6 * s, (0.7 if s > 0 else 0.15) if walking else 0, True)
    arm(img, shoulder, -0.65 * s if walking else -0.05, True)

    # Dark outline around the whole figure.
    filled = set(img)
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in filled and 0 <= p[0] < FW and 0 <= p[1] < FH:
                img[p] = OUTLINE
    return img


def main(out):
    sheet = Image.new("RGBA", (FW * FRAMES, FH), (0, 0, 0, 0))
    px = sheet.load()
    for i in range(FRAMES):
        for (x, y), c in frame(i).items():
            if 0 <= x < FW and 0 <= y < FH:
                px[i * FW + x, y] = c + (255,)
    sheet.save(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "res/drawable-nodpi/pirate.png")
