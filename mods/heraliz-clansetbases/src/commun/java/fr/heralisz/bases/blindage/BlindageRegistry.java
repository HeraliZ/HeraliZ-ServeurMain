package fr.heralisz.bases.blindage;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Registres du blindage (BASES_SPEC §5) : blocs et objets {@code heralisz_bases:blindage_1..8}, leurs 4 sons maison
 * (tools/bases/gen_sons.py) (l'ancien onglet « HeraliZ Blindage » est devenu le groupe Bases de l'onglet commun). Registres propres au module (meme espace de noms que
 * le coeur, DeferredRegister distincts).
 */
public final class BlindageRegistry {
    private BlindageRegistry() {
    }

    public static final int NIVEAUX = 8;

    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszBases.MODID);
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszBases.MODID);
    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszBases.MODID);

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszBases.id(id)));
    }

    /** Pas sur un blindage. */
    public static final RegistryObject<SoundEvent> PAS = son("blindage_pas");
    /** Pose, coup, casse a la pioche. */
    public static final RegistryObject<SoundEvent> COUP = son("blindage_coup");
    /** Coup de C4 encaisse (fissures). */
    public static final RegistryObject<SoundEvent> FISSURE = son("blindage_fissure");
    /** Rupture au dernier coup. */
    public static final RegistryObject<SoundEvent> RUPTURE = son("blindage_rupture");

    /** Sons de bloc du blindage (aucun son vanilla). */
    public static final ForgeSoundType SON_BLOC = new ForgeSoundType(1.0f, 1.0f, COUP, PAS, COUP, COUP, PAS);

    /** Durete (pioche) par niveau : les membres retirent leur blindage, lentement aux hauts niveaux. */
    private static final float[] DURETE = {6f, 9f, 12f, 16f, 20f, 26f, 34f, 45f};

    /** Blocs par niveau : index 0 = niveau 1. */
    public static final List<RegistryObject<Block>> BLINDAGES = new ArrayList<>();
    public static final List<RegistryObject<Item>> OBJETS_BLINDAGE = new ArrayList<>();

    static {
        for (int n = 1; n <= NIVEAUX; n++) {
            final int niveau = n;
            String id = "blindage_" + n;
            RegistryObject<Block> b = BLOCS.register(id, () -> new BlindageBlock(niveau, BlockBehaviour.Properties.of()
                    .mapColor(niveau <= 4 ? MapColor.STONE : MapColor.METAL)
                    .strength(DURETE[niveau - 1], 1200f)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SON_BLOC)));
            BLINDAGES.add(b);
            OBJETS_BLINDAGE.add(OBJETS.register(id, () -> new BlockItem(b.get(),
                    new Item.Properties().rarity(niveau >= 7 ? Rarity.EPIC : niveau >= 5 ? Rarity.RARE : niveau >= 3 ? Rarity.UNCOMMON : Rarity.COMMON))));
        }
    }

    /** Bloc de blindage d'un niveau (1..8). */
    public static Block bloc(int niveau) {
        return BLINDAGES.get(Math.max(1, Math.min(NIVEAUX, niveau)) - 1).get();
    }

    public static void register(IEventBus modBus) {
        BLOCS.register(modBus);
        OBJETS.register(modBus);
        SONS.register(modBus);
    }
}
