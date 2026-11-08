package fr.heraliz.armory.cible;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.Protection;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.network.PaquetsCible;
import fr.heraliz.armory.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Cible d'essai, côté serveur (03/11) : mesure des coups, écran, pose et ramassage.
 * <ul>
 *    <li><b>Mesure</b> : LivingHurtEvent en priorité la plus haute (dégâts du coup avant toute armure, après les réglages
 *    des armes et la tête), LivingDamageEvent en priorité la plus haute (ce qu'aurait perdu un joueur, après les armures de
 *    l'Armurerie et leur plafond), puis ce dernier est ANNULÉ : la cible ne perd jamais de vie, et aucun autre écouteur ne
 *    voit ce coup (statistiques et quêtes de clan « infliger N dégâts », minutes de jeu actif : rien à farmer). Aucun mort :
 *    ni points de saison, ni butin, ni compteur de kills.</li>
 *    <li><b>Balles de TaCZ</b> (si TaCZ est là) : EntityHurtByGunEvent.Pre en priorité la plus basse (après les réglages
 *    des armes : tête ou corps, plomb arrêté par le plafond d'un tir de fusil à pompe), puis .Post (fin de la balle : ses
 *    deux parts, normale et perce-armure, sont additionnées et envoyées au tireur).</li>
 *    <li><b>Pose</b> : seulement le staff (niveau 2) et le créatif, où ils veulent (03/11, choix du propriétaire).
 *    <b>Ramasser</b> : le staff et le créatif seulement (04/11 ; avant, aussi celui qui l'avait posée), bouton de
 *    l'écran ; l'objet est rendu.</li>
 * </ul>
 * Rien ne tourne à chaque tick : seulement à un coup sur une cible, ou à un clic. Fil du serveur.
 */
public final class CiblesServeur {
    private CiblesServeur() {}

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CiblesServeur::avantArmure);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CiblesServeur::apresArmure);
        PaquetsCible.RECEPTION_SERVEUR.brancher(CiblesServeur::action);
        if (ModList.get() != null && ModList.get().isLoaded("tacz")) BallesTacz.brancher();
    }

    // ------------------------------------------------------------------ mesure
    private static void avantArmure(LivingHurtEvent e) {
        if (e.getEntity() instanceof CibleEssaiServeur c && c.enCours != null) c.enCours.avant += Math.max(0.0F, e.getAmount());
    }

    private static void apresArmure(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof CibleEssaiServeur c)) return;
        if (c.enCours != null) c.enCours.recu += Math.max(0.0F, e.getAmount());
        e.setCanceled(true);   // jamais de vie perdue, et personne d'autre ne compte ce coup
    }

    /** Envoie un coup mesuré à son tireur (rien si rien n'est passé : temps d'invincibilité, coup refusé). */
    static void envoyer(CibleEssaiServeur c, CibleEssaiServeur.Coup coup) {
        ServerPlayer tireur = coup.tireur;
        if (coup.avant <= 0.0F && !coup.plafond) {
            journal("coup de {} sur la cible : aucun dégât mesuré (refusé par une protection ou temps d'invincibilité)",
                    tireur.getGameProfile().getName());
            return;
        }
        if (tireur.hasDisconnected()) return;
        journal("coup de {} sur la cible : {} avant armure, {} reçus{}, envoyé au tireur", tireur.getGameProfile().getName(),
                coup.avant, coup.recu, coup.plafond ? " (plafond du tir)" : "");
        float distance = (float) tireur.getEyePosition().distanceTo(coup.point);
        ArmoryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> tireur), new PaquetsCible.Touche(c.getId(), coup.point.x,
                coup.point.y, coup.point.z, coup.zone, coup.avant, coup.recu, distance, coup.nomArme, coup.tir, coup.plafond,
                c.tenue()));
    }

    // ------------------------------------------------------------------ écran
    /** Clic droit : écran du choix de l'armure, avec les protections calculées ici (vrai plafond, vrai multiplicateur). */
    static void ouvrir(ServerPlayer joueur, CibleEssaiServeur c) {
        List<Tenues.Tenue> toutes = Tenues.TOUTES;
        float[] corps = new float[toutes.size()], tete = new float[toutes.size()];
        float plafond = Protection.cap();
        double mult = ArmorConfig.PROTECTION_MULTIPLIER.get();
        for (int i = 0; i < toutes.size(); i++) {
            float b = 0, h = 0;
            for (PieceDef p : toutes.get(i).pieces()) {
                b += Protection.value(p, Protection.Kind.BULLET) * Protection.share(p, false);
                h += Protection.value(p, Protection.Kind.BULLET) * Protection.share(p, true);
            }
            corps[i] = Math.min(plafond, (float) (b * mult));
            tete[i] = Math.min(plafond, (float) (h * mult));
        }
        ArmoryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joueur),
                new PaquetsCible.Ouvrir(c.getId(), c.tenue(), peutRamasser(joueur, c), corps, tete));
    }

    private static void action(PaquetsCible.Recu r) {
        ServerPlayer joueur = r.joueur();
        PaquetsCible.Action a = r.action();
        if (joueur.isSpectator() || !(joueur.level().getEntity(a.entite()) instanceof CibleEssaiServeur c) || !c.isAlive()) return;
        if (joueur.distanceToSqr(c) > CibleEssaiServeur.PORTEE * CibleEssaiServeur.PORTEE) {
            dire(joueur, "message.heraliz_armory.cible.loin", "Trop loin de la cible.");
            return;
        }
        if (a.action() == PaquetsCible.CHOISIR) {
            c.choisir(a.tenue());
        } else if (a.action() == PaquetsCible.RAMASSER) {
            if (!peutRamasser(joueur, c)) {
                dire(joueur, "message.heraliz_armory.cible.pas_a_toi", "Seul le staff peut ramasser une cible d'essai.");
                return;
            }
            c.discard();
            c.level().playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!joueur.getAbilities().instabuild) joueur.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CIBLE.get()));
        }
    }

    /**
     * 04/11 (retour du propriétaire : « un joueur peut la récupérer en faisant clic droit, Ramasser ») : seulement le staff
     * (niveau 2) et le créatif. Avant, celui qui l'avait posée la reprenait aussi, même redevenu simple joueur (cible posée
     * en créatif puis op retiré, ou posée par un joueur sous la 1.1.0).
     */
    static boolean peutRamasser(ServerPlayer joueur, CibleEssaiServeur c) {
        return joueur.getAbilities().instabuild || joueur.hasPermissions(2);
    }

    /** Dernière ligne de chaque sorte (le texte du message sert de clé), en nanosecondes. */
    private static final java.util.Map<String, Long> DERNIERS = new java.util.HashMap<>();

    /**
     * Journal des essais (04/11, chiffres non vus par le propriétaire) : ce qu'est devenu un tir sur une cible (balle à
     * côté, coup refusé, dégâts envoyés au tireur). Au plus une ligne de chaque sorte toutes les 2 s (une balle à côté ne
     * cache pas les dégâts des plombs qui touchent).
     */
    public static void journal(String texte, Object... args) {
        long t = System.nanoTime();
        Long d = DERNIERS.get(texte);
        if (d != null && t - d < 2_000_000_000L) return;
        DERNIERS.put(texte, t);
        fr.heraliz.armory.HeralizArmory.LOGGER.info("[cible] " + texte, args);
    }

    private static void dire(ServerPlayer joueur, String cle, String francais, Object... args) {
        joueur.displayClientMessage(Component.translatableWithFallback(cle, francais, args).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ pose
    /**
     * Pose permise ? 03/11 (choix du propriétaire) : seulement le staff (niveau 2) et le créatif, où ils veulent ; un joueur
     * ne la pose nulle part (message dit au joueur).
     */
    public static boolean posePermise(ServerPlayer joueur, ServerLevel sl, BlockPos pos) {
        if (joueur.getAbilities().instabuild || joueur.hasPermissions(2)) return true;
        dire(joueur, "message.heraliz_armory.cible.staff", "Seul le staff peut poser une cible d'essai.");
        return false;
    }

    // ------------------------------------------------------------------ balles de TaCZ (chargé seulement si TaCZ est là)
    private static final class BallesTacz {
        static void brancher() {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityHurtByGunEvent.Pre.class, BallesTacz::avant);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityHurtByGunEvent.Post.class, BallesTacz::apres);
        }

        /** Après les réglages des armes (DegatsArmes) : tête ou corps, plomb arrêté par le plafond d'un tir. */
        private static void avant(EntityHurtByGunEvent.Pre e) {
            // 08/11 : tir à la tête d'un joueur, pour la couleur du petit chiffre de ses dégâts (ChiffresJoueurs)
            if (e.getLogicalSide() == LogicalSide.SERVER && !e.isCanceled() && e.isHeadShot()
                    && e.getHurtEntity() instanceof ServerPlayer victime) {
                ChiffresJoueurs.noterTete(victime, victime.level().getGameTime());
            }
            if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof CibleEssaiServeur c)
                    || !(e.getAttacker() instanceof ServerPlayer tireur)) return;
            long t = c.level().getGameTime();
            Entity projectile = e.getBullet();
            boolean tete = e.isHeadShot();
            CibleEssaiServeur.Coup b = new CibleEssaiServeur.Coup(tireur, t, tete ? PaquetsCible.TETE : PaquetsCible.CORPS,
                    c.pointBalle(t, tete), tireur.getMainHandItem().getHoverName(), tir(e, projectile, t), projectile);
            if (e.isCanceled()) {
                // plomb au-delà du plafond d'un tir de fusil à pompe : il ne fait rien (comme sur un joueur)
                b.plafond = true;
                c.balle = null;
                envoyer(c, b);
                return;
            }
            c.balle = b;
        }

        private static void apres(EntityHurtByGunEvent.Post e) {
            if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof CibleEssaiServeur c)) return;
            CibleEssaiServeur.Coup b = c.balle;
            if (b == null || b.projectile != e.getBullet()) return;
            c.balle = null;
            envoyer(c, b);
        }

        /** Départ du tir pour une arme à plombs (les plombs d'une décharge partent dans le même tick), -1 sinon. */
        private static long tir(EntityHurtByGunEvent.Pre e, @Nullable Entity projectile, long t) {
            if (projectile == null || e.getGunId() == null) return -1L;
            int plombs = TimelessAPI.getCommonGunIndex(e.getGunId()).map(i -> i.getBulletData().getBulletAmount()).orElse(1);
            return plombs > 1 ? t - projectile.tickCount : -1L;
        }
    }
}
