"""Én kilde for alle strekfigurene: beregner ledd (albuer/knær) så lemmene holder lengden,
og skriver POSES-blokken, IllustrationType-enumen og illustrationFor() i Illustration.kt.

Kjør:  python gen_poses.py        (skriver rett inn i app/.../Illustration.kt)
Rediger ALDRI de genererte delene av Illustration.kt for hånd; endre her og kjør på nytt.

Koordinater: 0..1, x mot høyre, y nedover, gulvet på y = 0.97.
Hver pose: A = stillbildet som vises i listene, B (end) = den andre ytterstillingen i bevegelsen.
"""
import math
import os
import re
import sys

# Standard: Illustration.kt i prosjektet (to mapper opp fra tools/figurer). Kan overstyres med en sti som argument.
KT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "no", "rigg", "app", "Illustration.kt")


def dist(a, b):
    return math.hypot(b[0] - a[0], b[1] - a[1])


def joint(p0, p2, l1, l2, side):
    """Ledd mellom p0 og p2 med lengde l1 fra p0 og l2 fra p2. side=+1/-1 velger hvilken vei leddet bøyer.
    Når avstanden er for lang, legges leddet på linjen (strakt lem)."""
    d = dist(p0, p2)
    ux, uy = (p2[0] - p0[0]) / d, (p2[1] - p0[1]) / d
    if d >= l1 + l2:
        return (p0[0] + ux * d * l1 / (l1 + l2), p0[1] + uy * d * l1 / (l1 + l2))
    a = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    h = math.sqrt(max(l1 * l1 - a * a, 0))
    px, py = p0[0] + ux * a, p0[1] + uy * a
    return (px - uy * h * side, py + ux * h * side)


def shift(pt, dx=0.0, dy=0.0):
    return (pt[0] + dx, pt[1] + dy)


P = {}  # navn -> dict(section, head, lines=[(a,b,kommentar)], props, dots, ground, end, note)


def pose(name, section, head, lines, props=(), dots=(), ground=True, end=None, note=None, front=()):
    """front: indekser for linjer (armer) som ligger foran kroppen og tegnes med luftkant rundt seg."""
    P[name] = dict(section=section, head=head, lines=list(lines), props=list(props), dots=list(dots),
                   ground=ground, end=end, note=note, front=set(front))


def frame(head, lines, dots=(), props=(), note=None):
    return dict(head=head, lines=list(lines), dots=list(dots), props=list(props), note=note)


# ======================================================================== PRESS
S_ = "PRESS"

pose("PUSHUP", S_, (0.1, 0.58), [
    ((0.18, 0.64), (0.58, 0.74), "overkropp"),
    ((0.58, 0.74), (0.92, 0.84), "bein til tærne"),
    ((0.18, 0.64), (0.2, 0.78), "overarm (nesten strak, med en anelse bøy samme vei som albuen går nede)"),
    ((0.2, 0.78), (0.2, 0.92), "underarm ned til hånden"),
], end=frame((0.12, 0.74), [
    ((0.2, 0.8), (0.58, 0.82)),
    ((0.58, 0.82), (0.92, 0.84)),
    ((0.2, 0.8), (0.326, 0.86)),
    ((0.326, 0.86), (0.2, 0.92)),
], note="Nede: kroppen vipper rundt tærne, albuene går bakover. Hender og tær står i ro."))

bench_props = [((0.1, 0.81), (0.62, 0.81), "benk"), ((0.18, 0.81), (0.18, 0.97), None), ((0.54, 0.81), (0.54, 0.97), None)]
S, H = (0.3, 0.74), (0.32, 0.36)
E = (0.31, 0.55)
Hb = (0.32, 0.5)
Eb = joint(S, Hb, dist(S, E), dist(E, H), 1)
pose("BENCH_PRESS", S_, (0.12, 0.7), [
    ((0.2, 0.74), (0.56, 0.74), "overkropp liggende på benken"),
    ((0.56, 0.74), (0.74, 0.72), "lår"),
    ((0.74, 0.72), (0.76, 0.95), "legg ned til gulvet"),
    (S, E, "overarm"),
    (E, H, "underarm, presset helt opp"),
], props=bench_props, dots=[(0.32, 0.32)], end=frame((0.12, 0.7), [
    ((0.2, 0.74), (0.56, 0.74)),
    ((0.56, 0.74), (0.74, 0.72)),
    ((0.74, 0.72), (0.76, 0.95)),
    (S, Eb), (Eb, Hb),
], dots=[shift(Hb, dy=-0.04)], note="Nede: vekta senkes mot brystet, albuen går ut mot beina."))

S, H = (0.28, 0.9), (0.3, 0.52)
E = (0.3, 0.71)
Hb = (0.3, 0.74)
Eb = joint(S, Hb, dist(S, E), dist(E, H), 1)
floor_base = [((0.18, 0.9), (0.54, 0.9), "liggende på gulvet"), ((0.54, 0.9), (0.7, 0.66), "bøyde knær"), ((0.7, 0.66), (0.84, 0.92), None)]
pose("FLOOR_PRESS", S_, (0.1, 0.87), floor_base + [(S, E, "overarm"), (E, H, "underarm, presset opp")],
     dots=[(0.3, 0.48)], end=frame((0.1, 0.87), [l[:2] for l in floor_base] + [(S, Eb), (Eb, Hb)],
                                   dots=[shift(Hb, dy=-0.04)], note="Nede: albuen i gulvet."))

# Skulderpress sett forfra. A = hendene over hodet på vei opp, B = nede ved skuldrene.
Sh = (0.5, 0.37)
EL, HL, ER, HR = (0.3, 0.35), (0.28, 0.12), (0.7, 0.35), (0.72, 0.12)
HLb, HRb = (0.32, 0.3), (0.68, 0.3)
ELb = joint(Sh, HLb, dist(Sh, EL), dist(EL, HL), -1)
ERb = joint(Sh, HRb, dist(Sh, ER), dist(ER, HR), 1)
ohp_body = [((0.5, 0.34), (0.5, 0.62), "overkropp (sett forfra)"), ((0.5, 0.62), (0.42, 0.96), "bein"), ((0.5, 0.62), (0.58, 0.96), None)]
pose("OVERHEAD_PRESS", S_, (0.5, 0.24), ohp_body + [
    (Sh, EL, "armer i \"målstolpe\" på vei opp"), (EL, HL, None), (Sh, ER, None), (ER, HR, None),
], dots=[(0.28, 0.09), (0.72, 0.09)], end=frame((0.5, 0.24), [l[:2] for l in ohp_body] + [
    (Sh, ELb), (ELb, HLb), (Sh, ERb), (ERb, HRb)], dots=[shift(HLb, dy=-0.03), shift(HRb, dy=-0.03)],
    note="Nede: vektene ved skuldrene, albuene ned."))

S, E, H = (0.4, 0.33), (0.6, 0.34), (0.8, 0.35)
Hb = (0.56, 0.37)
Eb = joint(S, Hb, dist(S, E), dist(E, H), 1)
fp_body = [((0.4, 0.27), (0.4, 0.58), "stående, sett fra siden"), ((0.4, 0.58), (0.34, 0.96), None), ((0.4, 0.58), (0.48, 0.96), None)]
pose("FORWARD_PRESS", S_, (0.4, 0.16), fp_body + [(S, E, "armer strakt frem"), (E, H, None)],
     props=[(H, (0.97, 0.42), "strikk mot festepunkt")],
     end=frame((0.4, 0.16), [l[:2] for l in fp_body] + [(S, Eb), (Eb, Hb)], props=[(Hb, (0.97, 0.42))],
               note="Hendene inn til brystet igjen, strikken følger med."))

pose("LATERAL_RAISE", S_, (0.5, 0.16), [
    ((0.5, 0.27), (0.5, 0.56), None), ((0.5, 0.56), (0.44, 0.95), None), ((0.5, 0.56), (0.56, 0.95), None),
    ((0.5, 0.3), (0.24, 0.28), "armer løftet ut til siden"), ((0.5, 0.3), (0.76, 0.28), None),
], dots=[(0.22, 0.28), (0.78, 0.28)], end=frame((0.5, 0.16), [
    ((0.5, 0.27), (0.5, 0.56)), ((0.5, 0.56), (0.44, 0.95)), ((0.5, 0.56), (0.56, 0.95)),
    ((0.5, 0.3), (0.4, 0.54)), ((0.5, 0.3), (0.6, 0.54)),
], dots=[(0.39, 0.565), (0.61, 0.565)], note="Armene ned langs siden."))

# Dips: A = oppe, B = nede. Hendene på stolen og hælene i gulvet står i ro.
Hd = (0.3, 0.5)
Sa, Ea = (0.36, 0.3), (0.33, 0.4)
Sb = (0.37, 0.44)
Eb = joint(Sb, Hd, dist(Sa, Ea), dist(Ea, Hd), 1)
heel = (0.84, 0.95)
Hipa, Hipb = (0.38, 0.58), (0.39, 0.72)
Ka = (0.66, 0.64)
Kb = joint(Hipb, heel, dist(Hipa, Ka), dist(Ka, heel), -1)
pose("DIP", S_, (0.37, 0.18), [
    ((0.36, 0.28), Hipa, "overkropp foran stolen"),
    (Sa, Ea, "arm bak til setet"), (Ea, Hd, None),
    (Hipa, Ka, "lår"), (Ka, heel, "legg, hælene i gulvet"),
], props=[((0.06, 0.52), (0.32, 0.52), "stol/benk"), ((0.1, 0.52), (0.1, 0.97), None)],
    end=frame((0.38, 0.32), [((0.37, 0.42), Hipb), (Sb, Eb), (Eb, Hd), (Hipb, Kb), (Kb, heel)],
              note="Nede: albuene bøyd bakover, hofta senket."))

pose("PUSHDOWN", S_, (0.44, 0.16), [
    ((0.44, 0.27), (0.44, 0.58), None), ((0.44, 0.58), (0.38, 0.96), None), ((0.44, 0.58), (0.52, 0.96), None),
    ((0.44, 0.32), (0.5, 0.48), "albuen inntil siden"), ((0.5, 0.48), (0.6, 0.64), "underarm presser ned"),
], props=[((0.6, 0.64), (0.66, 0.03), "strikk fra festepunkt oppe")], front=[3, 4], end=frame((0.44, 0.16), [
    ((0.44, 0.27), (0.44, 0.58)), ((0.44, 0.58), (0.38, 0.96)), ((0.44, 0.58), (0.52, 0.96)),
    ((0.44, 0.32), (0.5, 0.48)), ((0.5, 0.48), (0.68, 0.42)),
], props=[((0.68, 0.42), (0.66, 0.03))], note="Underarmen tilbake opp, albuen står i ro."))

# Pike push-up: A = oppe, B = hodet ned mot gulvet. Føtter og hender står i ro.
Foot, Hand = (0.1, 0.9), (0.86, 0.9)
Hip_a, Sh_a = (0.48, 0.14), (0.7, 0.5)
E_a = (0.78, 0.7)
torso = dist(Hip_a, Sh_a)
Hip_b = (0.5, 0.2)
Sh_b = (Hip_b[0] + torso * 0.25, Hip_b[1] + torso * 0.968)
E_b = joint(Sh_b, Hand, dist(Sh_a, E_a), dist(E_a, Hand), -1)
pose("PIKE", S_, (0.78, 0.42), [
    (Foot, Hip_a, "bein opp til hoften"), (Hip_a, Sh_a, "overkropp ned"),
    (Sh_a, E_a, "arm ned til hånden"), (E_a, Hand, None),
], end=frame((Sh_b[0] + 0.03, Sh_b[1] + 0.1), [(Foot, Hip_b), (Hip_b, Sh_b), (Sh_b, E_b), (E_b, Hand)],
             note="Nede: hodet mot gulvet mellom hendene, albuene bøyd."))

# ======================================================================== TREKK
S_ = "TREKK"
Sh = (0.76, 0.42)
row_body = [((0.78, 0.41), (0.36, 0.5), "flat rygg, fremoverbøyd"), ((0.36, 0.5), (0.44, 0.74), "lett bøyde knær"),
            ((0.44, 0.74), (0.38, 0.97), None)]
pose("ROW", S_, (0.86, 0.34), row_body + [(Sh, (0.75, 0.57), "armen henger (startposisjon)"), ((0.75, 0.57), (0.74, 0.72), None)],
     dots=[(0.74, 0.75)], front=[3, 4], end=frame((0.86, 0.34), [l[:2] for l in row_body] + [(Sh, (0.64, 0.33)), ((0.64, 0.33), (0.66, 0.48))],
                                    dots=[(0.66, 0.51)], note="Trukket opp: albuen over ryggen."))

