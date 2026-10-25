"""Beskjærer rendrede rammer (gjennomsiktig bakgrunn) likt, legger dem på figurbakgrunnen og lagrer som WebP.

Bruk: python3 pakk.py <mappe med anim_NN.png> <ut-mappe>
"""
import glob, os, sys
from PIL import Image

SRC, DST = sys.argv[1], sys.argv[2]
SIZE = 480
BG = (38, 37, 35, 255)  # surfaceVariant i mørkt tema (0xFF262523), så figuren glir inn i flisene

frames = [Image.open(p).convert("RGBA") for p in sorted(glob.glob(os.path.join(SRC, "anim_*.png")))]
# Felles utsnitt for alle rammene (så figuren ikke hopper), kvadratisk og med litt luft rundt.
box = None
for im in frames:
    b = im.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
side = max(box[2] - box[0], box[3] - box[1]) * 1.08
crop = (int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2))
os.makedirs(DST, exist_ok=True)
for old in glob.glob(os.path.join(DST, "*.webp")):
    os.remove(old)
for i, im in enumerate(frames):
    # Utsnittet kan gå utenfor bildet: legg rammen på en bakgrunn som dekker hele utsnittet.
    bg = Image.new("RGBA", (crop[2] - crop[0], crop[3] - crop[1]), BG)
    bg.alpha_composite(im.crop(crop))
    bg.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(DST, f"{i:02d}.webp"), quality=82, method=6)
print(len(frames), "rammer ->", DST)
