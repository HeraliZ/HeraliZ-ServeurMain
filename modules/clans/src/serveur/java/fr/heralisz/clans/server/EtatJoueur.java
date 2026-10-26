package fr.heralisz.clans.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * État d'un joueur lu dans les autres mods, par réflexion (chaque mod absent = « non ») : en combat (Matchs,
 * {@code CombatTag}), à terre (Réanimation), en match (Matchs), sur la carte (overworld), objet prêté par un match ou
 * objet à vie (Tactique). Sert au coffre du clan et à la création d'un clan (23/10, demande du propriétaire).
 */
final class EtatJoueur {
    /** Clé NBT des objets prêtés en match ({@code MatchApi.NBT_PRET}). */
    static final String NBT_PRET = "HeraliszMatch";

    private static final Method COMBAT = methode("fr.heraliz.noyau.api.CombatTag", "restantMs", UUID.class);
    private static final Method MATCH = methode("fr.heraliz.noyau.api.Matchs", "enMatch", UUID.class);
    private static final Method A_TERRE = methode("fr.heraliz.reanimation.core.Downed", "isDowned", Entity.class);
    private static final Method A_VIE = methode("fr.heraliz.tactique.avie.ObjetsAVie", "estAVie", ItemStack.class);

    private EtatJoueur() {
    }

    @Nullable
    private static Method methode(String classe, String nom, Class<?>... types) {
        try {
            return Class.forName(classe).getMethod(nom, types);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Object appel(@Nullable Method m, Object arg) {
        if (m == null) return null;
        try {
            return m.invoke(null, arg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static boolean enCombat(ServerPlayer p) {
        return appel(COMBAT, p.getUUID()) instanceof Long l && l > 0;
    }

    static boolean aTerre(ServerPlayer p) {
        return Boolean.TRUE.equals(appel(A_TERRE, p));
    }

    static boolean enMatch(ServerPlayer p) {
        if (Boolean.TRUE.equals(appel(MATCH, p.getUUID()))) return true;
        if (p.getPersistentData().getBoolean("heralisz:en_match")) return true;
        ResourceLocation monde = p.level().dimension().location();
        return "heraliz_clashwar".equals(monde.getNamespace());
    }

    static boolean surCarte(ServerPlayer p) {
        return p.level().dimension() == Level.OVERWORLD;
    }

    /** Objet prêté par un match ou objet à vie (couteaux, skins à vie). */
    static boolean pretOuAVie(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.hasTag() && s.getTag().contains(NBT_PRET)) return true;
        return Boolean.TRUE.equals(appel(A_VIE, s));
    }
}
