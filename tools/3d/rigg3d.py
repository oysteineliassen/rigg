"""Rigg 3D: øvelsesfigurer laget med MakeHuman-modellen (CC0) i Blender (Cycles).

Oppsett: ./hent_modell.sh (modellen), og Blender som Python-modul: python3.11 -m venv b3d && b3d/bin/pip install bpy
Kjør:    b3d/bin/python rigg3d.py <øvelse> <ut-mappe> [bilder i animasjonen, 0 = tre stillbilder] [størrelse]
Sjekk:   SJEKK=1 b3d/bin/python rigg3d.py <øvelse> x   (måler om armene går inn i overkroppen)

Hver øvelse er en funksjon som setter figuren i stilling for s = 0..1 (start -> slutt) og sier hvor manualene
holdes. Rammeverket tar seg av kropp, muskelfarger (samme vekting som muskelkartet i appen), grep og lys.
"""
import json, math, os, sys
import bpy
from mathutils import Vector, Matrix, Quaternion

HERE = os.environ.get("MODELL") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "modell")
EX = sys.argv[1] if len(sys.argv) > 1 else "goblet_squat"
OUT = sys.argv[2] if len(sys.argv) > 2 else HERE
NFRAMES = int(sys.argv[3]) if len(sys.argv) > 3 else 0
SIZE = int(sys.argv[4]) if len(sys.argv) > 4 else 600


def ramp(x, a, b):
    return max(0.0, min(1.0, (x - a) / (b - a)))


# ======================================================================== KROPPEN
verts, faces, group = [], [], None
for line in open(os.path.join(HERE, "base.obj")):
    if line.startswith("v "):
        x, y, z = map(float, line.split()[1:4])
        verts.append([x, y, z])
    elif line.startswith("g "):
        group = line.split()[1]
    elif line.startswith("f ") and group == "body":
        faces.append([int(p.split("/")[0]) - 1 for p in line.split()[1:]])


def apply_target(fn, w):
    for line in open(os.path.join(HERE, fn)):
        if line.startswith("#") or not line.strip():
            continue
        p = line.split()
        i = int(p[0])
        verts[i][0] += float(p[1]) * w
        verts[i][1] += float(p[2]) * w
        verts[i][2] += float(p[3]) * w


apply_target("caucasian-male-young.target", 1.0)
apply_target("universal-male-young-maxmuscle-averageweight.target", 1.15)
apply_target("universal-male-young-maxmuscle-minweight.target", 0.45)
# Hver muskelgruppe for seg. Over 1,0 forvrenger formen (særlig leggene), så bein og legger holdes innenfor.
LOCAL = {"upperarm-muscle": 1.3, "upperarm-shoulder-muscle": 1.3, "lowerarm-muscle": 1.0,
         "upperleg-muscle": 1.0, "lowerleg-muscle": 0.55, "torso-muscle-pectoral": 0.5, "torso-muscle-dorsi": 1.2}
for fn in sorted(os.listdir(os.path.join(HERE, "lokal"))):
    key = fn.replace("-incr.target", "")
    key = key[2:] if key[:2] in ("l-", "r-") else key
    apply_target(os.path.join("lokal", fn), LOCAL[key])
apply_target("male-young-averagemuscle-averageweight-maxheight.target", 0.25)

# MakeHuman: Y opp, Z fram, desimeter -> Blender: Z opp, -Y fram, meter. Figurens venstre side er +X.
V = [Vector((x * 0.1, -z * 0.1, y * 0.1)) for x, y, z in verts]
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
mesh = bpy.data.meshes.new("kropp")
mesh.from_pydata([tuple(v) for v in V], [], faces)
mesh.update()
body = bpy.data.objects.new("kropp", mesh)
bpy.context.scene.collection.objects.link(body)

skel = json.load(open(os.path.join(HERE, "default.mhskel")))
J = {name: sum((V[i] for i in idx), Vector()) / len(idx) for name, idx in skel["joints"].items()}
weights = json.load(open(os.path.join(HERE, "default_weights.mhw")))["weights"]
normals = [Vector(v.normal) for v in mesh.vertices]
wmap = {}
for bone, lst in weights.items():
    for vi, w in lst:
        wmap.setdefault(vi, {})[bone] = w


def bone_w(vi, *prefixes):
    return sum(w for b, w in wmap.get(vi, {}).items() if b.startswith(prefixes))


def jh(name):
    return J[name + "____head"]


# ======================================================================== MUSKLER
# Hver muskelgruppe som et område på huden i hvilestilling (0..1), ut fra hvilke ben huden følger, hvor den
# sitter og hvilken vei den vender. Samme grupper som i appen (Muscle i Models.kt).
FWD, BACK = Vector((0, -1, 0)), Vector((0, 1, 0))
z_pelvis, z_waist = jh("spine05").z, jh("spine04").z
z_shorts = z_pelvis + (z_waist - z_pelvis) * 0.75
z_breast = jh("breast.L").z
z_clav = jh("clavicle.L").z
z_neck = jh("neck01").z
sh_x = abs(jh("upperarm01.L").x)


def limb_t(v, a, b):
    ax = b - a
    L = ax.length
    t = (v - a).dot(ax) / (L * L)
    perp = (v - a) - ax * t
    return t, perp


MUSCLES = {k: [0.0] * len(V) for k in ("QUADS", "GLUTES_HAMS", "CALVES", "CHEST", "ABS", "BACK", "SHOULDERS",
                                        "BICEPS", "TRICEPS")}
