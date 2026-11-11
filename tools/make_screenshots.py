#!/usr/bin/env python3
"""Composes marketing screenshots from the real UI renders.

Usage: python3 tools/make_screenshots.py <rendered_dir> <out_dir> [scale]
The renders come from `Preview <fixtures> <dir> 2` (932x932 PNGs).
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REG = "/System/Library/Fonts/Supplemental/Arial.ttf"

BG_TOP = (10, 16, 22)
BG_BOTTOM = (19, 32, 43)
AMBER = (255, 176, 32)
TEXT = (244, 248, 251)
MUTED = (159, 179, 196)
BEZEL = (14, 22, 29)
RING = (43, 59, 73)


def font(size, bold=True):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(img)
    for y in range(h):
        k = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=(
            int(top[0] + (bottom[0] - top[0]) * k),
            int(top[1] + (bottom[1] - top[1]) * k),
            int(top[2] + (bottom[2] - top[2]) * k)))
    return img


def glow(canvas, cx, cy, radius, color=AMBER, strength=70):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(10, 0, -1):
        r = int(radius * i / 10)
        a = int(strength * (1 - i / 10) ** 1.6)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (a,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius // 6))
    canvas.alpha_composite(layer)


def wrap(draw, text, fnt, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        probe = (cur + " " + w).strip()
        if draw.textlength(probe, font=fnt) <= max_w:
            cur = probe
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def watch(screen_path, diameter):
    """Round watch: shadow, bezel and the app screen clipped into a circle."""
    screen = Image.open(screen_path).convert("RGBA").resize((diameter, diameter), Image.LANCZOS)
    mask = Image.new("L", (diameter, diameter), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, diameter - 1, diameter - 1), fill=255)
    screen.putalpha(mask)

    pad = 26
    size = diameter + pad * 2
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((pad, pad, size - pad, size - pad), fill=BEZEL)
    d.ellipse((pad, pad, size - pad, size - pad), outline=RING, width=10)
    img.alpha_composite(screen, (pad, pad))
    return img


def drop_shadow(canvas, box, radius=40, alpha=150):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=(0, 0, 0, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(radius))
    canvas.alpha_composite(layer)


def card(shot, kicker, title, subtitle, out, w=1200, h=1500):
    canvas = gradient(w, h, BG_TOP, BG_BOTTOM).convert("RGBA")
    glow(canvas, w // 2, 880, 700, strength=90)

    d = ImageDraw.Draw(canvas)
    d.text((w // 2, 110), kicker, font=font(22), fill=AMBER, anchor="ma")
    d.text((w // 2, 165), title, font=font(58), fill=TEXT, anchor="ma")
    y = 250
    for line in wrap(d, subtitle, font(28, False), w - 260):
        d.text((w // 2, y), line, font=font(28, False), fill=MUTED, anchor="ma")
        y += 40

    diameter = 780
    art = watch(shot, diameter)
    x = (w - art.width) // 2
    yy = 760 - art.height // 2
    drop_shadow(canvas, (x + 30, yy + 54, x + art.width - 30, yy + art.height - 10), 46, 190)
    canvas.alpha_composite(art, (x, yy))

    d = ImageDraw.Draw(canvas)
    d.ellipse((w // 2 - 88, h - 120, w // 2 - 76, h - 108), fill=AMBER)
    d.text((w // 2 - 62, h - 126), "Автобусы", font=font(30), fill=TEXT)
    d.text((w // 2 - 62, h - 84), "живые данные · 22 города", font=font(20, False), fill=MUTED)
    canvas.convert("RGB").save(out, quality=95)
    print("card:", os.path.basename(out))


def hero(renders, out, w=2400, h=1200):
    canvas = gradient(w, h, (8, 13, 18), (17, 29, 39)).convert("RGBA")
    glow(canvas, int(w * 0.68), h // 2, 900, strength=80)

    d = ImageDraw.Draw(canvas)
    d.text((150, 300), "Автобусы", font=font(120), fill=TEXT)
    d.text((150, 450), "живые автобусы, троллейбусы", font=font(44, False), fill=MUTED)
    d.text((150, 505), "и трамваи на умных часах", font=font(44, False), fill=MUTED)
    d.rectangle((150, 600, 330, 608), fill=AMBER)
    chips = ["22 города России", "GPS-автоопределение", "Уведомление о прибытии"]
    y = 660
    for c in chips:
        d.rounded_rectangle((150, y, 150 + d.textlength(c, font=font(30, False)) + 60, y + 62),
                            radius=31, outline=(58, 78, 96), width=2)
        d.text((180, y + 15), c, font=font(30, False), fill=TEXT)
        y += 84

    sizes = [420, 470, 420]
    gap = 60
    total = sum(sizes) + gap * 2
    x = w - total - 90
    # never let the watches reach the text column
    text_right = 150 + max(d.textlength(t, font=font(120)) for t in ["Автобусы"])
    assert x > text_right + 60, "hero: watches overlap the text"
    for path, size in zip(renders, sizes):
        art = watch(path, size)
        yy = h // 2 - art.height // 2 + (30 if size == 470 else 0)
        drop_shadow(canvas, (x + 20, yy + 40, x + art.width - 20, yy + art.height - 4), 34, 170)
        canvas.alpha_composite(art, (x, yy))
        x += size + gap
    canvas.convert("RGB").save(out, quality=95)
    print("hero:", os.path.basename(out))


def banner(icon, out, w=1280, h=640):
    canvas = gradient(w, h, (8, 13, 18), (20, 33, 44)).convert("RGBA")
    glow(canvas, 1120, 120, 520, strength=70)
    ic = Image.open(icon).convert("RGBA").resize((300, 300), Image.LANCZOS)
    canvas.alpha_composite(ic, (110, (h - 300) // 2))
    d = ImageDraw.Draw(canvas)
    d.text((470, 190), "Автобусы", font=font(84), fill=TEXT)
    d.text((474, 300), "Автобусы, троллейбусы и трамваи", font=font(32, False), fill=MUTED)
    d.text((474, 344), "на Wear OS, HarmonyOS и Tizen", font=font(32, False), fill=MUTED)
    d.rectangle((474, 410, 590, 416), fill=AMBER)
    d.text((474, 440), "Открытый исходный код · MIT", font=font(26, False), fill=(120, 140, 158))
    canvas.convert("RGB").save(out, quality=95)
    print("banner:", os.path.basename(out))


if __name__ == "__main__":
    renders = sys.argv[1]
    out = sys.argv[2]
    os.makedirs(out, exist_ok=True)
    r = lambda n: os.path.join(renders, n)

    card(r("13-spb-routes.png"), "МАРШРУТЫ", "Выбор маршрута",
         "Все автобусы, троллейбусы и трамваи города в одном списке",
         os.path.join(out, "01-routes.png"))
    card(r("14-spb-stops.png"), "ОСТАНОВКИ", "Где автобус",
         "Зелёные кружки — машина уже здесь, ~N′ — через сколько минут",
         os.path.join(out, "02-stops.png"))
    card(r("15-spb-stop-menu.png"), "ДЕЙСТВИЯ", "Я здесь и жду",
         "Отметьте остановку — часы завибрируют и разбудят вас при прибытии",
         os.path.join(out, "03-stop-menu.png"))
    card(r("17-spb-cities.png"), "ГОРОДА", "22 города России",
         "Город определяется по GPS или выбирается вручную в настройках",
         os.path.join(out, "04-cities.png"))
    card(r("16-spb-settings.png"), "НАСТРОЙКИ", "Всё под рукой",
         "Город, автопозиция, ожидание автобуса и ручная метка",
         os.path.join(out, "05-settings.png"))
    card(r("18-spb-error.png"), "НАДЁЖНОСТЬ", "Понятные ошибки",
         "Подскажем, что включить: Wi-Fi, Bluetooth или LTE",
         os.path.join(out, "06-error.png"))

    hero([r("13-spb-routes.png"), r("14-spb-stops.png"), r("16-spb-settings.png")],
         os.path.join(out, "hero.png"))
    banner("docs/images/icon.png", os.path.join(out, "github-banner.png"))
