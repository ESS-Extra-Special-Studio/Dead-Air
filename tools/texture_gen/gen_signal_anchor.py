"""signal_anchor GeckoLib entity: geo, animations, texture and glowmask.

Writes geo + animation JSON, paints the 64x64 texture to the box-UV layout
defined in BONES, then validates JSON parse and UV bounds/overlap.
"""
import json
import os

import numpy as np

from tg_common import (ASSETS, COPPER, CYAN, CYAN_DARK, CYAN_LIGHT, CYAN_WHITE, GUN, PATINA, PURPLE,
                       PURPLE_DARK, PURPLE_LIGHT, TEX, Canvas, box_faces, hexc, mix, paint_box)

TEX_W = TEX_H = 64

# Keep base clear of terrain — flat bottom faces at y≈0 z-fight sand/shadow.
# Cube sizes/UVs must stay in lockstep with paint(); do not shrink the slab without re-painting.
BASE_Y = 0.5

# kind is used only for painting
BONES = [
    {"name": "root", "pivot": [0, 0, 0], "cubes": []},
    {"name": "base", "parent": "root", "pivot": [0, BASE_Y, 0], "cubes": [
        {"origin": [-6, BASE_Y, -6], "size": [12, 3, 12], "uv": [0, 0], "kind": "slab"},
        {"origin": [-4, 3 + BASE_Y, -3], "size": [5, 2, 4], "uv": [0, 15], "kind": "rubble"},
        {"origin": [2, 3 + BASE_Y, 1], "size": [3, 1, 3], "uv": [18, 15], "kind": "rubble"},
    ]},
    {"name": "shard", "parent": "root", "pivot": [0, 8, 0], "rotation": [4, 0, -6], "cubes": [
        {"origin": [-2, 2 + BASE_Y, -2], "size": [4, 22, 4], "uv": [0, 21], "kind": "spike"},
        {"origin": [1.5, 4 + BASE_Y, -1], "size": [2, 10, 2], "uv": [16, 21], "kind": "crystal",
         "pivot": [2.5, 4 + BASE_Y, 0], "rotation": [0, 0, -14]},
        {"origin": [-3.5, 10 + BASE_Y, 0], "size": [2, 6, 2], "uv": [24, 21], "kind": "crystal",
         "pivot": [-2.5, 10 + BASE_Y, 1], "rotation": [0, 0, 18]},
        {"origin": [-0.5, 24 + BASE_Y, -0.5], "size": [1, 5, 1], "uv": [32, 21], "kind": "antenna"},
        {"origin": [-3, 18 + BASE_Y, -0.5], "size": [6, 1, 1], "uv": [36, 21], "kind": "antenna"},
    ]},
    {"name": "fragments", "parent": "shard", "pivot": [0, 14, 0], "cubes": [
        {"origin": [6, 13 + BASE_Y, -1], "size": [2, 2, 2], "uv": [48, 0], "kind": "frag"},
        {"origin": [-8, 15 + BASE_Y, -1], "size": [2, 2, 2], "uv": [56, 0], "kind": "frag"},
        {"origin": [-1, 12 + BASE_Y, 6], "size": [2, 2, 2], "uv": [48, 4], "kind": "frag"},
        {"origin": [-1, 16 + BASE_Y, -8], "size": [2, 2, 2], "uv": [56, 4], "kind": "frag"},
    ]},
]


def geo_json():
    bones = []
    for b in BONES:
        out = {"name": b["name"]}
        if "parent" in b:
            out["parent"] = b["parent"]
        out["pivot"] = b["pivot"]
        if "rotation" in b:
            out["rotation"] = b["rotation"]
        if b["cubes"]:
            cubes = []
            for c in b["cubes"]:
                cc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
                if "pivot" in c:
                    cc["pivot"] = c["pivot"]
                    cc["rotation"] = c["rotation"]
                cubes.append(cc)
            out["cubes"] = cubes
        bones.append(out)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.signal_anchor",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": bones,
        }],
    }


