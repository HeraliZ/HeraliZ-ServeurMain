package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Inventaire du joueur mis de côté pendant le tutoriel des bases (retours du propriétaire du 03/11) : « on fait le tuto :
 * ça met le stuff du joueur en attente, ça le téléporte, ça lui donne un nouvel inventaire, puis ça lui rend le sien à la
 * fin ». Avant, le kit s'ajoutait à ce qu'il portait (inventaire plein : objets du kit au sol, mélange avec ses affaires).
 * <ul>
 *   <li><b>Mise de côté</b> ({@link #mettreDeCote}, juste avant le kit) : inventaire, armure, main gauche, case choisie,
 *   objet tenu à la souris, grille 2 × 2, emplacements Curios (sac porté compris, son contenu écrit dans l'objet avant)
 *   sont écrits sur le disque, hors du monde ({@code crac/tutoriel_inventaires/<uuid>.<n°>.depot.dat} : écrit à part,
 *   synchronisé, relu et comparé, puis mis en place d'un coup). Seulement ensuite le joueur est vidé, marqué
 *   ({@code PlayerPersisted.CracTutoDepot} = n° du dépôt) et enregistré tout de suite. Fichier non écrit : rien ne
 *   change, le tutoriel attend. L'expérience n'est pas touchée (rien ne peut la faire perdre pendant le tutoriel).</li>
 *   <li><b>Retour</b> ({@link #rendre}) à toute sortie du tutoriel : fin, {@code /tuto finir}, {@code /tuto rejouer},
 *   {@code /tuto lancer}, sortie du tutoriel par une téléportation, connexion de qui n'a plus de tutoriel en cours (fin
 *   pendant son absence, plantage puis redémarrage, {@code /wipe tout}). Les objets du kit sont repris avant. Chaque
 *   objet revient à sa place ; ce qui est arrivé dans son inventaire pendant le tutoriel (et ce qui était à la souris ou
 *   dans la grille) est ajouté ensuite. Ce qui ne rentre pas n'est jamais posé au sol : gardé dans un fichier
 *   d'attente ({@code <uuid>.<n°>.attente.dat}) et rendu dès qu'une case se libère (essai chaque seconde, et à la
 *   connexion).</li>
 *   <li><b>Jamais de double, jamais de perte</b> : la marque du joueur dit quel fichier est le bon, et elle est
 *   enregistrée en même temps que son inventaire (même fichier de joueur de Minecraft). Retour : inventaire remis, marque
 *   retirée, joueur enregistré, PUIS fichier effacé. Un plantage entre deux étapes : soit le joueur n'a pas été
 *   enregistré (marque toujours là : on recommence), soit il l'a été (marque partie : le fichier restant n'est plus
 *   jamais rendu, il est rangé dans {@code enquete/} pour le staff). Un fichier illisible : rangé dans {@code enquete/},
 *   jamais effacé.</li>
 *   <li>Mort pendant le tutoriel : le dépôt n'est pas sur lui, il ne tombe pas. Objets à vie : gardés comme le reste.</li>
 * </ul>
 * Fil du serveur seulement.
 */
@EventBusSubscriber(modid = "crac")
public final class InventaireTuto {
    private static final int VERSION = 1;
    private static final String CLE_DEPOT = "CracTutoDepot";
    private static final String CLE_ATTENTE = "CracTutoAttente";
    /** Joueurs connectés qui ont des objets en attente (rendus dès qu'une case se libère). */
    private static final Set<UUID> EN_ATTENTE = new HashSet<>();
    @Nullable
    private static Method sauverJoueur;

    private InventaireTuto() {
    }

    // ------------------------------------------------------------------------------------------------ marques

    private static CompoundTag persistant(Player p) {
        CompoundTag racine = p.getPersistentData();
        if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return racine.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static long marque(Player p, String cle) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return t.contains(cle, Tag.TAG_LONG) ? t.getLong(cle) : 0L;
    }

    private static void marquer(Player p, String cle, long n) {
        if (n == 0L) {
            persistant(p).remove(cle);
        } else {
            persistant(p).putLong(cle, n);
        }
    }

    /** L'inventaire du joueur est mis de côté (tutoriel en cours ou retour pas encore fait). */
    public static boolean aUnDepot(Player p) {
        return marque(p, CLE_DEPOT) != 0L;
    }

    // ------------------------------------------------------------------------------------------------ mise de côté

    /**
     * Met de côté tout ce que le joueur porte, juste avant le kit du tutoriel. Vrai : il est vide (ou l'était déjà par un
     * dépôt en cours) ; faux : le fichier n'a pas pu être écrit, rien n'a changé.
     */
    public static boolean mettreDeCote(ServerPlayer p) {
        if (aUnDepot(p)) {
            return true;   // déjà de côté (tutoriel relancé) : son inventaire n'est que le kit
        }
        Path dossier = dossier();
        // 1. objets tenus à la souris et dans la grille 2 × 2 : gardés dans le dépôt (jamais rendus à l'inventaire ni au sol)
        List<ItemStack> aCote = horsInventaire(p);
        if (p.containerMenu != p.inventoryMenu) {
            p.closeContainer();   // un écran ouvert rend ce qu'il tenait à l'inventaire, mis de côté avec lui
        }
        // 2. le sac porté écrit son contenu dans son objet (HeraliZ Backpack, à l'enregistrement du joueur)
        p.saveWithoutId(new CompoundTag());
        long n = nouveauNumero();
        CompoundTag t = new CompoundTag();
        t.putInt("version", VERSION);
        t.putLong("numero", n);
        t.putLong("date", System.currentTimeMillis());
        t.putUUID("uuid", p.getUUID());
        t.putString("nom", p.getGameProfile().getName());
        Inventory inv = p.getInventory();
        t.put("inventaire", inv.save(new ListTag()));
        t.putInt("selection", inv.selected);
        t.put("a_cote", liste(aCote));
        if (curios()) {
            t.put("curios", CuriosTuto.lister(p));
        }
        // 3. sur le disque AVANT de toucher au joueur
        Path f = dossier.resolve(p.getUUID() + "." + n + ".depot.dat");
        if (!ecrire(f, t)) {
            rendreACote(p, aCote);
            return false;
        }
        // 4. seulement maintenant : vidage, marque, enregistrement du joueur
        inv.clearContent();
        if (curios()) {
            CuriosTuto.vider(p);
        }
        marquer(p, CLE_DEPOT, n);
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        sauver(p);
        Crac.LOGGER.info("[CRac] tutoriel : inventaire de {} mis de côté ({} piles, {} à côté, curios {})", p.getGameProfile().getName(),
            t.getList("inventaire", Tag.TAG_COMPOUND).size(), aCote.size(), t.contains("curios") ? t.getList("curios", Tag.TAG_COMPOUND).size() : "-");
        return true;
    }

    // ------------------------------------------------------------------------------------------------ retour

    /**
     * Rend l'inventaire mis de côté (sortie du tutoriel, quelle qu'elle soit), plus {@code enPlus} (ce qui était rangé dans
     * la caisse de la cabane, hors kit). Les objets du kit doivent avoir été repris. Sans dépôt : seulement {@code enPlus}.
     */
    public static void rendre(ServerPlayer p, List<ItemStack> enPlus) {
        List<ItemStack> extras = new ArrayList<>(enPlus);
        long n = marque(p, CLE_DEPOT);
        if (n == 0L) {
            // tutoriel commencé avant le 03/11 (rien de côté) : seulement ce qui revient de la caisse de la cabane
            if (!extras.isEmpty()) {
                donnerOuGarder(p, extras);
                p.inventoryMenu.broadcastChanges();
                sauver(p);
            }
            return;
        }
        Path f = dossier().resolve(p.getUUID() + "." + n + ".depot.dat");
        CompoundTag t = Files.isRegularFile(f) ? lire(f) : null;
        if (t == null || t.getLong("numero") != n) {
            // fichier absent ou illisible : rien ne peut être rendu automatiquement (staff : dossier enquete/)
            Crac.LOGGER.error("[CRac] tutoriel : inventaire mis de côté de {} introuvable ou illisible ({}) : rien rendu, voir {}",
                p.getGameProfile().getName(), f.getFileName(), dossier().resolve("enquete"));
            if (Files.exists(f)) {
                enquete(f, "illisible");
            }
            marquer(p, CLE_DEPOT, 0L);
            donnerOuGarder(p, extras);
            sauver(p);
            T.chat(p, T.t("crac.tuto.inventaire_erreur_retour", "[Tuto] Ton inventaire n'a pas pu être rendu automatiquement : préviens le staff (rien n'est perdu)."),
                ChatFormatting.RED);
            return;
        }
        // tout ce que le joueur tient maintenant revient avec le reste, après son inventaire ; un objet du kit encore là
        // (grille d'artisanat, souris, écran ouvert) n'est jamais rendu (06/11, sécurité en plus de Tutoriel.reprendreKit)
        for (ItemStack s : horsInventaire(p)) {
            if (!Tutoriel.estKit(s)) {
                extras.add(s);
            }
        }
        if (p.containerMenu != p.inventoryMenu) {
            p.closeContainer();
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.removeItemNoUpdate(i);
            if (!s.isEmpty() && !Tutoriel.estKit(s)) {
                extras.add(s);
            }
        }
        // son inventaire, chaque objet à sa place (Inventory.load vide d'abord : tout le reste a été pris juste avant)
        inv.load(t.getList("inventaire", Tag.TAG_COMPOUND));
        inv.selected = Math.max(0, Math.min(8, t.getInt("selection")));
        if (t.contains("curios")) {
            if (curios()) {
                CuriosTuto.remettre(p, t.getList("curios", Tag.TAG_COMPOUND), extras);
            } else {
                objetsCurios(t.getList("curios", Tag.TAG_COMPOUND), extras);   // Curios retiré du serveur : avec le reste
            }
        }
        extras.addAll(0, objets(t.getList("a_cote", Tag.TAG_COMPOUND)));
        marquer(p, CLE_DEPOT, 0L);
        donnerOuGarder(p, extras);
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        // l'inventaire rendu et la marque retirée sont enregistrés ensemble ; le fichier n'est effacé qu'après
        if (sauver(p)) {
            supprimer(f);
        } else {
            Crac.LOGGER.error("[CRac] tutoriel : inventaire de {} rendu mais joueur non enregistré : {} gardé (jamais rendu une 2e fois)",
                p.getGameProfile().getName(), f.getFileName());
        }
        T.chat(p, T.t("crac.tuto.inventaire_rendu", "[Tuto] Ton inventaire t'a été rendu."), ChatFormatting.GREEN);
        Crac.LOGGER.info("[CRac] tutoriel : inventaire de {} rendu", p.getGameProfile().getName());
    }

    /**
     * Donne ces objets au joueur ; ce qui ne rentre pas rejoint ses objets en attente (fichier écrit avant que la marque ne
     * change ; le joueur doit être enregistré ensuite par l'appelant). Jamais au sol.
     */
    private static void donnerOuGarder(ServerPlayer p, List<ItemStack> objets) {
        List<ItemStack> reste = new ArrayList<>();
        for (ItemStack s : objets) {
            if (s.isEmpty()) {
                continue;
            }
            p.getInventory().add(s);
            if (!s.isEmpty()) {
                reste.add(s);
            }
        }
        if (reste.isEmpty()) {
            return;
        }
        // avec ce qui attendait déjà : un seul fichier d'attente à la fois
        long avant = marque(p, CLE_ATTENTE);
        Path fAvant = avant == 0L ? null : dossier().resolve(p.getUUID() + "." + avant + ".attente.dat");
        CompoundTag ta = fAvant != null && Files.isRegularFile(fAvant) ? lire(fAvant) : null;
        if (ta != null && ta.getLong("numero") == avant) {
            reste.addAll(objets(ta.getList("objets", Tag.TAG_COMPOUND)));
        }
        long n = nouveauNumero();
        CompoundTag t = new CompoundTag();
        t.putInt("version", VERSION);
        t.putLong("numero", n);
        t.putLong("date", System.currentTimeMillis());
        t.putUUID("uuid", p.getUUID());
        t.putString("nom", p.getGameProfile().getName());
        t.put("objets", liste(reste));
        if (ecrire(dossier().resolve(p.getUUID() + "." + n + ".attente.dat"), t)) {
            marquer(p, CLE_ATTENTE, n);
            EN_ATTENTE.add(p.getUUID());
            if (fAvant != null) {
                ANCIENS.add(fAvant);   // effacé après le prochain enregistrement réussi
            }
            T.chat(p, T.t("crac.tuto.inventaire_attente", "[Tuto] %s objet(s) n'ont pas tenu dans ton inventaire : ils te sont rendus dès qu'une case se libère.",
                String.valueOf(reste.size() - (ta == null ? 0 : ta.getList("objets", Tag.TAG_COMPOUND).size()))), ChatFormatting.YELLOW);
        } else {
            // disque en panne : gardés avec le monde par CRac (rendus dès qu'une case se libère), jamais au sol
            for (ItemStack s : reste) {
                ObjetsAVieBases.garder(p.server, p.getUUID(), s, "tutoriel, inventaire plein et disque en erreur");
            }
        }
    }

    /** Fichiers d'attente remplacés : effacés après l'enregistrement du joueur qui a la nouvelle marque. */
    private static final List<Path> ANCIENS = new ArrayList<>();

    /** Toutes les secondes (Tutoriel.seconde) : objets en attente rendus dès qu'une case se libère. */
    static void seconde(MinecraftServer server) {
        if (EN_ATTENTE.isEmpty()) {
            return;
        }
        for (UUID u : List.copyOf(EN_ATTENTE)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) {
                EN_ATTENTE.remove(u);
            } else if (p.isAlive() && !p.isSpectator() && !Tutoriel.occupe(server, u)) {
                donnerAttente(p);
            }
        }
    }

    private static void donnerAttente(ServerPlayer p) {
        long n = marque(p, CLE_ATTENTE);
        if (n == 0L) {
            EN_ATTENTE.remove(p.getUUID());
            return;
        }
        if (p.getInventory().getFreeSlot() < 0) {
            return;   // aucune case libre : rien à essayer (une pile entamée se remplit aussi, mais attend la case libre)
        }
        Path f = dossier().resolve(p.getUUID() + "." + n + ".attente.dat");
        CompoundTag t = Files.isRegularFile(f) ? lire(f) : null;
        if (t == null || t.getLong("numero") != n) {
            Crac.LOGGER.error("[CRac] tutoriel : objets en attente de {} introuvables ou illisibles ({}), voir {}", p.getGameProfile().getName(),
                f.getFileName(), dossier().resolve("enquete"));
            if (Files.exists(f)) {
                enquete(f, "illisible");
            }
            marquer(p, CLE_ATTENTE, 0L);
            EN_ATTENTE.remove(p.getUUID());
            sauver(p);
            return;
        }
        List<ItemStack> objets = objets(t.getList("objets", Tag.TAG_COMPOUND));
        int total = objets.size();
        List<ItemStack> reste = new ArrayList<>();
        for (ItemStack s : objets) {
            p.getInventory().add(s);
            if (!s.isEmpty()) {
                reste.add(s);
            }
        }
        int rendus = total - reste.size();
        if (rendus == 0 && reste.size() == total && sansChangement(objets, t)) {
            return;   // rien n'est entré
        }
        if (reste.isEmpty()) {
            marquer(p, CLE_ATTENTE, 0L);
            EN_ATTENTE.remove(p.getUUID());
            if (sauver(p)) {
                supprimer(f);
            }
        } else {
            long m = nouveauNumero();
            CompoundTag t2 = t.copy();
            t2.putLong("numero", m);
            t2.putLong("date", System.currentTimeMillis());
            t2.put("objets", liste(reste));
            Path f2 = dossier().resolve(p.getUUID() + "." + m + ".attente.dat");
            if (!ecrire(f2, t2)) {
                // le nouveau fichier n'est pas écrit : on reprend ce qui vient d'être donné (l'ancien fichier reste le bon)
                reprendre(p, objets(t.getList("objets", Tag.TAG_COMPOUND)), reste);
                return;
            }
            marquer(p, CLE_ATTENTE, m);
            if (sauver(p)) {
                supprimer(f);
            }
        }
        p.inventoryMenu.broadcastChanges();
        if (rendus > 0) {
            T.chat(p, T.t("crac.tuto.attente_rendue", "[Tuto] %s objet(s) mis de côté t'ont été rendus.", String.valueOf(rendus)),
                ChatFormatting.GREEN);
        }
    }

    /** Vrai si aucune pile n'a changé de taille (rien n'est entré, même en partie). */
    private static boolean sansChangement(List<ItemStack> apres, CompoundTag t) {
        List<ItemStack> avant = objets(t.getList("objets", Tag.TAG_COMPOUND));
        for (int i = 0; i < avant.size(); i++) {
            if (avant.get(i).getCount() != apres.get(i).getCount()) {
                return false;
            }
        }
        return true;
    }

    /** Annule un don partiel : retire de l'inventaire ce qui vient d'y entrer (l'ancien fichier d'attente reste valable). */
    private static void reprendre(ServerPlayer p, List<ItemStack> avant, List<ItemStack> reste) {
        Inventory inv = p.getInventory();
        for (int k = 0; k < avant.size(); k++) {
            ItemStack voulu = avant.get(k);
            int aRetirer = voulu.getCount();
            for (ItemStack r : reste) {
                if (ItemStack.isSameItemSameTags(r, voulu)) {
                    aRetirer -= Math.min(aRetirer, r.getCount());
                }
            }
            for (int i = inv.getContainerSize() - 1; i >= 0 && aRetirer > 0; i--) {
                ItemStack s = inv.getItem(i);
                if (ItemStack.isSameItemSameTags(s, voulu)) {
                    int k2 = Math.min(aRetirer, s.getCount());
                    s.shrink(k2);
                    aRetirer -= k2;
                }
            }
        }
        p.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------------------------------------ connexion

    /** Connexion : objets en attente suivis ; marques dont le fichier a déjà servi ignorées (jamais rendu deux fois). */
    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && marque(p, CLE_ATTENTE) != 0L) {
            EN_ATTENTE.add(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        EN_ATTENTE.remove(e.getEntity().getUUID());
    }

    // ------------------------------------------------------------------------------------------------ /wipe

    /**
     * {@code /wipe <joueur>} (HeraliZ Quêtes, par {@code WipeApi.inventaireTutoriel}) : tout ce qui était mis de côté pour
     * ce joueur connecté (dépôt et attente) est retiré et renvoyé ; Quêtes en garde les objets à vie et efface le reste.
     */
    public static List<ItemStack> vider(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        long n = marque(p, CLE_DEPOT);
        if (n != 0L) {
            Path f = dossier().resolve(p.getUUID() + "." + n + ".depot.dat");
            CompoundTag t = Files.isRegularFile(f) ? lire(f) : null;
            if (t != null && t.getLong("numero") == n) {
                Inventory tmp = new Inventory(p);
                tmp.load(t.getList("inventaire", Tag.TAG_COMPOUND));
                for (int i = 0; i < tmp.getContainerSize(); i++) {
                    if (!tmp.getItem(i).isEmpty()) {
                        out.add(tmp.getItem(i));
                    }
                }
                out.addAll(objets(t.getList("a_cote", Tag.TAG_COMPOUND)));
                objetsCurios(t.getList("curios", Tag.TAG_COMPOUND), out);
                enquete(f, "wipe");
            }
            marquer(p, CLE_DEPOT, 0L);
        }
        long a = marque(p, CLE_ATTENTE);
        if (a != 0L) {
            Path f = dossier().resolve(p.getUUID() + "." + a + ".attente.dat");
            CompoundTag t = Files.isRegularFile(f) ? lire(f) : null;
            if (t != null && t.getLong("numero") == a) {
                out.addAll(objets(t.getList("objets", Tag.TAG_COMPOUND)));
                enquete(f, "wipe");
            }
            marquer(p, CLE_ATTENTE, 0L);
            EN_ATTENTE.remove(p.getUUID());
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ outils

    static boolean curios() {
        return ModList.get() != null && ModList.get().isLoaded("curios");
    }

    private static long nouveauNumero() {
        long n;
        do {
            n = ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE;
        } while (n == 0L);
        return n;
    }

    static Path dossier() {
        return FMLPaths.GAMEDIR.get().resolve("crac").resolve("tutoriel_inventaires");
    }

    /** Souris et grille 2 × 2 de l'inventaire vidées : leurs objets. */
    private static List<ItemStack> horsInventaire(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        ItemStack souris = p.containerMenu.getCarried();
        if (!souris.isEmpty()) {
            l.add(souris);
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grille.getContainerSize(); i++) {
            ItemStack s = grille.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
                l.add(s);
            }
        }
        return l;
    }

    /** Dépôt pas écrit : souris et grille rendues (inventaire, sinon souris), comme avant le clic. */
    private static void rendreACote(ServerPlayer p, List<ItemStack> objets) {
        for (ItemStack s : objets) {
            p.getInventory().add(s);
            if (!s.isEmpty() && p.containerMenu.getCarried().isEmpty()) {
                p.containerMenu.setCarried(s);
            } else if (!s.isEmpty()) {
                ObjetsAVieBases.garder(p.server, p.getUUID(), s, "tutoriel, dépôt impossible");
            }
        }
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
    }

    private static ListTag liste(List<ItemStack> objets) {
        ListTag l = new ListTag();
        for (ItemStack s : objets) {
            if (!s.isEmpty()) {
                l.add(s.save(new CompoundTag()));
            }
        }
        return l;
    }

    private static List<ItemStack> objets(ListTag l) {
        List<ItemStack> out = new ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Les objets d'une liste de Curios gardée ({@code CuriosTuto.lister} : {Id, Case, Cosm, Objet}), sans Curios. */
    private static void objetsCurios(ListTag l, List<ItemStack> out) {
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i).getCompound("Objet"));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
    }

    /** Enregistrement immédiat du joueur ({@code PlayerList.save}, protégée : par réflexion, nom SRG m_6765_). */
    static boolean sauver(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) {
            return false;
        }
        try {
            if (sauverJoueur == null) {
                sauverJoueur = ObfuscationReflectionHelper.findMethod(PlayerList.class, "m_6765_", ServerPlayer.class);
            }
            sauverJoueur.invoke(s.getPlayerList(), p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Crac.LOGGER.error("[CRac] tutoriel : enregistrement de {} impossible", p.getGameProfile().getName(), e);
            return false;
        }
        // anciens fichiers d'attente : la nouvelle marque est enregistrée, ils ne servent plus
        for (Path f : List.copyOf(ANCIENS)) {
            if (f.getFileName().toString().startsWith(p.getUUID() + ".")) {
                supprimer(f);
                ANCIENS.remove(f);
            }
        }
        return true;
    }

    /** Écrit le fichier d'un coup et vérifié (tmp, synchronisé, relu, mis en place) ; faux si quoi que ce soit échoue. */
    static boolean ecrire(Path f, CompoundTag t) {
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try {
            Files.createDirectories(f.getParent());
            ByteArrayOutputStream mem = new ByteArrayOutputStream(16384);
            NbtIo.writeCompressed(t, mem);
            byte[] octets = mem.toByteArray();
            try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
                out.write(octets);
                out.getFD().sync();
            }
            CompoundTag relu = NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(tmp)));
            if (!t.equals(relu)) {
                throw new IOException("relecture différente");
            }
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            Crac.LOGGER.error("[CRac] tutoriel : écriture de {} impossible", f, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // rien
            }
            return false;
        }
    }

    @Nullable
    static CompoundTag lire(Path f) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(f)));
        } catch (IOException | RuntimeException e) {
            Crac.LOGGER.error("[CRac] tutoriel : lecture de {} impossible", f, e);
            return null;
        }
    }

    private static void supprimer(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            Crac.LOGGER.error("[CRac] tutoriel : {} servi mais pas effacé (jamais rendu une 2e fois : sa marque est partie)", f, e);
        }
    }

    /** Range le fichier dans {@code enquete/} (jamais effacé). */
    private static void enquete(Path f, String motif) {
        try {
            Path d = f.getParent().resolve("enquete");
            Files.createDirectories(d);
            String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Files.move(f, d.resolve(f.getFileName().toString().replace(".dat", "") + "-" + motif + "-" + date + ".dat"),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Crac.LOGGER.error("[CRac] tutoriel : {} non rangé dans enquete/", f, e);
        }
    }
}
