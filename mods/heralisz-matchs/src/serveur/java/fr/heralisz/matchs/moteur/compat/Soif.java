package fr.heralisz.matchs.moteur.compat;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Soif de ThirstWasTaken (capacite {@code ModCapabilities.PLAYER_THIRST}, interface {@code IThirst}) par reflexion
 * tolerante : absent = rien. {@code serializeNBT} ecrit {thirst, quenched, exhaustion, enable} ; le drapeau
 * {@code enable} (soif qui baisse) est remis par {@code deserializeNBT} a la restauration. Fil serveur.
 */
public final class Soif {
    private Soif() {
    }

    private static boolean pret, present;
    private static Capability<?> cap;
    private static Method serialiser, deserialiser, setThirst, setQuenched, setExhaustion, setTick, maj, getThirst, getQuenched, getTick;

    private static boolean init() {
        if (pret) return present;
        pret = true;
        try {
            Class<?> caps = Class.forName("dev.ghen.thirst.foundation.common.capability.ModCapabilities");
            Class<?> it = Class.forName("dev.ghen.thirst.foundation.common.capability.IThirst");
            cap = (Capability<?>) caps.getField("PLAYER_THIRST").get(null);
            serialiser = it.getMethod("serializeNBT");
            deserialiser = it.getMethod("deserializeNBT", CompoundTag.class);
            setThirst = it.getMethod("setThirst", int.class);
            setQuenched = it.getMethod("setQuenched", int.class);
            setExhaustion = it.getMethod("setExhaustion", float.class);
            setTick = it.getMethod("setShouldTickThirst", boolean.class);
            getTick = it.getMethod("getShouldTickThirst");
            maj = it.getMethod("updateThirstData", Player.class);
            getThirst = it.getMethod("getThirst");
            getQuenched = it.getMethod("getQuenched");
            present = cap != null;
        } catch (ClassNotFoundException e) {
            present = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HeraliszMatchs.LOG.warn("[matchs] ThirstWasTaken present mais illisible : {}", e.toString());
            present = false;
        }
        return present;
    }

    /** Vrai si la soif est geree (mod present et lisible). */
    public static boolean presente() {
        return init();
    }

    @Nullable
    private static Object de(ServerPlayer p) {
        if (!init()) return null;
        return p.getCapability(cap).resolve().orElse(null);
    }

    /** Instantane de la soif du joueur, ou null (mod absent, capacite manquante). */
    @Nullable
    public static CompoundTag sauver(ServerPlayer p) {
        Object t = de(p);
        if (t == null) return null;
        try {
            return ((CompoundTag) serialiser.invoke(t)).copy();
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.warn("[matchs] soif de {} illisible : {}", p.getGameProfile().getName(), e.toString());
            return null;
        }
    }

    /** Soif pleine et figee (match) puis synchronisee au client. */
    public static void pleineEtFigee(ServerPlayer p) {
        Object t = de(p);
        if (t == null) return;
        try {
            setThirst.invoke(t, 20);
            setQuenched.invoke(t, 20);
            setExhaustion.invoke(t, 0f);
            setTick.invoke(t, false);
            maj.invoke(t, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.warn("[matchs] soif de {} non figee : {}", p.getGameProfile().getName(), e.toString());
        }
    }

    /** Restaure la soif (drapeau « enable » compris) puis la synchronise. */
    public static void restaurer(ServerPlayer p, CompoundTag tag) {
        Object t = de(p);
        if (t == null || tag == null) return;
        try {
            deserialiser.invoke(t, tag);
            maj.invoke(t, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.warn("[matchs] soif de {} non restauree : {}", p.getGameProfile().getName(), e.toString());
        }
    }

    /** Banc d'essai : fixe la soif et la saturation d'eau (le drapeau « enable » est remis a vrai). */
    public static void definir(ServerPlayer p, int soif, int quench) {
        Object t = de(p);
        if (t == null) return;
        try {
            setThirst.invoke(t, soif);
            setQuenched.invoke(t, quench);
            setTick.invoke(t, true);
            maj.invoke(t, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeraliszMatchs.LOG.warn("[matchs] soif de {} non fixee : {}", p.getGameProfile().getName(), e.toString());
        }
    }

    /** Texte d'etat « soif 7, eau 3, active » (journal du banc), ou « absente ». */
    public static String etat(ServerPlayer p) {
        Object t = de(p);
        if (t == null) return "absente";
        try {
            return "soif " + getThirst.invoke(t) + ", eau " + getQuenched.invoke(t) + ", " + ((Boolean) getTick.invoke(t) ? "active" : "figee");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "illisible";
        }
    }
}
