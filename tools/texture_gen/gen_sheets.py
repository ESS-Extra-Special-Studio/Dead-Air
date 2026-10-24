"""Review sheets written to the Desktop Dead Air docs folder."""
import os

from PIL import Image, ImageDraw

from tg_common import DOCS, TEX, checker, upscale

ICONS = ["signal_decoder", "relay_log", "signal_breaker", "component_distortion_core",
         "component_broadcaster_coil", "component_maw_fang", "component_scrambler_chip",
         "component_echo_crystal"]
BLOCKS = ["broken_relay_side", "broken_relay_top", "broken_relay_front", "broken_relay_front_off"]


def icons_sheet():
    entries = [("item", n) for n in ICONS] + [("block", n) for n in BLOCKS]
    cols, cell, pad, label = 4, 128, 16, 14
    rows = (len(entries) + cols - 1) // cols
    W = cols * (cell + pad) + pad
    H = rows * (cell + pad + label) + pad
    sheet = Image.new("RGBA", (W, H), (24, 24, 28, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (kind, name) in enumerate(entries):
        cx = pad + (i % cols) * (cell + pad)
        cy = pad + (i // cols) * (cell + pad + label)
        tile = checker(cell, cell, 16)
        img = Image.open(os.path.join(TEX, kind, name + ".png")).convert("RGBA")
        tile.alpha_composite(upscale(img, cell // img.width))
        sheet.alpha_composite(tile, (cx, cy))
        draw.text((cx, cy + cell + 1), name, fill=(220, 220, 230, 255))
    out = os.path.join(DOCS, "icons_sheet.png")
    sheet.save(out)
    print("wrote", out)


def anchor_render():
    tex = Image.open(os.path.join(TEX, "entity", "signal_anchor.png")).convert("RGBA")
    glow = Image.open(os.path.join(TEX, "entity", "signal_anchor_glowmask.png")).convert("RGBA")
    k = 4
    w = tex.width * k
    sheet = Image.new("RGBA", (w * 2 + 48, w + 40), (24, 24, 28, 255))
    for i, (img, title) in enumerate(((tex, "signal_anchor.png"), (glow, "signal_anchor_glowmask.png"))):
        bg = checker(w, w, 16)
        bg.alpha_composite(upscale(img, k))
        x = 16 + i * (w + 16)
        sheet.alpha_composite(bg, (x, 16))
        ImageDraw.Draw(sheet).text((x, w + 20), title + " (4x)", fill=(220, 220, 230, 255))
    out = os.path.join(DOCS, "signal_anchor_texture_4x.png")
    sheet.save(out)
    print("wrote", out)


def main():
    os.makedirs(DOCS, exist_ok=True)
    icons_sheet()
    if os.path.exists(os.path.join(TEX, "entity", "signal_anchor.png")):
        anchor_render()


if __name__ == "__main__":
    main()
