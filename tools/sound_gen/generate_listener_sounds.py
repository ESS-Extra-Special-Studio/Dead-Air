"""Procedural sound generator for Dead Air: The Listener + broadcast cues.

Everything is synthesized from scratch with numpy (no external samples).
Run from anywhere:  python tools/sound_gen/generate_listener_sounds.py
Writes temp WAVs to tools/sound_gen/out_wav, OGGs to assets/dead_air/sounds/{listener,broadcast},
and merges the sound events into assets/dead_air/sounds.json (existing entries are kept).
"""
import json
import math
import subprocess
import wave
from pathlib import Path

import numpy as np

SR = 44100
ROOT = Path(__file__).resolve().parents[2]
WAV_DIR = Path(__file__).resolve().parent / "out_wav"
SOUNDS = ROOT / "src" / "main" / "resources" / "assets" / "dead_air" / "sounds"
SOUNDS_JSON = ROOT / "src" / "main" / "resources" / "assets" / "dead_air" / "sounds.json"

rng = np.random.default_rng(1337)


# ---------------------------------------------------------------- primitives

def n_of(sec):
    return int(round(sec * SR))


def tvec(sec):
    return np.arange(n_of(sec)) / SR


def noise(sec):
    return rng.standard_normal(n_of(sec))


def fft_band(x, lo=None, hi=None, order=2):
    """Zero-phase Butterworth-shaped band/low/high-pass via FFT."""
    n = len(x)
    pad = 1 << int(math.ceil(math.log2(n + SR // 10)))
    X = np.fft.rfft(x, pad)
    f = np.fft.rfftfreq(pad, 1 / SR)
    g = np.ones_like(f)
    if hi is not None:
        g *= 1 / np.sqrt(1 + (f / hi) ** (2 * order))
    if lo is not None:
        with np.errstate(divide="ignore"):
            g *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-6)) ** (2 * order))
    return np.fft.irfft(X * g, pad)[:n]