shorts = [0.0] * len(V)
for vi, v in enumerate(V):
    n = normals[vi]
    front, back = max(0.0, n.dot(FWD)), max(0.0, n.dot(BACK))
    sx = 1 if v.x > 0 else -1
    S = "L" if sx > 0 else "R"
    armw = bone_w(vi, "upperarm", "lowerarm", "wrist", "finger", "metacarpal", "clavicle", "shoulder")
    thigh = bone_w(vi, "upperleg")
    shin = bone_w(vi, "lowerleg")
    t_th, perp_th = limb_t(v, jh(f"upperleg01.{S}"), jh(f"lowerleg01.{S}"))
    # Shortsen
    if armw < 0.2 and v.z < z_shorts and (t_th < 0.34 or thigh < 0.5) and v.z > jh(f"lowerleg01.{S}").z + 0.05:
        shorts[vi] = 1.0
    if shorts[vi]:
        continue
    # Quads: dråpeform på forsiden av låret
    lat = perp_th.x * sx
    half = 0.066 * math.sin(math.pi * min(max(t_th, 0), 1) ** 0.75) + 0.014
    MUSCLES["QUADS"][vi] = min(1, thigh * 1.3) * ramp(front, 0.05, 0.4) * (1 - ramp(abs(lat - 0.006) / half, 0.75, 1.05)) \
        * ramp(t_th, 0.04, 0.16) * (1 - ramp(t_th, 0.86, 0.97))
    # Bakside lår (rumpa er under shortsen)
    MUSCLES["GLUTES_HAMS"][vi] = min(1, thigh * 1.3) * ramp(back, 0.25, 0.65) * ramp(t_th, 0.12, 0.3) * (1 - ramp(t_th, 0.85, 0.95))
    # Legg: baksiden, øvre del
    t_sh, _ = limb_t(v, jh(f"lowerleg01.{S}"), jh(f"foot.{S}"))
    MUSCLES["CALVES"][vi] = min(1, shin * 1.3) * ramp(back, 0.2, 0.55) * ramp(t_sh, 0.06, 0.16) * (1 - ramp(t_sh, 0.5, 0.65))
    # Overkroppen (ikke armer)
    if armw < 0.35:
        inner = 1 - ramp(abs(v.x), sh_x - 0.06, sh_x - 0.02)
        MUSCLES["CHEST"][vi] = ramp(front, 0.3, 0.6) * ramp(v.z, z_breast - 0.11, z_breast - 0.07) * \
            (1 - ramp(v.z, z_clav - 0.04, z_clav - 0.01)) * inner
        MUSCLES["ABS"][vi] = ramp(front, 0.3, 0.6) * ramp(v.z, z_shorts + 0.01, z_shorts + 0.03) * \
            (1 - ramp(v.z, z_breast - 0.12, z_breast - 0.09)) * (1 - ramp(abs(v.x), 0.075, 0.095))
        MUSCLES["BACK"][vi] = ramp(back, 0.25, 0.55) * ramp(v.z, z_waist - 0.02, z_waist + 0.03) * \
            (1 - ramp(v.z, z_neck - 0.04, z_neck)) * inner
    # Armer
    up = bone_w(vi, f"upperarm", f"shoulder01")
    t_ua, _ = limb_t(v, jh(f"upperarm01.{S}"), jh(f"lowerarm01.{S}"))
    near_sh = (v - jh(f"upperarm01.{S}")).length
    # Deltamuskelen: kula ytterst på skulderen (utside og overside), ikke rundt hele armen
    cap = n.dot(Vector((sx, 0.0, 0.55)).normalized())
    MUSCLES["SHOULDERS"][vi] = min(1, up * 1.4) * (1 - ramp(near_sh, 0.075, 0.11)) * ramp(cap, 0.1, 0.45) * \
        (1 - ramp(v.z, z_clav + 0.03, z_clav + 0.06))
    # Biceps/triceps: muskelbuk på for-/baksiden av overarmen, smal mot albuen
    ua_c = jh(f"upperarm01.{S}").lerp(jh(f"lowerarm01.{S}"), t_ua)
    ua_n = (v - ua_c)
    ua_n = ua_n.normalized() if ua_n.length > 1e-6 else Vector()
    bell = math.sin(math.pi * min(max((t_ua - 0.28) / 0.66, 0), 1)) ** 0.7
    MUSCLES["BICEPS"][vi] = min(1, up * 1.3) * ramp(ua_n.dot(FWD), 0.35 + 0.35 * (1 - bell), 0.75 + 0.2 * (1 - bell)) * bell
    MUSCLES["TRICEPS"][vi] = min(1, up * 1.3) * ramp(ua_n.dot(BACK), 0.3 + 0.35 * (1 - bell), 0.7 + 0.2 * (1 - bell)) * bell

# Samme vekting som MuscleWeights.kt (for øvelsene med 3D-figur). >= 0,75 mye (rød), >= 0,4 middels (oransje).
WEIGHTS = {
    "goblet_squat": dict(QUADS=1, GLUTES_HAMS=.6, ABS=.3, BACK=.2),
    "db_shoulder_press": dict(SHOULDERS=1, TRICEPS=.5, BACK=.2, ABS=.2),
    "db_curl": dict(BICEPS=1),
    "rdl_db": dict(GLUTES_HAMS=1, BACK=.5),
    "db_lunge": dict(QUADS=1, GLUTES_HAMS=.7),
    "db_lateral_raise": dict(SHOULDERS=1, BACK=.2),
}
mye, middels = [0.0] * len(V), [0.0] * len(V)
for m, w in WEIGHTS[EX].items():
    target = mye if w >= 0.75 else middels if w >= 0.4 else None
    if target is not None:
        for i, val in enumerate(MUSCLES[m]):
            target[i] = max(target[i], val)