heel = (0.93, 0.93)
Hand = (0.42, 0.32)
Sa, Hipa = (0.36, 0.6), (0.64, 0.75)
Ea = (0.39, 0.46)
Ls, Lh = dist(heel, Sa), dist(heel, Hipa)
ang = math.radians(40)
Sb = (heel[0] - Ls * math.cos(ang), heel[1] - Ls * math.sin(ang))
Hipb = (heel[0] - Lh * math.cos(ang), heel[1] - Lh * math.sin(ang))
Eb = joint(Sb, Hand, dist(Sa, Ea), dist(Ea, Hand), 1)
pose("INVERTED_ROW", S_, (0.26, 0.54), [
    (Sa, Hipa, "stiv kropp på skrå under bordet"), (Hipa, heel, None),
    (Sa, Ea, "armer opp til bordkanten"), (Ea, Hand, None),
], props=[((0.03, 0.3), (0.5, 0.3), "bordplate"), ((0.08, 0.3), (0.08, 0.97), None)],
    end=frame((Sb[0] - 0.1, Sb[1] - 0.06), [(Sb, Hipb), (Hipb, heel), (Sb, Eb), (Eb, Hand)],
              note="Trukket opp: brystet mot bordet, kroppen vipper rundt hælene."))

sr_body = [((0.3, 0.44), (0.32, 0.9), "sittende, rett rygg"), ((0.32, 0.9), (0.9, 0.9), "strake bein på gulvet")]
pose("SEATED_ROW", S_, (0.3, 0.34), sr_body + [((0.3, 0.48), (0.18, 0.62), "albuen trukket bak"), ((0.18, 0.62), (0.38, 0.64), None)],
     props=[((0.38, 0.64), (0.9, 0.84), "strikk rundt føttene")],
     front=[2, 3], end=frame((0.3, 0.34), [l[:2] for l in sr_body] + [((0.3, 0.48), (0.47, 0.56)), ((0.47, 0.56), (0.66, 0.62))],
               props=[((0.66, 0.62), (0.9, 0.84))], note="Armene strakt frem igjen."))

pose("PULLUP_HANG", S_, (0.5, 0.36), [
    ((0.5, 0.46), (0.5, 0.68), "overkropp"),
    ((0.5, 0.68), (0.46, 0.95), "hengende bein"), ((0.5, 0.68), (0.54, 0.95), None),
    ((0.5, 0.48), (0.37, 0.31), "armer i V opp til stanga"), ((0.37, 0.31), (0.3, 0.16), None),
    ((0.5, 0.48), (0.63, 0.31), None), ((0.63, 0.31), (0.7, 0.16), None),
], props=[((0.1, 0.16), (0.9, 0.16), "tverrligger")], ground=False, end=frame((0.5, 0.1), [
    ((0.5, 0.22), (0.5, 0.46)),
    ((0.5, 0.46), (0.46, 0.73)), ((0.5, 0.46), (0.54, 0.73)),
    ((0.5, 0.24), (0.331, 0.323)), ((0.331, 0.323), (0.3, 0.16)),
    ((0.5, 0.24), (0.669, 0.323)), ((0.669, 0.323), (0.7, 0.16)),
], note="Toppen: haken over stanga, albuene ut og ned. Hendene står i ro på stanga."), note="Hengende, sett forfra. Stanga er senket litt så haken får plass over den i toppen.")

pd_body = [((0.48, 0.4), (0.48, 0.68), "knestående"), ((0.48, 0.68), (0.5, 0.94), "lår ned til kneet"),
           ((0.5, 0.94), (0.22, 0.94), "legg langs gulvet")]
pose("PULLDOWN", S_, (0.48, 0.3), pd_body + [((0.48, 0.43), (0.66, 0.32), "armer oppe"), ((0.66, 0.32), (0.64, 0.14), None)],
     props=[((0.64, 0.14), (0.62, 0.02), "strikk til festepunkt oppe")],
     front=[3, 4], end=frame((0.48, 0.3), [l[:2] for l in pd_body] + [((0.48, 0.43), (0.58, 0.6)), ((0.58, 0.6), (0.64, 0.43))],
               props=[((0.64, 0.43), (0.62, 0.02))], note="Trukket ned: albuen ned langs siden."))

curl_body = [((0.44, 0.27), (0.44, 0.58), None), ((0.44, 0.58), (0.38, 0.96), None), ((0.44, 0.58), (0.52, 0.96), None)]
pose("CURL", S_, (0.44, 0.16), curl_body + [((0.44, 0.32), (0.47, 0.5), "albuen inntil siden"), ((0.47, 0.5), (0.68, 0.38), "underarm curlet opp")],
     dots=[(0.71, 0.36)], front=[3, 4], end=frame((0.44, 0.16), [l[:2] for l in curl_body] + [((0.44, 0.32), (0.47, 0.5)), ((0.47, 0.5), (0.5, 0.74))],
                                    dots=[(0.505, 0.77)], note="Armen strakt ned. Albuen står i ro."))

# ======================================================================== BEIN
S_ = "BEIN"
pose("SQUAT", S_, (0.61, 0.44), [
    ((0.544, 0.516), (0.39, 0.7), "overkropp lent frem"),
    ((0.39, 0.7), (0.635, 0.726), "lår nesten vannrett"),
    ((0.635, 0.726), (0.5, 0.96), "legg, kneet frem over tærne"),
    ((0.544, 0.52), (0.8, 0.54), "armer frem for balanse"),
], end=frame((0.5, 0.1), [
    ((0.5, 0.2), (0.5, 0.44)), ((0.5, 0.44), (0.5, 0.69)), ((0.5, 0.69), (0.5, 0.96)), ((0.5, 0.21), (0.756, 0.23)),
], note="Stående. Føttene står i ro."), note="Nederst i knebøyen. Figuren er litt mindre enn før, fordi den også må få plass stående.")

pose("WALL_SIT", S_, (0.31, 0.27), [
    ((0.3, 0.37), (0.3, 0.62), "ryggen mot veggen"), ((0.3, 0.62), (0.64, 0.62), "vannrette lår"),
    ((0.64, 0.62), (0.64, 0.96), "loddrett legg"), ((0.3, 0.42), (0.46, 0.58), "hendene på lårene"),
], props=[((0.22, 0.04), (0.22, 0.97), "vegg")], note="Holdøvelse: står stille.")

# Utfall: A = midt i bevegelsen (som før), B = nede med bakre kne mot gulvet.
Hip_a, Ff, Fb = (0.44, 0.5), (0.5, 0.96), (0.15, 0.96)
Kf_a = (0.56, 0.7)
# Bakre kne: nesten strakt i A, men bøyd samme vei (ned/frem) som i B, ellers velger festingen feil side.
Kb_a = joint(Hip_a, Fb, 0.3, 0.246, -1)
Hip_b = (0.44, 0.64)
Kf_b = joint(Hip_b, Ff, dist(Hip_a, Kf_a), dist(Kf_a, Ff), -1)
Kb_b = joint(Hip_b, Fb, 0.3, 0.246, -1)
pose("LUNGE", S_, (0.44, 0.12), [
    ((0.44, 0.24), (0.44, 0.29), "nakke til skulder"), ((0.44, 0.29), Hip_a, "overkropp"), (Hip_a, Kf_a, "fremre bein bøyd"), (Kf_a, Ff, None),
    (Hip_a, Kb_a, "bakre bein"), (Kb_a, Fb, None),
], end=frame((0.44, 0.26), [((0.44, 0.38), (0.44, 0.43)), ((0.44, 0.43), Hip_b), (Hip_b, Kf_b), (Kf_b, Ff), (Hip_b, Kb_b), (Kb_b, Fb)],
             note="Nede: bakre kne nesten i gulvet. Føttene står i ro."))

# Bulgarsk: A = nede, B = oppe. Fremre fot og bakre fot (på benken) står i ro.
Ff, Fb = (0.22, 0.96), (0.72, 0.58)
thigh_f, shin_f, thigh_b, shin_b = 0.216, 0.341, 0.335, 0.311
Hip_a, Hip_b = (0.39, 0.58), (0.37, 0.46)
Kf_a, Kb_a = joint(Hip_a, Ff, thigh_f, shin_f, 1), joint(Hip_a, Fb, thigh_b, shin_b, 1)
Kf_b, Kb_b = joint(Hip_b, Ff, thigh_f, shin_f, 1), joint(Hip_b, Fb, thigh_b, shin_b, 1)
pose("BULGARIAN", S_, (0.36, 0.22), [
    ((0.37, 0.32), (0.374, 0.37), "nakke til skulder"), ((0.374, 0.37), Hip_a, "overkropp"), (Hip_a, Kf_a, "fremre lår"), (Kf_a, Ff, "fremre legg"),
    (Hip_a, Kb_a, "bakre lår ned mot gulvet"), (Kb_a, Fb, "bakre fot oppe på benken"),
], props=[((0.64, 0.62), (0.96, 0.62), "benk"), ((0.9, 0.62), (0.9, 0.97), None)],
    end=frame((0.36, 0.1), [((0.36, 0.2), (0.362, 0.25)), ((0.362, 0.25), Hip_b), (Hip_b, Kf_b), (Kf_b, Ff), (Hip_b, Kb_b), (Kb_b, Fb)], note="Oppe."),
    note="Nede i bevegelsen.")

# Step-up: litt mindre figur, så den får plass stående oppå kassen.
Ffront, Fback = (0.66, 0.7), (0.38, 0.96)
TH, SH = 0.17, 0.16
Hip_a = (0.45, 0.64)
Kf_a = joint(Hip_a, Ffront, TH, SH, -1)
Kb_a = joint(Hip_a, Fback, TH, SH, 1)
Hip_b = (0.64, 0.37)
Kf_b = joint(Hip_b, Ffront, TH, SH, -1)
Kb_b, Fback_b = (0.58, 0.53), (0.5, 0.66)
pose("STEP_UP", S_, (0.44, 0.34), [
    ((0.44, 0.44), Hip_a, "overkropp"), (Hip_a, Kf_a, "fremre lår"), (Kf_a, Ffront, "fremre fot oppe på kassen"),
    (Hip_a, Kb_a, "bakre bein i gulvet"), (Kb_a, Fback, None),
], props=[((0.52, 0.74), (0.92, 0.74), "kasse/trapp"), ((0.52, 0.74), (0.52, 0.97), None), ((0.92, 0.74), (0.92, 0.97), None)],
    end=frame((0.63, 0.08), [((0.63, 0.17), Hip_b), (Hip_b, Kf_b), (Kf_b, Ffront), (Hip_b, Kb_b), (Kb_b, Fback_b)],
              note="Oppe på kassen, bakre bein løftet."))

# ======================================================================== HOFTE
S_ = "HOFTE"
Foot = (0.45, 0.97)
pose("HIP_HINGE", S_, (0.76, 0.26), [
    ((0.68, 0.33), (0.4, 0.55), "flat rygg, fremoverlent"), ((0.4, 0.55), (0.42, 0.78), "lett bøyde knær"),
    ((0.42, 0.78), Foot, None), ((0.68, 0.33), (0.55, 0.62), "arm henger med vekten"),
], dots=[(0.55, 0.62)], front=[3], end=frame((0.45, 0.09), [
    ((0.45, 0.194), (0.44, 0.55)), ((0.44, 0.55), (0.44, 0.78)), ((0.44, 0.78), Foot), ((0.45, 0.194), (0.5, 0.5)),
], dots=[(0.5, 0.515)], note="Stående: hofta skjøvet frem, vekta langs beina."))

Sh, Foot = (0.16, 0.9), (0.8, 0.94)
Hip_a, Knee_a = (0.5, 0.64), (0.72, 0.7)
Hip_b = (0.57, 0.915)
Knee_b = joint(Hip_b, Foot, dist(Hip_a, Knee_a), dist(Knee_a, Foot), -1)
pose("GLUTE_BRIDGE", S_, (0.08, 0.87), [
    (Sh, Hip_a, "hofta løftet, skuldrene i gulvet"),
    (Hip_a, Knee_a, "lår"), (Knee_a, Foot, "legg, foten i gulvet"),
], end=frame((0.08, 0.87), [(Sh, Hip_b), (Hip_b, Knee_b), (Knee_b, Foot)], note="Hofta senket til gulvet."))

calf = [((0.5, 0.24), (0.5, 0.5), None), ((0.5, 0.5), (0.5, 0.8), None),
        ((0.44, 0.86), (0.44, 0.8), "hælene løftet"), ((0.56, 0.86), (0.56, 0.8), None)]
pose("CALF_RAISE", S_, (0.5, 0.12), calf, end=frame((0.5, 0.2), [(shift(a, dy=0.08), shift(b, dy=0.08)) for a, b, _ in calf],
                                                    note="Hælene ned i gulvet."))

pose("SUPERMAN", S_, (0.06, 0.68), [
    ((0.14, 0.74), (0.5, 0.84), "overkropp løftet fra gulvet"),
    ((0.5, 0.84), (0.78, 0.76), "bein løftet fra gulvet"), ((0.78, 0.76), (0.92, 0.66), None),
], end=frame((0.06, 0.84), [((0.14, 0.88), (0.5, 0.9)), ((0.5, 0.9), (0.78, 0.9)), ((0.78, 0.9), (0.94, 0.9))],
             note="Liggende flatt."))

