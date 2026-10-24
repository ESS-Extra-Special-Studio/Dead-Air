#!/usr/bin/env python3
"""Generate profession-themed Minecraft 64x64 survivor skins.

24 skins: 6 professions x (2 male + 2 female).
Naming: {profession}_{male|female}_{0|1}.png

Human skin tones only. Distinct faces, shaped hair (with outer layer),
and detailed wasteland outfits themed per profession — not flat placeholders.
"""
from __future__ import annotations

import random
from pathlib import Path
from typing import Iterable, Sequence, Tuple

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/dead_air/textures/entity/survivor"
NEO_OUT = Path(__file__).resolve().parents[3] / "Dead Air - 1.21.1 NeoForge/src/main/resources/assets/dead_air/textures/entity/survivor"

RGB = Tuple[int, int, int]
RGBA = Tuple[int, int, int, int]

SKINS: list[RGB] = [
    (255, 219, 172),  # fair
    (241, 194, 125),  # light
    (224, 172, 105),  # light-medium
    (198, 134, 66),   # medium
    (141, 85, 36),    # brown
    (92, 56, 32),     # deep brown
    (255, 224, 189),  # pale
    (210, 150, 95),   # warm medium
    (168, 112, 68),   # warm olive-brown (not green)
    (70, 42, 28),     # very deep
]

HAIR: list[RGB] = [
    (28, 22, 18),     # black
    (61, 40, 20),     # dark brown
    (110, 70, 35),    # brown
    (160, 110, 50),   # light brown
    (200, 160, 70),   # dirty blond
    (230, 200, 120),  # blond
    (90, 40, 30),     # auburn
    (180, 80, 40),    # ginger
    (95, 95, 100),    # salt-pepper
    (48, 52, 58),     # charcoal gray
]

EYE_COLORS: list[RGB] = [
    (55, 85, 120),    # blue
    (70, 95, 55),     # green-hazel
    (90, 60, 35),     # brown
    (50, 50, 55),     # dark
    (100, 80, 45),    # amber
]


def clamp(v: int) -> int:
    return max(0, min(255, v))


def shade(c: RGB, d: int) -> RGB:
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d))


def mix(a: RGB, b: RGB, t: float) -> RGB:
    return (
        clamp(int(a[0] + (b[0] - a[0]) * t)),
        clamp(int(a[1] + (b[1] - a[1]) * t)),
        clamp(int(a[2] + (b[2] - a[2]) * t)),
    )


def rgba(c: RGB, a: int = 255) -> RGBA:
    return (*c, a)


def px(img: Image.Image, x: int, y: int, color: Sequence[int]) -> None:
    if 0 <= x < 64 and 0 <= y < 64:
        img.putpixel((x, y), rgba(tuple(color[:3]), color[3] if len(color) > 3 else 255))


def clear_rect(img: Image.Image, box: Tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), (0, 0, 0, 0))


def fill_rect(
    img: Image.Image,
    box: Tuple[int, int, int, int],
    color: RGB,
    *,
    noise: int = 0,
    vert: int = 0,
    seed: int = 0,
) -> None:
    """Fill [x0,y0)-(x1,y1) with optional noise and vertical gradient."""
    x0, y0, x1, y1 = box
    rng = random.Random(seed + x0 * 17 + y0 * 31)
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        v = int(vert * ((y - y0) / h - 0.5) * 2) if vert else 0
        for x in range(x0, x1):
            n = rng.randint(-noise, noise) if noise else 0
            px(img, x, y, shade(color, v + n))


def dither_rect(
    img: Image.Image,
    box: Tuple[int, int, int, int],
    base: RGB,
    alt: RGB,
    density: float = 0.18,
    seed: int = 0,
) -> None:
    x0, y0, x1, y1 = box
    rng = random.Random(seed + 99)
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = alt if rng.random() < density else base
            # checkerboard-ish wear
            if (x + y) % 5 == 0 and rng.random() < 0.35:
                c = shade(c, -8)
            px(img, x, y, c)


def paint_box_faces(
    img: Image.Image,
    faces: dict[str, Tuple[int, int, int, int]],
    color: RGB,
    *,
    noise: int = 4,
    seed: int = 0,
) -> None:
    for name, box in faces.items():
        extra = {"top": -12, "bottom": -22, "right": -6, "left": -4, "back": -10, "front": 0}.get(name, 0)
        fill_rect(img, box, shade(color, extra), noise=noise, vert=6 if name == "front" else 3, seed=seed + hash(name) % 1000)


