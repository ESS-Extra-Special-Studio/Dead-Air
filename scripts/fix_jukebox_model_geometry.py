"""
Post-process Blockbench jukebox_upgrade item model:
- Expand any zero-thickness axis to EPS (stops z-fighting between coplanar faces).
- Lift #3/#4 decal groups that sit on the slab top (y ~= 1.0–1.05) above y=1 so they
  do not share a plane with the base's top face.
"""
from __future__ import annotations

import json
from pathlib import Path

EPS = 0.02
LIFT_DECAL_Y = 0.032

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "src/main/resources/assets/dead_air/models/item/jukebox_upgrade.json"


def face_textures(el: dict) -> set[str]:
    out: set[str] = set()
    for fc in el.get("faces", {}).values():
        if isinstance(fc, dict) and "texture" in fc:
            out.add(fc["texture"])
    return out


def main() -> None:
    data = json.loads(MODEL.read_text(encoding="utf-8"))
    for el in data["elements"]:
        f, t = el["from"], el["to"]
        for i in range(3):
            if abs(t[i] - f[i]) < 1e-6:
                t[i] = round(f[i] + EPS, 5)

        tex = face_textures(el)
        if "#3" in tex or "#4" in tex:
            ymin = min(f[1], t[1])
            ymax = max(f[1], t[1])
            if ymin >= 0.98 and ymax <= 1.15:
                f[1] = round(f[1] + LIFT_DECAL_Y, 5)
                t[1] = round(t[1] + LIFT_DECAL_Y, 5)

    MODEL.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n", encoding="utf-8")
    print("Wrote", MODEL)


if __name__ == "__main__":
    main()
