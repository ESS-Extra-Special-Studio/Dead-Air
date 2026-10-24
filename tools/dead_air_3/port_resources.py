"""Copy Dead Air 3.0 assets and data from the Forge project into the NeoForge 1.21.1 project."""
import json
import shutil
import sys
from pathlib import Path

forge = Path(sys.argv[1])
neo = Path(sys.argv[2])
fa, na = forge / "assets/dead_air", neo / "assets/dead_air"


def copy(rel_from: Path, rel_to: Path):
    rel_to.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(rel_from, rel_to)


copied = 0
asset_globs = [
    "sounds/listener/*.ogg", "sounds/broadcast/*.ogg",
    "textures/entity/**/*.png", "textures/block/broken_relay*.png",
    "geo/entity/*.geo.json", "animations/entity/*.animation.json",
]
for item in ["walkie_t3", "signal_decoder", "relay_log", "signal_breaker", "component_distortion_core",
             "component_broadcaster_coil", "component_maw_fang", "component_scrambler_chip", "component_echo_crystal"]:
    asset_globs.append(f"textures/item/{item}.png")
for pattern in asset_globs:
    for f in fa.glob(pattern):
        copy(f, na / f.relative_to(fa))
        copied += 1
copy(fa / "models/item/the_listener_spawn_egg.json", na / "models/item/the_listener_spawn_egg.json")

for name in ["lang/en_us.json", "sounds.json"]:
    src = json.loads((fa / name).read_text(encoding="utf-8"))
    dst_path = na / name
    dst = json.loads(dst_path.read_text(encoding="utf-8"))
    added = [k for k in src if k not in dst]
    for k in added:
        dst[k] = src[k]
    dst_path.write_text(json.dumps(dst, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{name}: +{len(added)}")

for f in (forge / "data/dead_air/structures").rglob("*.nbt"):
    copy(f, neo / "data/dead_air/structure" / f.relative_to(forge / "data/dead_air/structures"))
    copied += 1
print(f"copied {copied} files")
