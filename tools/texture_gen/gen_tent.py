"""Generate crisp fabric-look tent canvas textures (16x16, not wood)."""
import os

import numpy as np

from tg_common import TEX, Canvas, hexc, mix

OUT = os.path.join(TEX, "block")

CANVAS = [hexc("6B5638"), hexc("8A6F48"), hexc("A88858"), hexc("C4A46E")]
CANVAS_DARK = [hexc("3A2E20"), hexc("4E3E2A"), hexc("66523A"), hexc("7D6548")]
POLE = [hexc("1E1812"), hexc("34281C"), hexc("4E3C28"), hexc("6A5236")]


def weave(cv: Canvas, palette, seed: int):
    rng = np.random.RandomState(seed)
    for y in range(cv.h):
        for x in range(cv.w):
            # Clear basket weave (2x2)
            cell = ((x // 2) + (y // 2)) % 2
            sub = (x % 2) + 2 * (y % 2)
            if cell == 0:
                tone = 2 if sub in (0, 3) else 1
            else:
                tone = 1 if sub in (0, 3) else 0
            # Warp threads every other column slightly darker
            if x % 4 == 0:
                tone = max(0, tone - 1)
            # Hem / stitch every 8
            if x % 8 == 7 or y % 8 == 7:
                tone = 0
            # Sparse wear
            if rng.rand() > 0.93:
                tone = min(3, tone + 1)
            elif rng.rand() < 0.05:
                tone = max(0, tone - 1)
            cv.px(x, y, palette[tone])


def pole_tex():
    cv = Canvas(16, 16)
    rng = np.random.RandomState(3)
    for y in range(16):
        for x in range(16):
            tone = 1
            if x in (0, 15):
                tone = 0
            elif x in (1, 14):
                tone = 2
            if y % 5 == 0:
                tone = min(3, tone + 1)
            if rng.rand() > 0.9:
                tone = max(0, min(3, tone + rng.choice([-1, 1])))
            cv.px(x, y, POLE[tone])
    return cv


def main():
    os.makedirs(OUT, exist_ok=True)
    c = Canvas(16, 16); weave(c, CANVAS, 41); c.save(os.path.join(OUT, "tent_canvas.png"))
    d = Canvas(16, 16); weave(d, CANVAS_DARK, 77); d.save(os.path.join(OUT, "tent_canvas_dark.png"))
    pole_tex().save(os.path.join(OUT, "tent_pole.png"))
    print("Wrote crisp tent canvas textures")


if __name__ == "__main__":
    main()
