"""Renders the logo glyph (core/designsystem ic_logo_glyph.xml) to a transparent PNG.

Usage: python logo.py <out.png> <height px> <color>, e.g. glyph_white.png 600 '#ffffff'
"""
import os
import re
import sys
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.path import Path
from matplotlib.patches import PathPatch
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
src = open(os.path.join(REPO, 'core', 'designsystem', 'src', 'main', 'res', 'drawable', 'ic_logo_glyph.xml'),
           encoding='utf-8').read()
d = re.search(r'pathData="([^"]+)"', src).group(1)
toks = re.findall(r'[MLHVQZmlhvqz]|-?\d*\.?\d+', d)
verts, codes = [], []
i = 0; cmd = None; cx = cy = 0; sx = sy = 0
def num():
    global i
    v = float(toks[i]); i += 1; return v
while i < len(toks):
    t = toks[i]
    if re.match(r'[A-Za-z]', t):
        cmd = t; i += 1
        if cmd in 'Zz':
            verts.append((sx, sy)); codes.append(Path.CLOSEPOLY); cx, cy = sx, sy; continue
    if cmd == 'M':
        cx, cy = num(), num(); sx, sy = cx, cy; verts.append((cx, cy)); codes.append(Path.MOVETO); cmd = 'L'
    elif cmd == 'L':
        cx, cy = num(), num(); verts.append((cx, cy)); codes.append(Path.LINETO)
    elif cmd == 'H':
        cx = num(); verts.append((cx, cy)); codes.append(Path.LINETO)
    elif cmd == 'V':
        cy = num(); verts.append((cx, cy)); codes.append(Path.LINETO)
    elif cmd == 'Q':
        x1, y1, x, y = num(), num(), num(), num()
        verts += [(x1, y1), (x, y)]; codes += [Path.CURVE3, Path.CURVE3]; cx, cy = x, y
    else:
        raise SystemExit('unsupported ' + str(cmd))
# Crop to the glyph's bounding box
xs = [v[0] for v in verts]; ys = [v[1] for v in verts]
x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
size = float(sys.argv[2]) if len(sys.argv) > 2 else 1024
color = sys.argv[3] if len(sys.argv) > 3 else '#ffffff'
w, h = x1 - x0, y1 - y0
fig = plt.figure(figsize=(w / h * size / 100, size / 100), dpi=100)
ax = fig.add_axes([0, 0, 1, 1]); ax.set_xlim(x0, x1); ax.set_ylim(y1, y0); ax.axis('off')
ax.add_patch(PathPatch(Path(verts, codes), facecolor=color, edgecolor='none', antialiased=True))
fig.savefig(sys.argv[1], transparent=True)
