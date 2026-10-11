package fr.heralisz.utils;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temps de jeu restant du « Bonus de loot » (objet de la boutique), décompté seulement quand le joueur est connecté.
 * Le bonus lui-même est appliqué par HeraliZ Loot ; contrat entre nos mods, sans dépendance (CLAUDE.md §5) : données
 * persistantes du joueur {@code PlayerPersisted.HeralizBonusLoot.objet = {Pct: <n>, Restant: <ticks de jeu>}}
 * (PlayerPersisted : gardé à la mort).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BonusLootTemps {
    private static final String PERSISTE = "PlayerPersisted";
    private static final String CLE = "HeralizBonusLoot";
    private static final String SOURCE = "objet";

    private BonusLootTemps() {
    }

    /** Ajoute la durée d'un objet au temps restant ; rend le nouveau temps restant (ticks). */
    static long activer(ServerPlayer p) {
        CompoundTag donnees = p.getPersistentData();
        CompoundTag persiste = donnees.getCompound(PERSISTE);
        CompoundTag sources = persiste.getCompound(CLE);
        CompoundTag source = sources.getCompound(SOURCE);
        long restant = Math.max(0L, source.getLong("Restant")) + BonusLootConfig.minutes() * 1200L;
        source.putInt("Pct", BonusLootConfig.pourcent());
        source.putLong("Restant", restant);
        sources.put(SOURCE, source);
        persiste.put(CLE, sources);
        donnees.put(PERSISTE, persiste);
        return restant;
    }

    /** Source du bonus du joueur, ou null s'il n'en a pas. */
    private static CompoundTag source(ServerPlayer p) {
        CompoundTag persiste = p.getPersistentData().getCompound(PERSISTE);
        if (!persiste.contains(CLE, Tag.TAG_COMPOUND)) return null;
        CompoundTag sources = persiste.getCompound(CLE);
        return sources.contains(SOURCE, Tag.TAG_COMPOUND) ? sources.getCompound(SOURCE) : null;
    }

    /** Une seconde de jeu de moins ; à zéro, le bonus s'arrête. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        CompoundTag source = source(p);
        if (source == null) return;
        long restant = source.getLong("Restant") - 20L;
        if (restant > 0L) {
            source.putLong("Restant", restant);
            return;
        }
        p.getPersistentData().getCompound(PERSISTE).getCompound(CLE).remove(SOURCE);
        p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.bonus_loot.termine", "Ton bonus de loot est terminé.")
                .withStyle(ChatFormatting.GRAY));
    }

    /** À la connexion : rappel du bonus en cours. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            CompoundTag source = source(p);
            if (source != null && source.getLong("Restant") > 0L) {
                p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.bonus_loot.actif",
                        "Bonus de loot +%s %% actif : encore %s de jeu.", String.valueOf(source.getInt("Pct")),
                        duree(source.getLong("Restant"))).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** « 2 h », « 1 h 35 », « 12 min » (traduit chez le joueur). */
    static Component duree(long ticks) {
        long minutes = (Math.max(0L, ticks) + 1199L) / 1200L;
        if (minutes < 60L) return Component.translatableWithFallback("heralisz_utils.duree.min", "%s min", String.valueOf(minutes));
        long h = minutes / 60L, m = minutes % 60L;
        return m == 0L ? Component.translatableWithFallback("heralisz_utils.duree.h", "%s h", String.valueOf(h))
                : Component.translatableWithFallback("heralisz_utils.duree.h_min", "%s h %s", String.valueOf(h),
                        String.format(Locale.ROOT, "%02d", m));
    }
}