for name, data in (("mye", mye), ("middels", middels), ("shorts", shorts)):
    a = mesh.attributes.new(name, "FLOAT", "POINT")
    a.data.foreach_set("value", data)
rest_attr = mesh.attributes.new("rest", "FLOAT_VECTOR", "POINT")
rest_attr.data.foreach_set("vector", [c for v in V for c in v])

# ======================================================================== SKJELETT
arm_data = bpy.data.armatures.new("skjelett")
arm = bpy.data.objects.new("skjelett", arm_data)
bpy.context.scene.collection.objects.link(arm)
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode="EDIT")
for name, b in skel["bones"].items():
    eb = arm_data.edit_bones.new(name)
    eb.head, eb.tail = J[b["head"]], J[b["tail"]]
    if (eb.tail - eb.head).length < 1e-4:
        eb.tail = eb.head + Vector((0, 0, 0.01))
for name, b in skel["bones"].items():
    if b["parent"]:
        arm_data.edit_bones[name].parent = arm_data.edit_bones[b["parent"]]
        arm_data.edit_bones[name].use_connect = False
bpy.ops.object.mode_set(mode="OBJECT")
# Deltamuskelen og toppen av overarmen skal følge overarmen, ikke skulderbladet. Med MakeHuman-vektene blir
# huden der liggende igjen når armen løftes over hodet, og skulderen krølles. Flytter derfor vekten fra
# skulderbladet/kragebeinet til overarmen gradvis utover fra skulderleddet.
DELT_A, DELT_B, DELT_K = (float(x) for x in os.environ.get("DELTVEKT", "-0.04,0.03,0.8").split(","))
for side in ("L", "R"):
    ua = skel["bones"][f"upperarm01.{side}"]
    h, d = J[ua["head"]], (J[ua["tail"]] - J[ua["head"]]).normalized()
    for vi, ws in wmap.items():
        g = ws.get(f"shoulder01.{side}", 0) + ws.get(f"clavicle.{side}", 0)
        k = DELT_K * ramp((V[vi] - h).dot(d), DELT_A, DELT_B)
        if g <= 0 or k <= 0:
            continue
        for b in (f"shoulder01.{side}", f"clavicle.{side}"):
            if b in ws:
                ws[b] *= 1 - k
        ws[f"upperarm01.{side}"] = ws.get(f"upperarm01.{side}", 0) + g * k
weights = {}
for vi, ws in wmap.items():
    for bone, w in ws.items():
        weights.setdefault(bone, []).append((vi, w))
for bone, lst in weights.items():
    vg = body.vertex_groups.new(name=bone)
    for vi, w in lst:
        vg.add([vi], w, "REPLACE")
mod = body.modifiers.new("skjelett", "ARMATURE")
mod.object = arm
mod.use_deform_preserve_volume = os.environ.get("DQ", "0") == "1"
body.parent = arm
bpy.context.view_layer.objects.active = body
bpy.ops.object.mode_set(mode="EDIT")
bpy.ops.mesh.select_all(action="DESELECT")
bpy.ops.mesh.select_loose()
bpy.ops.mesh.delete(type="VERT")
bpy.ops.object.mode_set(mode="OBJECT")
bpy.ops.object.shade_smooth()
sub = body.modifiers.new("glatt", "SUBSURF")
sub.levels = 1
sub.render_levels = 2
# Bare skjelettet trengs mens stillingen regnes ut; kroppen deformeres først ved rendering (raskere).
mod.show_viewport = False
sub.show_viewport = False

PB = arm.pose.bones
REST = {n: (arm_data.bones[n].head_local.copy(), arm_data.bones[n].tail_local.copy()) for n in arm_data.bones.keys()}


def upd():
    bpy.context.view_layer.update()


def rotate_world(pb, q, pivot):
    pb.matrix = Matrix.Translation(pivot) @ q.to_matrix().to_4x4() @ Matrix.Translation(-pivot) @ pb.matrix
    upd()


def rot(pb, axis, deg, pivot=None):
    rotate_world(pb, Matrix.Rotation(math.radians(deg), 4, axis).to_quaternion(), pivot or pb.head.copy())


def aim(pb, point_now, point_want):
    h = pb.head.copy()
    rotate_world(pb, (point_now - h).rotation_difference(point_want - h), h)


def reset():
    for pb in PB:
        pb.matrix_basis = Matrix.Identity(4)
    upd()


def two_bone(a, c, l1, l2, hint):
    """Midtleddet (kne/albue) mellom a og c med lengdene l1 og l2, bøyd mot [hint]."""
    d = c - a
    L = min(d.length, l1 + l2 - 1e-5)
    x = (l1 * l1 - l2 * l2 + L * L) / (2 * L)
    h = math.sqrt(max(l1 * l1 - x * x, 0))
    u = d.normalized()
    side = u.cross(hint).cross(u).normalized()
    return a + u * x + side * h


def move_root(delta):
    m = PB["root"].matrix.copy()
    m.translation += delta
    PB["root"].matrix = m
    upd()


