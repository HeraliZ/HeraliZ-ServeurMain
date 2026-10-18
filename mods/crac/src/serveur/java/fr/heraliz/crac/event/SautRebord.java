package fr.heraliz.crac.event;

import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.StructureService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Monter sur un plancher posé sur un demi-mur (choix du propriétaire, 18/10) : le demi-mur fait 1,5 bloc et un saut
 * ordinaire ne monte qu'à 1,25. Au pied d'un sol de construction (fondation ou plancher) dont le dessus est entre 1,25 et
 * 1,75 bloc au-dessus de ses pieds, le joueur reçoit un court Saut amélioré I invisible (1,8 bloc) : les pièces gardent
 * leur hauteur, et c'est un effet de Minecraft que l'anticheat connaît.
 */
@EventBusSubscriber(modid = "crac")
public final class SautRebord {
    private static final double BAS = 1.25, HAUT = 1.75, PORTEE = 1.0;
    /** Durée de l'effet (ticks), renouvelé tant que le joueur reste au pied du rebord. */
    private static final int DUREE = 8;

    private SautRebord() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)
            || p.tickCount % 4 != 0 || !p.onGround() || p.isSpectator()) {
            return;
        }
        MobEffectInstance deja = p.getEffect(MobEffects.JUMP);
        if (deja != null && (deja.getAmplifier() > 0 || deja.getDuration() > 4)) {
            return;   // un saut plus fort, ou le nôtre encore là
        }
        double pieds = p.getY();
        AABB zone = p.getBoundingBox().inflate(PORTEE, 0, PORTEE).setMinY(pieds + BAS - 0.5).setMaxY(pieds + HAUT + 0.1);
        for (Piece s : StructureService.piecesIn(level, zone)) {
            if (s.kind().family != PieceKind.Family.SURFACE || s.kind().isRoof()) {
                continue;
            }
            double dessus = s.key().origin().getY() + s.key().baseY() / 16.0;
            double d = dessus - pieds;
            if (d > BAS && d <= HAUT) {
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, DUREE, 0, true, false, false));
                return;
            }
        }
    }
}