def anim_json():
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.signal_anchor.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "shard": {
                        "position": {"0.0": [0, 0, 0], "1.0": [0, 0.5, 0], "2.0": [0, 1, 0],
                                     "3.0": [0, 0.5, 0], "4.0": [0, 0, 0]},
                        "rotation": {"0.0": [0, 0, 0], "1.0": [0, 8, 0], "2.0": [0, 0, 0],
                                     "3.0": [0, -8, 0], "4.0": [0, 0, 0]},
                    },
                    "fragments": {
                        "rotation": {"0.0": [0, 0, 0], "2.0": [0, 180, 0], "4.0": [0, 360, 0]},
                        "position": {"0.0": [0, 0, 0], "1.0": [0, 0.5, 0], "2.0": [0, 0, 0],
                                     "3.0": [0, -0.5, 0], "4.0": [0, 0, 0]},
                    },
                },
            },
            "animation.signal_anchor.hit": {
                "loop": False,
                "animation_length": 0.3,
                "bones": {
                    "shard": {
                        "rotation": {"0.0": [0, 0, 0], "0.05": [0, 0, 6], "0.1": [0, 0, -6],
                                     "0.15": [0, 0, 6], "0.2": [0, 0, -6], "0.25": [0, 0, 3],
                                     "0.3": [0, 0, 0]},
                    },
                },
            },
            "animation.signal_anchor.break": {
                "loop": "hold_on_last_frame",
                "animation_length": 0.6,
                "bones": {
                    "shard": {
                        "scale": {"0.0": [1, 1, 1], "0.25": [1.3, 1.3, 1.3], "0.6": [0, 0, 0]},
                    },
                    "fragments": {
                        "scale": {"0.0": [1, 1, 1], "0.4": [0, 0, 0]},
                    },
                },
            },
        },
    }


def _h(*t):
    v = 2166136261
    for b in repr(t).encode():
        v = ((v ^ b) * 16777619) & 0xFFFFFFFF
    return (v & 0xFFFF) / 0xFFFF


def painter_for(kind, glow: Canvas, ox, oy):
    """Return (painter for colour, painter for glow) closures for a cube kind."""
    stone = [hexc("1E1D22"), hexc("2F2E35"), hexc("45434B"), hexc("5C5A62")]

    def colour(face, x, y, w, h):
        r = _h(kind, ox, oy, face, x, y)
        if kind == "slab":
            if face == "top":
                # cracked stone with copper/patina veins
                c = stone[2] if r > 0.3 else stone[1]
                if (x + 2 * y) % 9 == 0 or (2 * x - y) % 11 == 0:
                    c = hexc("0E0D12")  # cracks
                elif (x * 3 + y) % 13 == 0:
                    c = PATINA[2] if r > 0.5 else COPPER[2]
                if x in (0, w - 1) or y in (0, h - 1):
                    c = stone[3] if (x == 0 or y == 0) else stone[0]
                if 4 <= x <= 7 and 4 <= y <= 7:
                    c = CYAN_DARK if (x + y) % 2 else PURPLE_DARK  # scorched socket
                return c
            if face == "bottom":
                return stone[0]
            c = stone[1] if r > 0.35 else stone[0]
            if y == 0:
                c = stone[2]
            if (x * 5 + y * 3) % 17 == 0:
                c = COPPER[1] if r > 0.5 else PATINA[1]
            return c
        if kind == "rubble":
            c = stone[2] if face == "top" else stone[1]
            if r > 0.8:
                c = COPPER[2]
            elif r < 0.15:
                c = PATINA[2]
            return c
        if kind == "spike":
            if face in ("top", "bottom"):
                return CYAN_LIGHT if face == "top" else PURPLE_DARK
            # vertical gradient: dark metal sleeve at the base, crystal above
            if y >= h - 5:
                return GUN[2] if x in (0,) else (GUN[1] if (y + x) % 3 else COPPER[1])
            if y % 6 == 3:
                return GUN[0]  # broken antenna segment band
            if x in (1, 2):
                return CYAN_WHITE if (y % 5 == 0 and x == 1) else CYAN
            return PURPLE_LIGHT if x == 0 else PURPLE
        if kind == "crystal":
            if face in ("top", "bottom"):
                return CYAN_LIGHT
            if x == 0:
                return mix(CYAN, PURPLE, 0.4)
            return CYAN_LIGHT if y < 2 else CYAN
        if kind == "antenna":
            if face in ("top", "bottom"):
                return GUN[3]
            return GUN[3] if r > 0.5 else GUN[2]
        if kind == "frag":
            if face == "top":
                return CYAN_WHITE
            return CYAN if (x + y) % 2 else CYAN_LIGHT
        return None

    def glowing(face, x, y, w, h):
        if kind == "spike":
            if face == "top":
                return True
            if face in ("front", "back", "right", "left") and x in (1, 2) \
                    and y < h - 5 and y % 6 != 3:
                return True
            return False
        return kind in ("crystal", "frag")

    return colour, glowing