def lean(deg, spread=(0.6, 0.3, 0.1), head=0.55):
    """Overkroppen fram (positiv). [spread]: hvor mye nederst, midt og øverst i ryggen (rett rygg = alt nederst).
    [head]: hvor mye hodet løftes tilbake (0 = følger ryggen, 1 = ser rett fram)."""
    X = Vector((1, 0, 0))
    for bone, k in zip(("spine05", "spine03", "spine01"), spread):
        if k:
            rot(PB[bone], X, deg * k)
    rot(PB["neck01"], X, -deg * head)


def leg(side, ankle, hint=Vector((0, -1, 0)), foot_dir=None):
    """Beinet: kneet regnes ut, ankelen havner på [ankle], foten peker [foot_dir] (standard: flatt som i hvile)."""
    hip = PB[f"upperleg01.{side}"].head.copy()
    knee_r = REST[f"lowerleg01.{side}"][0]
    l1 = (knee_r - REST[f"upperleg01.{side}"][0]).length
    l2 = (REST[f"foot.{side}"][0] - knee_r).length
    knee = two_bone(hip, ankle, l1, l2, hint)
    aim(PB[f"upperleg01.{side}"], PB[f"lowerleg01.{side}"].head.copy(), knee)
    aim(PB[f"lowerleg01.{side}"], PB[f"foot.{side}"].head.copy(), ankle)
    # Foten: peker [d], uten å vri seg rundt (fotsålen ned, sidene vannrett som i hvilestilling).
    ft = PB[f"foot.{side}"]
    d = (foot_dir if foot_dir is not None else (REST[f"foot.{side}"][1] - REST[f"foot.{side}"][0])).normalized()
    Rf = ft.matrix.to_3x3() @ arm_data.bones[f"foot.{side}"].matrix_local.to_3x3().inverted()
    f_now = (Rf @ (REST[f"foot.{side}"][1] - REST[f"foot.{side}"][0])).normalized()
    x_now = (Rf @ Vector((1, 0, 0)))
    x_now = (x_now - f_now * x_now.dot(f_now)).normalized()
    x_want = Vector((1, 0, 0))
    x_want = (x_want - d * x_want.dot(d)).normalized()
    B = Matrix((f_now, x_now, f_now.cross(x_now))).transposed()
    D = Matrix((d, x_want, d.cross(x_want))).transposed()
    rotate_world(ft, (D @ B.transposed()).to_quaternion(), ft.head.copy())


def toes_flat(side):
    """Tærne bøyes opp så de ligger flatt mot gulvet (når hælen er løftet)."""
    for pb in PB:
        if pb.name.startswith("toe") and pb.name.endswith("-1." + side):
            L = (REST[pb.name][1] - REST[pb.name][0]).length
            d = Vector((0, -1, 0))
            aim(pb, pb.tail.copy(), pb.head + d * L)


# ======================================================================== HENDER OG GREP
FINGER_R = 0.011


def palm_rest(side, sx):
    a = REST[f"finger2-1.{side}"][0] - REST[f"wrist.{side}"][0]
    b = REST[f"finger5-1.{side}"][0] - REST[f"finger2-1.{side}"][0]
    n = a.cross(b).normalized()
    return -n if n.x * sx > 0 else n


def hand_R(side):
    w = PB[f"wrist.{side}"]
    return w.matrix.to_3x3() @ arm_data.bones[f"wrist.{side}"].matrix_local.to_3x3().inverted()


def orient_hand(side, sx, f_want, n_want):
    """Snur hånda så fingrene peker [f_want] og håndflata vender [n_want]."""
    w = PB[f"wrist.{side}"]
    R = hand_R(side)
    f_now = (R @ (REST[f"finger3-1.{side}"][0] - REST[f"wrist.{side}"][0])).normalized()
    n_now = (R @ palm_rest(side, sx)).normalized()
    f_want = f_want.normalized()
    n_want = (n_want - f_want * n_want.dot(f_want)).normalized()
    n_now = (n_now - f_now * n_now.dot(f_now)).normalized()
    B = Matrix((f_now, n_now, f_now.cross(n_now))).transposed()
    D = Matrix((f_want, n_want, f_want.cross(n_want))).transposed()
    rotate_world(w, (D @ B.transposed()).to_quaternion(), w.head.copy())


def palm_center(side):
    w, f3 = PB[f"wrist.{side}"].head, PB[f"finger3-1.{side}"].head
    return w + (f3 - w) * 0.55


def grip(side, sx, c, r, n, f, elbow_hint):
    """Hånda rundt et håndtak/hode med senter [c] og radius [r]: håndflata vender [n] (mot håndtaket),
    fingrene peker [f] før de bøyes rundt."""
    n, f = n.normalized(), f.normalized()
    sh = PB[f"upperarm01.{side}"].head.copy()
    hand_len = (REST[f"finger3-1.{side}"][0] - REST[f"wrist.{side}"][0]).length
    palm = c - n * (r + 0.016)
    wrist = palm - f * hand_len * 0.55
    el_r = REST[f"lowerarm01.{side}"][0]
    l1 = (el_r - REST[f"upperarm01.{side}"][0]).length
    l2 = (REST[f"wrist.{side}"][0] - el_r).length
    elbow = two_bone(sh, wrist, l1, l2, elbow_hint)
    # Skulderbladet og kragebeinet følger med når armen løftes (ca. 1/3 av bevegelsen), ellers knekker
    # skulderen ned. Bare den delen av løftet som går over ca. 30 grader fra kroppen.
    sp = PB[f"shoulder01.{side}"]
    d_now = (PB[f"lowerarm01.{side}"].head - PB[f"upperarm01.{side}"].head).normalized()
    d_want = (elbow - sh).normalized()
    raise_deg = math.degrees(math.acos(max(-1, min(1, d_want.dot(Vector((0, 0, -1)))))))
    k = ramp(raise_deg, 30, 90)
    if k > 0:
        q = d_now.rotation_difference(d_want)
        for bone, share in GIRDLE:
            b = PB[f"{bone}.{side}"]
            rotate_world(b, Quaternion().slerp(q, share * k), b.head.copy())
        sh = PB[f"upperarm01.{side}"].head.copy()
        elbow = two_bone(sh, wrist, l1, l2, elbow_hint)
    aim(PB[f"upperarm01.{side}"], PB[f"lowerarm01.{side}"].head.copy(), elbow)
    if k > 0 and TWIST:
        rot(PB[f"upperarm01.{side}"], (elbow - sh).normalized(), TWIST * sx * k, sh)
    aim(PB[f"lowerarm01.{side}"], PB[f"wrist.{side}"].head.copy(), wrist)
    orient_hand(side, sx, f, n)
    return palm_center(side) + n * (r + 0.016)   # der håndtaket faktisk havner


