package fr.heralisz.matchs.duels;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.bombe.BombeDuel;
import fr.heralisz.matchs.duels.bombe.ChargeObjet;
import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registres propres aux duels : sons maison (prefixe {@code duel_} ; ceux de la bombe : outils/bombe/sons.py), objet
 * porte de recherche et destruction (l'ordinateur d'amorcage, {@code duel_charge}), bombe des sites et ordinateur pose
 * (entites GeckoLib, modeles et textures : outils/bombe/modeles.py) et cible d'entrainement.
 */
public final class Registres {
    private Registres() {
    }

    private static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszMatchs.MODID);
    private static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszMatchs.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszMatchs.MODID);

    public static final RegistryObject<SoundEvent> BIP = son("duel_bip");
    public static final RegistryObject<SoundEvent> POSE = son("duel_pose");
    public static final RegistryObject<SoundEvent> DESAMORCE = son("duel_desamorce");
    public static final RegistryObject<SoundEvent> EXPLOSION = son("duel_explosion");
    public static final RegistryObject<SoundEvent> MANCHE = son("duel_manche");
    public static final RegistryObject<SoundEvent> DEFI = son("duel_defi");
    public static final RegistryObject<SoundEvent> VICTOIRE = son("duel_victoire");
    public static final RegistryObject<SoundEvent> DEFAITE = son("duel_defaite");
    public static final RegistryObject<SoundEvent> RAMASSE = son("duel_ramasse");
    /** Frappe au clavier de l'ordinateur (amorcage) et bips de piratage : joues chez le joueur par OrdinateurDuel. */
    public static final RegistryObject<SoundEvent> CLAVIER = son("duel_clavier");
    public static final RegistryObject<SoundEvent> PIRATAGE = son("duel_piratage");

    /** Ids des sons pour {@code Annonces.Annonce#son}. */
    public static final String SON_MANCHE = HeraliszMatchs.MODID + ":duel_manche", SON_VICTOIRE = HeraliszMatchs.MODID + ":duel_victoire",
            SON_DEFAITE = HeraliszMatchs.MODID + ":duel_defaite", SON_EXPLOSION = HeraliszMatchs.MODID + ":duel_explosion",
            SON_DESAMORCE = HeraliszMatchs.MODID + ":duel_desamorce", SON_POSE = HeraliszMatchs.MODID + ":duel_pose";

    /** Ordinateur d'amorcage ferme, porte par un attaquant (objet prete du match, modele GeckoLib en main). */
    public static final RegistryObject<Item> CHARGE = OBJETS.register("duel_charge", ChargeObjet::new);

    /** Bombe d'un site (A ou B), posee par la partie pendant un duel de recherche et destruction. */
    public static final RegistryObject<EntityType<BombeDuel>> BOMBE = ENTITES.register("duel_bombe",
            () -> EntityType.Builder.<BombeDuel>of(BombeDuel::new, MobCategory.MISC).sized(0.9f, 1.0f).clientTrackingRange(8).updateInterval(20)
                    .noSummon().fireImmune().build(HeraliszMatchs.MODID + ":duel_bombe"));
    /** Ordinateur d'amorcage pose (au sol, en amorcage, amorce, pirate, desamorce). */
    public static final RegistryObject<EntityType<OrdinateurDuel>> ORDINATEUR = ENTITES.register("duel_ordinateur",
            () -> EntityType.Builder.<OrdinateurDuel>of(OrdinateurDuel::new, MobCategory.MISC).sized(0.7f, 0.4f).clientTrackingRange(8).updateInterval(2)
                    .noSummon().fireImmune().build(HeraliszMatchs.MODID + ":duel_ordinateur"));

    /** Cible d'entrainement (adversaire immobile de {@code /duel entrainement}). */
    /** Usine de la cible (docs/METHODE_CLIENT_SERVEUR.md §4) : version serveur branchée par la partie serveur. */
    public static final fr.heralisz.matchs.pont.UsineEntite<CibleEntrainement> CIBLE_USINE = new fr.heralisz.matchs.pont.UsineEntite<>(CibleEntrainement::new);
    public static final RegistryObject<EntityType<CibleEntrainement>> CIBLE = ENTITES.register("duel_cible",
            () -> EntityType.Builder.of(CIBLE_USINE, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10)
                    .build(HeraliszMatchs.MODID + ":duel_cible"));

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszMatchs.id(id)));
    }

    static void enregistrer(IEventBus modBus) {
        SONS.register(modBus);
        OBJETS.register(modBus);
        ENTITES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(CIBLE.get(), CibleEntrainement.attributs().build()));
    }
}
