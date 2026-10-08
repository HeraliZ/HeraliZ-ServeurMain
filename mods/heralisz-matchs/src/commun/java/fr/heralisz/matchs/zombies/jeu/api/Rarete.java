package fr.heralisz.matchs.zombies.jeu.api;

import java.util.Locale;

/**
 * Rarete d'un tirage de la boite mystere (ZOMBIES_V2_SPEC §2) : la couleur s'affiche sur la roulette (lueur de l'objet
 * tire, faisceau de la boite via {@link ZombiesDecor#boiteTirage}) et dans le nom de l'arme. Les accessoires TaCZ
 * montent la rarete d'une arme (AK nue = rare, AK + viseur = epique, AK + viseur + munitions a degats = legendaire).
 * Cle de traduction {@code zombies.jeu.rarete.<id>}.
 */
public enum Rarete {
    /** Pistolets, pistolets-mitrailleurs legers. */
    COMMUN(0xC9CED6),
    /** Pistolets-mitrailleurs, fusils a pompe, fusils d'assaut nus. */
    RARE(0x4FA3FF),
    /** Fusils d'assaut avec viseur, tireurs d'elite, fusils de precision. */
    EPIQUE(0xB46CFF),
    /** Fusils d'assaut haut de gamme (viseur + munitions a degats), mitrailleuses. */
    LEGENDAIRE(0xFFB327),
    /** Arme unique de la carte (« wonder weapon »), seulement dans la boite, tres rare. */
    MYTHIQUE(0xFF4A4A);

    /** Couleur RGB (lueur, nom, faisceau). */
    public final int couleur;

    Rarete(int couleur) {
        this.couleur = couleur;
    }

    /** Identifiant en minuscules ("legendaire"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Cle de traduction du nom de la rarete. */
    public String cle() {
        return "zombies.jeu.rarete." + id();
    }

    /** Rarete superieure (plafonnee a {@link #LEGENDAIRE} : le mythique est reserve a l'arme de la carte). */
    public Rarete monter(int crans) {
        int o = Math.min(LEGENDAIRE.ordinal(), ordinal() + Math.max(0, crans));
        return ordinal() >= LEGENDAIRE.ordinal() ? this : values()[o];
    }

    /** Lecture tolerante (id, nom ou ordinal), {@code defaut} si inconnu. */
    public static Rarete parId(String s, Rarete defaut) {
        if (s == null) return defaut;
        for (Rarete r : values()) if (r.id().equalsIgnoreCase(s) || r.name().equalsIgnoreCase(s)) return r;
        return defaut;
    }

    public static Rarete de(int ordinal) {
        Rarete[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : COMMUN;
    }
}
