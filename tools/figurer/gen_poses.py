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
        for (fh, fl), ms, label in d["seq"]:
            out.append(f"            // {label}")
            out.append(f"            Key({ms}, Frame(")
            out.append(f"                head = {pp(fh)},")
            out.append("                lines = listOf(")
            out += seg_lines(fl, "                    ")
            out.append("                ),")
            out.append("            )),")
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
    ("pike_pushup",): "PIKE",
    ("dips_chair",): "DIP",
    ("band_triceps_pushdown",): "PUSHDOWN",
    ("db_lateral_raise",): "LATERAL_RAISE",
    ("band_lateral_raise",): "LATERAL_RAISE_BAND",
    # Trekk
    ("pullup", "chinup", "negative_pullup"): "PULLUP_HANG",
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
    ("ab_wheel_alt",): "PLANK",
    ("suitcase_carry",): "CARRY",
    # Kondisjon
    ("mountain_climber",): "MOUNTAIN_CLIMBER",
    ("jump_rope",): "JUMP_ROPE",
    ("jump_squat", "burpee"): "JUMP",
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