# ---- UV boxes (classic Steve 4px arms) ----
HEAD = {
    "top": (8, 0, 16, 8),
    "bottom": (16, 0, 24, 8),
    "right": (0, 8, 8, 16),
    "front": (8, 8, 16, 16),
    "left": (16, 8, 24, 16),
    "back": (24, 8, 32, 16),
}
HAT = {
    "top": (40, 0, 48, 8),
    "bottom": (48, 0, 56, 8),
    "right": (32, 8, 40, 16),
    "front": (40, 8, 48, 16),
    "left": (48, 8, 56, 16),
    "back": (56, 8, 64, 16),
}
BODY = {
    "top": (20, 16, 28, 20),
    "bottom": (28, 16, 36, 20),
    "right": (16, 20, 20, 32),
    "front": (20, 20, 28, 32),
    "left": (28, 20, 32, 32),
    "back": (32, 20, 40, 32),
}
JACKET = {
    "top": (20, 32, 28, 36),
    "bottom": (28, 32, 36, 36),
    "right": (16, 36, 20, 48),
    "front": (20, 36, 28, 48),
    "left": (28, 36, 32, 48),
    "back": (32, 36, 40, 48),
}
RARM = {
    "top": (44, 16, 48, 20),
    "bottom": (48, 16, 52, 20),
    "right": (40, 20, 44, 32),
    "front": (44, 20, 48, 32),
    "left": (48, 20, 52, 32),
    "back": (52, 20, 56, 32),
}
RARM_O = {
    "top": (44, 32, 48, 36),
    "bottom": (48, 32, 52, 36),
    "right": (40, 36, 44, 48),
    "front": (44, 36, 48, 48),
    "left": (48, 36, 52, 48),
    "back": (52, 36, 56, 48),
}
LARM = {
    "top": (36, 48, 40, 52),
    "bottom": (40, 48, 44, 52),
    "right": (32, 52, 36, 64),
    "front": (36, 52, 40, 64),
    "left": (40, 52, 44, 64),
    "back": (44, 52, 48, 64),
}
LARM_O = {
    "top": (52, 48, 56, 52),
    "bottom": (56, 48, 60, 52),
    "right": (48, 52, 52, 64),
    "front": (52, 52, 56, 64),
    "left": (56, 52, 60, 64),
    "back": (60, 52, 64, 64),
}
RLEG = {
    "top": (4, 16, 8, 20),
    "bottom": (8, 16, 12, 20),
    "right": (0, 20, 4, 32),
    "front": (4, 20, 8, 32),
    "left": (8, 20, 12, 32),
    "back": (12, 20, 16, 32),
}
LLEG = {
    "top": (20, 48, 24, 52),
    "bottom": (24, 48, 28, 52),
    "right": (16, 52, 20, 64),
    "front": (20, 52, 24, 64),
    "left": (24, 52, 28, 64),
    "back": (28, 52, 32, 64),
}
RLEG_O = {
    "top": (4, 32, 8, 36),
    "bottom": (8, 32, 12, 36),
    "right": (0, 36, 4, 48),
    "front": (4, 36, 8, 48),
    "left": (8, 36, 12, 48),
    "back": (12, 36, 16, 48),
}
LLEG_O = {
    "top": (4, 48, 8, 52),
    "bottom": (8, 48, 12, 52),
    "right": (0, 52, 4, 64),
    "front": (4, 52, 8, 64),
    "left": (8, 52, 12, 64),
    "back": (12, 52, 16, 64),
}


def paint_limb(
    img: Image.Image,
    faces: dict,
    sleeve: RGB,
    skin: RGB,
    *,
    hand_rows: int = 2,
    noise: int = 5,
    seed: int = 0,
    cuff: RGB | None = None,
) -> None:
    paint_box_faces(img, faces, sleeve, noise=noise, seed=seed)
    # hands / boots at bottom of each side face
    for name in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = faces[name]
        fill_rect(img, (x0, y1 - hand_rows, x1, y1), skin if hand_rows else sleeve, noise=2, seed=seed + 3)
    if cuff:
        for name in ("right", "front", "left", "back"):
            x0, y0, x1, y1 = faces[name]
            fill_rect(img, (x0, y1 - hand_rows - 1, x1, y1 - hand_rows), cuff, noise=1, seed=seed + 7)


def paint_legs(
    img: Image.Image,
    pants: RGB,
    boots: RGB,
    *,
    noise: int = 5,
    seed: int = 0,
    boot_rows: int = 3,
) -> None:
    for faces, s in ((RLEG, seed), (LLEG, seed + 40)):
        paint_box_faces(img, faces, pants, noise=noise, seed=s)
        for name in ("right", "front", "left", "back"):
            x0, y0, x1, y1 = faces[name]
            fill_rect(img, (x0, y1 - boot_rows, x1, y1), boots, noise=3, seed=s + 9)
            # sole darker
            fill_rect(img, (x0, y1 - 1, x1, y1), shade(boots, -25), noise=0, seed=s)
            # cuff line
            fill_rect(img, (x0, y1 - boot_rows - 1, x1, y1 - boot_rows), shade(pants, -18), noise=0, seed=s)


