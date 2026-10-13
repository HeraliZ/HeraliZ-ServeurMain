package fr.heraliz.vehicules;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Suivi des véhicules du serveur : le coffre de chaque véhicule qui arrive dans un monde (posé, chargé avec son chunk,
 * changé de dimension) est vidé au sol ({@link CoffreRetire}), puis de nouveau une fois par seconde tant qu'il est chargé
 * (réglage changé, objet poussé par un entonnoir).
 * <p>Ordre : les objets ne sont jamais posés pendant l'arrivée elle-même (ajouter une entité pendant le chargement d'un
 * chunk), seulement au début ou à la fin du tick suivant. Écouteur d'arrivée en priorité basse : un véhicule refusé par
 * {@code Regles} (dimension interdite) n'est pas suivi. Ensembles « faibles » : un véhicule déchargé ou détruit est oublié.
 */
public final class SuiviVehicules {
    /** Contrôle des véhicules chargés : une fois par seconde. */
    private static final int INTERVALLE = 20;
    /** Véhicules chargés. */
    private static final Set<AbstractVehicle> SUIVIS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Arrivés depuis le dernier passage : réglés au prochain début ou fin de tick. */
    private static final Set<AbstractVehicle> ARRIVES = Collections.newSetFromMap(new WeakHashMap<>());
    private static int compteur;

    private SuiviVehicules() {
    }

    static void enregistrer(IEventBus bus) {
        bus.addListener(EventPriority.LOW, SuiviVehicules::onArrivee);
        bus.addListener(SuiviVehicules::onTick);
        bus.addListener(SuiviVehicules::onArret);
    }

    private static void onArrivee(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractVehicle v)) return;
        SUIVIS.add(v);
        ARRIVES.add(v);
    }

    private static void onTick(TickEvent.ServerTickEvent e) {
        if (!ARRIVES.isEmpty()) {
            for (AbstractVehicle v : new ArrayList<>(ARRIVES)) {
                Entity ent = v;
                if (ent.isRemoved()) {
                    ARRIVES.remove(v);
                } else if (ent.isAddedToWorld()) { // sinon chunk pas encore actif : au passage suivant
                    ARRIVES.remove(v);
                    entretenir(v);
                }
            }
        }
        if (e.phase != TickEvent.Phase.END || ++compteur < INTERVALLE) return;
        compteur = 0;
        for (AbstractVehicle v : new ArrayList<>(SUIVIS)) {
            Entity ent = v;
            if (ent.isRemoved()) SUIVIS.remove(v);
            else if (ent.isAddedToWorld()) entretenir(v);
        }
    }

    /** Coffre vidé au sol (objets à côté du véhicule). */
    private static void entretenir(AbstractVehicle v) {
        if (!CoffreRetire.actif() || !CoffreRetire.contientQuelqueChose(v)) return;
        Entity ent = v;
        if (ent.level() instanceof ServerLevel level) {
            CoffreRetire.poser(level, CoffreRetire.aCote(v), CoffreRetire.sortir(v), v.getVehicleId());
        }
    }

    private static void onArret(ServerStoppedEvent e) {
        SUIVIS.clear();
        ARRIVES.clear();
        compteur = 0;
    }
}
