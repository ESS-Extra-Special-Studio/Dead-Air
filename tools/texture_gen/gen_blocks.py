"""16x16 broken_relay block textures (weathered copper / teal patina)."""
import os

import numpy as np

from tg_common import (COPPER, CYAN, CYAN_DARK, CYAN_LIGHT, CYAN_WHITE, GUN, PATINA, TEX, Canvas,
                       hexc, mix)

OUT = os.path.join(TEX, "block")


def weathered_base(seed):
    """Copper panel with blotchy teal oxidation, lit top-left."""
    cv = Canvas(16, 16)
    rng = np.random.RandomState(seed)
    coarse = rng.rand(5, 5)
    # cheap smooth noise: bilinear upsample of a coarse grid
    yy, xx = np.mgrid[0:16, 0:16] / 15 * 4
    x0, y0 = np.floor(xx).astype(int), np.floor(yy).astype(int)
    x1, y1 = np.minimum(x0 + 1, 4), np.minimum(y0 + 1, 4)
    fx, fy = xx - x0, yy - y0
    smooth = (coarse[y0, x0] * (1 - fx) * (1 - fy) + coarse[y0, x1] * fx * (1 - fy)
              + coarse[y1, x0] * (1 - fx) * fy + coarse[y1, x1] * fx * fy)
    fine = rng.rand(16, 16)
    for y in range(16):
        for x in range(16):
            ox = smooth[y, x] + (fine[y, x] - 0.5) * 0.25
            tone = 2 if fine[y, x] > 0.35 else 1
            if fine[y, x] > 0.9:
                tone = 3
            c = PATINA[tone] if ox > 0.55 else COPPER[tone]
            if 0.5 < ox <= 0.55:
                c = mix(COPPER[1], PATINA[1], 0.5)
            cv.px(x, y, c)
    return cv, rng


def frame(cv):
    for i in range(16):
        cv.px(i, 0, COPPER[3])
        cv.px(0, i, COPPER[3])
        cv.px(i, 15, COPPER[0])
        cv.px(15, i, COPPER[0])
    cv.px(15, 0, COPPER[1])
    cv.px(0, 15, COPPER[1])


def rivet(cv, x, y):
    cv.px(x, y, hexc("C7D3CF"))
    cv.px(x + 1, y, GUN[2])
    cv.px(x, y + 1, GUN[2])
    cv.px(x + 1, y + 1, GUN[0])


def broken_relay_side():
    cv, rng = weathered_base(11)
    frame(cv)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(cv, x, y)
    # vent slots
    for row in (6, 8, 10):
        for x in range(4, 12):
            cv.px(x, row, GUN[0])
            cv.px(x, row + 1, mix(COPPER[3], PATINA[3], 0.4) if x % 3 else COPPER[3])
        cv.px(4, row, GUN[1])
    # scratches
    for x, y in ((5, 3), (6, 4), (7, 4), (8, 5), (10, 13), (11, 12), (3, 9)):
        cv.px(x, y, hexc("E6B88A"))
    # drip of oxidation from a rivet
    for y in range(4, 7):
        cv.px(13, y, PATINA[3] if y == 4 else PATINA[2])
    return cv


def broken_relay_top():
    cv, rng = weathered_base(23)
    frame(cv)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(cv, x, y)
    # antenna socket ring
    ring = [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (5, 8), (10, 8),
            (5, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10)]
    for x, y in ring:
        lit = x + y < 15
        cv.px(x, y, GUN[3] if lit else GUN[1])
    cv.rect(6, 6, 9, 9, GUN[0])
    # stubby snapped antenna in the socket
    cv.rect(7, 7, 8, 8, GUN[2])
    cv.px(7, 7, hexc("9A9AAA"))
    cv.px(8, 8, GUN[1])
    # rust / oxidation stains
    for x, y in ((3, 7), (4, 7), (3, 8), (11, 4), (12, 5), (10, 12), (11, 11)):
        cv.px(x, y, hexc("8A3A1A") if (x + y) % 2 else hexc("A44E22"))
    for x, y in ((11, 9), (12, 9), (12, 10), (4, 3)):
        cv.px(x, y, PATINA[3])
    return cv


def broken_relay_front(active=True):
    cv, rng = weathered_base(37)
    frame(cv)
    for x, y in ((1, 1), (13, 1)):
        rivet(cv, x, y)
    # screen bezel
    cv.rect(2, 3, 13, 10, GUN[0])
    cv.rect(2, 3, 13, 3, GUN[1])
    cv.rect(2, 3, 2, 10, GUN[1])
    # screen
    if active:
        for y in range(4, 10):
            for x in range(3, 13):
                scan = (y % 2 == 0)
                cv.px(x, y, CYAN if scan else mix(CYAN, CYAN_DARK, 0.35))
        cv.pts([(4, 5), (5, 5), (3, 4)], CYAN_WHITE)
        wave = [7, 6, 6, 7, 8, 8, 7, 6, 7, 7]
        for i, y in enumerate(wave):
            cv.px(3 + i, y, CYAN_LIGHT)
        crack = hexc("0A2A4A")
        crack_hi = CYAN_WHITE
    else:
        for y in range(4, 10):
            for x in range(3, 13):
                cv.px(x, y, hexc("0C1218") if (x + y) % 3 else hexc("101A22"))
        cv.pts([(4, 5), (5, 5), (3, 4)], hexc("2A3640"))
        crack = hexc("03060A")
        crack_hi = hexc("3A4A58")
    # crack star from (10,5)
    for x, y in ((10, 5), (9, 6), (8, 7), (11, 4), (12, 4), (11, 6), (12, 7), (10, 6)):
        cv.px(x, y, crack)
    for x, y in ((9, 5), (11, 5), (12, 6)):
        cv.px(x, y, crack_hi)
    # dead buttons
    for i, x in enumerate((3, 5, 7)):
        cv.px(x, 12, GUN[1])
        cv.px(x + 1, 12, GUN[0])
        cv.px(x, 13, GUN[0])
        cv.px(x + 1, 13, hexc("0D0C10"))
    cv.px(3, 12, hexc("5A1A1A"))
    # dangling wire from a hole, bottom-right
    cv.px(11, 11, hexc("050405"))
    wire = [(11, 12), (12, 13), (12, 14), (11, 15)]
    for i, (x, y) in enumerate(wire):
        cv.px(x, y, hexc("B53A2A") if i % 2 == 0 else hexc("7A2218"))
    cv.px(13, 12, COPPER[3])
    return cv


def main():
    broken_relay_side().save(os.path.join(OUT, "broken_relay_side.png"))
    broken_relay_top().save(os.path.join(OUT, "broken_relay_top.png"))
    broken_relay_front(True).save(os.path.join(OUT, "broken_relay_front.png"))
    broken_relay_front(False).save(os.path.join(OUT, "broken_relay_front_off.png"))


if __name__ == "__main__":
    main()
