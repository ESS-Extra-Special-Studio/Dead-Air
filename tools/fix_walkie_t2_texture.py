"""Diagnose and repair UV coverage for the walkie_t2 texture.

The higher-res artwork was authored in a 32-space UV layout while
geo/item/walkie_t2.geo.json maps faces in a 16-space layout, so some islands
moved and a few faces now sample fully transparent pixels. Holes are refilled
from the original texture, which is layout-correct for the model.
"""

import json
import sys
from PIL import Image

GEO = "src/main/resources/assets/dead_air/geo/item/walkie_t2.geo.json"
NEW = "src/main/resources/assets/dead_air/textures/item/walkie_t2.png"
OLD = r"C:\Users\Ksivi\Downloads\radio_t2.png"

ALPHA_MIN = 16


def face_rects(geo):
    """Yield (label, u0, v0, u1, v1) in the geometry's own UV space."""
    for g in geo["minecraft:geometry"]:
        d = g["description"]
        tw, th = d["texture_width"], d["texture_height"]
        for bone in g["bones"]:
            for ci, cube in enumerate(bone.get("cubes", [])):
                uv = cube.get("uv")
                size = cube.get("size", [0, 0, 0])
                if isinstance(uv, dict):
                    for face, data in uv.items():
                        u, v = data["uv"]
                        du, dv = data["uv_size"]
                        yield (f"{bone['name']}#{ci} {face}", tw, th,
                               u, v, u + abs(du), v + abs(dv))
                elif isinstance(uv, list):
                    x, y, z = size
                    u, v = uv
                    boxes = {
                        "east": (u, v + z, z, y),
                        "north": (u + z, v + z, x, y),
                        "west": (u + z + x, v + z, z, y),
                        "south": (u + z + x + z, v + z, x, y),
                        "up": (u + z, v, x, z),
                        "down": (u + z + x, v, x, z),
                    }
                    for face, (fu, fv, fw, fh) in boxes.items():
                        if fw <= 0 or fh <= 0:
                            continue
                        yield (f"{bone['name']}#{ci} {face}", tw, th,
                               fu, fv, fu + fw, fv + fh)


def coverage(img, tw, th, u0, v0, u1, v1):
    w, h = img.size
    sx, sy = w / tw, h / th
    box = (int(u0 * sx), int(v0 * sy), max(int(u1 * sx), int(u0 * sx) + 1),
           max(int(v1 * sy), int(v0 * sy) + 1))
    box = (max(0, box[0]), max(0, box[1]), min(w, box[2]), min(h, box[3]))
    if box[2] <= box[0] or box[3] <= box[1]:
        return 0.0
    alpha = img.crop(box).getchannel("A")
    px = list(alpha.getdata())
    return sum(1 for a in px if a >= ALPHA_MIN) / len(px)


def main():
    geo = json.load(open(GEO, encoding="utf-8"))
    new = Image.open(NEW).convert("RGBA")
    old = Image.open(OLD).convert("RGBA")
    old_up = old.resize(new.size, Image.NEAREST)

    npx, opx = new.load(), old_up.load()
    fixed = new.copy()
    fpx = fixed.load()
    filled = 0
    for y in range(new.height):
        for x in range(new.width):
            if npx[x, y][3] < ALPHA_MIN and opx[x, y][3] >= ALPHA_MIN:
                fpx[x, y] = opx[x, y]
                filled += 1

    rects = list(face_rects(geo))
    print(f"faces: {len(rects)}  pixels refilled: {filled}")
    print(f"{'face':<28}{'old':>7}{'new':>7}{'fixed':>7}")
    regressions = 0
    for label, tw, th, u0, v0, u1, v1 in rects:
        co = coverage(old, tw, th, u0, v0, u1, v1)
        cn = coverage(new, tw, th, u0, v0, u1, v1)
        cf = coverage(fixed, tw, th, u0, v0, u1, v1)
        if co - cn > 0.05 or co - cf > 0.05:
            print(f"{label:<28}{co:>7.2f}{cn:>7.2f}{cf:>7.2f}")
        if co - cf > 0.05:
            regressions += 1

    if "--write" in sys.argv:
        fixed.save(NEW)
        print(f"wrote {NEW}")
    print(f"remaining faces below original coverage: {regressions}")


if __name__ == "__main__":
    main()
