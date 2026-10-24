"""Survivor NPC skins (64x64, modern player layout) + flat preview sheet."""
import os

from PIL import Image, ImageDraw

from tg_common import CYAN, CYAN_LIGHT, DOCS, GUN, TEX, Canvas, box_faces, checker, hexc, paint_box, upscale

OUT = os.path.join(TEX, "entity", "survivor")

# part: (base_u, base_v, overlay_u, overlay_v, w, h, d)
PARTS = {
    "head": (0, 0, 32, 0, 8, 8, 8),
    "body": (16, 16, 16, 32, 8, 12, 4),
    "right_arm": (40, 16, 40, 32, 4, 12, 4),
    "left_arm": (32, 48, 48, 48, 4, 12, 4),
    "right_leg": (0, 16, 0, 32, 4, 12, 4),
    "left_leg": (16, 48, 0, 48, 4, 12, 4),
}

FACE_LIGHT = {"front": 1.0, "top": 1.06, "right": 0.88, "left": 0.88, "back": 0.8, "bottom": 0.7}


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (c[3],)


# FNV-1a: Python salts str hashes per run, this keeps the output stable
def _stable_hash(t):
    s = repr(t).encode()
    v = 2166136261
    for b in s:
        v = ((v ^ b) * 16777619) & 0xFFFFFFFF
    return v


def grain(part, face, x, y):
    return 0.94 + (_stable_hash((part, face, x, y)) & 0xFFFF) / 0xFFFF * 0.1


def side_back_col(face, x, w):
    """True if column x of a side face is the back-facing half."""
    if face == "right":
        return x < w // 2
    if face == "left":
        return x >= w // 2
    return False


class Survivor:
    name = ""
    skin = hexc("C08A63")
    hair = hexc("2A1D14")
    eye = hexc("2B3A4A")
    shirt = hexc("555555")
    sleeve = None
    pants = hexc("333333")
    boots = hexc("1E1A16")
    glove = None

    # --- base layer ---
    def head(self, face, x, y, w, h):
        s = self.skin
        if face == "top":
            return self.hair
        if face == "bottom":
            return shade(s, 0.85)
        if face == "back":
            return self.hair if y <= self.hair_back_rows else s
        if face in ("right", "left"):
            if y <= 1 or (side_back_col(face, x, w) and y <= self.hair_back_rows):
                return self.hair
            if y == 4 and ((face == "right" and x == 5) or (face == "left" and x == 2)):
                return shade(s, 0.8)  # ear
            return s
        # front face
        if y <= 1:
            return self.hair
        if y == 2 and x in (0, 7):
            return self.hair
        if y == 3 and x in (1, 2, 5, 6):
            return shade(self.hair, 1.2)  # brows
        if y == 4:
            if x in (1, 6):
                return hexc("EDEDE6")
            if x in (2, 5):
                return self.eye
        if y == 5 and x in (3, 4):
            return shade(s, 0.86)  # nose
        if y == 6 and x in (3, 4):
            return hexc("7A3E36")
        return s

    def torso(self, face, x, y, w, h):
        if face == "bottom":
            return self.pants
        if y == 11 and face != "top":
            return hexc("2A2018")  # belt
        return self.shirt

    def arm(self, side, face, x, y, w, h):
        sl = self.sleeve or self.shirt
        if face == "top":
            return sl
        hand = self.glove or self.skin
        if face == "bottom":
            return hand
        if y >= 9:
            return hand
        return sl

    def leg(self, side, face, x, y, w, h):
        if face == "top":
            return self.pants
        if face == "bottom":
            return shade(self.boots, 0.7)
        if y >= 9:
            return self.boots if y < 11 else shade(self.boots, 0.75)
        return self.pants

    # --- overlay layer (None = transparent) ---
    def hat(self, face, x, y, w, h):
        return None

    def jacket(self, face, x, y, w, h):
        return None

    def sleeve_ov(self, side, face, x, y, w, h):
        return None

    def pants_ov(self, side, face, x, y, w, h):
        return None

    hair_back_rows = 5

    def paint(self):
        cv = Canvas(64, 64)
        for part, (bu, bv, ou, ov, w, h, d) in PARTS.items():
            def base(face, x, y, fw, fh, part=part):
                if part == "head":
                    c = self.head(face, x, y, fw, fh)
                elif part == "body":
                    c = self.torso(face, x, y, fw, fh)
                elif part.endswith("arm"):
                    c = self.arm(part.split("_")[0], face, x, y, fw, fh)
                else:
                    c = self.leg(part.split("_")[0], face, x, y, fw, fh)
                return shade(c, FACE_LIGHT[face] * grain(part, face, x, y))

            def over(face, x, y, fw, fh, part=part):
                if part == "head":
                    c = self.hat(face, x, y, fw, fh)
                elif part == "body":
                    c = self.jacket(face, x, y, fw, fh)
                elif part.endswith("arm"):
                    c = self.sleeve_ov(part.split("_")[0], face, x, y, fw, fh)
                else:
                    c = self.pants_ov(part.split("_")[0], face, x, y, fw, fh)
                if c is None:
                    return None
                return shade(c, FACE_LIGHT[face] * grain(part + "o", face, x, y))

            paint_box(cv, bu, bv, w, h, d, base)
            paint_box(cv, ou, ov, w, h, d, over)
        return cv


