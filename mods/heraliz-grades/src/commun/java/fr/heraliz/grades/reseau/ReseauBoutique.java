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

/**
 * Canal de la boutique en jeu (/boutique) et des échanges entre joueurs (/trade), protocole 1. Le joueur ne fait que
 * DEMANDER (acheter un article par son identifiant, proposer une case de son inventaire, des PB, se dire prêt) : prix,
 * soldes, objets et échanges sont décidés par le serveur, et les PB par le site.
 */
public final class ReseauBoutique {
    private ReseauBoutique() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "boutique"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static final int MAX_OFFRES = 64, MAX_ID = 32, MAX_TEXTE = 300, MAX_CASES = 9;
    /** Cases d'objets proposées par chaque joueur dans un échange. */
    public static final int CASES_ECHANGE = MAX_CASES;

    /** Un article : identifiant, catégorie, nom, description, prix en PB, couleur, icône, mis en avant. */
    public record Offre(String id, String categorie, String nom, String description, long prix, int couleur, ItemStack icone, boolean avant) {
    }

    /**
     * État de la boutique (serveur -> joueur) : ouvrir = ouvrir l'écran ; solde = PB du compte (-1 : inconnu, site
     * injoignable) ; message = réponse au dernier achat (vide sinon), ok = achat réussi.
     */
    public record EtatBoutique(boolean ouvrir, long solde, List<Offre> offres, String message, boolean ok) {
    }

    /** Achat d'un article (joueur -> serveur). */
    public record Acheter(String id) {
    }

    /**
     * État d'un échange (serveur -> chacun des deux joueurs, vu de son côté). phase : 0 en cours, 1 compte à rebours,
     * 2 exécution, 3 fini (réussi), 4 annulé ; reste = ticks du compte à rebours ; mesCases = cases de l'inventaire
     * proposées (même ordre que mesObjets).
     */
    public record EtatEchange(String autre, int phase, int reste, List<ItemStack> mesObjets, List<Integer> mesCases, long mesPb, boolean monPret,
                              List<ItemStack> sesObjets, long sesPb, boolean sonPret, long monSolde, String message) {
    }

    /** Action dans un échange (joueur -> serveur) : valeur = case de l'inventaire, rang de l'objet proposé, ou PB. */
    public record ActionEchange(Genre genre, long valeur) {
        public enum Genre { PROPOSER, RETIRER, PB, PRET, ANNULER }
    }

    public record AchatRecu(ServerPlayer joueur, String id) {
    }

    public record ActionRecue(ServerPlayer joueur, ActionEchange action) {
    }

    public static final Crochet<EtatBoutique> RECEPTION_BOUTIQUE = new Crochet<>();
    public static final Crochet<EtatEchange> RECEPTION_ECHANGE = new Crochet<>();
    public static final Crochet<AchatRecu> RECEPTION_ACHAT = new Crochet<>();
    public static final Crochet<ActionRecue> RECEPTION_ACTION = new Crochet<>();

    public static void enregistrer() {
        CANAL.messageBuilder(EtatBoutique.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauBoutique::ecrireBoutique).decoder(ReseauBoutique::lireBoutique)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_BOUTIQUE.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Acheter.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeUtf(m.id(), MAX_ID)).decoder(b -> new Acheter(b.readUtf(MAX_ID)))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_ACHAT.accept(new AchatRecu(p, m.id()));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(EtatEchange.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauBoutique::ecrireEchange).decoder(ReseauBoutique::lireEchange)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_ECHANGE.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(ActionEchange.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeEnum(m.genre());
                    b.writeVarLong(m.valeur());
                })
                .decoder(b -> new ActionEchange(b.readEnum(ActionEchange.Genre.class), b.readVarLong()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_ACTION.accept(new ActionRecue(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    private static void ecrireBoutique(EtatBoutique m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeLong(m.solde());
        int n = Math.min(m.offres().size(), MAX_OFFRES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Offre o = m.offres().get(i);
            b.writeUtf(o.id(), MAX_ID);
            b.writeUtf(o.categorie(), MAX_ID);
            b.writeUtf(o.nom(), MAX_TEXTE);
            b.writeUtf(o.description(), MAX_TEXTE);
            b.writeVarLong(o.prix());
            b.writeInt(o.couleur());
            b.writeItem(o.icone());
            b.writeBoolean(o.avant());
        }
        b.writeUtf(m.message(), MAX_TEXTE);
        b.writeBoolean(m.ok());
    }

    private static EtatBoutique lireBoutique(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        long solde = b.readLong();
        int n = Math.min(b.readVarInt(), MAX_OFFRES);
        List<Offre> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new Offre(b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readUtf(MAX_TEXTE), b.readUtf(MAX_TEXTE), b.readVarLong(), b.readInt(), b.readItem(), b.readBoolean()));
        }
        return new EtatBoutique(ouvrir, solde, l, b.readUtf(MAX_TEXTE), b.readBoolean());
    }

    private static void objets(FriendlyByteBuf b, List<ItemStack> l) {
        int n = Math.min(l.size(), MAX_CASES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeItem(l.get(i));
    }

    private static List<ItemStack> objets(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), MAX_CASES);
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(b.readItem());
        return l;
    }

    private static void ecrireEchange(EtatEchange m, FriendlyByteBuf b) {
        b.writeUtf(m.autre(), 32);
        b.writeVarInt(m.phase());
        b.writeVarInt(m.reste());
        objets(b, m.mesObjets());
        int n = Math.min(m.mesCases().size(), MAX_CASES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeVarInt(m.mesCases().get(i));
        b.writeVarLong(m.mesPb());
        b.writeBoolean(m.monPret());
        objets(b, m.sesObjets());
        b.writeVarLong(m.sesPb());
        b.writeBoolean(m.sonPret());
        b.writeLong(m.monSolde());
        b.writeUtf(m.message(), MAX_TEXTE);
    }

    private static EtatEchange lireEchange(FriendlyByteBuf b) {
        String autre = b.readUtf(32);
        int phase = b.readVarInt(), reste = b.readVarInt();
        List<ItemStack> mes = objets(b);
        int n = Math.min(b.readVarInt(), MAX_CASES);
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < n; i++) cases.add(b.readVarInt());
        long mesPb = b.readVarLong();
        boolean monPret = b.readBoolean();
        List<ItemStack> ses = objets(b);
        long sesPb = b.readVarLong();
        boolean sonPret = b.readBoolean();
        return new EtatEchange(autre, phase, reste, mes, cases, mesPb, monPret, ses, sesPb, sonPret, b.readLong(), b.readUtf(MAX_TEXTE));
    }
}
