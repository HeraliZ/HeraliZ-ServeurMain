package fr.heraliz.casino.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.data.EcrituresFond;
import fr.heraliz.casino.item.ChipItem;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ComptePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compte de capsules des joueurs (27/10, demande du proprietaire : « ATM a capsules », pour ne plus garder des piles de
 * capsules dans les bases). Un nombre par joueur, garde par le serveur hors du monde (il survit aux changements de
 * carte), un petit fichier par joueur : {@code heralizcasino/comptes/<uuid>.json} a la racine du serveur.
 * <pre>
 * {"version": 1, "nom": "Pseudo", "solde": 12500}
 * </pre>
 * Le serveur decide de tout, sur le fil serveur. Chaque changement est ecrit tout de suite, AVANT de rendre vrai (fichier
 * a cote puis mis a sa place : jamais un fichier a moitie ecrit) ; ecriture ratee : le changement est annule et
 * l'operation refusee (une operation d'argent attend toujours son fichier, ARCHITECTURE.md, perf du 27/10). Le journal
 * ({@code heralizcasino/comptes_journal.log}) part sur le fil d'ecriture du casino ({@link EcrituresFond}). Un fichier
 * illisible n'est jamais ecrase : copie a cote ({@code .illisible.<date>}) et compte FERME jusqu'a ce que le staff le
 * repare. Jamais de solde negatif ni au-dessus du plafond ; les capsules sont deplacees, jamais copiees : depot = objets
 * retires de l'inventaire, retrait refuse sans la place pour toutes les capsules, joueur sauvegarde apres chaque mouvement.
 */
public final class CompteCapsules {
    private CompteCapsules() {
    }

    /** Une operation, au plus (aucun debordement possible). */
    private static final long MAX_OPERATION = 1_000_000_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final class Compte {
        final UUID uuid;
        String nom = "";
        long solde;
        /** Fichier illisible : compte ferme (jamais reecrit). */
        boolean ferme;

        Compte(UUID uuid) {
            this.uuid = uuid;
        }
    }

    /** Paiement fait : part prise dans l'inventaire, part prise sur le compte. */
    public record Paiement(long inventaire, long compte) {
        public long total() {
            return inventaire + compte;
        }
    }

    private static final Map<UUID, Compte> CACHE = new HashMap<>();

    /** Une fois, au chargement de la partie serveur. */
    public static void init() {
        // oublie tout a l'arret (monde solo suivant)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, e -> CACHE.clear());
    }

    public static CasinoConfig.Compte reglages() {
        return CasinoConfig.get().compte;
    }

    public static boolean actif() {
        return reglages().actif;
    }

    /** La caisse et l'entree prennent sur le compte ce qui manque dans l'inventaire. */
    public static boolean paiementOuvert() {
        return reglages().actif && reglages().payerCaisse;
    }

    public static Path dossier() {
        return FMLPaths.GAMEDIR.get().resolve("heralizcasino").resolve("comptes");
    }

    private static Path fichier(UUID u) {
        return dossier().resolve(u + ".json");
    }