# ======================================================================== KJERNE
S_ = "KJERNE"
pose("PLANK", S_, (0.08, 0.66), [
    ((0.18, 0.72), (0.58, 0.8), "rett kropp"), ((0.58, 0.8), (0.94, 0.91), "bein til tærne"),
    ((0.19, 0.72), (0.21, 0.92), "overarm ned til albuen"), ((0.21, 0.92), (0.04, 0.92), "underarm langs gulvet"),
], note="Holdøvelse: står stille.")

pose("SIDE_PLANK", S_, (0.1, 0.42), [
    ((0.2, 0.48), (0.54, 0.6), "overkropp"), ((0.54, 0.6), (0.88, 0.74), "bein stablet"),
    ((0.22, 0.48), (0.24, 0.92), "støttearm ned til gulvet"), ((0.26, 0.46), (0.26, 0.16), "øverste arm hevet"),
], note="Holdøvelse: står stille.")

Hip = (0.56, 0.7)
legA = [(Hip, (0.74, 0.8)), ((0.74, 0.8), (0.92, 0.9))]
legB = [(Hip, (0.36, 0.8)), ((0.36, 0.8), (0.47, 0.965))]
mc_top = [((0.18, 0.62), Hip, "overkropp i høy planke"), ((0.18, 0.62), (0.2, 0.93), "strak arm")]
pose("MOUNTAIN_CLIMBER", S_, (0.1, 0.56), mc_top + [
    (legA[0][0], legA[0][1], "strakt bakre bein"), (legA[1][0], legA[1][1], None),
    (legB[0][0], legB[0][1], "kneet dratt frem"), (legB[1][0], legB[1][1], None),
], end=frame((0.1, 0.56), [l[:2] for l in mc_top] + legB + legA, note="Beina bytter plass."))

bd_base = [((0.32, 0.62), (0.64, 0.62), "flat rygg på alle fire"), ((0.32, 0.62), (0.32, 0.95), "støttearm"),
           ((0.64, 0.62), (0.66, 0.95), "støttekne"), ((0.66, 0.95), (0.86, 0.95), None)]
pose("BIRD_DOG", S_, (0.22, 0.5), bd_base + [
    ((0.32, 0.64), (0.04, 0.66), "motsatt arm strakt frem"), ((0.64, 0.62), (0.96, 0.58), "motsatt bein strakt bak"),
], end=frame((0.22, 0.5), [l[:2] for l in bd_base] + [((0.32, 0.64), (0.36, 0.95)), ((0.64, 0.62), (0.7, 0.95))],
             note="Tilbake på alle fire."))

Hip = (0.54, 0.9)
table = [(Hip, (0.56, 0.66)), ((0.56, 0.66), (0.76, 0.66))]
table2 = [(Hip, (0.58, 0.665)), ((0.58, 0.665), (0.78, 0.67))]
straight = [(Hip, (0.78, 0.87)), ((0.78, 0.87), (0.97, 0.85))]
db_base = [((0.18, 0.9), Hip, "liggende på ryggen"), ((0.24, 0.9), (0.24, 0.5), "armer rett opp")]
pose("DEAD_BUG", S_, (0.1, 0.87), db_base + [
    (table[0][0], table[0][1], "ett bein i 90 grader"), (table[1][0], table[1][1], None),
    (straight[0][0], straight[0][1], "andre bein strakt lavt"), (straight[1][0], straight[1][1], None),
], end=frame((0.1, 0.87), [l[:2] for l in db_base] + table + table2, note="Begge beina tilbake i 90 grader."))

pose("HOLLOW_HOLD", S_, (0.26, 0.7), [
    ((0.32, 0.8), (0.54, 0.9), "skuldrene løftet, korsryggen i gulvet"),
    ((0.54, 0.9), (0.95, 0.78), "strake bein løftet"), ((0.32, 0.8), (0.04, 0.74), "armer strakt bak hodet"),
], note="Holdøvelse: står stille.")

lr_base = [((0.18, 0.9), (0.54, 0.9), "liggende flatt")]
lr_arms = [((0.24, 0.91), (0.44, 0.93), "armene langs siden")]
pose("LEG_RAISE", S_, (0.1, 0.87), lr_base + [((0.54, 0.9), (0.61, 0.49), "strake bein løftet opp")] + lr_arms,
     end=frame((0.1, 0.87), [lr_base[0][:2], ((0.54, 0.9), (0.95, 0.86)), lr_arms[0][:2]], note="Beina senket nesten til gulvet."))

hr_top = [((0.5, 0.34), (0.5, 0.08), "armer opp til stanga (sett fra siden)"), ((0.5, 0.34), (0.48, 0.62), "overkropp")]
pose("HANGING_RAISE", S_, (0.6, 0.25), hr_top + [((0.48, 0.62), (0.66, 0.58), "knærne løftet"), ((0.66, 0.58), (0.67, 0.76), None)],
     props=[((0.2, 0.08), (0.8, 0.08), "tverrligger")], ground=False,
     end=frame((0.6, 0.25), [l[:2] for l in hr_top] + [((0.48, 0.62), (0.49, 0.8)), ((0.49, 0.8), (0.5, 0.97))],
               note="Hengende med strake bein."))

st_base = [((0.46, 0.9), (0.3, 0.56), "sittende, lent bakover"), ((0.46, 0.9), (0.7, 0.66), "lår"), ((0.7, 0.66), (0.92, 0.72), "føttene løftet")]
pose("SEATED_TWIST", S_, (0.26, 0.46), st_base + [((0.31, 0.6), (0.5, 0.66), "armer holder vekten ut til siden")],
     dots=[(0.53, 0.66)], front=[3], end=frame((0.26, 0.46), [l[:2] for l in st_base] + [((0.31, 0.6), (0.14, 0.72))],
                                    dots=[(0.11, 0.74)], note="Vekta over til den andre siden."))

# ======================================================================== KONDISJON / ANNET
S_ = "KONDISJON"
Hip = (0.46, 0.5)
up = [(Hip, (0.68, 0.54)), ((0.68, 0.54), (0.66, 0.77))]
stand = [(Hip, (0.43, 0.73)), ((0.43, 0.73), (0.42, 0.96))]
armF, armB = ((0.4, 0.28), (0.56, 0.38)), ((0.4, 0.28), (0.26, 0.4))
pose("CARDIO_RUN", S_, (0.38, 0.14), [
    ((0.4, 0.26), Hip, "overkropp"),
    (up[0][0], up[0][1], "ett kne høyt opp"), (up[1][0], up[1][1], None),
    (stand[0][0], stand[0][1], "støttebein"), (stand[1][0], stand[1][1], None),
    (armF[0], armF[1], "arm frem"), (armB[0], armB[1], "arm bak"),
], end=frame((0.38, 0.14), [((0.4, 0.26), Hip)] + stand + up + [armB, armF], note="Beina og armene bytter."))

arrow = [((0.7, 0.5), (0.7, 0.3), "pil som viser bevegelsen"), ((0.7, 0.3), (0.65, 0.37), None), ((0.7, 0.3), (0.75, 0.37), None)]
pose("JUMP", S_, (0.5, 0.12), [
    ((0.5, 0.24), (0.5, 0.42), None), ((0.5, 0.42), (0.6, 0.565), "bein trukket opp"), ((0.6, 0.565), (0.48, 0.69), None),
    ((0.5, 0.26), (0.35, 0.14), "arm svingt opp"),
] + arrow, ground=False, end=frame((0.52, 0.46), [
    ((0.5, 0.56), (0.46, 0.72)), ((0.46, 0.72), (0.62, 0.8)), ((0.62, 0.8), (0.52, 0.96)), ((0.5, 0.58), (0.33, 0.7)),
] + [l[:2] for l in arrow], note="Nede i knebøy, klar til å hoppe."))

front = [((0.5, 0.55), (0.62, 0.75)), ((0.62, 0.75), (0.58, 0.96))]
back = [((0.5, 0.55), (0.382, 0.751)), ((0.382, 0.751), (0.32, 0.955))]
pose("CARRY", S_, (0.5, 0.14), [
    ((0.5, 0.25), (0.5, 0.55), None),
    (front[0][0], front[0][1], "fremre bein"), (front[1][0], front[1][1], None),
    (back[0][0], back[0][1], "bakre bein"), (back[1][0], back[1][1], None),
    ((0.5, 0.3), (0.56, 0.62), "arm med vekten langs siden"),
], dots=[(0.56, 0.64)], front=[5], end=frame((0.5, 0.14), [((0.5, 0.25), (0.5, 0.55))] + back + front + [((0.5, 0.3), (0.56, 0.62))],
                                  dots=[(0.56, 0.64)], note="Går: beina bytter plass."))


# ======================================================================== VARIANTER: utstyr og grep per øvelse
import copy


def derive(name, base, drop=(), add=(), add_end=(), dots=None, end_dots=None, props=None, end_props=None,
           note=None, end_note=None, static=False, head=None, end_head=None, front_add=False):
    """Ny pose basert på en eksisterende: fjern linjer (drop, samme indekser i start og slutt) og legg til nye."""
    b = copy.deepcopy(P[base])
    d = dict(section=b["section"], head=head or b["head"],
             lines=[l for i, l in enumerate(b["lines"]) if i not in drop] + list(add),
             props=b["props"] if props is None else list(props),
             dots=b["dots"] if dots is None else list(dots),
             ground=b["ground"], end=None, note=note or b["note"])
    kept = [i for i in range(len(b["lines"])) if i not in drop]
    front = {kept.index(i) for i in b.get("front", set()) if i in kept}
    if front_add:
        front |= set(range(len(kept), len(kept) + len(add)))
    d["front"] = front
    e = b["end"]
    if e and not static:
        d["end"] = dict(head=end_head or e["head"],
                        lines=[l for i, l in enumerate(e["lines"]) if i not in drop] + list(add_end),
                        dots=e["dots"] if end_dots is None else list(end_dots),
                        props=e["props"] if end_props is None else list(end_props),
                        note=end_note or e["note"])
    P[name] = d


# --- Armer som manglet helt (kroppsvektversjonene), og manualversjoner med vekt i hendene
derive("LUNGE", "LUNGE", add=[((0.44, 0.29), (0.49, 0.55), "armene rett ned langs siden")], add_end=[((0.44, 0.43), (0.49, 0.69))],
       front_add=True)
derive("LUNGE_DB", "LUNGE", dots=[(0.495, 0.575)], end_dots=[(0.495, 0.715)], note="Utfall med en manual i hver hånd.")
derive("BULGARIAN", "BULGARIAN", add=[((0.374, 0.37), (0.335, 0.63), "armene rett ned langs siden")], add_end=[((0.362, 0.25), (0.323, 0.51))],
       front_add=True)
derive("BULGARIAN_DB", "BULGARIAN", dots=[(0.33, 0.655)], end_dots=[(0.318, 0.535)], note="Med en manual i hver hånd.")
derive("STEP_UP", "STEP_UP", add=[((0.44, 0.44), (0.5, 0.62), "armen langs siden")], add_end=[((0.63, 0.17), (0.69, 0.35))],
       front_add=True)
derive("CALF_RAISE", "CALF_RAISE",
       add=[((0.5, 0.24), (0.38, 0.5), "armene langs siden (sett forfra)"), ((0.5, 0.24), (0.62, 0.5), None)],
       add_end=[((0.5, 0.32), (0.38, 0.58)), ((0.5, 0.32), (0.62, 0.58))])
derive("CALF_RAISE_DB", "CALF_RAISE", dots=[(0.37, 0.53), (0.63, 0.53)], end_dots=[(0.37, 0.61), (0.63, 0.61)],
       note="Med en manual i hver hånd.")
derive("GLUTE_BRIDGE", "GLUTE_BRIDGE", add=[((0.16, 0.9), (0.4, 0.93), "armene langs gulvet")], add_end=[((0.16, 0.9), (0.4, 0.93))])
derive("GLUTE_BRIDGE_1LEG", "GLUTE_BRIDGE", add=[((0.5, 0.64), (0.85, 0.373), "det andre beinet strakt i forlengelsen av kroppen")],
       add_end=[((0.57, 0.915), (0.97, 0.93))], note="Enbens: det frie beinet følger hofta.")

# --- Knebøy med forskjellig utstyr (linje 3 i SQUAT er armene frem, som byttes ut)
derive("GOBLET_SQUAT", "SQUAT", drop=[3],
       add=[((0.544, 0.516), (0.6, 0.67), "albuene ned"), ((0.6, 0.67), (0.65, 0.56), "hendene holder vekta inntil brystet")],
       add_end=[((0.5, 0.2), (0.55, 0.36)), ((0.55, 0.36), (0.61, 0.27))],
       dots=[(0.675, 0.57)], end_dots=[(0.635, 0.28)], front_add=True, note="Goblet: én vekt holdt inntil brystet med albuene ned.")
