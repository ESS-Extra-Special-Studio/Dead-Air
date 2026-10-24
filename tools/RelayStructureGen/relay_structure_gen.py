"""Dead Air structure template generator.

Builds vanilla StructureTemplate .nbt files for the broken relay (small / medium / large)
and the survivor camp, writes them into the Forge 1.20.1 project (data/dead_air/structures/...)
and the NeoForge 1.21.1 port (data/dead_air/structure/...), verifies them with a round-trip
reader and renders isometric preview PNGs.

Usage (from the Dead Air project root):
    python tools/RelayStructureGen/relay_structure_gen.py
Options:
    --no-neoforge        skip copying into the NeoForge port
    --no-preview         skip PNG previews / README
    --preview-dir PATH   override preview output folder
"""
from __future__ import annotations

import argparse
import gzip
import io
import random
import shutil
import struct
import sys
from pathlib import Path

DATA_VERSION = 3465  # Minecraft 1.20.1

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[1]
FORGE_STRUCT_DIR = PROJECT_ROOT / "src" / "main" / "resources" / "data" / "dead_air" / "structures"
NEOFORGE_ROOT = PROJECT_ROOT.parent / "Dead Air - 1.21.1 NeoForge"
NEOFORGE_STRUCT_DIR = NEOFORGE_ROOT / "src" / "main" / "resources" / "data" / "dead_air" / "structure"
PREVIEW_DIR = Path.home() / "Desktop" / "Extra Special Studio" / "Minecraft" / "Dead Air" / "docs" / "relay-structures"

RELAY_ID = "dead_air:broken_relay"


# ---------------------------------------------------------------------------
# Minimal NBT writer / reader
# ---------------------------------------------------------------------------
class Byte(int):
    TID = 1


class Short(int):
    TID = 2


class Int(int):
    TID = 3


class Long(int):
    TID = 4


class Float(float):
    TID = 5


class Double(float):
    TID = 6


class ByteArray(bytes):
    TID = 7


class NList(list):
    TID = 9

    def __init__(self, elem_tid: int, items=()):
        super().__init__(items)
        self.elem_tid = elem_tid


class IntArray(list):
    TID = 11


class LongArray(list):
    TID = 12


def tid_of(v) -> int:
    if isinstance(v, str):
        return 8
    if isinstance(v, dict):
        return 10
    if hasattr(v, "TID"):
        return v.TID
    raise TypeError(f"Untyped NBT value {v!r} ({type(v).__name__}); wrap it in Byte/Int/...")


def _write_str(buf: io.BytesIO, s: str) -> None:
    raw = s.encode("utf-8")
    buf.write(struct.pack(">H", len(raw)))
    buf.write(raw)


def _write_payload(buf: io.BytesIO, tid: int, v) -> None:
    if tid == 1:
        buf.write(struct.pack(">b", v))
    elif tid == 2:
        buf.write(struct.pack(">h", v))
    elif tid == 3:
        buf.write(struct.pack(">i", v))
    elif tid == 4:
        buf.write(struct.pack(">q", v))
    elif tid == 5:
        buf.write(struct.pack(">f", v))
    elif tid == 6:
        buf.write(struct.pack(">d", v))
    elif tid == 7:
        buf.write(struct.pack(">i", len(v)))
        buf.write(bytes(v))
    elif tid == 8:
        _write_str(buf, v)
    elif tid == 9:
        elem = v.elem_tid if len(v) else (v.elem_tid if isinstance(v, NList) else 0)
        buf.write(struct.pack(">b", elem))
        buf.write(struct.pack(">i", len(v)))
        for item in v:
            if tid_of(item) != elem:
                raise TypeError(f"List element type mismatch: expected {elem}, got {tid_of(item)}")
            _write_payload(buf, elem, item)
    elif tid == 10:
        for k, item in v.items():
            t = tid_of(item)
            buf.write(struct.pack(">b", t))
            _write_str(buf, k)
            _write_payload(buf, t, item)
        buf.write(b"\x00")
    elif tid == 11:
        buf.write(struct.pack(">i", len(v)))
        for item in v:
            buf.write(struct.pack(">i", item))
    elif tid == 12:
        buf.write(struct.pack(">i", len(v)))
        for item in v:
            buf.write(struct.pack(">q", item))
    else:
        raise ValueError(f"Bad tag id {tid}")


def encode_nbt(root: dict) -> bytes:
    buf = io.BytesIO()
    buf.write(b"\x0a")
    _write_str(buf, "")
    _write_payload(buf, 10, root)
    return buf.getvalue()


def write_nbt(path: Path, root: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(encode_nbt(root), mtime=0))


