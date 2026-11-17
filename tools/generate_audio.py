#!/usr/bin/env python3
"""
Generates all Plantagotchi audio (8-bit style) into shared/src/commonMain/composeResources/files.

    python3 tools/generate_audio.py

Everything is synthesized from code (square/triangle/sine waves and filtered noise), deterministic
(fixed seeds), mono 16-bit WAV. Loops are crossfaded end-to-start so they repeat without clicks.
"""
import os
import wave

import numpy as np

SR = 22050
ROOT = os.path.join(os.path.dirname(__file__), "..", "shared", "src", "commonMain", "composeResources", "files")
rng = np.random.default_rng(7)


# region Building blocks

def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def osc(freq, dur, shape="square", duty=0.5):
    """freq may be a number or an array (frequency over time)."""
    n = int(SR * dur)
    f = np.full(n, float(freq)) if np.isscalar(freq) else np.asarray(freq, dtype=float)[:n]
    phase = np.cumsum(f) / SR % 1.0
    if shape == "square":
        return np.where(phase < duty, 1.0, -1.0)
    if shape == "triangle":
        return 4 * np.abs(phase - 0.5) - 1
    if shape == "saw":
        return 2 * phase - 1
    return np.sin(2 * np.pi * phase)


def sweep(f0, f1, dur, curve=1.0):
    x = np.linspace(0, 1, int(SR * dur)) ** curve
    return f0 + (f1 - f0) * x


def env(n, attack=0.005, release=0.05, sustain_level=1.0):
    e = np.full(n, sustain_level)
    a = min(n, int(SR * attack))
    r = min(n - a, int(SR * release))
    if a > 0:
        e[:a] = np.linspace(0, sustain_level, a)
    if r > 0:
        e[n - r:] = np.linspace(sustain_level, 0, r)
    return e


