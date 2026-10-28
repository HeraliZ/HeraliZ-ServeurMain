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

    /** 7 (Quêtes 1.11.0) : échanges (troc) et marchands créés en jeu. 9 (1.13.0) : aperçu du recycleur. 10 (1.14.0) : quêtes du jour. */
    private static final String VERSION = "12";   // 12 (28/10) : angle de la caméra des points de la visite du Guide ; 11 (26/10) : état de l'objet dans l'aperçu du recycleur
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
     * articles. ouvrir = ouvrir l'écran, sinon mettre à jour celui qui est ouvert. entite -1 = fermer. pointsSlots (25/10) :
     * points de saison que rapporte l'objet de chaque case (objets d'événement ; -1 = aucun) ; raisonPoints : 0 le clan
     * du joueur les gagne, 1 pas de saison, 2 sans clan, 3 clan sans cœur (le joueur peut garder l'objet pour plus tard).
     */
    public record Boutique(int entite, boolean ouvrir, Texte nom, Texte role, long solde, boolean rachat, List<Article> articles,
                           int[] prixSlots, int[] rarSlots, boolean troc, List<Echange> echanges, int[] pointsSlots, int raisonPoints) {
    }

    /**
     * Un échange proposé par un marchand (troc) : nDonne × donne contre nRecu × recu. rarete : celle de l'objet reçu ;
     * possede : combien d'objets qui conviennent le joueur a dans son inventaire ; actif faux = échange coupé par le
     * serveur (objet reçu par un autre échange, voir Echanges).
     */
    public record Echange(int index, ItemStack donne, int nDonne, ItemStack recu, int nRecu, int rarete, int possede, boolean actif) {
    }

    /** Achat d'un article (joueur -> serveur). */
    public record Achat(int entite, int index, int quantite) {
    }

    /** Faire un échange (joueur -> serveur), fois = 1 à 5 de suite. */
    public record Troc(int entite, int index, int fois) {
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
    /**
     * Tour de caméra (présentation d'un PNJ ou d'un point de la visite du Guide). {@code angle} : direction (radians,
     * atan2(dz, dx) du centre vers la caméra) ; NaN = devant le PNJ le plus proche du centre (comme avant).
     */
    public record Presentation(double x, double y, double z, float rayon, int ticks, float angle) {
    }

    /** Éditeur des marchands (staff op) : un article vendu, ou une règle de rachat et ses 6 prix (Commun ... Mythique). */
    public record EArticle(ItemStack objet, long prix, int rarete) {
    }

    public record ERegle(String tag, long[] prix) {
    }

    /** Éditeur : un échange (objet donné et sa quantité, objet reçu et sa quantité). */
    public record EEchange(ItemStack donne, int nDonne, ItemStack recu, int nRecu) {
    }

    /**
     * Ouvre (ou met à jour) l'éditeur d'un marchand (serveur -> joueur). cree = marchand créé en jeu (/marchand nouveau :
     * supprimable, pas de fichier où revenir) ; troc = il ne fait que des échanges.
     */
    public record Edition(String pnj, Texte nom, boolean rachat, boolean modifie, List<EArticle> articles, List<ERegle> regles,
                          boolean cree, boolean troc, List<EEchange> echanges) {
    }

    /**
     * Un changement demandé dans l'éditeur (joueur -> serveur) ; le serveur revérifie les droits. AJOUTER_REGLE : rarete =
     * case de la barre d'objets (0 à 8), prix = prix de toutes les raretés ; ECH_N_DONNE / ECH_N_RECU : prix = quantité.
     */
    public record Modif(String pnj, int type, int index, int rarete, long prix) {
        public static final int PRIX = 0, RETIRER = 1, AJOUTER_MAIN = 2, PRIX_RACHAT = 3, REINITIALISER = 4, MONTER = 5,
                AJOUTER_REGLE = 6, RETIRER_REGLE = 7, ECH_RETIRER = 8, ECH_MONTER = 9, ECH_N_DONNE = 10, ECH_N_RECU = 11, SUPPRIMER = 12;
    }

    /** Ajouter un échange depuis l'éditeur (joueur -> serveur) : objets de deux cases de la barre d'objets (0 à 8). */
    public record AjoutEchange(String pnj, int caseDonne, int nDonne, int caseRecu, int nRecu) {
    }

    /** Message reçu par le serveur, avec le joueur qui l'envoie. */
    public record Recu<T>(ServerPlayer joueur, T message) {
    }

    /**
     * Aperçu d'une case du menu du recycleur (25/10) : état (0 se recycle, 1 ne se recycle pas, 2 refusé car acheté chez
     * un marchand, 3 protégé, 4 il en manque pour un paquet), rang de rareté, taille du paquet, hérals si rien ne sort, et
     * les composants que la règle peut rendre (chaque entrée tirée à part).
     */
    public record ApercuCase(int index, int etat, int rang, int par, int herals, List<String> composants, List<Integer> nombres,
                             int facteur, int usure) {
    }

    /** Aperçu de toutes les cases non vides du menu du recycleur ouvert (conteneur), avec la chance et le plafond de pièces. */
    public record ApercuRecyclage(int conteneur, float chance, int maxPieces, List<ApercuCase> cases) {
    }

    // ------------------------------------------------------------------ quêtes du jour (26/10)
    /** Une récompense possible : icône (l'objet), nom (vide : celui de l'objet), nombre, rareté (0 à 5, -1), chance en %. */
    public record JRec(String id, ItemStack icone, Texte nom, int nombre, int rarete, float chance) {
    }

    /** Une difficulté : durée indicative (min), exemples de quêtes, récompenses (dans l'ordre de la table). */
    public record JDiff(String id, int duree, List<Texte> exemples, List<JRec> recompenses) {
    }

    /** Une quête proposée ou acceptée ; carte : il faut une carte d'accès ; livrer : objets à rapporter depuis le menu. */
    public record JProp(String id, String famille, Texte titre, Texte aide, int fait, int total, boolean carte, boolean livrer) {
    }

    /**
     * État des quêtes du jour (serveur -> joueur). etat : 0 choisir la difficulté, 1 choisir la quête, 2 en cours, 3 réussie,
     * 4 récompense tirée, 5 abandonnée. gains : tirages à faire (gainDiff : difficulté du premier, peut-être d'un autre jour) ;
     * remise : un tirage attend d'être remis (inventaire plein) ;
     * resultat : « difficulté:récompense » tirée aujourd'hui ; livrables : objets qui comptent dans l'inventaire.
     */
    public record Jour(boolean ouvrir, int etat, String difficulte, long resteMs, List<JDiff> difficultes, List<JProp> propositions,
                       int gains, String gainDiff, boolean remise, String resultat, int livrables) {
    }

    /** Tirage (serveur -> joueur) : la bande de récompenses (index dans la table de la difficulté) et la place du gagnant. */
    public record JourTirage(String difficulte, List<Integer> bande, int gagnant) {
    }

    /** Demande (joueur -> serveur) : 0 ouvrir, 1 difficulté, 2 accepter, 3 abandonner, 4 tirer / récupérer, 5 livrer, 6 fermé. */
    public record JourAction(int type, String cle) {
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
    public static final Crochet<ApercuRecyclage> APERCU_RECYCLAGE = new Crochet<>();
    public static final Crochet<Jour> JOUR = new Crochet<>();
    public static final Crochet<JourTirage> JOUR_TIRAGE = new Crochet<>();

    public static final Crochet<Recu<Choix>> CHOIX = new Crochet<>();
    public static final Crochet<Recu<DemandeJournal>> DEMANDE_JOURNAL = new Crochet<>();
    public static final Crochet<Recu<Action>> ACTION = new Crochet<>();
    public static final Crochet<Recu<Code>> CODE = new Crochet<>();
    public static final Crochet<Recu<Achat>> ACHAT = new Crochet<>();
    public static final Crochet<Recu<Vente>> VENTE = new Crochet<>();
    public static final Crochet<Recu<Modif>> MODIF = new Crochet<>();
    public static final Crochet<Recu<Troc>> TROC = new Crochet<>();
    public static final Crochet<Recu<JourAction>> JOUR_ACTION = new Crochet<>();
    public static final Crochet<Recu<AjoutEchange>> AJOUT_ECHANGE = new Crochet<>();

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
            b.writeFloat(m.angle());
        }, b -> new Presentation(b.readDouble(), b.readDouble(), b.readDouble(), Math.max(2F, Math.min(20F, b.readFloat())),
                Math.max(20, Math.min(1200, b.readVarInt())), b.readFloat()), PRESENTATION);
        versJoueur(Edition.class, Reseau::ecrireEdition, Reseau::lireEdition, EDITION);
        versServeur(Modif.class, (m, b) -> {
            b.writeUtf(m.pnj(), MAX_CLE);
            b.writeVarInt(m.type());
            b.writeVarInt(m.index());
            b.writeVarInt(m.rarete());
            b.writeVarLong(m.prix());
        }, b -> new Modif(b.readUtf(MAX_CLE), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong()), MODIF);
        versServeur(Troc.class, (m, b) -> {
            b.writeVarInt(m.entite());
            b.writeVarInt(m.index());
            b.writeVarInt(m.fois());
        }, b -> new Troc(b.readVarInt(), b.readVarInt(), b.readVarInt()), TROC);
        versServeur(AjoutEchange.class, (m, b) -> {
            b.writeUtf(m.pnj(), MAX_CLE);
            b.writeVarInt(m.caseDonne());
            b.writeVarInt(m.nDonne());
            b.writeVarInt(m.caseRecu());
            b.writeVarInt(m.nRecu());
        }, b -> new AjoutEchange(b.readUtf(MAX_CLE), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()), AJOUT_ECHANGE);
        versJoueur(ApercuRecyclage.class, (m, b) -> {
            b.writeVarInt(m.conteneur());
            b.writeFloat(m.chance());
            b.writeVarInt(m.maxPieces());
            int n = Math.min(m.cases().size(), MAX_LISTE);
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                ApercuCase c = m.cases().get(i);
                b.writeVarInt(c.index());
                b.writeVarInt(c.etat());
                b.writeVarInt(c.rang());
                b.writeVarInt(c.par());
                b.writeVarInt(c.herals());
                int k = Math.min(c.composants().size(), MAX_LISTE);
                b.writeVarInt(k);
                for (int j = 0; j < k; j++) {
                    b.writeUtf(c.composants().get(j), MAX_CLE);
                    b.writeVarInt(c.nombres().get(j));
                }
                b.writeVarInt(c.facteur());
                b.writeVarInt(c.usure());
            }
        }, b -> {
            int conteneur = b.readVarInt();
            float chance = b.readFloat();
            int max = b.readVarInt();
            int n = Math.min(b.readVarInt(), MAX_LISTE);
            List<ApercuCase> cases = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int index = b.readVarInt(), etat = b.readVarInt(), rang = b.readVarInt(), par = b.readVarInt(), herals = b.readVarInt();
                int k = Math.min(b.readVarInt(), MAX_LISTE);
                List<String> composants = new java.util.ArrayList<>(k);
                List<Integer> nombres = new java.util.ArrayList<>(k);
                for (int j = 0; j < k; j++) {
                    composants.add(b.readUtf(MAX_CLE));
                    nombres.add(b.readVarInt());
                }
                cases.add(new ApercuCase(index, etat, rang, par, herals, composants, nombres, b.readVarInt(), b.readVarInt()));
            }
            return new ApercuRecyclage(conteneur, chance, max, cases);
        }, APERCU_RECYCLAGE);
        // quêtes du jour (26/10) : ajoutées à la fin (numéros des messages d'avant inchangés), version 10
        versJoueur(Jour.class, Reseau::ecrireJour, Reseau::lireJour, JOUR);
        versJoueur(JourTirage.class, (m, b) -> {
            b.writeUtf(m.difficulte(), MAX_CLE);
            int n = Math.min(m.bande().size(), MAX_LISTE + 16);
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) b.writeVarInt(m.bande().get(i));
            b.writeVarInt(m.gagnant());
        }, b -> {
            String d = b.readUtf(MAX_CLE);
            int n = Math.min(b.readVarInt(), MAX_LISTE + 16);
            List<Integer> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(b.readVarInt());
            return new JourTirage(d, l, b.readVarInt());
        }, JOUR_TIRAGE);
        versServeur(JourAction.class, (m, b) -> {
            b.writeVarInt(m.type());
            b.writeUtf(m.cle(), MAX_CLE);
        }, b -> new JourAction(b.readVarInt(), b.readUtf(MAX_CLE)), JOUR_ACTION);
    }

    private static void ecrireJour(Jour m, FriendlyByteBuf b) {
        b.writeBoolean(m.ouvrir());
        b.writeVarInt(m.etat());
        b.writeUtf(m.difficulte(), MAX_CLE);
        b.writeVarLong(m.resteMs());
        int nd = Math.min(m.difficultes().size(), 4);
        b.writeVarInt(nd);
        for (int i = 0; i < nd; i++) {
            JDiff d = m.difficultes().get(i);
            b.writeUtf(d.id(), MAX_CLE);
            b.writeVarInt(d.duree());
            int ne = Math.min(d.exemples().size(), 8);
            b.writeVarInt(ne);
            for (int k = 0; k < ne; k++) d.exemples().get(k).ecrire(b);
            int nr = Math.min(d.recompenses().size(), MAX_LISTE);
            b.writeVarInt(nr);
            for (int k = 0; k < nr; k++) {
                JRec r = d.recompenses().get(k);
                b.writeUtf(r.id(), MAX_CLE);
                b.writeItem(r.icone());
                r.nom().ecrire(b);
                b.writeVarInt(r.nombre());
                b.writeVarInt(r.rarete() + 1);
                b.writeFloat(r.chance());
            }
        }
        int np = Math.min(m.propositions().size(), 4);
        b.writeVarInt(np);
        for (int i = 0; i < np; i++) {
            JProp q = m.propositions().get(i);
            b.writeUtf(q.id(), MAX_CLE);
            b.writeUtf(q.famille(), MAX_CLE);
            q.titre().ecrire(b);
            q.aide().ecrire(b);
            b.writeVarInt(q.fait());
            b.writeVarInt(q.total());
            b.writeBoolean(q.carte());
            b.writeBoolean(q.livrer());
        }
        b.writeVarInt(m.gains());
        b.writeUtf(m.gainDiff(), MAX_CLE);
        b.writeBoolean(m.remise());
        b.writeUtf(m.resultat(), MAX_CLE);
        b.writeVarInt(m.livrables());
    }

    private static Jour lireJour(FriendlyByteBuf b) {
        boolean ouvrir = b.readBoolean();
        int etat = b.readVarInt();
        String diff = b.readUtf(MAX_CLE);
        long reste = b.readVarLong();
        int nd = Math.min(b.readVarInt(), 4);
        List<JDiff> diffs = new ArrayList<>(nd);
        for (int i = 0; i < nd; i++) {
            String id = b.readUtf(MAX_CLE);
            int duree = b.readVarInt();
            int ne = Math.min(b.readVarInt(), 8);
            List<Texte> ex = new ArrayList<>(ne);
            for (int k = 0; k < ne; k++) ex.add(Texte.lire(b));
            int nr = Math.min(b.readVarInt(), MAX_LISTE);
            List<JRec> rs = new ArrayList<>(nr);
            for (int k = 0; k < nr; k++) {
                rs.add(new JRec(b.readUtf(MAX_CLE), b.readItem(), Texte.lire(b), b.readVarInt(), b.readVarInt() - 1, b.readFloat()));
            }
            diffs.add(new JDiff(id, duree, ex, rs));
        }
        int np = Math.min(b.readVarInt(), 4);
        List<JProp> props = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            props.add(new JProp(b.readUtf(MAX_CLE), b.readUtf(MAX_CLE), Texte.lire(b), Texte.lire(b), b.readVarInt(), b.readVarInt(),
                    b.readBoolean(), b.readBoolean()));
        }
        return new Jour(ouvrir, etat, diff, reste, diffs, props, b.readVarInt(), b.readUtf(MAX_CLE), b.readBoolean(), b.readUtf(MAX_CLE),
                b.readVarInt());
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
            b.writeVarInt(m.pointsSlots() == null || i >= m.pointsSlots().length ? 0 : m.pointsSlots()[i] + 1);
        }
        b.writeVarInt(m.raisonPoints());
        b.writeBoolean(m.troc());
        List<Echange> ech = m.echanges() == null ? List.of() : m.echanges();
        n = Math.min(ech.size(), MAX_ECHANGES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Echange e = ech.get(i);
            b.writeVarInt(e.index());
            b.writeItem(e.donne());
            b.writeVarInt(e.nDonne());
            b.writeItem(e.recu());
            b.writeVarInt(e.nRecu());
            b.writeVarInt(e.rarete() + 1);
            b.writeVarInt(e.possede());
            b.writeBoolean(e.actif());
        }
    }

    /** Échanges d'un marchand, au plus. */
    public static final int MAX_ECHANGES = 64;

    private static Boutique lireBoutique(FriendlyByteBuf b) {
        int e = b.readInt();
        boolean ouvrir = b.readBoolean();
        Texte nom = Texte.lire(b), role = Texte.lire(b);
        long solde = b.readVarLong();
        boolean rachat = b.readBoolean();
        int n = Math.min(b.readVarInt(), MAX_ARTICLES);
        List<Article> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Article(b.readVarInt(), b.readItem(), b.readVarLong(), b.readVarInt() - 1));
        int[] prix = new int[CASES], rar = new int[CASES], pts = new int[CASES];
        for (int i = 0; i < CASES; i++) {
            prix[i] = b.readVarInt();
            rar[i] = b.readVarInt() - 1;
            pts[i] = b.readVarInt() - 1;
        }
        int raison = b.readVarInt();
        boolean troc = b.readBoolean();
        int ne = Math.min(b.readVarInt(), MAX_ECHANGES);
        List<Echange> ech = new ArrayList<>(ne);
        for (int i = 0; i < ne; i++) {
            ech.add(new Echange(b.readVarInt(), b.readItem(), b.readVarInt(), b.readItem(), b.readVarInt(), b.readVarInt() - 1, b.readVarInt(),
                    b.readBoolean()));
        }
        return new Boutique(e, ouvrir, nom, role, solde, rachat, l, prix, rar, troc, ech, pts, raison);
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
        b.writeBoolean(m.cree());
        b.writeBoolean(m.troc());
        List<EEchange> ech = m.echanges() == null ? List.of() : m.echanges();
        n = Math.min(ech.size(), MAX_ECHANGES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            EEchange x = ech.get(i);
            b.writeItem(x.donne());
            b.writeVarInt(x.nDonne());
            b.writeItem(x.recu());
            b.writeVarInt(x.nRecu());
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
        boolean cree = b.readBoolean(), troc = b.readBoolean();
        n = Math.min(b.readVarInt(), MAX_ECHANGES);
        List<EEchange> ech = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ech.add(new EEchange(b.readItem(), b.readVarInt(), b.readItem(), b.readVarInt()));
        return new Edition(pnj, nom, rachat, modifie, l, r, cree, troc, ech);
    }
}