derive("FRONT_SQUAT", "SQUAT", drop=[3],
       add=[((0.544, 0.516), (0.66, 0.65), "albuene frem"), ((0.66, 0.65), (0.7, 0.55), "manualene foran skuldrene")],
       add_end=[((0.5, 0.2), (0.6, 0.33)), ((0.6, 0.33), (0.62, 0.21))],
       dots=[(0.72, 0.54)], end_dots=[(0.63, 0.2)], front_add=True, note="Frontknebøy: manualene holdt foran skuldrene.")
derive("BB_SQUAT", "SQUAT", drop=[3],
       add=[((0.544, 0.516), (0.47, 0.6), "albuene bak og ned"), ((0.47, 0.6), (0.44, 0.5), "hendene holder stanga")],
       add_end=[((0.5, 0.2), (0.4, 0.28)), ((0.4, 0.28), (0.38, 0.2))],
       props=[((0.34, 0.5), (0.74, 0.5), "stanga over skuldrene")], end_props=[((0.3, 0.2), (0.7, 0.2))],
       dots=[(0.34, 0.5), (0.74, 0.5)], end_dots=[(0.3, 0.2), (0.7, 0.2)], front_add=True, note="Stanga på skuldrene, med skiver i hver ende.")
derive("BAND_SQUAT", "FRONT_SQUAT", dots=[], end_dots=[],
       props=[((0.7, 0.55), (0.5, 0.96), "strikk fra hendene ned under føttene")], end_props=[((0.62, 0.21), (0.5, 0.96))],
       note="Strikken går fra hendene ved skuldrene ned under føttene.")
derive("PISTOL_BOX", "SQUAT",
       add=[((0.39, 0.7), (0.635, 0.65), "det andre beinet strakt frem"), ((0.635, 0.65), (0.88, 0.6), None)],
       add_end=[((0.5, 0.44), (0.66, 0.63)), ((0.66, 0.63), (0.82, 0.82))],
       props=[((0.18, 0.78), (0.42, 0.78), "stol bak"), ((0.21, 0.78), (0.21, 0.97), None), ((0.39, 0.78), (0.39, 0.97), None),
              ((0.18, 0.78), (0.18, 0.56), None)],
       note="Pistol squat ned mot en stol, på ett bein.")

# --- Press og armer
derive("OVERHEAD_PRESS_1ARM", "OVERHEAD_PRESS", drop=[5, 6],
       add=[((0.5, 0.37), (0.6, 0.5), "den andre armen henger"), ((0.6, 0.5), (0.63, 0.64), None)],
       add_end=[((0.5, 0.37), (0.6, 0.5)), ((0.6, 0.5), (0.63, 0.64))],
       dots=[P["OVERHEAD_PRESS"]["dots"][0]], end_dots=[P["OVERHEAD_PRESS"]["end"]["dots"][0]], note="Enarms press med én kettlebell.")
derive("LATERAL_RAISE_BAND", "LATERAL_RAISE", dots=[], end_dots=[],
       props=[((0.24, 0.28), (0.44, 0.95), "strikk fra hendene ned under føttene"), ((0.76, 0.28), (0.56, 0.95), None)],
       end_props=[((0.4, 0.54), (0.44, 0.95)), ((0.6, 0.54), (0.56, 0.95))], note="Med strikk i stedet for manualer.")
derive("CURL_BAND", "CURL", dots=[], end_dots=[],
       props=[((0.68, 0.38), (0.52, 0.96), "strikk fra hånden ned under foten")], end_props=[((0.5, 0.74), (0.52, 0.96))],
       note="Med strikk i stedet for manual.")

pose("TRICEPS_EXT", "PRESS", (0.5, 0.33), [
    ((0.5, 0.42), (0.5, 0.7), "sett forfra"), ((0.5, 0.7), (0.44, 0.96), None), ((0.5, 0.7), (0.56, 0.96), None),
    ((0.5, 0.45), (0.36, 0.25), "albuene opp på hver side av hodet"), ((0.36, 0.25), (0.48, 0.1), "hendene sammen om manualen"),
    ((0.5, 0.45), (0.64, 0.25), None), ((0.64, 0.25), (0.52, 0.1), None),
], dots=[(0.5, 0.07)], end=frame((0.5, 0.33), [
    ((0.5, 0.42), (0.5, 0.7)), ((0.5, 0.7), (0.44, 0.96)), ((0.5, 0.7), (0.56, 0.96)),
    ((0.5, 0.45), (0.36, 0.25)), ((0.36, 0.25), (0.45, 0.42)),
    ((0.5, 0.45), (0.64, 0.25)), ((0.64, 0.25), (0.55, 0.42)),
], dots=[(0.5, 0.17)], note="Manualen senket bak hodet (stikker så vidt opp). Albuene står i ro."),
    note="Triceps-fransk: én manual holdt med begge hender over hodet, sett forfra.")

pose("THRUSTER", "PRESS", (0.596, 0.528), [
    ((0.539, 0.597), (0.41, 0.75), "nede i knebøy"), ((0.41, 0.75), (0.61, 0.77), None), ((0.61, 0.77), (0.5, 0.96), None),
    ((0.539, 0.597), (0.6, 0.68), "manualene foran skuldrene"), ((0.6, 0.68), (0.68, 0.62), None),
], dots=[(0.705, 0.61)], front=[3, 4], end=frame((0.5, 0.23), [
    ((0.5, 0.32), (0.5, 0.52)), ((0.5, 0.52), (0.5, 0.74)), ((0.5, 0.74), (0.5, 0.96)),
    ((0.5, 0.32), (0.58, 0.23)), ((0.58, 0.23), (0.6, 0.116)),
], dots=[(0.6, 0.09)], note="Opp fra knebøyen og rett videre til press over hodet."),
    note="Thruster: knebøy med manualene foran skuldrene. Figuren er mindre så den får plass med armene over hodet.")

# --- Trekk
derive("DEAD_HANG", "PULLUP_HANG", static=True, note="Hang i tverrligger: står stille.")
derive("PULLUP_BAND", "PULLUP_HANG",
       props=[((0.1, 0.16), (0.9, 0.16), "tverrligger"), ((0.47, 0.16), (0.46, 0.95), "strikk fra stanga ned under føttene"),
              ((0.53, 0.16), (0.54, 0.95), None)],
       end_props=[((0.1, 0.16), (0.9, 0.16)), ((0.47, 0.16), (0.46, 0.73)), ((0.53, 0.16), (0.54, 0.73))],
       note="Assistert pull-up: strikken fra stanga ned under føttene.")
derive("HANGING_LEG_RAISE", "HANGING_RAISE", drop=[2, 3],
       add=[((0.48, 0.62), (0.66, 0.56), "strake bein løftet frem"), ((0.66, 0.56), (0.84, 0.5), None)],
       add_end=[((0.48, 0.62), (0.49, 0.8)), ((0.49, 0.8), (0.5, 0.97))], note="Beinløft i hang: strake bein.")

# --- Hofte
Sh, Foot = (0.18, 0.66), (0.78, 0.95)
Hip_a, Knee_a = (0.5, 0.62), (0.72, 0.64)
Hip_b = (0.44, 0.85)
Knee_b = joint(Hip_b, Foot, dist(Hip_a, Knee_a), dist(Knee_a, Foot), -1)
pose("HIP_THRUST", "HOFTE", (0.1, 0.62), [
    (Sh, Hip_a, "skuldrene på benken, hofta løftet"), (Hip_a, Knee_a, "lår"), (Knee_a, Foot, "legg, foten i gulvet"),
], props=[((0.04, 0.73), (0.25, 0.73), "benk"), ((0.08, 0.73), (0.08, 0.97), None), ((0.22, 0.73), (0.22, 0.97), None)],
    dots=[(0.5, 0.56)], end=frame((0.1, 0.62), [(Sh, Hip_b), (Hip_b, Knee_b), (Knee_b, Foot)], dots=[(0.44, 0.79)],
                                    note="Hofta senket. Skuldrene og føttene står i ro."),
    note="Hip thrust: skuldrene mot benken, manualen på hofta.")
derive("KB_SWING", "HIP_HINGE", drop=[3],
       add=[((0.68, 0.33), (0.55, 0.62), "armene henger med kettlebellen mellom beina")],
       add_end=[((0.45, 0.194), (0.75, 0.22))], end_dots=[(0.78, 0.22)],
       front_add=True, note="Kettlebell-sving: fra hoftebøy til strake armer frem i brysthøyde.")
derive("GOOD_MORNING", "HIP_HINGE", drop=[3], dots=[], end_dots=[],
       add=[((0.68, 0.33), (0.6, 0.43), "hendene holder strikken ved skuldrene"), ((0.6, 0.43), (0.7, 0.4), None)],
       add_end=[((0.45, 0.194), (0.5, 0.31)), ((0.5, 0.31), (0.55, 0.2))],
       props=[((0.7, 0.4), (0.45, 0.97), "strikk fra skuldrene ned under føttene")], end_props=[((0.55, 0.2), (0.45, 0.97))],
       front_add=True, note="Good morning med strikk over skuldrene.")
derive("PULL_THROUGH", "HIP_HINGE", dots=[], end_dots=[],
       props=[((0.55, 0.62), (0.02, 0.88), "strikk mellom beina bak til festepunkt")], end_props=[((0.5, 0.5), (0.02, 0.88))],
       note="Pull-through: strikken går mellom beina bak til et festepunkt.")
derive("SINGLE_LEG_RDL", "HIP_HINGE", add=[((0.4, 0.55), (0.02, 0.44), "det andre beinet løftet bak")],
       add_end=[((0.44, 0.55), (0.36, 0.95))], note="Enbens markløft: det andre beinet løftes bak.")

# --- Kondisjon
pose("JUMP_ROPE", "KONDISJON", (0.5, 0.21), [
    ((0.5, 0.3), (0.5, 0.58), "i luften, sett forfra"), ((0.5, 0.58), (0.46, 0.88), None), ((0.5, 0.58), (0.54, 0.88), None),
    ((0.5, 0.33), (0.38, 0.52), "armer ut til siden"), ((0.5, 0.33), (0.62, 0.52), None),
], props=[((0.38, 0.52), (0.36, 0.95), "tauet under føttene"), ((0.36, 0.95), (0.64, 0.95), None), ((0.64, 0.95), (0.62, 0.52), None)],
    end=frame((0.5, 0.27), [
        ((0.5, 0.36), (0.5, 0.64)), ((0.5, 0.64), (0.46, 0.94)), ((0.5, 0.64), (0.54, 0.94)),
        ((0.5, 0.39), (0.38, 0.58)), ((0.5, 0.39), (0.62, 0.58)),
    ], props=[((0.38, 0.58), (0.34, 0.08)), ((0.34, 0.08), (0.66, 0.08)), ((0.66, 0.08), (0.62, 0.58))],
        note="Landet, tauet over hodet."), note="Hoppetau sett forfra.")


# --- Bokshopp: fra knebøy på gulvet opp over kassen. Første linje starter i foten, som blir roten,
#     så foten går i en rett bane som klarer kanten på kassen.
BOX_TOP = 0.76
BOX_PROPS = [((0.52, BOX_TOP), (0.95, BOX_TOP), "kasse"), ((0.52, BOX_TOP), (0.52, 0.97), None), ((0.95, BOX_TOP), (0.95, 0.97), None)]


def knee_fwd(hip, foot, up=False):
    """Kne mellom hofte og fot, bøyd fremover (mot kassen), lår og legg 0.16 hver.
    up=True: kneet peker opp (foten løftet bak/under kroppen, som når man tar et steg ned fra kassen)."""
    a = joint(hip, foot, 0.16, 0.16, 1)
    b = joint(hip, foot, 0.16, 0.16, -1)
    k = (a if a[1] <= b[1] else b) if up else (a if a[0] >= b[0] else b)
    return (round(k[0], 3), round(k[1], 3))


def fig2(foot1, hip, foot2, torso, arm, up1=False):
    """Figur med to bein, bygd fra føtter og hofte (knærne regnes ut) og vinkler for overkropp og arm.
    Linje 0 starter i foten på det fremre beinet, som blir roten når ingen fot står i ro."""
    def step(p, ang, ln):
        r = math.radians(ang)
        return (round(p[0] + ln * math.cos(r), 3), round(p[1] + ln * math.sin(r), 3))
    if foot2 == foot1:  # beina samlet: en anelse fra hverandre, ellers regnes de som samme ledd når de skilles
        foot2 = (round(foot1[0] - 0.004, 3), foot1[1])
    k1, k2 = knee_fwd(hip, foot1, up1), knee_fwd(hip, foot2)
    sh = step(hip, torso, 0.2)
    hand = step(sh, arm, 0.17)
    head = step(sh, torso, 0.1)
    return head, [(foot1, k1), (k1, hip), (hip, sh), (sh, hand), (hip, k2), (k2, foot2)]


