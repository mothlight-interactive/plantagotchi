"""Builds store screenshots and the Play feature graphic from raw app renders.

Usage: python3 tools/store/store_assets.py <raw_dir> <out_dir> <icon_art.png>
raw_dir holds <lang>/01..05.png (rendered by the desktop shot test, see docs/store-assets.md).
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = os.path.join(ROOT, 'shared/src/commonMain/composeResources/font/pixelmix_bold.ttf')
BG = (0x2B, 0x1D, 0x24)
BG2 = (0x35, 0x25, 0x2D)
YELLOW = (0xFF, 0xEB, 0x57)
WHITE = (0xED, 0xED, 0xED)

CAPTIONS = {
    'en': ['Grow your own\npixel plants', 'Water, feed\nand cuddle them', 'Ten species,\neach one unique', 'Real weather\nwhere you live', 'Cozy scenes\nand pretty pots'],
    'de': ['Zieh deine eigenen\nPixelpflanzen gross', 'Giessen, düngen\nund streicheln', 'Zehn Arten,\njede einzigartig', 'Echtes Wetter\nbei dir', 'Gemütliche Szenen\nund schöne Töpfe'],
    'fr': ['Fais pousser tes\nplantes en pixels', 'Arrose, nourris\net câline-les', 'Dix espèces,\ntoutes uniques', 'La vraie météo\nde chez toi', 'Scènes douillettes\net jolis pots'],
    'es': ['Cultiva tus propias\nplantas pixeladas', 'Riégalas, abónalas\ny mímalas', 'Diez especies,\ncada una única', 'El tiempo real\nde tu zona', 'Escenas acogedoras\ny macetas bonitas'],
}
TAGLINE = {
    'en': 'A cozy pixel plant to care for',
    'de': 'Eine gemütliche Pixelpflanze zum Umsorgen',
    'fr': 'Une plante en pixels à chouchouter',
    'es': 'Una planta pixelada para cuidar',
}
FORMATS = {'iphone-6.9': (1320, 2868), 'android-phone': (1080, 1920)}


def pattern(w, h, cell):
    im = Image.new('RGB', (w, h), BG)
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell * 2):
        for x in range(0, w, cell * 2):
            if (x // (cell * 2) + y // (cell * 2)) % 2 == 0:
                d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=BG2)
    return im


def fit_font(text, max_w, size):
    while size > 10:
        f = ImageFont.truetype(FONT, size)
        if max(f.getbbox(line)[2] for line in text.split('\n')) <= max_w:
            return f
        size -= 2
    return ImageFont.truetype(FONT, size)


def screenshot(raw, caption, w, h):
    im = pattern(w, h, max(4, w // 90))
    d = ImageDraw.Draw(im)
    top_h = int(h * 0.2)
    font = fit_font(caption, int(w * 0.88), int(w * 0.075))
    lines = caption.split('\n')
    lh = int(font.size * 1.35)
    y = (top_h - lh * len(lines)) // 2 + int(h * 0.01)
    for i, line in enumerate(lines):
        tw = font.getbbox(line)[2]
        col = YELLOW if i == 0 else WHITE
        d.text(((w - tw) // 2 + max(2, w // 300), y + max(2, w // 300)), line, font=font, fill=(0, 0, 0))
        d.text(((w - tw) // 2, y), line, font=font, fill=col)
        y += lh
    # Device frame
    shot_h = h - top_h - int(h * 0.03)
    shot_w = int(shot_h * raw.width / raw.height)
    if shot_w > w * 0.86:
        shot_w = int(w * 0.86)
        shot_h = int(shot_w * raw.height / raw.width)
    s = raw.convert('RGB').resize((shot_w, shot_h), Image.LANCZOS)
    r = int(shot_w * 0.07)
    bezel = max(6, shot_w // 40)
    x0 = (w - shot_w) // 2
    y0 = top_h
    d.rounded_rectangle((x0 - bezel, y0 - bezel, x0 + shot_w + bezel, y0 + shot_h + bezel), radius=r + bezel, fill=(0x14, 0x12, 0x1F))
    mask = Image.new('L', (shot_w, shot_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot_w - 1, shot_h - 1), radius=r, fill=255)
    im.paste(s, (x0, y0), mask)
    return im


def feature(lang, raw, icon_art):
    w, h = 1024, 500
    im = pattern(w, h, 8)
    d = ImageDraw.Draw(im)
    # Scene strip on the right
    crop = raw.convert('RGB')
    cw = crop.width
    part = crop.crop((0, int(crop.height * 0.08), cw, int(crop.height * 0.08) + int(cw * 1.1)))
    part = part.resize((int(h * 0.92 / 1.1), int(h * 0.92)), Image.LANCZOS)
    px = w - part.width - 24
    mask = Image.new('L', part.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, part.width - 1, part.height - 1), radius=24, fill=255)
    d.rounded_rectangle((px - 6, 14, px + part.width + 6, 20 + part.height + 6), radius=30, fill=(0x14, 0x12, 0x1F))
    im.paste(part, (px, 20), mask)
    # Icon plant, title, tagline
    art = Image.open(icon_art).convert('RGBA')
    art = art.crop(art.getbbox())
    scale = 2
    art = art.resize((art.width * scale, art.height * scale), Image.NEAREST)
    im.paste(art, (40, h - art.height - 40), art)
    title = ImageFont.truetype(FONT, 50)
    d.text((44, 52), 'Plantagotchi', font=title, fill=(0, 0, 0))
    d.text((40, 48), 'Plantagotchi', font=title, fill=YELLOW)
    tf = fit_font(TAGLINE[lang], px - 260, 30)
    words = TAGLINE[lang].split(' ')
    lines, cur = [], ''
    for wd in words:
        t = (cur + ' ' + wd).strip()
        if tf.getbbox(t)[2] > px - 80 - art.width - 20 and cur:
            lines.append(cur); cur = wd
        else:
            cur = t
    lines.append(cur)
    y = 140
    for line in lines:
        d.text((42, y), line, font=tf, fill=WHITE)
        y += 38
    return im


def main():
    raw_dir, out, icon_art = sys.argv[1:4]
    for lang, caps in CAPTIONS.items():
        for fmt, (w, h) in FORMATS.items():
            d = os.path.join(out, 'screenshots', fmt, lang)
            os.makedirs(d, exist_ok=True)
            for i, cap in enumerate(caps):
                raw = Image.open(os.path.join(raw_dir, lang, '%02d.png' % (i + 1)))
                screenshot(raw, cap, w, h).save(os.path.join(d, '%02d.png' % (i + 1)))
        os.makedirs(os.path.join(out, 'feature'), exist_ok=True)
        raw = Image.open(os.path.join(raw_dir, lang, '01.png'))
        feature(lang, raw, icon_art).save(os.path.join(out, 'feature', 'feature-graphic-1024x500-%s.png' % lang))


if __name__ == '__main__':
    main()
