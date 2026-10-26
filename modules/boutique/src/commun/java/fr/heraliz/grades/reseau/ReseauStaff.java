package fr.heraliz.grades.reseau;

import fr.heraliz.grades.HeralizGrades;
import fr.heraliz.grades.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canal du panel du staff (touche P, 25/10), à part de celui des grades. Le joueur ne fait que DEMANDER : ouvrir le
 * panel, la fiche d'un joueur, une action, une case des permissions. Le serveur revérifie tout (grade du staff, /login
 * AuthMe, droits réglés par le Fondateur, bornes) et répond par l'état du panel. Jars joueurs et serveur de la même
 * fabrication (version du canal).
 */
public final class ReseauStaff {
    private ReseauStaff() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "staff"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Bornes de lecture (un paquet trafiqué ne fait pas exploser la mémoire). */
    public static final int MAX_ID = 32, MAX_TEXTE = 160, MAX_LIGNES = 120, MAX_JOUEURS = 500;

    /** Une ligne du journal du staff : heure, staff et son grade, action, joueur visé, détail (durée, grade…), raison. */
    public record Ligne(long ms, String staff, String grade, String action, String cible, String detail, String raison) {
    }

    /**
     * État du panel (serveur -> staff) : rang (1 Guide … 5 Fondateur), grade affiché, actions permises, masques de toutes
     * les actions (Fondateur seulement, sinon vide), grades qu'il peut donner (Fondateur seulement), journal récent,
     * joueurs rendus muets depuis le panel, réponse à la dernière action.
     */
    public record Etat(boolean ouvrir, int rang, String grade, List<String> permises, Map<String, Integer> masques, List<String> grades,
                       List<Ligne> historique, List<UUID> muets, String message, boolean ok) {
    }

    /** Fiche d'un joueur connecté (serveur -> staff). */
    public record Fiche(UUID uuid, String nom, String monde, int x, int y, int z, long enJeuMs, int sanctions, long combatMs, boolean cache,
                        String gradeStaff, String gradeBoutique, List<Ligne> dernieres) {
    }

    /** Ouvrir (ou rafraîchir) le panel (staff -> serveur). */
    public record Ouvrir() {
    }

    /** Fiche d'un joueur (staff -> serveur). */
    public record DemandeFiche(UUID joueur) {
    }

    /** Une action (staff -> serveur) : joueur visé (null si aucun), option choisie, texte, retirer (grade). */
    public record Agir(String action, UUID cible, String option, String texte, boolean retirer) {
    }

    /** Une case de l'onglet Permissions (Fondateur -> serveur). */
    public record Permission(String action, int rang, boolean permis) {
    }

    /** Vider le chat (serveur -> chaque joueur). */
    public record ViderChat() {
    }

    /** Message reçu par le serveur, avec le staff qui l'envoie. */
    public record Recu<T>(ServerPlayer joueur, T message) {
    }

    public static final Crochet<Etat> RECEPTION_ETAT = new Crochet<>();
    public static final Crochet<Fiche> RECEPTION_FICHE = new Crochet<>();
    public static final Crochet<ViderChat> RECEPTION_VIDER = new Crochet<>();
    public static final Crochet<Recu<Ouvrir>> RECEPTION_OUVRIR = new Crochet<>();
    public static final Crochet<Recu<DemandeFiche>> RECEPTION_DEMANDE_FICHE = new Crochet<>();
    public static final Crochet<Recu<Agir>> RECEPTION_AGIR = new Crochet<>();
    public static final Crochet<Recu<Permission>> RECEPTION_PERMISSION = new Crochet<>();

