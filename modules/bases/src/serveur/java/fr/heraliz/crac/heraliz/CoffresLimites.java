package fr.heraliz.crac.heraliz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.CoffreBlock;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.PlacementSync;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.registry.ModBlocks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Limite de coffres par base et par rareté (27/10, demande du propriétaire) : caisse en métal (Commune), malle de chantier
 * (Peu commune), casier métallique (Rare), caisse d'armes renforcée (Épique), coffre-fort (Légendaire). Au-delà, la pose est
 * refusée avec un message ; les coffres déjà posés comptent (ceux des tronçons chargés : une base est chargée autour du
 * joueur qui y pose). Libres : créatif, hors d'une base. Limites dans {@code config/crac/coffres.json} (fichier du dépôt qui
 * fait foi, écrit s'il manque), relu par {@code /coffres recharger}.
 */
@EventBusSubscriber(modid = "crac")
public final class CoffresLimites {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Raretés dans l'ordre, et limites par défaut (celles validées par le propriétaire). */
    private static final String[] RARETES = {"commune", "peu_commune", "rare", "epique", "legendaire"};
    private static final int[] DEFAUT = {14, 12, 10, 8, 5};
    private static final Map<String, Integer> LIMITES = new LinkedHashMap<>();

    private CoffresLimites() {
    }

    /** Rareté d'un coffre de base, null pour tout autre bloc (et pour l'autre case d'un coffre de deux cases). */
    @Nullable
    public static String rarete(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof MetalCrateBlock) {
            return "commune";
        }
        if (!(b instanceof CoffreBlock) || s.getValue(CoffreBlock.ANNEXE)) {
            return null;
        }
        if (b == ModBlocks.MALLE_CHANTIER.get()) {
            return "peu_commune";
        }
        if (b == ModBlocks.CASIER_METALLIQUE.get()) {
            return "rare";
        }
        if (b == ModBlocks.CAISSE_ARMES.get()) {
            return "epique";
        }
        return b == ModBlocks.COFFRE_FORT.get() ? "legendaire" : null;
    }

    public static int limite(String rarete) {
        return LIMITES.getOrDefault(rarete, Integer.MAX_VALUE);
    }

    // ---------------------------------------------------------------------------------------------- pose

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pose(EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel sl) || p.getAbilities().instabuild) {
            return;
        }
        BlockState s = e.getPlacedBlock();
        String r = rarete(s);
        if (r == null) {
            return;
        }
        Claim claim = ClaimManager.getAt(sl, e.getPos());
        if (claim == null) {
            return;
        }
        int limite = limite(r);
        if (compter(sl, claim, e.getPos())[indice(r)] < limite) {
            return;
        }
        e.setCanceled(true);
        p.displayClientMessage(Component.translatableWithFallback("crac.coffre.limite",
            "Cette base a déjà %2$s × « %1$s » : c'est la limite.", s.getBlock().getName(), limite).withStyle(ChatFormatting.RED), true);
        for (BlockPos cell : PlacableHpManager.cells(e)) {
            PlacementSync.revert(p, sl, cell);
        }
    }

    private static int indice(String r) {
        for (int i = 0; i < RARETES.length; i++) {
            if (RARETES[i].equals(r)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Coffres de la base, par rareté (ordre de {@link #RARETES}), sans compter la case {@code sauf} (celui qu'on pose) :
     * les inventaires des tronçons chargés de la zone de l'armoire (une case principale par coffre).
     */
    static int[] compter(ServerLevel sl, Claim claim, @Nullable BlockPos sauf) {
        int[] n = new int[RARETES.length];
        BlockPos a = claim.anchor();
        int r = claim.radius();
        for (int cx = (a.getX() - r) >> 4; cx <= (a.getX() + r) >> 4; cx++) {
            for (int cz = (a.getZ() - r) >> 4; cz <= (a.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos q = be.getBlockPos();
                    if (q.equals(sauf) || !claim.contains(q)) {
                        continue;
                    }
                    String rr = rarete(be.getBlockState());
                    if (rr != null) {
                        n[indice(rr)]++;
                    }
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------------------------------------- fichier

    @SubscribeEvent
    public static void demarrage(ServerAboutToStartEvent e) {
        charger();
    }

    static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("crac").resolve("coffres.json");
    }

    static void charger() {
        LIMITES.clear();
        for (int i = 0; i < RARETES.length; i++) {
            LIMITES.put(RARETES[i], DEFAUT[i]);
        }
        Path f = fichier();
        try {
            if (!Files.exists(f)) {
                JsonObject o = new JsonObject();
                JsonObject l = new JsonObject();
                LIMITES.forEach(l::addProperty);
                o.add("limites_par_base", l);
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
                Crac.LOGGER.info("[CRac] coffres.json écrit avec les limites par défaut");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject l = o.getAsJsonObject("limites_par_base");
            if (l != null) {
                for (String r : RARETES) {
                    if (l.has(r)) {
                        LIMITES.put(r, Math.max(0, l.get(r).getAsInt()));
                    }
                }
            }
            Crac.LOGGER.info("[CRac] Coffres par base : {}", LIMITES);
        } catch (IOException | RuntimeException ex) {
            Crac.LOGGER.error("[CRac] coffres.json illisible : limites par défaut", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------- commandes

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("coffres").requires(s -> s.hasPermission(4))
            .then(Commands.literal("voir").executes(c -> voir(c.getSource())))
            .then(Commands.literal("recharger").executes(c -> {
                charger();
                c.getSource().sendSuccess(() -> Component.translatableWithFallback("crac.coffres.recharge",
                    "coffres.json relu : %s", ligne(null)), false);
                return 1;
            })));
    }

    /** Coffres de la base où se tient le staff, et ses limites. */
    private static int voir(CommandSourceStack src) {
        BlockPos pos = BlockPos.containing(src.getPosition());
        Claim claim = ClaimManager.getAt(src.getLevel(), pos);
        if (claim == null) {
            src.sendFailure(Component.translatableWithFallback("crac.coffres.hors_base", "Pas de base ici."));
            return 0;
        }
        src.sendSuccess(() -> Component.translatableWithFallback("crac.coffres.voir", "Coffres de cette base : %s",
            ligne(compter(src.getLevel(), claim, null))), false);
        return 1;
    }

    /** « Commune 3/14 · Peu commune 0/12 ... » (sans compte : les limites seules). */
    private static MutableComponent ligne(@Nullable int[] n) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < RARETES.length; i++) {
            if (i > 0) {
                out.append(Component.translatableWithFallback("crac.coffres.separateur", " · "));
            }
            String chiffres = n == null ? String.valueOf(limite(RARETES[i])) : n[i] + "/" + limite(RARETES[i]);
            out.append(Component.translatableWithFallback("crac.coffres.rarete." + RARETES[i], RARETES[i], chiffres));
        }
        return out;
    }
}
