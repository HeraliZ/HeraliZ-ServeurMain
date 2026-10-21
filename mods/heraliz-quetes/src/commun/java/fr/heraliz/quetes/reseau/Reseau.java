package fr.heraliz.quetes.reseau;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canal réseau de HeraliZ Quêtes (protocole 2 : marchands ; 3 : radio du Commandant Varga). Le serveur décide de tout et envoie ce qu'il faut afficher (dialogue,
 * journal, suivi, notes, lueurs des points) ; le joueur ne fait que CHOISIR (une réponse du dialogue, suivre ou
 * abandonner une quête, un code tapé) : chaque demande est revérifiée par le serveur. Un joueur avec un jar sans ce
 * canal est refusé à la connexion.
 */
public final class Reseau {
    private Reseau() {
    }

    private static final String VERSION = "6";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizQuetes.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static final int MAX_LISTE = 64, MAX_CLE = 96;

    // ------------------------------------------------------------------ messages
    /** Réponse proposée dans un dialogue. style : 0 normal, 1 nouvelle quête, 2 donner / rendre, 3 retour / au revoir, 4 accepter. */
    public record Option(int id, Texte libelle, int style) {
    }

    /** Page de dialogue (serveur -> joueur) ; entite = l'entité PNJ (pour la caméra et la distance). */
    public record Dialogue(int entite, Texte nom, Texte role, Texte texte, List<Option> options) {
    }

    /** Réponse choisie (joueur -> serveur) ; option -1 = dialogue fermé. */
    public record Choix(int entite, int option) {
    }

    /** etat : 0 à venir, 1 en cours, 2 fait. */
    public record JEtape(Texte libelle, int fait, int total, int etat) {
    }

    public record JQuete(String cle, Texte titre, int difficulte, boolean contrat, boolean suivie, boolean aRendre,
                         Texte recompense, Texte resume, List<JEtape> etapes) {
    }

    public record JPnj(String id, Texte nom, Texte role, int niveau, int rep, int repSuivant, List<JQuete> quetes) {
    }

    /** Journal des quêtes (/quetes) ; ouvrir = ouvrir l'écran, sinon mettre à jour celui qui est ouvert. */
    public record Journal(boolean ouvrir, List<JPnj> pnj, List<Texte> titres, int finies) {
    }

    /** Demande du journal (touche ou /quetes côté joueur). */
    public record DemandeJournal(boolean ouvrir) {
    }

    /** type : 1 suivre, 2 ne plus suivre, 3 abandonner. */
    public record Action(int type, String cle) {
    }

    /** distance : mètres jusqu'au but le plus proche, -1 = rien à montrer ; approx = zone floue (« ≈ 300 m »). */
    public record SLigne(Texte libelle, int fait, int total, int distance, boolean approx, int etat) {
    }

    /** Quête suivie, affichée à droite de l'écran ; titre vide = rien de suivi. */
    public record Suivi(Texte pnj, Texte titre, List<SLigne> lignes) {
    }

    /** Note, lettre, indice (clic droit sur un point « indice »). */
    public record Note(Texte titre, Texte texte) {
    }

    /** Clavier d'un coffre à code (clic droit sur un point « code »). */
    public record Clavier(String point, Texte titre) {
    }

    /** Code tapé (joueur -> serveur). */
    public record Code(String point, String code) {
    }

    /** Lueurs à montrer : points de la quête en cours (près du joueur), et tous les points pour le staff à la baguette. */
    public record Lueurs(List<BlockPos> quete, List<BlockPos> staff) {
    }

    /** Petite notification en haut à droite ; couleur RGB. */
    public record Notif(Texte texte, int couleur) {
    }

    /** Un article d'un marchand ; rarete : rang 0 (Commun) à 5, -1 sans rareté. */
    public record Article(int index, ItemStack objet, long prix, int rarete) {
    }

    /**
     * Écran d'un marchand (serveur -> joueur). rachat = il rachète : prixSlots et rarSlots donnent, pour chacune des 36
     * cases de l'inventaire, le prix de rachat de l'objet (0 = il ne le prend pas) et sa rareté ; sinon il vend ses
     * articles. ouvrir = ouvrir l'écran, sinon mettre à jour celui qui est ouvert. entite -1 = fermer.
     */
    public record Boutique(int entite, boolean ouvrir, Texte nom, Texte role, long solde, boolean rachat, List<Article> articles,
                           int[] prixSlots, int[] rarSlots) {
    }

    /** Achat d'un article (joueur -> serveur). */
    public record Achat(int entite, int index, int quantite) {
    }