class Scavenger(Survivor):
    name = "scavenger"
    skin = hexc("B98260")
    hair = hexc("3B2A1C")
    eye = hexc("4A3A22")
    shirt = hexc("5A5232")  # olive-brown jacket
    pants = hexc("4E4A3A")
    boots = hexc("2B2116")
    glove = hexc("3A2C1E")
    hood = hexc("5C4127")
    strap = hexc("2E2418")

    def head(self, face, x, y, w, h):
        # gas-mask-like bandana over the lower face
        if face in ("front", "right", "left", "back") and y >= 5:
            cloth = hexc("3E4A2A")
            if face == "front" and y >= 5 and x in (3, 4):
                return hexc("6A6A70") if y == 6 else hexc("2A2A2E")  # filter canister
            if face == "front" and y == 5:
                return shade(cloth, 1.15)
            return cloth
        return super().head(face, x, y, w, h)

    def torso(self, face, x, y, w, h):
        base = super().torso(face, x, y, w, h)
        if face == "front":
            if x in (1, 6) and y <= 10:
                return self.strap
            if x == 6 and y == 4 or x == 1 and y == 4:
                return hexc("8A8A80")  # buckle
            if x in (2, 3) and y in (6, 7):
                return hexc("7A5A34")  # patch
            if x == 4 and 1 <= y <= 10:
                return shade(self.shirt, 0.8)  # zip seam
        if face == "back":
            if 1 <= x <= 6 and 1 <= y <= 9:
                edge = x in (1, 6) or y in (1, 9)
                return hexc("3A3020") if edge else hexc("4A3E28")  # backpack
        if face in ("right", "left") and x in (1, 2) and y <= 9:
            return self.strap
        return base

    def leg(self, side, face, x, y, w, h):
        c = super().leg(side, face, x, y, w, h)
        outer = (side == "right" and face == "right") or (side == "left" and face == "left")
        if outer and 3 <= y <= 6 and 0 < x < 3:
            return hexc("3E3A2C") if y == 3 else hexc("5A5644")  # cargo pocket
        if face == "front" and side == "left" and y in (6, 7) and x in (1, 2):
            return hexc("6A5A3A")  # knee patch
        return c

    def hat(self, face, x, y, w, h):
        hood, inner = self.hood, shade(self.hood, 0.6)
        if face in ("top", "back"):
            if face == "back" and (x, y) in ((2, 3), (3, 3), (2, 4)):
                return hexc("6E5A3A")  # stitched patch
            return hood
        if face in ("right", "left"):
            return hood if y <= 6 else None
        if face == "front":
            if y == 0:
                return hood
            if y == 1:
                return inner
            if x in (0, 7) and y <= 6:
                return inner if y > 1 else hood
        return None

    def jacket(self, face, x, y, w, h):
        if face == "front":
            if x in (2, 3) and y == 6:
                return hexc("8A6A40")  # raised patch stitch line
            if y == 9 and 0 <= x <= 7:
                return shade(self.shirt, 0.85)  # jacket hem
        if face == "back" and 1 <= x <= 6 and y == 1:
            return hexc("2E2418")  # backpack top flap
        return None

    def sleeve_ov(self, side, face, x, y, w, h):
        if y == 8 and face != "top" and face != "bottom":
            return shade(self.shirt, 0.75)  # rolled cuff
        if side == "right" and face in ("front", "right") and y in (3, 4) and x in (1, 2):
            return hexc("6E5530")
        return None


