"""Felles for sjekk.py og forhandsvis.py: leser posene fra Illustration.kt og tegner dem som i appen.

Leser den genererte Kotlin-koden (ikke gen_poses.py), så det er det appen faktisk viser som sjekkes.
"""
import math
import os
import re

from PIL import Image, ImageDraw, ImageFont

from anim_solve import solve, solve_seq

HERE = os.path.dirname(os.path.abspath(__file__))
APP_SRC = os.path.join(HERE, "..", "..", "app", "src", "main", "java", "no", "rigg", "app")

_P = r"p\(\s*([-\d.]+)f\s*,\s*([-\d.]+)f\s*\)"


def _pts(s):
    return [(float(a), float(b)) for a, b in re.findall(_P, s)]


def _segs(s):
    return [((float(a), float(b)), (float(c), float(d))) for a, b, c, d in re.findall(_P + r"\s*to\s*" + _P, s)]


def _opt(pattern, text, conv):
    m = re.search(pattern, text, re.S)
    return conv(m.group(1)) if m else []


def load(src_dir=APP_SRC):
    """Returnerer (poses, rep_ms). poses: navn -> dict(head, lines, props, dots, ground, front, end, seq)."""
    ill = open(os.path.join(src_dir, "Illustration.kt"), encoding="utf-8").read()
    rep_ms = int(re.search(r"REP_MS = (\d+)", ill).group(1))
    poses = {}
    for m in re.finditer(r"IllustrationType\.(\w+) to Pose\((.*?)\n    \),", ill, re.S):
        name, body = m.group(1), m.group(2)
        body_wo_seq, _, seqpart = body.partition("sequence = listOf(")
        main, _, endpart = body_wo_seq.partition("end = Frame(")
        pose = dict(
            head=_pts(re.search(r"head = (p\([^)]*\))", main).group(1))[0],
            lines=_segs(re.search(r"lines = listOf\((.*?)\n        \)", main, re.S).group(1)),
            props=_opt(r"props = listOf\((.*?)\n        \)", main, _segs),
            dots=_opt(r"dots = listOf\((.*?\))\)", main, _pts),
            ground="ground = false" not in main,
            front=set(int(x) for x in re.findall(r"\d+", m6.group(1))) if (m6 := re.search(r"front = setOf\(([^)]*)\)", main)) else set(),
            end=None,
            seq=None,
        )
        if endpart:
            pose["end"] = dict(
                head=_pts(re.search(r"head = (p\([^)]*\))", endpart).group(1))[0],
                lines=_segs(re.search(r"lines = listOf\((.*?)\n            \)", endpart, re.S).group(1)),
                dots=_opt(r"dots = listOf\((.*?\))\)", endpart, _pts),
                props=_opt(r"props = listOf\((.*?)\n            \)", endpart, _segs),
            )
        if seqpart:
            keys = []
            for km in re.finditer(r"Key\((\d+), Frame\((.*?)\n            \)\),", seqpart, re.S):
                kb = km.group(2)
                keys.append((dict(head=_pts(re.search(r"head = (p\([^)]*\))", kb).group(1))[0],
                                  lines=_segs(re.search(r"lines = listOf\((.*?)\n                \)", kb, re.S).group(1))),
                             int(km.group(1))))
            pose["seq"] = keys
        poses[name] = pose
    return poses, rep_ms


def cycle_ms(pose, rep_ms):
    return sum(ms for _, ms in pose["seq"]) if pose["seq"] else rep_ms


def solve_at(pose, t_ms, rep_ms):
    """Figuren ved tid t_ms, som i appen: sekvens, frem-og-tilbake, eller stillbilde."""
    if pose["seq"]:
        return solve_seq(pose, pose["seq"], t_ms % cycle_ms(pose, rep_ms))
    if pose["end"]:
        k = (1 - math.cos(2 * math.pi * (t_ms % rep_ms) / rep_ms)) / 2
        return solve(pose, pose["end"], k)
    return pose["head"], pose["lines"], pose["dots"], pose["props"]


BG, INK, PROP, GROUND, DOT = (247, 243, 250), (29, 27, 32), (160, 156, 168), (190, 186, 196), (103, 80, 164)


def draw(pose, solved, size):
    """Tegner en løst figur omtrent slik appen gjør (samme rekkefølge og luftkant rundt armene foran kroppen)."""
    head, lines, dots, props = solved
    img = Image.new("RGB", (size, size), BG)
    d = ImageDraw.Draw(img)
    stroke = size * 0.07

    def line(a, b, col, width):
        a, b = (a[0] * size, a[1] * size), (b[0] * size, b[1] * size)
        d.line([a, b], fill=col, width=max(1, round(width)))
        r = width / 2
        for q in (a, b):
            d.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=col)

    if pose["ground"]:
        line((0.04, 0.97), (0.96, 0.97), GROUND, stroke * 0.5)
    front = pose["front"]
    if not front:
        for a, b in props:
            line(a, b, PROP, stroke * 0.75)
    for i, (a, b) in enumerate(lines):
        if i not in front:
            line(a, b, INK, stroke)
    for i in front:
        a, b = lines[i]
        line((a[0] + (b[0] - a[0]) * 0.3, a[1] + (b[1] - a[1]) * 0.3), b, BG, stroke * 1.9)
    if front:
        for a, b in props:
            line(a, b, PROP, stroke * 0.75)
    for i in front:
        line(lines[i][0], lines[i][1], INK, stroke)
    hx, hy, r = head[0] * size, head[1] * size, 0.07 * size
    d.ellipse([hx - r, hy - r, hx + r, hy + r], fill=INK)
    for x, y in dots:
        rr = stroke * 1.1
        d.ellipse([x * size - rr, y * size - rr, x * size + rr, y * size + rr], fill=DOT)
    return img


def font(px):
    for name in ("DejaVuSans.ttf", "arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, px)
        except OSError:
            pass
    return ImageFont.load_default()
