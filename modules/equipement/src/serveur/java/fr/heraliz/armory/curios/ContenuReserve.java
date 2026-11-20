package fr.heraliz.armory.curios;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Contenu des sacs portés réservé à leur porteur (20/11, audit réseau, mémoire et disque). Curios (jar d'un autre auteur,
 * intact) envoie chaque objet de ses emplacements, avec TOUTES ses données, au porteur ET à chaque joueur qui le voit :
 * <ul>
 * <li>à chaque changement de l'objet ({@code CuriosEventHandler.syncCurios}, paquet {@code SPacketSyncStack}) : un sac
 * change à chaque objet rangé ou sorti (il réécrit sa liste « Items » tout de suite, {@code BackpackInventory.setChanged}) ;
 * un sac de 54 cases rempli pèse plusieurs Ko, renvoyés à tous ceux qui voient le porteur ;</li>
 * <li>quand un joueur commence à voir le porteur ({@code playerStartTracking}, paquet {@code SPacketSyncCurios}, tous ses
 * emplacements) : en approchant, et à chaque fois que l'anti-ESP de l'anticheat le lui montre de nouveau.</li>
 * </ul>
 * Les autres joueurs n'en ont pas besoin (le rendu du sac ne lit que son modèle et son camo) et un jeu modifié pouvait y
 * lire ce que chacun transporte. Le porteur reçoit toujours tout (son infobulle compte les objets du sac). Les autres
 * reçoivent l'objet sans les mêmes clés que la tenue d'un corps ({@code CorpsEntityServeur.allege}) : contenu d'un sac ou
 * d'un conteneur, numéro anti-copie de l'anticheat. Branché par {@code CuriosContenuPorteurMixin} ; champs des paquets de
 * Curios 5.14.1 lus par leur nom (si Curios change, les envois redeviennent ceux d'avant, rien ne casse) ; réglage
 * {@code [reseau] contenu_porteur_seul} de heraliz_armory-common.toml (false = comme avant). Fil serveur.
 */
public final class ContenuReserve {
    private ContenuReserve() {
    }

    /** Clés jamais utiles au rendu chez les autres joueurs (mêmes que la tenue des corps). */
    private static final Set<String> CLES = Set.of("Items", "Inventory", "BlockEntityTag", "spartan_uid");
    private static final String PAQUET_PILE = "top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack";
    private static final String PAQUET_TOUT = "top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios";
    /** Envois allégés depuis le démarrage : changements d'un objet, joueurs qui commencent à voir un porteur. */
    public static final LongAdder CHANGEMENTS = new LongAdder(), ARRIVEES = new LongAdder();
    private static final AtomicBoolean ANNONCE = new AtomicBoolean();
    private static volatile Field champPile, champReleves;
    private static volatile boolean champsCherches;

    /** Le réglage (vrai tant que la configuration n'est pas encore lue). */
    public static boolean actif() {
        try {
            return ArmorConfig.CONTENU_PORTEUR_SEUL.get();
        } catch (IllegalStateException pasEncoreLue) {
            return true;
        }
    }

    /** Démarrage du serveur : dit dans le journal si les paquets de Curios sont bien ceux attendus. */
    public static void verifier() {
        chercherChamps();
        if (champPile != null && champReleves != null) {
            HeralizArmory.LOGGER.info("[armurerie] contenu des sacs : envoyé seulement au porteur ({})", actif() ? "actif" : "coupé par le réglage");
        } else {
            HeralizArmory.LOGGER.warn("[armurerie] contenu des sacs : paquets de Curios inattendus, envoi à tous comme avant");
        }
    }

    private static void chercherChamps() {
        if (champsCherches) return;
        champsCherches = true;
        try {
            Field f = Class.forName(PAQUET_PILE).getDeclaredField("stack");
            f.setAccessible(true);
            champPile = f.getType() == ItemStack.class ? f : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            champPile = null;
        }
        try {
            Field f = Class.forName(PAQUET_TOUT).getDeclaredField("map");
            f.setAccessible(true);
            champReleves = Map.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            champReleves = null;
        }
    }

    private static boolean aCacher(@Nullable CompoundTag tag) {
        if (tag == null) return false;
        for (String k : CLES) {
            if (tag.contains(k)) return true;
        }
        return false;
    }

    /**
     * Copie de {@code pile} pour les autres joueurs, sans ces clés ; null si elle n'en a aucune, ou si le paquet de Curios
     * ne peut pas être changé (envoi inchangé). Les autres données ne sont pas recopiées mais partagées : la copie ne sert
     * qu'à l'envoi, mis en forme tout de suite sur ce fil.
     */
    @Nullable
    public static ItemStack pourLesAutres(ItemStack pile, Object paquet) {
        CompoundTag tag = pile.getTag();
        if (pile.isEmpty() || !aCacher(tag)) return null;
        chercherChamps();
        Field f = champPile;
        if (f == null || !f.getDeclaringClass().isInstance(paquet)) return null;
        CompoundTag leger = new CompoundTag();
        for (String k : tag.getAllKeys()) {
            if (!CLES.contains(k)) {
                Tag t = tag.get(k);
                if (t != null) leger.put(k, t);
            }
        }
        ItemStack copie = new ItemStack(pile.getItem(), pile.getCount());
        copie.setTag(leger.isEmpty() ? null : leger);
        return copie;
    }

    /** Met {@code pile} dans le paquet de Curios (déjà envoyé au porteur). Faux si impossible : il garde tout. */
    public static boolean remplacerPile(Object paquet, ItemStack pile) {
        Field f = champPile;
        if (f == null) return false;
        try {
            f.set(paquet, pile);
            CHANGEMENTS.increment();
            annoncer();
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Paquet de tous les emplacements d'un porteur, envoyé à un joueur qui commence à le voir (jamais au porteur) : clés
     * retirées sur place dans chaque relevé (relevés neufs, faits pour cet envoi par {@code getSyncTag}).
     */
    public static void allegerArrivee(Object paquet) {
        chercherChamps();
        Field f = champReleves;
        if (f == null || !f.getDeclaringClass().isInstance(paquet)) return;
        boolean change = false;
        try {
            if (f.get(paquet) instanceof Map<?, ?> releves) {
                for (Object r : releves.values()) {
                    if (r instanceof CompoundTag releve) change |= alleger(releve);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        if (change) {
            ARRIVEES.increment();
            annoncer();
        }
    }

    /** Un relevé d'un type d'emplacement (« Stacks » et « Cosmetics » : piles sauvées dans une liste « Items »). */
    private static boolean alleger(CompoundTag releve) {
        boolean change = false;
        for (String partie : new String[]{"Stacks", "Cosmetics"}) {
            if (!releve.contains(partie, Tag.TAG_COMPOUND)) continue;
            ListTag piles = releve.getCompound(partie).getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < piles.size(); i++) {
                CompoundTag pile = piles.getCompound(i);
                if (!pile.contains("tag", Tag.TAG_COMPOUND)) continue;
                CompoundTag tag = pile.getCompound("tag");
                for (String k : CLES) {
                    if (tag.contains(k)) {
                        tag.remove(k);
                        change = true;
                    }
                }
            }
        }
        return change;
    }

    private static void annoncer() {
        if (ANNONCE.compareAndSet(false, true)) {
            HeralizArmory.LOGGER.info("[armurerie] contenu des sacs : premier envoi allégé pour les autres joueurs");
        }
    }
}