# Nøkkelstillinger: (fremre fot, hofte, bakre fot, overkropp, arm). Stående på kassen: foten på y = 0.725.
BJ = {
    "A": fig2((0.34, 0.96), (0.28, 0.8), (0.34, 0.96), -55, 150),     # knebøy foran kassen, armene bak
    "B": fig2((0.42, 0.76), (0.43, 0.45), (0.42, 0.76), -78, 10),     # sats: strak kropp, armene svinger frem
    "C": fig2((0.62, 0.6), (0.56, 0.44), (0.62, 0.6), -70, 0),        # knærne opp over kanten
    "D": fig2((0.7, 0.725), (0.64, 0.56), (0.7, 0.725), -55, 20),     # landet mykt i knebøy på kassen
    "E": fig2((0.7, 0.725), (0.69, 0.42), (0.7, 0.725), -88, 85),     # står oppreist på kassen
    "F0": fig2((0.7, 0.725), (0.64, 0.46), (0.44, 0.64), -85, 60),    # bakre fot løftes bakover over kanten
    "F1": fig2((0.7, 0.725), (0.53, 0.62), (0.34, 0.87), -80, 50),     # senker seg på fremre bein
    "F2": fig2((0.7, 0.725), (0.47, 0.68), (0.32, 0.96), -80, 60),      # bakre fot i gulvet
    "F2b": fig2((0.68, 0.65), (0.45, 0.67), (0.32, 0.96), -82, 65, up1=True),   # fremre fot løftes rett opp
    "F3": fig2((0.56, 0.64), (0.41, 0.66), (0.32, 0.96), -85, 70, up1=True),     # fremre fot løftes fra kassen
    "F4": fig2((0.4, 0.7), (0.34, 0.65), (0.32, 0.96), -86, 80),     # ... bakover forbi kanten
    "F5": fig2((0.34, 0.96), (0.34, 0.66), (0.32, 0.96), -88, 85),      # begge føtter i gulvet
    "G": fig2((0.34, 0.96), (0.33, 0.645), (0.32, 0.96), -88, 85),     # står oppreist
}

head, lines = BJ["A"]
pose("BOX_JUMP", "BEIN", head, [
    (lines[0][0], lines[0][1], "fremre legg"), (lines[1][0], lines[1][1], "fremre lår"),
    (lines[2][0], lines[2][1], "overkropp"), (lines[3][0], lines[3][1], "arm"),
    (lines[4][0], lines[4][1], "bakre lår (dekker det fremre når beina står samlet)"), (lines[5][0], lines[5][1], "bakre legg"),
], props=BOX_PROPS, front=[3], note="Bokshopp: knebøy foran kassen. Animeres som en sekvens, se sequence.")
P["BOX_JUMP"]["seq"] = [
    (BJ["B"], 300, "sats"), (BJ["C"], 250, "knærne opp over kanten"), (BJ["D"], 250, "myk landing"),
    (BJ["E"], 400, "reiser seg"), (BJ["E"], 300, "står"),
    (BJ["F0"], 300, "bakre fot bakover over kanten"), (BJ["F1"], 250, "senker seg"), (BJ["F2"], 250, "bakre fot i gulvet"),
    (BJ["F2b"], 200, "fremre fot løftes"), (BJ["F3"], 200, "føres bakover"), (BJ["F4"], 200, "forbi kanten"), (BJ["F5"], 250, "begge føtter i gulvet"),
    (BJ["G"], 300, "står"), (BJ["A"], 450, "ned i knebøy"), (BJ["A"], 300, "klar"),
]

# ======================================================================== SKJELETT (nyere figurer)
# Figurene under er bygd av et skjelett med faste lengder, så lemmene holder lengden og leddene alltid henger
# sammen. Vinkler er i grader i bildets retninger: 0 = mot høyre, 90 = ned, -90 = opp, 180 = mot venstre.
# Et lem (arm eller bein) oppgis enten med vinkler eller med et fast endepunkt:
#   ("a", vinkel1, vinkel2)  overarm/lår og underarm/legg
#   ("p", endepunkt, hint)   endepunktet står fast (hånd i gulvet, fot på bakken); leddet legges mot hint-vinkelen
#   ("j", ledd, endepunkt)   begge punktene oppgitt direkte
LEN = dict(shin=0.19, thigh=0.19, torso=0.25, uarm=0.14, farm=0.12, neck=0.1)


def rnd(p):
    return (round(p[0], 3), round(p[1], 3))


def at(p, a, ln):
    r = math.radians(a)
    return rnd((p[0] + ln * math.cos(r), p[1] + ln * math.sin(r)))


def ang(a, b):
    return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))


def limb(base, spec, l1, l2):
    kind = spec[0]
    if kind == "a":
        j = at(base, spec[1], l1)
        return j, at(j, spec[2], l2)
    if kind == "j":
        return rnd(spec[1]), rnd(spec[2])
    end, hint = rnd(spec[1]), spec[2]
    a, b = joint(base, end, l1, l2, 1), joint(base, end, l1, l2, -1)
    hx, hy = math.cos(math.radians(hint)), math.sin(math.radians(hint))
    mx, my = (base[0] + end[0]) / 2, (base[1] + end[1]) / 2
    score = lambda q: (q[0] - mx) * hx + (q[1] - my) * hy
    return rnd(a if score(a) >= score(b) else b), end


def apart(pts, used):
    """Punkter som tilfeldigvis ligger oppå et annet ledd flyttes en anelse, ellers tror animasjonen de er samme ledd."""
    out = []
    for p in pts:
        while p in used:
            p = (round(p[0] + 0.003, 3), p[1])
        used.add(p)
        out.append(p)
    return out


def up_from(foot, shin_a, thigh_a, s=1.0):
    """Hofta regnet ut fra en fot som står fast: vinkel fot->kne og kne->hofte."""
    return at(at(foot, shin_a, LEN["shin"] * s), thigh_a, LEN["thigh"] * s)


def side(hip, torso, legs, arms, s=1.0, neck=0.0, head=None, dots=(), props=(), alpha=1.0, L=None):
    """Figur sett fra siden. legs/arms: lister med 1 eller 2 lem-spesifikasjoner (None = utelatt).
    Linjerekkefølge: bein 1 (fot, kne, hofte), overkropp, arm 1, bein 2, arm 2. Arm 1 er linje 3 og 4."""
    L = {k: v * s for k, v in (L or LEN).items()}
    hip = rnd(hip)
    sh = at(hip, torso, L["torso"])
    used = {hip, sh}
    k0, f0 = limb(hip, legs[0], L["thigh"], L["shin"])
    k0, f0 = apart([k0, f0], used)
    lines = [(f0, k0), (k0, hip), (hip, sh)]
    e0, h0 = apart(list(limb(sh, arms[0], L["uarm"], L["farm"])), used)
    lines += [(sh, e0), (e0, h0)]
    pts = dict(hip=hip, sh=sh, knee=k0, foot=f0, elbow=e0, hand=h0)
    if len(legs) > 1 and legs[1]:
        k1, f1 = apart(list(limb(hip, legs[1], L["thigh"], L["shin"])), used)
        lines += [(hip, k1), (k1, f1)]
        pts.update(knee2=k1, foot2=f1)
    if len(arms) > 1 and arms[1]:
        e1, h1 = apart(list(limb(sh, arms[1], L["uarm"], L["farm"])), used)
        lines += [(sh, e1), (e1, h1)]
        pts.update(elbow2=e1, hand2=h1)
    hd = rnd(head) if head else at(sh, torso + neck, L["neck"])
    return dict(head=hd, lines=lines, dots=[rnd(d) for d in dots], props=list(props), alpha=alpha, pts=pts)


def front(pel, legs, arms, torso=-90.0, s=1.0, hw=0.05, sw=0.085, shoulders=None, head=None, dots=(), props=(),
          alpha=1.0, L=None):
    """Figur sett forfra. legs/arms: [venstre, høyre]. shoulders kan overstyres (f.eks. når overkroppen vris)."""
    L = {k: v * s for k, v in (L or LEN).items()}
    pel = rnd(pel)
    neck = at(pel, torso, L["torso"])
    hl, hr = at(pel, torso - 90, hw), at(pel, torso + 90, hw)
    sl, sr = (rnd(shoulders[0]), rnd(shoulders[1])) if shoulders else (at(neck, torso - 90, sw), at(neck, torso + 90, sw))
    used = {pel, neck, hl, hr, sl, sr}
    kl, fl = apart(list(limb(hl, legs[0], L["thigh"], L["shin"])), used)
    el, al = apart(list(limb(sl, arms[0], L["uarm"], L["farm"])), used)
    kr, fr = apart(list(limb(hr, legs[1], L["thigh"], L["shin"])), used)
    er, ar = apart(list(limb(sr, arms[1], L["uarm"], L["farm"])), used)
    lines = [(fl, kl), (kl, hl), (hl, pel), (pel, neck), (neck, sl), (sl, el), (el, al),
             (pel, hr), (hr, kr), (kr, fr), (neck, sr), (sr, er), (er, ar)]
    hd = rnd(head) if head else at(neck, torso, L["neck"])
    pts = dict(pel=pel, neck=neck, sl=sl, sr=sr, hand_l=al, hand_r=ar, foot_l=fl, foot_r=fr, elbow_l=el, elbow_r=er)
    return dict(head=hd, lines=lines, dots=[rnd(d) for d in dots], props=list(props), alpha=alpha, pts=pts)


def arms_front(neck, arms, sw=0.1, head=None, s=1.0, dots=(), props=()):
    """Bare skuldre og armer sett forfra (armhevinger forfra: resten av kroppen er skjult bak hodet)."""
    L = {k: v * s for k, v in LEN.items()}
    neck = rnd(neck)
    sl, sr = rnd((neck[0] - sw, neck[1])), rnd((neck[0] + sw, neck[1]))
    used = {neck, sl, sr}
    el, al = apart(list(limb(sl, arms[0], L["uarm"], L["farm"])), used)
    er, ar = apart(list(limb(sr, arms[1], L["uarm"], L["farm"])), used)
    lines = [(al, el), (el, sl), (sl, neck), (neck, sr), (sr, er), (er, ar)]
    return dict(head=rnd(head), lines=lines, dots=list(dots), props=list(props), alpha=1.0, pts={})


def put(name, section, frames, note, keys=None, props=(), ground=True, front=(), labels=None):
    """Registrerer en pose. frames[0] er stillbildet. Med keys=[(ms, etikett), ...] spilles frames[1:] som sekvens;
    uten keys og med to frames er frames[1] sluttstillingen (frem og tilbake). Én frame = står stille."""
    a = frames[0]
    n = len(a["lines"])
    for fr in frames[1:]:
        assert len(fr["lines"]) == n, f"{name}: ulikt antall linjer"
    lines = [(p, q, (labels or {}).get(i)) for i, (p, q) in enumerate(a["lines"])]
    P[name] = dict(section=section, head=a["head"], lines=lines, props=list(props) + a["props"], dots=a["dots"],
                   ground=ground, end=None, note=note, front=set(front))
    if keys:
        assert len(keys) == len(frames) - 1, f"{name}: {len(keys)} tider for {len(frames) - 1} nøkkelbilder"
        P[name]["seq"] = [(dict(head=fr["head"], lines=fr["lines"], dots=fr["dots"],
                                props=(list(props) + fr["props"]) if fr["props"] else [], alpha=fr["alpha"]), ms, lab)
                          for fr, (ms, lab) in zip(frames[1:], keys)]
    elif len(frames) == 2:
        b = frames[1]
        P[name]["end"] = dict(head=b["head"], lines=b["lines"], dots=b["dots"],
                              props=(list(props) + b["props"]) if b["props"] else [], note=None)


def box(x0, x1, top):
    """Benk/kasse som utstyr: topp og to bein."""
    return [((x0, top), (x1, top), "benk"), ((x0 + 0.02, top), (x0 + 0.02, 0.97), None), ((x1 - 0.02, top), (x1 - 0.02, 0.97), None)]


def on_line(a, b, d):
    """Punktet d fra a mot b."""
    return at(a, ang(a, b), d)


FLOOR_Y = 0.955  # midt i streken for noe som ligger/står på gulvet
ARM = LEN["uarm"] + LEN["farm"]
BODY = LEN["torso"] + LEN["thigh"] + LEN["shin"]  # skulder til fot, strak kropp


# ---------------------------------------------------------------- ARMHEVINGER (hodet til venstre)
def pushup_frame(H, F, S, bent_hint=-45, **kw):
    """Strak kropp fra skulder S til føtter F, hendene i H."""
    hip = on_line(S, F, LEN["torso"])
    return side(hip, ang(hip, S), [("p", F, -90)], [("p", H, bent_hint)], neck=-12, **kw)


def plank_shoulder(F, y, H=None):
    """Skulderen på høyden y, med strak kropp til føttene F (kroppen er BODY lang)."""
    dx = math.sqrt(max(BODY ** 2 - (F[1] - y) ** 2, 0))
    return rnd((F[0] - dx, y))


