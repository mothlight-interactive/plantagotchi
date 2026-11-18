import sys, random, math
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]
N = 128
def bg(n=N, ground=True, gfrac=0.80):
    im = Image.new('RGBA', (n, n))
    px = im.load()
    bands = [(0x3E,0x96,0xEE),(0x4A,0xA3,0xF2),(0x58,0xB0,0xF5),(0x68,0xBC,0xF7),(0x7A,0xC7,0xF8),(0x8E,0xD2,0xF9)]
    gtop = int(n*gfrac)
    for y in range(n):
        # dithered band boundaries
        f = y / gtop * (len(bands)-1)
        for x in range(n):
            i = int(f); fr = f - i
            if i < len(bands)-1 and fr > 0.75 and (x + y) % 2 == 0: i += 1
            px[x, y] = bands[min(i, len(bands)-1)] + (255,)
    d = ImageDraw.Draw(im)
    def cloud(cx, cy, s):
        random.seed(cx*7+cy)
        cells=set()
        puffs=[(cx,cy,s),(cx-s*1.1,cy+s*0.35,s*0.7),(cx+s*1.2,cy+s*0.3,s*0.75),(cx+s*0.4,cy-s*0.2,s*0.8)]
        bottom = cy + s*0.8
        for x in range(int(cx-3*s), int(cx+3*s)):
            for y in range(int(cy-2*s), int(bottom)+1):
                if any((x+.5-a)**2+(y+.5-b)**2 <= r*r for a,b,r in puffs) and y <= bottom:
                    cells.add((x,y))
        for (x,y) in cells:
            if 0<=x<n and 0<=y<n:
                c=(0xE6,0xEE,0xF6)
                if (x,y-1) not in cells: c=(0xFF,0xFF,0xFF)
                elif (x,y+1) not in cells or (x,y+2) not in cells: c=(0xBF,0xCB,0xDA)
                px[x,y]=c+(255,)
    cloud(int(n*0.2), int(n*0.17), n*0.065)
    cloud(int(n*0.86), int(n*0.27), n*0.05)
    if ground:
        g=[(0x5C,0xB8,0x4A),(0x4C,0xA2,0x3E),(0x3F,0x8C,0x36)]
        for y in range(gtop, n):
            for x in range(n):
                k = 0 if y < gtop+2 else (1 if y < gtop+9 else 2)
                if k==1 and (x*13+y*7)%11==0: k=0
                if k==2 and (x*5+y*3)%9==0: k=1
                px[x,y]=g[k]+(255,)
        for x in range(0,n,3):
            h=(x*17)%3
            for j in range(h):
                px[x, gtop-1-j]=g[0]+(255,)
    return im, gtop

def plant(img_path):
    p = Image.open(img_path).convert('RGBA')
    return p.crop(p.getbbox())

pl = plant(src)
# iOS: 1024, 8 px per art pixel
im, gtop = bg()
x = (N - pl.width)//2
y = gtop + 3 - pl.height
sh = Image.new('RGBA',(N,N)); ImageDraw.Draw(sh).ellipse((x+pl.width//2-24, gtop, x+pl.width//2+24, gtop+6), fill=(0x2E,0x6E,0x2A,255)); im.alpha_composite(sh)
im.alpha_composite(pl, (x, y))
ios = im.resize((1024,1024), Image.NEAREST).convert('RGB')
ios.save(out + '/AppIcon.png')
# Android adaptive: 108dp layer, safe circle 66dp. Art grid 108 -> plant fills ~60 %.
A = 180
fore = Image.new('RGBA', (A, A))
back, ag = bg(A, gfrac=0.72)
fx = (A - pl.width)//2; fy = ag + 3 - pl.height
sh = Image.new('RGBA',(A,A)); ImageDraw.Draw(sh).ellipse((fx+pl.width//2-24, ag, fx+pl.width//2+24, ag+6), fill=(0x2E,0x6E,0x2A,255)); back.alpha_composite(sh)
fore.alpha_composite(pl, (fx, fy))
fore.save(out + '/fore_art.png'); back.save(out + '/back_art.png')

import os
res = out + '/res'
for d, layer, legacy in [('mdpi',108,48),('hdpi',162,72),('xhdpi',216,96),('xxhdpi',324,144),('xxxhdpi',432,192)]:
    os.makedirs(f'{res}/mipmap-{d}', exist_ok=True)
    def up(a, size): return a.resize((a.width*4, a.height*4), Image.NEAREST).resize((size,size), Image.BOX)
    f = up(fore, layer); b = up(back, layer)
    for pre in ['ic_launcher', 'ic_launcher_round']:
        f.save(f'{res}/mipmap-{d}/{pre}_adaptive_fore.png'); b.save(f'{res}/mipmap-{d}/{pre}_adaptive_back.png')
    full = Image.alpha_composite(back, fore)
    # legacy: visible 72/108 of the layer
    c = (A - A*72//108)//2
    leg = up(full.crop((c,c,A-c,A-c)), legacy)
    m = Image.new('L',(legacy*4,legacy*4)); ImageDraw.Draw(m).rounded_rectangle((0,0,legacy*4-1,legacy*4-1), radius=legacy*4//6, fill=255)
    sq = leg.copy(); sq.putalpha(m.resize((legacy,legacy), Image.LANCZOS)); sq.save(f'{res}/mipmap-{d}/ic_launcher.png')
    m = Image.new('L',(legacy*4,legacy*4)); ImageDraw.Draw(m).ellipse((0,0,legacy*4-1,legacy*4-1), fill=255)
    rd = leg.copy(); rd.putalpha(m.resize((legacy,legacy), Image.LANCZOS)); rd.save(f'{res}/mipmap-{d}/ic_launcher_round.png')
