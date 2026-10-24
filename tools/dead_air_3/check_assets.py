"""Check that dead_air models, blockstates and sounds.json point at files that exist."""
import json
import sys
from pathlib import Path

assets = Path(sys.argv[1]) / "assets" / "dead_air"
problems = []


def res(ref, folder, ext):
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    if ns != "dead_air":
        return None
    return assets / folder / f"{path}{ext}"


for model in (assets / "models").rglob("*.json"):
    m = json.loads(model.read_text(encoding="utf-8"))
    for tex in (m.get("textures") or {}).values():
        if tex.startswith("#"):
            continue
        p = res(tex, "textures", ".png")
        if p and not p.exists():
            problems.append(f"{model.name}: missing texture {tex}")
    parent = m.get("parent")
    if parent:
        p = res(parent, "models", ".json")
        if p and not p.exists():
            problems.append(f"{model.name}: missing parent {parent}")

for bs in (assets / "blockstates").glob("*.json"):
    b = json.loads(bs.read_text(encoding="utf-8"))
    for v in (b.get("variants") or {}).values():
        for entry in v if isinstance(v, list) else [v]:
            p = res(entry["model"], "models", ".json")
            if p and not p.exists():
                problems.append(f"{bs.name}: missing model {entry['model']}")

sounds = json.loads((assets / "sounds.json").read_text(encoding="utf-8"))
for event, spec in sounds.items():
    for s in spec.get("sounds", []):
        name = s if isinstance(s, str) else s["name"]
        stream = isinstance(s, dict) and s.get("type") == "event"
        if stream:
            continue
        p = res(name, "sounds", ".ogg")
        if p and not p.exists():
            problems.append(f"sounds.json {event}: missing {name}")

for geo in (assets / "geo" / "entity").glob("*.geo.json"):
    base = geo.name[:-len(".geo.json")]
    for need in [assets / "textures/entity" / f"{base}.png", assets / "animations/entity" / f"{base}.animation.json"]:
        if not need.exists():
            problems.append(f"{geo.name}: missing {need.relative_to(assets)}")

print("\n".join(problems) if problems else "all asset references resolve")