    // ------------------------------------------------------------------ lecture, ecriture, journal
    private static Compte compte(UUID u) {
        Compte c = CACHE.get(u);
        if (c != null) return c;
        c = new Compte(u);
        Path f = fichier(u);
        if (Files.isRegularFile(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("nom")) c.nom = o.get("nom").getAsString();
                long s = o.get("solde").getAsLong();
                if (s < 0) throw new IllegalStateException("solde negatif : " + s);
                c.solde = s;
            } catch (Exception ex) {
                c.solde = 0;
                c.ferme = true;
                HeralizCasino.LOGGER.error("[HeralizCasino] compte de capsules illisible ({}) : {} — compte FERMÉ, fichier gardé tel quel", f, ex.toString());
                try {
                    Files.copy(f, f.resolveSibling(u + ".json.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignore) {
                }
            }
        }
        CACHE.put(u, c);
        return c;
    }

    private static boolean ecrire(Compte c) {
        if (c.ferme) return false;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("version", 1);
            o.addProperty("nom", c.nom);
            o.addProperty("solde", c.solde);
            Path f = fichier(c.uuid);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(c.uuid + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            HeralizCasino.LOGGER.error("[HeralizCasino] compte de capsules de {} impossible à écrire : {}", c.uuid, ex.toString());
            return false;
        }
    }

    private static void journal(Compte c, String op, long n, String source) {
        Path f = dossier().getParent().resolve("comptes_journal.log");
        String ligne = java.time.LocalDateTime.now().withNano(0) + " " + c.nom + " " + c.uuid + " " + op + n + " -> " + c.solde
                + " (" + source + ")\n";
        EcrituresFond.ajouter(f, () -> {
            Files.createDirectories(f.getParent());
            Files.writeString(f, ligne, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        });
    }

    /**
     * Change le solde de {@code delta} et ecrit le compte AVANT de rendre vrai. Faux (rien change) si le compte est ferme,
     * si le solde passerait sous 0 ou au-dessus du plafond, ou si l'ecriture echoue.
     */
    private static boolean changer(UUID u, @Nullable String nom, long delta, String source) {
        if (delta == 0 || Math.abs(delta) > MAX_OPERATION) return false;
        Compte c = compte(u);
        if (c.ferme) return false;
        long avant = c.solde, apres = avant + delta;
        if (apres < 0 || apres > reglages().max) return false;
        String ancienNom = c.nom;
        if (nom != null && !nom.isEmpty()) c.nom = nom;
        c.solde = apres;
        if (!ecrire(c)) {
            c.solde = avant;
            c.nom = ancienNom;
            return false;
        }
        journal(c, delta > 0 ? "+" : "-", Math.abs(delta), source);
        envoyer(u);
        return true;
    }

    // ------------------------------------------------------------------ API (fil serveur)
    public static long solde(UUID u) {
        return Math.max(0, compte(u).solde);
    }

    public static boolean ouvert(UUID u) {
        return !compte(u).ferme;
    }

    /** Ajoute {@code n} capsules au compte (joueur en ligne ou non). Faux : rien change. */
    public static boolean crediter(UUID u, @Nullable String nom, long n, String source) {
        return n > 0 && changer(u, nom, n, source);
    }

    /** Retire {@code n} capsules du compte s'il les a. Faux : rien change. */
    public static boolean debiter(UUID u, @Nullable String nom, long n, String source) {
        return n > 0 && changer(u, nom, -n, source);
    }

    /** Remise a zero (/wipe) : solde a 0. Faux si le compte est ferme ou si l'ecriture echoue. */
    public static boolean remettreAZero(UUID u, String source) {
        Compte c = compte(u);
        if (c.ferme) return false;
        if (c.solde == 0) return true;
        long avant = c.solde;
        c.solde = 0;
        if (!ecrire(c)) {
            c.solde = avant;
            return false;
        }
        journal(c, "-", avant, source);
        envoyer(u);
        return true;
    }

    /** Capsules utilisables a la caisse : inventaire, plus le compte si le paiement par le compte est ouvert. */
    public static long disponible(ServerPlayer p) {
        long inv = Chips.count(p);
        return paiementOuvert() && ouvert(p.getUUID()) ? inv + solde(p.getUUID()) : inv;
    }

    /**
     * Paie {@code montant} capsules : d'abord avec les capsules de l'inventaire (monnaie rendue comme toujours), puis, si
     * elles ne suffisent pas et que le paiement par le compte est ouvert, le reste est pris sur le compte et toutes les
     * capsules de l'inventaire partent (paiement exact, aucune monnaie). Null : rien n'a ete pris.
     */
    @Nullable
    public static Paiement payer(ServerPlayer p, long montant, String source) {
        if (montant < 0) return null;
        if (montant == 0) return new Paiement(0, 0);
        ChipsServeur.convertirAnciennes(p);
        long inv = Chips.count(p);
        if (inv >= montant || !paiementOuvert()) return ChipsServeur.take(p, montant) ? new Paiement(montant, 0) : null;
        long manque = montant - inv;
        if (solde(p.getUUID()) < manque) return null;
        if (!debiter(p.getUUID(), p.getGameProfile().getName(), manque, source)) return null;
        if (inv > 0 && !ChipsServeur.take(p, inv)) {
            // impossible (compte juste avant, sur le meme fil) : le compte est rendu
            crediter(p.getUUID(), p.getGameProfile().getName(), manque, source + " (annule)");
            return null;
        }
        sauverJoueur(p);
        return new Paiement(inv, manque);
    }

    /**
     * Rend {@code montant} capsules d'un paiement (achat qui n'a rien donne) : d'abord sur le compte, a hauteur de ce qui y
     * a ete pris, le reste en capsules dans l'inventaire (au sol s'il est plein, comme les gains).
     */
    public static void rendre(ServerPlayer p, Paiement pay, long montant, String source) {
        long r = Math.max(0, Math.min(montant, pay.total()));
        long surCompte = Math.min(r, pay.compte());
        if (surCompte > 0 && !crediter(p.getUUID(), p.getGameProfile().getName(), surCompte, source)) surCompte = 0;
        if (r - surCompte > 0) ChipsServeur.give(p, r - surCompte);
        if (surCompte > 0) sauverJoueur(p);
    }

    // ------------------------------------------------------------------ depot et retrait (commande /capsules)
    /** Le joueur est a moins de {@code distance} blocs d'une caisse du casino (0 : partout). */
    public static boolean pres(ServerPlayer p) {
        double d = reglages().distance;
        if (d <= 0) return true;
        ServerLevel lvl = p.serverLevel();
        Vec3 pos = p.position();
        BlockPos c = p.blockPosition();
        int r = (int) Math.ceil(d) + 1;
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                LevelChunk ch = lvl.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (be instanceof CashierBlockEntity && Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(pos) <= d * d) return true;
                }
            }
        }
        return false;
    }

    /** Ouvert, et a cote d'une caisse ; sinon le joueur est prevenu. */
    private static boolean peutOperer(ServerPlayer p) {
        if (!actif()) {
            dire(p, msg("ferme_partout", "Le compte de capsules est fermé pour le moment.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (p.isSpectator()) return false;
        if (!ouvert(p.getUUID())) {
            dire(p, msg("bloque", "Ton compte de capsules est bloqué : préviens le staff.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!pres(p)) {
            dire(p, msg("loin", "Va à la caisse du casino pour déposer ou retirer des capsules.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /**
     * Depot de {@code demande} capsules (0 = toutes) de l'inventaire sur le compte. Sans monnaie : si les capsules du
     * joueur ne forment pas exactement ce nombre, le plus grand nombre en dessous est depose (et le joueur le sait).
     */
    public static void deposer(ServerPlayer p, long demande) {
        if (!peutOperer(p)) return;
        ChipsServeur.convertirAnciennes(p);
        Inventory inv = p.getInventory();
        long[] have = new long[Chips.VALUES.length];
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) {
                int k = Chips.indexOf(c.value);
                if (k >= 0) {
                    have[k] += s.getCount();
                    total += (long) c.value * s.getCount();
                }
            }
        }
        if (total <= 0) {
            dire(p, msg("rien", "Tu n'as aucune capsule sur toi.").withStyle(ChatFormatting.RED));
            return;
        }
        long vise = demande <= 0 ? total : Math.min(demande, total);
        // les plus grosses capsules d'abord : avec 1, 5 et 10, c'est le plus grand nombre possible sous « vise »
        long[] use = new long[Chips.VALUES.length];
        long rem = vise;
        for (int k = Chips.VALUES.length - 1; k >= 0; k--) {
            use[k] = Math.min(have[k], rem / Chips.VALUES[k]);
            rem -= use[k] * Chips.VALUES[k];
        }
        long n = vise - rem;
        if (n <= 0) {
            dire(p, msg("pas_monnaie", "Tes capsules ne permettent pas de déposer %s : dépose-les toutes avec /capsules deposer.",
                    Chips.format(vise)).withStyle(ChatFormatting.RED));
            return;
        }
        if (solde(p.getUUID()) + n > reglages().max) {
            dire(p, msg("plein", "Ton compte est plein (%s capsules au plus).", Chips.format(reglages().max)).withStyle(ChatFormatting.RED));
            return;
        }
        // le compte d'abord (ecrit sur le disque) ; ecriture ratee : rien n'a bouge
        if (!crediter(p.getUUID(), p.getGameProfile().getName(), n, "depot")) {
            dire(p, msg("erreur", "Opération impossible pour le moment, réessaie.").withStyle(ChatFormatting.RED));
            return;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) {
                int k = Chips.indexOf(c.value);
                if (k < 0 || use[k] <= 0) continue;
                int m = (int) Math.min(use[k], s.getCount());
                s.shrink(m);
                use[k] -= m;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
        sauverJoueur(p);
        long s = solde(p.getUUID());
        if (demande > 0 && n < demande && n < total) {
            dire(p, msg("depose_arrondi", "%s capsules déposées (pas de monnaie pour %s). Compte : %s capsules.",
                    Chips.format(n), Chips.format(demande), Chips.format(s)).withStyle(ChatFormatting.GREEN));
        } else {
            dire(p, msg("depose", "%s capsules déposées. Compte : %s capsules.", Chips.format(n), Chips.format(s)).withStyle(ChatFormatting.GREEN));
        }
    }

    /** Retrait de {@code n} capsules du compte, refuse si elles ne tiennent pas toutes dans l'inventaire. */
    public static void retirer(ServerPlayer p, long n) {
        if (n <= 0 || !peutOperer(p)) return;
        long s = solde(p.getUUID());
        if (s < n) {
            dire(p, msg("pas_assez", "Ton compte n'a que %s capsules.", Chips.format(s)).withStyle(ChatFormatting.RED));
            return;
        }
        List<ItemStack> piles = ChipsServeur.stacks(n);
        if (!tiennent(p.getInventory(), piles)) {
            dire(p, msg("place", "Pas assez de place dans ton inventaire pour %s capsules.", Chips.format(n)).withStyle(ChatFormatting.RED));
            return;
        }
        if (!debiter(p.getUUID(), p.getGameProfile().getName(), n, "retrait")) {
            dire(p, msg("erreur", "Opération impossible pour le moment, réessaie.").withStyle(ChatFormatting.RED));
            return;
        }
        Inventory inv = p.getInventory();
        for (ItemStack pile : piles) {
            // place verifiee juste avant : jamais au sol ; par securite, le reste irait a ses pieds
            if (!inv.add(pile) && !pile.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, pile);
        }
        inv.setChanged();
        sauverJoueur(p);
        dire(p, msg("retire", "%s capsules retirées. Compte : %s capsules.", Chips.format(n), Chips.format(solde(p.getUUID())))
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Les piles tiennent toutes dans l'inventaire, comme {@link Inventory#add} les y mettrait : d'abord sur les piles
     * pareilles (36 cases et main gauche), puis dans les cases vides des 36 cases.
     */
    static boolean tiennent(Inventory inv, List<ItemStack> piles) {
        int vides = 0;
        for (int i = 0; i < inv.items.size(); i++) if (inv.items.get(i).isEmpty()) vides++;
        Map<net.minecraft.world.item.Item, Long> besoin = new HashMap<>();
        for (ItemStack s : piles) besoin.merge(s.getItem(), (long) s.getCount(), Long::sum);
        for (Map.Entry<net.minecraft.world.item.Item, Long> e : besoin.entrySet()) {
            ItemStack ref = new ItemStack(e.getKey());
            int max = ref.getMaxStackSize();
            long place = 0;
            for (ItemStack s : inv.items) if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, ref)) place += Math.max(0, max - s.getCount());
            for (ItemStack s : inv.offhand) if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, ref)) place += Math.max(0, max - s.getCount());
            long reste = Math.max(0, e.getValue() - place);
            vides -= (int) ((reste + max - 1) / max);
            if (vides < 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ affichage
    /** Envoie son solde au joueur s'il est en ligne (boutons de la caisse). */
    public static void envoyer(UUID u) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(u);
        if (p != null) envoyer(p);
    }

    public static void envoyer(ServerPlayer p) {
        CasinoNet.sendTo(p, new ComptePacket(actif() ? solde(p.getUUID()) : 0));
    }

    /** Solde et aide (commande /capsules). */
    public static void montrer(ServerPlayer p) {
        if (!actif()) {
            dire(p, msg("ferme_partout", "Le compte de capsules est fermé pour le moment.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!ouvert(p.getUUID())) {
            dire(p, msg("bloque", "Ton compte de capsules est bloqué : préviens le staff.").withStyle(ChatFormatting.RED));
            return;
        }
        dire(p, msg("solde", "Compte de capsules : %s · sur toi : %s", Chips.format(solde(p.getUUID())), Chips.format(Chips.count(p)))
                .withStyle(ChatFormatting.GOLD));
        dire(p, msg("aide", "À la caisse du casino : /capsules deposer [nombre] (rien = tout), /capsules retirer <nombre>. "
                + "La caisse prend sur ton compte ce qui manque sur toi.").withStyle(ChatFormatting.GRAY));
    }

    public static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heralizcasino.compte." + cle, fr, args);
    }

    private static void dire(ServerPlayer p, Component c) {
        p.sendSystemMessage(c);
    }

    // ------------------------------------------------------------------ sauvegarde du joueur
    private static Method sauver;

    /** Sauvegarde tout de suite ce joueur seulement ({@code PlayerList.save}, protegee : par reflexion, comme /trade). */
    static void sauverJoueur(ServerPlayer p) {
        try {
            if (sauver == null) {
                sauver = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.server.players.PlayerList.class,
                        "m_6765_", ServerPlayer.class);
            }
            sauver.invoke(p.server.getPlayerList(), p);
        } catch (Exception ex) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] sauvegarde ciblée impossible, sauvegarde de tous les joueurs : {}", ex.toString());
            p.server.getPlayerList().saveAll();
        }
    }
}
