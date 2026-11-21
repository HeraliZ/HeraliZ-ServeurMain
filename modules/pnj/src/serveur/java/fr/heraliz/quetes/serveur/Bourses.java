package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.objet.Bourse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des bourses de hérals (18/11, demande du propriétaire : « FAIRE TRÈS ATTENTION AU POSSIBLE BUG DE
 * DUPLICATION ») : objet {@link Bourse}, revendu à Mila ({@link Marchands}).
 *
 * <p><b>La règle qui empêche toute copie d'argent</b> : chaque bourse a un numéro ; le registre garde, pour chaque numéro
 * encore valable, un plafond. Il est écrit sur le disque AVANT que la bourse n'apparaisse ou ne change dans le monde
 * (inventaire, machine, sol), jamais après. À la revente, Mila paie le plus petit du montant affiché et du plafond, puis
 * efface le numéro (écrit avant le paiement). Donc :
 * <ul>
 * <li>une bourse copiée par n'importe quel bug (machine, coffre, plantage, sac...) a le même numéro que l'originale : la
 * première revendue paie, l'autre est refusée ;</li>
 * <li>un montant gonflé (données modifiées) ne paie jamais plus que le plafond ;</li>
 * <li>un plantage entre l'écriture du registre et celle du monde fait au pire perdre un gain, il n'en copie aucun : le
 * plafond est remis au montant réel au gain suivant ({@link #ajouter}) ;</li>
 * <li>deux bourses de même numéro ne se fusionnent jamais (ce serait additionner une copie).</li>
 * </ul>
 *
 * <p>Fichiers ({@code heraliz_quetes/bourses/}) : {@code bourses.txt} (tout le registre, « numéro plafond » par ligne) et
 * {@code bourses.journal} (les changements depuis, une ligne chacun : « numéro plafond » ou « numéro - » pour une bourse
 * revendue ou vidée), relu par-dessus au démarrage ; le registre complet est réécrit d'un coup (fichier temporaire puis
 * remplacement) toutes les {@value #COMPACTER_APRES} lignes et à l'arrêt. Une ligne coûte quelques dizaines d'octets, sur
 * le fil serveur. Un fichier illisible n'est jamais écrasé : les bourses sont alors bloquées (gains versés sur le compte
 * comme avant, reventes refusées) et le journal du serveur dit pourquoi ; une écriture ratée bloque de même jusqu'au
 * redémarrage.
 */
public final class Bourses {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int COMPACTER_APRES = 2000;
    /** Plafond d'une bourse (celui d'un compte de l'économie). */
    private static final long MAX = 10_000_000L;

    /** Numéro → plafond des bourses encore valables. */
    private static final Map<UUID, Long> PLAFONDS = new HashMap<>();
    @Nullable
    private static Path dossier;
    private static boolean charge, bloque;
    @Nullable
    private static BufferedWriter journal;
    private static int lignes;
    /** Le journal relu finissait sur une ligne coupée (arrêt brutal) : la prochaine ligne commence par un retour. */
    private static boolean ligneCoupee;

    private Bourses() {
    }

    // ------------------------------------------------------------------ fichiers
    static void demarrer(MinecraftServer srv) {
        fermer();
        PLAFONDS.clear();
        charge = false;
        bloque = false;
        lignes = 0;
        ligneCoupee = false;
        dossier = srv.getServerDirectory().toPath().resolve("heraliz_quetes").resolve("bourses");
        lire();
        Bourse.FUSION = Bourses::fusionner;
    }

    /** Arrêt du serveur (plus rien ne bouge) : registre complet réécrit, journal fermé. */
    static void arreter() {
        if (charge && !bloque && lignes > 0) compacter();
        fermer();
        charge = false;
        dossier = null;
        PLAFONDS.clear();
    }

    private static Path complet() {
        return dossier.resolve("bourses.txt");
    }

    private static Path journalFichier() {
        return dossier.resolve("bourses.journal");
    }

    /** Le registre est chargé et utilisable (chargé à la demande si le serveur tourne déjà). */
    static boolean pret() {
        if (!charge && dossier == null) {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) demarrer(srv);
        }
        return charge && !bloque;
    }

    private static void lire() {
        try {
            Files.createDirectories(dossier);
            if (Files.exists(complet())) {
                for (String l : Files.readAllLines(complet(), StandardCharsets.UTF_8)) {
                    if (!appliquer(l, false)) throw new IOException("ligne illisible dans bourses.txt : « " + l + " »");
                }
            }
            if (Files.exists(journalFichier())) {
                byte[] brut = Files.readAllBytes(journalFichier());
                ligneCoupee = brut.length > 0 && brut[brut.length - 1] != '\n';
                List<String> ls = new String(brut, StandardCharsets.UTF_8).lines().toList();
                for (int i = 0; i < ls.size(); i++) {
                    // la dernière ligne peut être coupée (arrêt brutal pendant son écriture) : son changement n'a eu aucun effet
                    if (!appliquer(ls.get(i), true) && i < ls.size() - 1) {
                        throw new IOException("ligne illisible dans bourses.journal (n° " + (i + 1) + ") : « " + ls.get(i) + " »");
                    }
                }
                lignes = ls.size();
            }
            charge = true;
            LOG.info("[heraliz_quetes] bourses de hérals : {} bourse(s) valable(s) au registre", PLAFONDS.size());
            // registre complet réécrit dès le démarrage : journal court, et plus de ligne coupée à la fin
            if (lignes > 0) compacter();
        } catch (IOException | RuntimeException ex) {
            // jamais écrasé : tout reste bloqué jusqu'à ce que le staff regarde le fichier
            bloque = true;
            charge = true;
            PLAFONDS.clear();
            LOG.error("[heraliz_quetes] bourses de hérals : registre {} illisible ({}) ; bourses BLOQUÉES (gains versés sur le "
                    + "compte, reventes refusées) jusqu'à ce que le fichier soit réparé", dossier, ex.toString());
        }
    }

    /** « numéro plafond » ou « numéro - » ; lignes vides et « # ... » ignorées. Faux si la ligne est illisible. */
    private static boolean appliquer(String ligne, boolean journalLigne) {
        String l = ligne.trim();
        if (l.isEmpty() || l.startsWith("#")) return true;
        int sp = l.indexOf(' ');
        if (sp <= 0) return false;
        UUID id;
        try {
            id = UUID.fromString(l.substring(0, sp));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String v = l.substring(sp + 1).trim();
        if (v.equals("-")) {
            if (!journalLigne) return false;
            PLAFONDS.remove(id);
            return true;
        }
        try {
            long n = Long.parseLong(v);
            if (n < 0) return false;
            PLAFONDS.put(id, n);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Une ligne au journal, sur le disque avant de rendre la main (vidée vers le système). Faux : bourses bloquées. */
    private static boolean ecrire(String ligne) {
        if (!charge || bloque || dossier == null) return false;
        try {
            if (journal == null) {
                journal = Files.newBufferedWriter(journalFichier(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                if (ligneCoupee) journal.write('\n');
                ligneCoupee = false;
            }
            journal.write(ligne);
            journal.write('\n');
            journal.flush();
        } catch (IOException ex) {
            bloque = true;
            LOG.error("[heraliz_quetes] bourses de hérals : écriture du journal impossible ({}) ; bourses BLOQUÉES jusqu'au redémarrage",
                    ex.toString());
            return false;
        }
        if (++lignes >= COMPACTER_APRES) compacter();
        return true;
    }

    /**
     * Registre complet réécrit (fichier temporaire puis remplacement), puis journal vidé. Un arrêt entre les deux ne gêne
     * pas : le journal relu par-dessus redonne le même registre (chaque ligne donne une valeur, elle n'ajoute rien).
     */
    private static void compacter() {
        if (dossier == null) return;
        try {
            StringBuilder b = new StringBuilder(PLAFONDS.size() * 48 + 64);
            b.append("# HeraliZ : bourses de hérals valables (numéro plafond). Ne pas modifier serveur allumé.\n");
            for (Map.Entry<UUID, Long> e : PLAFONDS.entrySet()) b.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
            Path tmp = complet().resolveSibling("bourses.txt.tmp");
            Files.writeString(tmp, b, StandardCharsets.UTF_8);
            Files.move(tmp, complet(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            fermer();
            journal = Files.newBufferedWriter(journalFichier(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            ligneCoupee = false;
            lignes = 0;
        } catch (IOException ex) {
            // le journal reste juste : on réessaiera plus tard
            lignes = COMPACTER_APRES / 2;
            LOG.warn("[heraliz_quetes] bourses de hérals : réécriture du registre impossible ({}), le journal continue", ex.toString());
        }
    }

    private static void fermer() {
        if (journal == null) return;
        try {
            journal.close();
        } catch (IOException ignore) {
            // rien à faire : chaque ligne a déjà été vidée vers le système
        }
        journal = null;
    }

    private static boolean poser(UUID id, long plafond) {
        if (!ecrire(id + " " + plafond)) return false;
        PLAFONDS.put(id, plafond);
        return true;
    }

    private static boolean effacer(UUID id) {
        if (!ecrire(id + " -")) return false;
        PLAFONDS.remove(id);
        return true;
    }

    // ------------------------------------------------------------------ bourses
    /** Ce que vaut cette bourse à la revente : -1 si ce n'est pas une bourse, 0 si elle ne vaut rien (inconnue, revendue). */
    static long valeur(ItemStack s) {
        if (!(s.getItem() instanceof Bourse)) return -1;
        if (!pret()) return 0;
        UUID id = Bourse.id(s);
        Long plafond = id == null ? null : PLAFONDS.get(id);
        return plafond == null ? 0 : Math.min(Bourse.herals(s), plafond);
    }

    /** Nouvelle bourse de {@code n} hérals, au registre AVANT d'exister ; null si les bourses sont bloquées. */
    @Nullable
    static ItemStack creer(long n) {
        if (n <= 0 || !pret()) return null;
        UUID id = UUID.randomUUID();
        while (PLAFONDS.containsKey(id)) id = UUID.randomUUID();
        long v = Math.min(MAX, n);
        if (!poser(id, v)) return null;
        return Bourse.marquer(new ItemStack(HeralizQuetes.BOURSE.get()), id, v);
    }

    /**
     * Ajoute {@code n} hérals à une bourse valable : plafond écrit d'abord (remis au montant réel + n : un écart laissé par
     * un plantage disparaît), puis le montant affiché. Faux (rien ne change) si la bourse est inconnue ou bloquée.
     */
    static boolean ajouter(ItemStack s, long n) {
        if (n <= 0 || !(s.getItem() instanceof Bourse) || !pret()) return false;
        UUID id = Bourse.id(s);
        Long plafond = id == null ? null : PLAFONDS.get(id);
        if (plafond == null) return false;
        // 21/11 (validation de l'audit) : une bourse pleine (10 000 000 hérals) ne reçoit plus rien (avant : le surplus était
        // coupé sans rien dire) ; l'appelant en fait une neuve
        long base = Math.min(Bourse.herals(s), plafond);
        if (base + n > MAX) return false;
        long v = base + n;
        if (!poser(id, v)) return false;
        Bourse.montant(s, v);
        return true;
    }

    /**
     * Gain de {@code n} hérals pour ce joueur sur la carte (API des autres sources : zombies, événements...) : ajouté à la
     * première bourse valable de son inventaire, sinon une bourse neuve (inventaire, sinon à ses pieds) ; joueur écrit
     * tout de suite et prévenu. Faux si les bourses sont bloquées : l'appelant verse alors sur le compte, comme avant.
     */
    public static boolean gagner(ServerPlayer p, long n, String motif) {
        if (n <= 0) return true;
        if (!pret() || !p.isAlive()) return false;
        Inventory inv = p.getInventory();
        long total = -1;
        for (int i = 0; i < inv.getContainerSize() && total < 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof Bourse && ajouter(s, n)) total = Bourse.herals(s);
        }
        if (total < 0) {
            ItemStack b = creer(n);
            if (b == null) return false;
            total = Bourse.herals(b);
            ItemHandlerHelper.giveItemToPlayer(p, b);
        }
        Ponts.sauverJoueur(p);
        prevenir(p, n, total, false);
        LOG.debug("[heraliz_quetes] bourse de {} : +{} hérals ({})", p.getGameProfile().getName(), n, motif);
        return true;
    }

    /**
     * Gain de {@code n} hérals rangé dans une machine (sorties du recycleur de terrain) : ajouté à la bourse valable qui y
     * est, sinon bourse neuve dans une case vide. À appeler AVANT l'écriture de la machine. Renvoie le montant de la bourse,
     * ou -1 (bourses bloquées) / -2 (aucune case libre) : rien n'a changé, l'appelant verse alors sur le compte, comme avant.
     */
    static long dansMachine(Container sortie, long n) {
        if (n <= 0) return 0;
        if (!pret()) return -1;
        for (int i = 0; i < sortie.getContainerSize(); i++) {
            ItemStack s = sortie.getItem(i);
            if (s.getItem() instanceof Bourse && ajouter(s, n)) {
                sortie.setChanged();
                return Bourse.herals(s);
            }
        }
        int libre = -1;
        for (int i = 0; i < sortie.getContainerSize() && libre < 0; i++) if (sortie.getItem(i).isEmpty()) libre = i;
        if (libre < 0) return -2;
        ItemStack b = creer(n);
        if (b == null) return -1;
        sortie.setItem(libre, b);
        return Bourse.herals(b);
    }

    /** Message au joueur : « +60 hérals · dans ta bourse (63) » ou « ... dans la bourse du recycleur (63) ». */
    static void prevenir(ServerPlayer p, long n, long total, boolean machine) {
        Moteur.notif(p, Texte.cle(machine ? "heraliz_quetes.bourse.gain_machine" : "heraliz_quetes.bourse.gain",
                Texte.brut(Ponts.nombre(n)), Texte.brut(Ponts.nombre(total))), 0xFFD678);
        if (!machine) p.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 1.25F);
    }

    /**
     * Revente à Mila : numéro effacé du registre (écrit AVANT que l'appelant paie et vide la case). Renvoie ce qu'elle paie,
     * 0 si elle ne vaut rien (inconnue, déjà revendue, copie) : l'appelant la laisse au joueur.
     */
    static long encaisser(ServerPlayer p, ItemStack s) {
        if (!(s.getItem() instanceof Bourse) || !pret()) return 0;
        UUID id = Bourse.id(s);
        Long plafond = id == null ? null : PLAFONDS.get(id);
        if (plafond == null) {
            LOG.warn("[heraliz_quetes] bourse refusée à {} : numéro {} inconnu du registre (déjà revendue, copie, ou créée hors jeu), "
                    + "{} hérals affichés", p.getGameProfile().getName(), id, Bourse.herals(s));
            return 0;
        }
        long v = Math.min(Bourse.herals(s), plafond);
        if (v <= 0 || !effacer(id)) return 0;
        LOG.info("[heraliz_quetes] bourse {} revendue par {} : {} hérals", id, p.getGameProfile().getName(), v);
        return v;
    }

    /**
     * Une bourse versée dans une autre ({@link Bourse#overrideOtherStackedOnMe}) : plafond de celle qui reste monté
     * d'abord, PUIS l'autre effacée (un arrêt entre les deux ne copie rien : la somme payable ne dépasse jamais ce qu'il y
     * avait), puis le montant affiché ; joueur écrit tout de suite.
     */
    static boolean fusionner(Player joueur, ItemStack dans, ItemStack versee) {
        if (!(joueur instanceof ServerPlayer p) || !pret()) return false;
        UUID a = Bourse.id(dans), b = Bourse.id(versee);
        if (a == null || b == null) return false;
        if (a.equals(b)) {
            LOG.warn("[heraliz_quetes] bourses : {} a voulu fusionner deux bourses de même numéro {} (copie), refusé",
                    p.getGameProfile().getName(), a);
            return false;
        }
        Long pa = PLAFONDS.get(a), pb = PLAFONDS.get(b);
        if (pa == null || pb == null) return false;
        long va = Math.min(Bourse.herals(dans), pa), vb = Math.min(Bourse.herals(versee), pb);
        // 21/11 (validation de l'audit) : au-delà du plafond d'une bourse, pas de fusion (avant : le surplus disparaissait)
        if (va + vb > MAX) return false;
        long total = va + vb;
        if (!poser(a, total)) return false;
        if (!effacer(b)) return false;   // bloqué : rien ne change dans le monde, les deux bourses gardent leur valeur
        Bourse.montant(dans, total);
        Ponts.sauverJoueur(p);
        p.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5F, 1.1F);
        return true;
    }

    // ------------------------------------------------------------------ staff
    /** Bourse donnée par le staff ({@code /bourse donner}) : null si bloqué. */
    @Nullable
    static ItemStack donner(ServerPlayer cible, long n, String par) {
        ItemStack b = creer(n);
        if (b == null) return null;
        LOG.info("[heraliz_quetes] bourse de {} hérals donnée à {} par {}", Bourse.herals(b), cible.getGameProfile().getName(), par);
        ItemHandlerHelper.giveItemToPlayer(cible, b.copy());
        Ponts.sauverJoueur(cible);
        return b;
    }

    /** Pour {@code /bourse info} : bourses valables, total des plafonds, bloqué ou non. */
    static long[] info() {
        pret();
        long somme = 0;
        for (long v : PLAFONDS.values()) somme += v;
        return new long[]{PLAFONDS.size(), somme, bloque ? 1 : 0};
    }

    /** {@code /wipe tout} : toutes les bourses perdent leur valeur (les inventaires partent aussi). */
    static int toutVider() {
        if (!pret()) return 0;
        int n = PLAFONDS.size();
        PLAFONDS.clear();
        lignes = COMPACTER_APRES;   // registre vide réécrit tout de suite, journal vidé
        compacter();
        LOG.warn("[heraliz_quetes] remise à zéro : {} bourse(s) de hérals effacée(s) du registre", n);
        return n;
    }
}