# ---- Faces ----
def paint_face(img: Image.Image, skin: RGB, hair: RGB, face: str, eye: RGB, seed: int) -> None:
    # Base head with soft shading
    paint_box_faces(img, HEAD, skin, noise=3, seed=seed)
    fill_rect(img, HEAD["bottom"], shade(skin, -18), noise=2, seed=seed)
    # cheek / jaw shade on front
    for x in range(8, 16):
        px(img, x, 15, shade(skin, -14))
    for x in (8, 15):
        for y in range(11, 15):
            px(img, x, y, shade(skin, -8))
    # nose hint
    px(img, 11, 12, shade(skin, -12))
    px(img, 12, 12, shade(skin, -8))
    px(img, 11, 13, shade(skin, -18))

    brow = shade(hair, -25)
    white = (242, 242, 248)
    pupil = (16, 16, 20)
    iris = mix(eye, pupil, 0.25)
    lip = mix(skin, (150, 75, 70), 0.5)

    def draw_brows(y: int = 10) -> None:
        # thin straight brows — never diagonal angry-V slabs
        px(img, 9, y, brow)
        px(img, 10, y, brow)
        px(img, 13, y, brow)
        px(img, 14, y, brow)

    def draw_eyes(style: str = "open") -> None:
        # 2-wide eyes: sclera + colored iris (readable at Steve scale)
        if style == "narrow":
            px(img, 9, 11, mix(white, skin, 0.4))
            px(img, 10, 11, pupil)
            px(img, 13, 11, mix(white, skin, 0.4))
            px(img, 14, 11, pupil)
        elif style == "wary":
            px(img, 9, 11, white)
            px(img, 10, 11, pupil)
            px(img, 13, 11, white)
            px(img, 14, 11, pupil)
            # slight under-lid
            px(img, 9, 12, mix(skin, (90, 70, 80), 0.25))
            px(img, 14, 12, mix(skin, (90, 70, 80), 0.25))
        else:  # open — iris color visible
            px(img, 9, 11, white)
            px(img, 10, 11, iris)
            px(img, 13, 11, white)
            px(img, 14, 11, iris)
            # micro highlight on iris
            px(img, 10, 11, mix(iris, (255, 255, 255), 0.15))
            px(img, 14, 11, mix(iris, (255, 255, 255), 0.15))

    if face == "scar":
        draw_brows(10)
        draw_eyes("open")
        px(img, 10, 9, mix(skin, (185, 115, 100), 0.55))
        px(img, 10, 10, mix(skin, (165, 95, 85), 0.65))
        px(img, 11, 11, mix(skin, (175, 105, 95), 0.4))
        px(img, 11, 14, lip)
        px(img, 12, 14, shade(lip, -18))
        px(img, 13, 14, lip)
    elif face == "tired":
        draw_brows(10)
        draw_eyes("narrow")
        bags = mix(skin, (110, 75, 90), 0.4)
        px(img, 9, 12, bags)
        px(img, 10, 12, bags)
        px(img, 13, 12, bags)
        px(img, 14, 12, bags)
        px(img, 11, 14, shade(lip, -12))
        px(img, 12, 14, shade(lip, -12))
        px(img, 13, 14, shade(lip, -12))
    elif face == "freckles":
        draw_brows(10)
        draw_eyes("open")
        freckle = shade(skin, -45)
        for fx, fy in ((9, 12), (11, 12), (14, 12), (10, 13), (12, 13), (15, 13), (9, 13), (14, 13)):
            px(img, fx, fy, freckle)
        px(img, 11, 14, lip)
        px(img, 12, 14, mix(lip, (210, 130, 115), 0.35))
        px(img, 13, 14, lip)
    elif face == "smile":
        draw_brows(10)
        draw_eyes("open")
        px(img, 10, 14, lip)
        px(img, 11, 14, mix(lip, (225, 145, 125), 0.4))
        px(img, 12, 14, mix(lip, (225, 145, 125), 0.4))
        px(img, 13, 14, lip)
        px(img, 11, 15, shade(skin, -12))
        px(img, 12, 15, shade(skin, -12))
    elif face == "wary":
        draw_brows(10)
        draw_eyes("wary")
        px(img, 11, 14, shade(lip, -22))
        px(img, 12, 14, shade(lip, -28))
    elif face == "stern":
        draw_brows(10)
        draw_eyes("open")
        px(img, 11, 14, shade(lip, -28))
        px(img, 12, 14, shade(lip, -32))
        px(img, 13, 14, shade(lip, -28))
    else:  # neutral
        draw_brows(10)
        draw_eyes("open")
        px(img, 11, 14, lip)
        px(img, 12, 14, lip)
        px(img, 13, 14, lip)


# ---- Hair ----
def hair_hi(c: RGB) -> RGB:
    return shade(c, 28)


def hair_lo(c: RGB) -> RGB:
    return shade(c, -22)