def decay(n, seconds):
    return np.exp(-np.arange(n) / (SR * seconds))


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    """One-pole low-pass."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def brown(dur):
    w = noise(dur)
    b = np.cumsum(w)
    b = highpass(b, 20)
    return b / (np.max(np.abs(b)) + 1e-9)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def place(buffer, sound, at):
    i = int(at * SR)
    end = min(len(buffer), i + len(sound))
    buffer[i:end] += sound[: end - i]


def seq(*parts, gap=0.0):
    out = []
    for p in parts:
        out.append(p)
        if gap:
            out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out)


def note(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def tone(freq, dur, shape="square", vol=0.3, attack=0.004, release=0.04, duty=0.5):
    x = osc(freq, dur, shape, duty)
    return x * env(len(x), attack, release) * vol


def loopable(x, fade=0.4):
    """Crossfades the tail into the head so the sound loops seamlessly."""
    f = int(SR * fade)
    head = x[:f].copy()
    tail = x[-f:]
    ramp = np.linspace(0, 1, f)
    out = x[:-f].copy()
    out[:f] = head * ramp + tail * (1 - ramp)
    return out


def normalize(x, peak=0.85):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def write(folder, name, x, peak=0.85):
    path = os.path.join(ROOT, folder)
    os.makedirs(path, exist_ok=True)
    data = (np.clip(normalize(x, peak), -1, 1) * 32000).astype("<i2")
    with wave.open(os.path.join(path, name + ".wav"), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


# endregion

# region Effects

def effects():
    N = note
    write("sfx", "coin", seq(tone(N(83), 0.06), tone(N(88), 0.12)), 0.7)
    write("sfx", "pet", tone(sweep(330, 660, 0.12), 0.12, "sine", 0.4), 0.7)
    bubbles = np.concatenate([
        np.concatenate([tone(sweep(rng.uniform(500, 900), rng.uniform(900, 1400), 0.04), 0.04, "sine", 0.25), np.zeros(int(SR * 0.02))])
        for _ in range(6)
    ])
    write("sfx", "water", mix(bubbles, lowpass(noise(len(bubbles) / SR + 0.2), 2500) * 0.15 * env(int(SR * (len(bubbles) / SR + 0.2)), 0.05, 0.2)), 0.7)
    write("sfx", "buy", seq(tone(N(84), 0.06), tone(N(88), 0.06), tone(N(91), 0.16)), 0.7)
    write("sfx", "levelup", seq(*(tone(N(m), d, "triangle", 0.45) for m, d in [(72, .08), (76, .08), (79, .08), (84, .25)])), 0.75)
    write("sfx", "reward", seq(tone(N(79), .1), tone(N(84), .1), tone(N(88), .1), np.zeros(int(SR * .03)), tone(N(91), .35)), 0.75)
    write("sfx", "tap", tone(1200, 0.03, "square", 0.2), 0.5)
    write("sfx", "wither", tone(sweep(400, 110, 0.6), 0.6, "triangle", 0.45), 0.7)

    # Fertilizer: dry sprinkle of short noise grains
    grains = np.zeros(int(SR * 0.6))
    for i in range(18):
        g = highpass(noise(0.015), 1500) * decay(int(SR * 0.015), 0.004)
        place(grains, g * rng.uniform(0.4, 1.0), i * 0.03 + rng.uniform(0, 0.01))
    write("sfx", "fertilizer", grains, 0.6)

    # Lamp: switch click and a rising electric hum
    hum = osc(sweep(80, 120, 0.7), 0.7, "saw") * 0.3 * env(int(SR * 0.7), 0.05, 0.3)
    hum += osc(sweep(160, 240, 0.7), 0.7, "square", 0.3) * 0.08 * env(int(SR * 0.7), 0.1, 0.3)
    write("sfx", "lamp", mix(highpass(noise(0.02), 2000) * 0.8, lowpass(hum, 1800)), 0.6)

    # Decor: sparkly arpeggio up
    write("sfx", "decor", seq(*(tone(N(m), 0.06, "triangle", 0.35) for m in [84, 88, 91, 96, 100])), 0.6)
    # Switch plant: short whoosh
    whoosh = lowpass(noise(0.18), 3000) * np.sin(np.linspace(0, np.pi, int(SR * 0.18))) * 0.6
    write("sfx", "switch", mix(whoosh, tone(sweep(500, 800, 0.08), 0.08, "sine", 0.15)), 0.5)
    # Flytrap snap: click and thump
    snap = mix(highpass(noise(0.01), 3000) * 1.0, tone(sweep(180, 60, 0.12), 0.12, "sine", 0.9, 0.001, 0.08))
    write("sfx", "snap", snap, 0.8)
    # Error: two low buzzes
    write("sfx", "error", seq(tone(N(45), 0.09, "square", 0.3, duty=0.25), np.zeros(int(SR * 0.04)), tone(N(44), 0.14, "square", 0.3, duty=0.25)), 0.6)
    # Sheet open / close: soft pops
    write("sfx", "open", tone(sweep(400, 900, 0.07), 0.07, "sine", 0.4), 0.5)
    write("sfx", "close", tone(sweep(900, 400, 0.07), 0.07, "sine", 0.4), 0.5)
    # Thunder: noise burst with a long rumbling tail
    crack = highpass(noise(0.15), 800) * decay(int(SR * 0.15), 0.05)
    rumble = lowpass(brown(3.0), 200) * decay(int(SR * 3.0), 1.0) * (1 + 0.5 * np.sin(np.linspace(0, 20, int(SR * 3.0))))
    write("sfx", "thunder", mix(crack * 0.6, rumble), 0.85)

# endregion

# region Plant voices

def voices():
    def vib(f, dur, depth=0.03, rate=7):
        t = t_axis(dur)
        return f * (1 + depth * np.sin(2 * np.pi * rate * t))

    def bell(f, dur):
        n = int(SR * dur)
        x = osc(f, dur, "sine") + 0.5 * osc(f * 2.76, dur, "sine") + 0.25 * osc(f * 5.4, dur, "sine")
        return x * decay(n, dur / 4) * env(n, 0.002, 0.02)

    v = {}
    v["sprout_happy"] = seq(tone(sweep(900, 1400, 0.07), 0.07, "sine", 0.4), tone(sweep(1100, 1750, 0.09), 0.09, "sine", 0.4), gap=0.03)
    v["sprout_sad"] = tone(sweep(800, 480, 0.4), 0.4, "triangle", 0.35, release=0.15)
    v["cactus_happy"] = seq(tone(180, 0.08, "square", 0.3, duty=0.3), tone(220, 0.1, "square", 0.3, duty=0.3), gap=0.05)
    v["cactus_sad"] = tone(sweep(150, 105, 0.45), 0.45, "square", 0.3, duty=0.3, release=0.2)
    rustle = lambda d, cut: lowpass(highpass(noise(d), 1200), cut) * np.sin(np.linspace(0, np.pi, int(SR * d)))
    v["fern_happy"] = mix(rustle(0.3, 5000), tone(sweep(700, 1000, 0.2), 0.2, "sine", 0.15))
    v["fern_sad"] = mix(rustle(0.45, 2500) * 0.8, tone(sweep(600, 420, 0.4), 0.4, "sine", 0.12))
    v["sunflower_happy"] = seq(tone(vib(note(76), 0.14), 0.14, "triangle", 0.4), tone(vib(note(81), 0.2), 0.2, "triangle", 0.4), gap=0.03)
    v["sunflower_sad"] = tone(sweep(note(69), note(64), 0.45), 0.45, "triangle", 0.35, release=0.2)
    v["tulip_happy"] = mix(bell(note(88), 0.6), np.concatenate([np.zeros(int(SR * 0.12)), bell(note(93), 0.5)]) * 0.8)
    v["tulip_sad"] = bell(note(76), 0.7)
    v["monstera_happy"] = tone(sweep(220, 330, 0.18), 0.18, "sine", 0.6, release=0.08)
    v["monstera_sad"] = tone(sweep(200, 130, 0.4), 0.4, "sine", 0.6, release=0.15)
    tok = lambda f: tone(sweep(f * 1.5, f, 0.04), 0.06, "sine", 0.6, 0.001, 0.05)
    v["bonsai_happy"] = seq(tok(600), tone(vib(note(72), 0.3, 0.015, 5), 0.3, "triangle", 0.35, 0.03, 0.12), gap=0.04)
    v["bonsai_sad"] = tone(vib(sweep(note(67), note(65), 0.5), 0.5, 0.015, 5), 0.5, "triangle", 0.35, 0.05, 0.2)
    chomp = mix(highpass(noise(0.015), 2500), tone(sweep(200, 70, 0.1), 0.1, "sine", 0.8, 0.001, 0.06))
    gurgle = tone(vib(140, 0.25, 0.15, 18), 0.25, "square", 0.2, duty=0.3)
    v["flytrap_happy"] = seq(chomp, gurgle, gap=0.05)
    v["flytrap_sad"] = tone(vib(sweep(110, 80, 0.5), 0.5, 0.1, 12), 0.5, "square", 0.25, duty=0.3, release=0.2)
    for name, x in v.items():
        write("voice", name, x, 0.7)

# endregion

# region Ambience (loops)

def ambience():
    # Rain: soft hiss with droplets
    d = 6.0
    rain = lowpass(highpass(noise(d), 400), 4000) * 0.35
    for _ in range(140):
        drop = highpass(noise(0.01), 2500) * decay(int(SR * 0.01), 0.003) * rng.uniform(0.2, 0.8)
        place(rain, drop, rng.uniform(0, d - 0.02))
    write("ambient", "rain", loopable(rain), 0.6)

    # Wind: brown noise with slow gusts
    d = 8.0
    t = t_axis(d)
    gust = 0.55 + 0.45 * np.sin(2 * np.pi * t / d) * np.sin(2 * np.pi * 3 * t / d + 1)
    wind = lowpass(brown(d), 700) * gust
    write("ambient", "wind", loopable(wind, 0.8), 0.6)

    # Waves: two swells per loop
    d = 8.0
    t = t_axis(d)
    swell = np.clip(np.sin(2 * np.pi * 2 * t / d - np.pi / 2) * 0.5 + 0.5, 0, 1) ** 2
    waves = lowpass(noise(d), 1200) * (0.15 + 0.85 * swell) + lowpass(brown(d), 300) * 0.4
    write("ambient", "waves", loopable(waves, 0.8), 0.6)

    # Birds: a few chirps
    d = 8.0
    birds = np.zeros(int(SR * d))
    for _ in range(9):
        at = rng.uniform(0, d - 0.6)
        base = rng.uniform(2500, 3800)
        for k in range(rng.integers(2, 5)):
            c = osc(sweep(base, base * rng.uniform(1.2, 1.5), 0.06, 0.6), 0.06, "sine") * env(int(SR * 0.06), 0.005, 0.03) * 0.3
            place(birds, c, at + k * 0.09)
    write("ambient", "birds", loopable(birds, 0.3), 0.5)

    # Crickets: pulsed high trills
    d = 6.0
    crickets = np.zeros(int(SR * d))
    for voice in range(2):
        f = 4300 + voice * 450
        start = voice * 0.37
        at = start
        while at < d - 0.5:
            for k in range(4):
                c = osc(f, 0.025, "sine") * env(int(SR * 0.025), 0.003, 0.01) * 0.25
                place(crickets, c, at + k * 0.04)
            at += rng.uniform(0.6, 1.1)
    write("ambient", "crickets", loopable(crickets, 0.3), 0.45)

    # City: low hum and distant traffic
    d = 8.0
    t = t_axis(d)
    city = lowpass(brown(d), 250) * 0.6 + osc(55, d, "sine") * 0.05
    for _ in range(3):
        at = rng.uniform(0, d - 2.5)
        car = lowpass(noise(2.0), 900) * np.sin(np.linspace(0, np.pi, int(SR * 2.0))) ** 2 * 0.5
        place(city, car, at)
    write("ambient", "city", loopable(city, 0.8), 0.5)

# endregion

# region Music

def music():
    """Two short chiptune loops: a cheerful day tune and a quiet night lullaby."""

    def render(bpm, bars, chords, melody_fn, lead, bass_shape, arp, vol_lead, vol_bass, vol_arp, swing=0.0):
        beat = 60 / bpm
        total = bars * 4 * beat
        out = np.zeros(int(SR * total) + SR)
        for bar in range(bars):
            root, kind = chords[bar % len(chords)]
            third = 3 if kind == "m" else 4
            triad = [root, root + third, root + 7]
            bar_start = bar * 4 * beat
            # Bass: root on 1 and 3, fifth on 2 and 4
            for b, interval in enumerate([0, 7, 0, 7]):
                place(out, tone(note(root - 24 + interval), beat * 0.9, bass_shape, vol_bass, 0.005, 0.08), bar_start + b * beat)
            # Arpeggio in 8ths
            if arp:
                for i in range(8):
                    m = triad[i % 3] + (12 if i % 6 >= 3 else 0)
                    place(out, tone(note(m), beat * 0.45, "square", vol_arp, 0.002, 0.05, duty=0.125), bar_start + i * beat / 2 + (swing * beat / 2 if i % 2 else 0))
            # Melody
            for start, length, m in melody_fn(bar, triad):
                place(out, tone(note(m), length * beat * 0.95, lead, vol_lead, 0.01, 0.06, duty=0.25), bar_start + start * beat)
        out = out[: int(SR * total)]
        return out

    # Day: C major, I - vi - IV - V, 112 bpm, 8 bars
    day_phrases = [
        [(0, 1, 76), (1, 0.5, 79), (1.5, 0.5, 81), (2, 1, 79), (3, 1, 76)],
        [(0, 1, 72), (1, 1, 76), (2, 1.5, 74), (3.5, 0.5, 72)],
        [(0, 0.5, 77), (0.5, 0.5, 76), (1, 1, 74), (2, 1, 72), (3, 1, 69)],
        [(0, 1, 74), (1, 1, 79), (2, 2, 77)],
        [(0, 1, 76), (1, 0.5, 79), (1.5, 0.5, 84), (2, 1, 83), (3, 1, 79)],
        [(0, 1, 81), (1, 1, 79), (2, 1, 76), (3, 1, 72)],
        [(0, 1, 77), (1, 1, 81), (2, 1, 79), (3, 1, 77)],
        [(0, 2, 74), (2, 1, 76), (3, 1, 72)],
    ]
    day = render(
        bpm=112, bars=8,
        chords=[(60, "M"), (57, "m"), (65, "M"), (67, "M")],
        melody_fn=lambda bar, triad: day_phrases[bar],
        lead="square", bass_shape="triangle", arp=True,
        vol_lead=0.22, vol_bass=0.35, vol_arp=0.07, swing=0.12,
    )
    write("music", "day", day, 0.7)

    # Night: A minor, i - VI - III - VII, 72 bpm, 8 bars, triangle lead, no arpeggio
    night_phrases = [
        [(0, 2, 76), (2, 2, 72)],
        [(0, 1, 77), (1, 1, 76), (2, 2, 72)],
        [(0, 2, 79), (2, 1, 76), (3, 1, 74)],
        [(0, 3, 74), (3, 1, 71)],
        [(0, 2, 72), (2, 2, 69)],
        [(0, 1, 72), (1, 1, 74), (2, 2, 77)],
        [(0, 2, 76), (2, 1, 74), (3, 1, 72)],
        [(0, 4, 71)],
    ]
    night = render(
        bpm=72, bars=8,
        chords=[(57, "m"), (65, "M"), (60, "M"), (67, "M")],
        melody_fn=lambda bar, triad: night_phrases[bar],
        lead="triangle", bass_shape="sine", arp=False,
        vol_lead=0.3, vol_bass=0.35, vol_arp=0.0,
    )
    # Soft pads: chord tones as quiet sines
    beat = 60 / 72
    for bar in range(8):
        root, kind = [(57, "m"), (65, "M"), (60, "M"), (67, "M")][bar % 4]
        third = 3 if kind == "m" else 4
        for m in (root, root + third, root + 7):
            pad = osc(note(m), 4 * beat, "sine") * env(int(SR * 4 * beat), 0.4, 0.6) * 0.05
            place(night, pad, bar * 4 * beat)
    write("music", "night", night, 0.65)

# endregion


if __name__ == "__main__":
    effects()
    voices()
    ambience()
    music()
    for folder in ("sfx", "voice", "ambient", "music"):
        files = sorted(os.listdir(os.path.join(ROOT, folder)))
        size = sum(os.path.getsize(os.path.join(ROOT, folder, f)) for f in files)
        print(f"{folder}: {len(files)} files, {size / 1024:.0f} KB")
