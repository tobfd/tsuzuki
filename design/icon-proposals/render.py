"""Renders three app icon proposals for Tsuzuki (M12): adaptive icon in circle and squircle masks plus
the themed (monochrome) icon, from the 続 glyph path in core/designsystem's ic_logo_glyph.xml."""
import io
import os
import re

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.path import Path
from matplotlib.patches import PathPatch
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "design", "icon-proposals")
os.makedirs(OUT, exist_ok=True)

SRC = open(os.path.join(ROOT, "core", "designsystem", "src", "main", "res", "drawable", "ic_logo_glyph.xml"), encoding="utf-8").read()
PATH_DATA = re.findall(r'pathData="([^"]+)"', SRC)[0]

S = 1024  # canvas = the 108 dp adaptive icon, 1024 px


def glyph_path():
    tokens = re.findall(r"[A-Za-z]|-?\d*\.?\d+", PATH_DATA)
    verts, codes = [], []
    i, cmd, cur, start = 0, None, (0, 0), (0, 0)
    nums = []

    def take(n):
        nonlocal i
        vals = [float(tokens[i + k]) for k in range(n)]
        i += n
        return vals

    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                verts.append(start)
                codes.append(Path.CLOSEPOLY)
            continue
        if cmd == "M":
            x, y = take(2); cur = start = (x, y); verts.append(cur); codes.append(Path.MOVETO); cmd = "L"
        elif cmd == "L":
            x, y = take(2); cur = (x, y); verts.append(cur); codes.append(Path.LINETO)
        elif cmd == "H":
            (x,) = take(1); cur = (x, cur[1]); verts.append(cur); codes.append(Path.LINETO)
        elif cmd == "V":
            (y,) = take(1); cur = (cur[0], y); verts.append(cur); codes.append(Path.LINETO)
        elif cmd == "Q":
            cx, cy, x, y = take(4); verts += [(cx, cy), (x, y)]; codes += [Path.CURVE3, Path.CURVE3]; cur = (x, y)
        else:
            raise ValueError(cmd)
    return Path(verts, codes)


GLYPH = glyph_path()


def render_glyph(color, scale=1.0, dx=0.0, dy=0.0):
    """The glyph on a transparent 108-unit canvas, scaled around the centre."""
    fig = plt.figure(figsize=(1, 1), dpi=S)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, 108)
    ax.set_ylim(108, 0)
    ax.axis("off")
    v = GLYPH.vertices.copy()
    v[:, 0] = (v[:, 0] - 54) * scale + 54 + dx
    v[:, 1] = (v[:, 1] - 54) * scale + 54 + dy
    ax.add_patch(PathPatch(Path(v, GLYPH.codes), facecolor=color, edgecolor="none"))
    buf = io.BytesIO()
    fig.savefig(buf, format="png", transparent=True)
    plt.close(fig)
    return Image.open(io.BytesIO(buf.getvalue())).convert("RGBA").resize((S, S), Image.LANCZOS)


def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(top, bottom):
    a, b = hex2rgb(top), hex2rgb(bottom)
    img = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(img)
    for y in range(S):
        t = y / (S - 1)
        d.line([(0, y), (S, y)], fill=tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3)) + (255,))
    return img


def u(x):  # 108 dp units to px
    return round(x * S / 108)


# --- A: "Blau" - the glyph in white on AniList's blue, the refined placeholder ---------------------
def icon_a():
    bg = gradient("#3DB4F2", "#0284C7")
    bg.alpha_composite(render_glyph("#FFFFFF"))
    return bg, render_glyph("#000000")


# --- B: "Weiter" - the glyph above a progress bar that runs on (what comes next) -----------------
def icon_b():
    bg = gradient("#152232", "#0B1622")
    bg.alpha_composite(render_glyph("#3DB4F2", scale=0.86, dy=-5))
    d = ImageDraw.Draw(bg)
    y0, y1 = u(76), u(80.5)
    d.rounded_rectangle([u(34), y0, u(74), y1], radius=u(2.25), fill="#2B3A4C")
    d.rounded_rectangle([u(34), y0, u(60), y1], radius=u(2.25), fill="#3DB4F2")
    d.ellipse([u(57.5), u(74.2), u(62.5), u(82.3)], fill="#FFFFFF")
    mono = render_glyph("#000000", scale=0.86, dy=-5)
    md = ImageDraw.Draw(mono)
    md.rounded_rectangle([u(34), y0, u(60), y1], radius=u(2.25), fill="#000000")
    return bg, mono


