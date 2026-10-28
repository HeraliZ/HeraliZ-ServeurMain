package fr.heraliz.crac.event;

import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
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
 * leur hauteur, et c'est un effet de Minecraft que l'anticheat connaît. Depuis le 26/10, le dessus d'un demi-mur compte
 * aussi (sauter sur un demi-mur, puis sur le plancher posé à côté : les montées façon Rust).
 * <p>27/10 (montées encore bloquées) : l'effet arrivait parfois après le saut. Il n'était cherché que tous les 4 ticks,
 * à 1 bloc du rebord, pour 8 ticks : un joueur qui arrive en courant (0,28 bloc par tick) sautait avant que son jeu l'ait
 * reçu (un tick et le ping plus tard) et retombait. Maintenant tous les 2 ticks, à 1,6 bloc, pour 16 ticks : l'effet est
 * déjà chez lui quand il atteint le rebord. Toujours un Saut amélioré I (1,8 bloc), toujours au sol seulement.
 */
@EventBusSubscriber(modid = "crac")
public final class SautRebord {
    private static final double BAS = 1.15, HAUT = 1.75, PORTEE = 1.6;
    /** Durée de l'effet (ticks), renouvelé tant que le joueur reste au pied du rebord. */
    private static final int DUREE = 16;

    private SautRebord() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)
            || p.tickCount % 2 != 0 || !p.onGround() || p.isSpectator()) {
            return;
        }
        MobEffectInstance deja = p.getEffect(MobEffects.JUMP);
        if (deja != null && (deja.getAmplifier() > 0 || deja.getDuration() > 8)) {
            return;   // un saut plus fort, ou le nôtre encore là
        }
        double pieds = p.getY();
        AABB zone = p.getBoundingBox().inflate(PORTEE, 0, PORTEE).setMinY(pieds + BAS - 0.5).setMaxY(pieds + HAUT + 0.1);
        for (Piece s : StructureService.piecesIn(level, zone)) {
            double dessus;
            if (s.kind().family == PieceKind.Family.SURFACE && !s.kind().isRoof()) {
                dessus = s.key().origin().getY() + s.key().baseY() / 16.0;
            } else if (s.kind() == PieceKind.HALF_WALL) {
                // 26/10 : on saute aussi sur le dessus d'un demi-mur (1,5 bloc), comme dans Rust
                dessus = s.key().origin().getY() + (s.key().baseY() + fr.heraliz.crac.build.geom.PieceSolids.halfTop(s.kind(), s.variant)) / 16.0;
            } else {
                continue;
            }
            double d = dessus - pieds;
            if (d > BAS && d <= HAUT) {
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, DUREE, 0, true, false, false));
                return;
            }
        }
    }
}
