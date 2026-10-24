"""16x16 item icons for Dead Air (dark 1px outline, 4-tone, top-left light)."""
import math
import os

import numpy as np

from tg_common import (CLEAR, COPPER, CYAN, CYAN_DARK, CYAN_LIGHT, CYAN_WHITE, GUN, OUTLINE, PURPLE,
                       PURPLE_DARK, PURPLE_LIGHT, TEX, Canvas, circle_mask, hexc, mix)

OUT = os.path.join(TEX, "item")


def signal_decoder():
    cv = Canvas(16, 16)
    m = np.zeros((16, 16), bool)
    m[3:15, 3:13] = True
    m[3, 3] = m[3, 12] = m[14, 3] = m[14, 12] = False
    cv.fill_mask(m, GUN)
    # antenna stub
    cv.pts([(11, 1), (11, 2)], GUN[2])
    cv.px(11, 0, CYAN)
    # screen
    cv.rect(5, 5, 10, 9, hexc("0B1A2A"))
    cv.rect(5, 5, 10, 5, hexc("12283E"))
    wave = [8, 7, 6, 7, 8, 6]
    for i, y in enumerate(wave):
        cv.px(5 + i, y, CYAN if i % 2 else CYAN_LIGHT)
    cv.px(5, 9, CYAN_DARK)
    # dial (copper)
    for x, y in [(9, 11), (10, 11), (9, 12), (10, 12)]:
        cv.px(x, y, COPPER[2])
    cv.px(9, 11, COPPER[3])
    cv.px(10, 12, COPPER[0])
    cv.px(10, 11, OUTLINE)  # pointer notch
    # buttons
    cv.px(5, 11, GUN[0])
    cv.px(6, 11, GUN[0])
    cv.px(5, 12, hexc("7A1F2A"))
    cv.px(6, 12, GUN[0])
    cv.outline()
    return cv


def relay_log():
    cv = Canvas(16, 16)
    rng = np.random.RandomState(7)
    paper = [hexc("7E7058"), hexc("B3A684"), hexc("D2C7A3"), hexc("EAE2C6")]
    m = np.zeros((16, 16), bool)
    m[1:15, 3:13] = True
    # torn right edge + bottom
    for y in range(1, 15):
        cut = rng.randint(0, 3)
        for x in range(13 - cut, 13):
            m[y, x] = False
    for x in range(3, 13):
        cut = rng.randint(0, 3)
        for y in range(15 - cut, 15):
            m[y, x] = False
    m[1, 3] = False
    # fold-shadow: slight skew
    m[1:4, 3] = False
    cv.fill_mask(m, paper)
    # burnt edges: pixels adjacent to the torn boundary on right/bottom
    burnt = [hexc("3A2414"), hexc("5C3A1E"), hexc("1A120C")]
    for y in range(16):
        for x in range(16):
            if not m[y, x]:
                continue
            edge_r = x + 1 >= 16 or not m[y, x + 1]
            edge_b = y + 1 >= 16 or not m[y + 1, x]
            if (edge_r and x > 8) or (edge_b and y > 10):
                cv.px(x, y, burnt[rng.randint(0, 3)])
            elif ((x + 1 < 16 and x + 2 < 16 and not m[y, min(15, x + 2)] and x > 8)
                  or (y + 2 < 16 and not m[y + 2, x] and y > 10)):
                cv.px(x, y, paper[0])
    # cyan printout lines (broken)
    for row, (x0, x1) in zip((4, 6, 8, 10, 12), ((5, 10), (5, 11), (5, 9), (5, 10), (5, 7))):
        for x in range(x0, x1 + 1):
            if m[row, x] and rng.rand() > 0.22:
                cv.px(x, row, mix(CYAN, paper[2], 0.35) if rng.rand() > 0.3 else CYAN_DARK)
    # header bar
    for x in range(5, 9):
        cv.px(x, 2, CYAN_DARK)
    cv.outline(hexc("2A1E14"))
    return cv