    public static void enregistrer() {
        CANAL.messageBuilder(Etat.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauStaff::ecrireEtat).decoder(ReseauStaff::lireEtat)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_ETAT.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Fiche.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauStaff::ecrireFiche).decoder(ReseauStaff::lireFiche)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_FICHE.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(ViderChat.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                }).decoder(b -> new ViderChat())
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_VIDER.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Ouvrir.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                }).decoder(b -> new Ouvrir())
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_OUVRIR.accept(new Recu<>(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(DemandeFiche.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeUUID(m.joueur())).decoder(b -> new DemandeFiche(b.readUUID()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_DEMANDE_FICHE.accept(new Recu<>(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Agir.class, 5, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeUtf(m.action(), MAX_ID);
                    b.writeBoolean(m.cible() != null);
                    if (m.cible() != null) b.writeUUID(m.cible());
                    b.writeUtf(m.option(), MAX_ID);
                    b.writeUtf(m.texte(), MAX_TEXTE);
                    b.writeBoolean(m.retirer());
                })
                .decoder(b -> new Agir(b.readUtf(MAX_ID), b.readBoolean() ? b.readUUID() : null, b.readUtf(MAX_ID), b.readUtf(MAX_TEXTE), b.readBoolean()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_AGIR.accept(new Recu<>(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Permission.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeUtf(m.action(), MAX_ID);
                    b.writeVarInt(m.rang());
                    b.writeBoolean(m.permis());
                })
                .decoder(b -> new Permission(b.readUtf(MAX_ID), b.readVarInt(), b.readBoolean()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_PERMISSION.accept(new Recu<>(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    // ------------------------------------------------------------------ écriture et lecture
    private static void ecrireLigne(FriendlyByteBuf b, Ligne l) {
        b.writeLong(l.ms());
        b.writeUtf(l.staff(), MAX_ID);
        b.writeUtf(l.grade(), MAX_ID);
        b.writeUtf(l.action(), MAX_ID);
        b.writeUtf(l.cible(), MAX_ID);
        b.writeUtf(l.detail(), MAX_ID);
        b.writeUtf(l.raison(), MAX_TEXTE);
    }

    private static Ligne lireLigne(FriendlyByteBuf b) {
        return new Ligne(b.readLong(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readUtf(MAX_TEXTE));
    }

    private static void ecrireLignes(FriendlyByteBuf b, List<Ligne> l) {
        int n = Math.min(l.size(), MAX_LIGNES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) ecrireLigne(b, l.get(i));
    }

    private static List<Ligne> lireLignes(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), MAX_LIGNES);
        List<Ligne> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(lireLigne(b));
        return l;
    }

    private static void ecrireTextes(FriendlyByteBuf b, List<String> l) {
        int n = Math.min(l.size(), MAX_LIGNES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeUtf(l.get(i), MAX_ID);
    }

    private static List<String> lireTextes(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), MAX_LIGNES);
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readUtf(MAX_ID));
        return l;
    }

    private static void ecrireEtat(Etat m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeVarInt(m.rang());
        b.writeUtf(m.grade(), MAX_ID);
        ecrireTextes(b, m.permises());
        int n = Math.min(m.masques().size(), MAX_LIGNES);
        b.writeVarInt(n);
        int i = 0;
        for (Map.Entry<String, Integer> e : m.masques().entrySet()) {
            if (i++ >= n) break;
            b.writeUtf(e.getKey(), MAX_ID);
            b.writeVarInt(e.getValue());
        }
        ecrireTextes(b, m.grades());
        ecrireLignes(b, m.historique());
        int nm = Math.min(m.muets().size(), MAX_JOUEURS);
        b.writeVarInt(nm);
        for (int j = 0; j < nm; j++) b.writeUUID(m.muets().get(j));
        b.writeUtf(m.message(), MAX_TEXTE * 2);
        b.writeBoolean(m.ok());
    }

    private static Etat lireEtat(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int rang = b.readVarInt();
        String grade = b.readUtf(MAX_ID);
        List<String> permises = lireTextes(b);
        int n = Math.min(b.readVarInt(), MAX_LIGNES);
        Map<String, Integer> masques = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) masques.put(b.readUtf(MAX_ID), b.readVarInt());
        List<String> grades = lireTextes(b);
        List<Ligne> historique = lireLignes(b);
        int nm = Math.min(b.readVarInt(), MAX_JOUEURS);
        List<UUID> muets = new ArrayList<>(nm);
        for (int j = 0; j < nm; j++) muets.add(b.readUUID());
        return new Etat(ouvrir, rang, grade, permises, masques, grades, historique, muets, b.readUtf(MAX_TEXTE * 2), b.readBoolean());
    }

    private static void ecrireFiche(Fiche m, FriendlyByteBuf b) {
        b.writeUUID(m.uuid());
        b.writeUtf(m.nom(), MAX_ID);
        b.writeUtf(m.monde(), MAX_ID * 2);
        b.writeVarInt(m.x());
        b.writeVarInt(m.y());
        b.writeVarInt(m.z());
        b.writeVarLong(Math.max(0, m.enJeuMs()));
        b.writeVarInt(m.sanctions());
        b.writeVarLong(Math.max(0, m.combatMs()));
        b.writeBoolean(m.cache());
        b.writeUtf(m.gradeStaff(), MAX_ID);
        b.writeUtf(m.gradeBoutique(), MAX_ID);
        ecrireLignes(b, m.dernieres());
    }

    private static Fiche lireFiche(FriendlyByteBuf b) {
        return new Fiche(b.readUUID(), b.readUtf(MAX_ID), b.readUtf(MAX_ID * 2), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(),
                b.readVarInt(), b.readVarLong(), b.readBoolean(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), lireLignes(b));
    }
}
