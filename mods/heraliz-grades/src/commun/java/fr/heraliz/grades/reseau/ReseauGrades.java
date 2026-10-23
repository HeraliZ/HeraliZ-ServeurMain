package fr.heraliz.grades.reseau;

import fr.heraliz.grades.HeralizGrades;
import fr.heraliz.grades.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Canal réseau de HeraliZ Grades (protocole 5 depuis le 22/10 : surnom dans le tab) : menu /kit, menu /warp, tab (grades et clans des joueurs connectés),
 * définitions des grades (2.0.0) et éditeur des grades des op. Le serveur envoie l'état des kits (contenu, recharge) ; le
 * joueur ne peut que DEMANDER un kit par son identifiant : grade, recharge, place et contenu sont décidés et vérifiés par
 * le serveur. Un joueur avec un jar plus ancien (sans ce canal) est refusé à la connexion : il doit prendre le nouveau jar.
 */
public final class ReseauGrades {
    private ReseauGrades() {
    }

    private static final String VERSION = "5";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Bornes de lecture (un paquet trafiqué ne fait pas exploser la mémoire). */
    private static final int MAX_KITS = 16, MAX_OBJETS = 36, MAX_ID = 32, MAX_LIGNES = 500, MAX_WARPS = 32, MAX_TEXTE = 200;

    /** État reçu par le joueur : branché par la partie client (ouvre ou met à jour le menu). */
    public static final Crochet<EtatKits> RECEPTION_CLIENT = new Crochet<>();
    /** Infos du tab reçues par le joueur : branchées par la partie client (nouveau tab). */
    public static final Crochet<InfosTab> RECEPTION_TAB = new Crochet<>();
    /** Menu des warps reçu par le joueur : branché par la partie client. */
    public static final Crochet<EtatWarps> RECEPTION_WARPS = new Crochet<>();
    /** Demande d'un warp reçue par le serveur : branchée par la partie serveur. */
    public static final Crochet<Demande> RECEPTION_WARP = new Crochet<>();
    /** Demande d'un kit reçue par le serveur : branchée par la partie serveur. */
    public static final Crochet<Demande> RECEPTION_SERVEUR = new Crochet<>();

    /**
     * Un kit tel qu'affiché : identifiant (= grade), contenu, recharge ; debloque = ce joueur peut le prendre (kit de son
     * grade, ou kit Soutien s'il porte le tag) ; resteMs = temps avant de pouvoir le reprendre.
     */
    public record VueKit(String id, String grade, String nom, List<ItemStack> objets, long rechargeMs, boolean debloque, long resteMs) {
    }

    /**
     * État des kits pour un joueur (serveur -> joueur) : ouvrir = ouvrir le menu (sinon mise à jour s'il est ouvert) ;
     * kitJoueur = le kit de son grade.
     */
    public record EtatKits(boolean ouvrir, List<VueKit> kits, String kitJoueur) {
    }

    /**
     * Une ligne du tab : grade affiché (id), tag de clan (vide sans clan) avec sa couleur, et surnom (/nick, vide sans
     * surnom ou si le joueur n'y a plus droit).
     */
    public record LigneTab(UUID uuid, String grade, String clan, int couleurClan, String surnom) {
    }

    /** Grades et clans des joueurs connectés (serveur -> tous), renvoyés quand quelque chose change. */
    public record InfosTab(List<LigneTab> lignes) {
    }

    /** Demande d'un kit (joueur -> serveur). */
    public record Recuperer(String id) {
    }

    /**
     * Un warp tel qu'affiché : identifiant, nom et description (écrits par le staff), monde, rang de grade boutique demandé
     * (0 : tous), permis = ce joueur peut y aller, icône.
     */
    public record VueWarp(String id, String nom, String description, String monde, int rang, boolean permis, ItemStack icone) {
    }

    /** Menu des warps (serveur -> joueur) ; utilisable = le joueur est au spawn (sinon les boutons sont éteints). */
    public record EtatWarps(List<VueWarp> warps, boolean utilisable) {
    }

    /** Demande d'un warp (joueur -> serveur). */
    public record AllerWarp(String id) {
    }

    /** Définitions de tous les grades (serveur -> joueur), envoyées à la connexion et à chaque changement. */
    public record Definitions(List<fr.heraliz.grades.Grade.Def> grades) {
    }

    /** Un kit dans l'éditeur : grade, identifiant, nom, recharge (heures), contenu. */
    public record KitEdite(String grade, String id, String nom, int rechargeH, List<ItemStack> objets) {
    }

    /** Un joueur qui a un grade (onglet Joueurs de l'éditeur). */
    public record Membre(String grade, String nom, boolean enLigne) {
    }

