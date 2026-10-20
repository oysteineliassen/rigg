"""Lager muskelkartet i appen fra kilde.png (generert illustrasjon av kroppen forfra og bakfra, musklene oransje).

    python lag_muskelkart.py

Skriver to filer til app/src/main/res/drawable-nodpi:
  muscle_body.webp   bildet med musklene grå (utrent) og gjennomsiktig bakgrunn
  muscle_labels.webp for hver piksel: R = Muscle.ordinal + 1 (0 = ingen muskel), G = skygge, B = hvor mye som er muskel
Appen (MuscleMap.kt) farger musklene etter belastning med disse.

Krever Pillow, numpy og scipy. Hver oransje flate i bildet kobles til en muskelgruppe ut fra hvor midtpunktet ligger
(se muskel()). Byttes kildebildet, må den sjekkes på nytt.
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage

HER = os.path.dirname(os.path.abspath(__file__))
UT = os.path.join(HER, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
# Samme rekkefølge som enum class Muscle i Models.kt.
MUSKLER = ["CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "QUADS", "GLUTES_HAMS", "CALVES", "ABS"]
BREDDE = 760


def muskel(x, y, midt_front, midt_bak):
    """Muskelgruppe for en oransje flate med midtpunkt (x, y). Forfra står til venstre, bakfra til høyre;
    midt_* er midtlinja i hver figur. Avstanden fra midtlinja skiller skulder/bryst og arm/mage."""
    front = abs(x - midt_front) < abs(x - midt_bak)
    av = abs(x - (midt_front if front else midt_bak))
    if y < 300:
        return "SHOULDERS" if av > 85 else ("CHEST" if front else "BACK")
    if y < 520:
        if av > 100:
            return "BICEPS" if front else "TRICEPS"
        return "ABS" if front else "BACK"
    if y < 760:
        return "QUADS" if front else "GLUTES_HAMS"
    return "CALVES"


def main():
    im = np.asarray(Image.open(os.path.join(HER, "kilde.png")).convert("RGB")).astype(float)
    r, g, b = im[..., 0], im[..., 1], im[..., 2]
    h, w = r.shape
    kjerne = (r > 120) & (r - b > 80) & (g > 40)
    lab, n = ndimage.label(kjerne)
    gruppe = np.zeros(lab.shape, int)
    storrelse = ndimage.sum(kjerne, lab, range(1, n + 1))
    midt = ndimage.center_of_mass(kjerne, lab, range(1, n + 1))
    flater = [(i + 1, cx, cy) for i, ((cy, cx), st) in enumerate(zip(midt, storrelse)) if st > 300]
    # Midtlinja i hver figur: snittet av alle flatene på hver side av bildet (figurene er symmetriske).
    midt_front = np.mean([cx for _, cx, _ in flater if cx < w / 2])
    midt_bak = np.mean([cx for _, cx, _ in flater if cx >= w / 2])
    for i, cx, cy in flater:
        gruppe[lab == i] = MUSKLER.index(muskel(cx, cy, midt_front, midt_bak)) + 1
    # Myke kanter: hvor oransje pikselen er, og nærmeste muskel for kantpiksler.
    oransje = np.clip(((r - b) - 20) / 80, 0, 1) * (r > 60)
    dist, (iy, ix) = ndimage.distance_transform_edt(gruppe == 0, return_indices=True)
    alfa = np.where(dist <= 4, oransje, 0)
    gid = np.where(alfa > 0.02, gruppe[iy, ix], 0)
    alfa = np.where(gid > 0, alfa, 0)
    lys = (0.6 * r + 0.4 * g) / 255
    skygge = np.clip(lys / np.percentile(lys[gid > 0], 95), 0, 1.2)
    utrent = np.array([72, 70, 66], float)
    grunn = im * (1 - alfa[..., None]) + utrent * skygge[..., None] * alfa[..., None]
    # Bakgrunnen (nesten svart, sammenhengende med kantene) blir gjennomsiktig.
    mork = im.max(axis=2) < 34
    bl, _ = ndimage.label(mork)
    kant = set(np.unique(np.concatenate([bl[0], bl[-1], bl[:, 0], bl[:, -1]]))) - {0}
    a = ndimage.gaussian_filter(np.where(np.isin(bl, list(kant)), 0, 255).astype(float), 0.8).clip(0, 255).astype(np.uint8)
    ys, xs = np.where(a > 0)
    y0, y1, x0, x1 = max(0, ys.min() - 6), min(h, ys.max() + 7), max(0, xs.min() - 6), min(w, xs.max() + 7)
    kropp = np.dstack([grunn.clip(0, 255).astype(np.uint8), a])[y0:y1, x0:x1]
    etiketter = np.dstack([gid.astype(np.uint8), (skygge / 1.2 * 255).astype(np.uint8), (alfa * 255).astype(np.uint8)])[y0:y1, x0:x1]
    hoyde = int((y1 - y0) * BREDDE / (x1 - x0))
    Image.fromarray(kropp, "RGBA").resize((BREDDE, hoyde), Image.LANCZOS).save(
        os.path.join(UT, "muscle_body.webp"), "WEBP", quality=90, method=6)
    Image.fromarray(etiketter, "RGB").resize((BREDDE, hoyde), Image.NEAREST).save(
        os.path.join(UT, "muscle_labels.webp"), "WEBP", lossless=True, method=6)
    print(f"skrev {BREDDE} x {hoyde}")


if __name__ == "__main__":
    main()
