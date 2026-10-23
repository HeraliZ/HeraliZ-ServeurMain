package fr.heralisz.matchs.moteur.coffre;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.compat.Compat;
import fr.heralisz.matchs.moteur.compat.Revive;
import fr.heralisz.matchs.moteur.compat.Soif;
import fr.heralisz.matchs.moteur.compat.Tactical;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
import java.util.Date;
import java.util.UUID;

/**
 * Implementation du {@link Coffre} (MATCHS_SPEC §1.1) : instantane complet du joueur sur disque, vidage, restauration,
 * reprise apres crash. Format du fichier {@code <serveur>/heralisz_matchs/coffres/<uuid>.dat} (NbtIo compresse) :
 * <pre>
 * version, raison, date (ms), uuid, nom,
 * inventaire (Inventory.save), selection, curios (saveInventory(false), absent sans Curios),
 * sante, absorption, faim (FoodData), xpNiveau, xpProgres, xpTotal, effets (MobEffectInstance.save), mode (GameType),
 * dim, x, y, z, yaw, pitch, soif (IThirst.serializeNBT, absent sans le mod), brut (saveWithoutId, pour enquete)
 * </pre>
 * Ecriture : octets prepares en memoire, ecrits dans {@code <uuid>.dat.tmp}, synchronises sur le disque (fsync), relus
 * et compares, puis deplaces par ATOMIC_MOVE. Un depot illisible ou une restauration incomplete deplace le fichier dans
 * {@code coffres/enquete/} (jamais supprime). Fil serveur.
 */
public final class Coffres {
    private Coffres() {
    }

    public static final int VERSION = 1;
    /** Etiquette de joueur « reste allonge » (lue par Reanimation, posee par les Zombies a terre). */
    private static final String RESTE_ALLONGE = "heraliz_reste_allonge";
    private static Method sauverJoueur;

    // ------------------------------------------------------------------ refus
    /**
     * Voir {@link Coffre#refus}. Un passager de vehicule n'est plus refuse (RETOURS du 01/10 §0) : {@link #deposer} le
     * fait descendre ({@link Descente#forcer}) ; {@link Coffre.Refus#VEHICULE} ne sert plus que si cette descente echoue.
     */
    @Nullable
    public static Coffre.Refus refus(ServerPlayer p) {
        if (p.isDeadOrDying() || p.isRemoved()) return Coffre.Refus.MORT;
        if (CombatTag.estTague(p)) return Coffre.Refus.COMBAT;
        if (Revive.aTerre(p)) return Coffre.Refus.A_TERRE;
        if (Coffre.aUnDepot(p.getUUID())) return Coffre.Refus.DEPOT_EXISTANT;
        if (Coffre.dossier() == null) return Coffre.Refus.ERREUR;
        return null;
    }

