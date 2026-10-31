package fr.heraliz.tactique.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import fr.heraliz.tactique.armes.DegatsJoueurs;
import fr.heraliz.tactique.armes.PaquetDegatsArmes;
import fr.heraliz.tactique.armes.ReseauArmes;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Dégâts des armes TaCZ contre les joueurs (équilibrage du 14/10, avec les armures de l'Armurerie 0.15.0), sans toucher
 * aux gunpacks des autres auteurs. Réglage par arme dans {@code config/heraliz_tactique/armes.json}, écrit par
 * {@code outils/equilibrage_armes.py} : « degats » = une balle à courte distance (arme à plombs : la décharge entière,
 * comme le « damage » des gunpacks : TaCZ 1.1.8 la partage lui-même entre les plombs), « tete » = multiplicateur d'un tir
 * à la tête.
 * <p>Sur chaque balle qui touche un joueur ({@code EntityHurtByGunEvent.Pre}, serveur) : les dégâts de TaCZ (baisse avec
 * la distance, accessoires et part de chaque plomb compris) sont multipliés par « degats » / dégâts de l'arme chargés par
 * TaCZ, pour garder la même proportion (31/10 : les fusils à pompe étaient divisés deux fois par le nombre de plombs,
 * une décharge entière faisait 4 au lieu de 36) ; une arme dont TaCZ donne 0 dégât (quelques fusils anti-matériel des gunpacks) prend
 * « degats » tel quel. Arme absente du fichier, créatures, fichier absent ou « actif » à false : rien ne change. Fichier
 * relu à chaque démarrage du serveur, et envoyé à chaque joueur qui se connecte (chiffres affichés chez lui).
 * <p>« plafond » (fusils à pompe, 24/10, choix du propriétaire : « pas de gros one tap mais utilisable ») : dégâts au plus
 * d'UN tir sur un même joueur, tous plombs additionnés, tête comprise, avant l'armure. Les plombs d'un même tir partent
 * dans le même tick : au-delà du plafond, les plombs suivants ne font rien (événement annulé). Réglage du serveur
 * seulement (pas envoyé aux joueurs).</p>
 */
final class DegatsArmes {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String FICHIER = "heraliz_tactique/armes.json";
   /** Tir oublié s'il n'a rien touché depuis 2 s (nettoyage de {@link #TIRS}). */
   private static final long OUBLI = 40L;
   /** Plafond des dégâts d'un tir par arme (fusils à pompe). */
   private static volatile Map<ResourceLocation, Float> plafonds = Map.of();
   /**
    * Dégâts déjà faits par le tir en cours : tireur + cible -> {tick de départ du tir, dégâts, tick du dernier plomb}. En
    * double (pas en float : le temps du monde dépasse vite 16 millions de ticks, un float l'arrondissait de plusieurs ticks
    * et deux tirs pouvaient se fondre).
    */
   private static final Map<String, double[]> TIRS = new HashMap<>();

   private DegatsArmes() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(DegatsArmes::demarrage);
      MinecraftForge.EVENT_BUS.addListener(DegatsArmes::touche);
      MinecraftForge.EVENT_BUS.addListener(DegatsArmes::connexion);
   }

   private static void demarrage(ServerAboutToStartEvent e) {
      charger();
   }

   /** Les réglages vont aussi au joueur qui se connecte (chiffres de l'infobulle et de l'Atelier d'arme). */
   private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer joueur) ReseauArmes.envoyer(joueur, new PaquetDegatsArmes(DegatsJoueurs.tous()));
   }

   static void charger() {
      Path p = FMLPaths.CONFIGDIR.get().resolve(FICHIER);
      if (!Files.isRegularFile(p)) {
         DegatsJoueurs.remplacer(Map.of());
         plafonds = Map.of();
         LOG.info("[armes] {} absent : dégâts de TaCZ contre les joueurs inchangés", FICHIER);
         return;
      }
      Map<ResourceLocation, DegatsJoueurs.Reglage> lus = new HashMap<>();
      Map<ResourceLocation, Float> plafondsLus = new HashMap<>();
      plafonds = Map.of();
      try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
         JsonObject racine = JsonParser.parseReader(r).getAsJsonObject();
         if (racine.has("actif") && !racine.get("actif").getAsBoolean()) {
            DegatsJoueurs.remplacer(Map.of());
            LOG.info("[armes] {} : « actif » à false, dégâts de TaCZ contre les joueurs inchangés", FICHIER);
            return;
         }
         for (Map.Entry<String, JsonElement> e : racine.getAsJsonObject("armes").entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            JsonObject o = e.getValue().getAsJsonObject();
            if (id == null || !o.has("degats")) continue;
            float degats = o.get("degats").getAsFloat();
            float tete = o.has("tete") ? o.get("tete").getAsFloat() : -1f;
            if (degats > 0) lus.put(id, new DegatsJoueurs.Reglage(degats, tete));
            if (degats > 0 && o.has("plafond") && o.get("plafond").getAsFloat() > 0) plafondsLus.put(id, o.get("plafond").getAsFloat());
         }
         DegatsJoueurs.remplacer(lus);
         plafonds = Map.copyOf(plafondsLus);
         LOG.info("[armes] {} armes réglées contre les joueurs, {} avec un plafond par tir ({})", lus.size(), plafondsLus.size(), FICHIER);
      } catch (Exception ex) {
         DegatsJoueurs.remplacer(Map.of());
         LOG.error("[armes] {} illisible : dégâts de TaCZ contre les joueurs inchangés", FICHIER, ex);
      }
   }

   private static void touche(EntityHurtByGunEvent.Pre e) {
      if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof Player)) return;
      ResourceLocation arme = e.getGunId();
      if (arme == null) return;
      DegatsJoueurs.Reglage r = DegatsJoueurs.de(arme);
      if (r == null) return;
      float origine = TimelessAPI.getCommonGunIndex(arme).map(CommonGunIndex::getBulletData).map(b -> b.getDamageAmount()).orElse(-1f);
      if (origine > 0.01f) {
         e.setBaseAmount(e.getBaseAmount() * r.degats() / origine);
      } else if (origine >= 0f) {
         e.setBaseAmount(r.degats());
      }
      if (e.isHeadShot() && r.tete() > 0) e.setHeadshotMultiplier(r.tete());
      Float plafond = plafonds.get(arme);
      if (plafond != null && e.getAttacker() != null) plafonner(e, plafond);
   }

   /**
    * Plafond d'un tir (tous ses plombs) sur un même joueur. 31/10 : le tir est reconnu à l'heure où ses plombs sont
    * PARTIS (heure du monde moins l'âge du plomb : tous les plombs d'une décharge partent dans le même tick), et non plus
    * à l'heure où ils touchent : avant, deux tirs à moins de 2 ticks d'écart (AA-12 en automatique, 450 coups par minute,
    * soit un tir toutes les 2,7 ticks ; Saiga 12 à 600) partageaient un seul plafond et un tir sur trois ne faisait rien.
    * Une rafale (mode BURST : les deux canons du double canon, Stakeout) compte comme un seul tir : sinon les deux canons
    * ensemble tueraient d'un coup sans armure.
    */
   private static void plafonner(EntityHurtByGunEvent.Pre e, float plafond) {
      long tick = e.getHurtEntity().level().getGameTime();
      long depart = e.getBullet() != null ? tick - e.getBullet().tickCount : tick;
      long fenetre = fenetre(e.getAttacker(), e.getGunId());
      String cle = e.getAttacker().getUUID() + ">" + e.getHurtEntity().getUUID();
      float mult = e.isHeadShot() ? Math.max(1f, e.getHeadshotMultiplier()) : 1f;
      float coup = e.getBaseAmount() * mult;
      synchronized (TIRS) {
         if (TIRS.size() > 512) TIRS.values().removeIf(t -> tick - (long) t[2] > OUBLI);
         double[] tir = TIRS.get(cle);
         if (tir == null || Math.abs(depart - (long) tir[0]) > fenetre) {
            tir = new double[] {depart, 0d, tick};
            TIRS.put(cle, tir);
         }
         tir[2] = tick;
         float reste = (float) (plafond - tir[1]);
         if (reste <= 0.01f) {
            e.setCanceled(true);
            return;
         }
         if (coup > reste) {
            e.setBaseAmount(reste / mult);
            coup = reste;
         }
         tir[1] += coup;
      }
   }

   /**
    * Écart (en ticks, entre les départs) jusqu'où deux plombs sont du même tir : 1 (même tick, avec une marge d'un tick ;
    * aucun fusil à pompe ne tire 1 200 coups par minute), ou toute la rafale si l'arme en main du tireur est en mode BURST.
    */
   private static long fenetre(LivingEntity tireur, ResourceLocation arme) {
      if (IGun.getMainHandFireMode(tireur) != FireMode.BURST) return 1L;
      BurstData rafale = TimelessAPI.getCommonGunIndex(arme).map(CommonGunIndex::getGunData).map(GunData::getBurstData).orElse(null);
      if (rafale == null || rafale.getCount() <= 1 || rafale.getBpm() <= 0) return 1L;
      return 1L + (long) Math.ceil((rafale.getCount() - 1) * 1200.0 / rafale.getBpm());
   }
}
