package fr.heraliz.armory.compat;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.api.ATerre;
import java.lang.reflect.Field;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Effets de geckolib222 (contenu ArkaniaZ, jar d'un autre auteur, jamais modifié), côté serveur (audit du 15/11).
 * <ul>
 *   <li>Combat M2 : jambe cassée ({@code geckolib:broken_bone}) et saignement ({@code geckolib:bleeding}) : notre message
 *   traduit dans la barre d'action, UNE fois, quand la blessure commence (effet nouveau, pas un effet renouvelé), avec les
 *   vrais objets qui soignent. Les phrases anglaises que geckolib222 répétait chaque seconde (jambe) ou toutes les 10 s
 *   (saignement) sont coupées par {@code fr.heraliz.armory.serveur.mixin.GeckolibMessagesMixin} : nos autres messages de
 *   la barre ne sont plus effacés. Rien pour un joueur à terre (il ne peut pas se soigner) ; en match, ces effets sont
 *   déjà refusés par le noyau.</li>
 *   <li>Combat B2 : l'irradiation ({@code IRRADIATED}) garde dans une liste privée et statique chaque entité irradiée (pour
 *   ne jouer le bip Geiger qu'une fois) et ne l'en retirait jamais : chaque joueur entré dans une zone radioactive restait
 *   en mémoire (inventaire, données...) jusqu'au redémarrage, une copie par vie et par connexion. Il en sort maintenant à
 *   sa mort et quand il quitte le monde (réapparition, déconnexion, créature retirée ou déchargée). Liste lue par
 *   réflexion, une fois ; introuvable (jar changé) : rien n'est fait, un avertissement dans le journal. Le bip sonne de
 *   nouveau au début de chaque vie, comme avant.</li>
 * </ul>
 * Fil du serveur ; rien ne tourne sans effet ajouté, sans mort ni départ (et rien si personne n'est irradié).
 */
public final class GeckolibServeur {
    private static final ResourceLocation JAMBE_CASSEE = new ResourceLocation("geckolib", "broken_bone");
    private static final ResourceLocation SAIGNEMENT = new ResourceLocation("geckolib", "bleeding");
    private static final Logger LOG = LogUtils.getLogger();
    private static final String IRRADIATION = "software.bernie.example.effect.IRRADIATED";
    /** La liste des entités irradiées de geckolib222 (null : introuvable). */
    @Nullable
    private static Set<?> irradies;
    private static boolean cherche;

    private GeckolibServeur() {
    }

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(GeckolibServeur::effetAjoute);
        MinecraftForge.EVENT_BUS.addListener(GeckolibServeur::mort);
        MinecraftForge.EVENT_BUS.addListener(GeckolibServeur::depart);
    }

    private static void effetAjoute(MobEffectEvent.Added e) {
        if (e.getOldEffectInstance() != null || !(e.getEntity() instanceof ServerPlayer p) || p.hasDisconnected() || ATerre.aTerre(p)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffectInstance().getEffect());
        if (JAMBE_CASSEE.equals(id)) {
            p.displayClientMessage(Component.translatableWithFallback("message.heraliz_armory.blessure.jambe",
                    "Jambe cassée : utilise une attelle."), true);
        } else if (SAIGNEMENT.equals(id)) {
            p.displayClientMessage(Component.translatableWithFallback("message.heraliz_armory.blessure.saignement",
                    "Tu saignes : utilise un bandage ou une trousse de soins."), true);
        }
    }

    // ------------------------------------------------------------------ irradiation (B2)
    private static void mort(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide()) oublier(e.getEntity());
    }

    private static void depart(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof LivingEntity) oublier(e.getEntity());
    }

    private static void oublier(Entity e) {
        Set<?> liste = irradies();
        if (liste != null && !liste.isEmpty()) liste.remove(e);
    }

    @Nullable
    private static Set<?> irradies() {
        if (!cherche) {
            cherche = true;
            try {
                Field f = Class.forName(IRRADIATION).getDeclaredField("entitiesWithEffect");
                f.setAccessible(true);
                irradies = f.get(null) instanceof Set<?> liste ? liste : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                irradies = null;
            }
            if (irradies == null) LOG.warn("[armurerie] liste des irradiés de geckolib222 introuvable : elle n'est pas vidée");
        }
        return irradies;
    }
}