def svf(x, fc, q=4.0, mode="band"):
    """Time-varying TPT state-variable filter. fc may be scalar or per-sample array."""
    n = len(x)
    fc = np.broadcast_to(np.asarray(fc, dtype=float), (n,))
    g = np.tan(np.pi * np.clip(fc, 20, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1 / (1 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    out = np.empty(n)
    ic1 = ic2 = 0.0
    xs = x.tolist()
    a1l, a2l, a3l = a1.tolist(), a2.tolist(), a3.tolist()
    want = {"band": 0, "low": 1, "high": 2}[mode]
    for i in range(n):
        v3 = xs[i] - ic2
        v1 = a1l[i] * ic1 + a2l[i] * v3
        v2 = ic2 + a2l[i] * ic1 + a3l[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        if want == 0:
            out[i] = v1
        elif want == 1:
            out[i] = v2
        else:
            out[i] = xs[i] - k * v1 - v2
    return out


def osc(freq, sec=None, n=None, phase=0.0):
    """Sine with scalar or per-sample frequency."""
    if n is None:
        n = n_of(sec)
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = phase + 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph)


def env_adsr(n, a, d=0.0, s=1.0, r=0.0, curve=2.0):
    e = np.full(n, s, dtype=float)
    na, nd, nr = n_of(a), n_of(d), n_of(r)
    na = min(na, n)
    if na:
        e[:na] = np.linspace(0, 1, na) ** curve
    if nd and na + nd <= n:
        e[na:na + nd] = 1 - (1 - s) * np.linspace(0, 1, nd) ** (1 / curve)
    if nr:
        nr = min(nr, n)
        e[-nr:] *= np.linspace(1, 0, nr) ** curve
    return e


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def bell(sec, f0, decay=1.5, partials=None, amp=1.0, strike=True):
    """Inharmonic metallic bell (broken church/antenna bell)."""
    if partials is None:
        partials = [(0.50, 1.0, 1.0), (1.0, 0.8, 0.9), (1.19, 0.6, 0.7), (1.56, 0.5, 0.6),
                    (2.0, 0.45, 0.5), (2.51, 0.35, 0.4), (2.74, 0.3, 0.35), (3.76, 0.22, 0.25),
                    (4.07, 0.18, 0.2), (5.43, 0.12, 0.15)]
    n = n_of(sec)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for ratio, a, dmul in partials:
        f = f0 * ratio * (1 + rng.uniform(-0.004, 0.004))
        if f > SR * 0.45:
            continue
        beat = 1 + 0.15 * np.sin(2 * np.pi * rng.uniform(0.5, 3) * t)
        y += a * beat * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / (decay * dmul))
    if strike:
        k = n_of(0.006)
        y[:k] += fft_band(rng.standard_normal(k), lo=2000) * np.linspace(1, 0, k) * 0.6
    return y * amp


def crackle(sec, rate=30.0, amp=1.0, bright=3000):
    """Sparse random clicks/pops like a dirty radio."""
    n = n_of(sec)
    y = np.zeros(n)
    count = rng.poisson(rate * sec)
    for _ in range(count):
        p = rng.integers(0, n)
        ln = rng.integers(20, 400)
        e = min(n, p + ln)
        seg = rng.standard_normal(e - p) * np.exp(-np.arange(e - p) / (ln / 4))
        y[p:e] += seg * rng.uniform(0.2, 1.0) * (1 if rng.random() > 0.5 else -1)
    return fft_band(y, lo=bright * 0.3) * amp


def bitcrush(x, bits=6, hold=4):
    y = np.repeat(x[::hold], hold)[:len(x)]
    q = 2 ** (bits - 1)
    m = np.max(np.abs(y)) + 1e-9
    return np.round(y / m * q) / q * m


def ringmod(x, f):
    return x * osc(f, n=len(x))


def drive(x, amount=3.0):
    m = np.max(np.abs(x)) + 1e-9
    return np.tanh(x / m * amount) / np.tanh(amount)


def wobble(x, depth_ms=3.0, rate=0.7, jitter=0.4):
    """Tape wow/flutter via modulated delay."""
    n = len(x)
    t = np.arange(n) / SR
    lfo = np.sin(2 * np.pi * rate * t) + jitter * np.sin(2 * np.pi * rate * 3.7 * t + 1.3)
    d = (depth_ms / 1000 * SR) * (1 + lfo) / 2
    idx = np.clip(np.arange(n) - d, 0, n - 1)
    return np.interp(idx, np.arange(n), x)


def dropouts(x, rate=1.5, min_len=0.03, max_len=0.15, floor=0.05):
    n = len(x)
    g = np.ones(n)
    for _ in range(rng.poisson(rate * n / SR)):
        p = rng.integers(0, n)
        ln = n_of(rng.uniform(min_len, max_len))
        e = min(n, p + ln)
        g[p:e] = floor
    g = fft_band(g, hi=150, order=1)
    return x * np.clip(g, 0, 1)


def radio(x, lo=300, hi=3000):
    return fft_band(x, lo=lo, hi=hi, order=3)


def pad_to(x, n):
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def mix(n, *parts):
    y = np.zeros(n)
    for p in parts:
        y += pad_to(p, n)
    return y


def reverb(x, sec=1.2, wet=0.3, bright=4000):
    """Cheap diffuse tail: convolve with decaying filtered noise."""
    ir = fft_band(rng.standard_normal(n_of(sec)), hi=bright) * exp_decay(n_of(sec), sec / 5)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    pad = 1 << int(math.ceil(math.log2(len(x) + len(ir))))
    wetsig = np.fft.irfft(np.fft.rfft(x, pad) * np.fft.rfft(ir, pad), pad)[:len(x)]
    return x * (1 - wet) + wetsig * wet


def finish(x, fade_in=0.004, fade_out=0.02, peak_db=-1.0):
    x = x - np.mean(x)
    n = len(x)
    fi, fo = n_of(fade_in), n_of(fade_out)
    if fi:
        x[:fi] *= np.linspace(0, 1, fi)
    if fo:
        x[-fo:] *= np.linspace(1, 0, fo) ** 2
    return normalize(x, peak_db)


def normalize(x, peak_db=-1.0):
    m = np.max(np.abs(x)) + 1e-12
    return x / m * (10 ** (peak_db / 20))


def make_loop(fn, loop_sec, xfade_sec):
    """Render loop_sec + xfade, then crossfade the overhang into the start (equal power)."""
    y = fn(loop_sec + xfade_sec)
    L, X = n_of(loop_sec), n_of(xfade_sec)
    out = y[:L].copy()
    w = np.linspace(0, np.pi / 2, X)
    out[:X] = y[:X] * np.sin(w) + y[L:L + X] * np.cos(w)
    out -= np.mean(out)
    return normalize(out)


# ---------------------------------------------------------------- listener sounds

def static_bed(sec, lo=400, hi=4000):
    return radio(noise(sec), lo, hi)


def s_ambient(variant):
    sec = [3.2, 2.8, 3.6][variant]
    n = n_of(sec)
    t = np.arange(n) / SR
    # breathing: two swells (inhale/exhale) of filtered static, center freq follows breath
    br = np.abs(np.sin(np.pi * t / sec * [2, 2, 3][variant])) ** 1.5
    fc = 500 + 1600 * br
    s = svf(noise(sec), fc, q=2.5) * br * 0.9
    s += svf(noise(sec), fc * 2.3, q=6) * br * 0.25
    # low detuned throat
    low = (osc(55 * [1, 0.94, 1.06][variant], n=n) + osc(55.7 * [1, 0.94, 1.06][variant], n=n)) * 0.12 * (0.4 + 0.6 * br)
    # faint bell partials
    b = np.zeros(n)
    for k in range(2):
        start = rng.uniform(0.3, sec - 1.5)
        bl = bell(sec - start, rng.uniform(380, 720), decay=1.2, amp=0.12, strike=False)
        b[n_of(start):] += ringmod(bl, rng.uniform(20, 60)) * 0.6 + bl * 0.4
    y = s + low + b + crackle(sec, rate=6, amp=0.15)
    y = wobble(bitcrush(y, bits=9, hold=2), depth_ms=4, rate=0.4)
    return finish(reverb(y, 1.4, 0.35), fade_in=0.15, fade_out=0.3)


def s_hurt(variant):
    sec = [0.62, 0.72][variant]
    n = n_of(sec)
    burst = fft_band(noise(sec), lo=800, hi=7000) * exp_decay(n, 0.07)
    burst = bitcrush(drive(burst, 4), bits=5, hold=3)
    f0 = [210, 170][variant]
    clang = bell(sec, f0, decay=0.35, amp=1.0)
    # pitch-down: resample clang over time
    idx = np.cumsum(np.linspace(1.0, 0.7, n))
    clang = np.interp(idx, np.arange(n), clang, right=0)
    clang = ringmod(clang, 90) * 0.5 + clang * 0.5
    thump = osc(np.linspace(110, 45, n), n=n) * exp_decay(n, 0.08) * 0.8
    y = burst * 0.8 + clang * 0.9 + thump + crackle(sec, 25, 0.3)
    return finish(reverb(y, 0.6, 0.2), fade_out=0.08)


def s_deflect():
    sec = 0.4
    n = n_of(sec)
    ping = bell(sec, 1450, decay=0.18, partials=[(1, 1, 1), (2.76, 0.5, 0.6), (5.4, 0.3, 0.4), (8.9, 0.15, 0.3)])
    ping += osc(2900, n=n) * exp_decay(n, 0.05) * 0.3
    crack = crackle(sec, rate=90, amp=0.6, bright=4000) * env_adsr(n, 0.01, 0.0, 1.0, 0.3)
    hiss = fft_band(noise(sec), lo=3000) * exp_decay(n, 0.03) * 0.4
    y = ping + crack + hiss
    return finish(y, fade_in=0.001, fade_out=0.05)


def s_death():
    sec = 3.8
    n = n_of(sec)
    t = np.arange(n) / SR
    collapse = np.clip(1 - t / 2.6, 0, 1)
    st = svf(noise(sec), 200 + 3500 * collapse ** 2, q=1.5) * (0.2 + collapse) * collapse ** 0.5
    st = bitcrush(st, bits=6, hold=1 + 0)
    carrier_f = 1800 * np.exp(-t * 1.6) + 40
    carrier = osc(carrier_f, n=n) * collapse ** 0.7 * 0.5
    carrier += osc(carrier_f * 1.01 + 3, n=n) * collapse ** 0.7 * 0.3
    bells = np.zeros(n)
    for start, f in [(0.0, 330), (0.25, 247), (0.6, 196), (1.1, 147)]:
        bl = bell(sec - start, f, decay=1.4, amp=0.5)
        bells[n_of(start):] += bl
    bells = wobble(bells, depth_ms=6, rate=0.3)
    sub = osc(np.linspace(60, 28, n), n=n) * exp_decay(n, 0.9) * 0.6
    y = st + carrier + bells + sub + crackle(sec, 20, 0.3) * collapse
    y = reverb(y, 2.0, 0.35)
    y *= np.clip((sec - t) / 0.8, 0, 1) ** 2  # ends in silence
    return finish(y, fade_in=0.002, fade_out=0.1)


def s_scream():
    sec = 2.0
    n = n_of(sec)
    t = np.arange(n) / SR
    build = 1.0
    # feedback howl: rising tone with harmonics + beating partner
    f = np.where(t < build, 700 * (2600 / 700) ** (t / build) ** 1.4, 2600 + 120 * np.sin(2 * np.pi * 7 * t))
    howl = osc(f, n=n) + 0.5 * osc(f * 2.01, n=n) + 0.3 * osc(f * 1.5, n=n) + 0.4 * osc(f * 0.995, n=n)
    amp_build = np.where(t < build, (t / build) ** 3, 1.0)
    howl *= amp_build
    # violent blast after build
    blast_env = np.where(t >= build, 1.0, 0.0) * np.clip(1 - (t - build) / 1.0, 0, 1) ** 0.6
    blast = fft_band(noise(sec), lo=150, hi=9000) * blast_env
    roar = (osc(70, n=n) + osc(73.5, n=n) + osc(140, n=n) * 0.5) * blast_env
    y = howl * 0.6 + blast * 1.2 + roar * 0.8 + crackle(sec, 60, 0.6) * blast_env
    y = ringmod(y, 31) * 0.35 + y * 0.65
    y = drive(y, 6.0)
    y = bitcrush(y, bits=6, hold=2) * np.where(t >= build, 1.0, 0.0) + y * np.where(t >= build, 0.0, 1.0)
    return finish(y, fade_in=0.01, fade_out=0.18)


def s_step(variant):
    sec = [0.42, 0.38][variant]
    n = n_of(sec)
    thud = osc(np.linspace([70, 62][variant], 32, n), n=n) * exp_decay(n, 0.07)
    thud += fft_band(noise(sec), hi=300) * exp_decay(n, 0.04) * 1.2
    rattle = np.zeros(n)
    for _ in range(7):
        st = rng.uniform(0.02, 0.18)
        r = bell(sec - st, rng.uniform(1800, 4200), decay=0.05,
                 partials=[(1, 1, 1), (2.4, 0.5, 0.7), (4.1, 0.3, 0.5)], amp=rng.uniform(0.1, 0.25))
        rattle[n_of(st):] += r
    y = thud * 1.2 + rattle + crackle(sec, 20, 0.15)
    return finish(y, fade_in=0.001, fade_out=0.05)


def s_jump_out():
    sec = 0.6
    n = n_of(sec)
    t = np.arange(n) / SR
    # reverse swell: build of static that sucks inward, ends abruptly
    sw = (t / sec) ** 3
    fc = 6000 * (1 - t / sec) + 300
    y = svf(noise(sec), fc, q=1.8) * sw
    y += osc(400 + 1500 * (t / sec) ** 2, n=n) * sw * 0.3
    rev_bell = bell(sec, 520, decay=0.25, amp=0.5)[::-1]
    y += rev_bell
    y = bitcrush(y, bits=7, hold=2)
    return finish(y, fade_in=0.01, fade_out=0.008)


def s_jump_in():
    sec = 0.6
    n = n_of(sec)
    t = np.arange(n) / SR
    crack = fft_band(noise(sec), lo=500) * exp_decay(n, 0.015) * 2
    burst = svf(noise(sec), 300 + 5000 * np.exp(-t * 6), q=1.5) * exp_decay(n, 0.15)
    boom = osc(np.linspace(90, 35, n), n=n) * exp_decay(n, 0.12)
    y = drive(crack + burst + boom * 0.8 + crackle(sec, 40, 0.4), 3)
    return finish(reverb(y, 0.8, 0.25), fade_in=0.001, fade_out=0.08)


def s_silence():
    sec = 3.0
    n = n_of(sec)
    t = np.arange(n) / SR
    drop = np.where(t < 0.25, 1 - t / 0.25, 0.0) ** 2
    rest = fft_band(noise(sec), lo=300, hi=4000) * drop  # everything cut off
    hum = (osc(48, n=n) + 0.5 * osc(96.3, n=n)) * 0.06 * env_adsr(n, 0.3, 0, 1, 0.4)
    listen_env = np.sin(np.pi * np.clip((t - 0.4) / 2.5, 0, 1)) ** 2
    listen = osc(3520 + 6 * np.sin(2 * np.pi * 0.3 * t), n=n) * listen_env * 0.05
    y = rest + hum + listen
    # finish() normalizes; keep the drop the loudest moment so the tail stays near-silent
    return finish(y, fade_in=0.002, fade_out=0.2)


def s_pulse():
    sec = 0.7
    n = n_of(sec)
    t = np.arange(n) / SR
    whoosh_env = np.sin(np.pi * np.clip(t / 0.55, 0, 1)) ** 2
    whoosh = svf(noise(sec), 400 + 3000 * whoosh_env, q=3) * whoosh_env
    zap_env = np.exp(-np.clip(t - 0.45, 0, None) / 0.06) * (t > 0.45)
    zap = ringmod(osc(np.linspace(2400, 300, n), n=n), 170) * zap_env
    y = whoosh + zap * 0.7 + crackle(sec, 40, 0.3) * whoosh_env
    return finish(bitcrush(y, 8, 2), fade_in=0.01, fade_out=0.06)


def s_claw():
    sec = 0.5
    n = n_of(sec)
    t = np.arange(n) / SR
    e = np.sin(np.pi * np.clip(t / 0.32, 0, 1)) ** 1.5 * (t < 0.32)
    fc = 600 + 5000 * (t / 0.32).clip(0, 1)
    sw = svf(noise(sec), fc, q=2) * e
    crk = crackle(sec, 120, 0.5, bright=3500) * e
    y = sw + crk
    return finish(y, fade_in=0.005, fade_out=0.12)


def r_drone(sec):
    n = n_of(sec)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for f, a in [(41.2, 0.5), (41.9, 0.45), (61.8, 0.25), (82.6, 0.2), (123.0, 0.08)]:
        y += a * osc(f + 0.3 * np.sin(2 * np.pi * 0.05 * t + f), n=n)
    bed_fc = 900 + 600 * np.sin(2 * np.pi * 0.09 * t) + 300 * np.sin(2 * np.pi * 0.23 * t)
    bed = svf(noise(sec), bed_fc, q=1.5) * (0.18 + 0.08 * np.sin(2 * np.pi * 0.13 * t))
    whine = osc(1310 + 15 * np.sin(2 * np.pi * 0.07 * t), n=n) * 0.015 * (1 + np.sin(2 * np.pi * 0.11 * t))
    y = y + bed + whine + crackle(sec, 4, 0.08)
    return wobble(y, depth_ms=5, rate=0.2)


def r_anchor(sec):
    n = n_of(sec)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for h in range(1, 12):
        y += osc(120 * h, n=n) / h ** 0.9 * (1 if h % 2 else 0.6)
    y = drive(y, 2.5) * (0.85 + 0.15 * np.sin(2 * np.pi * 2 * t))
    y = fft_band(y, lo=80, hi=3500)
    return y + crackle(sec, 10, 0.25)


def s_anchor_break():
    sec = 1.0
    n = n_of(sec)
    t = np.arange(n) / SR
    shatter = fft_band(noise(sec), lo=2500) * exp_decay(n, 0.12)
    shards = np.zeros(n)
    for _ in range(14):
        st = rng.uniform(0, 0.5)
        shards[n_of(st):] += bell(sec - st, rng.uniform(2500, 7000), decay=0.06,
                                  partials=[(1, 1, 1), (2.3, 0.6, 0.6), (3.9, 0.4, 0.5)],
                                  amp=rng.uniform(0.1, 0.4))
    zap = osc(1500 * np.exp(-t * 4) + 60, n=n) * exp_decay(n, 0.35)
    zap = drive(ringmod(zap, 220) + zap, 2) * 0.6
    boom = osc(np.linspace(80, 30, n), n=n) * exp_decay(n, 0.1)
    y = shatter + shards + zap + boom + crackle(sec, 50, 0.4) * exp_decay(n, 0.3)
    return finish(reverb(y, 0.9, 0.25), fade_in=0.001, fade_out=0.15)


# ---------------------------------------------------------------- broadcast

def s_alert():
    sec = 3.0
    n = n_of(sec)
    t = np.arange(n) / SR
    tone = osc(853, n=n) + osc(960, n=n)
    tone = wobble(tone, depth_ms=1.5, rate=3.0)
    tone = drive(tone, 2.5)
    tone = radio(tone, 500, 2400)
    tone *= 0.75 + 0.25 * np.sin(2 * np.pi * 1.3 * t)  # fading AM
    st = static_bed(sec, 300, 3500) * 0.35
    y = dropouts(tone, rate=2.0, floor=0.0) + st + crackle(sec, 25, 0.4)
    y = bitcrush(y, bits=8, hold=3)
    return finish(y, fade_in=0.01, fade_out=0.05)


VOWELS = [(730, 1090, 2440), (530, 1840, 2480), (270, 2290, 3010), (570, 840, 2410),
          (300, 870, 2240), (660, 1720, 2410), (440, 1020, 2240), (490, 1350, 1690)]


def s_voice(variant):
    sec = [2.6, 3.2, 2.2, 3.7][variant]
    n = n_of(sec)
    t = np.arange(n) / SR
    syl_rate = [4.5, 5.2, 5.8, 4.2][variant]
    # build syllable timeline: vowel targets + amplitude envelope + pauses
    f1 = np.zeros(n); f2 = np.zeros(n); f3 = np.zeros(n); amp = np.zeros(n)
    pos = 0
    while pos < n:
        ln = n_of(rng.uniform(0.7, 1.3) / syl_rate)
        e = min(n, pos + ln)
        if rng.random() < 0.15:
            pos = e + n_of(rng.uniform(0.05, 0.2))
            continue
        v = VOWELS[rng.integers(len(VOWELS))]
        f1[pos:e], f2[pos:e], f3[pos:e] = v
        k = e - pos
        amp[pos:e] = np.sin(np.pi * np.linspace(0, 1, k)) ** 0.8 * rng.uniform(0.6, 1.0)
        pos = e
    smooth = lambda a: fft_band(a, hi=12, order=1)
    for arr in (f1, f2, f3):
        arr[arr == 0] = 500
    f1, f2, f3 = smooth(f1), smooth(f2), smooth(f3)
    # glottal source: jittery sawtooth-ish pulse train with intonation
    f0 = [120, 145, 105, 160][variant] * (1 + 0.12 * np.sin(2 * np.pi * 0.7 * t + variant)) \
        * (1 + 0.01 * rng.standard_normal(n))
    ph = np.cumsum(f0) / SR
    src = (2 * (ph % 1.0) - 1) ** 3 + 0.08 * rng.standard_normal(n)
    v = svf(src, f1, q=8) * 1.0 + svf(src, f2, q=10) * 0.6 + svf(src, f3, q=12) * 0.3
    # fricative hiss on some syllable edges
    fric = fft_band(noise(sec), lo=2500) * np.clip(np.abs(np.gradient(amp)) * SR / 40, 0, 1) * 0.3
    voice = (v + fric) * smooth(amp)
    voice = wobble(voice, depth_ms=2, rate=1.1)
    voice = drive(radio(voice, 300, 3000), 3.0)
    voice = ringmod(voice, [12, 0, 18, 0][variant] + 1e-3) * 0.25 + voice * 0.75 if variant in (0, 2) else voice
    voice = dropouts(voice, rate=1.4, floor=0.0)
    y = voice + static_bed(sec, 300, 3500) * 0.25 + crackle(sec, 30, 0.35)
    y = radio(bitcrush(y, bits=9, hold=2), 280, 3200)
    return finish(y, fade_in=0.03, fade_out=0.08)


def s_decoded():
    sec = 1.2
    n = n_of(sec)
    y = np.zeros(n)
    for i, f in enumerate([660, 880, 1320]):
        st = n_of(i * 0.18)
        seg = n - st
        tone = osc(f, n=seg) + 0.3 * osc(f * 2, n=seg) + 0.1 * osc(f * 3, n=seg)
        y[st:] += tone * exp_decay(seg, 0.35) * env_adsr(seg, 0.005)
    y = radio(y, 350, 3200) + static_bed(sec, 300, 3500) * 0.03
    return finish(reverb(y, 0.8, 0.2), fade_in=0.002, fade_out=0.15)


def s_relay_ping():
    sec = 0.8
    n = n_of(sec)
    t = np.arange(n) / SR
    ping = osc(1180 * (1 + 0.02 * np.exp(-t * 20)), n=n) * exp_decay(n, 0.18)
    ping += osc(2360, n=n) * exp_decay(n, 0.07) * 0.2
    echo = np.zeros(n)
    d = n_of(0.22)
    echo[d:] = ping[:-d] * 0.35
    y = radio(ping + echo, 400, 3000) + crackle(sec, 25, 0.25) + static_bed(sec, 400, 3500) * 0.05
    return finish(reverb(y, 1.0, 0.3), fade_in=0.002, fade_out=0.1)


# ---------------------------------------------------------------- output

def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def to_ogg(wav, ogg):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", "5", str(ogg)], check=True)


JOBS = {
    "listener/ambient1": lambda: s_ambient(0),
    "listener/ambient2": lambda: s_ambient(1),
    "listener/ambient3": lambda: s_ambient(2),
    "listener/hurt1": lambda: s_hurt(0),
    "listener/hurt2": lambda: s_hurt(1),
    "listener/deflect": s_deflect,
    "listener/death": s_death,
    "listener/scream": s_scream,
    "listener/step1": lambda: s_step(0),
    "listener/step2": lambda: s_step(1),
    "listener/jump_out": s_jump_out,
    "listener/jump_in": s_jump_in,
    "listener/silence": s_silence,
    "listener/pulse": s_pulse,
    "listener/claw": s_claw,
    "listener/drone": lambda: make_loop(r_drone, 11.0, 1.5),
    "listener/anchor_hum": lambda: make_loop(r_anchor, 3.0, 0.4),
    "listener/anchor_break": s_anchor_break,
    "broadcast/alert": s_alert,
    "broadcast/voice1": lambda: s_voice(0),
    "broadcast/voice2": lambda: s_voice(1),
    "broadcast/voice3": lambda: s_voice(2),
    "broadcast/voice4": lambda: s_voice(3),
    "broadcast/decoded": s_decoded,
    "broadcast/relay_ping": s_relay_ping,
}

EVENTS = [
    ("listener.ambient", "hostile", ["listener/ambient1", "listener/ambient2", "listener/ambient3"], False),
    ("listener.hurt", "hostile", ["listener/hurt1", "listener/hurt2"], False),
    ("listener.deflect", "hostile", ["listener/deflect"], False),
    ("listener.death", "hostile", ["listener/death"], False),
    ("listener.scream", "hostile", ["listener/scream"], False),
    ("listener.step", "hostile", ["listener/step1", "listener/step2"], False),
    ("listener.jump_out", "hostile", ["listener/jump_out"], False),
    ("listener.jump_in", "hostile", ["listener/jump_in"], False),
    ("listener.silence", "hostile", ["listener/silence"], False),
    ("listener.pulse", "hostile", ["listener/pulse"], False),
    ("listener.claw", "hostile", ["listener/claw"], False),
    ("listener.drone", "ambient", ["listener/drone"], True),
    ("anchor.hum", "hostile", ["listener/anchor_hum"], False),
    ("anchor.break", "hostile", ["listener/anchor_break"], False),
    ("broadcast.alert", "record", ["broadcast/alert"], False),
    ("broadcast.voice", "record", ["broadcast/voice1", "broadcast/voice2", "broadcast/voice3", "broadcast/voice4"], False),
    ("broadcast.decoded", "record", ["broadcast/decoded"], False),
    ("relay.ping", "block", ["broadcast/relay_ping"], False),
]


def update_sounds_json():
    data = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    for key, cat, names, stream in EVENTS:
        sounds = [{"name": f"dead_air:{nm}", "stream": True} if stream else f"dead_air:{nm}" for nm in names]
        data[key] = {"category": cat, "subtitle": f"subtitles.dead_air.{key}", "sounds": sounds}
    SOUNDS_JSON.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    WAV_DIR.mkdir(parents=True, exist_ok=True)
    for name, fn in JOBS.items():
        x = fn()
        wav = WAV_DIR / (name.replace("/", "_") + ".wav")
        ogg = SOUNDS / (name + ".ogg")
        ogg.parent.mkdir(parents=True, exist_ok=True)
        write_wav(wav, x)
        to_ogg(wav, ogg)
        print(f"{name:28s} {len(x) / SR:5.2f}s  {ogg.stat().st_size / 1024:6.1f} KB")
    update_sounds_json()
    print("sounds.json updated")


if __name__ == "__main__":
    main()