class _Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def take(self, fmt: str):
        size = struct.calcsize(fmt)
        val = struct.unpack_from(fmt, self.d, self.i)
        self.i += size
        return val[0]

    def string(self) -> str:
        n = self.take(">H")
        s = self.d[self.i:self.i + n].decode("utf-8")
        self.i += n
        return s

    def payload(self, tid: int):
        if tid == 1:
            return Byte(self.take(">b"))
        if tid == 2:
            return Short(self.take(">h"))
        if tid == 3:
            return Int(self.take(">i"))
        if tid == 4:
            return Long(self.take(">q"))
        if tid == 5:
            return Float(self.take(">f"))
        if tid == 6:
            return Double(self.take(">d"))
        if tid == 7:
            n = self.take(">i")
            b = ByteArray(self.d[self.i:self.i + n])
            self.i += n
            return b
        if tid == 8:
            return self.string()
        if tid == 9:
            elem = self.take(">b")
            n = self.take(">i")
            return NList(elem, [self.payload(elem) for _ in range(n)])
        if tid == 10:
            out = {}
            while True:
                t = self.take(">b")
                if t == 0:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tid == 11:
            n = self.take(">i")
            return IntArray(self.take(">i") for _ in range(n))
        if tid == 12:
            n = self.take(">i")
            return LongArray(self.take(">q") for _ in range(n))
        raise ValueError(f"Bad tag id {tid} at offset {self.i}")


def decode_nbt(raw: bytes) -> dict:
    r = _Reader(raw)
    if r.take(">b") != 10:
        raise ValueError("Root is not a compound")
    if r.string() != "":
        raise ValueError("Root name is not empty")
    root = r.payload(10)
    if r.i != len(raw):
        raise ValueError(f"Trailing bytes after root ({len(raw) - r.i})")
    return root


def read_nbt(path: Path) -> tuple[dict, bytes]:
    raw = gzip.decompress(path.read_bytes())
    return decode_nbt(raw), raw


# ---------------------------------------------------------------------------
# Block state helpers
# ---------------------------------------------------------------------------
def B(name: str, **props):
    full = name if ":" in name else "minecraft:" + name
    return full, {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in props.items()}


AIR = B("air")


def slab(kind="bottom"):
    return B("weathered_cut_copper_slab", type=kind, waterlogged=False)


