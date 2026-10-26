package fr.heraliz.tactique.armurier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Partie serveur du Registre de l'armurier : changements faits en jeu par les op sur les recettes de la Table d'armurier
 * (recettes TaCZ {@code tacz:gun_smith_table_crafting}), gardés à la racine du serveur dans
 * {@code heraliz_tactique/recettes_jeu.json} (hors de {@code config/} : jamais écrasé par la mise en ligne).
 * <p>
 * TaCZ range ses recettes de table dans le gestionnaire de recettes de Minecraft (lu dans les data des gunpacks). À chaque
 * lecture des données (démarrage, /reload), une fois que TaCZ a préparé ses recettes (TagsUpdatedEvent, priorité la plus
 * basse), les recettes d'origine sont relevées puis les changements posés par-dessus ({@code replaceRecipes}) : recette
 * modifiée ou ajoutée = construite par le lecteur de TaCZ à partir du même JSON qu'un fichier de gunpack ; supprimée =
 * retirée. Minecraft envoie ensuite les recettes aux joueurs comme d'habitude ; après un changement en jeu, elles sont
 * renvoyées tout de suite à tous les joueurs ({@code ClientboundUpdateRecipesPacket}, comme un /reload).
 */
public final class ArmurierServeur {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final String FICHIER = "recettes_jeu.json";
   private static final int QTE_MAX_RESULTAT = 64, QTE_MAX_ENTREE = 999;

   /** Changements en jeu, par identifiant de recette : {"etat": "modifiee"|"ajoutee"|"supprimee", "recette": {...}}. */
   private static final Map<String, JsonObject> MODIFS = new LinkedHashMap<>();
   /** Recettes de la table telles que lues dans les fichiers (avant les changements), relevées à chaque lecture. */
   private static final Map<ResourceLocation, GunSmithTableRecipe> ORIGINES = new LinkedHashMap<>();
   private static RecipeManager gestionnaire;
   private static boolean charge;

   private ArmurierServeur() {
   }