H = (0.22, FLOOR_Y)
F = rnd((H[0] + math.sqrt(BODY ** 2 - ARM ** 2), FLOOR_Y))
top, bot = plank_shoulder(F, FLOOR_Y - ARM * 0.97), plank_shoulder(F, 0.87)
put("PUSHUP", "PRESS", [pushup_frame(H, F, top), pushup_frame(H, F, bot)],
    "Armhevinger: strak kropp fra hæl til hode, hender og tær i gulvet. A = oppe, B = brystet ned mot gulvet.")

# Med hendene hevet (på benk/kasse): kroppen står brattere.
BENCH_TOP = 0.77
Hi = (0.36, round(BENCH_TOP - 0.026 - 0.035, 3))
Fi = rnd((Hi[0] + math.sqrt(BODY ** 2 - (FLOOR_Y - (Hi[1] - ARM)) ** 2), FLOOR_Y))
put("PUSHUP_INCLINE", "PRESS",
    [pushup_frame(Hi, Fi, plank_shoulder(Fi, Hi[1] - ARM * 0.97)), pushup_frame(Hi, Fi, plank_shoulder(Fi, Hi[1] - 0.075))],
    "Armhevinger med hendene på en benk: lettere variant, kroppen på skrå oppover.", props=box(0.22, 0.46, BENCH_TOP))

# Med føttene hevet: hodet lavere enn føttene.
Fd = (0.84, round(BENCH_TOP - 0.026 - 0.035, 3))
Hd = (0.2, FLOOR_Y)
put("PUSHUP_DECLINE", "PRESS",
    [pushup_frame(Hd, Fd, plank_shoulder(Fd, FLOOR_Y - ARM * 0.97)), pushup_frame(Hd, Fd, plank_shoulder(Fd, 0.87))],
    "Armhevinger med føttene på en benk: tyngre variant, hodet lavere enn føttene.", props=box(0.72, 0.97, BENCH_TOP))


# Med strikk over ryggen: strikken går fra hånden over øvre del av ryggen. Punktet på ryggen følger kroppen.
def band_back(fr):
    p = fr["pts"]
    a = ang(p["sh"], p["hip"])
    over_sh = at(p["sh"], a - 90, 0.062)       # rett over skulderen
    over_back = at(on_line(p["sh"], p["hip"], 0.14), a - 90, 0.062)
    fr["props"] = [(p["hand"], over_sh, "strikk fra hånden opp over skulderen"), (over_sh, over_back, "og over ryggen")]
    return fr


put("PUSHUP_BAND", "PRESS", [band_back(pushup_frame(H, F, top)), band_back(pushup_frame(H, F, bot))],
    "Armhevinger med strikk over ryggen: strikken holdes i hendene og går over skulderbladene.", front=[3, 4])

# Diamant, sett forfra: hendene samlet under brystet, albuene går ut når brystet senkes.
ARMS_F = 1.12  # armene forfra: litt lengre så de når gulvet med skuldrene i riktig høyde
la = ARM * ARMS_F
Hl, Hr = (0.465, FLOOR_Y), (0.535, FLOOR_Y)
y_top = round(FLOOR_Y - math.sqrt(la ** 2 - 0.065 ** 2), 3)
put("PUSHUP_DIAMOND", "PRESS", [
    arms_front((0.5, y_top), [("p", Hl, 180), ("p", Hr, 0)], sw=0.1, head=(0.5, y_top + 0.03), s=ARMS_F),
    arms_front((0.5, 0.845), [("p", Hl, 200), ("p", Hr, -20)], sw=0.1, head=(0.5, 0.855), s=ARMS_F),
], "Diamant-armhevinger sett forfra: hendene samlet under brystet (tommel og pekefinger danner en diamant). "
   "Albuene går ut og bak når brystet senkes.", ground=True)

# Bueskytter, sett forfra: vekten flyttes til én arm som bøyes, den andre armen er strak ut til siden.
Al, Ar = (0.24, FLOOR_Y), (0.76, FLOOR_Y)
y_arch = round(FLOOR_Y - math.sqrt(la ** 2 - 0.16 ** 2), 3)
mid = arms_front((0.5, y_arch), [("p", Al, 180), ("p", Ar, 0)], sw=0.1, head=(0.5, y_arch - 0.06), s=ARMS_F)
left = arms_front((0.36, 0.85), [("p", Al, 200), ("p", Ar, -60)], sw=0.1, head=(0.34, 0.8), s=ARMS_F)
right = arms_front((0.64, 0.85), [("p", Al, 240), ("p", Ar, -20)], sw=0.1, head=(0.66, 0.8), s=ARMS_F)
put("PUSHUP_ARCHER", "PRESS", [mid, left, mid, mid, right, mid, mid],
    "Bueskytter-armhevinger sett forfra: ned mot den ene hånden med den andre armen strak, så til den andre siden.",
    keys=[(700, "ned mot venstre hånd"), (600, "opp"), (150, "kort pause"), (700, "ned mot høyre hånd"), (600, "opp"),
          (150, "kort pause")])

# Pike: hoftene høyt, hodet ned mot gulvet foran hendene.
Fp, Hp = (0.26, FLOOR_Y), (0.68, FLOOR_Y)
D = Hp[0] - Fp[0]
legs_l, arm_l = LEN["thigh"] + LEN["shin"], LEN["torso"] + ARM
a_ = (legs_l ** 2 - arm_l ** 2 + D ** 2) / (2 * D)
hip_top = rnd((Fp[0] + a_, FLOOR_Y - math.sqrt(legs_l ** 2 - a_ ** 2)))
pike_top = side(hip_top, ang(hip_top, Hp), [("p", Fp, 0)], [("p", Hp, 180)], neck=-5)
hip_bot = rnd((hip_top[0] + 0.03, hip_top[1] + 0.02))
pike_bot = side(hip_bot, 45, [("p", Fp, 0)], [("p", Hp, -150)], neck=-5)
put("PIKE", "PRESS", [pike_top, pike_bot],
    "Pike-armhevinger: hoftene høyt i en omvendt V, strake bein. Albuene bøyes bakover og hodet går ned mot gulvet "
    "rett foran hendene. Hender og føtter står i ro.")


# ---------------------------------------------------------------- STÅENDE ARMØVELSER (vendt mot høyre)
def stand(arm, hip=(0.46, 0.575), dots=(), props=(), neck=0.0, legs=None):
    """Stående, beina litt fra hverandre (fremre fot først, så båndet under foten henger på den)."""
    legs = legs or [("p", (hip[0] + 0.035, FLOOR_Y), 0), ("p", (hip[0] - 0.035, FLOOR_Y), 0)]
    return side(hip, -90, legs, [arm], neck=neck, dots=dots, props=props)


# Triceps-fransk: manualen holdt med begge hender over hodet, sett fra siden. Albuene peker opp og står i ro.
TRI_S = 0.9  # litt mindre figur, så manualen får plass over hodet
TRI_L = {k: v * TRI_S for k, v in dict(LEN, uarm=0.165, farm=0.14).items()}  # armene litt lengre, så albuen synes
tri_hip = (0.47, round(FLOOR_Y - (LEN["thigh"] + LEN["shin"]) * TRI_S, 3))


def tri(fore):
    return side(tri_hip, -90, [("p", (0.5, FLOOR_Y), 0), ("p", (0.44, FLOOR_Y), 0)], [("a", -101, fore)],
                neck=18, L=TRI_L)


tri_top, tri_bot = tri(-84), tri(106)  # >180 grader fra toppen, så underarmen går bak hodet og ikke foran
tri_top["dots"] = [tri_top["pts"]["hand"]]
tri_bot["dots"] = [tri_bot["pts"]["hand"]]
put("TRICEPS_EXT", "PRESS", [tri_top, tri_bot],
    "Triceps-fransk sett fra siden: manualen holdt over hodet med albuene pekende opp. Underarmen senkes bak hodet "
    "og strekkes opp igjen. Armen ligger bak hodet i tegningen, så hodet skjuler underarmen nederst.")

# Curl: overarmen inntil siden, bare underarmen beveger seg.
curl_up = stand(("a", 86, -52))
curl_dn = stand(("a", 86, 84))
for fr in (curl_up, curl_dn):
    fr["dots"] = [fr["pts"]["hand"]]
put("CURL", "TREKK", [curl_up, curl_dn],
    "Bicepscurl: albuen inntil siden, underarmen løftes. A = oppe, B = armen strakt ned.", front=[3, 4])
cb_up, cb_dn = stand(("a", 86, -52)), stand(("a", 86, 84))
for fr in (cb_up, cb_dn):
    fr["props"] = [(fr["pts"]["hand"], fr["pts"]["foot"], "strikk fra hånden ned under foten")]
put("CURL_BAND", "TREKK", [cb_up, cb_dn], "Bicepscurl med strikk: strikken går fra hånden ned under foten.", front=[3, 4])

# ---------------------------------------------------------------- HENG I STANG (sett forfra)
BAR_Y = 0.13
BAR = [((0.14, BAR_Y), (0.86, BAR_Y), "tverrligger")]
HANG_S = 0.9


def hang(neck_y, hands, bend=False, feet_dx=0.055, s=HANG_S, elbows_down=False):
    """elbows_down: chin-up i toppen. Albuene peker frem mot oss, så overarmen ser kort ut forfra."""
    L = {k: v * s for k, v in LEN.items()}
    pel = (0.5, neck_y + L["torso"])
    fy = pel[1] + L["thigh"] + L["shin"]
    hl, hr = hands
    arms = [("p", hl, 150 if bend else 180), ("p", hr, 30 if bend else 0)]
    if elbows_down:
        el, er = (0.44, neck_y + 0.065), (0.56, neck_y + 0.065)
        arms = [("j", el, hl), ("j", er, hr)]
    return front(pel, [("p", (0.5 - feet_dx, fy), 180), ("p", (0.5 + feet_dx, fy), 0)], arms, s=s, hw=0.045, sw=0.075)


wide = [(0.33, BAR_Y), (0.67, BAR_Y)]
narrow = [(0.4, BAR_Y), (0.6, BAR_Y)]
put("PULLUP_HANG", "TREKK", [hang(0.34, wide), hang(0.17, wide, bend=True)],
    "Pull-up sett forfra: kroppen midt mellom armene. Fra strake armer til haken over stanga, albuene ut og ned.",
    props=BAR, ground=False)
put("CHINUP", "TREKK", [hang(0.34, narrow), hang(0.17, narrow, bend=True, elbows_down=True)],
    "Chin-up sett forfra: smalere grep med håndflatene mot deg, albuene ned foran kroppen.", props=BAR, ground=False)
put("DEAD_HANG", "TREKK", [hang(0.34, wide)], "Hang i tverrligger: strake armer, står stille.", props=BAR, ground=False)
pb_a, pb_b = hang(0.34, wide), hang(0.17, wide, bend=True)
for fr in (pb_a, pb_b):
    p = fr["pts"]
    fr["props"] = [((0.47, BAR_Y), p["foot_l"], "strikk fra stanga ned under føttene"), ((0.53, BAR_Y), p["foot_r"], None)]
put("PULLUP_BAND", "TREKK", [pb_a, pb_b], "Assistert pull-up: strikken henger fra stanga og ned under føttene.",
    props=BAR, ground=False)


# ---------------------------------------------------------------- KNEBØY (vendt mot høyre, én fot står i ro)
SQ_F = (0.5, FLOOR_Y)


def squat(depth, arm_fn, dots_fn=None, props_fn=None):
    """depth 0 = stående, 1 = nederst. arm_fn(torso) gir armen; dots_fn/props_fn(pts) gir vekter og utstyr."""
    shin = -90 + 28 * depth
    thigh = -90 - 98 * depth  # fra kne til hofte: rett opp -> bakover og litt opp
    torso = -90 + 34 * depth
    hip = up_from(SQ_F, shin, thigh)
    fr = side(hip, torso, [("p", SQ_F, 0)], [arm_fn(torso)])
    if dots_fn:
        fr["dots"] = [rnd(d) for d in dots_fn(fr["pts"])]
    if props_fn:
        fr["props"] = props_fn(fr["pts"])
    return fr


# Kroppsvekt: armene henger i toppen og løftes frem for balanse på vei ned.
put("SQUAT", "BEIN", [squat(1, lambda t: ("a", 0, 0)), squat(0, lambda t: ("a", 92, 92))],
    "Knebøy med kroppsvekt: A = nederst med armene frem for balanse, B = stående med armene langs siden.", front=[3, 4])
# Goblet: én manual holdt inntil brystet med begge hender, albuene ned.
gob = lambda t: ("a", t + 165, t + 30)
gob_dot = lambda p: [at(p["hand"], 60, 0.02)]
put("GOBLET_SQUAT", "BEIN", [squat(1, gob, gob_dot), squat(0, gob, gob_dot)],
    "Goblet-knebøy: én manual holdt inntil brystet med albuene pekende ned.", front=[3, 4])
