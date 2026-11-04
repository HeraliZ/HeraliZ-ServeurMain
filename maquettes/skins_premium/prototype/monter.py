"""LIB d'essai : heralisz_camo.glsl du mod + prototypes 210 / 211 ; copie d'apercu.html avec rechargement simulé."""
import sys
from pathlib import Path
S = Path(sys.argv[1]); R = Path(sys.argv[2])
lib = (R / "modules/armes/src/client/resources/assets/minecraft/shaders/include/heralisz_camo.glsl").read_text(encoding="utf-8")
proto = (S / "proto/skins_premium.glsl").read_text(encoding="utf-8")
i = lib.index("vec3 skinSurface(vec3 p, out vec3 em) {")
lib = lib[:i] + proto + "\n" + lib[i:]
old = "#elif CAMO_ID == 209\n    return infernalIron(p, em);\n"
assert lib.count(old) == 1
lib = lib.replace(old, old + "#elif CAMO_ID == 210\n    { vec3 c = faille(p, em); return failleRecharge(p, c, em); }\n"
                  "#elif CAMO_ID == 211\n    { vec3 c = accesRouge(p, em); return accesRecharge(p, c, em); }\n")
(S / "proto/inclus.glsl").write_text(lib, encoding="utf-8")
(S / "proto/camo_210.fsh").write_text("#version 150\n#define CAMO_ID 210\n#moj_import <heralisz_camo.glsl>\n", encoding="utf-8")
(S / "proto/camo_211.fsh").write_text("#version 150\n#define CAMO_ID 211\n#moj_import <heralisz_camo.glsl>\n", encoding="utf-8")
# apercu.html : chargeur déplacé (rechargement simulé), uniforme CamoReload, boîte du chargeur à l'écran
h = (R / "modules/armes/outils/apercu_camos/apercu.html").read_text(encoding="utf-8")
def rep(a, b):
    global h
    assert h.count(a) == 1, a
    h = h.replace(a, b)
rep("uniform mat4 uProj; uniform mat4 uView; uniform mat3 IViewRotMat;",
    "uniform mat4 uProj; uniform mat4 uView; uniform mat3 IViewRotMat; uniform vec3 uMagOff; uniform float uMagRot;")
rep("  vec4 vp = uView * vec4(aPos, 1.0);",
    "  bool mag = aPart > 0.62 && aPart < 0.88;\n  vec3 pos = aPos;\n  if (mag) { pos += uMagOff; }\n  vec4 vp = uView * vec4(pos, 1.0);")
rep("  gl.uniform4f(u('CamoUvRect'), 0, 0, 1, 1);",
    "  gl.uniform4f(u('CamoUvRect'), 0, 0, 1, 1);\n  gl.uniform1f(u('CamoReload'), o.recharge ?? -1);\n"
    "  const mo = o.magOff || [0, 0, 0];\n  gl.uniform3f(u('uMagOff'), mo[0] * arme.size, mo[1] * arme.size, mo[2] * arme.size);\n"
    "  window.dernierMag = boiteChargeur(arme, mul(P, V), mo, w, h);")
rep("async function rendre(nom, camo, o) {",
    """function boiteChargeur(arme, M, mo, w, h) {
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9, n = 0;
  const q = arme.pos, pa = arme.part;
  for (let i = 0; i < pa.length; i++) {
    if (!(pa[i] > 0.62 && pa[i] < 0.88)) continue;
    const x = q[i * 3] + mo[0] * arme.size, y = q[i * 3 + 1] + mo[1] * arme.size, z = q[i * 3 + 2] + mo[2] * arme.size;
    const cx = M[0] * x + M[4] * y + M[8] * z + M[12], cy = M[1] * x + M[5] * y + M[9] * z + M[13], cw = M[3] * x + M[7] * y + M[11] * z + M[15];
    const sx = (cx / cw * 0.5 + 0.5) * w, sy = (1 - (cy / cw * 0.5 + 0.5)) * h;
    x0 = Math.min(x0, sx); x1 = Math.max(x1, sx); y0 = Math.min(y0, sy); y1 = Math.max(y1, sy); n++;
  }
  return n ? [x0, y0, x1, y1] : null;
}

async function rendre(nom, camo, o) {""")
rep("armes[nom] = {vao, tex, n: d.part.length, center: d.center, size: d.size, canon, anchor, pos: d.pos};",
    "let my0 = 9, my1b = -9;\n  for (let i = 0; i < d.part.length; i++) if (d.part[i] > 0.62 && d.part[i] < 0.88) { my0 = Math.min(my0, d.cam[i * 3 + 1]); my1b = Math.max(my1b, d.cam[i * 3 + 1]); }\n"
    "  armes[nom] = {vao, tex, n: d.part.length, center: d.center, size: d.size, canon, anchor, pos: d.pos, part: d.part, magY: [my0, my1b]};")
rep("  gl.uniform1f(u('CamoReload'), o.recharge ?? -1);", "  gl.uniform1f(u('CamoReload'), o.recharge ?? -1);\n  gl.uniform2f(u('CamoMagY'), arme.magY[0], arme.magY[1]);")
(S / "rendu/apercu.html").write_text(h, encoding="utf-8")
print("ok")