   public static void brancher() {
      Armurier.OUVRIR_SERVEUR.brancher(ArmurierServeur::ouvrir);
      Armurier.MODIF_SERVEUR.brancher(r -> modif(r.joueur(), r.paquet()));
      MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> gestionnaire = e.getServerResources().getRecipeManager());
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TagsUpdatedEvent.class, ArmurierServeur::donneesLues);
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commandes(e.getDispatcher()));
   }

   // ------------------------------------------------------------------ fichier
   private static Path fichier() {
      return FMLPaths.GAMEDIR.get().resolve("heraliz_tactique").resolve(FICHIER);
   }

   private static void lire() {
      if (charge) return;
      charge = true;
      MODIFS.clear();
      Path f = fichier();
      if (!Files.exists(f)) return;
      try {
         JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
         for (Map.Entry<String, JsonElement> e : o.entrySet()) if (e.getValue().isJsonObject()) MODIFS.put(e.getKey(), e.getValue().getAsJsonObject());
      } catch (Exception ex) {
         // illisible : jamais écrasé, mis de côté
         LOG.error("[heraliz_tactique] {} illisible, mis de côté : {}", f, ex.getMessage());
         try {
            Files.move(f, f.resolveSibling(FICHIER + ".illisible-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException ignore) {
         }
      }
   }

   private static void sauver() {
      Path f = fichier();
      JsonObject o = new JsonObject();
      MODIFS.forEach(o::add);
      try {
         Files.createDirectories(f.getParent());
         Path tmp = f.resolveSibling(FICHIER + ".tmp");
         Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
         Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException ex) {
         LOG.error("[heraliz_tactique] écriture de {} impossible : {}", f, ex.getMessage());
      }
   }

   // ------------------------------------------------------------------ application
   private static void donneesLues(TagsUpdatedEvent e) {
      if (e.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || gestionnaire == null) return;
      lire();
      ORIGINES.clear();
      for (GunSmithTableRecipe r : gestionnaire.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) ORIGINES.put(r.getId(), r);
      appliquer();
   }

   /** Recettes d'origine + changements en jeu, posés dans le gestionnaire de recettes du serveur. */
   private static void appliquer() {
      if (gestionnaire == null) return;
      List<Recipe<?>> toutes = new ArrayList<>();
      for (Recipe<?> r : gestionnaire.getRecipes()) {
         if (!(r instanceof GunSmithTableRecipe)) toutes.add(r);
      }
      for (Map.Entry<ResourceLocation, GunSmithTableRecipe> e : ORIGINES.entrySet()) {
         if (!MODIFS.containsKey(e.getKey().toString())) toutes.add(e.getValue());
      }
      int poses = 0;
      for (Map.Entry<String, JsonObject> e : MODIFS.entrySet()) {
         JsonObject m = e.getValue();
         if ("supprimee".equals(etat(m)) || !m.has("recette")) continue;
         try {
            toutes.add(construire(new ResourceLocation(e.getKey()), m.getAsJsonObject("recette")));
            poses++;
         } catch (Exception ex) {
            LOG.error("[heraliz_tactique] recette {} du Registre de l'armurier illisible, ignorée : {}", e.getKey(), ex.getMessage());
            // l'originale reste en place si elle existe
            GunSmithTableRecipe o = ORIGINES.get(new ResourceLocation(e.getKey()));
            if (o != null) toutes.add(o);
         }
      }
      gestionnaire.replaceRecipes(toutes);
      if (!MODIFS.isEmpty()) LOG.info("[heraliz_tactique] Registre de l'armurier : {} changement(s), {} recette(s) posée(s)", MODIFS.size(), poses);
   }

   private static GunSmithTableRecipe construire(ResourceLocation id, JsonObject json) {
      GunSmithTableRecipe r = (GunSmithTableRecipe) ModRecipe.GUN_SMITH_TABLE_RECIPE_SERIALIZER.get().fromJson(id, json);
      r.init();
      return r;
   }

   private static String etat(JsonObject m) {
      return m.has("etat") ? m.get("etat").getAsString() : "modifiee";
   }

   /** Renvoie les recettes à tous les joueurs (comme après un /reload). */
   private static void diffuser() {
      MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
      if (s == null || gestionnaire == null) return;
      s.getPlayerList().broadcastAll(new ClientboundUpdateRecipesPacket(gestionnaire.getRecipes()));
   }

   // ------------------------------------------------------------------ droits
   static boolean peut(ServerPlayer p) {
      return p.hasPermissions(4);
   }

   private static void dire(ServerPlayer p, String cle, String fr, ChatFormatting c, Object... args) {
      p.sendSystemMessage(Component.translatableWithFallback(cle, fr, args).withStyle(c));
   }

   // ------------------------------------------------------------------ éditeur
   static void ouvrir(ServerPlayer p) {
      envoyerListe(p, "");
   }

   private static void envoyerListe(ServerPlayer p, String choisie) {
      if (!peut(p)) {
         dire(p, "heraliz_tactique.armurier.refuse", "Réservé aux op.", ChatFormatting.RED);
         return;
      }
      if (gestionnaire == null) return;
      lire();
      List<PaquetEdition.Recette> l = new ArrayList<>();
      for (GunSmithTableRecipe r : gestionnaire.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
         JsonObject m = MODIFS.get(r.getId().toString());
         byte etat = m == null ? PaquetEdition.FICHIER : "ajoutee".equals(etat(m)) ? PaquetEdition.AJOUTEE : PaquetEdition.MODIFIEE;
         l.add(vers(r, etat));
      }
      // supprimées en jeu : montrées grisées pour pouvoir les remettre
      for (Map.Entry<String, JsonObject> e : MODIFS.entrySet()) {
         if (!"supprimee".equals(etat(e.getValue()))) continue;
         GunSmithTableRecipe o = ORIGINES.get(new ResourceLocation(e.getKey()));
         if (o != null) l.add(vers(o, PaquetEdition.SUPPRIMEE));
      }
      l.sort(Comparator.comparing(PaquetEdition.Recette::onglet).thenComparing(r -> r.resultat().getHoverName().getString()));
      Armurier.envoyer(p, new PaquetEdition(l, choisie));
   }

   private static PaquetEdition.Recette vers(GunSmithTableRecipe r, byte etat) {
      ResourceLocation tab = r.getTab();
      String onglet = tab != null && Armurier.NS_ONGLETS.equals(tab.getNamespace()) ? tab.getPath() : "";
      List<PaquetEdition.Entree> e = new ArrayList<>();
      for (GunSmithTableIngredient in : r.getInputs()) {
         ItemStack[] items = in.getIngredient().getItems();
         ItemStack icone = items.length > 0 ? items[0].copyWithCount(1) : new ItemStack(Items.BARRIER);
         String json;
         try {
            json = in.getIngredient().toJson().toString();
         } catch (RuntimeException ex) {
            json = "";
         }
         e.add(new PaquetEdition.Entree(icone, in.getCount(), json));
      }
      return new PaquetEdition.Recette(r.getId().toString(), onglet, etat, r.getOutput().copy(), e);
   }

   static void modif(ServerPlayer p, PaquetModif m) {
      if (!peut(p)) {
         dire(p, "heraliz_tactique.armurier.refuse", "Réservé aux op.", ChatFormatting.RED);
         return;
      }
      if (gestionnaire == null) return;
      lire();
      switch (m.action()) {
         case PaquetModif.OUVRIR -> envoyerListe(p, "");
         case PaquetModif.ENREGISTRER -> enregistrer(p, m);
         case PaquetModif.SUPPRIMER -> {
            ResourceLocation id = ResourceLocation.tryParse(m.id());
            if (id == null) return;
            if (ORIGINES.containsKey(id)) {
               JsonObject o = new JsonObject();
               o.addProperty("etat", "supprimee");
               MODIFS.put(id.toString(), o);
            } else if (MODIFS.remove(id.toString()) == null) {
               return;
            }
            fini(p, "supprimée", id.toString(), "");
         }
         case PaquetModif.REVENIR -> {
            ResourceLocation id = ResourceLocation.tryParse(m.id());
            if (id == null || MODIFS.remove(id.toString()) == null) return;
            fini(p, "remise au fichier", id.toString(), ORIGINES.containsKey(id) ? id.toString() : "");
         }
         default -> {
         }
      }
   }

   private static void fini(ServerPlayer p, String quoi, String id, String choisie) {
      sauver();
      appliquer();
      diffuser();
      LOG.info("[heraliz_tactique] recette {} {} par {}", id, quoi, p.getGameProfile().getName());
      envoyerListe(p, choisie);
   }

   private static void enregistrer(ServerPlayer p, PaquetModif m) {
      if (!Armurier.ONGLETS.contains(m.onglet()) || m.resultat().isEmpty() || m.quantite() < 1 || m.quantite() > QTE_MAX_RESULTAT
         || m.lignes().isEmpty() || m.lignes().size() > PaquetModif.MAX_LIGNES) {
         dire(p, "heraliz_tactique.armurier.invalide", "Recette incomplète : un objet donné, au moins un composant, des quantités valides.",
            ChatFormatting.RED);
         return;
      }
      ResourceLocation id;
      if (m.id().isEmpty()) {
         int n = 1;
         do {
            id = new ResourceLocation("heraliz_tactique", "atelier/r" + n++);
         } while (ORIGINES.containsKey(id) || MODIFS.containsKey(id.toString()));
      } else {
         id = ResourceLocation.tryParse(m.id());
         if (id == null) return;
      }
      JsonArray materiaux = new JsonArray();
      for (PaquetModif.Ligne l : m.lignes()) {
         if (l.quantite() < 1 || l.quantite() > QTE_MAX_ENTREE) {
            dire(p, "heraliz_tactique.armurier.invalide", "Recette incomplète : un objet donné, au moins un composant, des quantités valides.",
               ChatFormatting.RED);
            return;
         }
         JsonElement ing;
         if (l.objet().isEmpty()) {
            try {
               ing = JsonParser.parseString(l.ingredient());
               Ingredient.fromJson(ing); // refusé tout de suite s'il est faux
            } catch (Exception ex) {
               dire(p, "heraliz_tactique.armurier.invalide", "Recette incomplète : un objet donné, au moins un composant, des quantités valides.",
                  ChatFormatting.RED);
               return;
            }
         } else {
            ing = ingredient(l.objet());
         }
         JsonObject e = new JsonObject();
         e.add("item", ing);
         e.addProperty("count", l.quantite());
         materiaux.add(e);
      }
      JsonObject objet = objet(m.resultat());
      objet.addProperty("count", m.quantite());
      JsonObject resultat = new JsonObject();
      resultat.addProperty("type", "custom");
      resultat.add("item", objet);
      resultat.addProperty("group", Armurier.groupe(m.onglet()));
      JsonObject recette = new JsonObject();
      recette.addProperty("type", "tacz:gun_smith_table_crafting");
      recette.add("materials", materiaux);
      recette.add("result", resultat);
      try {
         construire(id, recette); // le lecteur de TaCZ doit l'accepter avant qu'on l'enregistre
      } catch (Exception ex) {
         LOG.warn("[heraliz_tactique] recette {} refusée : {}", id, ex.getMessage());
         dire(p, "heraliz_tactique.armurier.refusee", "Recette refusée par TaCZ : %s", ChatFormatting.RED, String.valueOf(ex.getMessage()));
         return;
      }
      JsonObject modif = new JsonObject();
      modif.addProperty("etat", ORIGINES.containsKey(id) ? "modifiee" : "ajoutee");
      modif.add("recette", recette);
      MODIFS.put(id.toString(), modif);
      dire(p, "heraliz_tactique.armurier.enregistree", "Recette enregistrée et envoyée à tous les joueurs.", ChatFormatting.GREEN);
      fini(p, "enregistrée", id.toString(), id.toString());
   }

   /** Données gardées d'un objet choisi dans la barre (sans usure, marque anti-copie ni camo à vie). */
   private static CompoundTag donnees(ItemStack s) {
      if (!s.hasTag()) return null;
      CompoundTag t = s.getTag().copy();
      t.remove("spartan_uid");
      t.remove("Damage");
      t.remove("HeraliszCamoProprio");
      t.remove("HeraliszCamoJeton");
      return t.isEmpty() ? null : t;
   }

   private static JsonObject objet(ItemStack s) {
      JsonObject o = new JsonObject();
      o.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())));
      CompoundTag t = donnees(s);
      if (t != null) o.addProperty("nbt", t.toString());
      return o;
   }

   /**
    * Ingrédient d'un objet de la barre : l'objet seul, ou l'objet qui porte au moins ses données (soins, munitions,
    * accessoires...). « forge:partial_nbt » et non « forge:nbt » (données exactes) : les objets non empilables des joueurs
    * portent en plus le numéro anti-copie de Spartan (spartan_uid) et les objets usables leur usure, retirés de la recette ;
    * avec des données exactes, un accessoire ou une arme demandés ne correspondaient à aucun objet : recette impossible.
    */
   private static JsonObject ingredient(ItemStack s) {
      JsonObject o = new JsonObject();
      CompoundTag t = donnees(s);
      if (t != null) {
         o.addProperty("type", "forge:partial_nbt");
         o.addProperty("nbt", t.toString());
      }
      o.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())));
      return o;
   }

   // ------------------------------------------------------------------ commande
   private static void commandes(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("armurier").requires(s -> s.hasPermission(4))
         .then(Commands.literal("registre").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (!peut(p)) return 0;
            ItemStack s = new ItemStack(Armurier.REGISTRE.get());
            if (!p.getInventory().add(s)) p.drop(s, false);
            c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_tactique.armurier.donne",
               "Registre de l'armurier : clic droit pour modifier les recettes de la Table d'armurier."), false);
            return 1;
         }))
         .then(Commands.literal("editer").executes(c -> {
            ouvrir(c.getSource().getPlayerOrException());
            return 1;
         })));
   }
}
