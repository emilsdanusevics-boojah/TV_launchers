# Generates the launcher icon and the 320x180 Android TV banner.
import sys
from PIL import Image, ImageDraw, ImageFont
out = sys.argv[1]
font = "/usr/share/fonts/opentype/inter/Inter-Light.otf"

banner = Image.new("RGB", (320, 180), (16, 18, 22))
d = ImageDraw.Draw(banner)
d.text((160, 78), "12:00", font=ImageFont.truetype(font, 64), fill="white", anchor="mm")
for i in range(5):
    d.rounded_rectangle((52 + i * 45, 128, 88 + i * 45, 148), 4, fill=(60, 66, 80))
banner.save(f"{out}/banner.png")

icon = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
d = ImageDraw.Draw(icon)
d.rounded_rectangle((4, 4, 92, 92), 18, fill=(16, 18, 22))
for r in range(3):
    for c in range(3):
        d.rounded_rectangle((20 + c * 22, 20 + r * 22, 36 + c * 22, 36 + r * 22), 3, fill="white")
icon.save(f"{out}/ic_launcher.png")