# Overarmen roteres litt utover når den løftes høyt (som i virkeligheten), så deltamuskelen legger seg over skulderen.
TWIST = float(os.environ.get("TWIST", 20))
# Hvor mye av armløftet kragebeinet og skulderen tar (resten tar overarmen). Kan prøves med SKULDERBLAD=...
GIRDLE = [(b, float(k)) for b, k in (x.split(":") for x in os.environ.get("SKULDERBLAD", "clavicle:0.25,shoulder01:0.15").split(","))]


def inside(p, shapes):
    for c, a, r, h in shapes:
        d = p - c
        t = d.dot(a)
        if abs(t) < h + FINGER_R and (d - a * t).length < r + FINGER_R:
            return True
    return False


def grasp(shapes):
    """Bøyer fingrene ett lite steg om gangen til de ligger an mot utstyret (ikke gjennom)."""
    for side, sx in (("L", 1), ("R", -1)):
        R = hand_R(side)
        palm = palm_rest(side, sx)
        for f, limit in ((2, 100), (3, 100), (4, 100), (5, 100), (1, 85)):
            chain = [PB[f"finger{f}-{k}.{side}"] for k in (1, 2, 3)]
            for i, pb in enumerate(chain):
                d = (REST[pb.name][1] - REST[pb.name][0]).normalized()
                q = Matrix.Rotation(math.radians(5), 4, (R @ d.cross(palm)).normalized()).to_quaternion()
                def hits():
                    pts = []
                    for k in chain[i:]:
                        pts += [k.tail.copy(), (k.head + k.tail) / 2]
                    return any(inside(p, shapes) for p in pts)

                # Starter fingeren delvis inni håndtaket, bøyes den videre til den er ute igjen og ligger an.
                was_inside = hits()
                bent = 0
                while bent < limit:
                    before = pb.matrix.copy()
                    rotate_world(pb, q, pb.head.copy())
                    now = hits()
                    if now and not was_inside:
                        pb.matrix = before
                        upd()
                        break
                    was_inside = was_inside and now
                    bent += 5


# ======================================================================== UTSTYR
def mat(name, rgb, rough=0.5, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*rgb, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    return m, b


rubber, _ = mat("gummi", (0.03, 0.03, 0.035), 0.6, 0.0)
chrome, _ = mat("krom", (0.75, 0.75, 0.78), 0.18, 1.0)
HEAD_R, HEAD_H, HANDLE_R, HANDLE_L = 0.06, 0.1, 0.017, 0.155
DB_OFF = HANDLE_L / 2 + HEAD_H / 2


class Dumbbell:
    """Manual med sekskantede gummihoder og blankt håndtak. Plasseres med senter (midt på håndtaket) og akse."""
    def __init__(self, name):
        bpy.ops.mesh.primitive_cylinder_add(radius=HANDLE_R, depth=HANDLE_L + 0.02)
        h = bpy.context.object
        h.data.materials.append(chrome)
        parts = [h]
        for z in (DB_OFF, -DB_OFF):
            bpy.ops.mesh.primitive_cylinder_add(vertices=6, radius=HEAD_R, depth=HEAD_H, location=(0, 0, z))
            p = bpy.context.object
            bev = p.modifiers.new("kant", "BEVEL"); bev.width = 0.008; bev.segments = 3
            p.data.materials.append(rubber)
            parts.append(p)
        for p in parts:
            bpy.ops.object.select_all(action="DESELECT")
            p.select_set(True)
            bpy.context.view_layer.objects.active = p
            bpy.ops.object.shade_smooth()
        self.root = bpy.data.objects.new(name, None)
        bpy.context.scene.collection.objects.link(self.root)
        for p in parts:
            p.parent = self.root
        self.c, self.a = Vector(), Vector((0, 0, 1))

    def place(self, center, axis):
        self.c, self.a = center.copy(), axis.normalized()
        self.root.location = center
        self.root.rotation_mode = "QUATERNION"
        self.root.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(self.a)

    def shapes(self):
        a = self.a
        return [(self.c, a, HANDLE_R, HANDLE_L / 2 + 0.01),
                (self.c + a * DB_OFF, a, HEAD_R * 0.93, HEAD_H / 2),
                (self.c - a * DB_OFF, a, HEAD_R * 0.93, HEAD_H / 2)]


# ======================================================================== ØVELSENE
X, Y, Z = Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))
SIDES = (("L", 1), ("R", -1))


