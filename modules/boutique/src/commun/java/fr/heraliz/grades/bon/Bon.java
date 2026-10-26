package fr.heraliz.grades.bon;

import fr.heraliz.grades.Grade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bon (2.5.0, 23/10, demande du propriétaire) : une page que les op règlent dans l'éditeur des bons ({@code /bon editeur})
 * et qui, au clic droit, donne ce qu'elle dit : hérals, Points Boutique, grade de la boutique pour un temps, bonus de loot
 * pour un temps, ou une commande de la console. Tout est dans l'étiquette de l'objet ({@value #CLE}), signée par le
 * serveur (une copie trafiquée en créatif est refusée) ; la rareté est l'étiquette {@value #CLE_RARETE} lue par HeraliZ
 * Loot (fond des cases, infobulle) et {@value #CLE_COULEUR_LIBRE} garde la couleur du nom choisie par l'op.
 */
public final class Bon {
    private Bon() {
    }

    public static final String CLE = "HeralizBon", CLE_RARETE = "HeralizRarete", CLE_COULEUR_LIBRE = "HeralizCouleurLibre";
    /** Raretés de HeraliZ Loot (identifiants), dans l'ordre ; « » = aucune. */
    public static final String[] RARETES = {"", "commun", "peu_commun", "rare", "epique", "legendaire", "unique"};
    public static final int[] COULEURS_RARETES = {0xA0A6B0, 0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF3333};

    /** Bornes (revérifiées par le serveur). */
    public static final int MAX_NOM = 40, MAX_EFFETS = 8, MAX_COMMANDE = 200, MAX_PCT = 25;
    public static final long MAX_HERALS = 1_000_000_000L, MAX_PB = 100_000L;
    /** Bonus de loot : 365 jours au plus. */
    public static final long MAX_BONUS_MS = 365L * 86_400_000L;

    public enum Type {
        HERALS, PB, GRADE, BONUS_LOOT, COMMANDE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type de(String s) {
            for (Type t : values()) if (t.id().equals(s)) return t;
            return null;
        }
    }

    /**
     * Un effet : hérals / PB (nombre), grade (valeur = identifiant, durée en jours ou « vie »), bonus de loot (nombre = %,
     * durée), commande (valeur = commande de la console, {joueur} = pseudo).
     */
    public record Effet(Type type, long nombre, String valeur, String duree) {
    }

    public record Donnees(String nom, int couleur, String rarete, List<Effet> effets) {
    }

    // ------------------------------------------------------------------ durées
    private static final Pattern DUREE = Pattern.compile("(\\d{1,5})(j|d|h|min|m)");

    /** Durée en ms (0 = « vie », -1 = illisible). */
    public static long dureeMs(String d) {
        if (d == null) return -1;
        String s = d.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.equals("vie") || s.equals("life") || s.equals("жизнь")) return 0;
        Matcher m = DUREE.matcher(s);
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        if (n <= 0) return -1;
        return switch (m.group(2)) {
            case "j", "d" -> n * 86_400_000L;
            case "h" -> n * 3_600_000L;
            default -> n * 60_000L;
        };
    }

    /** Nombre de jours d'une durée de grade (0 = à vie, -1 = refusée : seulement des jours entiers ou « vie »). */
    public static int joursGrade(String d) {
        long ms = dureeMs(d);
        if (ms == 0) return 0;
        if (ms < 0 || ms % 86_400_000L != 0) return -1;
        long j = ms / 86_400_000L;
        return j > 3650 ? -1 : (int) j;
    }

    /** Durée lisible dans la langue du joueur : « 7 j », « 12 h », « 30 min », « à vie ». */
    public static MutableComponent texteDuree(String d) {
        long ms = dureeMs(d);
        if (ms == 0) return Component.translatableWithFallback("item.heraliz_grades.bon.duree.vie", "à vie");
        if (ms < 0) return Component.literal(String.valueOf(d));
        if (ms % 86_400_000L == 0) return Component.translatableWithFallback("item.heraliz_grades.bon.duree.j", "%s j", String.valueOf(ms / 86_400_000L));
        if (ms % 3_600_000L == 0) return Component.translatableWithFallback("item.heraliz_grades.bon.duree.h", "%s h", String.valueOf(ms / 3_600_000L));
        return Component.translatableWithFallback("item.heraliz_grades.bon.duree.min", "%s min", String.valueOf(ms / 60_000L));
    }

    /** Nombre avec des espaces entre les milliers : 1 500. */
    public static String nombre(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', ' ');
    }

    /** Ce que donne un effet, dans la langue du joueur (infobulle, messages). */
    public static MutableComponent texteEffet(Effet e) {
        return switch (e.type()) {
            case HERALS -> Component.translatableWithFallback("item.heraliz_grades.bon.effet.herals", "+%s hérals", nombre(e.nombre()));
            case PB -> Component.translatableWithFallback("item.heraliz_grades.bon.effet.pb", "+%s Points Boutique", nombre(e.nombre()));
            case GRADE -> {
                Grade g = Grade.parId(e.valeur());
                Component nom = g == null ? Component.literal(e.valeur()) : g.nom();
                yield Component.translatableWithFallback("item.heraliz_grades.bon.effet.grade", "Grade %s (%s)", nom, texteDuree(e.duree()));
            }
            case BONUS_LOOT -> Component.translatableWithFallback("item.heraliz_grades.bon.effet.bonus_loot", "Bonus de loot +%s %% (%s)",
                    String.valueOf(e.nombre()), texteDuree(e.duree()));
            case COMMANDE -> Component.translatableWithFallback("item.heraliz_grades.bon.effet.commande", "Récompense spéciale");
        };
    }

    // ------------------------------------------------------------------ validation
    /**
     * Vérifie les données ; null si tout va bien, sinon la clé de l'erreur (sous « message.heraliz_grades.bon. »). Les
     * grades sont lus dans les définitions (côté joueur : celles envoyées par le serveur).
     */
    @Nullable
    public static String erreur(Donnees d) {
        if (d == null) return "invalide";
        if (d.nom() == null || d.nom().isBlank() || d.nom().length() > MAX_NOM || d.nom().indexOf('§') >= 0) return "nom";
        if (d.couleur() < 0 || d.couleur() > 0xFFFFFF) return "couleur";
        boolean rareteOk = false;
        for (String r : RARETES) rareteOk |= r.equals(d.rarete());
        if (!rareteOk) return "rarete";
        if (d.effets() == null || d.effets().isEmpty()) return "aucun_effet";
        if (d.effets().size() > MAX_EFFETS) return "trop_effets";
        for (Effet e : d.effets()) {
            String err = erreur(e);
            if (err != null) return err;
        }
        return null;
    }

    @Nullable
    public static String erreur(Effet e) {
        if (e == null || e.type() == null) return "invalide";
        switch (e.type()) {
            case HERALS -> {
                if (e.nombre() < 1 || e.nombre() > MAX_HERALS) return "herals";
            }
            case PB -> {
                if (e.nombre() < 1 || e.nombre() > MAX_PB) return "pb";
            }
            case GRADE -> {
                Grade g = Grade.parId(e.valeur());
                if (g == null || g.type != Grade.Type.BOUTIQUE) return "grade";
                if (joursGrade(e.duree()) < 0) return "duree_grade";
            }
            case BONUS_LOOT -> {
                if (e.nombre() < 1 || e.nombre() > MAX_PCT) return "bonus";
                long ms = dureeMs(e.duree());
                if (ms < 60_000L || ms > MAX_BONUS_MS) return "duree_bonus";
            }
            case COMMANDE -> {
                String c = e.valeur() == null ? "" : e.valeur().trim();
                if (c.startsWith("/")) c = c.substring(1);
                if (c.isEmpty() || c.length() > MAX_COMMANDE || c.indexOf('\n') >= 0 || c.indexOf('§') >= 0) return "commande";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ objet (NBT)
    /** Données d'un bon, null s'il n'en a pas (bon vierge ou autre objet). */
    @Nullable
    public static Donnees lire(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains(CLE, Tag.TAG_COMPOUND)) return null;
        CompoundTag b = t.getCompound(CLE);
        List<Effet> effets = new ArrayList<>();
        ListTag l = b.getList("Effets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(l.size(), MAX_EFFETS); i++) {
            CompoundTag e = l.getCompound(i);
            Type type = Type.de(e.getString("T"));
            if (type == null) continue;
            effets.add(new Effet(type, e.getLong("N"), e.getString("V"), e.getString("D")));
        }
        return new Donnees(b.getString("Nom"), b.getInt("Couleur"), t.getString(CLE_RARETE), effets);
    }

    /** Identifiant unique du bon (« » sans). */
    public static String id(ItemStack st) {
        CompoundTag t = st.getTag();
        return t == null ? "" : t.getCompound(CLE).getString("Id");
    }

    public static String signature(ItemStack st) {
        CompoundTag t = st.getTag();
        return t == null ? "" : t.getCompound(CLE).getString("Sig");
    }

    /** Écrit les données, l'identifiant et la signature sur l'objet. */
    public static void ecrire(ItemStack st, Donnees d, String id, String sig) {
        CompoundTag t = st.getOrCreateTag();
        CompoundTag b = new CompoundTag();
        b.putString("Id", id);
        b.putString("Nom", d.nom());
        b.putInt("Couleur", d.couleur());
        ListTag l = new ListTag();
        for (Effet e : d.effets()) {
            CompoundTag x = new CompoundTag();
            x.putString("T", e.type().id());
            x.putLong("N", e.nombre());
            x.putString("V", e.valeur() == null ? "" : e.valeur());
            x.putString("D", e.duree() == null ? "" : e.duree());
            l.add(x);
        }
        b.put("Effets", l);
        b.putString("Sig", sig);
        t.put(CLE, b);
        if (d.rarete().isEmpty()) t.remove(CLE_RARETE);
        else t.putString(CLE_RARETE, d.rarete());
        t.putBoolean(CLE_COULEUR_LIBRE, true);
    }

    // ------------------------------------------------------------------ modèle et exemplaires
    /** Un objet remis par le serveur, avec d'où il vient (journal). */
    public record Copie(ItemStack objet, String source) {
    }

    /** Renouvellement d'un exemplaire : branché par la partie serveur des grades ; rien côté joueur. */
    public static final fr.heraliz.grades.pont.Crochet<Copie> RENOUVELER = new fr.heraliz.grades.pont.Crochet<>();

    /**
     * 25/10 (audit) : à appeler sur CHAQUE objet que le serveur remet à un joueur depuis ses propres données (butin d'une
     * caisse, kit, kit de départ…). Si c'est un bon valablement signé, il reçoit un identifiant et une signature neufs :
     * le bon réglé par l'op (posé dans la caisse ou le kit) est un MODÈLE, chaque objet remis un EXEMPLAIRE à usage unique.
     * Avant, toutes les copies d'un même modèle portaient le même identifiant : seul le premier gagnant pouvait s'en
     * servir. Un bon faux n'est jamais re-signé ; une copie faite par un joueur (bug de duplication) garde son
     * identifiant et reste refusée. Tout autre objet n'est pas touché. Fil serveur ; rien côté joueur.
     */
    public static void exemplaire(ItemStack st, String source) {
        if (st == null || st.isEmpty() || !st.is(ObjetBon.BON.get())) return;
        RENOUVELER.accept(new Copie(st, source));
    }

    /** Texte signé par le serveur : tout ce qui compte, dans un ordre fixe. */
    public static String texteASigner(String id, Donnees d) {
        StringBuilder sb = new StringBuilder("bon\n").append(id).append('\n').append(d.nom()).append('\n').append(d.couleur()).append('\n')
                .append(d.rarete());
        for (Effet e : d.effets()) {
            sb.append('\n').append(e.type().id()).append('|').append(e.nombre()).append('|').append(e.valeur() == null ? "" : e.valeur())
                    .append('|').append(e.duree() == null ? "" : e.duree());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ réseau
    public static void ecrire(FriendlyByteBuf b, Donnees d) {
        b.writeUtf(d.nom(), MAX_NOM * 4);
        b.writeInt(d.couleur());
        b.writeUtf(d.rarete(), 32);
        int n = Math.min(d.effets().size(), MAX_EFFETS);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Effet e = d.effets().get(i);
            b.writeUtf(e.type().id(), 32);
            b.writeVarLong(Math.max(0, e.nombre()));
            b.writeUtf(e.valeur() == null ? "" : e.valeur(), MAX_COMMANDE * 4);
            b.writeUtf(e.duree() == null ? "" : e.duree(), 32);
        }
    }

    public static Donnees lire(FriendlyByteBuf b) {
        String nom = b.readUtf(MAX_NOM * 4);
        int couleur = b.readInt();
        String rarete = b.readUtf(32);
        int n = Math.min(b.readVarInt(), MAX_EFFETS);
        List<Effet> effets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Type t = Type.de(b.readUtf(32));
            long nombre = b.readVarLong();
            String valeur = b.readUtf(MAX_COMMANDE * 4), duree = b.readUtf(32);
            if (t != null) effets.add(new Effet(t, nombre, valeur, duree));
        }
        return new Donnees(nom, couleur, rarete, effets);
    }
}