def paint_hair(img: Image.Image, hair: RGB, style: str, female: bool, seed: int) -> None:
    hi, lo = hair_hi(hair), hair_lo(hair)

    def strand_top(y0: int = 0, y1: int = 8) -> None:
        fill_rect(img, (8, y0, 16, y1), hair, noise=8, vert=14, seed=seed)
        rng = random.Random(seed + 44)
        for y in range(y0, y1):
            for x in range(8, 16):
                if rng.random() < 0.22:
                    px(img, x, y, hi)
                elif rng.random() < 0.18:
                    px(img, x, y, lo)
        for x in range(8, 16):
            if (x + seed) % 3 == 0:
                px(img, x, y0, hi)
            if (x + seed) % 4 == 0:
                px(img, x, y1 - 1, lo)

    def fringe(depth: int = 2) -> None:
        for d in range(depth):
            for x in range(8, 16):
                c = hi if (x + d + seed) % 3 == 0 else hair
                if (x + d) % 5 == 0:
                    c = lo
                px(img, x, 8 + d, c)

    def sideburns(long: bool = False) -> None:
        ymax = 14 if long else 11
        for y in range(8, ymax):
            px(img, 0, y, lo if y > 10 else hair)
            px(img, 7, y, hair)
            px(img, 16, y, hair)
            px(img, 23, y, lo if y > 10 else hair)

    def back_full() -> None:
        fill_rect(img, HEAD["back"], hair, noise=5, vert=8, seed=seed + 2)
        for y in range(8, 16):
            px(img, 24, y, lo)
            px(img, 31, y, lo)

    # clear top first to hair for most styles
    if style != "bald":
        strand_top()
        back_full()

    if style == "short":
        fringe(1)
        sideburns(False)
        for x in range(8, 16):
            px(img, x, 8, hair if x % 2 == 0 else hi)
    elif style == "messy":
        fringe(2)
        sideburns(True)
        for x, y in ((9, 0), (11, 1), (14, 0), (10, 2), (13, 2)):
            px(img, x, y, hi)
        for x in (8, 15):
            px(img, x, 9, lo)
    elif style == "buzz":
        fill_rect(img, HEAD["top"], shade(hair, -15), noise=8, seed=seed)
        for x in range(8, 16):
            px(img, x, 8, mix(hair, SKINS[0], 0.15) if False else shade(hair, -10))
        # faint side fuzz
        for x in range(0, 8):
            px(img, x, 8, shade(hair, -20))
        for x in range(16, 24):
            px(img, x, 8, shade(hair, -20))
        fill_rect(img, HEAD["back"], shade(hair, -18), noise=6, seed=seed)
    elif style == "undercut":
        strand_top(0, 5)
        fringe(1)
        # sides stay mostly skin (already painted) with light fuzz
        for y in range(8, 10):
            for x in range(0, 8):
                if (x + y) % 2 == 0:
                    px(img, x, y, shade(hair, -35))
            for x in range(16, 24):
                if (x + y) % 2 == 0:
                    px(img, x, y, shade(hair, -35))
        back_full()
        for y in range(8, 12):
            px(img, 24 + (y % 2), y, hi)
    elif style == "mohawk":
        # sides short
        for x in range(8, 16):
            px(img, x, 8, shade(hair, -25))
        fill_rect(img, (10, 0, 14, 6), hair, noise=4, seed=seed)
        for y in range(0, 5):
            px(img, 11, y, hi)
            px(img, 12, y, hi)
        back_full()
        fill_rect(img, (26, 8, 30, 14), hair, noise=3, seed=seed)
    elif style == "ponytail":
        strand_top()
        fringe(2)
        sideburns(True)
        back_full()
        # ponytail blob on back lower
        for y in range(12, 16):
            for x in range(26, 30):
                px(img, x, y, hi if x == 27 else hair)
        # outer layer volume + fringe
        fill_rect(img, HAT["top"], hair, noise=5, seed=seed + 5)
        for x in range(40, 48):
            px(img, x, 8, hi if (x + seed) % 3 == 0 else hair)
            if x not in (43, 44):  # leave face center clear-ish
                px(img, x, 9, hair)
        fill_rect(img, HAT["back"], hair, noise=4, seed=seed)
        fill_rect(img, (58, 10, 62, 16), hair, noise=3, seed=seed)
        fill_rect(img, HAT["right"], lo, noise=3, seed=seed)
        fill_rect(img, HAT["left"], hair, noise=3, seed=seed)
    elif style == "long":
        strand_top()
        fringe(2 if female else 1)
        sideburns(True)
        back_full()
        # long sides down face edges
        for y in range(8, 16):
            px(img, 0, y, lo)
            px(img, 1, y, hair)
            px(img, 6, y, hair)
            px(img, 7, y, lo)
            px(img, 16, y, lo)
            px(img, 17, y, hair)
            px(img, 22, y, hair)
            px(img, 23, y, lo)
        # outer layer for volume
        fill_rect(img, HAT["top"], hair, noise=6, seed=seed)
        for x in range(40, 48):
            c = hi if (x + seed) % 3 == 0 else hair
            px(img, x, 8, c)
            if x in (40, 41, 46, 47):
                px(img, x, 9, c)
                px(img, x, 10, lo)
        fill_rect(img, HAT["back"], hair, noise=5, vert=8, seed=seed)
        fill_rect(img, HAT["right"], hair, noise=4, seed=seed)
        fill_rect(img, HAT["left"], hair, noise=4, seed=seed)
        # outer hair hanging lower on sides/back
        for y in range(11, 16):
            for x in range(32, 40):
                if x in (32, 33, 38, 39) or y > 12:
                    px(img, x, y, lo if x % 2 == 0 else hair)
            for x in range(48, 56):
                if x in (48, 49, 54, 55) or y > 12:
                    px(img, x, y, lo if x % 2 == 0 else hair)
    elif style == "bun":
        strand_top()
        fringe(1)
        sideburns(False)
        back_full()
        fill_rect(img, (10, 0, 14, 3), hi, noise=2, seed=seed)
        fill_rect(img, HAT["top"], hair, noise=4, seed=seed)
        fill_rect(img, (42, 0, 46, 4), hi, noise=2, seed=seed)
        for x in range(40, 48):
            px(img, x, 8, hair)
        fill_rect(img, HAT["back"], hair, noise=3, seed=seed)
    elif style == "beard":
        strand_top()
        fringe(1)
        sideburns(True)
        back_full()
        # beard on face
        for x in range(9, 15):
            px(img, x, 13, lo)
            px(img, x, 14, hair)
            px(img, x, 15, lo)
        px(img, 8, 12, hair)
        px(img, 8, 13, lo)
        px(img, 15, 12, hair)
        px(img, 15, 13, lo)
        # stubble noise
        for x, y in ((10, 12), (12, 12), (14, 12), (11, 13)):
            px(img, x, y, shade(hair, -10))
    elif style == "cap":
        cap = (58, 72, 48)
        fill_rect(img, HEAD["top"], cap, noise=4, seed=seed)
        for x in range(8, 16):
            px(img, x, 8, shade(cap, 10))
        for x in range(0, 8):
            px(img, x, 8, cap)
        for x in range(16, 24):
            px(img, x, 8, cap)
        fill_rect(img, HEAD["back"], shade(cap, -10), noise=3, seed=seed)
        # short hair peeking
        for x in range(9, 15):
            if x % 2 == 0:
                px(img, x, 9, hair)
    elif style == "bald":
        # sparse stubble ring
        for x in range(8, 16):
            if x % 3 == 0:
                px(img, x, 8, shade(hair, -30))


# ---- Outfits ----
def torso_base(img: Image.Image, shirt: RGB, seed: int) -> None:
    paint_box_faces(img, BODY, shirt, noise=6, seed=seed)
    # collar / neck shadow
    fill_rect(img, (21, 20, 27, 21), shade(shirt, -22), noise=0, seed=seed)


def belt(img: Image.Image, color: RGB) -> None:
    fill_rect(img, (20, 29, 28, 31), color, noise=2, seed=1)
    px(img, 23, 29, shade(color, 30))
    px(img, 24, 29, shade(color, 40))
    px(img, 23, 30, shade(color, 20))
    px(img, 24, 30, shade(color, 25))


