"""Samme algoritme som solvePose() i Illustration.kt. Holdes i takt med Kotlin-koden.

1. Lemmene roterer rundt leddene (vinkel og lengde interpoleres), så de ikke krymper underveis.
2. Punkter som står likt i begge stillinger (hender på stanga, føtter i gulvet) festes, og leddet
   mellom (albue/kne) regnes ut med to-ledds IK så lemmene når frem.
3. Ingenting går under gulvet.
"""
import math

FLOOR = 0.965


def lerp(a, b, k):
    return (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)


def lerp_angle(a, b, k):
    d = b - a
    while d > math.pi:
        d -= 2 * math.pi
    while d < -math.pi:
        d += 2 * math.pi
    return a + d * k


def bone(anchor_now, from_a, to_a, from_b, to_b, k):
    va = (to_a[0] - from_a[0], to_a[1] - from_a[1])
    vb = (to_b[0] - from_b[0], to_b[1] - from_b[1])
    ang = lerp_angle(math.atan2(va[1], va[0]), math.atan2(vb[1], vb[0]), k)
    la, lb = math.hypot(*va), math.hypot(*vb)
    ln = la + (lb - la) * k
    return (anchor_now[0] + math.cos(ang) * ln, anchor_now[1] + math.sin(ang) * ln)


def two_bone(p, f, l1, l2, side):
    d = max(math.dist(p, f), 1e-4)
    ux, uy = (f[0] - p[0]) / d, (f[1] - p[1]) / d
    if d >= l1 + l2:
        return (p[0] + ux * d * l1 / (l1 + l2), p[1] + uy * d * l1 / (l1 + l2))
    a = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    h = math.sqrt(max(l1 * l1 - a * a, 0.0))
    return (p[0] + ux * a - uy * h * side, p[1] + uy * a + ux * h * side)


def bend(p, j, f):
    """Hvor mye og hvilken vei leddet j er bøyd på linjen p-f (normalisert kryssprodukt, ca. sinus til vinkelen)."""
    c = (f[0] - p[0]) * (j[1] - p[1]) - (f[1] - p[1]) * (j[0] - p[0])
    return c / max(math.dist(p, f) * math.dist(p, j), 1e-9)


def solve(pose, end, k, ground=None):
    A, B = pose["lines"], end["lines"]
    ground = pose["ground"] if ground is None else ground
    n = len(A)
    a2b = {}
    for (a0, b0), (a1, b1) in zip(A, B):
        a2b[a0], a2b[b0] = a1, b1
    fixed = [p for p, q in a2b.items() if p == q]
    placed = {}
    if fixed:
        placed[fixed[0]] = fixed[0]
    done = [False] * n
    remaining = n
    while remaining > 0:
        progressed = False
        for i in range(n):
            if done[i]:
                continue
            (a0, b0), (a1, b1) = A[i], B[i]
            if a0 in placed:
                if b0 not in placed:
                    placed[b0] = bone(placed[a0], a0, b0, a1, b1, k)
            elif b0 in placed:
                placed[a0] = bone(placed[b0], b0, a0, b1, a1, k)
            else:
                continue
            done[i] = True
            remaining -= 1
            progressed = True
        if not progressed:
            i = done.index(False)
            placed[A[i][0]] = lerp(A[i][0], B[i][0], k)

    # 2. Fest faste punkter med to-ledds IK: F (fast) - J (ledd) - P (resten av kroppen)
    for f in fixed[1:]:
        if math.dist(placed[f], f) < 1e-6:
            continue
        seg = next(((a, b) for a, b in A if f in (a, b)), None)
        if seg is None:
            continue
        j = seg[1] if seg[0] == f else seg[0]
        if j in fixed:
            continue
        seg2 = next(((a, b) for a, b in A if j in (a, b) and f not in (a, b)), None)
        if seg2 is None:
            continue
        p = seg2[1] if seg2[0] == j else seg2[0]
        l1 = math.dist(p, j) + (math.dist(a2b[p], a2b[j]) - math.dist(p, j)) * k
        l2 = math.dist(j, f) + (math.dist(a2b[j], a2b[f]) - math.dist(j, f)) * k
        # Bøyeretning fra den stillingen der lemmet er mest bøyd (en nesten strak arm sier lite om retningen).
        bend_a = bend(p, j, f)
        bend_b = bend(a2b[p], a2b[j], a2b[f])
        chosen = bend_a if abs(bend_a) >= abs(bend_b) else bend_b
        placed[j] = two_bone(placed[p], f, l1, l2, 1.0 if chosen >= 0 else -1.0)
        placed[f] = f

    # 3. Gulvet
    if ground:
        placed = {key: (x, min(y, FLOOR)) for key, (x, y) in placed.items()}

    lines = [(placed[a0], placed[b0]) for a0, b0 in A]
    endpoints = list(a2b.keys())

    def attached(p0, p1):
        anchor = min(endpoints, key=lambda q: math.dist(q, p0))
        return bone(placed[anchor], anchor, p0, a2b[anchor], p1, k)

    # Hodet følger nakken: festepunktet som har samme avstand til hodet i begge stillinger (og er nærmest),
    # ikke bare det nærmeste. Ellers kan hodet henge på en albue eller hånd som svinger forbi (dead bug, thrusters).
    h0, h1 = pose["head"], end["head"]
    anchor = min(endpoints, key=lambda q: math.dist(q, h0) + math.dist(a2b[q], h1)
                 + 3 * abs(math.dist(q, h0) - math.dist(a2b[q], h1)))
    head = bone(placed[anchor], anchor, h0, a2b[anchor], h1, k)
    dots = [attached(d0, d1) for d0, d1 in zip(pose["dots"], end["dots"])] \
        if end["dots"] and len(end["dots"]) == len(pose["dots"]) else pose["dots"]
    if end["props"] and len(end["props"]) == len(pose["props"]):
        # Utstyr som beveger seg (stang, tau) følger kroppen som vektene gjør. Faste festepunkter står i ro.
        def prop_point(p0, p1):
            if p0 in placed:
                return placed[p0]
            if p0 == p1:
                return p0
            if p0 in pose["dots"]:  # f.eks. stanga, som går mellom skivene
                return attached(p0, p1)
            return lerp(p0, p1, k)  # f.eks. hoppetauet
        props = [(prop_point(a0, a1), prop_point(b0, b1)) for (a0, b0), (a1, b1) in zip(pose["props"], end["props"])]
    else:
        props = pose["props"]
    return head, lines, dots, props


