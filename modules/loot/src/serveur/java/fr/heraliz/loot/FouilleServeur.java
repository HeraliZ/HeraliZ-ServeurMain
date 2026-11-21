package fr.heraliz.loot;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fouille avec un objet en main, côté serveur (0.9.18). Le joueur a cliqué droit sur une chose à fouiller avec une arme
 * ou un objet en main (client/FouilleObjetEnMain) : on l'ouvre ici comme une main vide. Avant, le clic passait par le
 * clic droit ordinaire de Minecraft avec l'objet en main, qu'un plugin du serveur refusait aux joueurs (seuls les op
 * passaient). Tout est revérifié : joueur en jeu, portée, point visé sur le bloc, chose à fouiller, et les règles des
 * autres mods (événements de Forge : claims et vue dégagée de CRac, joueur à terre, conteneurs scellés, tutoriel…), qui
 * décident comme pour un clic ordinaire.
 */
final class FouilleServeur {
    /** Entités qu'on fouille par clic droit : cadavres (HeraliZ Utils), caisses de largage (Airstrike). */
    private static final Set<ResourceLocation> ENTITES = Set.of(
            new ResourceLocation("heraliz_corps", "corpse"),
            new ResourceLocation("airstrike", "supply_crate"));
    /** Portée au plus, des yeux au point le plus proche (portée de Minecraft 4,5 en survie + marge réseau). */
    private static final double PORTEE = 6.0;
    /**
     * Tick du serveur de la dernière demande de chaque joueur (21/11, audit) : une demande par tick au plus. Un jeu honnête
     * n'en envoie qu'une par clic (ou une tous les 4 ticks, touche tenue) ; un jeu modifié qui en envoyait des centaines
     * d'un coup faisait refaire, pour chacune, les règles des autres mods et les rayons de l'anticheat sur le fil du
     * serveur. Fil du serveur seulement ; un joueur parti est oublié tout seul (table faible).
     */
    private static final java.util.Map<ServerPlayer, Integer> DERNIERE = new java.util.WeakHashMap<>();

    private FouilleServeur() {
    }

    static void brancher() {
        ReseauLoot.FOUILLER_SERVEUR.brancher(FouilleServeur::recevoir);
    }

    private static void recevoir(ReseauLoot.Demande d) {
        ServerPlayer sp = d.joueur();
        int tick = sp.server.getTickCount();
        Integer avant = DERNIERE.put(sp, tick);
        if (avant != null && avant == tick) return;
        if (sp.isSpectator() || !sp.isAlive() || sp.containerMenu != sp.inventoryMenu) return;
        if (sp.getMainHandItem().isEmpty()) return; // main vide : le clic ordinaire marche déjà
        sp.resetLastActionTime();
        if (d.m().entite() >= 0) entite(sp, d.m().entite());
        else bloc(sp, d.m().bloc());
    }

    private static void bloc(ServerPlayer sp, BlockHitResult hit) {
        ServerLevel sl = sp.serverLevel();
        BlockPos pos = hit.getBlockPos();
        if (!sl.isLoaded(pos) || !aPortee(sp, new AABB(pos))) return;
        // le point visé doit être sur le bloc (comme le vérifie Minecraft pour un clic ordinaire)
        Vec3 v = hit.getLocation();
        if (!new AABB(pos).inflate(1.0E-4 + 0.5).contains(v)) return;
        BlockState st = sl.getBlockState(pos);
        if (!OuvertureAccroupi.fouillable(sl, pos)) return;
        InteractionHand main = InteractionHand.MAIN_HAND;
        PlayerInteractEvent.RightClickBlock e = ForgeHooks.onRightClickBlock(sp, main, pos, hit);
        if (e.isCanceled() || e.getUseBlock() == Event.Result.DENY) {
            sp.connection.send(new ClientboundBlockUpdatePacket(sl, pos));
            return;
        }
        InteractionResult r = st.use(sl, sp, main, hit);
        if (r.shouldSwing()) sp.swing(main, true);
    }

    private static boolean aPortee(ServerPlayer sp, AABB boite) {
        Vec3 yeux = sp.getEyePosition();
        double dx = Math.max(0, Math.max(boite.minX - yeux.x, yeux.x - boite.maxX));
        double dy = Math.max(0, Math.max(boite.minY - yeux.y, yeux.y - boite.maxY));
        double dz = Math.max(0, Math.max(boite.minZ - yeux.z, yeux.z - boite.maxZ));
        return dx * dx + dy * dy + dz * dz <= PORTEE * PORTEE;
    }

    private static void entite(ServerPlayer sp, int id) {
        Entity en = sp.serverLevel().getEntity(id);
        if (en == null || !en.isAlive()) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(en.getType());
        if (type == null || !ENTITES.contains(type) || !aPortee(sp, en.getBoundingBox())) return;
        InteractionHand main = InteractionHand.MAIN_HAND;
        InteractionResult refus = ForgeHooks.onInteractEntity(sp, en, main);
        if (refus != null) return;
        InteractionResult r = en.interact(sp, main);
        if (r.shouldSwing()) sp.swing(main, true);
    }
}