def paint():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)
    for b in BONES:
        for c in b["cubes"]:
            dx, dy, dz = (int(round(s)) for s in c["size"])
            u, v = c["uv"]
            colour, glowing = painter_for(c["kind"], glow, u, v)
            paint_box(tex, u, v, dx, dy, dz, colour)

            def gpaint(face, x, y, w, h, colour=colour, glowing=glowing):
                if not glowing(face, x, y, w, h):
                    return None
                col = colour(face, x, y, w, h)
                # brighten for the emissive layer
                return tuple(min(255, int(col[i] * 1.15 + 20)) for i in range(3)) + (255,)

            paint_box(glow, u, v, dx, dy, dz, gpaint)
    tex.save(os.path.join(TEX, "entity", "signal_anchor.png"))
    glow.save(os.path.join(TEX, "entity", "signal_anchor_glowmask.png"))


def validate(geo_path, anim_path):
    with open(geo_path, encoding="utf-8") as f:
        geo = json.load(f)
    with open(anim_path, encoding="utf-8") as f:
        anim = json.load(f)
    desc = geo["minecraft:geometry"][0]["description"]
    tw, th = desc["texture_width"], desc["texture_height"]
    used = np.zeros((th, tw), dtype=np.int32)
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    errors = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        if "parent" in b and b["parent"] not in names:
            errors.append(f"bone {b['name']} has unknown parent {b['parent']}")
        for c in b.get("cubes", []):
            dx, dy, dz = c["size"]
            # Bedrock box UV rounds fractional sizes per face; all ours are integers
            w, h = 2 * (dx + dz), dy + dz
            u, v = c["uv"]
            if u < 0 or v < 0 or u + w > tw or v + h > th:
                errors.append(f"{b['name']} cube uv {c['uv']} size {c['size']} -> {w}x{h} out of bounds")
                continue
            for fx, fy, fw, fh in box_faces(u, v, int(dx), int(dy), int(dz)).values():
                used[fy:fy + fh, fx:fx + fw] += 1
            print(f"  {b['name']:9s} uv {str(c['uv']):9s} size {str(c['size']):14s} rect {w}x{h} "
                  f"-> ({u},{v})-({u + w},{v + h})")
    if (used > 1).any():
        ys, xs = np.nonzero(used > 1)
        errors.append(f"UV overlap at {len(xs)} texels, e.g. ({xs[0]},{ys[0]})")
    for an, a in anim["animations"].items():
        for bone in a.get("bones", {}):
            if bone not in names:
                errors.append(f"{an} animates unknown bone {bone}")
    if errors:
        raise SystemExit("VALIDATION FAILED:\n" + "\n".join(errors))
    print("validation OK: JSON parses, all UV rects inside", f"{tw}x{th}", "with no overlap;",
          "animations:", ", ".join(anim["animations"]))


def main():
    geo_path = os.path.join(ASSETS, "geo", "entity", "signal_anchor.geo.json")
    anim_path = os.path.join(ASSETS, "animations", "entity", "signal_anchor.animation.json")
    for p, data in ((geo_path, geo_json()), (anim_path, anim_json())):
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("wrote", p)
    paint()
    validate(geo_path, anim_path)


if __name__ == "__main__":
    main()
