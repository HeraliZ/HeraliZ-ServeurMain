"""Copie le kit d'interface « style clan » (heralisz_matchs/client/kit, lui-même copié de heralisz_clans) dans un autre mod,
sans dépendance de compilation : polices DM Sans (assets/<modid>/font), Ui, Echelle, HzButton, HzField, HzScroll, SonsUi.

    python outils/copier_kit_ui.py <dossier projet> <paquet java cible> <modid>
    ex. python outils/copier_kit_ui.py src/heraliz-emote fr.heraliz.emote.client.gui.kit heraliz_emote

- Accent : celui du clan du joueur lu par réflexion (fr.heralisz.clans.client.ClientClans.accent()) si le mod clans est
  présent, sinon l'accent neutre ; un écran peut l'imposer (Ui.forcerAccent).
- Sons : heralisz_clans:ui_clic / ui_ouvrir / ui_fermer résolus par identifiant ; sans le mod clans, clic vanilla discret.
- Les fichiers déjà présents sont écrasés (le kit de référence reste celui des matchs).
"""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_KIT = ROOT / "mods/heralisz-matchs/src/client/java/fr/heralisz/matchs/client/kit"
SRC_FONT = ROOT / "mods/heralisz-matchs/src/client/resources/assets/heralisz_matchs/font"
FICHIERS = ["Ui.java", "Echelle.java", "HzButton.java", "HzField.java", "HzScroll.java", "SonsUi.java"]

ACCENT_REFLEXION = '''    /** Accent impose par un ecran ou un mode, -1 = aucun. */
    private static int accentForce = -1;
    /** Lecture de l'accent du clan par reflexion (pas de dependance au mod clans) ; null = pas encore cherche. */
    private static java.lang.reflect.Method accentClan;
    private static boolean accentClanAbsent;

    /**
     * Couleur d'accent courante : l'accent force s'il y en a un ({@link #forcerAccent}), sinon celle du clan du joueur
     * (mod heralisz_clans, lu par reflexion), neutre sinon.
     */
    public static int accent() {
        if (accentForce >= 0) return accentForce;
        if (!accentClanAbsent) {
            try {
                if (accentClan == null) accentClan = Class.forName("fr.heralisz.clans.client.ClientClans").getMethod("accent");
                return ((Number) accentClan.invoke(null)).intValue() & 0xFFFFFF;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                accentClanAbsent = true;
            }
        }
        return ACCENT_NEUTRE;
    }
'''


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    projet, paquet, modid = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    if not projet.is_absolute():
        projet = ROOT / projet
    dest = projet / "src/main/java" / paquet.replace(".", "/")
    dest.mkdir(parents=True, exist_ok=True)
    for nom in FICHIERS:
        t = (SRC_KIT / nom).read_text(encoding="utf-8")
        t = t.replace("package fr.heralisz.matchs.client.kit;", f"package {paquet};")
        t = t.replace("import fr.heralisz.matchs.HeraliszMatchs;\n", "")
        t = re.sub(r'HeraliszMatchs\.id\("(\w+)"\)', lambda m: f'new ResourceLocation("{modid}", "{m.group(1)}")', t)
        t = t.replace("assets/heralisz_matchs/font", f"assets/{modid}/font")
        if nom == "Ui.java":
            debut = t.index("    /** Accent impose par un ecran")
            fin = t.index("    /** Impose l'accent (RGB)")
            t = t[:debut] + ACCENT_REFLEXION + "\n" + t[fin:]
        if nom == "SonsUi.java":
            t = t.replace("Les sons propres aux matchs sont dans le registre du mod.",
                          "Sans le mod clans : clic vanilla discret.")
            t = t.replace("    public static void clic(float volume) {\n        resoudre();\n        jouer(clic, volume);\n    }",
                          "    public static void clic(float volume) {\n        resoudre();\n"
                          "        if (clic == null) jouer(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), volume * 0.35F);\n"
                          "        else jouer(clic, volume);\n    }")
        if "matchs" in t.lower():
            print(f"[kit] attention : '{nom}' mentionne encore les matchs")
        (dest / nom).write_text(t, encoding="utf-8", newline="\n")
        print(f"[kit] {dest / nom}")
    fdest = projet / "src/main/resources/assets" / modid / "font"
    fdest.mkdir(parents=True, exist_ok=True)
    for f in SRC_FONT.iterdir():
        if f.suffix == ".json":
            (fdest / f.name).write_text(f.read_text(encoding="utf-8").replace("heralisz_matchs:", f"{modid}:"), encoding="utf-8")
        else:
            shutil.copy2(f, fdest / f.name)
        print(f"[kit] {fdest / f.name}")


if __name__ == "__main__":
    main()