    /** État de l'éditeur (serveur -> op) : ouvrir = ouvrir l'écran (sinon mise à jour), message = réponse à une action. */
    public record EtatEditeur(boolean ouvrir, List<KitEdite> kits, List<Membre> membres, String message) {
    }

    /** Action de l'éditeur (op -> serveur) : tout est revérifié par le serveur (op, /login, bornes). */
    public record ActionEditeur(Genre genre, String grade, fr.heraliz.grades.Grade.Def def, KitEdite kit) {
        public enum Genre { ENREGISTRER_GRADE, CREER_GRADE, SUPPRIMER_GRADE, ENREGISTRER_KIT, SUPPRIMER_KIT }
    }

    /** Action reçue par le serveur, avec l'op qui l'envoie. */
    public record ActionRecue(ServerPlayer joueur, ActionEditeur action) {
    }

    public static final Crochet<Definitions> RECEPTION_DEFINITIONS = new Crochet<>();
    public static final Crochet<EtatEditeur> RECEPTION_EDITEUR = new Crochet<>();
    public static final Crochet<ActionRecue> RECEPTION_ACTION = new Crochet<>();

    /** Demande reçue, avec le joueur qui l'envoie (côté serveur). */
    public record Demande(ServerPlayer joueur, String id) {
    }

