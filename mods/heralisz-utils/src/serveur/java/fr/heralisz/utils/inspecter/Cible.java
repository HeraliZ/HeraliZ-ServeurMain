package fr.heralisz.utils.inspecter;

import com.mojang.authlib.GameProfile;
import fr.heralisz.utils.HeraliszUtils;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Joueur inspecté par le staff (/inspecter). Connecté : son vrai inventaire, modifié en direct. Déconnecté : sa sauvegarde
 * ({@code playerdata/<uuid>.dat} du monde) chargée dans un joueur « fantôme » jamais ajouté au monde (inventaire, Curios,
 * sac à dos, coffre de l'Ender lus comme à la connexion), réécrite à CHAQUE modification (seulement l'inventaire, le coffre
 * de l'Ender et Curios ; le reste du fichier est gardé tel quel). Ainsi, s'il se connecte, il retrouve tout de suite ce que
 * le staff a changé ; ses vues sont alors fermées (Inspecter), on ne réécrit plus jamais son fichier. Une seule copie par
 * joueur, partagée par tous les membres du staff qui le regardent (pas de copie qui en écraserait une autre).
 */
final class Cible {
    private static final Map<UUID, Cible> OUVERTES = new HashMap<>();

    final MinecraftServer srv;
    final UUID uuid;
    final String nom;
    /** Le joueur (vrai ou fantôme). */
    final ServerPlayer joueur;
    final boolean horsLigne;
    private int menus;
    private boolean casse;

    private Cible(MinecraftServer srv, UUID uuid, String nom, ServerPlayer joueur, boolean horsLigne) {
        this.srv = srv;
        this.uuid = uuid;
        this.nom = nom;
        this.joueur = joueur;
        this.horsLigne = horsLigne;
    }

    static File fichier(MinecraftServer srv, UUID uuid) {
        return srv.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat").toFile();
    }

    /** Le joueur à inspecter, ou null s'il n'est ni connecté ni sauvegardé dans ce monde. */
    static Cible obtenir(MinecraftServer srv, GameProfile profil) {
        UUID id = profil.getId();
        ServerPlayer en = srv.getPlayerList().getPlayer(id);
        Cible c = OUVERTES.get(id);
        if (c != null && c.valide()) {
            return c;
        }
        if (c != null) {
            Inspecter.fermerVues(c, false);
            OUVERTES.remove(id, c);
        }
        if (en != null) {
            c = new Cible(srv, id, en.getGameProfile().getName(), en, false);
        } else {
            File f = fichier(srv, id);
            if (!f.isFile()) {
                return null;
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(f);
                ServerPlayer fantome = new ServerPlayer(srv, srv.overworld(), new GameProfile(id, profil.getName()));
                fantome.load(tag);
                c = new Cible(srv, id, profil.getName(), fantome, true);
            } catch (Exception ex) {
                HeraliszUtils.LOG.error("[inspecter] sauvegarde de {} illisible", profil.getName(), ex);
                return null;
            }
        }
        OUVERTES.put(id, c);
        return c;
    }

    /** Toujours la même situation qu'à l'ouverture (déconnecté toujours déconnecté ; connecté toujours là). */
    boolean valide() {
        ServerPlayer en = this.srv.getPlayerList().getPlayer(this.uuid);
        return !this.casse && (this.horsLigne ? en == null : en == this.joueur && !this.joueur.hasDisconnected());
    }

    void ouvert() {
        this.menus++;
        OUVERTES.putIfAbsent(this.uuid, this);
    }

    void ferme() {
        if (--this.menus <= 0) {
            this.menus = 0;
            OUVERTES.remove(this.uuid, this);
        }
    }

    static Cible ouverte(UUID uuid) {
        return OUVERTES.get(uuid);
    }

    /** Déconnecté : réécrit sa sauvegarde (inventaire, coffre de l'Ender, Curios et sac à dos). Connecté : rien à faire. */
    void sauver() {
        if (!this.horsLigne || this.casse) {
            return;
        }
        if (this.srv.getPlayerList().getPlayer(this.uuid) != null) {
            this.casse = true; // connecté entre-temps : son fichier n'est plus à nous
            return;
        }
        try {
            viderSac(this.joueur);
            CompoundTag neuf = this.joueur.saveWithoutId(new CompoundTag());
            File f = fichier(this.srv, this.uuid);
            CompoundTag tag = NbtIo.readCompressed(f);
            for (String cle : new String[]{"Inventory", "EnderItems"}) {
                if (neuf.contains(cle)) {
                    tag.put(cle, neuf.get(cle));
                }
            }
            CompoundTag capsNeuves = neuf.getCompound("ForgeCaps");
            CompoundTag caps = tag.getCompound("ForgeCaps");
            for (String cle : capsNeuves.getAllKeys()) {
                if (cle.startsWith("curios")) {
                    caps.put(cle, capsNeuves.get(cle));
                }
            }
            if (!caps.isEmpty()) {
                tag.put("ForgeCaps", caps);
            }
            File dossier = f.getParentFile();
            File tmp = File.createTempFile(this.uuid + "-", ".dat", dossier);
            NbtIo.writeCompressed(tag, tmp);
            Util.safeReplaceFile(f, tmp, new File(dossier, this.uuid + ".dat_old"));
        } catch (Exception ex) {
            this.casse = true;
            HeraliszUtils.LOG.error("[inspecter] impossible de réécrire la sauvegarde de {} : vues fermées", this.nom, ex);
        }
    }

    // ------------------------------------------------------------------ sac à dos (HeraliZ Backpack, par réflexion)
    private static Method sac, vider;
    private static boolean sacCherche;

    /** Contenu du sac à dos porté (le vrai, modifié en direct), ou null sans sac. */
    static Container sac(Player p) {
        if (!sacCherche) {
            sacCherche = true;
            try {
                sac = Class.forName("com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess").getMethod("backpacked$GetBackpackInventory");
                vider = Class.forName("com.mrcrayfish.backpacked.inventory.BackpackInventory").getMethod("saveItemsToStack");
            } catch (Throwable t) {
                HeraliszUtils.LOG.warn("[inspecter] HeraliZ Backpack absent : pas de sac à dos ({})", t.toString());
            }
        }
        if (sac == null) {
            return null;
        }
        try {
            return sac.invoke(p) instanceof Container c ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Écrit le contenu du sac dans l'objet sac (avant la sauvegarde du fantôme). */
    private static void viderSac(Player p) {
        Container c = sac(p);
        if (c != null && vider != null) {
            try {
                vider.invoke(c);
            } catch (Throwable ignored) {
            }
        }
    }
}
