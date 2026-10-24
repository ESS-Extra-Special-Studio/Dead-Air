"""Shared helpers for the Dead Air procedural texture generators.

Everything here is drawn pixel by pixel; no external/vanilla art is read
except the mod's own walkie_t2 texture (for the recolour).
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image

PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(PROJECT, "src", "main", "resources", "assets", "dead_air")
TEX = os.path.join(ASSETS, "textures")
DOCS = r"C:\Users\Ksivi\Desktop\Extra Special Studio\Minecraft\Dead Air\docs\dead-air-3-art"


def hexc(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t: float):
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(4))


# Studio palette
CYAN = hexc("2FA8FF")
CYAN_LIGHT = hexc("9FE0FF")
CYAN_WHITE = hexc("E6F8FF")
CYAN_DARK = hexc("1A5FA8")
PURPLE = hexc("4A1E7A")
PURPLE_LIGHT = hexc("8A4FC4")
PURPLE_DARK = hexc("2A0F47")
OUTLINE = hexc("14121A")
GUN = [hexc("1C1B22"), hexc("2E2D37"), hexc("44434F"), hexc("626170")]
COPPER = [hexc("4A2416"), hexc("7A3F22"), hexc("B0663A"), hexc("D9935C")]
PATINA = [hexc("1F4A43"), hexc("2F6E62"), hexc("4C9A86"), hexc("7CC4AE")]
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w: int, h: int):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.uint8)

    def px(self, x: int, y: int, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = c

    def get(self, x: int, y: int):
        return tuple(int(v) for v in self.a[y, x])

    def opaque(self, x: int, y: int) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h and self.a[y, x, 3] > 0

    def rect(self, x0, y0, x1, y1, c):
        """Inclusive rectangle fill."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def pts(self, points, c):
        for x, y in points:
            self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def fill_mask(self, mask: np.ndarray, ramp, light_bias: float = 0.0):
        """Fill a boolean mask with a 4-tone ramp, lit from the top-left.

        Edge pixels facing up/left get the light tone, edges facing
        down/right the dark tone, interior a diagonal gradient.
        """
        h, w = mask.shape
        ys, xs = np.nonzero(mask)
        if len(xs) == 0:
            return
        span = max(1, (xs.max() + ys.max()) - (xs.min() + ys.min()))
        for x, y in zip(xs, ys):
            up = y == 0 or not mask[y - 1, x]
            left = x == 0 or not mask[y, x - 1]
            down = y == h - 1 or not mask[y + 1, x]
            right = x == w - 1 or not mask[y, x + 1]
            if (up or left) and not (down or right):
                idx = 3
            elif (down or right) and not (up or left):
                idx = 0
            else:
                t = ((x + y) - (xs.min() + ys.min())) / span - light_bias
                idx = 2 if t < 0.5 else 1
            self.px(int(x), int(y), ramp[idx])

    def outline(self, c=OUTLINE, diagonal: bool = False):
        """Add a 1px outline on transparent pixels touching opaque ones."""
        src = self.a[..., 3] > 0
        out = []
        nbrs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
        if diagonal:
            nbrs += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
        for y in range(self.h):
            for x in range(self.w):
                if src[y, x]:
                    continue
                for dx, dy in nbrs:
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and src[ny, nx]:
                        out.append((x, y))
                        break
        for x, y in out:
            self.a[y, x] = c

    def image(self) -> Image.Image:
        return Image.fromarray(self.a, "RGBA")

    def save(self, path: str):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.image().save(path)
        print("wrote", path)


def circle_mask(w, h, cx, cy, r):
    yy, xx = np.mgrid[0:h, 0:w]
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def noise(w, h, seed, scale=1.0):
    rng = np.random.RandomState(seed)
    return rng.rand(h, w) * scale


# ---- box-UV helpers (Minecraft / Bedrock layout) ----

def box_faces(u, v, w, h, d):
    """Return {face: (x, y, fw, fh)} for a Bedrock/Java box-UV cube."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(cv: Canvas, u, v, w, h, d, painter):
    """painter(face, lx, ly, fw, fh) -> RGBA or None, for every face pixel."""
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for ly in range(fh):
            for lx in range(fw):
                c = painter(face, lx, ly, fw, fh)
                if c is not None:
                    cv.px(fx + lx, fy + ly, c)


def upscale(img: Image.Image, k: int) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def checker(w, h, cell=8, c1=(40, 40, 46, 255), c2=(52, 52, 60, 255)) -> Image.Image:
    a = np.zeros((h, w, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:h, 0:w]
    m = ((xx // cell + yy // cell) % 2) == 0
    a[m] = c1
    a[~m] = c2
    return Image.fromarray(a, "RGBA")
