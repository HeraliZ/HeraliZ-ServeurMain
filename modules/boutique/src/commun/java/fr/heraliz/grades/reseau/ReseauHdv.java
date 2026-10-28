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
 * Canal de l'hôtel des ventes ({@code /hdv}, PNJ commissaire-priseur), protocole 1 (Grades 2.9.0). Le joueur ne fait
 * que DEMANDER : une page de la liste (filtres), acheter une offre par son identifiant, mettre en vente une case de son
 * inventaire avec une monnaie, un prix et une durée, retirer, récupérer. Le serveur relit tout (objet, règles, prix,
 * soldes) ; les PB sont débités et versés par le site.
 */
public final class ReseauHdv {
    private ReseauHdv() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizGrades.MODID, "hdv"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Offres par page de la liste (8 lignes, comme la maquette). */
    public static final int PAR_PAGE = 8;
    /** Lignes de « Mes ventes » envoyées au plus. */
    public static final int MES_MAX = 60;
    private static final int MAX_ID = 64, MAX_TEXTE = 200, MAX_NOM = 32, MAX_IDS = 64;

    // ------------------------------------------------------------------ joueur -> serveur
    /**
     * Une page de la liste : recherche (texte tapé) et objets dont le nom, dans la langue du joueur, contient ce texte
     * ({@code ids}, cherchés par son jeu) ; catégorie (0 tout), monnaie (0 toutes, 1 hérals, 2 PB), tri (0 récent,
     * 1 prix croissant, 2 prix décroissant), page (0…).
     */
    public record Liste(String recherche, List<String> ids, int categorie, int devise, int tri, int page) {
    }

    /** Types d'action. */
    public static final int ACHETER = 1, VENDRE = 2, RETIRER = 3, RECUPERER = 4, MES_VENTES = 5, FERMER = 6;

    /**
     * Une action : acheter (id de l'offre, devise et prix attendus), vendre (case de l'inventaire, objet et nombre
     * attendus, devise, prix, durée = rang dans la liste des durées), retirer (id de l'offre), récupérer (id de la ligne,
     * vide : tout), demander « Mes ventes », fermer l'écran.
     */
    public record Action(int type, String id, int slot, int devise, long prix, int duree, int nombre) {
    }

    // ------------------------------------------------------------------ serveur -> joueur
    /**
     * État général : ouvrir l'écran ; soldes (hérals, PB : -1 inconnu) ; offres en cours sur tout l'hôtel ; ventes du
     * joueur en cours, maximum, lignes à récupérer ; frais des ventes en PB (%) ; durées (h) ; plafond des prix en PB
     * par rareté (index 0 : sans rareté, puis Commun … Unique) ; prix maximal en hérals ; PB reliés au site ; message de
     * la dernière action (clé de langue sans préfixe, vide sinon) et réussite.
     */
    public record Etat(boolean ouvrir, long herals, long pb, int offres, int mesEnVente, int ventesMax, int aRecuperer, int fraisPct,
                       List<Integer> durees, List<Long> plafondsPb, long prixHeralsMax, boolean pbActif, String message, boolean ok) {
    }

    /** Une offre de la liste : reste en ms ; devise 0 hérals / 1 PB ; total = ce que paie l'acheteur ; mienne : vendue par lui. */
    public record LigneOffre(String id, ItemStack objet, String vendeur, long resteMs, int devise, long prix, long total, boolean reservee,
                             boolean mienne) {
    }

    /** Une page de la liste et le nombre d'offres de chaque catégorie (avec la recherche et la monnaie choisies). */
    public record Page(int page, int pages, List<Integer> parCategorie, List<LigneOffre> lignes) {
    }

    /** Genres d'une ligne de « Mes ventes ». */
    public static final int EN_VENTE = 0, RESERVEE = 1, VENDU_HERALS = 2, VENDU_PB_VERSE = 3, VENDU_PB_ATTENTE = 4, EXPIRE = 5, RETIRE = 6,
            ACHETE = 7, HERALS_ATTENTE = 8;

    /**
     * Une ligne de « Mes ventes » : genre, id (offre ou ligne à récupérer), objet, l'autre joueur (acheteur), temps (reste
     * pour une vente en cours, ancienneté sinon, ms), devise, prix, bouton (retirer ou récupérer) actif.
     */
    public record LigneMv(int genre, String id, ItemStack objet, String autre, long tempsMs, int devise, long prix, boolean action) {
    }

    public record MesVentes(List<LigneMv> lignes) {
    }

    // ------------------------------------------------------------------ crochets
    public record ListeRecue(ServerPlayer joueur, Liste liste) {
    }

    public record ActionRecue(ServerPlayer joueur, Action action) {
    }

    public static final Crochet<Etat> RECEPTION_ETAT = new Crochet<>();
    public static final Crochet<Page> RECEPTION_PAGE = new Crochet<>();
    public static final Crochet<MesVentes> RECEPTION_MES = new Crochet<>();
    public static final Crochet<ListeRecue> RECEPTION_LISTE = new Crochet<>();
    public static final Crochet<ActionRecue> RECEPTION_ACTION = new Crochet<>();