def arm_lengths(side):
    el = REST[f"lowerarm01.{side}"][0]
    l1 = (el - REST[f"upperarm01.{side}"][0]).length
    l2 = (REST[f"wrist.{side}"][0] - el).length
    hand = (REST[f"finger3-1.{side}"][0] - REST[f"wrist.{side}"][0]).length
    return l1, l2, hand


def ankle_rest(side):
    return REST[f"foot.{side}"][0].copy()


def ex_goblet_squat(s):
    e = s * s * (3 - 2 * s)
    move_root(Vector((0, 0.17 * e, -0.40 * e)))
    lean(38 * e)
    for side, sx in SIDES:
        leg(side, ankle_rest(side), Vector((0.24 * sx * e, -1, 0)))
    # Goblet: manualen loddrett foran brystet, hendene rundt sidene på det øverste hodet, fingrene over toppen.
    neck = PB["neck01"].head.copy()
    fb = (PB["spine01"].tail - PB["spine01"].head).cross(X).normalized()
    if fb.y > 0:
        fb = -fb
    head_c = neck + fb * 0.24 + Vector((0, 0, -0.055))
    got = []
    for side, sx in SIDES:
        got.append(grip(side, sx, head_c + Vector((0, 0, -0.01)), HEAD_R * 0.93,
                        Vector((-sx, -0.05, 0.25)), Vector((-0.2 * sx, -0.1, 1)), Vector((0.5 * sx, 0.1, -1))))
    mid = (got[0] + got[1]) / 2
    DB[0].place(mid + Vector((0, 0, 0.012)) - Z * DB_OFF, Z)
    DB[1].root.hide_render = True
    return [DB[0]]


def ex_db_shoulder_press(s):
    e = s * s * (3 - 2 * s)
    used = []
    for (side, sx), db in zip(SIDES, DB):
        sh = PB[f"upperarm01.{side}"].head.copy()
        l1, l2, hand = arm_lengths(side)
        # Start: albuene ut til siden i omtrent skulderhøyde, underarmene loddrett, manualene ved hodet.
        elbow0 = sh + Vector((0.24 * sx, -0.07, -0.05))
        low = elbow0 + Vector((-0.015 * sx, 0, l2 + hand * 0.55))
        # Topp: strake armer, manualene nesten inntil hverandre over hodet. Målet ligger litt over det armen
        # rekker fra hvilestillingen, siden skulderen løftes med skulderbladet; armen strekkes da helt ut.
        high = Vector((0.2 * sx, sh.y - 0.04, sh.z + (l1 + l2 + hand * 0.5) * 1.15))
        c = low.lerp(high, e)
        at = grip(side, sx, c, HANDLE_R, Vector((0, -1, 0.1)), Vector((0, -0.1, 1)), Vector((sx, 0.2, -0.25)))
        db.place(at, X)
        used.append(db)
    return used


def ex_db_curl(s):
    e = s * s * (3 - 2 * s)
    used = []
    for (side, sx), db in zip(SIDES, DB):
        l1, l2, hand = arm_lengths(side)
        sh = PB[f"upperarm01.{side}"].head.copy()
        elbow = sh + Vector((0.09 * sx, 0.0, -l1 * 0.98))
        th = math.radians(8 + 132 * e)            # underarmen fra nesten rett ned til opp mot skulderen
        d = Vector((0.03 * sx, -math.sin(th), -math.cos(th))).normalized()
        n = Vector((0, -math.cos(th), math.sin(th)))   # håndflata fram nede, opp mot ansiktet øverst
        c = elbow + d * (l2 + hand * 0.55) + n * (HANDLE_R + 0.016)
        at = grip(side, sx, c, HANDLE_R, n, d, Vector((0, 1, -0.2)))
        db.place(at, X)
        used.append(db)
    return used


def ex_rdl_db(s):
    e = s * s * (3 - 2 * s)
    move_root(Vector((0, 0.16 * e, -0.07 * e)))
    # Hoftebøy: rett (litt spent) rygg, bøyningen kommer fra hofta. Hodet følger ryggen og ser litt fram.
    lean(72 * e, spread=(1.04, -0.04, 0.0), head=0.35)
    for side, sx in SIDES:
        leg(side, ankle_rest(side), Vector((0, -1, 0)))
    used = []
    for (side, sx), db in zip(SIDES, DB):
        l1, l2, hand = arm_lengths(side)
        sh = PB[f"upperarm01.{side}"].head.copy()
        # Armene henger rett ned fra skuldrene, manualene tett langs beina.
        c = sh + Vector((0.02 * sx, -0.02 - 0.08 * (1 - e), -(l1 + l2 + hand * 0.5) * 0.96))
        at = grip(side, sx, c, HANDLE_R, Vector((0, 1, 0)), Vector((0, 0, -1)), Vector((sx, 0.4, 0)))
        db.place(at, X)
        used.append(db)
    return used


