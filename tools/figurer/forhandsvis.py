"""Lager forhåndsvisninger av figurene, så du kan se endringer uten å bygge appen.

    python forhandsvis.py                    -> alle.gif: alle animerte figurer i et rutenett
    python forhandsvis.py BOX_JUMP SQUAT     -> utvalg.gif: bare disse, store
    python forhandsvis.py --stripe BOX_JUMP  -> stripe.png: 10 stillbilder gjennom bevegelsen

Navnene er de samme som i IllustrationType (se Illustration.kt eller gen_poses.py).
Filene havner i mappen du står i.
"""
import sys

from PIL import Image, ImageDraw

from figurer import load, cycle_ms, solve_at, draw, font

poses, REP_MS = load()
args = sys.argv[1:]
stripe = "--stripe" in args
names = [a for a in args if not a.startswith("--")]
unknown = [n for n in names if n not in poses]
if unknown:
    sys.exit(f"Ukjent figur: {', '.join(unknown)}. Finnes: {', '.join(sorted(poses))}")

if stripe:
    names = names or ["BOX_JUMP"]
    size, n = 160, 10
    out = Image.new("RGB", (n * (size + 4), len(names) * (size + 20)), "white")
    d = ImageDraw.Draw(out)
    for r, name in enumerate(names):
        total = cycle_ms(poses[name], REP_MS)
        for c in range(n):
            t = int(total * c / n)
            y = r * (size + 20)
            out.paste(draw(poses[name], solve_at(poses[name], t, REP_MS), size), (c * (size + 4), y + 18))
            d.text((c * (size + 4) + 4, y + 2), f"{name} {t} ms", fill=(0, 0, 0), font=font(11))
    out.save("stripe.png")
    print("stripe.png")
    sys.exit()

grid = not names
names = names or [n for n, p in poses.items() if p["end"] or p["seq"]]
size, pad, fps = (140, 8, 20) if grid else (240, 16, 25)
cols = 8 if grid else len(names)
rows = (len(names) + cols - 1) // cols
label_h = 16 if grid else 0
total = max(cycle_ms(poses[n], REP_MS) for n in names)
frames = []
for i in range(int(total / 1000 * fps)):
    t = i * 1000 / fps
    sheet = Image.new("RGB", (cols * (size + pad) + pad, rows * (size + pad + label_h) + pad), "white")
    d = ImageDraw.Draw(sheet)
    for j, name in enumerate(names):
        x, y = pad + (j % cols) * (size + pad), pad + (j // cols) * (size + pad + label_h)
        sheet.paste(draw(poses[name], solve_at(poses[name], t, REP_MS), size), (x, y))
        if grid:
            d.text((x + 2, y + size + 2), name[:22], fill=(60, 60, 60), font=font(11))
    frames.append(sheet.convert("P", palette=Image.ADAPTIVE, colors=32))
out = "alle.gif" if grid else "utvalg.gif"
frames[0].save(out, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, optimize=True)
print(f"{out}: {len(names)} figurer, {len(frames)} bilder")
