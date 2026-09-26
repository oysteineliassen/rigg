"""Sjekker alle animasjonene i Illustration.kt. Kjør etter gen_poses.py:

    python sjekk.py

Ser etter:
  - feil antall linjer/prikker mellom stillingene
  - ledd som henger sammen i én stilling men ikke i den andre
  - lemmer som krymper underveis (under 80 % av lengden)
  - punkter som skal stå i ro (føtter i gulvet, hender på stanga) men flytter seg
  - knær/albuer som bøyer feil vei underveis
  - deler av figuren utenfor bildet
  - bokshopp: at figuren ikke går gjennom kassen

Avslutter med feilkode 1 hvis noe er galt.
"""
import math
import sys

from anim_solve import solve
from figurer import load, cycle_ms, solve_at

poses, REP_MS = load()
errors = []
warnings = []


def cross(p, j, f):
    return (f[0] - p[0]) * (j[1] - p[1]) - (f[1] - p[1]) * (j[0] - p[0])


for name, pose in poses.items():
    frames = []
    if pose["end"]:
        frames.append(pose["end"])
    if pose["seq"]:
        frames += [fr for fr, _ in pose["seq"]]
    for e in frames:
        if len(e["lines"]) != len(pose["lines"]):
            errors.append(f"{name}: {len(pose['lines'])} linjer i start, {len(e['lines'])} i en annen stilling")
        if e.get("dots") and len(e["dots"]) != len(pose["dots"]):
            errors.append(f"{name}: ulikt antall prikker")
    if errors and errors[-1].startswith(name):
        continue

    # Ledd: punkter som ligger oppå hverandre regnes som samme ledd. Da må de også henge sammen i neste stilling,
    # ellers hopper figuren. Gjelder både frem-og-tilbake og hvert steg i en sekvens.
    steps = [(pose, pose["end"])] if pose["end"] else []
    if pose["seq"]:
        chain = [pose] + [fr for fr, _ in pose["seq"]]
        steps += list(zip(chain, chain[1:]))
    for a_fr, b_fr in steps:
        a_pts = [q for l in a_fr["lines"] for q in l]
        b_pts = [q for l in b_fr["lines"] for q in l]
        bad = next((a_pts[i] for i in range(len(a_pts)) for j in range(i + 1, len(a_pts))
                    if math.dist(a_pts[i], a_pts[j]) < 1e-6 and math.dist(b_pts[i], b_pts[j]) >= 1e-6), None)
        if bad:
            errors.append(f"{name}: to ledd ved {bad} ligger oppå hverandre, men skilles i neste stilling")
            break

    e = pose["end"]
    if e:

        # Bøyer knær/albuer mellom to faste punkter samme vei i begge stillinger?
        A, B = pose["lines"], e["lines"]
        a2b = {}
        for (a0, b0), (a1, b1) in zip(A, B):
            a2b[a0], a2b[b0] = a1, b1
        fixed = [p for p, q in a2b.items() if p == q]
        for f in fixed[1:]:
            seg = next(((a, b) for a, b in A if f in (a, b)), None)
            j = seg[1] if seg[0] == f else seg[0]
            if j in fixed:
                continue
            seg2 = next(((a, b) for a, b in A if j in (a, b) and f not in (a, b)), None)
            if not seg2:
                continue
            p = seg2[1] if seg2[0] == j else seg2[0]
            if (cross(p, j, f) >= 0) != (cross(a2b[p], a2b[j], a2b[f]) >= 0):
                errors.append(f"{name}: leddet ved {j} bøyer ulik vei i de to stillingene")

        # Lengde og drift underveis.
        worst, drift = 1.0, 0.0
        for i in range(21):
            _, lines, _, _ = solve(pose, e, i / 20)
            for (a0, b0), (sa, sb) in zip(pose["lines"], lines):
                if math.dist(a0, b0) >= 0.05:
                    worst = min(worst, math.dist(sa, sb) / math.dist(a0, b0))
                for p0, sp in ((a0, sa), (b0, sb)):
                    if p0 in fixed:
                        drift = max(drift, math.dist(p0, sp))
        if worst < 0.8:
            warnings.append(f"{name}: korteste lem underveis er {worst:.0%} av lengden")
        if drift > 0.03:
            errors.append(f"{name}: punkter som skal stå i ro flytter seg {drift:.3f}")

    # Utenfor bildet, gjennom hele bevegelsen (også sekvenser).
    if pose["end"] or pose["seq"]:
        total = cycle_ms(pose, REP_MS)
        for t in range(0, total, 20):
            head, lines, _, _ = solve_at(pose, t, REP_MS)
            pts = [q for l in lines for q in l]
            if any(not (-0.02 <= x <= 1.02 and -0.02 <= y <= 1.02) for x, y in pts) or head[1] - 0.07 < -0.02:
                errors.append(f"{name}: går utenfor bildet ved {t} ms")
                break

# Bokshopp: ingen del av figuren gjennom kassen (kassen er første prop, en vannrett linje).
bj = poses.get("BOX_JUMP")
if bj and bj["seq"]:
    (bx0, top), (bx1, _) = bj["props"][0]
    half = 0.035  # halv strektykkelse
    hits = 0
    for t in range(0, cycle_ms(bj, REP_MS), 10):
        _, lines, _, _ = solve_at(bj, t, REP_MS)
        for a, b in lines:
            for s in range(11):
                x, y = a[0] + (b[0] - a[0]) * s / 10, a[1] + (b[1] - a[1]) * s / 10
                if bx0 - half < x < bx1 + half and y > top - half + 0.002:
                    hits += 1
                    if hits == 1:
                        errors.append(f"BOX_JUMP: figuren går gjennom kassen ved {t} ms ({x:.2f}, {y:.2f})")

animated = sum(1 for p in poses.values() if p["end"] or p["seq"])
print(f"{len(poses)} poser, {animated} animerte")
for w in warnings:
    print("  obs:", w)
for e in errors:
    print("  FEIL:", e)
print("OK" if not errors else f"{len(errors)} feil")
sys.exit(1 if errors else 0)
