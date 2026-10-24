package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Recherche des sens de Zombie Awareness (sons, odeurs, points de horde), à la place de {@code ZAUtil.getSenseNearEntity}
 * (mixin serveur {@code ZombieAwarenessSensMixin}, jar de Corosus intact). Même résultat, beaucoup moins cher :
 * <ul>
 * <li>Zombie Awareness listait TOUTES les entités d'une boîte de 128 blocs de côté autour du zombie (objets au sol, autres
 * zombies, joueurs...), puis, 9 fois sur 10, jetait la liste (son tirage {@code findSense_PercentChance} = 10 %). Ici le
 * tirage est fait d'abord : 9 fois sur 10, rien n'est cherché ;</li>
 * <li>la recherche ne lit que ses entités de sens (EntityScent, rangées à part par Minecraft dans chaque section).</li>
 * </ul>
 * Appelé pour chaque zombie inoccupé toutes les 5 ticks (une fois sur trois). Classe, champs et réglages de Zombie Awareness
 * lus par réflexion une seule fois ; s'ils manquent (autre version du mod), on laisse faire Zombie Awareness.
 */
public final class SensAwareness {
    private SensAwareness() {
    }

    private static boolean pret, horsService;
    private static Class<? extends Entity> classeSens;
    private static Method portee;
    private static Field genre, chance, rayon;

    /** Point d'entrée du mixin : remplit cir (le mixin annule alors la méthode de Zombie Awareness) ou ne fait rien. */
    public static void chercher(Entity source, CallbackInfoReturnable<Object> cir) {
        try {
            if (!ZombiesConfig.IA_AWARENESS.get()) return;
        } catch (IllegalStateException ex) {
            return;
        }
        if (!pret) preparer();
        if (horsService) return;
        try {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            // tirage de Zombie Awareness (« rand.nextFloat() <= pourcent / 100 »), fait AVANT de chercher
            if (rnd.nextFloat() > chance.getInt(null) / 100.0F) {
                cir.setReturnValue(null);
                return;
            }
            int r = rayon.getInt(null);
            List<? extends Entity> sens = source.level().getEntitiesOfClass(classeSens, source.getBoundingBox().inflate(r, r, r));
            Entity meilleur = null;
            float meilleurePortee = 0.0F;
            double meilleureDistance = 9999.0;
            for (Entity e : sens) {
                float p = (Float) portee.invoke(e);
                double d = source.distanceTo(e);
                // mêmes règles que Zombie Awareness : le sens le plus fort à portée ; un point de horde (genre 2) une fois sur deux
                if (p > meilleurePortee && d < p && (genre.getInt(e) != 2 || rnd.nextInt(2) == 0)) {
                    meilleur = e;
                    meilleurePortee = p;
                    meilleureDistance = d;
                }
            }
            cir.setReturnValue(meilleureDistance <= 5.0 ? null : meilleur);   // déjà dessus : rien à rejoindre
        } catch (ReflectiveOperationException | RuntimeException ex) {
            horsService = true;
            HeraliszUtils.LOG.warn("[zombies] Zombie Awareness : recherche rapide des sens coupée, celle du mod reprend : {}", ex.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void preparer() {
        pret = true;
        try {
            Class<?> c = Class.forName("com.corosus.zombieawareness.EntityScent");
            classeSens = (Class<? extends Entity>) c.asSubclass(Entity.class);
            portee = c.getMethod("getRange");
            // le champ « type » du sens est écrit f_19847_ dans le jar (même nom que le type d'entité de Minecraft)
            genre = c.getDeclaredField("f_19847_");
            if (genre.getType() != int.class) throw new NoSuchFieldException("EntityScent.type");
            genre.setAccessible(true);
            Class<?> cfg = Class.forName("com.corosus.zombieawareness.config.ZAConfigGeneral");
            chance = cfg.getField("findSense_PercentChance");
            rayon = cfg.getField("maxPFRangeSense");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            horsService = true;
            HeraliszUtils.LOG.warn("[zombies] Zombie Awareness introuvable ou changé : recherche rapide des sens coupée ({})", ex.toString());
        }
    }
}
