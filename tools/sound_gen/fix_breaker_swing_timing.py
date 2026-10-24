"""Trim lead-in and pull Signal Breaker swing whoosh earlier to match arm swing."""
import numpy as np
import soundfile as sf
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src/main/resources/assets/dead_air/sounds/breaker/swing.ogg"
NEO = Path(r"C:\Users\Ksivi\IdeaProjects\Dead Air - 1.21.1 NeoForge") / \
      "src/main/resources/assets/dead_air/sounds/breaker/swing.ogg"


def process(src: Path) -> None:
    bak = src.with_suffix(".ogg.bak")
    if not bak.exists():
        bak.write_bytes(src.read_bytes())

    # Always process from backup so re-runs are stable
    data, sr = sf.read(str(bak))
    if data.ndim > 1:
        data = data.mean(axis=1)
    data = data.astype(np.float64)

    absd = np.abs(data)
    win = max(1, int(sr * 0.005))
    env = np.convolve(absd, np.ones(win) / win, mode="same")
    peak_i = int(np.argmax(env))
    thr = env[peak_i] * 0.12
    onset = int(np.argmax(env >= thr))

    preroll = int(sr * 0.02)
    start = max(0, onset - preroll)
    end = min(len(data), peak_i + int(sr * 0.45))
    body = data[start:end]

    peak_rel = peak_i - start
    target_peak = int(sr * 0.09)
    speed = float(np.clip(peak_rel / max(1, target_peak), 1.15, 2.8))

    n_out = int(len(body) / speed)
    idx = np.linspace(0, len(body) - 1, n_out)
    out = np.interp(idx, np.arange(len(body)), body)

    abs_o = np.abs(out)
    env_o = np.convolve(abs_o, np.ones(win) / win, mode="same")
    peak_o = int(np.argmax(env_o))
    max_len = int(sr * 0.42)
    low = env_o < (env_o[peak_o] * 0.08)
    run = 0
    need = int(sr * 0.04)
    cut = min(len(out), max_len)
    for i in range(peak_o, min(len(out), max_len)):
        if low[i]:
            run += 1
            if run >= need:
                cut = i
                break
        else:
            run = 0
    out = out[:cut]

    fade_in = int(sr * 0.004)
    fade_out = int(sr * 0.05)
    if fade_in > 0:
        out[:fade_in] *= np.linspace(0, 1, fade_in)
    if fade_out > 0 and len(out) > fade_out:
        out[-fade_out:] *= np.linspace(1, 0, fade_out)

    peak = np.max(np.abs(out)) or 1.0
    out = out / peak * 0.92

    src.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(src), out.astype(np.float32), sr, format="OGG", subtype="VORBIS")

    data2, sr2 = sf.read(str(src))
    if data2.ndim > 1:
        data2 = data2.mean(axis=1)
    abs2 = np.abs(data2)
    env2 = np.convolve(abs2, np.ones(win) / win, mode="same")
    p2 = int(np.argmax(env2))
    thr2 = env2[p2] * 0.15
    o2 = int(np.argmax(env2 >= thr2))
    print(
        f"{src.name}: speed={speed:.2f}x dur={len(data2)/sr2:.3f}s "
        f"onset={o2/sr2*1000:.0f}ms peak={p2/sr2*1000:.0f}ms "
        f"size={src.stat().st_size} bak={bak.name}"
    )


def main() -> None:
    process(SRC)
    neo_root = Path(r"C:\Users\Ksivi\IdeaProjects\Dead Air - 1.21.1 NeoForge")
    if neo_root.is_dir() and NEO.exists():
        process(NEO)
    elif neo_root.is_dir():
        # Port may not have the file yet — copy processed Forge result
        NEO.parent.mkdir(parents=True, exist_ok=True)
        NEO.write_bytes(SRC.read_bytes())
        print(f"copied to NeoForge: {NEO}")


if __name__ == "__main__":
    main()
