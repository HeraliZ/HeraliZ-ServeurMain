package fr.heraliz.emote;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * When a player may not play an emote on the HeraliZ server. Soft checks only: every other mod is read by reflection or
 * through the player's {@code persistentData}, so nothing breaks when one of them is missing.
 * <ul>
 *     <li>downed (Reanimation, {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}, works on both sides);</li>
 *     <li>in a match ({@code persistentData} flag {@code heralisz:en_match} set by ClashWar): refused;</li>
 *     <li>combat-tagged ({@code persistentData} long {@code heralisz:combat_jusqua} = end of the tag, epoch ms);</li>
 *     <li>emote not unlocked (emote crate, staff): {@link #DEBLOQUEE}, plugged in by the server part.</li>
 * </ul>
 * Also: no weapon in the hands while an emote plays and {@link #SANS_ARME_MS} after it (an emote shows the player in
 * third person, which must not help in a fight): {@link #estArme}, enforced by {@code serveur.SansArme}.
 * This class holds the server side (used by {@code mixin.ServerEmotePlayMixin} when the mod is also on the server);
 * {@code client.ClientRules} does the same with what the client knows.
 */
public final class EmoteRules {
    private EmoteRules() {
    }

    public static final String EN_MATCH = "heralisz:en_match";
    public static final String COMBAT_JUSQUA = "heralisz:combat_jusqua";

    /** Weapons stay out of the hands this long after the end of an emote (ms). */
    public static final long SANS_ARME_MS = 10_000;
    /** Extra weapons (data tag), besides swords, axes, bows, tridents, TaCZ guns and knives, melee and grenades. */
    public static final TagKey<Item> ARMES = TagKey.create(Registries.ITEM, new ResourceLocation(HeralizEmote.MODID, "armes"));
    private static final Set<String> ESPACES_ARMES = Set.of("heralisz_melee", "heralisz_grenade");
    private static final ResourceLocation ARME_TACZ = new ResourceLocation("tacz", "modern_kinetic_gun");
    /**
     * Mêlée, grenades, bouclier flash et détonateur de Tactique : enregistrés sous {@code heralisz_tactical} (les modIds
     * {@code heralisz_melee} / {@code heralisz_grenade} ne nomment que leur contenu, rangé dans les données de l'objet).
     * 25/10 : avant, seul l'espace de noms était testé, ces objets n'étaient pas des armes pour la règle des emotes (même
     * défaut que le coffre du clan, corrigé le 23/10).
     */
    private static final Set<ResourceLocation> ARMES_TACTIQUE = Set.of(
            new ResourceLocation("heralisz_tactical", "melee"), new ResourceLocation("heralisz_tactical", "throwable"),
            new ResourceLocation("heralisz_tactical", "flash_shield"), new ResourceLocation("heralisz_tactical", "detonator"));
    /** Fin de l'interdiction des armes gardée dans les données du joueur à sa déconnexion (epoch ms), relue à la connexion. */
    public static final String SANS_ARME_JUSQUA = "heraliz_emote:sans_arme_jusqua";

    /** Has this player unlocked this emote? Plugged in by the server part (without it: yes). */
    public static volatile BiPredicate<ServerPlayer, UUID> DEBLOQUEE = (p, u) -> true;

    private static boolean downedLooked;
    @Nullable
    private static Method isDowned;

    // ------------------------------------------------------------------ messages (shared with the client)
    public static Component downed() {
        return Component.translatableWithFallback("heraliz_emote.refused.downed", "Pas d'emote quand on est à terre");
    }

    public static Component match() {
        return Component.translatableWithFallback("heraliz_emote.refused.match", "Pas d'emote pendant un match");
    }

    public static Component combat(long remainingMs) {
        long s = Math.max(1, (remainingMs + 999) / 1000);
        return Component.translatableWithFallback("heraliz_emote.refused.combat", "Pas d'emote en combat (encore %s s)", s);
    }

    public static Component verrouillee() {
        return Component.translatableWithFallback("heraliz_emote.refused.locked", "Emote à débloquer dans la caisse des emotes");
    }

    public static Component sansArme(long remainingMs) {
        long s = Math.max(1, (remainingMs + 999) / 1000);
        return Component.translatableWithFallback("heraliz_emote.no_weapon", "Pas d'arme après une emote (encore %s s)", s);
    }

    /** Guns and knives of TaCZ, melee weapons, grenades, Wonder Weapons, swords, axes, bows, tridents, tag {@link #ARMES}. */
    public static boolean estArme(ItemStack s) {
        if (s.isEmpty()) return false;
        Item it = s.getItem();
        if (it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem || it instanceof ProjectileWeaponItem) return true;
        if (s.is(ARMES)) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        return id != null && (id.equals(ARME_TACZ) || ARMES_TACTIQUE.contains(id) || ESPACES_ARMES.contains(id.getNamespace()));
    }

    // ------------------------------------------------------------------ Reanimation
    /** True if Reanimation says this entity is downed (false without Reanimation). Client and server. */
    public static boolean isDowned(@Nullable Entity e) {
        if (e == null) return false;
        if (!downedLooked) {
            downedLooked = true;
            try {
                isDowned = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                isDowned = null;
            }
        }
        if (isDowned == null) return false;
        try {
            return Boolean.TRUE.equals(isDowned.invoke(null, e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ server side
    /** Why this player may not play this emote right now, or null if they may. Server thread. */
    @Nullable
    public static Component refusal(ServerPlayer p, UUID emote) {
        if (p.isCreative() || p.isSpectator()) return null;
        if (isDowned(p)) return downed();
        CompoundTag data = p.getPersistentData();
        if (data.getBoolean(EN_MATCH)) return match();
        long left = data.getLong(COMBAT_JUSQUA) - System.currentTimeMillis();
        if (left > 0) return combat(left);
        if (!DEBLOQUEE.test(p, emote)) return verrouillee();
        return null;
    }
}