    // ------------------------------------------------------------------ depot
    /** Voir {@link Coffre#deposer}. */
    public static boolean deposer(ServerPlayer p, String raison) {
        Coffre.Refus r = refus(p);
        if (r != null) {
            HeraliszMatchs.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, r);
            return false;
        }
        // 0. passager d'un vehicule (train compris) : il descend, et l'instantane prend SA position, pas celle du vehicule
        if (!Descente.forcer(p)) {
            HeraliszMatchs.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, Coffre.Refus.VEHICULE);
            return false;
        }
        // 1. fermer le conteneur ouvert (le contenu du menu revient dans l'inventaire ou tombe)
        p.closeContainer();
        // 2. forcer l'ecriture du sac (mixin addAdditionalSaveData de backpacked) : la copie brute sert aussi d'enquete
        CompoundTag brut = p.saveWithoutId(new CompoundTag());
        // 3. instantane
        CompoundTag t = instantane(p, raison, brut);
        // 4. disque (atomique, verifie) AVANT toute modification du joueur
        Path f = Coffre.fichier(p.getUUID());
        if (f == null || !ecrire(f, t)) {
            HeraliszMatchs.LOG.error("[matchs] coffre de {} NON ecrit : joueur laisse intact", p.getGameProfile().getName());
            return false;
        }
        // 5. seulement maintenant : vidage (malus d'ArkaniaZ compris : tous les effets partent)
        vider(p);
        CombatTag.effacer(p.getUUID());
        Hud.combat(p, 0);
        HeraliszMatchs.LOG.info("[matchs] coffre de {} depose ({}) : {} piles, curios {}, soif {}", p.getGameProfile().getName(), raison,
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
        CompoundTag soif = Soif.sauver(p);
        if (soif != null) t.put("soif", soif);
        if (brut != null) t.put("brut", brut);
        return t;
    }

    /** Vidage apres depot : inventaire, Curios, effets, soif pleine et figee, recharges tactiques, sante et faim pleines. */
    private static void vider(ServerPlayer p) {
        p.getInventory().clearContent();
        Compat.viderCurios(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.setAbsorptionAmount(0);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.getFoodData().setExhaustion(0f);
        Soif.pleineEtFigee(p);
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
            // rien a rendre : on retire quand meme tout objet prete et le drapeau
            Equipement.retirer(p);
            detacher(p);
            return false;
        }
        CompoundTag t = lire(f);
        if (t == null) {
            HeraliszMatchs.LOG.error("[matchs] coffre de {} illisible : deplace en enquete, rien rendu", p.getGameProfile().getName());
            enquete(f, "illisible");
            return false;
        }
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
        // soif apres la teleportation aussi (donnees du client renvoyees sur le nouveau joueur client)
        if (t.contains("soif")) Soif.restaurer(p, t.getCompound("soif"));
        // sante apres armure, Curios et effets (la sante max peut en dependre)
        p.setAbsorptionAmount(t.getFloat("absorption"));
        float sante = t.getFloat("sante");
        p.setHealth(Math.max(1f, Math.min(sante, p.getMaxHealth())));
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        if (!curiosOk) {
            HeraliszMatchs.LOG.error("[matchs] coffre de {} : Curios non recharges, fichier garde en enquete", p.getGameProfile().getName());
            enquete(f, "curios");
            return false;
        }
        // suppression du fichier APRES la sauvegarde du joueur
        if (sauverJoueur(p)) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException e) {
                HeraliszMatchs.LOG.error("[matchs] coffre de {} rendu mais fichier non supprime : {}", p.getGameProfile().getName(), e.toString());
            }
        } else {
            HeraliszMatchs.LOG.error("[matchs] coffre de {} rendu mais joueur non sauvegarde : fichier garde", p.getGameProfile().getName());
        }
        HeraliszMatchs.LOG.info("[matchs] coffre de {} rendu ({})", p.getGameProfile().getName(), t.getString("raison"));
        return true;
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
            HeraliszMatchs.LOG.error("[matchs] sauvegarde de {} impossible", p.getGameProfile().getName(), e);
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
                HeraliszMatchs.LOG.error("[matchs] surReconnexion de {} en erreur", partie.id(), e);
                garde = false;
            }
            if (garde) {
                MatchApi.rejoindre(p, partie);
                return;
            }
        }
        if (Coffre.aUnDepot(u)) {
            HeraliszMatchs.LOG.warn("[matchs] {} : depot non rendu trouve a la connexion, restauration", p.getGameProfile().getName());
            rendre(p);
            return;
        }
        int n = Equipement.retirer(p);
        if (n > 0) HeraliszMatchs.LOG.warn("[matchs] {} : {} objet(s) prete(s) purge(s) a la connexion", p.getGameProfile().getName(), n);
        detacher(p);
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
            HeraliszMatchs.LOG.error("[matchs] ecriture du coffre {} impossible", f, e);
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
            HeraliszMatchs.LOG.error("[matchs] lecture du coffre {} impossible", f, e);
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
            HeraliszMatchs.LOG.error("[matchs] coffre {} non deplace en enquete", f, e);
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
