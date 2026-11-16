package fr.heraliz.armory.cible;

import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.network.PaquetsCible;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Chiffres des dégâts sur les joueurs (08/11, demande du propriétaire : « quand on tire sur un joueur, voir en petit les
 * dégâts qu'on lui met, pour être sûr d'avoir touché »). Le SERVEUR décide : LivingDamageEvent en priorité la plus basse,
 * donc la vie que le joueur touché perd vraiment (après les réglages des armes, les armures de l'Armurerie et toutes les
 * protections ; un coup annulé — spawn, protection, à terre… — n'envoie rien). Envoyé au seul tireur (jamais à la victime
 * ni aux autres) ; rien pour 0, rien sur un joueur spectateur ou invisible (vanish du staff). Réglage du serveur
 * {@code [chiffres] joueurs} de heraliz_armory-common.toml. Un paquet par coup touché (les plombs d'un fusil à pompe
 * s'additionnent chez le tireur). Rien ne tourne sans coup.
 * <p>Audit du 15/11 (combat, M1) : le chiffre est borné à la vie (et à l'absorption) que la victime avait encore, comme les
 * dégâts comptés des clans ({@code ClanEvents.onDamage}) : un coup mortel affiche la vie vraiment perdue. Les coups de
 * l'exécution de Réanimation ne s'affichent pas : coup d'essai au début ({@code reanimation:execution_check}, « 0,1 ») et
 * coup final ({@code reanimation:execution}, dégâts sans limite : un nombre de 39 chiffres au-dessus de la victime).</p>
 */
public final class ChiffresJoueurs {
    private ChiffresJoueurs() {}

    /** Joueurs touchés à la tête par une balle de TaCZ, au tick du coup (lu une fois au moment des dégâts). */
    private static final Map<Integer, Long> TETES = new HashMap<>();
    /** Coups de l'exécution (Réanimation) : jamais de chiffre. */
    private static final ResourceKey<DamageType> EXECUTION_ESSAI = cle("execution_check");
    private static final ResourceKey<DamageType> EXECUTION = cle("execution");

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, ChiffresJoueurs::degats);
    }

    /** Balle de TaCZ à la tête d'un joueur (CiblesServeur.BallesTacz), fil du serveur. */
    static void noterTete(ServerPlayer victime, long tick) {
        if (TETES.size() > 256) TETES.clear();   // jamais lu (coup annulé ensuite) : jamais plus de quelques entrées
        TETES.put(victime.getId(), tick);
    }

    private static ResourceKey<DamageType> cle(String nom) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("reanimation", nom));
    }

    private static void degats(LivingDamageEvent e) {
        DamageSource source = e.getSource();
        if (!(e.getEntity() instanceof ServerPlayer victime) || !(source.getEntity() instanceof ServerPlayer tireur)
                || tireur == victime || e.getAmount() <= 0.0F || !ArmorConfig.CHIFFRES_JOUEURS.get()) return;
        if (victime.isSpectator() || victime.isInvisible() || tireur.hasDisconnected()) return;
        if (source.is(EXECUTION_ESSAI) || source.is(EXECUTION)) return;
        // la vie vraiment perdue (balle de 30 sur un joueur à 4 PV : 4), comme ClanEvents
        float perdu = Math.min(e.getAmount(), victime.getHealth() + victime.getAbsorptionAmount());
        if (perdu <= 0.0F) return;
        long tick = victime.level().getGameTime();
        Long t = TETES.remove(victime.getId());
        boolean tete = t != null && t == tick;
        ArmoryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> tireur),
                new PaquetsCible.Degats(victime.getId(), perdu, tete, tick));
    }
}
