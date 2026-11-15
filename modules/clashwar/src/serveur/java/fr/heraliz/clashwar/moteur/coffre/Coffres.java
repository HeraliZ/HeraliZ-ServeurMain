package fr.heraliz.clashwar.moteur.coffre;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.api.Coffre;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.clashwar.moteur.api.Equipement;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.clashwar.moteur.api.Partie;
import fr.heraliz.clashwar.moteur.compat.Compat;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.clashwar.moteur.compat.Tactical;
import fr.heraliz.clashwar.moteur.spectateur.Spectateurs;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation du {@link Coffre} (MATCHS_SPEC §1.1) : instantane complet du joueur sur disque, vidage, restauration,
 * reprise apres crash. Format du fichier {@code <serveur>/heraliz_clashwar/coffres/<uuid>.dat} (NbtIo compresse) :
 * <pre>
 * version, raison, date (ms), uuid, nom,
 * inventaire (Inventory.save), selection, curios (saveInventory(false), absent sans Curios),
 * sante, absorption, faim (FoodData), xpNiveau, xpProgres, xpTotal, effets (MobEffectInstance.save), mode (GameType),
 * dim, x, y, z, yaw, pitch, soif (IThirst.serializeNBT, absent sans le mod), brut (saveWithoutId, pour enquete),
 * a_cote (02/11 : objets tenus au curseur ou dans la grille 2 x 2 de l'inventaire au depot, absent s'il n'y en a pas),
 * numero (version 2, 15/11 : numero du depot, le meme que la marque {@link Coffre#CLE_MARQUE} posee sur le joueur)
 * </pre>
 * Objets a vie (02/11, audit point 8) : rien ne tombe au sol au depot (curseur et grille gardes dans le depot), et un
 * objet a vie qui n'est pas un pret du match retrouve dans l'inventaire du match au retour (rendu par HeraliZ Tactique
 * apres une question « Jeter cet objet ? » restee ouverte, par exemple) est rendu avec le reste, jamais efface.
 * Ecriture : octets prepares en memoire, ecrits dans {@code <uuid>.dat.tmp}, synchronises sur le disque (fsync), relus
 * et compares, puis deplaces par ATOMIC_MOVE. Un depot illisible ou une restauration incomplete deplace le fichier dans
 * {@code coffres/enquete/} (jamais supprime).
 * <p>Jamais de double, jamais de perte (15/11, audit clans_bases M4) : apres le vidage, le joueur recoit la marque du depot
 * ({@link Coffre#CLE_MARQUE} = son numero) et il est enregistre tout de suite (avant : pas avant la sauvegarde
 * automatique suivante ; un plantage entre les deux rendait le depot a un joueur qui avait encore tout, objets a vie
 * doubles). Le depot n'est rendu qu'a un joueur qui porte SA marque : marque absente = le joueur n'a jamais ete enregistre
 * vide (plantage juste apres le depot : il a encore son inventaire) ou le depot lui a deja ete rendu (plantage entre son
 * enregistrement et l'effacement du fichier) ; le fichier part alors en enquete, jamais rendu une 2e fois. Au retour :
 * marque retiree, joueur enregistre, PUIS fichier efface. Ce qu'un ecran ouvert ferait tomber au depot (inventaire plein)
 * est garde dans le depot ({@code a_cote}), jamais au sol. Fil serveur.
 */
public final class Coffres {
    private Coffres() {
    }

    /** 2 (15/11) : numero du depot et marque du joueur ; un depot de version 1 (sans numero) est rendu comme avant. */
    public static final int VERSION = 2;
    /** Etiquette de joueur « reste allonge » (lue par Reanimation, posee par les Zombies a terre). */
    private static final String RESTE_ALLONGE = "heraliz_reste_allonge";
    private static Method sauverJoueur;
    /** Joueur dont l'ecran se ferme pendant son depot : ce qui tomberait au sol est garde dans {@link #captures}. */
    @Nullable
    private static ServerPlayer capture;
    @Nullable
    private static List<ItemStack> captures;

    /** Ecouteur du depot (branche par le moteur dans le constructeur du mod). */
    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::lache);
    }

    /**
     * Objet que Minecraft fait tomber pendant la fermeture de l'ecran du depot (inventaire plein : cases d'un atelier,
     * d'une enclume...) : garde dans le depot avec les objets « a cote », jamais au sol.
     */
    private static void lache(ItemTossEvent e) {
        ServerPlayer p = capture;
        List<ItemStack> l = captures;
        if (p == null || l == null || e.getPlayer() != p) return;
        ItemStack s = e.getEntity().getItem();
        if (s.isEmpty()) return;
        l.add(s.copy());
        e.setCanceled(true);
        HeralizClashWar.LOG.info("[matchs] {} : {} x{} garde dans le depot (il serait tombe a la fermeture de l'ecran)",
                p.getGameProfile().getName(), s.getHoverName().getString(), s.getCount());
    }

    // ------------------------------------------------------------------ marque du depot (15/11, audit M4)
    /** Numero du depot que porte le joueur (0 : aucun). */
    public static long marque(Player p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return t.contains(Coffre.CLE_MARQUE, Tag.TAG_LONG) ? t.getLong(Coffre.CLE_MARQUE) : 0L;
    }

    /** Pose (numero different de 0) ou retire (0) la marque du depot ; elle est enregistree avec le joueur. */
    private static void marquer(Player p, long numero) {
        CompoundTag racine = p.getPersistentData();
        if (numero == 0L) {
            if (racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
                racine.getCompound(Player.PERSISTED_NBT_TAG).remove(Coffre.CLE_MARQUE);
            }
            return;
        }
        if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        racine.getCompound(Player.PERSISTED_NBT_TAG).putLong(Coffre.CLE_MARQUE, numero);
    }

    private static long nouveauNumero() {
        long n;
        do {
            n = ThreadLocalRandom.current().nextLong();
        } while (n == 0L);
        return n;
    }

    // ------------------------------------------------------------------ refus
    /**
     * Voir {@link Coffre#refus}. Un passager de vehicule n'est plus refuse (RETOURS du 01/10 §0) : {@link #deposer} le
     * fait descendre ({@link Descente#forcer}) ; {@link Coffre.Refus#VEHICULE} ne sert plus que si cette descente echoue.
     */
    @Nullable
    public static Coffre.Refus refus(ServerPlayer p) {
        if (p.isDeadOrDying() || p.isRemoved()) return Coffre.Refus.MORT;
        if (CombatTag.estTague(p)) return Coffre.Refus.COMBAT;
        if (ATerre.aTerre(p)) return Coffre.Refus.A_TERRE;
        if (Coffre.aUnDepot(p.getUUID())) return Coffre.Refus.DEPOT_EXISTANT;
        if (Coffre.dossier() == null) return Coffre.Refus.ERREUR;
        return null;
    }

    // ------------------------------------------------------------------ depot
    /** Voir {@link Coffre#deposer}. */
    public static boolean deposer(ServerPlayer p, String raison) {
        Coffre.Refus r = refus(p);
        if (r != null) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, r);
            return false;
        }
        // 0. passager d'un vehicule (train compris) : il descend, et l'instantane prend SA position, pas celle du vehicule
        if (!Descente.forcer(p)) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, Coffre.Refus.VEHICULE);
            return false;
        }
        // 1. objets tenus au curseur et dans la grille 2 x 2 de l'inventaire : pris a part et gardes DANS le depot (02/11,
        //    audit point 8 : la fermeture de l'ecran les rendait a l'inventaire, ou les jetait au sol s'il etait plein ;
        //    un objet a vie etait alors mis de cote par Tactique, rendu dans l'inventaire du match et efface a la fin)
        List<ItemStack> aCote = horsInventaire(p);
        // puis fermer le conteneur ouvert : son contenu revient dans l'inventaire ; ce qui n'y tient pas (et tomberait au
        // sol) est garde avec les objets a cote (15/11)
        capture = p;
        captures = aCote;
        try {
            p.closeContainer();
        } finally {
            capture = null;
            captures = null;
        }
        // 2. forcer l'ecriture du sac (mixin addAdditionalSaveData de backpacked) : la copie brute sert aussi d'enquete
        CompoundTag brut = p.saveWithoutId(new CompoundTag());
        // 3. instantane
        CompoundTag t = instantane(p, raison, brut);
        long numero = nouveauNumero();
        t.putLong("numero", numero);
        if (!aCote.isEmpty()) {
            ListTag l = new ListTag();
            for (ItemStack s : aCote) l.add(s.save(new CompoundTag()));
            t.put("a_cote", l);
        }
        // 4. disque (atomique, verifie) AVANT toute modification du joueur
        Path f = Coffre.fichier(p.getUUID());
        if (f == null || !ecrire(f, t)) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} NON ecrit : joueur laisse intact", p.getGameProfile().getName());
            rendreACote(p, aCote);
            return false;
        }
        // 5. seulement maintenant : vidage (malus d'ArkaniaZ compris : tous les effets partent), marque du depot, joueur
        //    enregistre tout de suite (15/11, audit M4 : un plantage avant la sauvegarde automatique rendait le depot a un
        //    joueur qui avait encore tout)
        vider(p);
        CombatTag.effacer(p.getUUID());
        marquer(p, numero);
        if (!sauverJoueur(p)) {
            HeralizClashWar.LOG.error("[matchs] {} vide mais pas enregistre : sa marque le sera avec sa prochaine sauvegarde",
                    p.getGameProfile().getName());
        }
        HeralizClashWar.LOG.info("[matchs] coffre de {} depose ({}) : {} piles, curios {}, soif {}", p.getGameProfile().getName(), raison,
                t.getList("inventaire", Tag.TAG_COMPOUND).size(), t.contains("curios") ? t.getList("curios", Tag.TAG_COMPOUND).size() : "-",
                t.contains("soif") ? "oui" : "-");
        return true;
    }

    /** Instantane NBT complet du joueur (sans rien modifier). */
    public static CompoundTag instantane(ServerPlayer p, String raison, @Nullable CompoundTag brut) {
        CompoundTag t = new CompoundTag();
        t.putInt("version", VERSION);
        t.putString("raison", raison == null ? "" : raison);
        t.putLong("date", System.currentTimeMillis());
        t.putUUID("uuid", p.getUUID());
        t.putString("nom", p.getGameProfile().getName());
        Inventory inv = p.getInventory();
        t.put("inventaire", inv.save(new ListTag()));
        t.putInt("selection", inv.selected);
        ListTag curios = Compat.sauverCurios(p);
        if (curios != null) t.put("curios", curios);
        t.putFloat("sante", p.getHealth());
        t.putFloat("absorption", p.getAbsorptionAmount());
        CompoundTag faim = new CompoundTag();
        p.getFoodData().addAdditionalSaveData(faim);
        t.put("faim", faim);
        t.putInt("xpNiveau", p.experienceLevel);
        t.putFloat("xpProgres", p.experienceProgress);
        t.putInt("xpTotal", p.totalExperience);
        ListTag effets = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) effets.add(e.save(new CompoundTag()));
        t.put("effets", effets);
        t.putInt("mode", p.gameMode.getGameModeForPlayer().getId());
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        if (brut != null) t.put("brut", brut);
        return t;
    }

    /** Vidage apres depot : inventaire, Curios, effets, recharges tactiques, sante et faim pleines. */
    private static void vider(ServerPlayer p) {
        p.getInventory().clearContent();
        Compat.viderCurios(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.setAbsorptionAmount(0);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.getFoodData().setExhaustion(0f);
        Tactical.zeroRecharges(p);
        p.clearFire();
        p.fallDistance = 0;
        p.setGameMode(GameType.SURVIVAL);
        p.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ restauration
    /** Voir {@link Coffre#rendre}. */
    public static boolean rendre(ServerPlayer p) {
        Path f = Coffre.fichier(p.getUUID());
        if (f == null || !Files.isRegularFile(f)) {
            // rien a rendre : on retire quand meme tout objet prete, le drapeau et une marque restee seule
            Equipement.retirer(p);
            detacher(p);
            oublierMarque(p);
            return false;
        }
        CompoundTag t = lire(f);
        if (t == null) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} illisible : deplace en enquete, rien rendu", p.getGameProfile().getName());
            enquete(f, "illisible");
            oublierMarque(p);
            return false;
        }
        long numero = t.getLong("numero");
        if (numero != 0L && marque(p) != numero) {
            // le joueur ne porte pas la marque de ce depot : jamais enregistre vide apres le depot (plantage juste apres :
            // il a encore son inventaire d'avant) ou depot deja rendu (plantage avant l'effacement du fichier) : le rendre
            // copierait ses objets
            HeralizClashWar.LOG.error("[matchs] coffre de {} : marque du joueur {} au lieu de {} (deja rendu, ou joueur jamais enregistre "
                    + "apres le depot) : fichier deplace en enquete, rien rendu", p.getGameProfile().getName(), marque(p), numero);
            enquete(f, "sans_marque");
            Equipement.retirer(p);
            detacher(p);
            oublierMarque(p);
            return false;
        }
        // objets a vie arrives dans l'inventaire du match sans en etre des prets, et ce qui est au curseur ou dans la
        // grille 2 x 2 (sauf les prets du match) : pris a part avant le vidage, rendus apres (02/11, audit point 8)
        List<ItemStack> sauves = sauverAvantVidage(p);
        p.closeContainer();
        Descente.forcer(p);
        detacher(p);
        // vider
        p.getInventory().clearContent();
        Compat.viderCurios(p);
        p.removeAllEffects();
        // recharger
        Inventory inv = p.getInventory();
        inv.load(t.getList("inventaire", Tag.TAG_COMPOUND));
        inv.selected = Math.max(0, Math.min(8, t.getInt("selection")));
        boolean curiosOk = true;
        if (t.contains("curios")) {
            ListTag c = t.getList("curios", Tag.TAG_COMPOUND);
            curiosOk = Compat.chargerCurios(p, c) || c.isEmpty();
        }
        // objets tenus hors de l'inventaire au depot, puis ceux sauves du match
        ListTag ac = t.getList("a_cote", Tag.TAG_COMPOUND);
        for (int i = 0; i < ac.size(); i++) sauves.add(ItemStack.of(ac.getCompound(i)));
        rendreACote(p, sauves);
        GameType mode = GameType.byId(t.getInt("mode"));
        p.setGameMode(mode);
        p.experienceProgress = t.getFloat("xpProgres");
        p.totalExperience = t.getInt("xpTotal");
        p.setExperienceLevels(t.getInt("xpNiveau"));
        p.getFoodData().readAdditionalSaveData(t.getCompound("faim"));
        p.clearFire();
        p.fallDistance = 0;
        teleporter(p, t);
        // effets APRES la teleportation : ServerPlayer.teleportTo vers une autre dimension envoie un paquet de reapparition
        // qui vide les effets cote client sans les renvoyer (le serveur les avait, le client ne les affichait plus)
        ListTag effets = t.getList("effets", Tag.TAG_COMPOUND);
        for (int i = 0; i < effets.size(); i++) {
            MobEffectInstance e = MobEffectInstance.load(effets.getCompound(i));
            if (e != null) p.addEffect(e);
        }
        // sante apres armure, Curios et effets (la sante max peut en dependre)
        p.setAbsorptionAmount(t.getFloat("absorption"));
        float sante = t.getFloat("sante");
        p.setHealth(Math.max(1f, Math.min(sante, p.getMaxHealth())));
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        // marque retiree : enregistree avec l'inventaire rendu ; le fichier n'est efface qu'apres (jamais rendu deux fois)
        marquer(p, 0L);
        if (!curiosOk) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} : Curios non recharges, fichier garde en enquete", p.getGameProfile().getName());
            enquete(f, "curios");
            sauverJoueur(p);
            return false;
        }
        // suppression du fichier APRES la sauvegarde du joueur
        if (sauverJoueur(p)) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException e) {
                HeralizClashWar.LOG.error("[matchs] coffre de {} rendu mais fichier non supprime : {}", p.getGameProfile().getName(), e.toString());
            }
        } else {
            HeralizClashWar.LOG.error("[matchs] coffre de {} rendu mais joueur non sauvegarde : fichier garde (sans sa marque, il ne "
                    + "sera jamais rendu une 2e fois)", p.getGameProfile().getName());
        }
        HeralizClashWar.LOG.info("[matchs] coffre de {} rendu ({})", p.getGameProfile().getName(), t.getString("raison"));
        return true;
    }

    /** Marque d'un depot qui n'existe plus (fichier efface par /wipe, deplace en enquete) : retiree et enregistree. */
    private static void oublierMarque(ServerPlayer p) {
        if (marque(p) == 0L) return;
        HeralizClashWar.LOG.warn("[matchs] {} : marque d'un depot sans fichier retiree", p.getGameProfile().getName());
        marquer(p, 0L);
        sauverJoueur(p);
    }

    /** Curseur et grille 2 x 2 de l'inventaire vides : leurs objets (rendus, a garder dans le depot). */
    private static List<ItemStack> horsInventaire(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        ItemStack curseur = p.containerMenu.getCarried();
        if (!curseur.isEmpty()) {
            l.add(curseur);
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grille.getContainerSize(); i++) {
            ItemStack s = grille.removeItemNoUpdate(i);
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    /**
     * Avant le vidage du retour : les objets a vie de l'inventaire qui ne sont pas des prets du match, et tout ce qui est
     * au curseur ou dans la grille 2 x 2 (les prets du match y sont jetes, comme ceux de l'inventaire). Rendus apres.
     */
    private static List<ItemStack> sauverAvantVidage(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : horsInventaire(p)) {
            if (!Equipement.estPrete(s)) l.add(s);
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !Equipement.estPrete(s) && Tactical.estAVie(s)) {
                l.add(s);
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (!l.isEmpty()) {
            HeralizClashWar.LOG.info("[matchs] {} : {} objet(s) pris a part avant le vidage du retour (rendus apres)",
                    p.getGameProfile().getName(), l.size());
        }
        return l;
    }

    /**
     * Rend des objets pris a part : dans l'inventaire, sinon une case libre de la grille 2 x 2, sinon au curseur (la ou ils
     * etaient), et seulement en tout dernier recours a ses pieds (journal).
     */
    private static void rendreACote(ServerPlayer p, List<ItemStack> objets) {
        if (objets.isEmpty()) return;
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (ItemStack s : objets) {
            if (s.isEmpty()) continue;
            p.getInventory().add(s);
            if (s.isEmpty()) continue;
            boolean pose = false;
            for (int i = 0; i < grille.getContainerSize() && !pose; i++) {
                if (grille.getItem(i).isEmpty()) {
                    grille.setItem(i, s);
                    pose = true;
                }
            }
            if (!pose && p.containerMenu.getCarried().isEmpty()) {
                p.containerMenu.setCarried(s);
                pose = true;
            }
            if (!pose) {
                HeralizClashWar.LOG.warn("[matchs] {} : inventaire, grille et curseur pleins, {} pose a ses pieds",
                        p.getGameProfile().getName(), s.getHoverName().getString());
                p.drop(s, false);
            }
        }
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
    }

    /** Retire le joueur de sa partie, du mode spectateur et efface son HUD de match. */
    private static void detacher(ServerPlayer p) {
        // « reste allonge » des Zombies (a terre au moment d'une deconnexion ou d'un plantage) : jamais garde hors du match
        if (p.getTags().contains(RESTE_ALLONGE)) {
            p.removeTag(RESTE_ALLONGE);
            p.setForcedPose(null);
        }
        Spectateurs.arreter(p, false);
        MatchApi.quitter(p);
        Hud.effacer(p);
    }

    private static void teleporter(ServerPlayer p, CompoundTag t) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        ServerLevel niveau = null;
        ResourceLocation rl = ResourceLocation.tryParse(t.getString("dim"));
        if (rl != null) niveau = s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (niveau == null) {
            niveau = s.overworld();
            var sp = niveau.getSharedSpawnPos();
            p.teleportTo(niveau, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
            return;
        }
        p.teleportTo(niveau, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
    }

    /** Sauvegarde immediate du joueur ({@code PlayerList.save}, protegee : par reflexion SRG m_6765_). */
    public static boolean sauverJoueur(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) return false;
        try {
            if (sauverJoueur == null) sauverJoueur = ObfuscationReflectionHelper.findMethod(PlayerList.class, "m_6765_", ServerPlayer.class);
            sauverJoueur.invoke(s.getPlayerList(), p);
            return true;
        } catch (Exception e) {
            HeralizClashWar.LOG.error("[matchs] sauvegarde de {} impossible", p.getGameProfile().getName(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------ connexion (reprise apres crash)
    /**
     * A la connexion : joueur rattache a une partie active qui le reprend = rien ; sinon depot present = restauration
     * (reprise apres crash, partie finie pendant son absence) ; sinon purge des objets pretes et du drapeau.
     */
    public static void surConnexion(ServerPlayer p) {
        UUID u = p.getUUID();
        Partie partie = MatchApi.partieDe(u);
        if (partie != null && partie.phase() != Partie.Phase.TERMINEE && partie.contient(u)) {
            boolean garde;
            try {
                garde = partie.surReconnexion(p);
            } catch (RuntimeException e) {
                HeralizClashWar.LOG.error("[matchs] surReconnexion de {} en erreur", partie.id(), e);
                garde = false;
            }
            if (garde) {
                MatchApi.rejoindre(p, partie);
                return;
            }
        }
        if (Coffre.aUnDepot(u)) {
            HeralizClashWar.LOG.warn("[matchs] {} : depot non rendu trouve a la connexion, restauration", p.getGameProfile().getName());
            rendre(p);
            return;
        }
        int n = Equipement.retirer(p);
        if (n > 0) HeralizClashWar.LOG.warn("[matchs] {} : {} objet(s) prete(s) purge(s) a la connexion", p.getGameProfile().getName(), n);
        detacher(p);
        oublierMarque(p);
    }

    // ------------------------------------------------------------------ fichiers
    /** Ecrit le depot de facon atomique et verifiee ; rend faux (fichier final intact) en cas d'echec. */
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
            if (!t.equals(relu)) throw new IOException("relecture differente");
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            HeralizClashWar.LOG.error("[matchs] ecriture du coffre {} impossible", f, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // rien
            }
            return false;
        }
    }

    /** Lit un depot, ou null s'il est illisible. */
    @Nullable
    public static CompoundTag lire(Path f) {
        try {
            byte[] b = Files.readAllBytes(f);
            return NbtIo.readCompressed(new ByteArrayInputStream(b));
        } catch (IOException | RuntimeException e) {
            HeralizClashWar.LOG.error("[matchs] lecture du coffre {} impossible", f, e);
            return null;
        }
    }

    /** Depot d'un joueur (lecture seule), ou null. */
    @Nullable
    public static CompoundTag lire(UUID u) {
        Path f = Coffre.fichier(u);
        return f != null && Files.isRegularFile(f) ? lire(f) : null;
    }

    /** Deplace le fichier dans coffres/enquete/ (jamais supprime). */
    private static void enquete(Path f, String motif) {
        try {
            Path d = f.getParent().resolve("enquete");
            Files.createDirectories(d);
            String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Files.move(f, d.resolve(f.getFileName().toString().replace(".dat", "") + "-" + motif + "-" + date + ".dat"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HeralizClashWar.LOG.error("[matchs] coffre {} non deplace en enquete", f, e);
        }
    }

    /** Dimension enregistree dans un depot (journal). */
    public static String dimension(CompoundTag t) {
        return t.getString("dim");
    }

    /** Vrai si la cle de dimension designe un niveau existant. */
    public static boolean niveauExiste(MinecraftServer s, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return false;
        ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, rl);
        return s.getLevel(k) != null;
    }
}