# Frontknebøy: manualene hviler på skuldrene, albuene høyt frem.
rack = lambda t: ("a", t + 100, t - 75)
rack_dot = lambda p: [at(p["hand"], 40, 0.06)]
put("FRONT_SQUAT", "BEIN", [squat(1, rack, rack_dot), squat(0, rack, rack_dot)],
    "Frontknebøy: manualene hviler oppå skuldrene, albuene høyt og pekende frem.", front=[3, 4])
# Stang på ryggen: armen har samme form hele veien, så stanga flytter seg uten å vippe.
bb_arm = lambda t: ("a", 125, -75)
bb_bar = lambda p: [rnd((p["hand"][0] - 0.2, p["hand"][1])), rnd((p["hand"][0] + 0.2, p["hand"][1]))]
put("BB_SQUAT", "BEIN",
    [squat(1, bb_arm, bb_bar, lambda p: [(rnd((p["hand"][0] - 0.2, p["hand"][1])), rnd((p["hand"][0] + 0.2, p["hand"][1])), "stanga")]),
     squat(0, bb_arm, bb_bar, lambda p: [(rnd((p["hand"][0] - 0.2, p["hand"][1])), rnd((p["hand"][0] + 0.2, p["hand"][1])), None)])],
    "Knebøy med stang: stanga ligger på øvre del av ryggen, med skiver i hver ende.", front=[3, 4])
band_sq = lambda p: [(p["hand"], p["foot"], "strikk fra hendene ved skuldrene ned under føttene")]
put("BAND_SQUAT", "BEIN", [squat(1, rack, None, band_sq), squat(0, rack, None, band_sq)],
    "Knebøy med strikk: strikken går fra hendene ved skuldrene ned under føttene.", front=[3, 4])

# Thruster: frontknebøy og rett videre til press over hodet.
press_up = lambda t: ("a", -76, -80)
thr = [squat(1, rack, rack_dot), squat(0.05, rack, rack_dot), squat(0, press_up, lambda p: [p["hand"]]),
       squat(0, press_up, lambda p: [p["hand"]]), squat(0.05, rack, rack_dot), squat(1, rack, rack_dot),
       squat(1, rack, rack_dot)]
put("THRUSTER", "PRESS", thr,
    "Thruster: manualene på skuldrene i knebøyen, opp og rett videre til press over hodet, ned igjen.",
    keys=[(450, "opp fra knebøyen"), (300, "presser over hodet"), (200, "strake armer"), (350, "ned til skuldrene"),
          (450, "ned i knebøy"), (200, "nederst")], front=[3, 4])


# Hoppknebøy: knebøy med armene bak, eksplosivt opp med armene frem, landing og ned igjen.
def jump_frame(depth, arm, lift=0.0, toes=False):
    fr = squat(depth, lambda t: arm)
    if lift or toes:  # løft hele figuren (i lufta); tærne ned
        dy = -lift
        fr = dict(fr, head=rnd((fr["head"][0], fr["head"][1] + dy)),
                  lines=[(rnd((a[0], a[1] + dy)), rnd((b[0], b[1] + dy))) for a, b in fr["lines"]])
    return fr


put("JUMP", "KONDISJON", [
    jump_frame(1, ("a", 150, 150)),
    jump_frame(0.05, ("a", -10, -10)),
    jump_frame(0, ("a", -42, -42), lift=0.08),
    jump_frame(0, ("a", -42, -42), lift=0.08),
    jump_frame(0.35, ("a", 25, 25)),
    jump_frame(1, ("a", 150, 150)),
    jump_frame(1, ("a", 150, 150)),
], "Hoppknebøy: ned i knebøy med armene bak, eksplosivt opp med armene frem og opp, myk landing.",
    keys=[(320, "eksplosivt opp"), (220, "i lufta"), (120, "toppen"), (280, "lander"), (450, "ned i knebøy"),
          (350, "klar")], front=[3, 4])


# ---------------------------------------------------------------- GÅENDE UTFALL OG SIDEUTFALL
def lunge_frame(hip, front_leg, back_leg, alpha=1.0):
    return side(hip, -88, [front_leg, back_leg], [("a", 92, 92)], alpha=alpha)


x0, step = 0.2, 0.29
S0 = lunge_frame((x0, 0.575), ("p", (x0 + 0.004, FLOOR_Y), 0), ("p", (x0, FLOOR_Y), 0))
L1 = lunge_frame((x0 + step * 0.52, 0.765), ("p", (x0 + step, FLOOR_Y), -30), ("p", (x0, FLOOR_Y), 80))
M1 = lunge_frame((x0 + step, 0.585), ("p", (x0 + step, FLOOR_Y), 0), ("a", 55, 105))
L2 = lunge_frame((x0 + step * 1.52, 0.765), ("p", (x0 + step, FLOOR_Y), 80), ("p", (x0 + 2 * step, FLOOR_Y), -30))
M2 = lunge_frame((x0 + 2 * step, 0.575), ("p", (x0 + 2 * step - 0.004, FLOOR_Y), 0), ("p", (x0 + 2 * step, FLOOR_Y), 0))
fade = lambda fr: dict(fr, alpha=0.0)
put("WALKING_LUNGE", "BEIN", [L1, L1, M1, L2, L2, M2, M2, fade(M2), fade(S0), S0, L1],
    "Gående utfall: steg frem ned i utfall, opp og videre med det andre beinet. Toner ut og starter forfra.",
    keys=[(150, "nederst"), (550, "opp, bakre bein svinger frem"), (550, "ned i neste utfall"),
          (150, "nederst"), (650, "opp, samler beina"), (250, "står"), (300, "toner ut"), (40, "tilbake til start"),
          (300, "toner inn"), (650, "steg frem og ned")])


def side_lunge(pel_x, pel_y, lean=0.0):
    pel = (pel_x, pel_y)
    chest = (pel_x + lean * 0.3, pel_y - 0.16)
    return front(pel, [("p", (0.3, FLOOR_Y), 180), ("p", (0.7, FLOOR_Y), 0)],
                 [("p", chest, 150), ("p", rnd((chest[0] + 0.004, chest[1])), 30)], torso=-90 + lean * 20)


C = side_lunge(0.5, 0.6)
LL, LR = side_lunge(0.36, 0.74, -0.4), side_lunge(0.64, 0.74, 0.4)
put("LATERAL_LUNGE", "BEIN", [LL, C, C, LR, C, C, LL],
    "Sideutfall sett forfra: bred stilling, vekten over til den ene siden med bøyd kne mens det andre beinet er strakt.",
    keys=[(550, "tilbake"), (150, "pause"), (650, "ned mot høyre"), (550, "tilbake"), (150, "pause"), (650, "ned mot venstre")])


# ---------------------------------------------------------------- KJERNE
# Sideplanke på albuen, sett forfra (hodet til venstre).
Sp_S, Sp_F = (0.2, round(FLOOR_Y - LEN["uarm"], 3)), None
Sp_F = rnd((Sp_S[0] + math.sqrt(BODY ** 2 - (FLOOR_Y - Sp_S[1]) ** 2), FLOOR_Y))
sp_hip = on_line(Sp_S, Sp_F, LEN["torso"])
put("SIDE_PLANK", "KJERNE", [side(sp_hip, ang(sp_hip, Sp_S), [("p", Sp_F, -90)],
                                  [("j", (0.2, FLOOR_Y), (0.2 - LEN["farm"], FLOOR_Y)), ("a", -90, -90)], neck=20)],
    "Sideplanke: underarmen i gulvet med albuen rett under skulderen, strak kropp. Den andre armen peker opp.")

# Dead bug: på ryggen, armene rett opp og knærne i 90 grader. Motsatt arm og bein strekkes ut, vekselvis.
DB_HIP = (0.57, 0.935)


def dead_bug(arm0, leg0, arm1, leg1):
    return side(DB_HIP, 180, [leg0, leg1], [arm0, arm1], head=(0.215, 0.9))


tab_leg, tab_arm = ("a", -90, 0), ("a", -90, -90)
ext_leg, ext_arm = ("a", -9, -9), ("a", 191, 191)
T = dead_bug(tab_arm, tab_leg, tab_arm, tab_leg)
put("DEAD_BUG", "KJERNE", [T, dead_bug(ext_arm, tab_leg, tab_arm, ext_leg), dead_bug(ext_arm, tab_leg, tab_arm, ext_leg), T,
                           T, dead_bug(tab_arm, ext_leg, ext_arm, tab_leg), dead_bug(tab_arm, ext_leg, ext_arm, tab_leg), T, T],
    "Dead bug: på ryggen med armene rett opp og knærne bøyd i 90 grader. Én arm og motsatt bein strekkes ut mot gulvet, "
    "så bytter det.",
    keys=[(700, "arm og motsatt bein ut"), (250, "hold"), (650, "tilbake"), (200, "pause"), (700, "andre arm og bein ut"),
          (250, "hold"), (650, "tilbake"), (200, "pause")])

# Russian twist sett forfra: sittende, knærne opp foran brystet og overkroppen lent litt bakover. Det er overkroppen
# som vris og lener seg mot siden; armene holder formen (bøyde, hendene samlet foran brystet) og følger bare med.
def twist(side_):
    pel = (0.5, 0.935)
    torso = -90 + 26 * side_
    neck = at(pel, torso, 0.295)
    ny = neck[1]
    if side_ == 0:
        sl, sr = (neck[0] - 0.085, ny), (neck[0] + 0.085, ny)
        hands = (0.5, 0.76)
        el, er = (0.43, 0.745), (0.57, 0.745)
    else:
        # Vridd: skulderen bak ses nær nakken, skulderen foran kommer over mot midten. Hendene følger brystet til siden.
        back, fwd = (neck[0] + 0.035 * side_, ny + 0.012), (neck[0] - 0.05 * side_, ny - 0.005)
        sl, sr = (fwd, back) if side_ > 0 else (back, fwd)
        hands = (neck[0] + 0.12 * side_, 0.83)
        el = rnd((sl[0] + 0.25 * (hands[0] - sl[0]) - 0.05 * side_, (sl[1] + hands[1]) / 2 + 0.02))
        er = rnd((sr[0] + 0.25 * (hands[0] - sr[0]) - 0.05 * side_, (sr[1] + hands[1]) / 2 + 0.02))
    knees, feet = ((0.455, 0.72), (0.545, 0.72)), ((0.425, 0.845), (0.575, 0.845))
    fr = front(pel, [("j", knees[0], feet[0]), ("j", knees[1], feet[1])],
               [("j", el, hands), ("j", er, rnd((hands[0] + 0.004, hands[1])))],
               torso=torso, shoulders=(sl, sr), head=at(neck, torso - 6 * side_, 0.09), L=dict(LEN, torso=0.295))
    fr["dots"] = [rnd((hands[0] + 0.002, hands[1] + 0.005))]
    return fr


put("SEATED_TWIST", "KJERNE", [twist(0), twist(-1), twist(-1), twist(0), twist(1), twist(1), twist(0)],
    "Russian twist sett forfra: sittende med knærne opp foran brystet og overkroppen lent litt bakover. Overkroppen "
    "vris og lener seg mot den ene siden så manualen kommer ned ved hofta, så mot den andre.",
    keys=[(600, "vri mot venstre"), (200, "manualen ned ved hofta"), (550, "tilbake"), (600, "vri mot høyre"),
          (200, "manualen ned ved hofta"), (550, "tilbake")], front=[0, 1, 5, 6, 8, 9, 11, 12])

# Kroppssag: planke på underarmene, kroppen skyves bakover og frem ved å vippe i skulderen. Føttene glir.
BS_E, BS_H = (0.2, FLOOR_Y), (0.2 - LEN["farm"], FLOOR_Y)


def body_saw(upper_ang):
    S = at(BS_E, upper_ang, LEN["uarm"])
    Fb = rnd((S[0] + math.sqrt(BODY ** 2 - (FLOOR_Y - S[1]) ** 2), FLOOR_Y))
    hip = on_line(S, Fb, LEN["torso"])
    return side(hip, ang(hip, S), [("p", Fb, -90)], [("j", BS_E, BS_H)], neck=-10)


put("BODY_SAW", "KJERNE", [body_saw(-100), body_saw(-40)],
    "Kroppssag: planke på underarmene med føttene på noe som glir. Kroppen skyves bakover og frem fra skuldrene.")

# Superman: på magen, armer, bryst og bein løftes samtidig og holdes. Hofta blir liggende.
SM_HIP = (0.56, 0.935)


def superman(lift):
    return side(SM_HIP, 180 + 17 * lift, [("a", -15 * lift, -15 * lift)], [("a", 180 + 26 * lift, 180 + 26 * lift)],
                neck=12)


put("SUPERMAN", "HOFTE", [superman(1), superman(1), superman(0), superman(0), superman(1)],
    "Superman: på magen med armene strakt frem. Armer, bryst og bein løftes fra gulvet og holdes, hofta ligger.",
    keys=[(600, "holder"), (550, "senker"), (400, "hviler"), (550, "løfter")])


# ---------------------------------------------------------------- KONDISJON
# Høye knær: oppreist, knærne vekselvis opp til hoftehøyde, armene pumper motsatt.
HK_X = 0.46


