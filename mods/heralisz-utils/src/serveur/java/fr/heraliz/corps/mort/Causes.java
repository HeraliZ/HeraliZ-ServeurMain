package fr.heraliz.corps.mort;

import fr.heraliz.corps.compat.ReanimationCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Ligne « cause » d'un corps, traduite chez chaque client :
 * <ol>
 *     <li>execution Reanimation : « Exécuté par X avec &lt;finition&gt; » ;</li>
 *     <li>un tueur connu (auteur des degats, sinon celui a qui revient le kill, par exemple celui qui a mis a terre un
 *     joueur qui s'est vide de son sang) : « Tué par X » ;</li>
 *     <li>sinon selon le type de degats : vide de son sang, abandon, deconnexion a terre, deconnexion en combat ;</li>
 *     <li>sinon rien.</li>
 * </ol>
 */
public final class Causes {
    private Causes() {
    }

    static final ResourceLocation EXECUTION = new ResourceLocation("reanimation", "execution");
    static final ResourceLocation SAIGNE = new ResourceLocation("reanimation", "bleed_out");
    static final ResourceLocation ABANDON = new ResourceLocation("reanimation", "give_up");
    static final ResourceLocation DECO_A_TERRE = new ResourceLocation("reanimation", "disconnect");
    static final ResourceLocation DECO_COMBAT = new ResourceLocation("heralisz_matchs", "deconnexion_combat");

    /** Vrai si la mort est une execution de Reanimation (la scene montre encore la victime un moment). */
    public static boolean execution(DamageSource source) {
        return EXECUTION.equals(type(source));
    }

    @Nullable
    static ResourceLocation type(DamageSource source) {
        return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
    }

    public static Component calculer(ServerPlayer mort, DamageSource source) {
        ResourceLocation type = type(source);
        Entity auteur = source.getEntity();
        LivingEntity credit = mort.getKillCredit();
        if (EXECUTION.equals(type)) {
            Entity bourreau = auteur != null && auteur != mort ? auteur : credit;
            if (bourreau != null && bourreau != mort) {
                String finition = ReanimationCompat.finition(bourreau);
                if (finition != null) {
                    return Component.translatable("corps.heraliz_corps.cause.execute_avec", nom(bourreau),
                            Component.translatable("execution.reanimation." + finition));
                }
                return Component.translatable("corps.heraliz_corps.cause.execute", nom(bourreau));
            }
        }
        Entity tueur = auteur instanceof LivingEntity && auteur != mort ? auteur : (credit != null && credit != mort ? credit : null);
        if (tueur != null) return Component.translatable("corps.heraliz_corps.cause.tue_par", nom(tueur));
        if (SAIGNE.equals(type)) return Component.translatable("corps.heraliz_corps.cause.saigne");
        if (ABANDON.equals(type)) return Component.translatable("corps.heraliz_corps.cause.abandon");
        if (DECO_A_TERRE.equals(type)) return Component.translatable("corps.heraliz_corps.cause.deconnexion_a_terre");
        if (DECO_COMBAT.equals(type)) return Component.translatable("corps.heraliz_corps.cause.deconnexion_combat");
        return Component.empty();
    }

    /** Nom sans decoration : pseudo d'un joueur, nom (traduisible) d'une creature. */
    private static Component nom(Entity e) {
        if (e instanceof Player p) return Component.literal(p.getGameProfile().getName());
        return e.getName().copy();
    }
}
