package fr.heraliz.crac.placable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CPlacableSet;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class PlacableSet {
    /** Étiquette des PV gardés dans l'objet d'un placable abîmé ; reprise par PlacableHpManager (serveur). */
    public static final String ITEM_HP_TAG = "CracHp";
    /** HeraliZ (11/11) : refus d'un bloc que plus personne ne pose ({@link #poseInterdite}). */
    public static final String POSE_INTERDITE = "crac.placable.interdit";

    private static final Set<Block> SERVER = new HashSet<>();
    private static final Set<Block> CLIENT = Collections.synchronizedSet(new HashSet<>());
    /** Liste {@code placables.forbiddenBlocks} lue, refaite seulement quand le réglage change. */
    @Nullable
    private static volatile Interdits interdits;

    private PlacableSet() {
    }

    public static void setServerSide(Collection<Block> blocks) {
        SERVER.clear();
        SERVER.addAll(blocks);
    }

    public static Collection<Block> serverSide() {
        return Collections.unmodifiableSet(SERVER);
    }

    public static void syncTo(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), S2CPlacableSet.of(SERVER));
    }

    public static void syncToAll(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                syncTo(p);
            }
        }
    }

    public static void setClientSide(Collection<Block> blocks) {
        CLIENT.clear();
        CLIENT.addAll(blocks);
    }

    public static boolean containsClient(Block block) {
        return CLIENT.contains(block);
    }

    public static boolean contains(Level level, Block block) {
        return level != null && level.isClientSide() ? CLIENT.contains(block) : SERVER.contains(block);
    }

    public static boolean canPlace(Level level, Block block) {
        return isClaimAnchor(block) || contains(level, block);
    }

    public static boolean canPlaceClient(Block block) {
        return isClaimAnchor(block) || CLIENT.contains(block);
    }

    public static boolean isClaimAnchor(Block block) {
        return block == ModBlocks.CLAIM_BLOCK.get();
    }

    /**
     * HeraliZ (11/11, demande du propriétaire : « supprimer le fait de pouvoir placer des coffres non moddés dans les
     * bases ») : un bloc que plus aucun joueur ne pose, nulle part (le créatif excepté) : les coffres et autres rangements
     * de Minecraft, réglage {@code placables.forbiddenBlocks} de crac-server.toml (envoyé aux joueurs : le fantôme de pose
     * le dit aussi). Ceux déjà posés restent des objets posés comme avant (PV, raids, ramassage). Les coffres de base
     * (caisse en métal, coffres par rareté) ne sont pas dans la liste.
     */
    public static boolean poseInterdite(BlockState state) {
        if (!Config.SPEC.isLoaded()) {
            return false;
        }
        List<? extends String> liste = Config.forbiddenBlocks.get();
        Interdits i = interdits;
        if (i == null || !i.source().equals(liste)) {
            i = Interdits.lire(liste);
            interdits = i;
        }
        return i.contient(state);
    }

    /** Les blocs et les étiquettes ({@code #modid:nom}) de la liste des blocs interdits. */
    private record Interdits(List<String> source, Set<Block> blocs, List<TagKey<Block>> etiquettes) {
        static Interdits lire(List<? extends String> liste) {
            Set<Block> blocs = new HashSet<>();
            List<TagKey<Block>> etiquettes = new ArrayList<>();
            for (String s : liste) {
                boolean etiquette = s.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(etiquette ? s.substring(1) : s);
                if (id == null) {
                    continue;
                }
                if (etiquette) {
                    etiquettes.add(BlockTags.create(id));
                } else if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    blocs.add(ForgeRegistries.BLOCKS.getValue(id));
                }
            }
            return new Interdits(List.copyOf(liste), Set.copyOf(blocs), List.copyOf(etiquettes));
        }

        boolean contient(BlockState state) {
            if (this.blocs.contains(state.getBlock())) {
                return true;
            }
            for (TagKey<Block> t : this.etiquettes) {
                if (state.is(t)) {
                    return true;
                }
            }
            return false;
        }
    }
}
