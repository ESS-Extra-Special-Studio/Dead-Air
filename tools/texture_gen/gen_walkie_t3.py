"""walkie_t3.png: HSV remap of walkie_t2.png (same size / UV layout).

Olive body -> dark gunmetal with a purple undertone.
Bright / saturated accents (screen, dial, LEDs) -> electric cyan #2FA8FF.
Neutral greys stay grey with a slight cool tint. Value structure is kept.
"""
import colorsys
import os

import numpy as np
from PIL import Image

from tg_common import TEX

SRC = os.path.join(TEX, "item", "walkie_t2.png")
DST = os.path.join(TEX, "item", "walkie_t3.png")

CYAN_H = 205 / 360
BODY_H = 268 / 360


def remap(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    hd = h * 360
    accent = (v >= 0.42 and s >= 0.25) or (140 <= hd <= 230 and s >= 0.3 and v >= 0.15)
    if s < 0.12:
        # neutral metal parts: keep value, cool/purple tint
        nh, ns, nv = BODY_H, 0.10, v * 0.95
    elif accent:
        nh = CYAN_H
        ns = max(0.55, min(0.85, s + 0.1))
        if v > 0.85:
            ns = 0.25  # hot highlight
        nv = min(1.0, 0.30 + v * 0.80)
    else:
        nh = BODY_H
        ns = 0.22 + 0.10 * s
        nv = 0.03 + v * 0.85
    nr, ng, nb = colorsys.hsv_to_rgb(nh, ns, nv)
    return int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255))


def main():
    a = np.array(Image.open(SRC).convert("RGBA"))
    out = a.copy()
    cache = {}
    ys, xs = np.nonzero(a[..., 3] > 0)
    for y, x in zip(ys, xs):
        key = tuple(int(c) for c in a[y, x, :3])
        if key not in cache:
            cache[key] = remap(*key)
        out[y, x, :3] = cache[key]
    Image.fromarray(out, "RGBA").save(DST)
    print("wrote", DST, out.shape)


if __name__ == "__main__":
    main()