    public static void enregistrer() {
        CANAL.messageBuilder(Liste.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ReseauHdv::ecrireListe).decoder(ReseauHdv::lireListe)
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_LISTE.accept(new ListeRecue(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Action.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeVarInt(m.type());
                    b.writeUtf(m.id(), MAX_ID);
                    b.writeVarInt(m.slot());
                    b.writeVarInt(m.devise());
                    b.writeVarLong(m.prix());
                    b.writeVarInt(m.duree());
                    b.writeVarInt(m.nombre());
                })
                .decoder(b -> new Action(b.readVarInt(), b.readUtf(MAX_ID), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readVarInt()))
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_ACTION.accept(new ActionRecue(p, m));
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Etat.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauHdv::ecrireEtat).decoder(ReseauHdv::lireEtat)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_ETAT.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(Page.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauHdv::ecrirePage).decoder(ReseauHdv::lirePage)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_PAGE.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
        CANAL.messageBuilder(MesVentes.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReseauHdv::ecrireMes).decoder(ReseauHdv::lireMes)
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_MES.accept(m);
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    // ------------------------------------------------------------------ encodage
    private static void ecrireListe(Liste m, FriendlyByteBuf b) {
        b.writeUtf(m.recherche(), MAX_NOM);
        int n = Math.min(m.ids().size(), MAX_IDS);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeUtf(m.ids().get(i), MAX_ID);
        b.writeVarInt(m.categorie());
        b.writeVarInt(m.devise());
        b.writeVarInt(m.tri());
        b.writeVarInt(m.page());
    }

    private static Liste lireListe(FriendlyByteBuf b) {
        String r = b.readUtf(MAX_NOM);
        int n = b.readVarInt();
        if (n < 0 || n > MAX_IDS) throw new IllegalArgumentException("hdv : trop d'objets cherchés");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(b.readUtf(MAX_ID));
        return new Liste(r, ids, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    private static void ecrireEtat(Etat m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeVarLong(m.herals());
        b.writeLong(m.pb());
        b.writeVarInt(m.offres());
        b.writeVarInt(m.mesEnVente());
        b.writeVarInt(m.ventesMax());
        b.writeVarInt(m.aRecuperer());
        b.writeVarInt(m.fraisPct());
        b.writeVarInt(Math.min(8, m.durees().size()));
        for (int i = 0; i < Math.min(8, m.durees().size()); i++) b.writeVarInt(m.durees().get(i));
        b.writeVarInt(Math.min(8, m.plafondsPb().size()));
        for (int i = 0; i < Math.min(8, m.plafondsPb().size()); i++) b.writeVarLong(m.plafondsPb().get(i));
        b.writeVarLong(m.prixHeralsMax());
        b.writeBoolean(m.pbActif());
        b.writeUtf(m.message(), MAX_TEXTE);
        b.writeBoolean(m.ok());
    }

    private static Etat lireEtat(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        long herals = b.readVarLong(), pb = b.readLong();
        int offres = b.readVarInt(), mes = b.readVarInt(), max = b.readVarInt(), recup = b.readVarInt(), frais = b.readVarInt();
        int nd = Math.min(8, b.readVarInt());
        List<Integer> durees = new ArrayList<>();
        for (int i = 0; i < nd; i++) durees.add(b.readVarInt());
        int np = Math.min(8, b.readVarInt());
        List<Long> plafonds = new ArrayList<>();
        for (int i = 0; i < np; i++) plafonds.add(b.readVarLong());
        return new Etat(ouvrir, herals, pb, offres, mes, max, recup, frais, durees, plafonds, b.readVarLong(), b.readBoolean(), b.readUtf(MAX_TEXTE),
                b.readBoolean());
    }

    private static void ecrirePage(Page m, FriendlyByteBuf b) {
        b.writeVarInt(m.page());
        b.writeVarInt(m.pages());
        b.writeVarInt(m.parCategorie().size());
        for (int c : m.parCategorie()) b.writeVarInt(c);
        int n = Math.min(m.lignes().size(), PAR_PAGE);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            LigneOffre o = m.lignes().get(i);
            b.writeUtf(o.id(), MAX_ID);
            b.writeItem(o.objet());
            b.writeUtf(o.vendeur(), MAX_NOM);
            b.writeVarLong(o.resteMs());
            b.writeVarInt(o.devise());
            b.writeVarLong(o.prix());
            b.writeVarLong(o.total());
            b.writeBoolean(o.reservee());
            b.writeBoolean(o.mienne());
        }
    }

    private static Page lirePage(FriendlyByteBuf b) {
        int page = b.readVarInt(), pages = b.readVarInt();
        int nc = Math.min(16, b.readVarInt());
        List<Integer> cats = new ArrayList<>();
        for (int i = 0; i < nc; i++) cats.add(b.readVarInt());
        int n = Math.min(PAR_PAGE, b.readVarInt());
        List<LigneOffre> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new LigneOffre(b.readUtf(MAX_ID), b.readItem(), b.readUtf(MAX_NOM), b.readVarLong(), b.readVarInt(), b.readVarLong(), b.readVarLong(),
                    b.readBoolean(), b.readBoolean()));
        }
        return new Page(page, pages, cats, l);
    }

    private static void ecrireMes(MesVentes m, FriendlyByteBuf b) {
        int n = Math.min(m.lignes().size(), MES_MAX);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            LigneMv l = m.lignes().get(i);
            b.writeVarInt(l.genre());
            b.writeUtf(l.id(), MAX_ID);
            b.writeItem(l.objet());
            b.writeUtf(l.autre(), MAX_NOM);
            b.writeVarLong(Math.max(0, l.tempsMs()));
            b.writeVarInt(l.devise());
            b.writeVarLong(l.prix());
            b.writeBoolean(l.action());
        }
    }

    private static MesVentes lireMes(FriendlyByteBuf b) {
        int n = Math.min(MES_MAX, b.readVarInt());
        List<LigneMv> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new LigneMv(b.readVarInt(), b.readUtf(MAX_ID), b.readItem(), b.readUtf(MAX_NOM), b.readVarLong(), b.readVarInt(), b.readVarLong(),
                    b.readBoolean()));
        }
        return new MesVentes(l);
    }
}