def ex_db_lunge(s):
    e = s * s * (3 - 2 * s)
    # Utfall bakover: venstre fot står, høyre går et langt steg bak og kneet nesten ned i gulvet.
    move_root(Vector((0, 0.2 * e, -0.36 * e)))
    lean(6 * e)
    leg("L", ankle_rest("L") + Vector((0, -0.04 * e, 0)), Vector((0, -1, 0)))
    ar = ankle_rest("R")
    # Bakfoten: hælen løftes, foten peker bratt ned med fotballen i gulvet og tærne flatt.
    back_ankle = ar + Vector((0, 0.72 * e, 0.05 * e))
    rest_dir = (REST["foot.R"][1] - REST["foot.R"][0]).normalized()
    steep = Vector((0, -0.42, -0.9)).normalized()
    leg("R", back_ankle, Vector((0, -1, -0.3)), foot_dir=rest_dir.lerp(steep, e))
    toes_flat("R")
    used = []
    for (side, sx), db in zip(SIDES, DB):
        l1, l2, hand = arm_lengths(side)
        sh = PB[f"upperarm01.{side}"].head.copy()
        c = sh + Vector((0.1 * sx, 0.0, -(l1 + l2 + hand * 0.5) * 0.96))
        at = grip(side, sx, c, HANDLE_R, Vector((-sx, 0, 0)), Vector((0, 0, -1)), Vector((0, 1, 0)))
        db.place(at, Y)
        used.append(db)
    return used


def ex_db_lateral_raise(s):
    e = s * s * (3 - 2 * s)
    used = []
    for (side, sx), db in zip(SIDES, DB):
        l1, l2, hand = arm_lengths(side)
        sh = PB[f"upperarm01.{side}"].head.copy()
        a = math.radians(12 + 76 * e)
        d = Vector((sx * math.sin(a), -0.08, -math.cos(a))).normalized()
        reach = (l1 + l2 + hand * 0.5) * 0.95
        n = Vector((-sx * math.cos(a), 0, -math.sin(a)))   # håndflata inn mot låret nede, ned mot gulvet oppe
        c = sh + d * reach
        at = grip(side, sx, c, HANDLE_R, n, d, Vector((0, 0.6, 0.4)))
        db.place(at, Y)
        used.append(db)
    return used


EXERCISES = {
    # navn: (funksjon, kameravinkel fra forsiden, høyde over midten[, midtpunkt i høyden, avstand])
    "goblet_squat": (ex_goblet_squat, 48, 1.25),
    "db_shoulder_press": (ex_db_shoulder_press, 35, 0.9, 0.6, 4.4),
    "db_curl": (ex_db_curl, 55, 0.9),
    "rdl_db": (ex_rdl_db, 70, 1.0),
    "db_lunge": (ex_db_lunge, 72, 0.9),
    "db_lateral_raise": (ex_db_lateral_raise, 20, 0.9),
}

# ======================================================================== MATERIALE, LYS, KAMERA
skin, sb = mat("hud", (0.34, 0.335, 0.35), 0.5)
nt = skin.node_tree
N = nt.nodes.new


def attr(name):
    n = N("ShaderNodeAttribute"); n.attribute_name = name; return n


def sramp(inp, a, b):
    r = N("ShaderNodeMapRange"); r.inputs["From Min"].default_value = a; r.inputs["From Max"].default_value = b
    nt.links.new(inp, r.inputs["Value"]); return r.outputs["Result"]


def mix(fac, A, B):
    m = N("ShaderNodeMix"); m.data_type = "RGBA"
    for k, v in (("A", A), ("B", B)):
        if isinstance(v, tuple):
            m.inputs[k].default_value = v
        else:
            nt.links.new(v, m.inputs[k])
    if isinstance(fac, float):
        m.inputs["Factor"].default_value = fac
    else:
        nt.links.new(fac, m.inputs["Factor"])
    return m.outputs["Result"]


rp = attr("rest")
sep = N("ShaderNodeSeparateXYZ"); nt.links.new(rp.outputs["Vector"], sep.inputs["Vector"])
comb = N("ShaderNodeCombineXYZ")
nt.links.new(sep.outputs["X"], comb.inputs["X"]); nt.links.new(sep.outputs["Y"], comb.inputs["Y"])
zs = N("ShaderNodeMath"); zs.operation = "MULTIPLY"; zs.inputs[1].default_value = 0.08
nt.links.new(sep.outputs["Z"], zs.inputs[0]); nt.links.new(zs.outputs["Value"], comb.inputs["Z"])
wave = N("ShaderNodeTexWave"); wave.wave_type = "BANDS"; wave.bands_direction = "X"
wave.inputs["Scale"].default_value = 70; wave.inputs["Distortion"].default_value = 6; wave.inputs["Detail"].default_value = 3
nt.links.new(comb.outputs["Vector"], wave.inputs["Vector"])
fib = sramp(wave.outputs["Fac"], 0.1, 0.9)
red = mix(fib, (0.42, 0.03, 0.02, 1), (0.72, 0.07, 0.04, 1))
orange = mix(fib, (0.6, 0.16, 0.04, 1), (0.88, 0.32, 0.08, 1))
skin_col = (0.34, 0.335, 0.35, 1)
c1 = mix(sramp(attr("middels").outputs["Fac"], 0.3, 0.5), skin_col, orange)
c2 = mix(sramp(attr("mye").outputs["Fac"], 0.3, 0.5), c1, red)
geo = N("ShaderNodeNewGeometry")
cav = sramp(geo.outputs["Pointiness"], 0.485, 0.515)
c3 = mix(cav, (0.0, 0.0, 0.0, 1), c2)
c3b = mix(0.7, c2, c3)
c4 = mix(sramp(attr("shorts").outputs["Fac"], 0.45, 0.55), c3b, (0.012, 0.012, 0.014, 1))
nt.links.new(c4, sb.inputs["Base Color"])
sb.inputs["Subsurface Weight"].default_value = 0.06
body.data.materials.append(skin)

DB = [Dumbbell("manual_v"), Dumbbell("manual_h")]

