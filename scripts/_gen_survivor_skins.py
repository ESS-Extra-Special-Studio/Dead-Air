from PIL import Image
import colorsys
import os

dir_path = r"C:\Users\Ksivi\IdeaProjects\Dead Air\src\main\resources\assets\dead_air\textures\entity\survivor"
bases = [
    Image.open(os.path.join(dir_path, "scavenger.png")).convert("RGBA"),
    Image.open(os.path.join(dir_path, "medic.png")).convert("RGBA"),
    Image.open(os.path.join(dir_path, "radio_operator.png")).convert("RGBA"),
]


def shift(img, hue_shift, sat_mul, val_mul, tint):
    px = img.load()
    w, h = img.size
    out = Image.new("RGBA", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                op[x, y] = (0, 0, 0, 0)
                continue
            h_, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h_ = (h_ + hue_shift / 360.0) % 1.0
            s = max(0, min(1, s * sat_mul))
            v = max(0, min(1, v * val_mul))
            rr, gg, bb = colorsys.hsv_to_rgb(h_, s, v)
            op[x, y] = (
                int(min(255, max(0, rr * tint[0] * 255))),
                int(min(255, max(0, gg * tint[1] * 255))),
                int(min(255, max(0, bb * tint[2] * 255))),
                a,
            )
    return out


male = [
    (0, 1.0, 1.0, (1.0, 1.0, 1.0)),
    (25, 1.1, 0.95, (1.0, 0.98, 0.9)),
    (55, 0.9, 1.05, (0.95, 1.0, 0.9)),
    (110, 1.0, 0.9, (0.9, 1.0, 0.95)),
    (160, 0.85, 1.0, (0.92, 0.98, 1.0)),
    (200, 1.05, 0.95, (0.9, 0.95, 1.05)),
    (240, 0.9, 1.0, (0.95, 0.92, 1.05)),
    (-30, 1.15, 0.92, (1.05, 0.95, 0.9)),
    (80, 0.7, 1.1, (1.0, 1.0, 0.95)),
    (300, 1.0, 0.88, (1.02, 0.9, 1.0)),
]
female = [
    (15, 1.05, 1.05, (1.05, 0.98, 0.98)),
    (40, 1.1, 1.0, (1.02, 1.0, 0.95)),
    (70, 0.95, 1.08, (0.98, 1.02, 0.95)),
    (130, 1.0, 1.0, (0.95, 1.02, 1.0)),
    (180, 0.9, 1.05, (0.95, 1.0, 1.05)),
    (220, 1.05, 1.0, (0.98, 0.98, 1.05)),
    (270, 0.95, 1.05, (1.0, 0.95, 1.05)),
    (-20, 1.2, 1.0, (1.05, 0.98, 0.95)),
    (95, 0.8, 1.12, (1.0, 1.02, 0.98)),
    (320, 1.1, 0.95, (1.05, 0.95, 1.0)),
]

for i in range(10):
    src = bases[i % 3]
    h, s, v, t = male[i]
    shift(src, h, s, v, t).save(os.path.join(dir_path, f"male_{i}.png"))
    h, s, v, t = female[i]
    shift(src, h, s, v, t).save(os.path.join(dir_path, f"female_{i}.png"))

print("done", sorted(f for f in os.listdir(dir_path) if f.endswith(".png")))
