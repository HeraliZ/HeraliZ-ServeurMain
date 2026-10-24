package fr.heralisz.utils.bots;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ce que « fait » un bot, tick par tick, avec les mêmes gestes qu'un joueur : marcher vers un point, courir, sauter
 * devant un obstacle, chercher un ennemi en vue (autre bot, monstre, et vrai joueur seulement si le staff le permet),
 * s'arrêter de courir pour tirer, viser avec un peu d'erreur, tirer par l'API de TaCZ (cadence, munitions, rechargement
 * : ceux de l'arme), se décaler sur le côté, lancer une grenade ou une flash (objet tenu, lancer relâché comme le clic
 * droit d'un joueur), un fumigène quand il est blessé, se soigner, ouvrir les conteneurs de loot proches, appeler des
 * frappes aériennes et des largages (comme le désignateur d'un joueur). Chaque action peut être coupée par
 * {@code /bots action}. Fil serveur.
 */
final class Cerveau {
    // cases de la barre d'objets du kit (Kit)
    static final int CASE_ARME = 0, CASE_GRENADE = 1, CASE_FLASH = 2, CASE_FUMI = 3, CASE_SOIN = 4, CASE_VIDE = 7;

    private final BotJoueur bot;
    private final RandomSource r;
    @Nullable
    private LivingEntity cible;
    @Nullable
    private Vec3 but;
    private Vec3 dernierePos = Vec3.ZERO;
    private int tick, immobile, cote, coteTicks, regard;
    // actions en cours (ticks restants) et délais avant la prochaine (ticks)
    private int lancer, lancerCase = -1, soin, fouille;
    private int prochGrenade, prochFumi, prochSoin, prochFouille, prochFrappe, prochLargage, prochRecharge;
    private boolean dessine;
    private int aTerreTicks;

    private static boolean reaCherche;
    @Nullable
    private static java.lang.reflect.Method estATerre;

    private boolean aTerre() {
        if (!reaCherche) {
            reaCherche = true;
            try {
                estATerre = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", net.minecraft.world.entity.Entity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                estATerre = null;
            }
        }
        if (estATerre == null) return false;
        try {
            return Boolean.TRUE.equals(estATerre.invoke(null, bot));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    Cerveau(BotJoueur bot) {
        this.bot = bot;
        this.r = RandomSource.create();
        // délais de départ décalés : les bots ne font pas tous la même chose au même tick
        prochGrenade = 200 + r.nextInt(600);
        prochFumi = 100 + r.nextInt(400);
        prochFouille = 200 + r.nextInt(1200);
        prochFrappe = Bots.reglages.frappeMin + r.nextInt(Math.max(1, Bots.reglages.frappeMax - Bots.reglages.frappeMin));
        prochLargage = Bots.reglages.largageMin + r.nextInt(Math.max(1, Bots.reglages.largageMax - Bots.reglages.largageMin));
    }

    void tick() {
        tick++;
        decompter();
        ServerLevel lv = bot.serverLevel();
        bot.setShiftKeyDown(false);
        bot.zza = 0;
        bot.xxa = 0;
        bot.setJumping(false);
        // à terre (Réanimation) : ne fait plus rien ; personne ne le relève : achevé au bout de 30 s
        if (aTerre()) {
            bot.setSprinting(false);
            if (aTerreTicks == 0) bot.getInventory().clearContent();   // rien à prendre en le fouillant à terre
            if (++aTerreTicks > 600) bot.hurt(bot.damageSources().genericKill(), Float.MAX_VALUE);
            return;
        }
        aTerreTicks = 0;

        if (!dessine) {
            tenir(CASE_ARME);
            dessine = true;
        }
        // action longue en cours : lancer d'un objet ou soin (le joueur reste sur place, l'objet en main)
        if (lancer > 0) {
            if (--lancer == 0) finirLancer();
            regarderCible();
            return;
        }
        if (soin > 0) {
            if (--soin == 0 || !bot.isUsingItem()) finirSoin();
            return;
        }
        if (fouille > 0) {
            if (--fouille == 0) {
                bot.closeContainer();
                tenir(CASE_ARME);
            }
            return;
        }

        if (tick % 10 == 0) choisirCible(lv);
        if (cible != null && (!cible.isAlive() || cible.isRemoved() || cible.distanceToSqr(bot) > 56 * 56)) cible = null;

        Reglages rg = Bots.reglages;
        if (rg.soins && prochSoin <= 0 && bot.getHealth() < bot.getMaxHealth() * 0.45f && commencerSoin()) return;
        if (cible != null) {
            double d = Math.sqrt(cible.distanceToSqr(bot));
            if (rg.fumigenes && prochFumi <= 0 && bot.getHealth() < bot.getMaxHealth() * 0.6f && commencerLancer(CASE_FUMI, d)) {
                prochFumi = 600 + r.nextInt(600);
                return;
            }
            if (rg.grenades && prochGrenade <= 0 && d > 8 && d < 28 && commencerLancer(r.nextFloat() < 0.65f ? CASE_GRENADE : CASE_FLASH, d)) {
                prochGrenade = 400 + r.nextInt(500);
                return;
            }
            combattre(d);
        } else {
            errer(lv);
            if (rg.fouille && prochFouille <= 0) {
                prochFouille = 900 + r.nextInt(1500);
                if (fouiller(lv)) return;
            }
        }
        if (rg.frappes && prochFrappe <= 0) {
            prochFrappe = rg.frappeMin + r.nextInt(Math.max(1, rg.frappeMax - rg.frappeMin));
            Vec3 c = cible != null ? cible.position() : bot.position().add(bot.getLookAngle().scale(40 + r.nextInt(30)));
            Bots.appelerFrappe(bot, c, false);
        }
        if (rg.largages && prochLargage <= 0) {
            prochLargage = rg.largageMin + r.nextInt(Math.max(1, rg.largageMax - rg.largageMin));
            Bots.appelerFrappe(bot, bot.position().add(r.nextInt(30) - 15, 0, r.nextInt(30) - 15), true);
        }
        debloquer();
    }

    private void decompter() {
        prochGrenade--;
        prochFumi--;
        prochSoin--;
        prochFouille--;
        prochFrappe--;
        prochLargage--;
        prochRecharge--;
    }

    // ------------------------------------------------------------------ cible

    private void choisirCible(ServerLevel lv) {
        if (cible != null && cible.isAlive() && bot.hasLineOfSight(cible) && r.nextFloat() < 0.8f) return;
        boolean joueurs = Bots.reglages.joueurs;
        List<LivingEntity> l = lv.getEntitiesOfClass(LivingEntity.class, new AABB(bot.blockPosition()).inflate(48),
                e -> e != bot && e.isAlive() && !e.isSpectator() && valide(e, joueurs));
        // la plus proche en vue : rangées par distance, la vue (un lancer de rayon) n'est testée que jusqu'à la première
        // visible (avant : un rayon par créature plus proche que la meilleure, dans l'ordre de la liste)
        LivingEntity mieux = null;
        if (!l.isEmpty()) {
            l.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(bot)));
            for (LivingEntity e : l) {
                if (bot.hasLineOfSight(e)) {
                    mieux = e;
                    break;
                }
            }
        }
        cible = mieux;
    }

    private static boolean valide(LivingEntity e, boolean joueurs) {
        if (e.getTags().contains(Bots.TAG)) return true;
        if (e instanceof Player p) return joueurs && !p.isCreative();
        return e instanceof Enemy;
    }

    // ------------------------------------------------------------------ combat

    private void combattre(double d) {
        LivingEntity c = cible;
        if (c == null) return;
        bot.setSprinting(false);
        regarderCible();
        // se décale sur le côté, avance un peu de loin, recule un peu de très près
        if (--coteTicks <= 0) {
            coteTicks = 15 + r.nextInt(35);
            cote = r.nextInt(3) - 1;
        }
        bot.xxa = cote;
        bot.zza = d > 25 ? 0.6f : d < 6 ? -0.5f : 0;
        if (bot.horizontalCollision && bot.onGround()) bot.setJumping(true);
        if (!Bots.reglages.tir) return;
        IGunOperator op = IGunOperator.fromLivingEntity(bot);
        if (bot.getInventory().selected != CASE_ARME) tenir(CASE_ARME);
        float[] a = angles(c, d);
        ShootResult res = op.shoot(() -> a[1], () -> a[0]);
        if (res == ShootResult.SUCCESS) {
            Bots.stats.tirs++;
        } else if (res == ShootResult.NO_AMMO && prochRecharge <= 0) {
            op.reload();
            prochRecharge = 40;
        } else if (res == ShootResult.NOT_DRAW) {
            op.draw(bot::getMainHandItem);
        } else if (res == ShootResult.NEED_BOLT) {
            op.bolt();
        }
    }

    /** Lacet et tangage vers la cible, avec une erreur de visée comme un joueur (plus grande de loin et en bougeant). */
    private float[] angles(LivingEntity c, double d) {
        double dx = c.getX() - bot.getX(), dy = c.getY() + c.getBbHeight() * 0.7 - bot.getEyeY(), dz = c.getZ() - bot.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        float erreur = (float) (1.2 + d * 0.05 + (cote != 0 ? 0.8 : 0));
        float lacet = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f) + (float) r.nextGaussian() * erreur;
        float tangage = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG) + (float) r.nextGaussian() * erreur * 0.6f;
        return new float[]{lacet, tangage};
    }

    private void regarderCible() {
        if (cible == null) return;
        double dx = cible.getX() - bot.getX(), dy = cible.getEyeY() - bot.getEyeY(), dz = cible.getZ() - bot.getZ();
        orienter(Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f),
                (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
    }

    private void orienter(float lacet, float tangage) {
        bot.setYRot(lacet);
        bot.setXRot(Mth.clamp(tangage, -89, 89));
        bot.setYHeadRot(lacet);
        bot.yBodyRot = lacet;
    }

    // ------------------------------------------------------------------ objets lancés et soins

    /** Prend la grenade, la flash ou le fumigène et commence le lancer (clic droit tenu), visée en cloche. */
    private boolean commencerLancer(int caseObjet, double d) {
        ItemStack st = bot.getInventory().getItem(caseObjet);
        if (st.isEmpty()) return false;
        tenir(caseObjet);
        regarderCible();
        bot.setXRot(Mth.clamp(bot.getXRot() - (float) (8 + d * 0.9), -60, 89));
        bot.gameMode.useItem(bot, bot.serverLevel(), bot.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (!bot.isUsingItem()) {
            tenir(CASE_ARME);
            return false;
        }
        lancer = 22 + r.nextInt(10);
        lancerCase = caseObjet;
        return true;
    }

    private void finirLancer() {
        if (bot.isUsingItem()) {
            bot.setXRot(Mth.clamp(bot.getXRot(), -60, 89));
            bot.releaseUsingItem();
            Bots.stats.lancers++;
        }
        lancerCase = -1;
        tenir(CASE_ARME);
    }

    private boolean commencerSoin() {
        prochSoin = 200 + r.nextInt(200);
        if (bot.getInventory().getItem(CASE_SOIN).isEmpty()) return false;
        tenir(CASE_SOIN);
        bot.gameMode.useItem(bot, bot.serverLevel(), bot.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (!bot.isUsingItem()) {
            tenir(CASE_ARME);
            return false;
        }
        soin = 200;
        Bots.stats.soins++;
        return true;
    }

    private void finirSoin() {
        if (bot.isUsingItem()) bot.stopUsingItem();
        soin = 0;
        tenir(CASE_ARME);
    }

    /** Change d'objet en main (comme la molette) ; l'arme est « sortie » par TaCZ. */
    private void tenir(int caseBarre) {
        if (bot.getInventory().selected != caseBarre) bot.getInventory().selected = caseBarre;
        if (caseBarre == CASE_ARME) IGunOperator.fromLivingEntity(bot).draw(bot::getMainHandItem);
    }

    // ------------------------------------------------------------------ déplacement

    private void errer(ServerLevel lv) {
        if (but == null || bot.position().distanceToSqr(but) < 4 || r.nextInt(600) == 0) but = nouveauBut(lv);
        if (but == null) return;
        double dx = but.x - bot.getX(), dz = but.z - bot.getZ();
        float lacet = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
        // tourne la tête petit à petit, comme une souris
        float l = Mth.approachDegrees(bot.getYRot(), lacet, 12f);
        if (--regard <= 0) regard = 20 + r.nextInt(60);
        orienter(l, regard > 40 ? 8f : 0f);
        bot.zza = 1f;
        boolean courir = Bots.reglages.course && bot.getFoodData().getFoodLevel() > 6 && Math.sqrt(dx * dx + dz * dz) > 12;
        bot.setSprinting(courir);
        if ((bot.horizontalCollision && bot.onGround()) || bot.isInWater()) bot.setJumping(true);
    }

    @Nullable
    private Vec3 nouveauBut(ServerLevel lv) {
        Vec3 centre = Bots.centre(bot);
        int rayon = Bots.rayon(bot);
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 8 + r.nextDouble() * rayon;
            int x = Mth.floor(centre.x + Math.cos(a) * rr), z = Mth.floor(centre.z + Math.sin(a) * rr);
            if (!lv.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!lv.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /** Coincé (aucun progrès depuis 3 s) : saute, se décale, change de but. */
    private void debloquer() {
        if (tick % 60 != 0) return;
        if (bot.position().distanceToSqr(dernierePos) < 1 && bot.zza > 0) {
            if (++immobile >= 2) {
                but = null;
                immobile = 0;
            }
            bot.setJumping(true);
        } else {
            immobile = 0;
        }
        dernierePos = bot.position();
    }

    // ------------------------------------------------------------------ loot

    /** Ouvre un conteneur de HeraliZ Loot à portée (clic droit main vide), le referme 2 s après. */
    private boolean fouiller(ServerLevel lv) {
        BlockPos o = bot.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-4, -2, -4), o.offset(4, 2, 4))) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(lv.getBlockState(p).getBlock());
            if (id == null || !"heraliz_loot".equals(id.getNamespace())) continue;
            BlockPos pos = p.immutable();
            tenir(CASE_VIDE);
            Vec3 centre = Vec3.atCenterOf(pos);
            double dx = centre.x - bot.getX(), dy = centre.y - bot.getEyeY(), dz = centre.z - bot.getZ();
            orienter(Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f),
                    (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
            bot.gameMode.useItemOn(bot, lv, bot.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(centre, Direction.UP, pos, false));
            fouille = 40;
            Bots.stats.fouilles++;
            return true;
        }
        return false;
    }
}