floor_m, _ = mat("gulv", (0.018, 0.018, 0.021), 0.9)
bpy.ops.mesh.primitive_plane_add(size=60, location=(0, 0, 0))
floor = bpy.context.object
floor.data.materials.append(floor_m)
BODY_N = 13380   # de første punktene i base.obj er selve kroppen (resten er hjelpegeometri)
FEET_Z = min(v.z for v in V[:BODY_N])
TOP_Z = max(v.z for v in V[:BODY_N])
floor.location.z = FEET_Z
floor.is_shadow_catcher = True

sc = bpy.context.scene
sc.render.engine = "CYCLES"
sc.cycles.samples = 96
sc.cycles.use_denoising = True
sc.render.resolution_x = sc.render.resolution_y = SIZE
sc.render.film_transparent = True
sc.world = bpy.data.worlds.new("verden")
sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.012, 0.012, 0.014, 1)
sc.view_settings.view_transform = "Standard"


def light(name, loc, energy, size, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, "AREA")
    ld.energy, ld.size, ld.color = energy, size, color
    lo = bpy.data.objects.new(name, ld)
    lo.location = loc
    sc.collection.objects.link(lo)
    c = lo.constraints.new("TRACK_TO"); c.target = body; c.track_axis = "TRACK_NEGATIVE_Z"


fn, cam_ang, cam_up, *rest_cam = EXERCISES[EX]
cam_mid = rest_cam[0] if rest_cam else 0.5
cam_dist = rest_cam[1] if len(rest_cam) > 1 else 3.9
cam_ang = float(os.environ.get("KAMERA", cam_ang))
a = math.radians(cam_ang)
# Lyset følger kameraet, så figuren blir lyssatt likt uansett vinkel.
light("hoved", Vector((3.4 * math.sin(a + 0.6), -3.4 * math.cos(a + 0.6), 3.2)), 420, 2.5)
light("fyll", Vector((3.2 * math.sin(a - 1.1), -3.2 * math.cos(a - 1.1), 1.4)), 90, 3.0, (0.85, 0.9, 1.0))
light("kant", Vector((3.0 * math.sin(a + 3.0), -3.0 * math.cos(a + 3.0), 2.4)), 520, 1.5, (0.8, 0.85, 1.0))

cam_d = bpy.data.cameras.new("kamera")
cam = bpy.data.objects.new("kamera", cam_d)
sc.collection.objects.link(cam)
sc.camera = cam
cam_d.lens = 60
dist = cam_dist
center = Vector((0, 0.08, FEET_Z + (TOP_Z - FEET_Z) * cam_mid))
cam.location = center + Vector((dist * math.sin(a), -dist * math.cos(a), cam_up))
tgt = bpy.data.objects.new("mål", None); tgt.location = center
sc.collection.objects.link(tgt)
tc = cam.constraints.new("TRACK_TO"); tc.target = tgt; tc.track_axis = "TRACK_NEGATIVE_Z"


def pose(s):
    reset()
    for db in DB:
        db.root.hide_render = False
    return fn(s)


def shot(s, path):
    used = pose(s)
    shapes = []
    for db in used:
        shapes += db.shapes()
    grasp(shapes)
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)


def check_arms():
    """Måler om armer og hender går inn i overkroppen (cm), for start, midt og slutt."""
    from mathutils.bvhtree import BVHTree
    mod.show_viewport = True
    ARMS = ("upperarm", "lowerarm", "wrist", "finger", "metacarpal")
    TORSO = ("spine", "breast", "clavicle", "pelvis", "root", "neck")
    for s in (0.0, 0.5, 1.0):
        pose(s)
        ev = body.evaluated_get(bpy.context.evaluated_depsgraph_get())
        me = ev.to_mesh()
        vs = [v.co.copy() for v in me.vertices]
        tf = [list(p.vertices) for p in me.polygons if all(bone_w(i, *TORSO) > 0.6 for i in p.vertices)]
        bvh = BVHTree.FromPolygons(vs, tf)
        worst, by = 0.0, {}
        for i, v in enumerate(vs):
            if bone_w(i, *ARMS) < 0.8:
                continue
            loc, nor, _, dist = bvh.find_nearest(v)
            if loc is None or dist > 0.08:
                continue
            depth = (loc - v).dot(nor)
            if depth > 0.003:
                top = max(wmap[i].items(), key=lambda kv: kv[1])[0].split(".")[0]
                by[top] = max(by.get(top, 0), round(depth * 100, 1))
                worst = max(worst, depth)
        ev.to_mesh_clear()
        print(f"KOLLISJON {EX} s={s}: dypest {worst * 100:.1f} cm {by}")
    mod.show_viewport = False


if os.environ.get("LEDD"):
    for s in (0.0, 0.5, 1.0):
        pose(s)
        print("LEDD", s, {n: tuple(round(x, 3) for x in PB[n].head) for n in ("clavicle.L", "shoulder01.L", "upperarm01.L", "lowerarm01.L", "wrist.L")})
elif os.environ.get("SJEKK"):
    check_arms()
elif not os.environ.get("BARE_OPPSETT"):
    if NFRAMES:
        for k in range(NFRAMES):
            s = (1 - math.cos(2 * math.pi * k / NFRAMES)) / 2
            shot(s, os.path.join(OUT, f"anim_{k:02d}.png"))
    else:
        for s, nm in ((0.0, "start"), (0.5, "midt"), (1.0, "slutt")):
            if nm in os.environ.get("BILDER", "start,midt,slutt"):
                shot(s, os.path.join(OUT, f"{EX}_{nm}.png"))
    print("ferdig")
