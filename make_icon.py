#!/usr/bin/env python3
"""Launcher icon: schematic FRONT view of a bus on an amber circle.

The bus (body + wheels) is centred vertically in the disc: its bounding box
y = 82..430 is symmetric around the circle centre y = 256.
"""
from PIL import Image, ImageDraw

S = 512
C = S // 2                     # 256 — centre of the disc
AMBER = (255, 176, 32, 255)
AMBER_DARK = (214, 138, 16, 255)
DARK = (11, 18, 24, 255)

img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# amber disc with a subtle darker rim
d.ellipse((6, 6, S - 6, S - 6), fill=AMBER)
d.ellipse((6, 6, S - 6, S - 6), outline=AMBER_DARK, width=12)

# --- bus geometry, centred on C -------------------------------------------
BODY_TOP, BODY_BOTTOM = 82, 390
WHEEL_TOP, WHEEL_BOTTOM = 358, 430
SHIELD_TOP, SHIELD_BOTTOM = 154, 278
BOARD_TOP, BOARD_BOTTOM = 98, 136
LIGHT_TOP, LIGHT_BOTTOM = 300, 354
MIRROR_TOP, MIRROR_BOTTOM = 174, 224

# mirrors (behind the body)
d.rounded_rectangle((84, MIRROR_TOP, 132, MIRROR_BOTTOM), radius=10, fill=DARK)
d.rounded_rectangle((380, MIRROR_TOP, 428, MIRROR_BOTTOM), radius=10, fill=DARK)
arm_y = (MIRROR_TOP + MIRROR_BOTTOM) // 2 - 7
d.rectangle((120, arm_y, 140, arm_y + 14), fill=DARK)
d.rectangle((372, arm_y, 392, arm_y + 14), fill=DARK)

# bus body (front)
d.rounded_rectangle((120, BODY_TOP, 392, BODY_BOTTOM), radius=46, fill=DARK)

# roof destination board
d.rounded_rectangle((176, BOARD_TOP, 336, BOARD_BOTTOM), radius=8, fill=AMBER)

# windshield, split in two panes
d.rounded_rectangle((148, SHIELD_TOP, 366, SHIELD_BOTTOM), radius=20, fill=AMBER)
d.rectangle((252, SHIELD_TOP, 262, SHIELD_BOTTOM), fill=DARK)

# headlights
d.ellipse((146, LIGHT_TOP, 200, LIGHT_BOTTOM), fill=AMBER)
d.ellipse((312, LIGHT_TOP, 366, LIGHT_BOTTOM), fill=AMBER)

# wheels: schematic rectangular blocks below the body (no bumper)
d.rounded_rectangle((128, WHEEL_TOP, 194, WHEEL_BOTTOM), radius=14, fill=DARK)
d.rounded_rectangle((318, WHEEL_TOP, 384, WHEEL_BOTTOM), radius=14, fill=DARK)

img.save("app/icon_master.png")

sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in sizes.items():
    img.resize((px, px), Image.LANCZOS).save(f"app/res/mipmap-{name}/ic_launcher.png")

# Tizen icon + docs preview
img.resize((192, 192), Image.LANCZOS).save("models/samsung-galaxy-watch-3/app/icon.png")
img.resize((256, 256), Image.LANCZOS).save("docs/images/icon.png")
print("icons written (bus centred)")