def signal_breaker():
    cv = Canvas(16, 16)
    blade_core = [GUN[1], GUN[2], GUN[3]]
    # blade along diagonal from (5,10) to (14,1), 2px wide
    for t in range(5, 15):
        x, y = t, 15 - t
        cv.px(x, y, GUN[2])
        if t < 14:
            cv.px(x + 1, y, GUN[1] if t % 3 else GUN[0])  # lower-right side (shadow)
        # cyan static edge on upper-left side
        if t < 14:
            cv.px(x, y - 1, CYAN if t % 2 else CYAN_LIGHT)
    cv.px(14, 1, CYAN_WHITE)
    cv.px(15, 0, CYAN_LIGHT)
    # jagged rebar notches / burrs
    for t in (7, 10, 13):
        cv.px(t + 1, 15 - t + 1, GUN[0])
    for t in (6, 9, 12):
        cv.px(t - 1, 15 - t - 1, CYAN_LIGHT)
        cv.px(t, 15 - t, GUN[3])
    # crossguard (perpendicular through (4,11))
    for i, (x, y) in enumerate([(2, 9), (3, 10), (4, 11), (5, 12), (6, 13)]):
        cv.px(x, y, COPPER[3] if i < 2 else (COPPER[2] if i < 4 else COPPER[1]))
    cv.px(3, 9, COPPER[3])
    # grip wrapped in copper wire
    cv.px(3, 12, COPPER[2])
    cv.px(2, 13, GUN[0])
    cv.px(2, 12, COPPER[1])
    cv.px(3, 13, COPPER[1])
    # pommel
    cv.px(1, 14, GUN[3])
    cv.px(0, 15, GUN[1])
    cv.px(1, 15, GUN[0])
    cv.px(0, 14, GUN[2])
    cv.outline()
    # stray static sparks near the tip (after outline so they float)
    cv.px(12, 0, mix(CYAN, CLEAR, 0.0))
    return cv


def component_distortion_core():
    cv = Canvas(16, 16)
    m = circle_mask(16, 16, 7.5, 7.5, 6.3)
    glass = [PURPLE_DARK, PURPLE, hexc("6A35A0"), PURPLE_LIGHT]
    cv.fill_mask(m, glass)
    # swirl
    for i in range(40):
        t = i / 40 * 2.6 * math.pi
        r = 0.6 + t * 0.62
        x = int(round(7.5 + r * math.cos(t)))
        y = int(round(7.5 + r * math.sin(t)))
        if m[y, x]:
            cv.px(x, y, hexc("C27BFF") if i % 3 else hexc("E9C8FF"))
    cv.px(7, 7, CYAN_WHITE)
    cv.px(8, 8, CYAN)
    # glass highlight
    cv.pts([(4, 4), (5, 3), (4, 5)], hexc("F0E6FF"))
    # cracks
    crack = hexc("1A0A2C")
    cv.pts([(10, 3), (10, 4), (11, 5), (10, 6), (12, 6), (13, 7)], crack)
    cv.pts([(3, 10), (4, 10), (5, 11), (5, 12), (4, 13)], crack)
    cv.pts([(11, 4), (12, 7)], hexc("D9B8FF"))
    cv.outline(hexc("120820"))
    return cv


def component_broadcaster_coil():
    cv = Canvas(16, 16)
    # core rod
    cv.rect(7, 1, 8, 14, GUN[1])
    cv.px(7, 1, GUN[3])
    # end caps
    cv.rect(4, 2, 11, 3, GUN[2])
    cv.rect(4, 2, 11, 2, GUN[3])
    cv.rect(4, 13, 11, 14, GUN[1])
    cv.rect(4, 14, 11, 14, GUN[0])
    # windings
    for y in range(4, 13):
        light = y % 2 == 0
        for x in range(4, 12):
            if x == 4:
                c = COPPER[3] if light else COPPER[2]
            elif x >= 10:
                c = COPPER[1] if light else COPPER[0]
            else:
                c = COPPER[2] if light else COPPER[1]
            cv.px(x, y, c)
        cv.px(5, y, COPPER[3] if light else COPPER[2])
    # lead wire
    cv.pts([(3, 12), (2, 13), (2, 14)], COPPER[2])
    cv.outline()
    # cyan spark from the top
    spark = [(12, 0), (13, 1), (12, 2), (13, 3), (14, 2), (14, 4)]
    for i, (x, y) in enumerate(spark):
        cv.px(x, y, CYAN_WHITE if i in (0, 3) else CYAN)
    cv.px(10, 1, CYAN_LIGHT)
    return cv


