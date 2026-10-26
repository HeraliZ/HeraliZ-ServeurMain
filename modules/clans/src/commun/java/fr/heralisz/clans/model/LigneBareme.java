package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Une ligne de « Gagner des points » (onglet Saison, 25/10), construite par le serveur depuis le bareme reel
 * (config/heralisz_clans/saison.json) : le joueur voit toujours les vrais chiffres. Tous les textes sont des cles de
 * langue du mod (le joueur les lit dans sa langue) : libelle {@code gui.saison.l.<cle>}, forme des points
 * {@code gui.saison.pts.<forme>} remplie avec {@code valeurs} (nombres mis en forme chez le joueur), condition
 * {@code gui.saison.c.<condition>} remplie avec {@code args} (vide : pas de condition).
 */
public record LigneBareme(int categorie, String cle, String forme, double[] valeurs, String condition, String[] args) {
    public static final int PVE = 0, PVP = 1, EVENTS = 2;

    public void write(FriendlyByteBuf b) {
        b.writeByte(categorie);
        Texte.utf(b, cle, 48);
        Texte.utf(b, forme, 24);
        b.writeVarInt(valeurs.length);
        for (double v : valeurs) b.writeDouble(v);
        Texte.utf(b, condition, 48);
        b.writeVarInt(args.length);
        for (String a : args) Texte.utf(b, a, 32);
    }

    public static LigneBareme read(FriendlyByteBuf b) {
        int cat = b.readByte();
        String cle = b.readUtf(48), forme = b.readUtf(24);
        double[] v = new double[Math.min(8, b.readVarInt())];
        for (int i = 0; i < v.length; i++) v[i] = b.readDouble();
        String cond = b.readUtf(48);
        String[] a = new String[Math.min(8, b.readVarInt())];
        for (int i = 0; i < a.length; i++) a[i] = b.readUtf(32);
        return new LigneBareme(cat, cle, forme, v, cond, a);
    }
}
