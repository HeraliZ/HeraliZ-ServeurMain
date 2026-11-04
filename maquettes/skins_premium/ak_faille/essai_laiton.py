"""Copie d'essai du code commun des camos : les faces marquées (alpha 200, balles du chargeur à fenêtre) restent en métal."""
import sys
from pathlib import Path

R, S = Path(sys.argv[1]), Path(sys.argv[2])
lib = (R / "modules/armes/src/client/resources/assets/minecraft/shaders/include/heralisz_camo.glsl").read_text(encoding="utf-8")


def rep(t, a, b):
    assert t.count(a) == 1, a
    return t.replace(a, b)


lib = rep(lib, "vec3 BASE_COL = vec3(0.0);\n", "vec3 BASE_COL = vec3(0.0);\n// alpha de la texture d'origine au point dessiné (200/255 : balle d'un chargeur à fenêtre, laissée en métal)\nfloat BASE_A = 1.0;\n")
laiton = """// balles d'un chargeur à fenêtre (texture marquée : alpha 200) : métal poli de la couleur de la texture (laiton, cuivre)
bool marqueLaiton() { return abs(BASE_A - 200.0 / 255.0) < 0.02; }
vec3 laitonPoli(out vec3 em) {
    vec3 n = normalize(viewNormal);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    em = vec3(0.0);
    return BASE_COL * (0.5 + 0.5 * env.g) + env * BASE_COL * (0.25 + 0.5 * fres);
}

"""
lib = rep(lib, "bool estChargeur() { return PART > 0.62 && PART < 0.88; }\n", "bool estChargeur() { return PART > 0.62 && PART < 0.88; }\n\n" + laiton)
lib = rep(lib, "vec3 faille(vec3 p, out vec3 em) {\n    float t = CamoTime;\n", "vec3 faille(vec3 p, out vec3 em) {\n    if (estChargeur() && marqueLaiton()) return laitonPoli(em);\n    float t = CamoTime;\n")
(S / "ak" / "inclus_laiton.glsl").write_text(lib, encoding="utf-8")
h = (S / "rendu2" / "apercu.html").read_text(encoding="utf-8")
if "BASE_A = base.a;" not in h:
    h = rep(h, "    BASE_COL = base.rgb;\n", "    BASE_COL = base.rgb;\n    BASE_A = base.a;\n")
(S / "rendu2" / "apercu.html").write_text(h, encoding="utf-8")
print("ok")
