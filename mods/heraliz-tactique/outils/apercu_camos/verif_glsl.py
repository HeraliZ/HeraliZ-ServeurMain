#!/usr/bin/env python3
"""Compile et lie chaque programme camo du mod (camo.vsh + camo_<id>.fsh) avec un vrai compilateur OpenGL de bureau
(#version 150, profil cœur), comme le jeu : une erreur ici serait un écran noir ou un plantage chez les joueurs.

    xvfb-run -a python3 verif_glsl.py            (machine sans écran, Mesa llvmpipe : pip install moderngl)
    python3 verif_glsl.py                        (PC avec écran)

Les fichiers inclus par Minecraft (#moj_import <fog.glsl>, <light.glsl>) sont remplacés par des fonctions de même
signature, suffisantes pour compiler (le contrôle porte sur notre code, pas sur celui de Minecraft).
"""
import re
import sys
from pathlib import Path

import moderngl

CORE = Path(__file__).resolve().parents[2] / "src" / "client" / "resources" / "assets" / "heralisz_camo" / "shaders" / "core"

IMPORTS = {
    "fog.glsl": """
vec4 linear_fog(vec4 c, float d, float s, float e, vec4 f) { return d <= s ? c : vec4(mix(c.rgb, f.rgb, f.a * smoothstep(s, e, d)), c.a); }
float fog_distance(mat4 m, vec3 p, int shape) { return length((m * vec4(p, 1.0)).xyz); }
""",
    "light.glsl": """
vec4 minecraft_mix_light(vec3 l0, vec3 l1, vec3 n, vec4 c) { return vec4(c.rgb * min(1.0, (max(0.0, dot(normalize(l0), n)) + max(0.0, dot(normalize(l1), n))) * 0.6 + 0.4), c.a); }
""",
}


def pretraiter(src):
    return re.sub(r"#moj_import <([a-z_]+\.glsl)>", lambda m: IMPORTS[m.group(1)], src)


def main():
    ctx = moderngl.create_standalone_context(require=330)
    print("OpenGL :", ctx.info["GL_RENDERER"], ctx.info["GL_VERSION"])
    vs = pretraiter((CORE / "camo.vsh").read_text(encoding="utf-8"))
    fichiers = sorted(CORE.glob("camo_*.fsh"), key=lambda f: int(re.sub(r"\D", "", f.stem)))
    echecs = 0
    for f in fichiers:
        try:
            ctx.program(vertex_shader=vs, fragment_shader=pretraiter(f.read_text(encoding="utf-8"))).release()
        except Exception as e:
            echecs += 1
            print("ECHEC", f.name, str(e)[:3000])
    print("%d / %d programmes compilés et liés" % (len(fichiers) - echecs, len(fichiers)))
    sys.exit(1 if echecs else 0)


if __name__ == "__main__":
    main()
