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
 * Canal réseau de HeraliZ Grades (protocole 3) : menu /kit, menu /warp et tab (grades et clans des joueurs connectés). Le serveur envoie l'état des kits (contenu, recharge) ; le
 * joueur ne peut que DEMANDER un kit par son identifiant : grade, recharge, place et contenu sont décidés et vérifiés par
 * le serveur. Un joueur avec un jar plus ancien (sans ce canal) est refusé à la connexion : il doit prendre le nouveau jar.
 */
public final class ReseauGrades {
    private ReseauGrades() {
    }

    private static final String VERSION = "3";
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
    public record VueKit(String id, List<ItemStack> objets, long rechargeMs, boolean debloque, long resteMs) {
    }

    /**
     * État des kits pour un joueur (serveur -> joueur) : ouvrir = ouvrir le menu (sinon mise à jour s'il est ouvert) ;
     * kitJoueur = le kit de son grade.
     */
    public record EtatKits(boolean ouvrir, List<VueKit> kits, String kitJoueur) {
    }

    /** Une ligne du tab : grade affiché (id) et tag de clan (vide sans clan) avec sa couleur. */
    public record LigneTab(UUID uuid, String grade, String clan, int couleurClan) {
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
                .encoder((m, b) -> b.writeUtf(m.id(), MAX_ID))
                .decoder(b -> new Recuperer(b.readUtf(MAX_ID)))
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
                    }
                })
                .decoder(b -> {
                    int n = Math.min(b.readVarInt(), MAX_LIGNES);
                    List<LigneTab> lignes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) lignes.add(new LigneTab(b.readUUID(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readInt()));
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
            b.writeUtf(k.id(), MAX_ID);
            int n = Math.min(k.objets().size(), MAX_OBJETS);
            b.writeVarInt(n);
            for (int j = 0; j < n; j++) b.writeItem(k.objets().get(j));
            b.writeVarLong(k.rechargeMs());
            b.writeBoolean(k.debloque());
            b.writeVarLong(Math.max(0, k.resteMs()));
        }
        b.writeUtf(m.kitJoueur(), MAX_ID);
    }

    private static EtatKits lire(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int nk = Math.min(b.readVarInt(), MAX_KITS);
        List<VueKit> kits = new ArrayList<>(nk);
        for (int i = 0; i < nk; i++) {
            String id = b.readUtf(MAX_ID);
            int n = Math.min(b.readVarInt(), MAX_OBJETS);
            List<ItemStack> objets = new ArrayList<>(n);
            for (int j = 0; j < n; j++) objets.add(b.readItem());
            kits.add(new VueKit(id, objets, b.readVarLong(), b.readBoolean(), b.readVarLong()));
        }
        return new EtatKits(ouvrir, kits, b.readUtf(MAX_ID));
    }
}
