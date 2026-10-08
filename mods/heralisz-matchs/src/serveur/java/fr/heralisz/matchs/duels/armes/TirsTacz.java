package fr.heralisz.matchs.duels.armes;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.MatchApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lien avec les tirs TaCZ pour le fil des eliminations et le tableau des scores : dernier tir recu par une entite en
 * duel (arme, tir a la tete), ecoute par reflexion de {@code EntityHurtByGunEvent.Post} (TaCZ absent = rien). Aussi :
 * arme tenue et nom court affichable d'une arme. Fil serveur.
 */
public final class TirsTacz {
    private TirsTacz() {
    }

    /** Dernier tir recu : id de l'arme, tir a la tete, tick serveur (temps de jeu du niveau). */
    public record Tir(String arme, boolean tete, long tick) {
    }

    private static final Map<UUID, Tir> DERNIERS = new HashMap<>();
    private static boolean branche;
    private static Method getHurt, getGunId, isHead;

    /** Branche l'ecoute des tirs TaCZ (une fois, si TaCZ est charge). */
    @SuppressWarnings("unchecked")
    public static void brancher() {
        if (branche) return;
        branche = true;
        try {
            if (ModList.get() == null || !ModList.get().isLoaded("tacz")) return;
            Class<?> post = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post");
            getHurt = post.getMethod("getHurtEntity");
            getGunId = post.getMethod("getGunId");
            isHead = post.getMethod("isHeadShot");
            Method side = post.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (Class<Event>) post, e -> {
                try {
                    if (!"SERVER".equals(String.valueOf(side.invoke(e)))) return;
                    Object cible = getHurt.invoke(e);
                    if (!(cible instanceof Entity ent)) return;
                    if (!(ent instanceof net.minecraft.server.level.ServerPlayer p && MatchApi.enMatch(p))
                            && !ent.getTags().contains(fr.heralisz.matchs.duels.CibleEntrainement.ETIQUETTE)) return;
                    Object id = getGunId.invoke(e);
                    DERNIERS.put(ent.getUUID(), new Tir(id == null ? "" : id.toString(), Boolean.TRUE.equals(isHead.invoke(e)),
                            ent.level().getGameTime()));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // tir ignore : le fil affichera l'arme tenue
                }
            });
            HeraliszMatchs.LOG.info("[duels] tirs TaCZ ecoutes (tirs a la tete)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[duels] evenement de tir TaCZ introuvable : {}", ex.toString());
        }
    }

    /** Dernier tir recu par l'entite il y a moins d'une seconde, ou null (et l'oublie). */
    @Nullable
    public static Tir consommer(Entity victime) {
        Tir t = DERNIERS.remove(victime.getUUID());
        if (t == null || victime.level().getGameTime() - t.tick() > 20) return null;
        return t;
    }

    /** Oublie tout (arret). */
    public static void vider() {
        DERNIERS.clear();
    }

    /** Id de l'arme tenue en main principale : GunId TaCZ, sinon id de l'objet ; vide si main vide. */
    public static String armeEnMain(@Nullable LivingEntity e) {
        if (e == null) return "";
        ItemStack s = e.getMainHandItem();
        if (s.isEmpty()) return "";
        CompoundTag t = s.getTag();
        if (t != null && t.contains("GunId")) return t.getString("GunId");
        if (t != null && t.contains("MeleeWeaponId")) return t.getString("MeleeWeaponId");
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        return rl == null ? "" : rl.toString();
    }

    /** Nom court affichable d'une arme d'apres son id : « tacz:hk_mp5a5 » -> « HK MP5A5 ». */
    public static String nomCourt(String id) {
        if (id == null || id.isEmpty()) return "";
        int i = id.indexOf(':');
        String p = i >= 0 ? id.substring(i + 1) : id;
        return p.replace('_', ' ').toUpperCase(Locale.ROOT);
    }
}
