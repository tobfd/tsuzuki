"""Builds the README and store images in docs/images from raw screenshots.

Usage: python docs/images/tools/make_images.py <folder with the raw screenshots and the logo glyphs>
See docs/images/README.md for the file names it expects.
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

SHOTS = sys.argv[1] if len(sys.argv) > 1 else '.'
IMG = SHOTS
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = os.path.join(REPO, 'docs', 'images')
FONT = os.path.join(REPO, 'core', 'designsystem', 'src', 'main', 'res', 'font', 'google_sans_flex.ttf')

NAVY = (11, 22, 34)          # AniList's dark background
NAVY2 = (21, 39, 61)
BLUE = (61, 180, 242)        # AniList blue, the token seed
BLUE_DEEP = (0, 101, 142)    # tokens: light primary


def font(size, weight=500):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([min(144, max(6, size // 2)), 100, weight, 0, 0, 0])
    except Exception:
        pass
    return f


# ---------------------------------------------------------------- status bar

# kind: (status bar height, icon centre y, time x) in screenshot pixels
KINDS = {
    'phone': (140, 88, 92),     # Pixel 9, 1080 x 2424
    'emu': (150, 78, 84),       # Pixel 9 Pro XL emulator, 1344 x 2992
    'tablet': (100, 51, 56),    # emulator as tablet, 2560 x 1600
}


def clean_status_bar(im, kind):
    """Replaces the status bar with a neutral one: 12:00, Wi-Fi, signal, full battery."""
    im = im.convert('RGB')
    H, cy, tx = KINDS[kind]
    W = im.width
    strip = im.crop((0, 0, W, H))
    # Uniform background (a plain app bar) → fill with its colour; an image (banner) → blur it.
    px = list(strip.resize((W // 8, H // 8)).get_flattened_data())
    med = tuple(sorted(c[i] for c in px)[len(px) // 2] for i in range(3))
    near = sum(1 for c in px if sum(abs(c[i] - med[i]) for i in range(3)) < 24) / len(px)
    if near > 0.8:
        clean = Image.new('RGB', (W, H), med)
    else:
        # Continue the row just below the bar upwards, softened (split panes, banners).
        row = im.crop((0, H + 4, W, H + 5)).resize((W, H))
        clean = row.filter(ImageFilter.GaussianBlur(H // 3))
    lum = sum(0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2] for c in clean.resize((32, 4)).get_flattened_data()) / 128
    ink = (255, 255, 255) if lum < 140 else (28, 27, 31)
    im.paste(clean, (0, 0))
    d = ImageDraw.Draw(im)
    s = H / 140  # scale relative to the phone
    f = font(int(42 * s * (0.75 if kind == 'tablet' else 1)), 500)
    d.text((tx, cy), '12:00', font=f, fill=ink, anchor='lm')
    # Right side: Wi-Fi wedge, signal triangle, battery.
    k = s * (0.75 if kind == 'tablet' else 1)
    right = W - tx
    bw, bh = 22 * k, 38 * k
    bx0, by0 = right - bw, cy - bh / 2
    d.rounded_rectangle((bx0, by0 + 4 * k, bx0 + bw, by0 + bh), radius=4 * k, fill=ink)
    d.rectangle((bx0 + bw * 0.3, by0, bx0 + bw * 0.7, by0 + 4 * k), fill=ink)
    sx1 = bx0 - 18 * k
    d.polygon([(sx1, cy - 17 * k), (sx1, cy + 17 * k), (sx1 - 34 * k, cy + 17 * k)], fill=ink)
    wx = sx1 - 34 * k - 26 * k
    r = 24 * k
    d.pieslice((wx - r, cy + 14 * k - r, wx + r, cy + 14 * k + r), 225, 315, fill=ink)
    return im


# ---------------------------------------------------------------- frames

def rounded_mask(size, radius):
    m = Image.new('L', size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def frame(screen, kind):
    """Puts a screenshot into a simple device frame; returns RGBA with a transparent outside."""
    W, H = screen.size
    if kind == 'tablet':
        r, b = int(W * 0.028), int(W * 0.022)
    else:
        r, b = int(W * 0.105), int(W * 0.03)
    out = Image.new('RGBA', (W + 2 * b, H + 2 * b), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((0, 0, out.width - 1, out.height - 1), radius=r + b, fill=(26, 26, 30, 255))
    d.rounded_rectangle((2, 2, out.width - 3, out.height - 3), radius=r + b - 2, outline=(70, 70, 76, 255), width=3)
    out.paste(screen.convert('RGB'), (b, b), rounded_mask((W, H), r))
    if kind == 'tablet':
        cx, cy, cr = out.width // 2, b // 2, max(6, b // 6)
    else:
        cx, cy, cr = out.width // 2, b + int(W * 0.058), int(W * 0.018)
    d.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(8, 8, 10, 255))
    d.ellipse((cx - cr * 0.45, cy - cr * 0.45, cx + cr * 0.15, cy + cr * 0.15), fill=(40, 44, 60, 255))
    return out


def shadow(img, blur, offset, alpha=90):
    a = img.split()[3].point(lambda v: alpha if v > 0 else 0)
    pad = blur * 3
    sh = Image.new('RGBA', (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    sh.paste(Image.new('RGBA', img.size, (0, 0, 0, 255)), (pad, pad + offset), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    sh.alpha_composite(img, (pad, pad))
    return sh


def scaled(img, width=None, height=None):
    if width:
        return img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    return img.resize((round(img.width * height / img.height), height), Image.LANCZOS)


def gradient(size, top, bottom, diagonal=True):
    w, h = size
    g = Image.new('RGB', (1, 256))
    for y in range(256):
        t = y / 255
        g.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    g = g.resize((w, h), Image.BICUBIC) if not diagonal else g.resize((w + h, w + h), Image.BICUBIC).rotate(
        -30, resample=Image.BICUBIC).crop(((w + h - w) // 2, (w + h - h) // 2, (w + h - w) // 2 + w, (w + h - h) // 2 + h))
    return g.convert('RGBA')


def glow(canvas, center, radius, color, alpha):
    layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(
        (center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius), fill=color + (alpha,))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius // 2)))


def logo_tile(size, light=True):
    tile = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    bg = (205, 233, 255, 255) if light else (0, 76, 108, 255)
    ImageDraw.Draw(tile).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.28), fill=bg)
    glyph = Image.open(os.path.join(IMG, 'glyph_navy.png' if light else 'glyph_white.png')).convert('RGBA')
    glyph = scaled(glyph, height=int(size * 0.58))
    tile.alpha_composite(glyph, ((size - glyph.width) // 2, (size - glyph.height) // 2))
    return tile


# ---------------------------------------------------------------- inputs

PHONE = ['home', 'lists', 'detail', 'share', 'browse', 'profile']
TABLET = [('detail', 'light'), ('detail', 'dark'), ('lists', 'light'), ('lists', 'dark'), ('browse', 'light'),
          ('login', 'light')]


def load(name):
    return Image.open(os.path.join(SHOTS, name + '.png')).convert('RGB')


def main():
    for sub in ('screenshots/phone', 'screenshots/tablet', 'screenshots/widgets', 'framed', 'store'):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)

    clean = {}
    for n in PHONE:
        for m in ('light', 'dark'):
            im = clean_status_bar(load(f'phone_{n}_{m}'), 'phone')
            clean[f'phone_{n}_{m}'] = im
            im.save(os.path.join(OUT, 'screenshots/phone', f'{n}-{m}.jpg'), quality=90, optimize=True)
    for n, m in TABLET:
        im = clean_status_bar(load(f'tablet_{n}_{m}'), 'tablet')
        clean[f'tablet_{n}_{m}'] = im
        im.save(os.path.join(OUT, 'screenshots/tablet', f'{n}-{m}.jpg'), quality=90, optimize=True)
    for m in ('light', 'dark'):
        for i in (1, 2):
            im = clean_status_bar(load(f'w_{m}_{i}'), 'emu')
            clean[f'widgets{i}_{m}'] = im
        clean[f'widgets1_{m}'].save(os.path.join(OUT, 'screenshots/widgets', f'home-screen-{m}.jpg'), quality=90,
                                    optimize=True)

    # Framed, transparent images for the README gallery.
    framed = {}
    for key, im in clean.items():
        kind = 'tablet' if key.startswith('tablet') else 'phone'
        f = frame(im, kind)
        framed[key] = f
        if key.startswith('widgets2'):
            continue
        name = key.replace('phone_', '').replace('tablet_', 'tablet-').replace('widgets1_', 'widgets_').replace('_', '-')
        width = 1000 if kind == 'tablet' else 400
        scaled(f, width=width).save(os.path.join(OUT, 'framed', f'{name}.webp'), quality=88, method=6)

    # Widget cards on their own (for the store image).
    cards = {}
    for m in ('light', 'dark'):
        a, b = clean[f'widgets1_{m}'], clean[f'widgets2_{m}']
        boxes = [(a, (78, 201, 1266, 951)), (a, (78, 999, 1266, 1749)), (b, (78, 201, 1266, 951))]
        cards[m] = []
        for src, box in boxes:
            c = src.crop(box).convert('RGBA')
            c.putalpha(rounded_mask(c.size, 72))
            cards[m].append(c)

    banner(framed)
    feature_graphic(framed)
    store_images(framed, cards)
    widgets_showcase(cards)


def banner(framed):
    W, H = 1280, 640
    c = gradient((W, H), NAVY2, NAVY)
    glow(c, (980, 260), 330, BLUE, 70)
    glow(c, (150, 600), 260, BLUE_DEEP, 60)
    d = ImageDraw.Draw(c)
    tile = logo_tile(104)
    c.alpha_composite(tile, (84, 104))
    d.text((80, 318), 'Tsuzuki', font=font(92, 650), fill=(255, 255, 255), anchor='ls')
    d.text((86, 372), 'for AniList', font=font(36, 500), fill=(165, 214, 245), anchor='ls')
    d.text((86, 452), 'Keep track of what comes next.', font=font(30, 450), fill=(225, 236, 245), anchor='ls')
    d.text((86, 494), 'A native Android client, made with', font=font(24, 400), fill=(150, 170, 190), anchor='ls')
    d.text((86, 528), 'Jetpack Compose and Material 3.', font=font(24, 400), fill=(150, 170, 190), anchor='ls')
    phones = [('phone_detail_light', 660, 120, 380), ('phone_home_dark', 900, 70, 430), ('phone_browse_light', 1100, 150, 360)]
    order = [phones[0], phones[2], phones[1]]
    for key, x, y, h in order:
        p = shadow(scaled(framed[key], height=h * 2), 18, 10, 110)
        p = scaled(p, height=round(p.height / 2))
        c.alpha_composite(p, (x - p.width // 2 + 60, y))
    c.convert('RGB').save(os.path.join(OUT, 'banner.png'), optimize=True)


def feature_graphic(framed):
    W, H = 1024, 500
    c = gradient((W, H), NAVY2, NAVY)
    glow(c, (780, 200), 260, BLUE, 70)
    d = ImageDraw.Draw(c)
    c.alpha_composite(logo_tile(84), (64, 84))
    d.text((60, 262), 'Tsuzuki', font=font(76, 650), fill=(255, 255, 255), anchor='ls')
    d.text((66, 308), 'for AniList', font=font(30, 500), fill=(165, 214, 245), anchor='ls')
    d.text((66, 372), 'Keep track of what comes next.', font=font(26, 450), fill=(225, 236, 245), anchor='ls')
    for key, x, y, h in [('phone_detail_light', 640, 70, 420), ('phone_home_dark', 840, 40, 460)]:
        p = shadow(scaled(framed[key], height=h * 2), 16, 8, 110)
        p = scaled(p, height=round(p.height / 2))
        c.alpha_composite(p, (x - p.width // 2 + 40, y))
    c.convert('RGB').save(os.path.join(OUT, 'store', 'feature-graphic.png'), optimize=True)


CAPTIONS = [
    ('phone_home_dark', 'Pick up where you left off', 'In Progress with +1, and what to start next'),
    ('phone_lists_light', 'Your lists, offline first', 'Changes are saved at once and sent when you are online'),
    ('phone_detail_dark', 'Everything about a title', 'Score, ranking, characters, staff and where to watch'),
    ('phone_share_light', 'Share your progress', 'As a story or a square image'),
    ('phone_browse_dark', 'Find what comes next', 'Search with filters, trending and this season'),
    ('phone_profile_light', 'Your profile at a glance', 'Stats, activity history and favourites'),
]


def promo(screen_framed, title, subtitle, size, light_bg, device_height, top):
    W, H = size
    if light_bg:
        c = gradient((W, H), (222, 240, 252), (190, 225, 248))
        tcol, scol = (11, 22, 34), (52, 72, 92)
    else:
        c = gradient((W, H), NAVY2, NAVY)
        glow(c, (int(W * 0.8), int(H * 0.25)), int(W * 0.45), BLUE, 60)
        tcol, scol = (255, 255, 255), (170, 195, 215)
    d = ImageDraw.Draw(c)
    s = min(W, H) / 1080 if W > H else W / 1080
    d.text((W // 2, int(top * 0.42)), title, font=font(int(76 * s), 650), fill=tcol, anchor='mm')
    d.text((W // 2, int(top * 0.42) + int(84 * s)), subtitle, font=font(int(38 * s), 450), fill=scol, anchor='mm')
    p = shadow(scaled(screen_framed, height=device_height), int(30 * s), int(14 * s), 120)
    c.alpha_composite(p, ((W - p.width) // 2, top))
    return c.convert('RGB')


def store_images(framed, cards):
    for i, (key, title, sub) in enumerate(CAPTIONS, start=1):
        img = promo(framed[key], title, sub, (1080, 1920), key.endswith('_light'), 1440, 330)
        img.save(os.path.join(OUT, 'store', f'phone-{i:02d}.jpg'), quality=92, optimize=True)
    # Widgets: the three cards stacked.
    W, H = 1080, 1920
    c = gradient((W, H), NAVY2, NAVY)
    glow(c, (860, 480), 480, BLUE, 60)
    d = ImageDraw.Draw(c)
    d.text((W // 2, 139), 'Right on your home screen', font=font(76, 650), fill=(255, 255, 255), anchor='mm')
    d.text((W // 2, 223), 'In Progress with +1, the next episode, friends', font=font(38, 450),
           fill=(170, 195, 215), anchor='mm')
    y = 320
    for card in cards['light'][:1] + cards['dark'][1:2] + cards['light'][2:3]:
        card = scaled(card, width=740)
        h = card.height
        card = shadow(card, 24, 10, 110)
        c.alpha_composite(card, ((W - card.width) // 2, y - 72))
        y += h + 56
    c.convert('RGB').save(os.path.join(OUT, 'store', f'phone-{len(CAPTIONS) + 1:02d}.jpg'), quality=92, optimize=True)
    # Tablet promos.
    for i, (key, title, sub) in enumerate([
        ('tablet_detail_light', 'Made for big screens too', 'Lists and details side by side on tablets and foldables'),
        ('tablet_lists_dark', 'Light, dark and Material You', 'Your wallpaper colours, or AniList blue'),
    ], start=1):
        img = promo(framed[key], title, sub, (1920, 1080), key.endswith('_light'), 740, 250)
        img.save(os.path.join(OUT, 'store', f'tablet-{i:02d}.jpg'), quality=92, optimize=True)


def widgets_showcase(cards):
    for m in ('light', 'dark'):
        W = 1300
        gap = 36
        cs = [scaled(cd, width=600) for cd in cards[m]]
        H = cs[0].height + cs[1].height + gap * 3
        c = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        c.alpha_composite(cs[0], (gap, gap))
        c.alpha_composite(cs[1], (gap, gap * 2 + cs[0].height))
        c.alpha_composite(cs[2], (W - 600 - gap, gap))
        c.save(os.path.join(OUT, 'framed', f'widgets-cards-{m}.webp'), quality=90, method=6)


if __name__ == '__main__':
    main()
