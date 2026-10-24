import math
import os

out_dir = r"C:\Users\Ksivi\IdeaProjects\Dead Air\src\main\resources\assets\dead_air\models\block"
os.makedirs(out_dir, exist_ok=True)

cx, cz = 8.0, 8.0
y_base_top = 1.5
y_tip = 14.2
r_bot = 4.6
n = 6


def ring(y, r):
    verts = []
    for i in range(n):
        a = 2 * math.pi * i / n - math.pi / 2
        verts.append((cx + r * math.cos(a), y, cz + r * math.sin(a)))
    return verts


def r_at(y):
    t = (y - y_base_top) / (y_tip - y_base_top)
    return r_bot * (1.0 - t)


y_b1a, y_b1b = 5.7, 7.1
y_b2a, y_b2b = 10.0, 11.2

rings = {
    "bot": ring(y_base_top, r_bot),
    "b1a": ring(y_b1a, r_at(y_b1a)),
    "b1b": ring(y_b1b, r_at(y_b1b)),
    "b2a": ring(y_b2a, r_at(y_b2a)),
    "b2b": ring(y_b2b, r_at(y_b2b)),
}
tip = (cx, y_tip, cz)

verts = []
idx = {}


def add(v, key=None):
    verts.append(v)
    i = len(verts)
    if key is not None:
        idx[key] = i
    return i


base_y0 = [(3, 0, 3), (13, 0, 3), (13, 0, 13), (3, 0, 13)]
base_y1 = [(3, 1.2, 3), (13, 1.2, 3), (13, 1.2, 13), (3, 1.2, 13)]
base_lip = [(3.8, 1.2, 3.8), (12.2, 1.2, 3.8), (12.2, 1.2, 12.2), (3.8, 1.2, 12.2)]
base_lip_top = [(3.8, 1.5, 3.8), (12.2, 1.5, 3.8), (12.2, 1.5, 12.2), (3.8, 1.5, 12.2)]

for i, v in enumerate(base_y0):
    add(v, f"b0_{i}")
for i, v in enumerate(base_y1):
    add(v, f"b1_{i}")
for i, v in enumerate(base_lip):
    add(v, f"bl_{i}")
for i, v in enumerate(base_lip_top):
    add(v, f"blt_{i}")

add(tip, "tip")
for name, vs in rings.items():
    for i, v in enumerate(vs):
        add(v, f"{name}_{i}")

uvs = [(0, 0), (1, 0), (1, 1), (0, 1)]

obj = [
    "# Dead Air traffic cone — 6-sided triangular cone",
    "mtllib traffic_cone.mtl",
    "o traffic_cone",
]
# Forge OBJ space: 1.0 = 1 block. Authoring used 0–16 px, so scale.
for v in verts:
    obj.append(f"v {v[0] / 16.0:.6f} {v[1] / 16.0:.6f} {v[2] / 16.0:.6f}")
for u in uvs:
    obj.append(f"vt {u[0]:.4f} {u[1]:.4f}")
obj.append("vn 0 1 0")


def face(mat, *vis):
    obj.append(f"usemtl {mat}")
    parts = []
    for j, k in enumerate(vis):
        vi = idx[k]
        ti = (j % 4) + 1
        parts.append(f"{vi}/{ti}/1")
    obj.append("f " + " ".join(parts))


face("base", "b0_0", "b0_1", "b1_1", "b1_0")
face("base", "b0_1", "b0_2", "b1_2", "b1_1")
face("base", "b0_2", "b0_3", "b1_3", "b1_2")
face("base", "b0_3", "b0_0", "b1_0", "b1_3")
face("base", "b1_0", "b1_1", "b1_2", "b1_3")
face("base", "b0_0", "b0_3", "b0_2", "b0_1")
face("base", "bl_0", "bl_1", "blt_1", "blt_0")
face("base", "bl_1", "bl_2", "blt_2", "blt_1")
face("base", "bl_2", "bl_3", "blt_3", "blt_2")
face("base", "bl_3", "bl_0", "blt_0", "blt_3")
face("base", "blt_0", "blt_1", "blt_2", "blt_3")

for i in range(n):
    j = (i + 1) % n
    face("orange", f"bot_{i}", f"bot_{j}", f"b1a_{j}", f"b1a_{i}")
    face("white", f"b1a_{i}", f"b1a_{j}", f"b1b_{j}", f"b1b_{i}")
    face("orange", f"b1b_{i}", f"b1b_{j}", f"b2a_{j}", f"b2a_{i}")
    face("white", f"b2a_{i}", f"b2a_{j}", f"b2b_{j}", f"b2b_{i}")
    face("orange", f"b2b_{i}", f"b2b_{j}", "tip")

obj_path = os.path.join(out_dir, "traffic_cone.obj")
with open(obj_path, "w", newline="\n") as f:
    f.write("\n".join(obj) + "\n")

mtl = """# Dead Air traffic cone materials
newmtl orange
Ka 1 1 1
Kd 1 1 1
Ks 0 0 0
map_Kd #orange

newmtl white
Ka 1 1 1
Kd 1 1 1
Ks 0 0 0
map_Kd #white

newmtl base
Ka 1 1 1
Kd 1 1 1
Ks 0 0 0
map_Kd #base
"""
with open(os.path.join(out_dir, "traffic_cone.mtl"), "w", newline="\n") as f:
    f.write(mtl)

model_json = """{
  "loader": "forge:obj",
  "flip_v": true,
  "model": "dead_air:models/block/traffic_cone.obj",
  "textures": {
    "particle": "dead_air:block/traffic_cone_orange",
    "orange": "dead_air:block/traffic_cone_orange",
    "white": "dead_air:block/traffic_cone_white",
    "base": "dead_air:block/traffic_cone_base"
  }
}
"""
with open(os.path.join(out_dir, "traffic_cone.json"), "w", newline="\n") as f:
    f.write(model_json)

print("Wrote", obj_path, "verts", len(verts))