def component_maw_fang():
    cv = Canvas(16, 16)
    bone = [hexc("7A6B52"), hexc("B5A47F"), hexc("D9CDA8"), hexc("F2EBD3")]
    m = np.zeros((16, 16), bool)
    centre = []
    # curved fang: wide root bottom-left, curling tip top-right
    for i in range(60):
        t = i / 59
        ang = math.pi * (1.05 - 0.75 * t)
        cx = 9.0 + 7.0 * math.cos(ang) * 0.95
        cy = 13.0 - 10.0 * math.sin(ang) * 0.95 - t * 1.5
        r = 2.6 * (1 - t) + 0.35
        centre.append((t, cx, cy))
        m |= circle_mask(16, 16, cx, cy, r)
    m[0, :] = False
    m[:, 15] = False
    m[15, :] = False
    m[:, 0] = False
    cv.fill_mask(m, bone)
    # wire wraps: bands across the fang at fixed points along its curve
    for y in range(16):
        for x in range(16):
            if not m[y, x]:
                continue
            t = min(centre, key=lambda c: (c[1] - x) ** 2 + (c[2] - y) ** 2)[0]
            if 0.16 <= t <= 0.24 or 0.38 <= t <= 0.43:
                lit = (x + y) % 2 == 0
                cv.px(x, y, COPPER[3] if lit else COPPER[1])
    # dark root cavity
    cv.px(3, 13, hexc("4A1E2A"))
    cv.px(4, 13, hexc("2A0F17"))
    cv.outline(hexc("231B12"))
    # loose wire end
    cv.pts([(6, 14), (7, 15)], COPPER[2])
    return cv


def component_scrambler_chip():
    cv = Canvas(16, 16)
    pcb = [hexc("0E3A1C"), hexc("17602C"), hexc("238A3E"), hexc("3DB25A")]
    m = np.zeros((16, 16), bool)
    m[3:13, 3:13] = True
    cv.fill_mask(m, pcb)
    # pins
    pin = hexc("A8A8B4")
    for k in range(4, 12, 2):
        cv.px(k, 2, pin)
        cv.px(k, 13, GUN[3])
        cv.px(2, k, pin)
        cv.px(13, k, GUN[3])
    # IC die
    cv.rect(6, 6, 9, 9, GUN[0])
    cv.rect(6, 6, 9, 6, GUN[1])
    cv.px(6, 6, GUN[2])
    # traces
    tr = hexc("C9B860")
    cv.pts([(4, 5), (5, 5), (5, 6), (10, 5), (11, 5), (4, 10), (5, 10), (5, 9)], tr)
    cv.px(4, 4, CYAN)
    # burnt corner (bottom-right)
    for y in range(9, 14):
        for x in range(9, 14):
            d = (x - 9) + (y - 9)
            if d >= 6:
                cv.px(x, y, CLEAR)
            elif d >= 4:
                cv.px(x, y, hexc("140C08") if (x + y) % 2 else hexc("3A2414"))
            elif d == 3 and (x * 7 + y) % 3:
                cv.px(x, y, hexc("4A3A1A"))
    cv.outline()
    return cv


def component_echo_crystal():
    cv = Canvas(16, 16)
    ice = [hexc("2E5E86"), hexc("5C9CCB"), hexc("9ED0EE"), hexc("E2F5FF")]
    m = np.zeros((16, 16), bool)
    # tilted elongated shard (diamond) from (4,13) to (12,2)
    for y in range(16):
        for x in range(16):
            along = (x - 8) * 0.6 + (7.5 - y) * 0.8
            across = (x - 8) * 0.8 - (7.5 - y) * 0.6
            half = 3.2 * (1 - abs(along) / 7.2)
            if abs(along) < 7.2 and abs(across) < half:
                m[y, x] = True
    cv.fill_mask(m, ice)
    # concentric rings
    for y in range(16):
        for x in range(16):
            if not m[y, x]:
                continue
            d = math.hypot(x - 8, y - 7.5)
            if abs(d - 2.0) < 0.5 or abs(d - 4.3) < 0.45:
                cv.px(x, y, hexc("C6ECFF") if x + y < 16 else hexc("3F79A8"))
    cv.px(8, 7, CYAN_WHITE)
    cv.outline(hexc("0F2236"))
    return cv


ICONS = {
    "signal_decoder": signal_decoder,
    "relay_log": relay_log,
    "signal_breaker": signal_breaker,
    "component_distortion_core": component_distortion_core,
    "component_broadcaster_coil": component_broadcaster_coil,
    "component_maw_fang": component_maw_fang,
    "component_scrambler_chip": component_scrambler_chip,
    "component_echo_crystal": component_echo_crystal,
}


def main():
    for name, fn in ICONS.items():
        fn().save(os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main()
