package fr.heralisz.camo.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.config.client.ResourceConfig;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Préchauffage en fond des modèles des armes TaCZ (audit des images par seconde du 19/11, point 4).
 * <p>
 * TaCZ 1.1.8 ne lit le modèle d'une arme (jusqu'à 1,6 Mo de JSON) qu'à sa première demande de rendu, et ne préchauffe que
 * les armes du joueur lui-même : une arme tenue par un autre joueur, ou posée au sol, était lue sur le fil du rendu au
 * moment de l'image (gel de quelques dizaines à quelques centaines de ms, souvent au début d'un combat). En 3e personne,
 * TaCZ demande toujours d'abord la version allégée (LOD), puis le modèle complet si l'arme n'en a pas
 * ({@code GunItemRendererWrapper}). Ici, par l'API publique de TaCZ ({@code warmUpLod}, {@code warmUpModel} : lecture
 * dans son propre fil, « tacz-client-asset-preload »), jar intact :
 * <ul>
 *     <li>d'abord les armes tenues par les autres joueurs présents (relevé toutes les 10 ticks) ;</li>
 *     <li>puis toutes les armes, une à la fois : leur LOD (petit), et le modèle complet de celles qui n'en ont pas.</li>
 * </ul>
 * Une seule demande à la fois, la suivante quand elle est finie, et rien tant que l'arme en main du joueur n'est pas
 * prête : le fil de TaCZ reste libre pour ses propres préchauffages. Rien si le chargement à la demande de TaCZ est coupé
 * (EnableLazyClientAssetLoad, tacz-client.toml : ses warmUp chargeraient alors tout de suite, ici même). Mémoire : les
 * modèles lus restent jusqu'au rechargement des ressources (≈ 40 Mo de Java pour toutes les armes). Rien n'est créé à
 * chaque tick en dehors des relevés.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class PrechauffageArmes {
   /** Ticks entre deux relevés des armes des autres joueurs. */
   private static final int RELEVE = 10;
   /** Une demande qui ne finit pas (fil de TaCZ occupé, erreur sans drapeau) est laissée au bout de 20 s. */
   private static final int ABANDON = 400;
   @Nullable
   private static final Field LOD = champ("lodLoaded"), LOD_RATE = champ("lodLoadFailed"),
      MODELE = champ("modelLoaded"), MODELE_RATE = champ("modelLoadFailed");
   private static final boolean ACTIF = LOD != null && LOD_RATE != null && MODELE != null && MODELE_RATE != null;

   /** Toutes les armes (copie de ClientIndexManager.GUN_DISPLAY, refaite quand TaCZ recharge ses armes). */
   private static ResourceLocation[] cles = new ResourceLocation[0];
   private static GunDisplayInstance[] armes = new GunDisplayInstance[0];
   private static int suivante;
   @Nullable
   private static GunDisplayInstance enCours;
   private static boolean enCoursModele;
   private static int attente, tour;

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || !ACTIF) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         oublier();
         return;
      }
      if (!aLaDemande()) return;
      if (enCours != null) {
         if (!fini(enCours, enCoursModele) && ++attente < ABANDON) return;
         GunDisplayInstance d = enCours;
         boolean modele = enCoursModele;
         enCours = null;
         // LOD prêt et l'arme n'en a pas : c'est son modèle complet que TaCZ dessinera chez les autres
         if (!modele && sansLod(d) && !lu(MODELE, d) && !lu(MODELE_RATE, d)) {
            lancer(d, true);
            return;
         }
      }
      // l'arme en main du joueur passe avant tout (TaCZ la prépare lui-même dans le même fil)
      if (!pret(mc.player.getMainHandItem())) return;
      if (++tour % RELEVE == 0) {
         for (AbstractClientPlayer p : mc.level.players()) {
            if (p != mc.player && (demander(p.getMainHandItem()) || demander(p.getOffhandItem()))) return;
         }
         if (copiePerimee()) copier();
      }
      while (suivante < armes.length) {
         if (demander(armes[suivante++])) return;
      }
   }

   /** Vrai si une demande vient d'être faite pour cette arme. */
   private static boolean demander(ItemStack stack) {
      if (stack.isEmpty() || !(stack.getItem() instanceof IGun)) return false;
      GunDisplayInstance d = TimelessAPI.getGunDisplay(stack).orElse(null);
      return d != null && demander(d);
   }

   private static boolean demander(GunDisplayInstance d) {
      if (!lu(LOD, d)) {
         if (lu(LOD_RATE, d)) return false;
         lancer(d, false);
         return true;
      }
      if (sansLod(d) && !lu(MODELE, d) && !lu(MODELE_RATE, d)) {
         lancer(d, true);
         return true;
      }
      return false;
   }

   private static void lancer(GunDisplayInstance d, boolean modele) {
      if (modele) d.warmUpModel();
      else d.warmUpLod();
      enCours = d;
      enCoursModele = modele;
      attente = 0;
   }

   /** Arme en main du joueur prête (ou pas une arme TaCZ). */
   private static boolean pret(ItemStack stack) {
      if (stack.isEmpty() || !(stack.getItem() instanceof IGun)) return true;
      GunDisplayInstance d = TimelessAPI.getGunDisplay(stack).orElse(null);
      return d == null || lu(MODELE, d) || lu(MODELE_RATE, d);
   }

   /** LOD lu et absent ({@code getLodModel} ne charge rien une fois le LOD lu). */
   private static boolean sansLod(GunDisplayInstance d) {
      return lu(LOD, d) && d.getLodModel() == null;
   }

   private static boolean fini(GunDisplayInstance d, boolean modele) {
      return modele ? lu(MODELE, d) || lu(MODELE_RATE, d) : lu(LOD, d) || lu(LOD_RATE, d);
   }

   /** Liste des armes à refaire : TaCZ a rechargé ses armes (nouveaux objets) ou pas encore copiée. */
   private static boolean copiePerimee() {
      Map<ResourceLocation, GunDisplayInstance> m = ClientIndexManager.GUN_DISPLAY;
      return m.size() != armes.length || armes.length > 0 && m.get(cles[0]) != armes[0];
   }

   private static void copier() {
      Map<ResourceLocation, GunDisplayInstance> m = ClientIndexManager.GUN_DISPLAY;
      ResourceLocation[] c = new ResourceLocation[m.size()];
      GunDisplayInstance[] a = new GunDisplayInstance[c.length];
      int i = 0;
      for (Map.Entry<ResourceLocation, GunDisplayInstance> e : m.entrySet()) {
         if (i == c.length) break;
         c[i] = e.getKey();
         a[i++] = e.getValue();
      }
      cles = c;
      armes = a;
      suivante = 0;
   }

   private static void oublier() {
      enCours = null;
      cles = new ResourceLocation[0];
      armes = new GunDisplayInstance[0];
      suivante = 0;
   }

   /** Chargement à la demande de TaCZ (sinon ses warmUp chargent tout de suite, sur le fil qui les appelle). */
   private static boolean aLaDemande() {
      try {
         return ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD != null && ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get();
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** Drapeaux « chargé » / « en échec » de TaCZ (pas de méthode publique) ; illisibles : rien n'est préchauffé. */
   @Nullable
   private static Field champ(String nom) {
      try {
         Field f = GunDisplayInstance.class.getDeclaredField(nom);
         f.setAccessible(true);
         return f;
      } catch (ReflectiveOperationException | RuntimeException e) {
         return null;
      }
   }

   private static boolean lu(@Nullable Field champ, GunDisplayInstance d) {
      try {
         return champ != null && champ.getBoolean(d);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return false;
      }
   }

   /** Après un rechargement des ressources, TaCZ refait ses armes : la liste est refaite. */
   @Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Rechargement {
      private Rechargement() {}

      @SubscribeEvent
      public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener) rm -> oublier());
      }
   }

   private PrechauffageArmes() {}
}