# --- C: "Cover" - two stacked 2:3 covers, the front one carries the glyph -----------------------
def icon_c():
    bg = Image.new("RGBA", (S, S), "#E8F4FD")
    d = ImageDraw.Draw(bg)
    d.rounded_rectangle([u(41), u(28), u(73), u(76)], radius=u(5), fill="#9AD4F7")
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([u(35), u(34), u(67), u(82)], radius=u(5), fill=(0, 40, 80, 70))
    bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(u(1.5))))
    d.rounded_rectangle([u(34), u(32), u(66), u(80)], radius=u(5), fill="#0284C7")
    bg.alpha_composite(render_glyph("#FFFFFF", scale=0.55, dx=-4, dy=2))
    mono = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    md = ImageDraw.Draw(mono)
    md.rounded_rectangle([u(41), u(28), u(73), u(76)], radius=u(5), outline="#000000", width=u(3))
    md.rounded_rectangle([u(34), u(32), u(66), u(80)], radius=u(5), fill="#000000")
    cut = render_glyph("#000000", scale=0.55, dx=-4, dy=2)
    mono = Image.composite(Image.new("RGBA", (S, S), (0, 0, 0, 0)), mono, cut.split()[3])
    return bg, mono


def mask(img, shape, size):
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    inset = u(9)  # launchers show the inner 72 x 72 of 108 plus a margin; crop like Pixel Launcher
    box = [inset, inset, S - inset, S - inset]
    if shape == "circle":
        d.ellipse(box, fill=255)
    else:
        d.rounded_rectangle(box, radius=round((S - 2 * inset) * 0.3), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), m)
    return out.crop(box).resize((size, size), Image.LANCZOS)


def themed(mono, light, size):
    bgc, fg = ("#D8E2FF", "#2B4678") if light else ("#2B4678", "#D8E2FF")
    tile = Image.new("RGBA", (S, S), bgc)
    tint = Image.new("RGBA", (S, S), fg)
    tile.paste(tint, (0, 0), mono.split()[3])
    return mask(tile, "circle", size)


def sheet(name, title, subtitle, icon, mono):
    W, H = 1500, 560
    out = Image.new("RGBA", (W, H), "#F4F6F8")
    d = ImageDraw.Draw(out)
    try:
        f1 = ImageFont.truetype("seguisb.ttf", 40)
        f2 = ImageFont.truetype("segoeui.ttf", 26)
    except OSError:
        f1 = f2 = ImageFont.load_default()
    d.text((40, 30), title, fill="#111", font=f1)
    d.text((40, 84), subtitle, fill="#444", font=f2)
    x = 40
    for label, img in [
        ("Circle", mask(icon, "circle", 300)),
        ("Squircle", mask(icon, "squircle", 300)),
        ("Themed light", themed(mono, True, 300)),
        ("Themed dark", themed(mono, False, 300)),
    ]:
        out.alpha_composite(img, (x, 150))
        d.text((x, 470), label, fill="#333", font=f2)
        x += 350
    out.convert("RGB").save(os.path.join(OUT, f"{name}.png"))
    small = Image.new("RGBA", (380, 120), "#0B1622")
    for i, sz in enumerate([96, 64, 48]):
        small.alpha_composite(mask(icon, "circle", sz), (10 + i * 130, (120 - sz) // 2))
    return small


smalls = []
for name, title, sub, (icon, mono) in [
    ("a-blau", "A · Blau", "The glyph in white on AniList blue: the current placeholder, refined.", icon_a()),
    ("b-weiter", "B · Weiter", "Dark AniList navy, blue glyph, a progress bar that runs on: \"what comes next\".", icon_b()),
    ("c-cover", "C · Cover", "Two stacked 2:3 covers, the front one carries the glyph: a list of shows.", icon_c()),
]:
    smalls.append(sheet(name, title, sub, icon, mono))

over = Image.new("RGBA", (380, 120 * 3 + 20), "#0B1622")
for i, sm in enumerate(smalls):
    over.alpha_composite(sm, (0, i * 125))
over.convert("RGB").save(os.path.join(OUT, "small-sizes.png"))
print("written to", OUT)