def pouches(img: Image.Image, color: RGB) -> None:
    fill_rect(img, (21, 25, 23, 28), color, noise=2, seed=2)
    fill_rect(img, (25, 25, 27, 28), shade(color, -10), noise=2, seed=3)
    px(img, 21, 25, shade(color, 25))
    px(img, 25, 25, shade(color, 18))
    # flap lines
    px(img, 21, 26, shade(color, -20))
    px(img, 22, 26, shade(color, -15))
    px(img, 25, 26, shade(color, -20))
    px(img, 26, 26, shade(color, -15))


def straps(img: Image.Image, color: RGB) -> None:
    for y in range(21, 29):
        px(img, 20, y, color)
        px(img, 27, y, shade(color, -10))
    fill_rect(img, (20, 22, 28, 23), shade(color, -5), noise=1, seed=4)
    # chest buckle
    px(img, 23, 22, shade(color, 35))
    px(img, 24, 22, shade(color, 25))


def paint_outfit(
    img: Image.Image,
    outfit: str,
    shirt: RGB,
    pants: RGB,
    boots: RGB,
    accent: RGB,
    skin: RGB,
    seed: int,
) -> None:
    torso_base(img, shirt, seed)
    paint_limb(img, RARM, shirt, skin, hand_rows=2, noise=5, seed=seed + 10, cuff=shade(shirt, -15))
    paint_limb(img, LARM, shirt, skin, hand_rows=2, noise=5, seed=seed + 20, cuff=shade(shirt, -15))
    paint_legs(img, pants, boots, noise=5, seed=seed + 30)

    if outfit == "medic":
        # clean-ish white coat over muted base
        white = (232, 230, 224)
        paint_box_faces(img, BODY, white, noise=4, seed=seed)
        fill_rect(img, (21, 20, 27, 21), shade(white, -18), noise=0, seed=seed)
        # red cross with dark border
        cross = (190, 45, 45)
        border = (120, 30, 30)
        for x in range(23, 25):
            for y in range(22, 28):
                px(img, x, y, cross)
        for x in range(21, 27):
            for y in range(24, 26):
                px(img, x, y, cross)
        px(img, 22, 23, border)
        px(img, 25, 23, border)
        px(img, 22, 26, border)
        px(img, 25, 26, border)
        # pocket
        fill_rect(img, (21, 27, 23, 30), shade(white, -12), noise=1, seed=seed)
        belt(img, (70, 70, 75))
        # sleeve stripe
        fill_rect(img, (44, 22, 48, 24), (240, 240, 245), noise=0, seed=seed)
        fill_rect(img, (36, 54, 40, 56), (240, 240, 245), noise=0, seed=seed)
        # outer coat flaps (open center so cross reads)
        fill_rect(img, JACKET["front"], shade(white, -8), noise=3, seed=seed)
        clear_rect(img, (22, 37, 26, 46))
        for y in range(36, 48):
            px(img, 20, y, shade(white, -15))
            px(img, 27, y, shade(white, -20))

    elif outfit == "radio":
        # olive ops jacket + radio pouch
        olive = shirt
        paint_box_faces(img, BODY, olive, noise=7, seed=seed)
        straps(img, shade(accent, -10))
        # radio pack on chest
        fill_rect(img, (22, 23, 26, 28), shade(accent, -25), noise=2, seed=seed)
        px(img, 23, 24, (40, 120, 70))  # LED
        px(img, 24, 24, (30, 30, 35))
        px(img, 23, 25, (35, 35, 40))
        px(img, 24, 25, (50, 50, 55))
        # antenna hint
        px(img, 25, 22, (60, 60, 65))
        px(img, 25, 21, (80, 80, 85))
        belt(img, (45, 42, 38))
        pouches(img, shade(olive, -20))
        # headset cable on shoulder (arm)
        px(img, 44, 20, (30, 30, 30))
        px(img, 45, 21, (30, 30, 30))
        # jacket outer
        fill_rect(img, JACKET["front"], shade(olive, -12), noise=4, seed=seed)
        clear_rect(img, (22, 37, 26, 46))
        for y in range(36, 47):
            px(img, 20, y, shade(olive, -20))
            px(img, 27, y, shade(olive, -25))

    elif outfit == "scav":
        # layered scavenger coat + vest
        paint_box_faces(img, BODY, shirt, noise=8, seed=seed)
        straps(img, accent)
        pouches(img, shade(accent, -15))
        belt(img, (55, 45, 30))
        # torn hem
        px(img, 20, 31, skin)
        px(img, 27, 31, skin)
        px(img, 22, 31, shade(shirt, -30))
        # patch on shoulder
        fill_rect(img, (45, 21, 47, 23), accent, noise=1, seed=seed)
        # outer ragged coat
        fill_rect(img, JACKET["front"], shade(accent, -5), noise=6, seed=seed)
        clear_rect(img, (22, 38, 26, 47))
        for x in (20, 21, 26, 27):
            img.putpixel((x, 47), (0, 0, 0, 0))
        for faces_o, s in ((RARM_O, seed), (LARM_O, seed + 5)):
            paint_limb(img, faces_o, shade(accent, -10), skin, hand_rows=0, noise=4, seed=s)
            for name in ("right", "front", "left", "back"):
                x0, y0, x1, y1 = faces_o[name]
                clear_rect(img, (x0, y1 - 2, x1, y1))

    elif outfit == "tactical" or outfit == "military":
        paint_box_faces(img, BODY, shirt, noise=5, seed=seed)
        # plate carrier / webbing
        fill_rect(img, (20, 21, 28, 24), shade(accent, -15), noise=3, seed=seed)
        for x in (21, 23, 25):
            fill_rect(img, (x, 24, x + 1, 29), shade(accent, -30), noise=1, seed=seed + x)
        belt(img, (35, 35, 35))
        pouches(img, shade(accent, -25))
        # camo dither on torso
        dither_rect(img, (20, 22, 28, 30), shirt, shade(accent, -20), density=0.22, seed=seed + 11)
        # knee pads on outer legs
        fill_rect(img, (4, 40, 8, 43), shade(accent, -20), noise=2, seed=seed)
        fill_rect(img, (4, 56, 8, 59), shade(accent, -20), noise=2, seed=seed)
        # arm patches
        fill_rect(img, (44, 22, 48, 24), accent, noise=1, seed=seed)
        fill_rect(img, (36, 54, 40, 56), shade(accent, -10), noise=1, seed=seed)
        # helmet / beret on hat layer for military
        if outfit == "military":
            helm = shade(accent, -35)
            fill_rect(img, HAT["top"], helm, noise=3, seed=seed)
            for x in range(40, 48):
                px(img, x, 8, shade(helm, 10))
            fill_rect(img, HAT["back"], shade(helm, -10), noise=2, seed=seed)
            fill_rect(img, (32, 8, 40, 11), helm, noise=2, seed=seed)
            fill_rect(img, (48, 8, 56, 11), helm, noise=2, seed=seed)

    elif outfit == "pilot":
        # flight jacket + harness
        jacket = shirt
        paint_box_faces(img, BODY, jacket, noise=5, seed=seed)
        straps(img, shade(accent, -20))
        # harness X
        for y in range(21, 29):
            px(img, 21 + (y - 21) // 2, y, accent)
            px(img, 26 - (y - 21) // 2, y, shade(accent, -15))
        belt(img, (40, 36, 30))
        pouches(img, shade(jacket, -25))
        # sleeve patches / rank bar
        fill_rect(img, (44, 22, 48, 23), accent, noise=0, seed=seed)
        fill_rect(img, (36, 54, 40, 55), accent, noise=0, seed=seed)
        # goggles on forehead (hat layer front band)
        gog = (28, 28, 32)
        lens = (60, 90, 110)
        fill_rect(img, (41, 9, 47, 12), gog, noise=1, seed=seed)
        px(img, 42, 10, lens)
        px(img, 43, 10, mix(lens, (200, 220, 240), 0.35))
        px(img, 45, 10, lens)
        px(img, 46, 10, mix(lens, (200, 220, 240), 0.35))
        fill_rect(img, HAT["top"], shade(jacket, -30), noise=2, seed=seed)  # cap
        for x in range(40, 48):
            px(img, x, 8, shade(jacket, -15))
        # outer flight jacket flaps
        fill_rect(img, JACKET["front"], shade(jacket, -8), noise=4, seed=seed)
        clear_rect(img, (22, 37, 26, 46))
        paint_limb(img, RARM_O, shade(jacket, -5), skin, hand_rows=0, noise=3, seed=seed)
        paint_limb(img, LARM_O, shade(jacket, -5), skin, hand_rows=0, noise=3, seed=seed + 3)
        for faces in (RARM_O, LARM_O):
            for name in ("right", "front", "left", "back"):
                x0, y0, x1, y1 = faces[name]
                clear_rect(img, (x0, y1 - 2, x1, y1))

    elif outfit == "civilian":
        # plain soft clothes — least “kit”
        paint_box_faces(img, BODY, shirt, noise=5, seed=seed)
        fill_rect(img, (21, 20, 27, 21), shade(shirt, -18), noise=0, seed=seed)
        # simple buttons
        px(img, 23, 23, shade(shirt, -30))
        px(img, 23, 25, shade(shirt, -30))
        px(img, 23, 27, shade(shirt, -30))
        belt(img, shade(pants, -15))
        paint_legs(img, pants, boots, noise=4, seed=seed + 30)
        # soft sleeve cuffs already from paint_limb above

    elif outfit == "mechanic":
        coverall = (52, 58, 68)
        paint_box_faces(img, BODY, coverall, noise=7, seed=seed)
        paint_limb(img, RARM, coverall, skin, hand_rows=2, noise=5, seed=seed)
        paint_limb(img, LARM, coverall, skin, hand_rows=2, noise=5, seed=seed)
        paint_legs(img, shade(coverall, -10), boots, noise=5, seed=seed)
        # tool pocket + yellow badge
        fill_rect(img, (22, 22, 26, 26), accent, noise=2, seed=seed)
        px(img, 23, 23, shade(accent, 30))
        px(img, 24, 24, shade(accent, -20))
        # grease
        px(img, 21, 28, (28, 28, 30))
        px(img, 26, 29, (25, 25, 28))
        px(img, 45, 26, (30, 30, 30))
        belt(img, (40, 40, 42))

    elif outfit == "farmer":
        overall = (70, 88, 48)
        paint_box_faces(img, BODY, (175, 145, 90), noise=6, seed=seed)  # shirt
        # overall bib
        fill_rect(img, (21, 22, 27, 31), overall, noise=5, seed=seed)
        px(img, 22, 23, shade(overall, 20))
        px(img, 25, 23, shade(overall, 15))
        # straps
        for y in range(20, 23):
            px(img, 21, y, overall)
            px(img, 26, y, overall)
        paint_legs(img, shade(overall, -15), boots, noise=5, seed=seed)
        # straw-ish hat hint on outer? skip — hair styles handle head

    elif outfit == "runner":
        paint_box_faces(img, BODY, shirt, noise=5, seed=seed)
        # chest stripe
        fill_rect(img, (20, 23, 28, 25), accent, noise=2, seed=seed)
        # light pants / sneakers
        paint_legs(img, pants, shade(boots, 40), noise=4, seed=seed, boot_rows=2)
        fill_rect(img, (4, 30, 8, 31), (240, 240, 245), noise=0, seed=seed)
        fill_rect(img, (20, 62, 24, 63), (240, 240, 245), noise=0, seed=seed)
        # arm stripe
        fill_rect(img, (44, 24, 48, 25), accent, noise=0, seed=seed)
        fill_rect(img, (36, 56, 40, 57), accent, noise=0, seed=seed)

    elif outfit == "coat":
        inner = shirt
        coat = accent
        paint_box_faces(img, BODY, inner, noise=5, seed=seed)
        belt(img, (50, 42, 35))
        # long coat outer layer
        fill_rect(img, JACKET["front"], coat, noise=6, seed=seed)
        fill_rect(img, JACKET["back"], shade(coat, -10), noise=5, seed=seed)
        fill_rect(img, JACKET["right"], shade(coat, -8), noise=4, seed=seed)
        fill_rect(img, JACKET["left"], shade(coat, -6), noise=4, seed=seed)
        fill_rect(img, JACKET["top"], shade(coat, -15), noise=2, seed=seed)
        clear_rect(img, (23, 37, 25, 47))
        # collar
        fill_rect(img, (20, 36, 22, 38), shade(coat, 15), noise=0, seed=seed)
        fill_rect(img, (26, 36, 28, 38), shade(coat, 10), noise=0, seed=seed)
        paint_limb(img, RARM_O, coat, skin, hand_rows=0, noise=4, seed=seed)
        paint_limb(img, LARM_O, coat, skin, hand_rows=0, noise=4, seed=seed)
        for faces in (RARM_O, LARM_O):
            for name in ("right", "front", "left", "back"):
                x0, y0, x1, y1 = faces[name]
                clear_rect(img, (x0, y1 - 2, x1, y1))

    elif outfit == "hoodie":
        paint_box_faces(img, BODY, shirt, noise=6, seed=seed)
        # kangaroo pocket
        fill_rect(img, (22, 26, 26, 30), shade(accent, -12), noise=2, seed=seed)
        # hood shadow on chest
        fill_rect(img, (21, 20, 27, 22), shade(shirt, -25), noise=2, seed=seed)
        # hood on hat layer (back/top)
        fill_rect(img, HAT["back"], shade(shirt, -10), noise=4, seed=seed)
        fill_rect(img, HAT["top"], shade(shirt, -5), noise=3, seed=seed)
        for x in range(40, 48):
            px(img, x, 8, shade(shirt, -15))
        # strings
        px(img, 22, 21, shade(shirt, 20))
        px(img, 25, 21, shade(shirt, 20))
        belt(img, shade(pants, -10))

    else:  # ragged
        paint_box_faces(img, BODY, shirt, noise=9, seed=seed)
        straps(img, accent)
        # tears
        px(img, 20, 28, skin)
        px(img, 21, 29, skin)
        px(img, 26, 30, skin)
        px(img, 27, 31, skin)
        px(img, 45, 28, skin)
        belt(img, (60, 50, 40))
        # patch
        fill_rect(img, (24, 22, 27, 24), accent, noise=2, seed=seed)


# ---- Profession specs: 2 male + 2 female each ----
# Tuple: skin_i, hair_i, eye_i, hair_style, face, outfit, shirt, pants, boots, accent
PROFESSIONS: dict[str, dict[str, list]] = {
    "scavenger": {
        "male": [
            (3, 1, 2, "messy", "scar", "scav", (95, 78, 58), (58, 52, 42), (38, 32, 26), (72, 88, 52)),
            (5, 0, 3, "beard", "tired", "scav", (105, 82, 62), (62, 52, 42), (36, 30, 24), (82, 70, 52)),
        ],
        "female": [
            (1, 6, 1, "ponytail", "freckles", "scav", (102, 78, 58), (68, 58, 48), (42, 36, 30), (82, 96, 58)),
            (4, 2, 2, "messy", "wary", "scav", (88, 70, 52), (58, 50, 42), (36, 30, 24), (70, 78, 48)),
        ],
    },
    "medic": {
        "male": [
            (0, 4, 0, "short", "tired", "medic", (228, 226, 220), (52, 52, 58), (42, 42, 48), (200, 200, 205)),
            (2, 1, 1, "buzz", "neutral", "medic", (220, 218, 212), (48, 48, 54), (40, 40, 46), (190, 45, 45)),
        ],
        "female": [
            (0, 5, 0, "bun", "smile", "medic", (235, 233, 228), (58, 52, 62), (42, 42, 48), (210, 210, 215)),
            (6, 3, 1, "ponytail", "tired", "medic", (230, 228, 222), (52, 48, 58), (40, 40, 46), (200, 50, 50)),
        ],
    },
    "radio_operator": {
        "male": [
            (4, 2, 2, "cap", "scar", "radio", (58, 72, 48), (68, 58, 44), (40, 34, 28), (42, 48, 42)),
            (7, 0, 3, "short", "wary", "radio", (52, 68, 44), (58, 52, 40), (36, 30, 24), (48, 55, 45)),
        ],
        "female": [
            (2, 2, 2, "bun", "neutral", "radio", (62, 78, 52), (78, 68, 52), (42, 36, 30), (48, 52, 48)),
            (1, 4, 0, "ponytail", "stern", "radio", (55, 70, 45), (70, 60, 48), (40, 34, 28), (40, 120, 70)),
        ],
    },
    "pilot": {
        "male": [
            (1, 0, 0, "short", "stern", "pilot", (48, 52, 62), (38, 40, 48), (28, 28, 32), (185, 145, 45)),
            (3, 8, 2, "buzz", "tired", "pilot", (55, 58, 68), (42, 44, 52), (30, 30, 34), (160, 120, 40)),
        ],
        "female": [
            (0, 5, 0, "bun", "neutral", "pilot", (52, 56, 70), (40, 42, 52), (28, 28, 32), (195, 155, 55)),
            (2, 3, 1, "ponytail", "smile", "pilot", (45, 50, 65), (38, 40, 50), (30, 30, 34), (170, 130, 45)),
        ],
    },
    "military": {
        "male": [
            (5, 0, 3, "buzz", "stern", "military", (48, 52, 48), (38, 40, 38), (28, 28, 28), (62, 68, 58)),
            (8, 1, 2, "short", "scar", "military", (52, 56, 50), (40, 42, 40), (30, 30, 30), (72, 78, 62)),
        ],
        "female": [
            (3, 0, 3, "bun", "stern", "military", (52, 56, 52), (42, 44, 42), (30, 30, 30), (72, 78, 68)),
            (4, 2, 2, "short", "wary", "military", (48, 54, 48), (40, 42, 40), (28, 28, 28), (65, 72, 58)),
        ],
    },
    "civilian": {
        "male": [
            (0, 4, 1, "messy", "smile", "civilian", (92, 108, 128), (58, 62, 72), (42, 42, 48), (70, 80, 95)),
            (6, 5, 0, "hoodie", "freckles", "hoodie", (125, 95, 72), (52, 52, 58), (38, 38, 42), (95, 75, 55)),
        ],
        "female": [
            (1, 6, 1, "long", "smile", "civilian", (145, 105, 125), (62, 58, 72), (40, 40, 46), (120, 85, 105)),
            (7, 7, 0, "ponytail", "neutral", "hoodie", (88, 118, 125), (52, 56, 62), (38, 38, 42), (70, 95, 100)),
        ],
    },
}

# Fix civilian male_1: "hoodie" is an outfit not hair style
PROFESSIONS["civilian"]["male"][1] = (
    6, 5, 0, "short", "freckles", "hoodie", (125, 95, 72), (52, 52, 58), (38, 38, 42), (95, 75, 55)
)


def add_micro_detail(img: Image.Image, seed: int) -> None:
    """Subtle fabric noise + edge darkening so skins read less flat in-game."""
    rng = random.Random(seed)
    px = img.load()
    for y in range(64):
        for x in range(64):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            # Soft grain (kept tiny — still Minecraft-readable at 64²)
            n = rng.randint(-6, 6)
            # Slightly stronger on torso/legs UV bands
            if 20 <= y < 32 or 16 <= x < 40:
                n = int(n * 1.35)
            px[x, y] = (clamp(r + n), clamp(g + n), clamp(b + n), a)


def make_skin(spec, female: bool) -> Image.Image:
    skin_i, hair_i, eye_i, hstyle, face, outfit, shirt, pants, boots, accent = spec
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKINS[skin_i]
    hair = HAIR[hair_i]
    eye = EYE_COLORS[eye_i]
    seed = skin_i * 100 + hair_i * 10 + (1 if female else 0) + hash(outfit) % 97
    paint_face(img, skin, hair, face, eye, seed)
    paint_hair(img, hair, hstyle, female, seed + 7)
    paint_outfit(img, outfit, shirt, pants, boots, accent, skin, seed + 13)
    add_micro_detail(img, seed + 99)
    return img


LEGACY_GLOB = ("male_*.png", "female_*.png", "scavenger.png", "medic.png", "radio_operator.png")


def save_all(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for profession, genders in PROFESSIONS.items():
        for gender, specs in genders.items():
            female = gender == "female"
            for i, spec in enumerate(specs):
                assert i in (0, 1), "only 2 variants per gender"
                p = out / f"{profession}_{gender}_{i}.png"
                make_skin(spec, female).save(p)
                print("wrote", p.name)


def clean_legacy(out: Path) -> None:
    import glob as _glob

    for pattern in ("male_*.png", "female_*.png", "scavenger.png", "medic.png", "radio_operator.png"):
        for path in out.glob(pattern):
            # keep if somehow named profession_* — male_/female_ only
            if path.name.startswith(("male_", "female_")) or path.stem in (
                "scavenger",
                "medic",
                "radio_operator",
            ):
                path.unlink(missing_ok=True)
                print("removed legacy", path.name)


def write_preview_sheet(out: Path, preview_dir: Path) -> None:
    """Simple 6x4 grid of all profession skins for QA."""
    preview_dir.mkdir(parents=True, exist_ok=True)
    cells: list[Image.Image] = []
    labels: list[str] = []
    for profession in PROFESSIONS:
        for gender in ("male", "female"):
            for i in (0, 1):
                p = out / f"{profession}_{gender}_{i}.png"
                if p.exists():
                    cells.append(Image.open(p).convert("RGBA"))
                    labels.append(f"{profession}_{gender}_{i}")
    if not cells:
        return
    scale = 4
    cw, ch = 64 * scale, 64 * scale
    cols = 4
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch), (24, 24, 28, 255))
    for idx, cell in enumerate(cells):
        r, c = divmod(idx, cols)
        big = cell.resize((cw, ch), Image.Resampling.NEAREST)
        sheet.paste(big, (c * cw, r * ch), big)
    dest = preview_dir / "survivor_professions_sheet.png"
    sheet.save(dest)
    print("preview", dest)


def main() -> None:
    save_all(OUT)
    clean_legacy(OUT)
    write_preview_sheet(OUT, Path(__file__).resolve().parent / "preview")
    if NEO_OUT.parent.exists():
        try:
            save_all(NEO_OUT)
            clean_legacy(NEO_OUT)
        except Exception as e:
            print("NeoForge mirror skipped:", e)
    else:
        print("NeoForge path missing, Forge only:", NEO_OUT)
    print("done — 24 profession skins")


if __name__ == "__main__":
    main()
