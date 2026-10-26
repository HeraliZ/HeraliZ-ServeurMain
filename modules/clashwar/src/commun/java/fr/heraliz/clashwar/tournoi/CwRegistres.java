package fr.heraliz.clashwar.tournoi;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.tournoi.bloc.TableauBlocEntite;
import fr.heraliz.clashwar.tournoi.bloc.TableauBloc;
import fr.heraliz.clashwar.tournoi.bloc.TableauBordBloc;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registres du ClashWar : mur « tableau du tournoi » (bloc maitre + bords + bloc-entite), son objet, les sons. */
public final class CwRegistres {
    private CwRegistres() {
    }

    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeralizClashWar.MODID);
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizClashWar.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeralizClashWar.MODID);

    private static BlockBehaviour.Properties proprietes() {
        // incassable en survie (decor d'administration), cassable en creatif ; leger halo
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3_600_000.0F).noLootTable()
                .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 6).pushReaction(PushReaction.BLOCK);
    }

    public static final RegistryObject<TableauBloc> TABLEAU = BLOCS.register("cw_tableau", () -> new TableauBloc(proprietes()));
    public static final RegistryObject<TableauBordBloc> TABLEAU_BORD = BLOCS.register("cw_tableau_bord", () -> new TableauBordBloc(proprietes()));
    public static final RegistryObject<Item> TABLEAU_OBJET = OBJETS.register("cw_tableau",
            () -> new BlockItem(TABLEAU.get(), new Item.Properties().rarity(Rarity.EPIC)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TableauBlocEntite>> TABLEAU_BE = BE.register("cw_tableau",
            () -> BlockEntityType.Builder.of(TableauBlocEntite::new, TABLEAU.get()).build(null));

    static void enregistrer(IEventBus modBus) {
        BLOCS.register(modBus);
        OBJETS.register(modBus);
        BE.register(modBus);
        CwSons.SONS.register(modBus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(CwRegistres::anciensIds);
    }

    /**
     * 25/10 (audit) : le tableau du tournoi s'appelait {@code heralisz_matchs:cw_tableau} dans l'ancien mod des matchs.
     * Un tableau posé avec l'ancien mod (panneau du classement au spawn) reprend ce bloc au lieu de disparaître.
     */
    private static void anciensIds(net.minecraftforge.registries.MissingMappingsEvent e) {
        for (var m : e.getMappings(net.minecraft.core.registries.Registries.BLOCK, "heralisz_matchs")) {
            String p = m.getKey().getPath();
            if (p.equals("cw_tableau")) m.remap(TABLEAU.get());
            else if (p.equals("cw_tableau_bord")) m.remap(TABLEAU_BORD.get());
        }
        for (var m : e.getMappings(net.minecraft.core.registries.Registries.ITEM, "heralisz_matchs")) {
            if (m.getKey().getPath().equals("cw_tableau")) m.remap(TABLEAU_OBJET.get());
        }
    }
}