    /** Vente des objets de ces cases de l'inventaire (joueur -> serveur). */
    public record Vente(int entite, List<Integer> cases) {
    }

    /**
     * Message radio d'un PNJ, en bas à gauche de l'écran (le Commandant Varga pendant le tutoriel des bases) : son nom,
     * son apparence (CaraCrea, vide sans), son skin de secours, la phrase, et l'étape (étape 0 = sans barre d'étapes).
     * Phrase vide = ranger la radio.
     */
    public record Radio(Texte nom, CompoundTag apparence, String skin, boolean brasFins, int etape, int total, Texte texte) {
    }

    /**
     * Présentation d'un PNJ (Quêtes 1.8.0 : le Bâtisseur, à la première arrivée au spawn après le tutoriel des bases) :
     * la caméra du joueur fait lentement le tour du point donné (rayon en blocs), pendant {@code ticks} ; seule la radio
     * reste à l'écran. Espace maintenu pour passer.
     */
    public record Presentation(double x, double y, double z, float rayon, int ticks) {
    }

    /** Éditeur des marchands (staff op) : un article vendu, ou une règle de rachat et ses 6 prix (Commun ... Mythique). */
    public record EArticle(ItemStack objet, long prix, int rarete) {
    }

    public record ERegle(String tag, long[] prix) {
    }

    /** Ouvre (ou met à jour) l'éditeur d'un marchand (serveur -> joueur). */
    public record Edition(String pnj, Texte nom, boolean rachat, boolean modifie, List<EArticle> articles, List<ERegle> regles) {
    }

    /** Un changement demandé dans l'éditeur (joueur -> serveur) ; le serveur revérifie les droits. */
    public record Modif(String pnj, int type, int index, int rarete, long prix) {
        public static final int PRIX = 0, RETIRER = 1, AJOUTER_MAIN = 2, PRIX_RACHAT = 3, REINITIALISER = 4, MONTER = 5;
    }

    /** Message reçu par le serveur, avec le joueur qui l'envoie. */
    public record Recu<T>(ServerPlayer joueur, T message) {
    }

    // ------------------------------------------------------------------ crochets (branchés par chaque côté)
    public static final Crochet<Dialogue> DIALOGUE = new Crochet<>();
    public static final Crochet<Journal> JOURNAL = new Crochet<>();
    public static final Crochet<Suivi> SUIVI = new Crochet<>();
    public static final Crochet<Note> NOTE = new Crochet<>();
    public static final Crochet<Clavier> CLAVIER = new Crochet<>();
    public static final Crochet<Lueurs> LUEURS = new Crochet<>();
    public static final Crochet<Notif> NOTIF = new Crochet<>();
    public static final Crochet<Boutique> BOUTIQUE = new Crochet<>();
    public static final Crochet<Radio> RADIO = new Crochet<>();
    public static final Crochet<Presentation> PRESENTATION = new Crochet<>();
    public static final Crochet<Edition> EDITION = new Crochet<>();

    public static final Crochet<Recu<Choix>> CHOIX = new Crochet<>();
    public static final Crochet<Recu<DemandeJournal>> DEMANDE_JOURNAL = new Crochet<>();
    public static final Crochet<Recu<Action>> ACTION = new Crochet<>();
    public static final Crochet<Recu<Code>> CODE = new Crochet<>();
    public static final Crochet<Recu<Achat>> ACHAT = new Crochet<>();
    public static final Crochet<Recu<Vente>> VENTE = new Crochet<>();
    public static final Crochet<Recu<Modif>> MODIF = new Crochet<>();

    private static int prochain;

