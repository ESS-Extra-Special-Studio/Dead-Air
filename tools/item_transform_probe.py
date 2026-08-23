"""Compute where a GeckoLib item's geometry lands in the hand frame.

Mirrors vanilla ItemTransform.apply order: translate (value/16 blocks), then
rotate as Rx*Ry*Rz, then scale. Geometry coords are geo units (1/16 block), so
the walkie models -- whose in-hand placement is already tuned -- can be used as
the reference for placing other GeckoLib items.
"""

import json
import math
import sys

GEO = "src/main/resources/assets/dead_air/geo/item/{}.geo.json"
MODEL = "src/main/resources/assets/dead_air/models/item/{}.json"


def rot_matrix(rx, ry, rz):
    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mul(Rx, Ry), Rz)


def apply(R, v):
    return [sum(R[i][j] * v[j] for j in range(3)) for i in range(3)]


def corners(name):
    g = json.load(open(GEO.format(name), encoding="utf-8"))
    xs, ys, zs = [], [], []
    for geo in g["minecraft:geometry"]:
        for bone in geo["bones"]:
            for c in bone.get("cubes", []):
                o, s = c["origin"], c["size"]
                xs += [o[0], o[0] + s[0]]
                ys += [o[1], o[1] + s[1]]
                zs += [o[2], o[2] + s[2]]
    box = (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs))
    pts = [[x, y, z] for x in box[0:2] for y in box[2:4] for z in box[4:6]]
    return pts


def hand_space(name, key, override=None):
    disp = json.load(open(MODEL.format(name), encoding="utf-8"))["display"][key]
    if override:
        disp = {**disp, **override}
    t = [v / 16.0 for v in disp.get("translation", [0, 0, 0])]
    r = disp.get("rotation", [0, 0, 0])
    s = disp.get("scale", [1, 1, 1])
    if not isinstance(s, list):
        s = [s, s, s]
    R = rot_matrix(*r)
    out = []
    for p in corners(name):
        scaled = [p[i] * s[i] / 16.0 for i in range(3)]
        rotated = apply(R, scaled)
        out.append([t[i] + rotated[i] for i in range(3)])
    lo = [min(p[i] for p in out) for i in range(3)]
    hi = [max(p[i] for p in out) for i in range(3)]
    ctr = [(lo[i] + hi[i]) / 2 for i in range(3)]
    ext = [hi[i] - lo[i] for i in range(3)]
    return lo, hi, ctr, ext


def report(label, name, key, override=None):
    lo, hi, ctr, ext = hand_space(name, key, override)
    f = lambda v: "[" + ", ".join(f"{x:+.3f}" for x in v) + "]"
    print(f"{label:<34} center {f(ctr)}  size {f(ext)}  lo {f(lo)} hi {f(hi)}")
    return ctr, ext


def solve_translation(name, key, scale, target_center):
    """Blockbench translation that puts the geometry AABB centre at target_center."""
    disp = json.load(open(MODEL.format(name), encoding="utf-8"))["display"][key]
    R = rot_matrix(*disp.get("rotation", [0, 0, 0]))
    _, _, ctr, _ = hand_space(name, key, {"scale": [scale] * 3, "translation": [0, 0, 0]})
    return [round((target_center[i] - ctr[i]) * 16, 2) for i in range(3)]


if __name__ == "__main__":
    if sys.argv[1:2] == ["solve"]:
        key, scale = sys.argv[2], float(sys.argv[3])
        target = [float(v) for v in sys.argv[4:7]]
        t = solve_translation("dimensional_relay", key, scale, target)
        print(f"{key}: scale {scale}  translation {t}")
        report("  result", "dimensional_relay", key, {"scale": [scale] * 3, "translation": t})
        report("  walkie_t2 reference", "walkie_t2", key)
        sys.exit()

    keys = sys.argv[1:] or ["firstperson_righthand", "thirdperson_righthand", "gui", "ground", "fixed"]
    for key in keys:
        print(f"--- {key} (blocks, hand frame) ---")
        report("walkie_t2 (reference)", "walkie_t2", key)
        report("dimensional_relay (current)", "dimensional_relay", key)
        print()