def stair(facing, half="bottom"):
    return B("weathered_cut_copper_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


BARS = B("iron_bars", east=False, north=False, south=False, west=False, waterlogged=False)


def chain(axis="y"):
    return B("chain", axis=axis, waterlogged=False)


def rod(facing="up"):
    return B("lightning_rod", facing=facing, powered=False, waterlogged=False)


def lantern(hanging):
    return B("lantern", hanging=hanging, waterlogged=False)


VEIN = B("sculk_vein", down=True, up=False, north=False, south=False, east=False, west=False, waterlogged=False)


def relay(facing):
    return B(RELAY_ID, facing=facing, active=True)


def chest(facing):
    return B("chest", facing=facing, type="single", waterlogged=False)


def log(axis):
    return B("spruce_log", axis=axis)


BOOL = {"true", "false"}
HORIZ = {"north", "south", "east", "west"}
ALL_DIRS = HORIZ | {"up", "down"}

# Every block id we intend to use, with its exact 1.20.1 property schema (None = no properties).
SCHEMA: dict[str, dict | None] = {
    "minecraft:air": None,
    "minecraft:weathered_copper": None,
    "minecraft:weathered_cut_copper": None,
    "minecraft:exposed_copper": None,
    "minecraft:oxidized_cut_copper": None,
    "minecraft:cracked_stone_bricks": None,
    "minecraft:stone_bricks": None,
    "minecraft:mossy_stone_bricks": None,
    "minecraft:mossy_cobblestone": None,
    "minecraft:andesite": None,
    "minecraft:gravel": None,
    "minecraft:coarse_dirt": None,
    "minecraft:dirt_path": None,
    "minecraft:moss_carpet": None,
    "minecraft:cobweb": None,
    "minecraft:sculk": None,
    "minecraft:brown_wool": None,
    "minecraft:gray_wool": None,
    "minecraft:green_wool": None,
    "minecraft:brown_carpet": None,
    "minecraft:green_carpet": None,
    "minecraft:crafting_table": None,
    "minecraft:weathered_cut_copper_slab": {"type": {"bottom", "top", "double"}, "waterlogged": BOOL},
    "minecraft:weathered_cut_copper_stairs": {
        "facing": HORIZ, "half": {"top", "bottom"},
        "shape": {"straight", "inner_left", "inner_right", "outer_left", "outer_right"}, "waterlogged": BOOL},
    "minecraft:iron_bars": {"east": BOOL, "north": BOOL, "south": BOOL, "west": BOOL, "waterlogged": BOOL},
    "minecraft:chain": {"axis": {"x", "y", "z"}, "waterlogged": BOOL},
    "minecraft:lightning_rod": {"facing": ALL_DIRS, "powered": BOOL, "waterlogged": BOOL},
    "minecraft:lantern": {"hanging": BOOL, "waterlogged": BOOL},
    "minecraft:sculk_vein": {d: BOOL for d in ("down", "up", "north", "south", "east", "west", "waterlogged")},
    "minecraft:chest": {"facing": HORIZ, "type": {"single", "left", "right"}, "waterlogged": BOOL},
    "minecraft:barrel": {"facing": ALL_DIRS, "open": BOOL},
    "minecraft:campfire": {"facing": HORIZ, "lit": BOOL, "signal_fire": BOOL, "waterlogged": BOOL},
    "minecraft:spruce_log": {"axis": {"x", "y", "z"}},
    RELAY_ID: {"facing": HORIZ, "active": BOOL},
}

# Full solid cubes that iron bars attach to.
SOLID = {
    "minecraft:weathered_copper", "minecraft:weathered_cut_copper", "minecraft:exposed_copper",
    "minecraft:oxidized_cut_copper", "minecraft:cracked_stone_bricks", "minecraft:stone_bricks",
    "minecraft:mossy_stone_bricks", "minecraft:mossy_cobblestone", "minecraft:andesite",
    "minecraft:brown_wool", "minecraft:gray_wool", "minecraft:green_wool", "minecraft:crafting_table",
    "minecraft:sculk",
}


# ---------------------------------------------------------------------------
# Template builder
# ---------------------------------------------------------------------------
class Template:
    def __init__(self, name: str, sx: int, sy: int, sz: int, seed: int):
        self.name = name
        self.size = (sx, sy, sz)
        self.rng = random.Random(seed)
        self.blocks: dict[tuple[int, int, int], tuple[str, dict, dict | None]] = {}
        self.carve_from_y = 1

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def put(self, x, y, z, spec, nbt=None):
        if not self.inside(x, y, z):
            raise ValueError(f"{self.name}: ({x},{y},{z}) outside {self.size}")
        name, props = spec
        self.blocks[(x, y, z)] = (name, dict(props), nbt)

    def name_at(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def remove(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, spec):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, spec)

    def pick(self, weighted):
        names = [n for n, _ in weighted]
        weights = [w for _, w in weighted]
        return B(self.rng.choices(names, weights)[0])

    def scatter_moss(self, count, tries=400):
        placed = 0
        for _ in range(tries):
            if placed >= count:
                break
            sx, _, sz = self.size
            x, z = self.rng.randrange(sx), self.rng.randrange(sz)
            if self.name_at(x, 0, z) in SOLID and (x, 1, z) not in self.blocks:
                self.put(x, 1, z, B("moss_carpet"))
                placed += 1

    def finalize(self):
        for (x, y, z), (name, props, nbt) in list(self.blocks.items()):
            if name != "minecraft:iron_bars":
                continue
            for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
                n = self.name_at(x + dx, y, z + dz)
                props[d] = "true" if (n == "minecraft:iron_bars" or n in SOLID) else "false"
        sx, sy, sz = self.size
        for x in range(sx):
            for y in range(self.carve_from_y, sy):
                for z in range(sz):
                    if (x, y, z) not in self.blocks:
                        self.put(x, y, z, AIR)

    def to_nbt(self) -> dict:
        palette_index: dict[tuple, int] = {}
        palette = NList(10)
        blocks = NList(10)
        for (x, y, z) in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[(x, y, z)]
            key = (name, tuple(sorted(props.items())))
            if key not in palette_index:
                palette_index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = {k: v for k, v in sorted(props.items())}
                palette.append(entry)
            block = {"pos": NList(3, [Int(x), Int(y), Int(z)]), "state": Int(palette_index[key])}
            if nbt is not None:
                block["nbt"] = nbt
            blocks.append(block)
        return {
            "DataVersion": Int(DATA_VERSION),
            "size": NList(3, [Int(v) for v in self.size]),
            "palette": palette,
            "blocks": blocks,
            "entities": NList(0),
        }


STONE_PAD = [("cracked_stone_bricks", 4), ("stone_bricks", 3), ("mossy_stone_bricks", 2), ("andesite", 1)]
EDGE = [("coarse_dirt", 3), ("gravel", 2), ("mossy_cobblestone", 1)]


def floor_pad(t: Template, x0, z0, x1, z1, edge_chance):
    sx, _, sz = t.size
    for x in range(sx):
        for z in range(sz):
            if x0 <= x <= x1 and z0 <= z <= z1:
                t.put(x, 0, z, t.pick(STONE_PAD))
            elif t.rng.random() < edge_chance:
                t.put(x, 0, z, t.pick(EDGE))


# ---------------------------------------------------------------------------
# Templates
# ---------------------------------------------------------------------------
def build_small() -> tuple[Template, dict]:
    t = Template("small", 7, 12, 7, seed=101)
    floor_pad(t, 1, 1, 5, 5, 0.55)
    t.put(5, 0, 1, B("gravel"))       # broken pad corners
    t.put(1, 0, 5, B("coarse_dirt"))

    mast = {1: B("weathered_cut_copper"), 2: BARS, 3: chain(), 4: BARS, 5: B("weathered_copper"),
            6: BARS, 7: chain(), 8: BARS, 9: B("exposed_copper"), 10: rod()}
    for y, spec in mast.items():
        t.put(3, y, 3, spec)
    t.put(2, 5, 3, rod("west"))
    t.put(4, 5, 3, rod("east"))
    t.put(2, 9, 3, slab("top"))
    t.put(4, 9, 3, slab("top"))
    t.put(2, 10, 3, rod())
    t.put(4, 10, 3, rod())

    relay_pos = (3, 1, 1)
    t.put(*relay_pos, relay("north"))
    t.put(3, 1, 2, VEIN)
    t.put(3, 0, 2, B("sculk"))
    t.put(3, 0, 1, B("sculk"))
    t.put(2, 0, 2, B("sculk"))

    # fallen dish, tipped over in the south-east corner
    t.put(4, 1, 5, stair("north"))
    t.put(5, 1, 5, B("oxidized_cut_copper"))
    t.put(5, 1, 4, stair("west"))
    t.put(5, 2, 5, slab("bottom"))
    t.put(4, 1, 4, slab("bottom"))

    t.put(2, 2, 3, B("cobweb"))
    t.put(4, 6, 3, B("cobweb"))
    t.scatter_moss(4)
    t.finalize()
    return t, {"relay": relay_pos, "facing": "north"}


def build_medium() -> tuple[Template, dict]:
    t = Template("medium", 11, 16, 11, seed=202)
    floor_pad(t, 1, 1, 9, 9, 0.5)

    # equipment shed: exterior x3..7 z1..5, interior 3x3
    for x in range(3, 8):
        for z in range(1, 6):
            if x in (3, 7) or z in (1, 5):
                corner = x in (3, 7) and z in (1, 5)
                for y in range(1, 4):
                    t.put(x, y, z, B("exposed_copper") if corner else
                          t.pick([("weathered_copper", 3), ("weathered_cut_copper", 2)]))
            t.put(x, 4, z, slab("bottom"))
    t.remove(5, 1, 1)                 # doorway
    t.remove(5, 2, 1)
    t.put(3, 2, 3, BARS)              # windows
    t.put(7, 2, 3, BARS)
    for p in [(6, 4, 4), (7, 4, 4), (7, 4, 5), (6, 4, 5), (7, 3, 5), (7, 3, 4)]:
        t.remove(*p)                  # collapsed back corner
    t.put(6, 1, 4, slab("bottom"))
    t.put(8, 1, 5, B("weathered_copper"))
    t.put(8, 1, 6, slab("bottom"))

    relay_pos = (5, 1, 2)
    t.put(*relay_pos, relay("north"))
    t.put(4, 3, 4, lantern(True))
    t.put(4, 3, 2, B("cobweb"))
    t.put(6, 3, 2, B("cobweb"))

    # sculk wiring: relay -> gap in back wall -> mast
    t.put(5, 1, 5, VEIN)
    for z in (3, 4, 5, 6, 7):
        t.put(5, 1, z, VEIN)
    for p in [(5, 0, 3), (5, 0, 6), (6, 0, 7), (4, 0, 6)]:
        t.put(*p, B("sculk"))

    # mast at (5, z=8)
    t.put(5, 1, 8, B("weathered_cut_copper"))
    t.put(4, 1, 8, stair("east"))
    t.put(6, 1, 8, stair("west"))
    t.put(5, 1, 9, stair("north"))
    for y, spec in {2: BARS, 3: chain(), 4: BARS, 5: chain(), 6: BARS}.items():
        t.put(5, y, 8, spec)
    t.put(5, 7, 8, B("weathered_copper"))
    for x in (3, 4, 6):
        t.put(x, 7, 8, slab("bottom"))
    for z in (6, 7, 9, 10):
        t.put(5, 7, z, slab("bottom"))
    t.put(8, 1, 9, slab("bottom"))    # missing brace slab (7,7,8) fell here
    for p in [(3, 8, 8), (5, 8, 10), (5, 8, 6)]:
        t.put(*p, BARS)
    for y, spec in {8: BARS, 9: chain(), 10: BARS, 11: chain(), 12: BARS,
                    13: B("exposed_copper"), 14: rod()}.items():
        t.put(5, y, 8, spec)
    t.put(4, 13, 8, rod("west"))
    t.put(6, 13, 8, rod("east"))
    t.put(6, 9, 8, B("cobweb"))
    t.put(4, 5, 8, B("cobweb"))
    t.scatter_moss(6)
    t.finalize()
    return t, {"relay": relay_pos, "facing": "north"}


def build_large() -> tuple[Template, dict]:
    t = Template("large", 15, 24, 15, seed=303)
    for x in range(15):
        for z in range(15):
            edge = x in (0, 14) or z in (0, 14)
            if edge:
                t.put(x, 0, z, t.pick(EDGE))
            elif t.rng.random() < 0.15:
                t.put(x, 0, z, t.pick([("coarse_dirt", 2), ("gravel", 1)]))
            else:
                t.put(x, 0, z, t.pick(STONE_PAD))

    # fenced compound (iron bars), gate in front of the hut + broken sections
    for i in range(15):
        for (x, z) in [(i, 0), (i, 14), (0, i), (14, i)]:
            t.put(x, 1, z, BARS)
            t.put(x, 2, z, BARS)
    for x in (3, 4, 5):
        t.remove(x, 1, 0)
        t.remove(x, 2, 0)
    for p in [(10, 2, 0), (11, 2, 0), (0, 2, 6), (0, 2, 7), (0, 1, 7), (6, 2, 14), (14, 2, 4)]:
        t.remove(*p)
    for z in (11, 12, 13):
        t.remove(14, 1, z)
        t.remove(14, 2, z)

    # equipment hut: exterior x2..6 z2..6
    for x in range(2, 7):
        for z in range(2, 7):
            if x in (2, 6) or z in (2, 6):
                corner = x in (2, 6) and z in (2, 6)
                for y in range(1, 4):
                    t.put(x, y, z, B("exposed_copper") if corner else
                          t.pick([("weathered_copper", 3), ("weathered_cut_copper", 2), ("oxidized_cut_copper", 1)]))
            t.put(x, 4, z, slab("bottom"))
    t.remove(4, 1, 2)                 # doorway
    t.remove(4, 2, 2)
    t.put(2, 2, 4, BARS)
    t.put(4, 2, 6, BARS)
    for p in [(6, 4, 6), (5, 4, 6), (6, 4, 5), (6, 3, 6)]:
        t.remove(*p)
    relay_pos = (4, 1, 3)
    t.put(*relay_pos, relay("north"))
    t.put(3, 1, 5, chest("east"), nbt={"id": "minecraft:chest", "LootTable": "dead_air:chests/broken_relay"})
    t.put(4, 3, 4, lantern(True))
    t.put(3, 3, 3, B("cobweb"))
    t.put(5, 3, 5, B("cobweb"))

    # sculk wiring relay -> wall gap -> tower junction
    t.remove(6, 1, 4)
    for p in [(4, 1, 4), (5, 1, 4), (6, 1, 4), (7, 1, 4), (7, 1, 5), (7, 1, 6), (7, 1, 7), (7, 1, 8)]:
        t.put(*p, VEIN)
    for p in [(5, 0, 4), (7, 0, 5), (7, 0, 7), (8, 0, 8), (6, 0, 9), (7, 0, 9)]:
        t.put(*p, B("sculk"))
    t.put(7, 1, 9, B("weathered_copper"))

    # lattice tower centred on (7, z=9): legs converge 2 -> 1 -> centre
    cx, cz = 7, 9
    for y in range(1, 18):
        off = 2 if y <= 9 else 1
        for dx in (-off, off):
            for dz in (-off, off):
                if y == 1:
                    spec = B("weathered_cut_copper")
                elif y in (9, 17):
                    spec = B("weathered_copper")
                elif y in (5, 13):
                    spec = B("exposed_copper")
                else:
                    spec = BARS
                t.put(cx + dx, y, cz + dz, spec)
    for x in range(cx - 1, cx + 2):
        t.put(x, 5, cz - 2, BARS)
        t.put(x, 5, cz + 2, BARS)
    for z in range(cz - 1, cz + 2):
        t.put(cx - 2, 5, z, BARS)
        t.put(cx + 2, 5, z, BARS)
    for (x, z) in [(cx, cz - 1), (cx, cz + 1), (cx - 1, cz), (cx + 1, cz)]:
        t.put(x, 13, z, BARS)
    for y in range(14, 18):           # one upper leg snapped
        t.remove(cx + 1, y, cz + 1)
    t.put(cx, 18, cz, B("weathered_copper"))
    t.put(cx, 19, cz, BARS)
    t.put(cx, 20, cz, slab("bottom"))
    t.put(6, 6, 10, B("cobweb"))
    t.put(7, 12, 9, B("cobweb"))

    # collapsed top section lying on the ground, through the broken east fence
    t.put(9, 1, 12, B("weathered_copper"))
    t.put(10, 1, 12, BARS)
    t.put(11, 1, 12, chain("x"))
    t.put(12, 1, 12, BARS)
    t.put(13, 1, 12, B("exposed_copper"))
    t.put(14, 1, 12, rod("east"))
    t.put(10, 1, 13, slab("bottom"))
    t.put(12, 1, 11, stair("south"))
    t.put(8, 1, 13, rod("south"))

    t.scatter_moss(12)
    t.finalize()
    return t, {"relay": relay_pos, "facing": "north"}


def build_camp() -> tuple[Template, dict]:
    t = Template("camp", 9, 5, 9, seed=404)
    for x in range(9):
        for z in range(9):
            d = ((x - 4) ** 2 + (z - 4) ** 2) ** 0.5
            if d <= 3.5:
                t.put(x, 0, z, t.pick([("coarse_dirt", 3), ("dirt_path", 2)]))
            elif t.rng.random() < 0.3:
                t.put(x, 0, z, B("coarse_dirt"))

    # tent: A-frame along X, closed at x=1, open to the east at x=5
    for x in range(1, 6):
        for z in (0, 2):
            t.put(x, 1, z, t.pick([("brown_wool", 4), ("gray_wool", 1), ("green_wool", 1)]))
            t.put(x, 2, z, B("brown_carpet"))
        t.put(x, 2, 1, B("brown_wool"))
    t.put(1, 1, 1, B("gray_wool"))
    t.put(2, 1, 1, B("green_carpet"))
    t.put(3, 1, 1, B("green_carpet"))

    t.put(4, 1, 4, B("campfire", facing="north", lit=True, signal_fire=False, waterlogged=False))
    for x in (3, 4, 5):
        t.put(x, 1, 6, log("x"))
    t.put(6, 1, 3, log("z"))
    t.put(6, 1, 4, log("z"))
    t.put(1, 1, 6, log("z"))
    t.put(1, 1, 7, log("z"))
    t.put(7, 1, 6, B("barrel", facing="up", open=False),
          nbt={"id": "minecraft:barrel", "LootTable": "dead_air:chests/survivor_camp"})
    t.put(7, 1, 7, B("crafting_table"))
    t.put(7, 1, 5, lantern(False))

    # dirt path reverts to dirt under blocks; keep it only under open air
    for (x, y, z), (name, _, _) in list(t.blocks.items()):
        if y == 0 and name == "minecraft:dirt_path" and (x, 1, z) in t.blocks:
            t.put(x, 0, z, B("coarse_dirt"))

    spawn = (3, 1, 4)
    t.finalize()
    return t, {"spawn": spawn}


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------
def verify(path: Path, expected_size, is_relay: bool, expect_air=None) -> dict:
    root, raw = read_nbt(path)
    if encode_nbt(root) != raw:
        raise AssertionError(f"{path.name}: round-trip re-encode mismatch")
    assert int(root["DataVersion"]) == DATA_VERSION, "DataVersion"
    size = tuple(int(v) for v in root["size"])
    assert size == tuple(expected_size), f"size {size} != {expected_size}"
    palette = root["palette"]
    for entry in palette:
        name = entry["Name"]
        if name not in SCHEMA:
            raise AssertionError(f"{path.name}: unexpected block id {name}")
        schema = SCHEMA[name]
        props = entry.get("Properties", {})
        if schema is None:
            assert not props, f"{name} should have no properties"
        else:
            assert set(props) == set(schema), f"{name} props {sorted(props)} != {sorted(schema)}"
            for k, v in props.items():
                assert v in schema[k], f"{name}.{k}={v} invalid"
    assert root["entities"].elem_tid in (0, 10) and len(root["entities"]) == 0
    mins, maxs = [10 ** 9] * 3, [-1] * 3
    relays = []
    seen = set()
    air_positions = set()
    for b in root["blocks"]:
        pos = tuple(int(v) for v in b["pos"])
        st = int(b["state"])
        assert 0 <= st < len(palette), f"state {st} out of range"
        assert pos not in seen, f"duplicate pos {pos}"
        seen.add(pos)
        for i in range(3):
            assert 0 <= pos[i] < size[i], f"pos {pos} outside size {size}"
            mins[i] = min(mins[i], pos[i])
            maxs[i] = max(maxs[i], pos[i])
        name = palette[st]["Name"]
        if name == RELAY_ID:
            relays.append((pos, palette[st]["Properties"]["facing"]))
        if name == "minecraft:air":
            air_positions.add(pos)
        if "nbt" in b:
            assert "LootTable" in b["nbt"] and "id" in b["nbt"]
    assert mins == [0, 0, 0] and [m + 1 for m in maxs] == list(size), f"bounds {mins}..{maxs} vs size {size}"
    if is_relay:
        assert len(relays) == 1, f"expected exactly one relay, found {len(relays)}"
    else:
        assert not relays
    if expect_air is not None:
        assert tuple(expect_air) in air_positions, f"{expect_air} is not air"
    return {"palette": len(palette), "blocks": len(root["blocks"]), "relays": relays, "size": size}


# ---------------------------------------------------------------------------
# Isometric preview renderer
# ---------------------------------------------------------------------------
COLOURS = {
    "minecraft:weathered_copper": (108, 153, 110), "minecraft:weathered_cut_copper": (104, 146, 104),
    "minecraft:weathered_cut_copper_slab": (104, 146, 104), "minecraft:weathered_cut_copper_stairs": (104, 146, 104),
    "minecraft:exposed_copper": (161, 125, 103), "minecraft:oxidized_cut_copper": (79, 153, 126),
    "minecraft:iron_bars": (120, 122, 128), "minecraft:chain": (58, 64, 78), "minecraft:lightning_rod": (205, 115, 80),
    "minecraft:cobweb": (228, 228, 232), "minecraft:cracked_stone_bricks": (118, 117, 118),
    "minecraft:stone_bricks": (125, 124, 125), "minecraft:mossy_stone_bricks": (112, 122, 100),
    "minecraft:mossy_cobblestone": (104, 116, 92), "minecraft:andesite": (138, 138, 139),
    "minecraft:gravel": (131, 127, 126), "minecraft:coarse_dirt": (119, 85, 59), "minecraft:dirt_path": (148, 122, 65),
    "minecraft:moss_carpet": (89, 110, 45), "minecraft:sculk": (14, 36, 44), "minecraft:sculk_vein": (24, 82, 96),
    RELAY_ID: (210, 64, 44), "minecraft:lantern": (236, 178, 72), "minecraft:chest": (164, 114, 46),
    "minecraft:barrel": (134, 98, 56), "minecraft:campfire": (236, 124, 40), "minecraft:spruce_log": (70, 52, 32),
    "minecraft:brown_wool": (114, 71, 40), "minecraft:gray_wool": (62, 68, 71), "minecraft:green_wool": (84, 109, 27),
    "minecraft:brown_carpet": (120, 76, 44), "minecraft:green_carpet": (90, 116, 32),
    "minecraft:crafting_table": (142, 102, 60),
}

THIN = {"minecraft:moss_carpet", "minecraft:brown_carpet", "minecraft:green_carpet", "minecraft:sculk_vein"}


def block_boxes(name, props):
    """Boxes (x0,x1,y0,y1,z0,z1) in block-local units."""
    if name in THIN:
        return [(0, 1, 0, 0.08, 0, 1)]
    if name.endswith("_slab"):
        return {"bottom": [(0, 1, 0, 0.5, 0, 1)], "top": [(0, 1, 0.5, 1, 0, 1)]}.get(props.get("type"), [(0, 1, 0, 1, 0, 1)])
    if name.endswith("_stairs"):
        f = props["facing"]
        back = {"north": (0, 1, 0.5, 1, 0, 0.5), "south": (0, 1, 0.5, 1, 0.5, 1),
                "west": (0, 0.5, 0.5, 1, 0, 1), "east": (0.5, 1, 0.5, 1, 0, 1)}[f]
        return [(0, 1, 0, 0.5, 0, 1), back]
    if name == "minecraft:iron_bars":
        boxes = [(0.42, 0.58, 0, 1, 0.42, 0.58)]
        arms = {"north": (0.44, 0.56, 0, 1, 0, 0.42), "south": (0.44, 0.56, 0, 1, 0.58, 1),
                "west": (0, 0.42, 0, 1, 0.44, 0.56), "east": (0.58, 1, 0, 1, 0.44, 0.56)}
        boxes += [arms[d] for d in arms if props.get(d) == "true"]
        return boxes
    if name in ("minecraft:chain", "minecraft:lightning_rod"):
        w = 0.1 if name == "minecraft:chain" else 0.12
        lo, hi = 0.5 - w, 0.5 + w
        axis = props.get("axis") or {"up": "y", "down": "y", "north": "z", "south": "z"}.get(props.get("facing"), "x")
        return [{"x": (0, 1, lo, hi, lo, hi), "y": (lo, hi, 0, 1, lo, hi), "z": (lo, hi, lo, hi, 0, 1)}[axis]]
    if name == "minecraft:lantern":
        return [(0.3, 0.7, 0.35, 0.85, 0.3, 0.7)] if props.get("hanging") == "true" else [(0.3, 0.7, 0, 0.5, 0.3, 0.7)]
    if name == "minecraft:cobweb":
        return [(0.15, 0.85, 0.15, 0.85, 0.15, 0.85)]
    if name == "minecraft:campfire":
        return [(0, 1, 0, 0.45, 0, 1)]
    if name == "minecraft:chest":
        return [(0.06, 0.94, 0, 0.88, 0.06, 0.94)]
    if name == RELAY_ID:
        return [(0.1, 0.9, 0, 0.8, 0.1, 0.9)]
    return [(0, 1, 0, 1, 0, 1)]


def render_preview(t: Template, out: Path, title: str, markers=()):
    from PIL import Image, ImageDraw

    sx, sy, sz = t.size
    target = 820
    span_w = sx + sz
    span_h = (sx + sz) * 0.5 + sy
    s = max(8, int(target / max(span_w, span_h)))
    hw, hq = s * 0.9, s * 0.45

    def proj(a, y, b):
        return (a - b) * hw, (a + b) * hq - y * s

    corners = [proj(a, y, b) for a in (0, sx) for y in (0, sy) for b in (0, sz)]
    minx = min(c[0] for c in corners)
    maxx = max(c[0] for c in corners)
    miny = min(c[1] for c in corners)
    maxy = max(c[1] for c in corners)
    pad, header = 24, 40
    w, h = int(maxx - minx + 2 * pad), int(maxy - miny + 2 * pad + header)
    img = Image.new("RGB", (w, h), (26, 28, 32))
    dr = ImageDraw.Draw(img)
    ox, oy = pad - minx, pad + header - miny

    def P(a, y, b):
        px, py = proj(a, y, b)
        return px + ox, py + oy

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)

    items = []
    for (x, y, z), (name, props, _) in t.blocks.items():
        if name == "minecraft:air":
            continue
        b = sz - 1 - z
        items.append((x + b + y, y, x, name, props, b))
    items.sort(key=lambda i: (i[0], i[1], i[2]))
    for _, y, x, name, props, b in items:
        col = COLOURS.get(name, (255, 0, 255))
        boxes = block_boxes(name, props)
        boxes.sort(key=lambda bx: (bx[1] + (1 - bx[4]) + bx[3]))
        for (x0, x1, y0, y1, z0, z1) in boxes:
            A0, A1 = x + x0, x + x1
            Y0, Y1 = y + y0, y + y1
            B0, B1 = b + (1 - z1), b + (1 - z0)
            edge = shade(col, 0.45)
            top = [P(A0, Y1, B0), P(A1, Y1, B0), P(A1, Y1, B1), P(A0, Y1, B1)]
            right = [P(A1, Y0, B0), P(A1, Y1, B0), P(A1, Y1, B1), P(A1, Y0, B1)]
            left = [P(A0, Y0, B1), P(A1, Y0, B1), P(A1, Y1, B1), P(A0, Y1, B1)]
            dr.polygon(left, fill=shade(col, 0.8), outline=edge)
            dr.polygon(right, fill=shade(col, 0.62), outline=edge)
            dr.polygon(top, fill=shade(col, 1.0), outline=edge)
    for (mx, my, mz), label, colour in markers:
        cx, cy = P(mx + 0.5, my + 1.15, (sz - 1 - mz) + 0.5)
        dr.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], outline=colour, width=3)
        dr.text((cx + 9, cy - 7), label, fill=colour)
    dr.text((pad, 12), title, fill=(235, 235, 235))
    dr.text((pad, 26), "view from north-east (front = -Z faces lower-left)", fill=(150, 150, 150))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    return img.size