    public static void enregistrer() {
        versJoueur(Dialogue.class, Reseau::ecrireDialogue, Reseau::lireDialogue, DIALOGUE);
        versJoueur(Journal.class, Reseau::ecrireJournal, Reseau::lireJournal, JOURNAL);
        versJoueur(Suivi.class, Reseau::ecrireSuivi, Reseau::lireSuivi, SUIVI);
        versJoueur(Note.class, (m, b) -> {
            m.titre().ecrire(b);
            m.texte().ecrire(b);
        }, b -> new Note(Texte.lire(b), Texte.lire(b)), NOTE);
        versJoueur(Clavier.class, (m, b) -> {
            b.writeUtf(m.point(), MAX_CLE);
            m.titre().ecrire(b);
        }, b -> new Clavier(b.readUtf(MAX_CLE), Texte.lire(b)), CLAVIER);
        versJoueur(Lueurs.class, (m, b) -> {
            ecrirePos(b, m.quete());
            ecrirePos(b, m.staff());
        }, b -> new Lueurs(lirePos(b), lirePos(b)), LUEURS);
        versJoueur(Notif.class, (m, b) -> {
            m.texte().ecrire(b);
            b.writeInt(m.couleur());
        }, b -> new Notif(Texte.lire(b), b.readInt()), NOTIF);

        versServeur(Choix.class, (m, b) -> {
            b.writeVarInt(m.entite());
            b.writeVarInt(m.option());
        }, b -> new Choix(b.readVarInt(), b.readVarInt()), CHOIX);
        versServeur(DemandeJournal.class, (m, b) -> b.writeBoolean(m.ouvrir()), b -> new DemandeJournal(b.readBoolean()), DEMANDE_JOURNAL);
        versServeur(Action.class, (m, b) -> {
            b.writeVarInt(m.type());
            b.writeUtf(m.cle(), MAX_CLE);
        }, b -> new Action(b.readVarInt(), b.readUtf(MAX_CLE)), ACTION);
        versServeur(Code.class, (m, b) -> {
            b.writeUtf(m.point(), MAX_CLE);
            b.writeUtf(m.code(), 32);
        }, b -> new Code(b.readUtf(MAX_CLE), b.readUtf(32)), CODE);
        versJoueur(Boutique.class, Reseau::ecrireBoutique, Reseau::lireBoutique, BOUTIQUE);
        versServeur(Achat.class, (m, b) -> {
            b.writeVarInt(m.entite());
            b.writeVarInt(m.index());
            b.writeVarInt(m.quantite());
        }, b -> new Achat(b.readVarInt(), b.readVarInt(), b.readVarInt()), ACHAT);
        versServeur(Vente.class, (m, b) -> {
            b.writeVarInt(m.entite());
            int n = Math.min(m.cases().size(), CASES);
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) b.writeVarInt(m.cases().get(i));
        }, b -> {
            int e = b.readVarInt();
            int n = Math.min(b.readVarInt(), CASES);
            List<Integer> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(b.readVarInt());
            return new Vente(e, l);
        }, VENTE);
        versJoueur(Radio.class, (m, b) -> {
            m.nom().ecrire(b);
            b.writeNbt(m.apparence());
            b.writeUtf(m.skin(), MAX_CLE);
            b.writeBoolean(m.brasFins());
            b.writeVarInt(m.etape());
            b.writeVarInt(m.total());
            m.texte().ecrire(b);
        }, b -> {
            Texte nom = Texte.lire(b);
            CompoundTag app = b.readNbt();
            return new Radio(nom, app == null ? new CompoundTag() : app, b.readUtf(MAX_CLE), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                    Texte.lire(b));
        }, RADIO);
        versJoueur(Presentation.class, (m, b) -> {
            b.writeDouble(m.x());
            b.writeDouble(m.y());
            b.writeDouble(m.z());
            b.writeFloat(m.rayon());
            b.writeVarInt(m.ticks());
        }, b -> new Presentation(b.readDouble(), b.readDouble(), b.readDouble(), Math.max(2F, Math.min(20F, b.readFloat())),
                Math.max(20, Math.min(1200, b.readVarInt()))), PRESENTATION);
        versJoueur(Edition.class, Reseau::ecrireEdition, Reseau::lireEdition, EDITION);
        versServeur(Modif.class, (m, b) -> {
            b.writeUtf(m.pnj(), MAX_CLE);
            b.writeVarInt(m.type());
            b.writeVarInt(m.index());
            b.writeVarInt(m.rarete());
            b.writeVarLong(m.prix());
        }, b -> new Modif(b.readUtf(MAX_CLE), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong()), MODIF);
    }

    private static <T> void versJoueur(Class<T> c, BiConsumer<T, FriendlyByteBuf> ecrire, Function<FriendlyByteBuf, T> lire, Crochet<T> crochet) {
        CANAL.messageBuilder(c, prochain++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ecrire)
                .decoder(lire)
                .consumerMainThread((T m, Supplier<NetworkEvent.Context> ctx) -> {
                    crochet.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    private static <T> void versServeur(Class<T> c, BiConsumer<T, FriendlyByteBuf> ecrire, Function<FriendlyByteBuf, T> lire, Crochet<Recu<T>> crochet) {
        CANAL.messageBuilder(c, prochain++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ecrire)
                .decoder(lire)
                .consumerMainThread((T m, Supplier<NetworkEvent.Context> ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) crochet.accept(new Recu<>(p, m));
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    /** Envoie un message à un joueur. */
    public static void envoyer(ServerPlayer p, Object message) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), message);
    }

    // ------------------------------------------------------------------ codecs
    private static void ecrireDialogue(Dialogue m, FriendlyByteBuf b) {
        b.writeVarInt(m.entite());
        m.nom().ecrire(b);
        m.role().ecrire(b);
        m.texte().ecrire(b);
        int n = Math.min(m.options().size(), 12);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Option o = m.options().get(i);
            b.writeVarInt(o.id());
            o.libelle().ecrire(b);
            b.writeVarInt(o.style());
        }
    }

    private static Dialogue lireDialogue(FriendlyByteBuf b) {
        int e = b.readVarInt();
        Texte nom = Texte.lire(b), role = Texte.lire(b), texte = Texte.lire(b);
        int n = Math.min(b.readVarInt(), 12);
        List<Option> o = new ArrayList<>(n);
        for (int i = 0; i < n; i++) o.add(new Option(b.readVarInt(), Texte.lire(b), b.readVarInt()));
        return new Dialogue(e, nom, role, texte, o);
    }

    private static void ecrireEtapes(FriendlyByteBuf b, List<JEtape> l) {
        int n = Math.min(l.size(), MAX_LISTE);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            JEtape e = l.get(i);
            e.libelle().ecrire(b);
            b.writeVarInt(e.fait());
            b.writeVarInt(e.total());
            b.writeVarInt(e.etat());
        }
    }

    private static List<JEtape> lireEtapes(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), MAX_LISTE);
        List<JEtape> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new JEtape(Texte.lire(b), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        return l;
    }

    private static void ecrireJournal(Journal m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        int np = Math.min(m.pnj().size(), MAX_LISTE);
        b.writeVarInt(np);
        for (int i = 0; i < np; i++) {
            JPnj p = m.pnj().get(i);
            b.writeUtf(p.id(), MAX_CLE);
            p.nom().ecrire(b);
            p.role().ecrire(b);
            b.writeVarInt(p.niveau());
            b.writeVarInt(p.rep());
            b.writeVarInt(p.repSuivant());
            int nq = Math.min(p.quetes().size(), MAX_LISTE);
            b.writeVarInt(nq);
            for (int j = 0; j < nq; j++) {
                JQuete q = p.quetes().get(j);
                b.writeUtf(q.cle(), MAX_CLE);
                q.titre().ecrire(b);
                b.writeVarInt(q.difficulte());
                b.writeBoolean(q.contrat());
                b.writeBoolean(q.suivie());
                b.writeBoolean(q.aRendre());
                q.recompense().ecrire(b);
                q.resume().ecrire(b);
                ecrireEtapes(b, q.etapes());
            }
        }
        int nt = Math.min(m.titres().size(), MAX_LISTE);
        b.writeVarInt(nt);
        for (int i = 0; i < nt; i++) m.titres().get(i).ecrire(b);
        b.writeVarInt(m.finies());
    }

    private static Journal lireJournal(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int np = Math.min(b.readVarInt(), MAX_LISTE);
        List<JPnj> pnj = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            String id = b.readUtf(MAX_CLE);
            Texte nom = Texte.lire(b), role = Texte.lire(b);
            int niveau = b.readVarInt(), rep = b.readVarInt(), suivant = b.readVarInt();
            int nq = Math.min(b.readVarInt(), MAX_LISTE);
            List<JQuete> q = new ArrayList<>(nq);
            for (int j = 0; j < nq; j++) {
                String cle = b.readUtf(MAX_CLE);
                Texte titre = Texte.lire(b);
                int diff = b.readVarInt();
                boolean contrat = b.readBoolean(), suivie = b.readBoolean(), aRendre = b.readBoolean();
                Texte rec = Texte.lire(b), resume = Texte.lire(b);
                q.add(new JQuete(cle, titre, diff, contrat, suivie, aRendre, rec, resume, lireEtapes(b)));
            }
            pnj.add(new JPnj(id, nom, role, niveau, rep, suivant, q));
        }
        int nt = Math.min(b.readVarInt(), MAX_LISTE);
        List<Texte> titres = new ArrayList<>(nt);
        for (int i = 0; i < nt; i++) titres.add(Texte.lire(b));
        return new Journal(ouvrir, pnj, titres, b.readVarInt());
    }

    private static void ecrireSuivi(Suivi m, FriendlyByteBuf b) {
        m.pnj().ecrire(b);
        m.titre().ecrire(b);
        int n = Math.min(m.lignes().size(), 8);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            SLigne l = m.lignes().get(i);
            l.libelle().ecrire(b);
            b.writeVarInt(l.fait());
            b.writeVarInt(l.total());
            b.writeVarInt(l.distance() + 1);
            b.writeBoolean(l.approx());
            b.writeVarInt(l.etat());
        }
    }

    private static Suivi lireSuivi(FriendlyByteBuf b) {
        Texte pnj = Texte.lire(b), titre = Texte.lire(b);
        int n = Math.min(b.readVarInt(), 8);
        List<SLigne> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new SLigne(Texte.lire(b), b.readVarInt(), b.readVarInt(), b.readVarInt() - 1, b.readBoolean(), b.readVarInt()));
        return new Suivi(pnj, titre, l);
    }

    /** Cases de l'inventaire principal (barre d'objets comprise). */
    public static final int CASES = 36;
    private static final int MAX_ARTICLES = 256;

    private static void ecrireBoutique(Boutique m, FriendlyByteBuf b) {
        b.writeInt(m.entite());
        b.writeBoolean(m.ouvrir());
        m.nom().ecrire(b);
        m.role().ecrire(b);
        b.writeVarLong(m.solde());
        b.writeBoolean(m.rachat());
        int n = Math.min(m.articles().size(), MAX_ARTICLES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Article a = m.articles().get(i);
            b.writeVarInt(a.index());
            b.writeItem(a.objet());
            b.writeVarLong(a.prix());
            b.writeVarInt(a.rarete() + 1);
        }
        for (int i = 0; i < CASES; i++) {
            b.writeVarInt(m.prixSlots() == null || i >= m.prixSlots().length ? 0 : m.prixSlots()[i]);
            b.writeVarInt(m.rarSlots() == null || i >= m.rarSlots().length ? 0 : m.rarSlots()[i] + 1);
        }
    }

    private static Boutique lireBoutique(FriendlyByteBuf b) {
        int e = b.readInt();
        boolean ouvrir = b.readBoolean();
        Texte nom = Texte.lire(b), role = Texte.lire(b);
        long solde = b.readVarLong();
        boolean rachat = b.readBoolean();
        int n = Math.min(b.readVarInt(), MAX_ARTICLES);
        List<Article> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Article(b.readVarInt(), b.readItem(), b.readVarLong(), b.readVarInt() - 1));
        int[] prix = new int[CASES], rar = new int[CASES];
        for (int i = 0; i < CASES; i++) {
            prix[i] = b.readVarInt();
            rar[i] = b.readVarInt() - 1;
        }
        return new Boutique(e, ouvrir, nom, role, solde, rachat, l, prix, rar);
    }

    private static void ecrirePos(FriendlyByteBuf b, List<BlockPos> l) {
        int n = Math.min(l.size(), 128);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeBlockPos(l.get(i));
    }

    private static List<BlockPos> lirePos(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 128);
        List<BlockPos> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readBlockPos());
        return l;
    }

    private static final int MAX_EDITION = 512;

    private static void ecrireEdition(Edition m, FriendlyByteBuf b) {
        b.writeUtf(m.pnj(), MAX_CLE);
        m.nom().ecrire(b);
        b.writeBoolean(m.rachat());
        b.writeBoolean(m.modifie());
        int n = Math.min(m.articles().size(), MAX_EDITION);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            EArticle a = m.articles().get(i);
            b.writeItem(a.objet());
            b.writeVarLong(a.prix());
            b.writeVarInt(a.rarete() + 1);
        }
        n = Math.min(m.regles().size(), 16);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            ERegle r = m.regles().get(i);
            b.writeUtf(r.tag(), MAX_CLE);
            for (int k = 0; k < 6; k++) b.writeVarLong(r.prix()[k]);
        }
    }

    private static Edition lireEdition(FriendlyByteBuf b) {
        String pnj = b.readUtf(MAX_CLE);
        Texte nom = Texte.lire(b);
        boolean rachat = b.readBoolean(), modifie = b.readBoolean();
        int n = Math.min(b.readVarInt(), MAX_EDITION);
        List<EArticle> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new EArticle(b.readItem(), b.readVarLong(), b.readVarInt() - 1));
        n = Math.min(b.readVarInt(), 16);
        List<ERegle> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String tag = b.readUtf(MAX_CLE);
            long[] prix = new long[6];
            for (int k = 0; k < 6; k++) prix[k] = b.readVarLong();
            r.add(new ERegle(tag, prix));
        }
        return new Edition(pnj, nom, rachat, modifie, l, r);
    }
}
