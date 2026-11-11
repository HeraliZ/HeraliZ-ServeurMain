package fr.heralisz.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * Durée du « Bonus de loot » (objet de la boutique). 11/11 (retour du propriétaire) : elle passe en TEMPS RÉEL, comme
 * celle des bons : fin = heure d'activation + durée ({@link BonusLootConfig#minutes()}), même déconnecté, AFK, au spawn
 * ou dans une autre dimension. Un deuxième objet ajoute sa durée à la fin en cours. Avant (02/11 → 11/11) : temps de
 * jeu, décompté seulement connecté, sur la carte et hors AFK.
 * <p>Le bonus lui-même est appliqué par HeraliZ Loot ; contrat entre nos mods, sans dépendance (CLAUDE.md §5) : données
 * persistantes du joueur {@code PlayerPersisted.HeralizBonusLoot.objet = {Pct: <n>, Fin: <heure système en ms>}}
 * (PlayerPersisted : gardé à la mort), comme les sources « clan » et « bon_N ». Une source s'éteint seule à son heure de
 * fin (Loot n'en tient plus compte). Un ancien temps de jeu {@code Restant} (ticks) devient une heure de fin à la
 * connexion du joueur.</p>
 * <p>Rien ne tourne pour les joueurs sans bonus : seuls ceux qui en ont un, connectés, sont suivis ({@link #FINS}), une
 * fois par seconde, pour le message de fin.</p>
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BonusLootTemps {
    private static final String PERSISTE = "PlayerPersisted";
    private static final String CLE = "HeralizBonusLoot";
    private static final String SOURCE = "objet";

    /** Joueurs connectés dont le bonus de l'objet court encore : heure de fin (ms). Oublié à la déconnexion. */
    private static final Map<UUID, Long> FINS = new HashMap<>();

    private BonusLootTemps() {
    }

    /** Ajoute la durée d'un objet à la fin en cours (ou à maintenant) ; rend le temps qui reste, en ms. */
    static long activer(ServerPlayer p) {
        CompoundTag donnees = p.getPersistentData();
        CompoundTag persiste = donnees.getCompound(PERSISTE);
        CompoundTag sources = persiste.getCompound(CLE);
        CompoundTag source = sources.getCompound(SOURCE);
        long now = System.currentTimeMillis();
        long fin = Math.max(now, fin(source, now)) + BonusLootConfig.minutes() * 60_000L;
        source.putInt("Pct", BonusLootConfig.pourcent());
        source.putLong("Fin", fin);
        source.remove("Restant");
        sources.put(SOURCE, source);
        persiste.put(CLE, sources);
        donnees.put(PERSISTE, persiste);
        FINS.put(p.getUUID(), fin);
        return fin - now;
    }

    /** Heure de fin d'une source : la sienne, ou celle d'un ancien temps de jeu restant compté à partir de maintenant. */
    private static long fin(CompoundTag source, long now) {
        if (source.contains("Fin", Tag.TAG_LONG)) return source.getLong("Fin");
        long restant = source.getLong("Restant");
        return restant > 0L ? now + restant * 50L : 0L;
    }

    /** Source du bonus du joueur, ou null s'il n'en a pas. */
    private static CompoundTag source(ServerPlayer p) {
        CompoundTag donnees = p.getPersistentData();
        if (!donnees.contains(PERSISTE, Tag.TAG_COMPOUND)) return null;
        CompoundTag persiste = donnees.getCompound(PERSISTE);
        if (!persiste.contains(CLE, Tag.TAG_COMPOUND)) return null;
        CompoundTag sources = persiste.getCompound(CLE);
        return sources.contains(SOURCE, Tag.TAG_COMPOUND) ? sources.getCompound(SOURCE) : null;
    }

    /** Une fois par seconde, seulement pour un joueur connecté dont le bonus court : à son heure de fin, le message. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FINS.isEmpty() || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        Long fin = FINS.get(p.getUUID());
        if (fin == null || fin > System.currentTimeMillis()) return;
        FINS.remove(p.getUUID());
        terminer(p);
    }

    /**
     * Bonus fini : la source est retirée (si un autre objet ne l'a pas prolongée entre-temps) et le joueur prévenu. Déjà
     * retirée par HeraliZ Loot (conteneur ouvert juste après l'heure de fin) : le joueur est prévenu quand même.
     */
    private static void terminer(ServerPlayer p) {
        CompoundTag source = source(p);
        if (source != null) {
            long now = System.currentTimeMillis();
            long fin = fin(source, now);
            if (fin > now) {
                FINS.put(p.getUUID(), fin);
                return;
            }
            p.getPersistentData().getCompound(PERSISTE).getCompound(CLE).remove(SOURCE);
        }
        p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.bonus_loot.termine", "Ton bonus de loot est terminé.")
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FINS.remove(event.getEntity().getUUID());
    }

    /**
     * À la connexion : un ancien temps de jeu restant devient une heure de fin (compté en temps réel à partir de
     * maintenant) ; bonus en cours : rappel du temps qui reste ; fini pendant l'absence : retiré, avec le message de fin.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag source = source(p);
        if (source == null) return;
        long now = System.currentTimeMillis();
        long fin = fin(source, now);
        if (source.contains("Restant")) {
            source.remove("Restant");
            if (fin > now) source.putLong("Fin", fin);
        }
        if (fin <= now) {
            terminer(p);
            return;
        }
        FINS.put(p.getUUID(), fin);
        p.sendSystemMessage(Component.translatableWithFallback("heralisz_utils.bonus_loot.actif_reel",
                "Bonus de loot +%s %% actif : encore %s (le temps passe aussi quand tu n'es pas connecté).",
                String.valueOf(source.getInt("Pct")), duree((fin - now) / 50L)).withStyle(ChatFormatting.GOLD));
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