# ---------------------------------------------------------------------------
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--no-neoforge", action="store_true")
    ap.add_argument("--no-preview", action="store_true")
    ap.add_argument("--preview-dir", type=Path, default=PREVIEW_DIR)
    args = ap.parse_args(argv)

    jobs = [
        ("broken_relay/small.nbt", build_small, True),
        ("broken_relay/medium.nbt", build_medium, True),
        ("broken_relay/large.nbt", build_large, True),
        ("survivor_camp/camp.nbt", build_camp, False),
    ]
    report = []
    for rel, builder, is_relay in jobs:
        t, info = builder()
        forge_path = FORGE_STRUCT_DIR / rel
        write_nbt(forge_path, t.to_nbt())
        v = verify(forge_path, t.size, is_relay, expect_air=info.get("spawn"))
        if is_relay:
            pos, facing = v["relays"][0]
            assert pos == info["relay"] and facing == info["facing"]
        written = [forge_path]
        if not args.no_neoforge:
            if not NEOFORGE_ROOT.is_dir():
                print(f"WARNING: NeoForge port not found at {NEOFORGE_ROOT}; skipping copy")
            else:
                neo_path = NEOFORGE_STRUCT_DIR / rel
                neo_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(forge_path, neo_path)
                assert neo_path.read_bytes() == forge_path.read_bytes()
                written.append(neo_path)
        png = None
        if not args.no_preview:
            png = args.preview_dir / (t.name + ".png")
            markers = []
            if is_relay:
                markers.append((info["relay"], "relay", (255, 90, 70)))
            if "spawn" in info:
                markers.append((info["spawn"], "survivor", (90, 200, 255)))
            dims = render_preview(t, png, f"Dead Air - {rel}  size {t.size[0]}x{t.size[1]}x{t.size[2]}", markers)
            png_info = f"{png} ({dims[0]}x{dims[1]})"
        report.append((rel, t, info, v, written, png_info if png else None))
        print(f"[ok] {rel}: size={t.size} palette={v['palette']} blocks={v['blocks']} " +
              (f"relay={info['relay']} facing={info['facing']}" if is_relay else f"survivor_spawn={info['spawn']}"))
        for p in written:
            print(f"     wrote {p}")
        if png:
            print(f"     preview {png_info}")

    if not args.no_preview:
        lines = [
            "# Dead Air relay / camp structure templates",
            "",
            "Generated by `tools/RelayStructureGen/relay_structure_gen.py` in the Dead Air (Forge 1.20.1) repo.",
            "Templates use DataVersion 3465 (1.20.1). y=0 is the floor layer; front faces -Z (north).",
            "Positions above the floor inside the footprint are explicit air so placement clears terrain.",
            "",
            "| Template | Size (x,y,z) | Key position (template-local) |",
            "|---|---|---|",
        ]
        for rel, t, info, v, _, _ in report:
            key = (f"`dead_air:broken_relay` at {info['relay']}, facing {info['facing']}" if "relay" in info
                   else f"survivor spawn (air next to campfire) at {info['spawn']}")
            lines.append(f"| `{rel}` | {t.size} | {key} |")
        lines += [
            "",
            "Large relay chest: `minecraft:chest` with LootTable `dead_air:chests/broken_relay`.",
            "Camp barrel: `minecraft:barrel` with LootTable `dead_air:chests/survivor_camp`.",
            "",
            "Output paths:",
            "- Forge: `src/main/resources/data/dead_air/structures/{broken_relay,survivor_camp}/`",
            "- NeoForge 1.21.1 port: `src/main/resources/data/dead_air/structure/{broken_relay,survivor_camp}/` (singular)",
            "",
            "## Regenerate",
            "",
            "```powershell",
            'Set-Location "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air"',
            "python tools\\RelayStructureGen\\relay_structure_gen.py",
            "```",
            "",
            "Flags: `--no-neoforge`, `--no-preview`, `--preview-dir <path>`. Output is deterministic (seeded RNG).",
            "Previews: `small.png`, `medium.png`, `large.png`, `camp.png` in this folder (red ring = relay, blue = survivor).",
        ]
        (args.preview_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
