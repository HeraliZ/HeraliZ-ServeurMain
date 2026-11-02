package fr.heraliz.crac.bases;

import com.mojang.serialization.Lifecycle;
import fr.heraliz.crac.Crac;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * L'ancien monde des bases ({@code crac:bases} : bases des clans de CRac 1.5.0 à 1.15, puis tutoriel jusqu'au 28/10) est
 * fermé depuis le 02/11 (audit) : il n'est plus créé au démarrage. Les bases sont sur la carte depuis le 21/10 et le
 * tutoriel dans son propre monde ({@code crac:tuto}) depuis le 28/10 ; plus aucun système ne s'en servait.
 * <ul>
 *   <li>Rien n'est effacé : ses fichiers ({@code dimensions/crac/bases} du monde principal : terrain, cabanes d'essai,
 *   claims, coffres) restent tels quels sur le disque, ainsi que ses fichiers de génération dans CRac (type de dimension,
 *   bruits, biomes, lus pour comprendre le level.dat) ; seule sa déclaration ({@code data/crac/dimension/bases.json})
 *   n'est plus livrée, et {@code AncienMondeBasesMixin} empêche Minecraft de le recréer d'après le level.dat.</li>
 *   <li>Pour le rouvrir : {@code python modules/bases/outils/monde_bases.py --remettre} (remet la déclaration à
 *   l'identique), refabriquer CRac et redémarrer : le monde revient avec ses fichiers.</li>
 *   <li>Un joueur déconnecté dans ce monde (tutoriel commencé avant le 28/10) aurait été posé par Minecraft sur la carte
 *   aux mêmes coordonnées : il est ramené au spawn à sa connexion, avec son inventaire, et son tutoriel en cours repart
 *   du début dans le monde du tutoriel (comme avant pour qui revenait de ce monde).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "crac")
public final class AncienMondeBases {
    private static final ResourceKey<LevelStem> DECLARATION = ResourceKey.create(Registries.LEVEL_STEM, MondeBases.ANCIEN);
    /** Joueurs dont la sauvegarde les place dans l'ancien monde (lue à la connexion), en attendant leur arrivée. */
    private static final Set<UUID> REVENANTS = new HashSet<>();

    private AncienMondeBases() {
    }

    /**
     * Les mondes du level.dat sans l'ancien monde des bases, quand plus aucun fichier de données ne le déclare
     * ({@code declares}) ; null : rien à changer (nouveau monde, ou monde rouvert par sa déclaration). Appelé par
     * {@code AncienMondeBasesMixin} au chargement du monde principal.
     */
    @Nullable
    public static WorldDimensions sansAncienMonde(WorldDimensions sauvegardes, Registry<LevelStem> declares) {
        Registry<LevelStem> mondes = sauvegardes.dimensions();
        if (!mondes.containsKey(DECLARATION) || declares.containsKey(DECLARATION)) {
            return null;
        }
        MappedRegistry<LevelStem> sans = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> e : mondes.entrySet()) {
            if (!DECLARATION.equals(e.getKey())) {
                sans.register(e.getKey(), e.getValue(), mondes.lifecycle(e.getValue()));
            }
        }
        Crac.LOGGER.info("[CRac] ancien monde des bases (crac:bases) fermé : pas créé ; ses fichiers restent dans "
            + "dimensions/crac/bases (pour le rouvrir : modules/bases/outils/monde_bases.py --remettre)");
        return new WorldDimensions(sans.freeze());
    }

    /**
     * Sauvegarde du joueur lue (avant sa mise en place) : était-il dans l'ancien monde ? Lecture seule de son fichier
     * {@code <uuid>.dat}. Forge refuse {@code getPlayerFile("dat")} (suffixe réservé : exception qui empêchait TOUTE
     * connexion, vue au banc du 02/11) : le chemin est donc fait à la main. Rien ici ne doit jamais empêcher une
     * connexion : toute erreur est seulement notée.
     */
    @SubscribeEvent
    public static void lecture(PlayerEvent.LoadFromFile e) {
        try {
            if (!(e.getEntity() instanceof ServerPlayer p) || p.server.getLevel(MondeBases.ANCIEN_CLE) != null) {
                return;
            }
            File f = new File(e.getPlayerDirectory(), e.getPlayerUUID() + ".dat");
            if (!f.isFile()) {
                return;
            }
            CompoundTag t = NbtIo.readCompressed(f);
            if (MondeBases.ANCIEN.toString().equals(t.getString("Dimension"))) {
                REVENANTS.add(e.getEntity().getUUID());
            }
        } catch (IOException | RuntimeException ex) {
            Crac.LOGGER.warn("[CRac] sauvegarde de {} illisible pour l'ancien monde des bases", e.getPlayerUUID(), ex);
        }
    }

    /** Arrivée d'un joueur sorti de l'ancien monde : au spawn (le tick suivant, après les autres arrivées). */
    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !REVENANTS.remove(p.getUUID())) {
            return;
        }
        p.server.tell(new TickTask(p.server.getTickCount() + 1, () -> ramener(p)));
    }

    private static void ramener(ServerPlayer p) {
        if (p.isRemoved() || p.hasDisconnected()) {
            return;
        }
        if (!Deplacements.allerAuSpawn(p)) {
            ServerLevel o = p.server.overworld();
            BlockPos s = o.getSharedSpawnPos();
            p.teleportTo(o, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0f);
        }
        T.chat(p, T.t("crac.bases.ancien_ferme", "Le monde où tu étais (l'ancien monde des bases) est fermé : te voilà au spawn, avec ton inventaire."),
            ChatFormatting.YELLOW);
        Crac.LOGGER.info("[CRac] {} sorti de l'ancien monde des bases (fermé) : ramené au spawn", p.getGameProfile().getName());
        // tutoriel commencé dans l'ancien monde : il repart du début, sur une parcelle du monde du tutoriel
        if (Tutoriel.enCours(p)) {
            Tutoriel.lancer(p);
            T.chat(p, T.t("crac.tuto.nouveau_monde", "[Tuto] Le tutoriel a maintenant son propre monde : il repart du début, sur ta parcelle."),
                ChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        REVENANTS.clear();
    }
}
