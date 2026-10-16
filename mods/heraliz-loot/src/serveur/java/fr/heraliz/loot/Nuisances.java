package fr.heraliz.loot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Autres façons d'abîmer la carte (Loot 0.9.0, section {@code [nuisances]} de {@code heraliz_loot-server.toml}), refusées à
 * un joueur en survie ou en aventure (« Impossible ici. ») ; libres : créatif, spectateur, staff (permission 2) ; permis
 * dans un claim de CRac par défaut ({@code permis_claims_crac} : les règles de CRac décident ensuite). CRac laisse tout
 * faire hors des claims ({@code protectOutsideClaims = false}) : c'est ici que la carte est gardée.
 * <ul>
 *   <li>{@code seaux} : poser ou ramasser eau, lave, neige poudreuse, poisson en seau ({@link FillBucketEvent} ; le seau de
 *   neige poudreuse est un bloc : clic sur un bloc) ;</li>
 *   <li>{@code feu} : briquet et boule de feu sur un bloc (feu, bougie, feu de camp, TNT) ;</li>
 *   <li>{@code outils} : écorcer, gratter ou décirer le cuivre, chemin, labour ({@link BlockEvent.BlockToolModificationEvent}),
 *   cirer au rayon de miel ;</li>
 *   <li>{@code engrais} : poudre d'os ({@link BonemealEvent}) ;</li>
 *   <li>{@code panneaux} : écrire sur un panneau posé, le teindre, le faire briller, le cirer (tout clic sur un panneau non
 *   ciré ; un panneau ciré par le staff garde ses commandes au clic) et poser un panneau.</li>
 * </ul>
 * Briser un bloc : CRac ({@code CasseBlocs}). Présentoirs : {@link Presentoirs}. Postes de Minecraft : {@link ConteneursVanilla}.
 */
final class Nuisances {
    private Nuisances() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Nuisances::clicBloc);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Nuisances::seau);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Nuisances::outil);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Nuisances::engrais);
    }

    private static boolean actif() {
        return ConfigLoot.SPEC.isLoaded() && ConfigLoot.NUIS_ACTIF.get();
    }

    /** Joueur concerné par ces règles (serveur, survie ou aventure, hors staff). */
    private static ServerPlayer concerne(Player p) {
        return p instanceof ServerPlayer sp && actif() && !ConteneursVanilla.libre(sp) ? sp : null;
    }

    /** Permis à ces endroits : tous dans un claim de CRac, et le réglage le permet. */
    private static boolean permis(ServerLevel sl, BlockPos... lieux) {
        if (!ConfigLoot.NUIS_CLAIMS.get()) return false;
        for (BlockPos p : lieux) if (!ConteneursVanilla.dansClaimCrac(sl, p)) return false;
        return true;
    }

    private static void clicBloc(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        ServerPlayer sp = concerne(e.getEntity());
        if (sp == null) return;
        BlockPos pos = e.getPos();
        BlockState st = sl.getBlockState(pos);
        // panneau posé : écrire, teindre, encre lumineuse, cire (SignBlock.use) ; un panneau ciré ne change plus
        if (ConfigLoot.NUIS_PANNEAUX.get() && st.getBlock() instanceof SignBlock
                && !(sl.getBlockEntity(pos) instanceof SignBlockEntity panneau && panneau.isWaxed()) && !permis(sl, pos)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp, true);
            return;
        }
        Item it = e.getItemStack().getItem();
        BlockPos dest = e.getFace() == null ? pos : pos.relative(e.getFace());
        boolean feu = ConfigLoot.NUIS_FEU.get() && (it instanceof FlintAndSteelItem || it instanceof FireChargeItem);
        boolean refus = feu
                || ConfigLoot.NUIS_OUTILS.get() && it instanceof HoneycombItem
                || ConfigLoot.NUIS_SEAUX.get() && it == Items.POWDER_SNOW_BUCKET
                || ConfigLoot.NUIS_PANNEAUX.get() && (it instanceof SignItem || it instanceof HangingSignItem);
        if (!refus || permis(sl, pos, dest)) return;
        if (feu) {
            // la TNT s'allume dans son propre clic (TntBlock.use) : rien du tout ne passe
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        } else {
            e.setUseItem(Event.Result.DENY);
        }
        sp.containerMenu.broadcastFullState();
        if (e.getHand() == InteractionHand.MAIN_HAND) prevenir(sp, false);
    }

    /** Seaux : le fluide est posé (ou ramassé) par un second paquet « utiliser l'objet », refusé ici. */
    private static void seau(FillBucketEvent e) {
        if (!ConfigLoot.SPEC.isLoaded() || !ConfigLoot.NUIS_SEAUX.get() || !(e.getLevel() instanceof ServerLevel sl)) return;
        ServerPlayer sp = concerne(e.getEntity());
        if (sp == null || !(e.getTarget() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos clic = hit.getBlockPos();
        BlockPos dest = clic.relative(hit.getDirection());
        if (permis(sl, clic, dest)) return;
        e.setCanceled(true);
        sp.connection.send(new ClientboundBlockUpdatePacket(sl, clic));
        sp.connection.send(new ClientboundBlockUpdatePacket(sl, dest));
        sp.containerMenu.broadcastFullState();
        prevenir(sp, false);
    }

    /** Écorcer, gratter, décirer, chemin, labour. */
    private static void outil(BlockEvent.BlockToolModificationEvent e) {
        if (!ConfigLoot.SPEC.isLoaded() || !ConfigLoot.NUIS_OUTILS.get() || !(e.getLevel() instanceof ServerLevel sl)) return;
        ServerPlayer sp = concerne(e.getPlayer());
        if (sp == null || permis(sl, e.getPos())) return;
        e.setCanceled(true);
        if (!e.isSimulated()) prevenir(sp, false);
    }

    private static void engrais(BonemealEvent e) {
        if (!ConfigLoot.SPEC.isLoaded() || !ConfigLoot.NUIS_ENGRAIS.get() || !(e.getLevel() instanceof ServerLevel sl)) return;
        ServerPlayer sp = concerne(e.getEntity());
        if (sp == null || permis(sl, e.getPos())) return;
        e.setCanceled(true);
        prevenir(sp, false);
    }

    private static void prevenir(ServerPlayer sp, boolean panneau) {
        Component c = panneau
                ? Component.translatableWithFallback("heraliz_loot.nuisance.panneau", "Ce panneau ne peut pas être modifié.")
                : Component.translatableWithFallback("heraliz_loot.nuisance.interdit", "Impossible ici.");
        sp.displayClientMessage(c.copy().withStyle(ChatFormatting.GRAY), true);
    }
}