class Medic(Survivor):
    name = "medic"
    skin = hexc("E0B394")
    hair = hexc("1E1814")
    eye = hexc("3A5A6E")
    shirt = hexc("D8D8D4")
    pants = hexc("26262E")
    boots = hexc("15151A")
    hair_back_rows = 3

    def head(self, face, x, y, w, h):
        if face == "front" and y == 2 and x in (0, 7):
            return self.skin  # short hair, no side locks
        return super().head(face, x, y, w, h)

    def torso(self, face, x, y, w, h):
        base = super().torso(face, x, y, w, h)
        grey = hexc("A8A8A6")
        if face == "front":
            if y == 0 and 2 <= x <= 5:
                return hexc("9A9A9A")  # collar
            if y == 0 and x in (3, 4):
                return self.skin
            if x == 4 and 1 <= y <= 10:
                return grey  # zipper
            if x == 4 and y in (2, 5, 8):
                return hexc("7A7A78")
            if y in (6, 7) and x in (1, 2):
                return grey if y == 6 else hexc("C4C4C0")  # pocket
            if y == 3 and x == 6:
                return hexc("C21F2A")  # small badge
        if face == "back" and y == 0:
            return hexc("9A9A9A")
        if y == 11 and face != "top":
            return hexc("3A3A44")
        return base

    def arm(self, side, face, x, y, w, h):
        c = super().arm(side, face, x, y, w, h)
        if side == "left" and face not in ("top", "bottom") and 1 <= y <= 3:
            red = hexc("C21F2A")
            if (x == 1 and y in (1, 2, 3)) or (y == 2 and x in (0, 1, 2)):
                return red
            return hexc("F4F4F0")  # armband
        if face not in ("top", "bottom") and y == 8:
            return hexc("B4B4B0")  # cuff
        return c

    def jacket(self, face, x, y, w, h):
        if face in ("front", "back", "right", "left") and y >= 9:
            if face == "front" and x in (3, 4):
                return None  # open coat front
            return hexc("E6E6E2") if y < 11 else hexc("C4C4C0")  # coat tail
        if face == "front" and y == 0 and x in (1, 2, 5, 6):
            return hexc("BDBDB9")  # lapels
        return None


