"""Tous les contrôles d'un coup, sur les jars fabriqués par outils/construire.py (à lancer AVANT tout envoi au Ptero).

    python outils/verifier.py                     tous les mods
    python outils/verifier.py heraliz-boss crac   seulement ceux-là
    python outils/verifier.py --rapide            sans verif_reobf
    python outils/verifier.py --sans-split        sans verif_split (machine sans Minecraft en noms SRG, ex. GitHub)

Contrôles :
  - verif_split      : séparation client / serveur de chaque mod coupé (besoin de outils/chemins_local.json : Minecraft
                       client et serveur en noms SRG ; voir outils/chemins.py) ;
  - check_geckolib   : API de geckolib222 4.4.7 respectée, aucun GeckoLib embarqué ;
  - verif_reobf      : noms de Minecraft réobfusqués (sinon plantage au chargement) ;
  - mention          : LICENCE-HeraliZ.txt dans chaque jar de nos mods (propriété HeraliZ) ;
  - silencieux       : chaque silencieux des gunpacks (serveur/tacz) a sa portée de son (outils/silencieux.py) ;
  - accessoires      : listes communes d'accessoires TaCZ à jour (gunpack heraliz_accessoires, outils/accessoires.py) ;
  - logos            : plus de logo « M » de Max Stuff sur les icônes d'accessoires (outils/icones_sans_logo.py) ;
  - verif_accessoires : chaque accessoire TaCZ bien placé sur chaque arme qui l'accepte, ni flottant, ni traversant,
                       ni invisible (outils/verif_accessoires.py ; défauts acceptés listés dans l'outil) ;
  - langues          : textes français, anglais et russe avec les mêmes paramètres et codes couleur
                       (outils/verifier_langues.py ; un texte pas encore traduit n'est qu'un avertissement) ;
  - noms perso      : aucun mot de outils/noms_interdits.txt (prénoms, anciens pseudos…, un par ligne, fichier hors Git)
                       dans nos sources ni dans nos jars.
Code de sortie 1 au moindre problème.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402
import construire  # noqa: E402

D = chemins.DEPOT
O = D / "outils"


def options_split(p, cote):
    """Options en plus pour verif_split, propres à certains mods (contenu embarqué exprès, mod facultatif absent)."""
    o = []
    if p.name == "caracrea":
        api = sorted(Path.home().glob(".gradle/caches/modules-2/files-2.1/de.maxhenkel.voicechat/voicechat-api/*/*/voicechat-api-*.jar"))
        if api:
            o += ["--ref", str(api[-1])]
        o += ["--embarque", "%s,LICENSE-concentus.txt" % (p / "libs/concentus-1.0.2-caracrea.jar")]
    if p.name == "heraliz-emote":
        jj = p / "libs/heraliz_emote-jarjar-2.5.2.jar"
        tmp = Path(tempfile.mkdtemp(prefix="hz_emote_"))
        with zipfile.ZipFile(jj) as z:
            refs = []
            for n in z.namelist():
                if n.endswith(".jar"):
                    t = tmp / Path(n).name
                    t.write_bytes(z.read(n))
                    refs.append(str(t))
        o += ["--embarque", str(jj), "--ref", ",".join(refs)]
    if p.name == "spartan" and cote == "serveur":
        o += ["--embarque", "META-INF/spartan/client.bin"]   # copie du jar joueur (défi d'intégrité)
    if p.name == "heralisz-matchs":
        boss = construire.jars(chemins.MODS / "heraliz-boss")[cote]
        curios = sorted(chemins.MODS_TIERS.glob("curios-*.jar"))
        o += ["--ref", ",".join(str(x) for x in [boss] + curios)]
    return o


def lancer(args):
    r = subprocess.run([sys.executable] + args, cwd=D, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.returncode, r.stdout + r.stderr


def noms_interdits():
    f = O / "noms_interdits.txt"
    if not f.exists():
        print("[verifier] outils/noms_interdits.txt absent : contrôle des noms perso sauté")
        return None
    mots = [l.strip() for l in f.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    return re.compile("(?i)(%s)" % "|".join(re.escape(m) for m in mots)) if mots else None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    choisis = [chemins.MODS / a for a in args] if args else construire.projets()
    # dépendances cherchées du même côté que le jar contrôlé (un -serveur a besoin des classes serveur des autres mods)
    dossier_mods = {"client": chemins.LIVRAISON / "mods client", "serveur": chemins.LIVRAISON / "mods serveur",
                    "complet": chemins.LIVRAISON / "mods solo"}
    erreurs = 0
    jars_a_voir = []
    for p in choisis:
        js = construire.jars(p)
        absents = [j for j in js.values() if not j.exists()]
        if absents:
            print("[verifier] %s : jar absent, lancer d'abord outils/construire.py" % p.name)
            erreurs += 1
            continue
        jars_a_voir += list(js.values())
        if construire.coupe(p) and "--sans-split" not in sys.argv:
            for cote in ("client", "serveur", "complet"):
                code, out = lancer([str(O / "verif_split.py"), str(js[cote]), "--mods", str(dossier_mods[cote]),
                                    "--projet", str(p)] + options_split(p, cote))
                res = [l for l in out.splitlines() if "ERREUR" in l or "au total" in l]
                print("[verif_split] %-22s %-8s %s" % (p.name, cote, res[-1].strip() if res else out[-300:]))
                for l in res[:-1][:15]:
                    print("      " + l.strip())
                erreurs += code != 0
        cible = js.get("complet") or js.get("seul")
        code, out = lancer([str(O / "check_geckolib_api.py"), str(cible), str(chemins.GECKOLIB)])
        dern = [l for l in out.splitlines() if "absentes" in l]
        if not dern or not dern[-1].startswith("0 absentes"):
            print("[geckolib]    %-22s %s" % (p.name, dern[-1] if dern else out[-300:]))
            erreurs += 1
        with zipfile.ZipFile(cible) as z:
            emb = [n for n in z.namelist() if n.startswith("META-INF/jarjar/") and "geckolib" in n.lower()]
        if emb:
            print("[geckolib]    %-22s GeckoLib embarqué : %s" % (p.name, emb))
            erreurs += 1
    if "--rapide" not in sys.argv and jars_a_voir:
        import verif_reobf
        if not verif_reobf.minecraft_disponible():
            print("[verif_reobf] Minecraft en noms SRG absent : contrôle sauté (Gradle réobfusque à chaque fabrication)")
        else:
            code, out = lancer([str(O / "verif_reobf.py"), "--minecraft", "--verifier"] + [str(j) for j in jars_a_voir])
            lignes = out.splitlines()
            for k, l in enumerate(lignes):
                if "suspecte" in l and not l.strip().endswith(": 0 référence(s) suspecte(s)"):
                    print("[verif_reobf] " + l.strip())
                    for x in lignes[k + 1:k + 6]:
                        if x.startswith("   "):
                            print("      " + x.strip())
                    erreurs += 1
            if code != 0 and not any("suspecte" in l for l in lignes):
                print("[verif_reobf] " + out[-500:])
                erreurs += 1
    # mention de propriété HeraliZ (LICENCE-HeraliZ.txt à la racine de chaque jar de nos mods)
    for p in choisis:
        for j in construire.jars(p).values():
            if j.exists():
                with zipfile.ZipFile(j) as z:
                    if "LICENCE-HeraliZ.txt" not in z.namelist():
                        print("[mention]     %s : LICENCE-HeraliZ.txt absent (le copier depuis mods/_gabarit/src/commun/resources)" % j.name)
                        erreurs += 1
    # portée du son de chaque silencieux des gunpacks (un gunpack ajouté peut en apporter un nouveau)
    code, out = lancer([str(O / "silencieux.py"), "--verifier"])
    if code != 0:
        for l in out.splitlines():
            if "silencieux sans portée" in l or "pas à jour" in l or "introuvable" in l:
                print(l.strip())
        erreurs += 1
    # accessoires à plat : un gunpack ajouté ou changé demande de refaire les listes communes (outils/accessoires.py)
    code, out = lancer([str(O / "accessoires.py"), "--verifier"])
    if code != 0:
        for l in out.splitlines():
            if l.startswith("[accessoires]") and "problème(s)" not in l:
                print(l.strip())
        print("[accessoires] relancer : python outils/accessoires.py")
        erreurs += 1
    # logo « M » de Max Stuff retiré des icônes (choix du propriétaire, 11/10) : un gunpack remis à neuf le ramènerait.
    # L'outil lit les images avec numpy et Pillow : sans eux (machines de GitHub), contrôle sauté comme verif_reobf.
    import importlib.util
    if not (importlib.util.find_spec("numpy") and importlib.util.find_spec("PIL")):
        print("[logos] numpy ou Pillow absent : contrôle des logos sauté (à faire sur un PC)")
        code = 0
    else:
        code, out = lancer([str(O / "icones_sans_logo.py"), "--verifier"])
    if code != 0:
        for l in out.splitlines():
            if l.startswith("[logos]"):
                print(l.strip())
        print("[logos] relancer : python outils/icones_sans_logo.py")
        erreurs += 1
    # placement de chaque accessoire sur chaque arme (ni flottant, ni traversant, ni invisible) : un gunpack ajouté ou
    # changé peut en déplacer. Calcul avec numpy et Pillow (textures) : sans eux, sauté comme les logos.
    if not (importlib.util.find_spec("numpy") and importlib.util.find_spec("PIL")):
        print("[verif_accessoires] numpy ou Pillow absent : placement des accessoires non contrôlé (à faire sur un PC)")
    else:
        code, out = lancer([str(O / "verif_accessoires.py")])
        if code != 0:
            for l in out.splitlines():
                if l.startswith("[verif_accessoires]") and "problème(s)" not in l:
                    print(l.strip())
            print("[verif_accessoires] détail et images : python outils/verif_accessoires.py --rapport --images")
            erreurs += 1
    # trois langues : paramètres (%s...) et codes couleur identiques, sinon le joueur voit un texte cassé
    code, out = lancer([str(O / "verifier_langues.py")] + [p.name for p in choisis])
    for l in out.splitlines():
        if "ERREUR" in l or l.startswith("[langues] ") and "erreur(s)" in l:
            print(l.strip())
    erreurs += code != 0
    pat = noms_interdits()
    if pat:
        for p in choisis:
            for f in (p / "src").rglob("*"):
                if f.is_file() and f.suffix in (".java", ".json", ".toml", ".properties", ".txt", ".md", ".mcmeta"):
                    if pat.search(f.read_text(encoding="utf-8", errors="replace")):
                        print("[noms perso]  %s" % f.relative_to(D))
                        erreurs += 1
            # fichiers du projet hors sources (README, docs, scripts du launcher, fabrication)
            for f in p.rglob("*"):
                rel = f.relative_to(p)
                if (f.is_file() and rel.parts[0] not in ("src", "build", "run", ".gradle", "out", "libs", "gradle")
                        and f.suffix in (".md", ".txt", ".js", ".json", ".gradle", ".properties", ".toml", "")):
                    if pat.search(f.read_text(encoding="utf-8", errors="replace")):
                        print("[noms perso]  %s" % f.relative_to(D))
                        erreurs += 1
        for j in jars_a_voir:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    if not n.endswith((".png", ".ogg", "/")) and pat.search(z.read(n).decode("latin-1")):
                        print("[noms perso]  %s : %s" % (j.name, n))
                        erreurs += 1
                        break
    print("[verifier] %d problème(s)" % erreurs)
    sys.exit(1 if erreurs else 0)


if __name__ == "__main__":
    main()
