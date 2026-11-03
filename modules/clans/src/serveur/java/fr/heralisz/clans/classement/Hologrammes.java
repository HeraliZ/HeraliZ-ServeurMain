package fr.heralisz.clans.classement;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.saison.Saison;
import fr.heralisz.economie.api.EconomieApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hologrammes de classement du spawn (03/11, demande du proprietaire : « un truc simple qui ne fait pas laguer, genre un
 * hologramme qui affiche les 8 premiers »). Un hologramme = <b>une</b> entite d'affichage de texte de Minecraft
 * ({@code minecraft:text_display}) : titre et {@value Classements#N} lignes « rang. pseudo [TAG] — valeur », sans fond,
 * toujours tourne vers le joueur, plein eclairage. Le jeu du joueur la dessine lui-meme (aucun rendu a nous) ; elle n'a
 * ni boite de collision ni points de vie (on ne la frappe pas, on ne clique pas dessus).
 * <ul>
 * <li>Gardee avec le monde comme toute entite : retrouvee au chargement de son troncon par son etiquette
 * {@value #ETIQUETTE} (plus {@code heraliz_classement_<genre>}), jamais recreee toute seule (donc jamais en double).</li>
 * <li>Remplie a la pose et au chargement de son troncon (au plus une fois par seconde, celles qui attendent), puis
 * toutes les {@code [classements] maj_minutes} minutes, seulement si un joueur est a moins de
 * {@code [classements] distance_joueurs} blocs. Le texte n'est change (et envoye aux joueurs proches) que si le
 * classement a change. Rien par tick tant qu'aucun hologramme n'est charge.</li>
 * </ul>
 * Textes : cles traduites par le jeu de chaque joueur (fr / en / ru), polices du noyau. Fil serveur.
 */
public final class Hologrammes {
    private Hologrammes() {
    }

    /** Etiquette de toutes nos entites (ne jamais la changer : c'est ainsi qu'on les retrouve dans le monde). */
    public static final String ETIQUETTE = "heraliz_classement";
    private static final String PREFIXE_GENRE = ETIQUETTE + "_";

    private static final ResourceLocation POLICE = new ResourceLocation("heraliz_noyau", "ui");
    private static final ResourceLocation POLICE_GRASSE = new ResourceLocation("heraliz_noyau", "ui_bold");
    private static final ResourceLocation POLICE_PETITE = new ResourceLocation("heraliz_noyau", "ui_small");
    private static final ResourceLocation POLICE_JETON = new ResourceLocation("heralisz_economie", "ui");
    private static final String JETON = "";
    private static final int[] PODIUM = {0xE8C36A, 0xB9C0CB, 0xD08A4E};
    private static final int BLANC = 0xECEEF2, CLAIR = 0xC9CED6, GRIS = 0xA8AEB8, SOMBRE = 0x9AA3B2;
    /** Longueur maximale d'un nom de clan affiche (le nom complet fait 32 caracteres au plus). */
    private static final int NOM_CLAN_MAX = 22;

    /** Taille du texte (transformation de l'entite). */
    private static final float ECHELLE = 0.75f;

    private static final Map<UUID, Display.TextDisplay> CHARGES = new LinkedHashMap<>();
    private static final Set<UUID> EN_ATTENTE = new LinkedHashSet<>();
    private static int ticks, depuisMaj;

    // ------------------------------------------------------------------ suivi des entites chargees
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.getEntity().getType() != EntityType.TEXT_DISPLAY) return;
        if (e.getEntity() instanceof Display.TextDisplay t && t.getTags().contains(ETIQUETTE)) {
            CHARGES.put(t.getUUID(), t);
            EN_ATTENTE.add(t.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || e.getEntity().getType() != EntityType.TEXT_DISPLAY) return;
        if (CHARGES.remove(e.getEntity().getUUID()) != null) EN_ATTENTE.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        CHARGES.clear();
        EN_ATTENTE.clear();
        ticks = 0;
        depuisMaj = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CHARGES.isEmpty()) return;
        if (++ticks < 20) return;
        ticks = 0;
        depuisMaj++;
        boolean periode = depuisMaj >= ClansConfig.CLASSEMENTS_MINUTES.get() * 60;
        if (!periode && EN_ATTENTE.isEmpty()) return;
        Map<Classements.Genre, MutableComponent> textes = new EnumMap<>(Classements.Genre.class);
        if (periode) {
            depuisMaj = 0;
            int d = ClansConfig.CLASSEMENTS_DISTANCE.get();
            for (Display.TextDisplay t : new ArrayList<>(CHARGES.values())) {
                if (EN_ATTENTE.contains(t.getUUID()) || joueurProche(t, d)) remplir(t, textes);
            }
        } else {
            for (UUID u : new ArrayList<>(EN_ATTENTE)) {
                Display.TextDisplay t = CHARGES.get(u);
                if (t != null) remplir(t, textes);
            }
        }
        EN_ATTENTE.clear();
    }

    private static boolean joueurProche(Display.TextDisplay t, int distance) {
        if (!(t.level() instanceof ServerLevel level)) return false;
        double d2 = (double) distance * distance;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(t) <= d2) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ actions du staff (/classement)
    /** Pose un hologramme ; null si un autre est deja a moins de 1,5 bloc de ce point (jamais deux au meme endroit). */
    @Nullable
    static Display.TextDisplay poser(ServerLevel level, Vec3 pos, Classements.Genre genre) {
        for (Display.TextDisplay t : CHARGES.values()) {
            if (t.level() == level && t.position().distanceToSqr(pos) < 1.5 * 1.5) return null;
        }
        Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(level);
        if (t == null) return null;
        // reglages par les donnees de l'entite (memes cles que /summon) : sans fond, tourne vers le joueur, plein eclairage
        CompoundTag tag = t.saveWithoutId(new CompoundTag());
        tag.putString("billboard", "center");
        CompoundTag lumiere = new CompoundTag();
        lumiere.putInt("block", 15);
        lumiere.putInt("sky", 15);
        tag.put("brightness", lumiere);
        tag.putInt("background", 0);
        tag.putBoolean("default_background", false);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putString("alignment", "left");
        tag.putInt("line_width", 400);
        tag.putFloat("view_range", 1.0f);
        // texte aux trois quarts de la taille de base (environ 3,5 blocs de large, 2,5 de haut)
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", flottants(0f, 0f, 0f, 1f));
        tr.put("right_rotation", flottants(0f, 0f, 0f, 1f));
        tr.put("translation", flottants(0f, 0f, 0f));
        tr.put("scale", flottants(ECHELLE, ECHELLE, ECHELLE));
        tag.put("transformation", tr);
        tag.putString("text", Component.Serializer.toJson(Component.literal("…")));
        t.load(tag);
        t.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        t.addTag(ETIQUETTE);
        t.addTag(PREFIXE_GENRE + genre.id);
        if (!level.addFreshEntity(t)) return null;
        remplir(t, new EnumMap<>(Classements.Genre.class));
        EN_ATTENTE.remove(t.getUUID());
        return t;
    }

    /** Retire l'hologramme charge le plus proche de ce point (a {@code rayon} blocs au plus) ; null s'il n'y en a pas. */
    @Nullable
    static Display.TextDisplay retirer(ServerLevel level, Vec3 pos, double rayon) {
        Display.TextDisplay proche = null;
        double best = rayon * rayon;
        for (Display.TextDisplay t : CHARGES.values()) {
            if (t.level() != level) continue;
            double d = t.position().distanceToSqr(pos);
            if (d <= best) {
                best = d;
                proche = t;
            }
        }
        if (proche != null) proche.discard();
        return proche;
    }

    /** Remet a jour tout de suite tous les hologrammes charges ({@code /classement maj}) ; rend leur nombre. */
    static int toutRafraichir() {
        Map<Classements.Genre, MutableComponent> textes = new EnumMap<>(Classements.Genre.class);
        int n = 0;
        for (Display.TextDisplay t : new ArrayList<>(CHARGES.values())) {
            remplir(t, textes);
            n++;
        }
        EN_ATTENTE.clear();
        depuisMaj = 0;
        return n;
    }

    /** Hologrammes charges : genre et position, pour {@code /classement liste}. */
    static List<Display.TextDisplay> charges() {
        return List.copyOf(CHARGES.values());
    }

    @Nullable
    static Classements.Genre genre(Display.TextDisplay t) {
        for (String s : t.getTags()) {
            if (s.startsWith(PREFIXE_GENRE)) return Classements.Genre.parId(s.substring(PREFIXE_GENRE.length()));
        }
        return null;
    }

    // ------------------------------------------------------------------ texte
    /** Met le texte du classement dans l'entite, seulement s'il a change (calcule une fois par genre et par passage). */
    private static void remplir(Display.TextDisplay t, Map<Classements.Genre, MutableComponent> textes) {
        if (t.isRemoved()) return;
        Classements.Genre g = genre(t);
        if (g == null) return;
        String json = Component.Serializer.toJson(textes.computeIfAbsent(g, Hologrammes::texte));
        // texte de l'entite par ses donnees (comme /data merge entity) : il part aux joueurs proches seulement s'il change
        CompoundTag tag = t.saveWithoutId(new CompoundTag());
        if (json.equals(tag.getString("text"))) return;
        tag.putString("text", json);
        try {
            t.load(tag);
        } catch (RuntimeException ex) {
            HeraliszClans.LOG.error("[classements] texte de l'hologramme {} impossible a changer", g.id, ex);
        }
    }

    private static int accent(Classements.Genre g) {
        return switch (g) {
            case ARGENT -> 0xE8C36A;
            case KILLS -> 0xFF6B6B;
            case CLANS -> 0x6FA8FF;
        };
    }

    private static MutableComponent texte(Classements.Genre g) {
        List<Classements.Ligne> lignes = Classements.calculer(g);
        MutableComponent c = Component.empty();
        // titre (et le numero de la saison pour les clans), puis une ligne vide
        c.append(tr("titre." + g.id, titreFr(g), String.valueOf(Math.max(1, Saison.numero()))).withStyle(style(POLICE_GRASSE, accent(g))));
        c.append("\n");
        if (lignes.isEmpty()) {
            c.append("\n").append(tr("vide", "Personne au classement pour l'instant").withStyle(style(POLICE, GRIS)));
        }
        for (int i = 0; i < lignes.size() && i < Classements.N; i++) {
            c.append("\n").append(ligne(g, i, lignes.get(i)));
        }
        c.append("\n\n").append(tr("maj", "Mis à jour toutes les %s min", String.valueOf(ClansConfig.CLASSEMENTS_MINUTES.get())).withStyle(style(POLICE_PETITE, GRIS)));
        if (g == Classements.Genre.CLANS) {
            c.append("\n").append(tr("regle", "Rang : somme des places PVE, PVP et événements").withStyle(style(POLICE_PETITE, GRIS)));
        }
        return c;
    }

    private static MutableComponent ligne(Classements.Genre g, int i, Classements.Ligne x) {
        boolean podium = i < 3;
        ResourceLocation police = podium ? POLICE_GRASSE : POLICE;
        MutableComponent l = Component.empty();
        l.append(Component.literal((i + 1) + ".  ").withStyle(style(POLICE_GRASSE, podium ? PODIUM[i] : SOMBRE)));
        if (g == Classements.Genre.CLANS) {
            l.append(Component.literal("[" + x.tag() + "] ").withStyle(style(POLICE_GRASSE, x.couleur())));
            l.append(Component.literal(court(x.nom(), NOM_CLAN_MAX)).withStyle(style(police, podium ? BLANC : CLAIR)));
        } else {
            l.append(Component.literal(x.nom()).withStyle(style(police, podium ? BLANC : CLAIR)));
            if (!x.tag().isEmpty()) l.append(Component.literal(" [" + x.tag() + "]").withStyle(style(POLICE, x.couleur())));
        }
        l.append(Component.literal("  —  ").withStyle(style(POLICE, SOMBRE)));
        String v = EconomieApi.nombre(x.valeur());
        int coulValeur = podium ? BLANC : CLAIR;
        switch (g) {
            case ARGENT -> {
                l.append(Component.literal(v + " ").withStyle(style(police, coulValeur)));
                l.append(Component.literal(JETON).withStyle(Style.EMPTY.withFont(POLICE_JETON)));
            }
            case KILLS -> l.append(tr("kills", "%s kills", v).withStyle(style(police, coulValeur)));
            case CLANS -> l.append(tr("points", "%s pts", v).withStyle(style(police, coulValeur)));
        }
        return l;
    }

    private static net.minecraft.nbt.ListTag flottants(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    private static String court(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    private static String titreFr(Classements.Genre g) {
        return switch (g) {
            case ARGENT -> "Les plus riches";
            case KILLS -> "Meilleurs tueurs";
            case CLANS -> "Saison %s · meilleurs clans";
        };
    }

    private static Style style(ResourceLocation police, int couleur) {
        return Style.EMPTY.withFont(police).withColor(couleur & 0xFFFFFF);
    }

    private static MutableComponent tr(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("heralisz_clans.classement.holo." + cle, fr, args);
    }
}
