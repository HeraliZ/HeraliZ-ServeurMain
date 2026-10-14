package fr.heraliz.tactique.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.resource.index.CommonGunIndex;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Dégâts des armes TaCZ contre les joueurs (équilibrage du 14/10, avec les armures de l'Armurerie 0.15.0), sans toucher
 * aux gunpacks des autres auteurs. Réglage par arme dans {@code config/heraliz_tactique/armes.json}, écrit par
 * {@code outils/equilibrage_armes.py} : « degats » = une balle (un plomb) à courte distance, « tete » = multiplicateur
 * d'un tir à la tête.
 * <p>Sur chaque balle qui touche un joueur ({@code EntityHurtByGunEvent.Pre}, serveur) : les dégâts de TaCZ (baisse avec
 * la distance et accessoires compris) sont multipliés par « degats » / dégâts d'une balle de l'arme chargée par TaCZ, pour
 * garder la même proportion ; une arme dont TaCZ donne 0 dégât (quelques fusils anti-matériel des gunpacks) prend
 * « degats » tel quel. Arme absente du fichier, créatures, fichier absent ou « actif » à false : rien ne change. Fichier
 * relu à chaque démarrage du serveur.
 */
final class DegatsArmes {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String FICHIER = "heraliz_tactique/armes.json";

   private record Reglage(float degats, float tete) {
   }

   private static volatile Map<ResourceLocation, Reglage> reglages = Map.of();

   private DegatsArmes() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(DegatsArmes::demarrage);
      MinecraftForge.EVENT_BUS.addListener(DegatsArmes::touche);
   }

   private static void demarrage(ServerAboutToStartEvent e) {
      charger();
   }

   static void charger() {
      Path p = FMLPaths.CONFIGDIR.get().resolve(FICHIER);
      if (!Files.isRegularFile(p)) {
         reglages = Map.of();
         LOG.info("[armes] {} absent : dégâts de TaCZ contre les joueurs inchangés", FICHIER);
         return;
      }
      Map<ResourceLocation, Reglage> lus = new HashMap<>();
      try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
         JsonObject racine = JsonParser.parseReader(r).getAsJsonObject();
         if (racine.has("actif") && !racine.get("actif").getAsBoolean()) {
            reglages = Map.of();
            LOG.info("[armes] {} : « actif » à false, dégâts de TaCZ contre les joueurs inchangés", FICHIER);
            return;
         }
         for (Map.Entry<String, JsonElement> e : racine.getAsJsonObject("armes").entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            JsonObject o = e.getValue().getAsJsonObject();
            if (id == null || !o.has("degats")) continue;
            float degats = o.get("degats").getAsFloat();
            float tete = o.has("tete") ? o.get("tete").getAsFloat() : -1f;
            if (degats > 0) lus.put(id, new Reglage(degats, tete));
         }
         reglages = Map.copyOf(lus);
         LOG.info("[armes] {} armes réglées contre les joueurs ({})", lus.size(), FICHIER);
      } catch (Exception ex) {
         reglages = Map.of();
         LOG.error("[armes] {} illisible : dégâts de TaCZ contre les joueurs inchangés", FICHIER, ex);
      }
   }

   private static void touche(EntityHurtByGunEvent.Pre e) {
      if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof Player)) return;
      ResourceLocation arme = e.getGunId();
      if (arme == null) return;
      Reglage r = reglages.get(arme);
      if (r == null) return;
      float origine = TimelessAPI.getCommonGunIndex(arme).map(CommonGunIndex::getBulletData).map(b -> b.getDamageAmount()).orElse(-1f);
      if (origine > 0.01f) {
         e.setBaseAmount(e.getBaseAmount() * r.degats() / origine);
      } else if (origine >= 0f) {
         e.setBaseAmount(r.degats());
      }
      if (e.isHeadShot() && r.tete() > 0) e.setHeadshotMultiplier(r.tete());
   }
}
