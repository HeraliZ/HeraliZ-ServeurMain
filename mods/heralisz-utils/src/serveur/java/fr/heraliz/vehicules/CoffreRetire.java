package fr.heraliz.vehicules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Plus de coffre dans les véhicules (choix des propriétaires) : Limitless Vehicle donne à chaque véhicule un coffre de 54
 * cases ({@code ContainerCraft} du moteur), ouvert à bord (touche « Coffre du véhicule ») ou d'un clic droit sur l'épave
 * d'un véhicule détruit.
 * <ul>
 *   <li>Ouverture refusée : mixin serveur {@code fr.heralisz.utils.serveur.mixin.VehiculeSansCoffreMixin} (le serveur ne
 *   crée plus l'écran du coffre), qui lit {@link #actif()}.</li>
 *   <li>Objets déjà rangés dans un véhicule posé : sortis du coffre et posés au sol juste à côté de lui, sans limite de
 *   temps, dès qu'il se charge (puis contrôle chaque seconde, pour un objet poussé par un entonnoir) :
 *   {@link SuiviVehicules}. Les objets sont DÉPLACÉS (case vidée, même objet posé au sol), jamais copiés : l'anti-duplication
 *   de Spartan ({@code spartan_uid}) n'en voit toujours qu'un exemplaire.</li>
 *   <li>Véhicule retiré d'une dimension interdite ({@code Regles}) : son contenu est posé au sol avec lui au lieu d'être
 *   perdu.</li>
 * </ul>
 * Réglage {@code coffre_retire} ({@link VehiculesConfig#COFFRE_RETIRE}) : à false, le coffre du moteur revient tel quel.
 */
public final class CoffreRetire {
    /** Écart entre le bord du véhicule et l'endroit où les objets sont posés (blocs). */
    private static final double ECART = 0.6;

    private CoffreRetire() {
    }

    /** Coffre retiré (réglage coffre_retire, vrai par défaut ; vrai aussi tant que les réglages ne sont pas chargés). */
    public static boolean actif() {
        try {
            return VehiculesConfig.COFFRE_RETIRE.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return true;
        }
    }

    /** Vrai si le coffre contient au moins un objet. */
    static boolean contientQuelqueChose(Container coffre) {
        for (int i = 0; i < coffre.getContainerSize(); i++) {
            if (!coffre.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /** Sort tout du coffre : chaque case est vidée et ses objets sont rendus (les mêmes, pas des copies). */
    static List<ItemStack> sortir(Container coffre) {
        List<ItemStack> objets = new ArrayList<>();
        for (int i = 0; i < coffre.getContainerSize(); i++) {
            ItemStack s = coffre.removeItemNoUpdate(i);
            if (!s.isEmpty()) objets.add(s);
        }
        return objets;
    }

    /**
     * Endroit libre juste à côté du véhicule, au ras du sol : posés sous la caisse, les objets seraient hors de portée (le
     * véhicule repousse les joueurs hors de sa boîte). Les quatre côtés sont essayés ; sinon, au milieu du véhicule.
     */
    static Vec3 aCote(AbstractVehicle vehicule) {
        Entity e = vehicule;
        Level level = e.level();
        AABB boite = e.getBoundingBox();
        double y = boite.minY + 0.25;
        double cx = (boite.minX + boite.maxX) / 2;
        double cz = (boite.minZ + boite.maxZ) / 2;
        Vec3[] essais = {
                new Vec3(boite.maxX + ECART, y, cz), new Vec3(boite.minX - ECART, y, cz),
                new Vec3(cx, y, boite.maxZ + ECART), new Vec3(cx, y, boite.minZ - ECART)};
        for (Vec3 p : essais) {
            if (level.noCollision(EntityType.ITEM.getDimensions().makeBoundingBox(p))) return p;
        }
        return e.position().add(0, 0.5, 0);
    }

    /**
     * Pose les objets au sol à {@code pos}, sans limite de temps (ils ne disparaissent pas au bout de 5 minutes), et le
     * note dans le journal du serveur (quoi, où), pour le staff.
     */
    static void poser(ServerLevel level, Vec3 pos, List<ItemStack> objets, ResourceLocation vehicule) {
        if (objets.isEmpty()) return;
        StringBuilder liste = new StringBuilder();
        for (ItemStack s : objets) {
            if (liste.length() > 0) liste.append(", ");
            liste.append(s.getCount()).append(' ').append(ForgeRegistries.ITEMS.getKey(s.getItem()));
            ItemEntity objet = new ItemEntity(level, pos.x, pos.y, pos.z, s);
            objet.setDefaultPickUpDelay();
            objet.setUnlimitedLifetime();
            if (!level.addFreshEntity(objet)) {
                HeralizVehicules.LOG.warn("[vehicules] coffre retire : objet non pose au sol ({} {}) : {}", s.getCount(),
                        ForgeRegistries.ITEMS.getKey(s.getItem()), s.getTag() == null ? "" : s.getTag());
            }
        }
        HeralizVehicules.LOG.info("[vehicules] coffre retire : {} objet(s) du vehicule {} poses au sol en {} {} {} ({}) : {}",
                objets.size(), vehicule, (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z),
                level.dimension().location(), liste);
    }
}