class RadioOperator(Survivor):
    name = "radio_operator"
    skin = hexc("8D5A3C")
    hair = hexc("16110E")
    eye = hexc("1E1A16")
    shirt = hexc("4F5A2E")
    pants = hexc("A08F62")
    boots = hexc("2A2218")
    hair_back_rows = 4

    def torso(self, face, x, y, w, h):
        base = super().torso(face, x, y, w, h)
        if face == "front":
            if y == 0 and x in (3, 4):
                return self.skin  # open collar
            if y == 0 and x in (2, 5):
                return shade(self.shirt, 0.75)
            if y in (2, 3, 4) and x in (1, 2, 5, 6):
                if y == 2:
                    return shade(self.shirt, 0.72)  # pocket flaps
                return shade(self.shirt, 0.92)
            if x == 4 and y in (3, 6, 9):
                return hexc("2A2A1E")  # buttons
            if x == 3 and 1 <= y <= 10:
                return shade(self.shirt, 0.85)
        if face == "back" and 2 <= x <= 5 and 2 <= y <= 4:
            return shade(self.shirt, 0.88)  # yoke seam
        if y == 11 and face != "top":
            return hexc("3A3222")
        return base

    def leg(self, side, face, x, y, w, h):
        c = super().leg(side, face, x, y, w, h)
        outer = (side == "right" and face == "right") or (side == "left" and face == "left")
        if outer and 3 <= y <= 5 and 1 <= x <= 2:
            return shade(self.pants, 0.8)
        return c

    def hat(self, face, x, y, w, h):
        band, cup, rim = GUN[0], GUN[1], GUN[3]
        if face == "top" and y in (3, 4):
            return band
        if face in ("right", "left"):
            if x in (3, 4) and y <= 2:
                return band
            if 2 <= x <= 5 and 3 <= y <= 6:
                edge = x in (2, 5) or y in (3, 6)
                return rim if edge else cup
        if face == "front" and y == 6 and x == 0:
            return band  # mic boom
        if face == "front" and y == 6 and x == 1:
            return GUN[2]
        return None

    def jacket(self, face, x, y, w, h):
        # cyan radio clipped to the chest
        if face == "front":
            if x == 5 and y == 1:
                return GUN[2]  # antenna
            if 5 <= x <= 6 and 2 <= y <= 5:
                if y == 3 and x == 5:
                    return CYAN
                if y == 3 and x == 6:
                    return CYAN_LIGHT
                return GUN[1] if y < 5 else GUN[0]
        return None


SKINS = [Scavenger(), Medic(), RadioOperator()]


def front_view(img: Image.Image, back=False) -> Image.Image:
    """Flat 16x32 assembled front (or back) view, base + overlay."""
    view = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    face = "back" if back else "front"
    layout = {
        "head": (4, 0),
        "body": (4, 8),
        "right_arm": (12 if back else 0, 8),
        "left_arm": (0 if back else 12, 8),
        "right_leg": (8 if back else 4, 20),
        "left_leg": (4 if back else 8, 20),
    }
    for layer in (0, 1):
        for part, (bu, bv, ou, ov, w, h, d) in PARTS.items():
            u, v = (bu, bv) if layer == 0 else (ou, ov)
            fx, fy, fw, fh = box_faces(u, v, w, h, d)[face]
            crop = img.crop((fx, fy, fx + fw, fy + fh))
            view.alpha_composite(crop, layout[part])
    return view


def preview():
    k = 8
    cell_w, cell_h = 16 * k, 32 * k
    W = len(SKINS) * (cell_w * 2 + 48) + 16
    H = cell_h + 56
    sheet = Image.new("RGBA", (W, H), (24, 24, 28, 255))
    draw = ImageDraw.Draw(sheet)
    for i, s in enumerate(SKINS):
        img = Image.open(os.path.join(OUT, s.name + ".png")).convert("RGBA")
        x0 = 16 + i * (cell_w * 2 + 48)
        for j, back in enumerate((False, True)):
            bg = checker(cell_w, cell_h, 16)
            bg.alpha_composite(upscale(front_view(img, back), k))
            sheet.alpha_composite(bg, (x0 + j * (cell_w + 8), 16))
        draw.text((x0, cell_h + 24), f"{s.name}  (front | back)", fill=(220, 220, 230, 255))
    os.makedirs(DOCS, exist_ok=True)
    out = os.path.join(DOCS, "skin_previews.png")
    sheet.save(out)
    print("wrote", out)


def main():
    for s in SKINS:
        s.paint().save(os.path.join(OUT, s.name + ".png"))
    preview()


if __name__ == "__main__":
    main()
