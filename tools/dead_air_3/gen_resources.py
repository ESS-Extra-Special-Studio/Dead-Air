"""Generate Dead Air 3.0 models, blockstates, recipes, tags and loot tables.

Usage: gen_resources.py <src/main/resources> [--neo]
--neo writes 1.21 layout (recipe/, loot_table/, tags/item/, result "id").
"""
import json
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1])
neo = "--neo" in sys.argv
assets = root / "assets" / "dead_air"
data = root / "data" / "dead_air"
recipe_dir = data / ("recipe" if neo else "recipes")
loot_dir = data / ("loot_table" if neo else "loot_tables")
tag_dir = data / "tags" / ("item" if neo else "items")


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def item(id_):
    return {"item": f"dead_air:{id_}"} if ":" not in id_ else {"item": id_}


def result(id_, count=1):
    key = "id" if neo else "item"
    return {key: id_, "count": count}


# --- item models ---
for name in ["signal_decoder", "relay_log", "component_distortion_core", "component_broadcaster_coil",
             "component_maw_fang", "component_scrambler_chip", "component_echo_crystal"]:
    write(assets / "models/item" / f"{name}.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"dead_air:item/{name}"}})
write(assets / "models/item/signal_breaker.json",
      {"parent": "minecraft:item/handheld", "textures": {"layer0": "dead_air:item/signal_breaker"}})
for egg in ["signal_anchor_spawn_egg", "survivor_spawn_egg"]:
    write(assets / "models/item" / f"{egg}.json", {"parent": "minecraft:item/template_spawn_egg"})
shutil.copyfile(assets / "models/item/walkie_t2.json", assets / "models/item/walkie_t3.json")
write(assets / "models/item/broken_relay.json", {"parent": "dead_air:block/broken_relay"})

# --- block ---
for model, front in [("broken_relay", "broken_relay_front"), ("broken_relay_off", "broken_relay_front_off")]:
    write(assets / "models/block" / f"{model}.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": "dead_air:block/broken_relay_top",
            "front": f"dead_air:block/{front}",
            "side": "dead_air:block/broken_relay_side",
        },
    })
variants = {}
for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    for active in ["true", "false"]:
        v = {"model": "dead_air:block/" + ("broken_relay" if active == "true" else "broken_relay_off")}
        if y:
            v["y"] = y
        variants[f"active={active},facing={facing}"] = v
write(assets / "blockstates/broken_relay.json", {"variants": variants})

# --- recipes ---
write(recipe_dir / "walkie_t3.json", {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": ["CUC", "RWR", "NSN"],
    "key": {
        "C": item("resonant_chord"),
        "U": item("signal_upgrade"),
        "R": item("dimensional_relay"),
        "W": item("walkie_t2"),
        "N": item("static_note"),
        "S": item("signal_decoder"),
    },
    "result": result("dead_air:walkie_t3"),
})
write(recipe_dir / "signal_decoder.json", {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": [" N ", "RCR", " U "],
    "key": {
        "N": item("static_note"),
        "R": item("dimensional_relay"),
        "C": item("resonant_chord"),
        "U": item("signal_upgrade"),
    },
    "result": result("dead_air:signal_decoder"),
})
write(recipe_dir / "signal_breaker.json", {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": [" EC", "DMS", "N  "],
    "key": {
        "E": item("component_echo_crystal"),
        "C": item("component_broadcaster_coil"),
        "D": item("component_distortion_core"),
        "M": item("component_maw_fang"),
        "S": item("component_scrambler_chip"),
        "N": item("minecraft:netherite_ingot"),
    },
    "result": result("dead_air:signal_breaker"),
})

# --- tags ---
radios = json.loads((tag_dir / "walkie_radios.json").read_text(encoding="utf-8"))
if "dead_air:walkie_t3" not in radios["values"]:
    radios["values"].append("dead_air:walkie_t3")
write(tag_dir / "walkie_radios.json", radios)
write(tag_dir / "walkie_t3.json", {"replace": False, "values": ["dead_air:walkie_t3"]})


# --- loot tables ---
def entry(name, weight, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1:
        e["functions"] = [{"function": "minecraft:set_count",
                           "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def table(kind, rolls, entries, extra_pools=()):
    pools = [{"rolls": rolls, "entries": entries}]
    pools.extend(extra_pools)
    return {"type": f"minecraft:{kind}", "pools": pools}


write(loot_dir / "chests/broken_relay.json", table("chest", {"type": "minecraft:uniform", "min": 3, "max": 6}, [
    entry("minecraft:copper_ingot", 20, 2, 6),
    entry("minecraft:redstone", 18, 3, 9),
    entry("minecraft:iron_ingot", 12, 1, 4),
    entry("minecraft:quartz", 10, 2, 6),
    entry("minecraft:amethyst_shard", 8, 1, 3),
    entry("minecraft:comparator", 5),
    entry("minecraft:lightning_rod", 5),
    entry("minecraft:diamond", 3),
    entry("dead_air:static_note", 4),
]))
write(loot_dir / "chests/survivor_camp.json", table("chest", {"type": "minecraft:uniform", "min": 3, "max": 6}, [
    entry("minecraft:bread", 20, 2, 5),
    entry("minecraft:cooked_beef", 12, 1, 3),
    entry("minecraft:torch", 15, 4, 12),
    entry("minecraft:string", 10, 2, 6),
    entry("minecraft:leather", 10, 1, 4),
    entry("minecraft:arrow", 10, 4, 12),
    entry("minecraft:paper", 8, 2, 6),
    entry("minecraft:iron_nugget", 10, 3, 9),
    entry("minecraft:map", 3),
]))
supplies = {
    "scavenger": [
        entry("minecraft:iron_ingot", 15, 1, 3), entry("minecraft:copper_ingot", 15, 2, 5),
        entry("minecraft:coal", 15, 3, 8), entry("minecraft:string", 10, 2, 5),
        entry("minecraft:arrow", 10, 4, 10), entry("minecraft:gold_nugget", 8, 3, 9),
        entry("minecraft:leather", 8, 1, 3), entry("minecraft:diamond", 2),
    ],
    "medic": [
        entry("minecraft:golden_carrot", 15, 1, 3), entry("minecraft:bread", 15, 2, 4),
        entry("minecraft:honey_bottle", 10, 1, 2), entry("minecraft:glistering_melon_slice", 8, 1, 2),
        entry("minecraft:golden_apple", 3), entry("minecraft:milk_bucket", 6),
        entry("minecraft:cooked_chicken", 12, 1, 3),
    ],
    "radio_operator": [
        entry("minecraft:redstone", 18, 3, 8), entry("minecraft:quartz", 12, 2, 5),
        entry("minecraft:copper_ingot", 14, 2, 5), entry("minecraft:amethyst_shard", 8, 1, 3),
        entry("minecraft:comparator", 4), entry("minecraft:repeater", 6, 1, 2),
        entry("dead_air:static_note", 4),
    ],
}
for prof, entries in supplies.items():
    write(loot_dir / f"gameplay/survivor_supplies/{prof}.json",
          table("gift", {"type": "minecraft:uniform", "min": 2, "max": 4}, entries))

print("generated into", root, "(neo)" if neo else "(forge)")
