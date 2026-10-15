package fr.heraliz.loot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Conteneurs de Minecraft scellés (choix du propriétaire) : un joueur en survie ou en aventure n'ouvre ni coffre, ni
 * baril, ni four, ni alambic, ni entonnoir, ni distributeur, ni boîte de Shulker... (section {@code [conteneurs_vanilla]}
 * de {@code heraliz_loot-server.toml}, {@link ConfigLoot}). Le loot est dans les conteneurs de HeraliZ Loot. Libres :
 * créatif, spectateur, staff (permission 2). Seulement le namespace {@code minecraft} : jamais nos blocs ni ceux de cfm.
 * Seule l'ouverture par un joueur est refusée (entonnoirs et distributeurs marchent toujours entre eux). Coffre de l'Ender
 * permis par défaut ({@code coffre_ender}). Wagonnets à coffre ou à entonnoir et coffre des bateaux à coffre : refusés
 * aussi ({@code entites}), on peut toujours monter dans le bateau. Dans un claim de CRac (rangements des bases posés par
 * les joueurs) : permis par défaut ({@code permis_claims_crac}, claims lus par réflexion, CRac facultatif).
 */
final class ConteneursVanilla {
    private ConteneursVanilla() {
    }

    private static final String MINECRAFT = "minecraft";
    private static volatile Regles regles;

    record Regles(Set<Block> blocs, List<TagKey<Block>> tags, Set<Block> sauf, List<TagKey<Block>> tagsSauf) {
        boolean dans(BlockState st, Set<Block> b, List<TagKey<Block>> t) {
            if (b.contains(st.getBlock())) return true;
            for (TagKey<Block> k : t) if (st.is(k)) return true;
            return false;
        }
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ConteneursVanilla::bloc);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ConteneursVanilla::entite);
    }

    /** La configuration a changé : listes relues au prochain clic. */
    static void invalider() {
        regles = null;
    }

    private static boolean actif() {
        return ConfigLoot.SPEC.isLoaded() && ConfigLoot.VANILLA_ACTIF.get();
    }

    private static boolean libre(ServerPlayer sp) {
        return sp.isCreative() || sp.isSpectator() || sp.hasPermissions(2);
    }

    private static void bloc(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp) || !actif() || libre(sp)) return;
        BlockState st = e.getLevel().getBlockState(e.getPos());
        if (!scelle(st, e.getLevel().getBlockEntity(e.getPos()))) return;
        if (ConfigLoot.VANILLA_CLAIMS.get() && e.getLevel() instanceof ServerLevel sl && dansClaimCrac(sl, e.getPos())) return;
        boolean objet = !sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty();
        if (sp.isSecondaryUseActive() && objet) {
            // accroupi avec un objet : Minecraft n'ouvre pas, l'objet sert (poser un bloc contre le coffre...) ;
            // le bloc reste refusé au cas où l'objet laisserait passer l'ouverture
            e.setUseBlock(Event.Result.DENY);
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp);
    }

    /** Clic sur une entité (wagonnet, bateau) : EntityInteractSpecific passe toujours par ici ensuite s'il ne fait rien. */
    private static void entite(PlayerInteractEvent.EntityInteract e) {
        if (refuserEntite(e.getTarget(), e)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean refuserEntite(Entity cible, PlayerInteractEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer sp) || !actif() || libre(sp)) return false;
        if (!ConfigLoot.VANILLA_ENTITES.get() || !(cible instanceof ContainerEntity)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(cible.getType());
        if (!MINECRAFT.equals(id.getNamespace())) return false;
        // bateau à coffre : clic simple = monter, accroupi = ouvrir le coffre ; wagonnet : le clic ouvre toujours
        if (!(cible instanceof AbstractMinecartContainer) && !sp.isSecondaryUseActive()) return false;
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp);
        return true;
    }

    /** Ce bloc de Minecraft est-il scellé ? */
    static boolean scelle(BlockState st, BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        if (!MINECRAFT.equals(id.getNamespace())) return false;
        Regles r = regles();
        if (r.dans(st, r.sauf(), r.tagsSauf())) return false;
        if (st.is(Blocks.ENDER_CHEST)) return ConfigLoot.VANILLA_ENDER.get();
        if (r.dans(st, r.blocs(), r.tags())) return true;
        return ConfigLoot.VANILLA_AUTO.get() && be instanceof Container;
    }

    // ------------------------------------------------------------------ CRac (facultatif, par réflexion)
    private static final String CRAC_CLAIMS = "fr.heraliz.crac.claim.ClaimManager";
    private static boolean cracCherche;
    private static Method cracClaimA;

    /** Un claim de CRac couvre-t-il cet endroit ? (CRac absent ou changé : non, avec un avertissement une seule fois) */
    private static boolean dansClaimCrac(ServerLevel l, BlockPos pos) {
        if (!cracCherche) {
            cracCherche = true;
            if (ModList.get().isLoaded("crac")) {
                try {
                    cracClaimA = Class.forName(CRAC_CLAIMS).getMethod("getAt", ServerLevel.class, BlockPos.class);
                } catch (ReflectiveOperationException | LinkageError ex) {
                    HeralizLoot.LOG.warn("[conteneurs_vanilla] {}.getAt introuvable : conteneurs scellés aussi dans les claims", CRAC_CLAIMS);
                }
            }
        }
        Method m = cracClaimA;
        if (m == null) return false;
        try {
            return m.invoke(null, l, pos) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeralizLoot.LOG.warn("[conteneurs_vanilla] claims de CRac illisibles : conteneurs scellés aussi dans les claims ({})", ex.toString());
            cracClaimA = null;
            return false;
        }
    }

    private static void prevenir(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatableWithFallback("heraliz_loot.vanilla.scelle",
                "Ce conteneur est vide et scellé.").withStyle(ChatFormatting.GRAY), true);
    }

    private static Regles regles() {
        Regles r = regles;
        if (r == null) {
            Set<Block> b = new HashSet<>(), s = new HashSet<>();
            List<TagKey<Block>> t = new ArrayList<>(), ts = new ArrayList<>();
            lire(ConfigLoot.VANILLA_BLOQUES.get(), b, t);
            lire(ConfigLoot.VANILLA_EXCEPTIONS.get(), s, ts);
            regles = r = new Regles(b, t, s, ts);
        }
        return r;
    }

    private static void lire(List<? extends String> liste, Set<Block> blocs, List<TagKey<Block>> tags) {
        for (String brut : liste) {
            String x = brut.trim();
            boolean tag = x.startsWith("#");
            ResourceLocation rl = ResourceLocation.tryParse(tag ? x.substring(1) : x);
            if (rl == null) {
                HeralizLoot.LOG.warn("[conteneurs_vanilla] identifiant illisible : {}", brut);
            } else if (tag) {
                tags.add(TagKey.create(Registries.BLOCK, rl));
            } else if (BuiltInRegistries.BLOCK.containsKey(rl)) {
                blocs.add(BuiltInRegistries.BLOCK.get(rl));
            } else {
                HeralizLoot.LOG.warn("[conteneurs_vanilla] bloc inconnu : {}", brut);
            }
        }
    }
}