def high_knees(which, mid=False):
    stance = ("p", (HK_X, FLOOR_Y), 0)
    if mid:
        up_leg = ("p", (HK_X + 0.05, FLOOR_Y - 0.03), 0)
        hip = (HK_X + 0.015, 0.585)
    else:
        up_leg = ("a", -8, 88)
        hip = (HK_X + 0.015, 0.585)
    # Armene pumper i takt med knærne: armen på motsatt side går frem og opp (hånden mot haka), den andre
    # bak med albuen. Midt i byttet passerer begge armene langs siden.
    arm_f, arm_b = ("a", 38, -78), ("a", 138, 72)
    if mid:
        arm_f = arm_b = ("a", 92, 10)
    if which == 0:  # bein 2 oppe, arm 1 frem
        return side(hip, -80, [stance, up_leg], [arm_f, arm_b])
    return side(hip, -80, [up_leg, stance], [arm_b, arm_f])


put("CARDIO_RUN", "KONDISJON",
    [high_knees(0), high_knees(1, mid=True), high_knees(1), high_knees(0, mid=True), high_knees(0)],
    "Høye knær: oppreist overkropp, knærne vekselvis opp til hoftehøyde og armene i motsatt takt.",
    keys=[(300, "bytter"), (300, "andre kne opp"), (300, "bytter"), (300, "første kne opp")], front=[3, 4])

# Burpee: ned med hendene i gulvet, hopp bakover til planke, inn igjen, opp og hopp med armene over hodet.
BU_F, BU_H = (0.6, FLOOR_Y), (0.74, FLOOR_Y)
bu_stand = side((BU_F[0], 0.575), -90, [("p", BU_F, 0)], [("a", 92, 92)])
bu_hip = (0.47, 0.8)
bu_squat = side(bu_hip, -24, [("p", BU_F, -45)], [("p", BU_H, 0)], neck=25)
bu_S = rnd((BU_H[0], FLOOR_Y - ARM))
bu_Fp = rnd((bu_S[0] - math.sqrt(BODY ** 2 - ARM ** 2), FLOOR_Y))
bu_hip_p = on_line(bu_S, bu_Fp, LEN["torso"])
bu_plank = side(bu_hip_p, ang(bu_hip_p, bu_S), [("p", bu_Fp, -90)], [("p", BU_H, 180)], neck=12)
bu_reach = side((BU_F[0], 0.57), -90, [("p", BU_F, 0)], [("a", -60, -70)])
bu_air = side((BU_F[0], 0.505), -90, [("a", 92, 94)], [("a", -55, -60)])
put("BURPEE", "KONDISJON", [bu_plank, bu_plank, bu_squat, bu_reach, bu_air, bu_stand, bu_stand, bu_squat, bu_plank],
    "Burpee: ned i knebøy med hendene i gulvet, hopp bakover til planke, hopp inn igjen, opp og hopp med armene over hodet.",
    keys=[(250, "planke"), (280, "hopper inn igjen"), (320, "reiser seg"), (220, "hopper"), (280, "lander"), (300, "står"),
          (400, "ned med hendene i gulvet"), (280, "hopper bakover til planke")], front=[3, 4])


# ======================================================================== Kotlin
SECTION_TITLES = {"PRESS": "PRESS", "TREKK": "TREKK", "BEIN": "BEIN", "HOFTE": "HOFTE", "KJERNE": "KJERNE",
                  "KONDISJON": "KONDISJON / ANNET"}


def f(x):
    s = f"{round(x, 3):.3f}".rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "f"


def pp(pt):
    return f"p({f(pt[0])}, {f(pt[1])})"


def seg_lines(segs, indent):
    out = []
    for s in segs:
        a, b = s[0], s[1]
        c = s[2] if len(s) > 2 else None
        out.append(f"{indent}{pp(a)} to {pp(b)}," + (f" // {c}" if c else ""))
    return out


out = ["private val POSES: Map<IllustrationType, Pose> = mapOf("]
last = None
ORDER = ["PRESS", "TREKK", "BEIN", "HOFTE", "KJERNE", "KONDISJON"]
for name, d in sorted(P.items(), key=lambda kv: ORDER.index(kv[1]["section"])):
    if d["section"] != last:
        if last is not None:
            out.append("")
        out.append(f"    // ------------------------------------------------------------ {SECTION_TITLES[d['section']]}")
        last = d["section"]
    out.append(f"    IllustrationType.{name} to Pose(")
    if d["note"]:
        out.append(f"        // {d['note']}")
    out.append(f"        head = {pp(d['head'])},")
    out.append("        lines = listOf(")
    out += seg_lines(d["lines"], "            ")
    out.append("        ),")
    if d["props"]:
        out.append("        props = listOf(")
        out += seg_lines(d["props"], "            ")
        out.append("        ),")
    if d["dots"]:
        out.append(f"        dots = listOf({', '.join(pp(x) for x in d['dots'])}),")
    if not d["ground"]:
        out.append("        ground = false,")
    if d.get("front"):
        out.append(f"        front = setOf({', '.join(str(i) for i in sorted(d['front']))}),")
    e = d["end"]
    if e:
        if e["note"]:
            out.append(f"        // {e['note']}")
        out.append("        end = Frame(")
        out.append(f"            head = {pp(e['head'])},")
        out.append("            lines = listOf(")
        out += seg_lines(e["lines"], "                ")
        out.append("            ),")
        if e["dots"]:
            out.append(f"            dots = listOf({', '.join(pp(x) for x in e['dots'])}),")
        if e["props"]:
            out.append("            props = listOf(")
            out += seg_lines(e["props"], "                ")
            out.append("            ),")
        out.append("        ),")
    if d.get("seq"):
        out.append("        sequence = listOf(")
        for fr, ms, label in d["seq"]:
            if isinstance(fr, tuple):
                fr = dict(head=fr[0], lines=fr[1])
            out.append(f"            // {label}")
            out.append(f"            Key({ms}, Frame(")
            out.append(f"                head = {pp(fr['head'])},")
            out.append("                lines = listOf(")
            out += seg_lines(fr["lines"], "                    ")
            out.append("                ),")
            if fr.get("dots"):
                out.append(f"                dots = listOf({', '.join(pp(x) for x in fr['dots'])}),")
            if fr.get("props"):
                out.append("                props = listOf(")
                out += seg_lines(fr["props"], "                    ")
                out.append("                ),")
            alpha = fr.get("alpha", 1.0)
            out.append("            )" + (f", alpha = {f(alpha)})," if alpha != 1.0 else "),"))
        out.append("        ),")
    out.append("    ),")
out.append(")")
block = "\n".join(out)

src = open(KT, encoding="utf-8").read()
start = src.index("private val POSES: Map<IllustrationType, Pose> = mapOf(")
stop = src.index("\n\nprivate fun poseFor")
src = src[:start] + block + src[stop:]
open(KT, "w", encoding="utf-8").write(src)
MAP = {
    # Press
    ("db_bench", "bb_bench"): "BENCH_PRESS",
    ("db_floor_press", "db_floor_press_1arm"): "FLOOR_PRESS",
    ("db_shoulder_press",): "OVERHEAD_PRESS",
    ("kb_press",): "OVERHEAD_PRESS_1ARM",
    ("db_triceps_ext",): "TRICEPS_EXT",
    ("thruster_db",): "THRUSTER",
    ("band_press", "pallof_press", "band_face_pull"): "FORWARD_PRESS",
    ("pushup",): "PUSHUP",
    ("pushup_incline",): "PUSHUP_INCLINE",
    ("pushup_decline",): "PUSHUP_DECLINE",
    ("pushup_diamond",): "PUSHUP_DIAMOND",
    ("pushup_archer",): "PUSHUP_ARCHER",
    ("pushup_band",): "PUSHUP_BAND",
    ("pike_pushup",): "PIKE",
    ("dips_chair",): "DIP",
    ("band_triceps_pushdown",): "PUSHDOWN",
    ("db_lateral_raise",): "LATERAL_RAISE",
    ("band_lateral_raise",): "LATERAL_RAISE_BAND",
    # Trekk
    ("pullup", "negative_pullup"): "PULLUP_HANG",
    ("chinup",): "CHINUP",
    ("pullup_band_assist",): "PULLUP_BAND",
    ("dead_hang",): "DEAD_HANG",
    ("band_pulldown",): "PULLDOWN",
    ("table_row", "trx_row"): "INVERTED_ROW",
    ("band_row",): "SEATED_ROW",
    ("db_rear_fly",): "ROW",
    ("db_curl",): "CURL",
    ("band_curl",): "CURL_BAND",
    ("superman",): "SUPERMAN",
    # Bein
    ("air_squat", "tempo_squat"): "SQUAT",
    ("goblet_squat",): "GOBLET_SQUAT",
    ("db_front_squat",): "FRONT_SQUAT",
    ("bb_squat",): "BB_SQUAT",
    ("band_squat",): "BAND_SQUAT",
    ("pistol_box",): "PISTOL_BOX",
    ("wall_sit",): "WALL_SIT",
    ("db_lunge",): "LUNGE_DB",
    ("bulgarian",): "BULGARIAN",
    ("db_bulgarian",): "BULGARIAN_DB",
    ("step_up",): "STEP_UP",
    ("walking_lunge",): "WALKING_LUNGE",
    ("lateral_lunge",): "LATERAL_LUNGE",
    # Hofte
    ("glute_bridge", "hamstring_slide"): "GLUTE_BRIDGE",
    ("glute_bridge_1leg",): "GLUTE_BRIDGE_1LEG",
    ("hip_thrust_db",): "HIP_THRUST",
    ("rdl_1leg_db",): "SINGLE_LEG_RDL",
    ("kb_swing", "kb_swing_cardio"): "KB_SWING",
    ("good_morning_band",): "GOOD_MORNING",
    ("band_pullthrough",): "PULL_THROUGH",
    ("calf_raise",): "CALF_RAISE",
    ("calf_raise_db",): "CALF_RAISE_DB",
    # Kjerne
    ("side_plank",): "SIDE_PLANK",
    ("bird_dog",): "BIRD_DOG",
    ("dead_bug",): "DEAD_BUG",
    ("hollow_hold",): "HOLLOW_HOLD",
    ("leg_raise",): "LEG_RAISE",
    ("hanging_knee_raise",): "HANGING_RAISE",
    ("hanging_leg_raise",): "HANGING_LEG_RAISE",
    ("russian_twist_db",): "SEATED_TWIST",
    ("ab_wheel_alt",): "BODY_SAW",
    ("suitcase_carry",): "CARRY",
    # Kondisjon
    ("mountain_climber",): "MOUNTAIN_CLIMBER",
    ("jump_rope",): "JUMP_ROPE",
    ("jump_squat",): "JUMP",
    ("burpee",): "BURPEE",
    ("box_jump",): "BOX_JUMP",
    ("high_knees",): "CARDIO_RUN",
}
FALLBACK = {"PUSH": "PUSHUP", "PULL": "ROW", "SQUAT": "SQUAT", "LUNGE": "LUNGE", "HINGE": "HIP_HINGE", "CORE": "PLANK", "CARDIO": "CARDIO_RUN"}
used = set(MAP.values()) | set(FALLBACK.values())
missing = [t for t in used if t not in P]
unused = [t for t in P if t not in used]
assert not missing, f"koblet til pose som ikke finnes: {missing}"
assert not unused, f"pose som ingen øvelse bruker: {unused}"

src = open(KT, encoding="utf-8").read()
enum_names = sorted(P.keys(), key=lambda n: ORDER.index(P[n]["section"]))
enum_lines, cur = [], []
for n in enum_names:
    cur.append(n)
    if len(", ".join(cur)) > 90:
        enum_lines.append("    " + ", ".join(cur) + ",")
        cur = []
if cur:
    enum_lines.append("    " + ", ".join(cur) + ",")
es = src.index("enum class IllustrationType {")
ee = src.index("}", es)
src = src[:es] + "enum class IllustrationType {\n" + "\n".join(enum_lines) + "\n" + src[ee:]

m = ['/** Kobler en øvelse-id til riktig illustrasjonstype, med fall-back basert på bevegelsesmønster. */',
     'fun illustrationFor(exercise: Exercise): IllustrationType = when (exercise.id) {']
for ids, typ in MAP.items():
    m.append("    " + ", ".join(f'"{i}"' for i in ids) + f" -> IllustrationType.{typ}")
m.append("    else -> when (exercise.pattern) {")
for pat, typ in FALLBACK.items():
    m.append(f"        Pattern.{pat} -> IllustrationType.{typ}")
m += ["    }", "}"]
ms = src.index("/** Kobler en øvelse-id til riktig illustrasjonstype")
me = src.index("\n}\n", src.index("else -> when (exercise.pattern)", ms)) + 3
src = src[:ms] + "\n".join(m) + "\n" + src[me:]
open(KT, "w", encoding="utf-8").write(src)

print(f"skrev {len(P)} poser, {sum(1 for d in P.values() if d['end'])} animerte")