    public static void enregistrer() {
        CANAL.messageBuilder(EtatKits.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauGrades::ecrire)
                .decoder(ReseauGrades::lire)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_CLIENT.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(Recuperer.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeUtf(m.id(), MAX_ID * 2))
                .decoder(b -> new Recuperer(b.readUtf(MAX_ID * 2)))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_SERVEUR.accept(new Demande(p, m.id()));
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(InfosTab.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                    int n = Math.min(m.lignes().size(), MAX_LIGNES);
                    b.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        LigneTab l = m.lignes().get(i);
                        b.writeUUID(l.uuid());
                        b.writeUtf(l.grade(), MAX_ID);
                        b.writeUtf(l.clan(), MAX_ID);
                        b.writeInt(l.couleurClan());
                        b.writeUtf(l.surnom(), MAX_ID);
                    }
                })
                .decoder(b -> {
                    int n = Math.min(b.readVarInt(), MAX_LIGNES);
                    List<LigneTab> lignes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) lignes.add(new LigneTab(b.readUUID(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readInt(), b.readUtf(MAX_ID)));
                    return new InfosTab(lignes);
                })
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_TAB.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(EtatWarps.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauGrades::ecrireWarps)
                .decoder(ReseauGrades::lireWarps)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_WARPS.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(AllerWarp.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeUtf(m.id(), MAX_ID))
                .decoder(b -> new AllerWarp(b.readUtf(MAX_ID)))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_WARP.accept(new Demande(p, m.id()));
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(Definitions.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> fr.heraliz.grades.Grade.ecrireTous(b))
                .decoder(b -> new Definitions(fr.heraliz.grades.Grade.lireTous(b)))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_DEFINITIONS.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(EtatEditeur.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauGrades::ecrireEditeur)
                .decoder(ReseauGrades::lireEditeur)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_EDITEUR.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CANAL.messageBuilder(ActionEditeur.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ReseauGrades::ecrireAction)
                .decoder(ReseauGrades::lireAction)
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_ACTION.accept(new ActionRecue(p, m));
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    private static final int MAX_KITS_EDITEUR = 128, MAX_MEMBRES = 1000, MAX_NOM_KIT = 32;

    private static void ecrireKit(FriendlyByteBuf b, KitEdite k) {
        b.writeUtf(k.grade(), MAX_ID);
        b.writeUtf(k.id(), MAX_ID);
        b.writeUtf(k.nom(), MAX_NOM_KIT);
        b.writeVarInt(k.rechargeH());
        int n = Math.min(k.objets().size(), MAX_OBJETS);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeItem(k.objets().get(i));
    }

    private static KitEdite lireKit(FriendlyByteBuf b) {
        String grade = b.readUtf(MAX_ID), id = b.readUtf(MAX_ID), nom = b.readUtf(MAX_NOM_KIT);
        int h = b.readVarInt();
        int n = Math.min(b.readVarInt(), MAX_OBJETS);
        List<ItemStack> objets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) objets.add(b.readItem());
        return new KitEdite(grade, id, nom, h, objets);
    }

    private static void ecrireEditeur(EtatEditeur m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        int nk = Math.min(m.kits().size(), MAX_KITS_EDITEUR);
        b.writeVarInt(nk);
        for (int i = 0; i < nk; i++) ecrireKit(b, m.kits().get(i));
        int nm = Math.min(m.membres().size(), MAX_MEMBRES);
        b.writeVarInt(nm);
        for (int i = 0; i < nm; i++) {
            Membre mb = m.membres().get(i);
            b.writeUtf(mb.grade(), MAX_ID);
            b.writeUtf(mb.nom(), MAX_ID);
            b.writeBoolean(mb.enLigne());
        }
        b.writeUtf(m.message(), MAX_TEXTE);
    }

    private static EtatEditeur lireEditeur(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int nk = Math.min(b.readVarInt(), MAX_KITS_EDITEUR);
        List<KitEdite> kits = new ArrayList<>(nk);
        for (int i = 0; i < nk; i++) kits.add(lireKit(b));
        int nm = Math.min(b.readVarInt(), MAX_MEMBRES);
        List<Membre> membres = new ArrayList<>(nm);
        for (int i = 0; i < nm; i++) membres.add(new Membre(b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readBoolean()));
        return new EtatEditeur(ouvrir, kits, membres, b.readUtf(MAX_TEXTE));
    }

    private static void ecrireAction(ActionEditeur m, FriendlyByteBuf b) {
        b.writeEnum(m.genre());
        b.writeUtf(m.grade(), MAX_ID);
        b.writeBoolean(m.def() != null);
        if (m.def() != null) fr.heraliz.grades.Grade.ecrireDef(b, m.def());
        b.writeBoolean(m.kit() != null);
        if (m.kit() != null) ecrireKit(b, m.kit());
    }

    private static ActionEditeur lireAction(FriendlyByteBuf b) {
        ActionEditeur.Genre genre = b.readEnum(ActionEditeur.Genre.class);
        String grade = b.readUtf(MAX_ID);
        fr.heraliz.grades.Grade.Def def = b.readBoolean() ? fr.heraliz.grades.Grade.lireDef(b) : null;
        KitEdite kit = b.readBoolean() ? lireKit(b) : null;
        return new ActionEditeur(genre, grade, def, kit);
    }

    private static void ecrireWarps(EtatWarps m, FriendlyByteBuf b) {
        int n = Math.min(m.warps().size(), MAX_WARPS);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            VueWarp w = m.warps().get(i);
            b.writeUtf(w.id(), MAX_ID);
            b.writeUtf(w.nom(), MAX_TEXTE);
            b.writeUtf(w.description(), MAX_TEXTE);
            b.writeUtf(w.monde(), MAX_TEXTE);
            b.writeVarInt(w.rang());
            b.writeBoolean(w.permis());
            b.writeItem(w.icone());
        }
        b.writeBoolean(m.utilisable());
    }

    private static EtatWarps lireWarps(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), MAX_WARPS);
        List<VueWarp> warps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            warps.add(new VueWarp(b.readUtf(MAX_ID), b.readUtf(MAX_TEXTE), b.readUtf(MAX_TEXTE), b.readUtf(MAX_TEXTE), b.readVarInt(),
                    b.readBoolean(), b.readItem()));
        }
        return new EtatWarps(warps, b.readBoolean());
    }

    private static void ecrire(EtatKits m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeVarInt(Math.min(m.kits().size(), MAX_KITS));
        for (int i = 0; i < Math.min(m.kits().size(), MAX_KITS); i++) {
            VueKit k = m.kits().get(i);
            b.writeUtf(k.id(), MAX_ID * 2);
            b.writeUtf(k.grade(), MAX_ID);
            b.writeUtf(k.nom(), MAX_ID);
            int n = Math.min(k.objets().size(), MAX_OBJETS);
            b.writeVarInt(n);
            for (int j = 0; j < n; j++) b.writeItem(k.objets().get(j));
            b.writeVarLong(k.rechargeMs());
            b.writeBoolean(k.debloque());
            b.writeVarLong(Math.max(0, k.resteMs()));
        }
        b.writeUtf(m.kitJoueur(), MAX_ID * 2);
    }

    private static EtatKits lire(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int nk = Math.min(b.readVarInt(), MAX_KITS);
        List<VueKit> kits = new ArrayList<>(nk);
        for (int i = 0; i < nk; i++) {
            String id = b.readUtf(MAX_ID * 2), grade = b.readUtf(MAX_ID), nom = b.readUtf(MAX_ID);
            int n = Math.min(b.readVarInt(), MAX_OBJETS);
            List<ItemStack> objets = new ArrayList<>(n);
            for (int j = 0; j < n; j++) objets.add(b.readItem());
            kits.add(new VueKit(id, grade, nom, objets, b.readVarLong(), b.readBoolean(), b.readVarLong()));
        }
        return new EtatKits(ouvrir, kits, b.readUtf(MAX_ID * 2));
    }
}