def _with_frame(pose, fr):
    """Som Pose.withFrame() i Kotlin: prikker og utstyr fra nøkkelbildet hvis antallet stemmer."""
    dots = fr.get("dots") or []
    props = fr.get("props") or []
    return dict(pose, head=fr["head"], lines=fr["lines"],
                dots=dots if len(dots) == len(pose["dots"]) else pose["dots"],
                props=props if len(props) == len(pose["props"]) else pose["props"])


def _segment(keys, t_ms):
    """(indeks, u) for nøkkelbildet t_ms ligger i, med u = 0..1 gjennom steget (før myk start/stopp)."""
    start = 0.0
    for idx, (_, ms) in enumerate(keys):
        end = start + ms
        if t_ms < end or idx == len(keys) - 1:
            return idx, min(max((t_ms - start) / max(ms, 1), 0.0), 1.0)
        start = end
    return len(keys) - 1, 1.0


def solve_seq(pose, keys, t_ms):
    """Samme som solveSequence() i Kotlin. keys: [(frame_dict, ms)]."""
    idx, u = _segment(keys, t_ms)
    eased = u * u * (3 - 2 * u)
    frm = keys[idx - 1][0] if idx > 0 else pose
    fr = keys[idx][0]
    from_pose = _with_frame(pose, frm)
    same = (frm["head"], frm["lines"], frm.get("dots") or [], frm.get("props") or []) == \
           (fr["head"], fr["lines"], fr.get("dots") or [], fr.get("props") or [])
    if same or eased <= 0:
        return from_pose["head"], from_pose["lines"], from_pose["dots"], from_pose["props"]
    end_frame = dict(head=fr["head"], lines=fr["lines"], dots=fr.get("dots") or [], props=fr.get("props") or [])
    return solve(from_pose, end_frame, eased)


def seq_alpha(keys, t_ms):
    """Samme som sequenceAlpha() i Kotlin: figuren kan tones ut og inn (f.eks. gående utfall som starter på nytt)."""
    idx, u = _segment(keys, t_ms)
    eased = u * u * (3 - 2 * u)
    a0 = keys[idx - 1][0].get("alpha", 1.0) if idx > 0 else 1.0
    a1 = keys[idx][0].get("alpha", 1.0)
    return min(max(a0 + (a1 - a0) * eased, 0.0), 1.0)
