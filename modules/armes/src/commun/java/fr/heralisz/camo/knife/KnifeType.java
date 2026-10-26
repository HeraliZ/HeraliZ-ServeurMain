package fr.heralisz.camo.knife;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Couteaux de base (modèles du pack ArkaniaZ) et rôle de leurs pièces : lame, manche caoutchouc, pièces métal, manche métal sombre.
 * Une pièce prend le rôle de son ancêtre listé le plus proche ; par défaut c'est de la lame.
 */
public enum KnifeType {
   // Butterfly (comme dans CS) : manches en métal noir (plaques fines), insert lisse d'une teinte plus sombre que la lame
   // (cubes plus épais au milieu des manches) ; les cubes épais près du pivot du manche (disques des pivots, dents du
   // ressort) et le loquet (bing) sont noirs
   BUTTERFLY("butterfly", 0.27F, 2.6F, Map.of("danger_grip", Part.HANDLE, "safe_grip", Part.HANDLE, "bing", Part.PIN)),
   // Karambit : manche caoutchouc ; l'anneau est le prolongement de la lame qui ressort sous le manche -> finition de la lame
   KARAMBIT("karambit", 0F, 0F, Map.of("bone2", Part.GRIP, "bone3", Part.GRIP, "bone4", Part.GRIP, "bone5", Part.GRIP, "bone8", Part.GRIP,
      "bone6", Part.METAL)),
   M9("m9", 0F, 0F, Map.of("daozhu", Part.GRIP, "daobing", Part.METAL, "daowei", Part.METAL)),
   // couteaux CS par défaut (pack ArkaniaZ) : manche = grip assorti à la finition
   CT_KNIFE("ct_knife", Map.of("daoba", Part.GRIP, "daobing", Part.GRIP), 0F, 0),
   // couteau T : le manche est bone6 (corps) et bone4 (garnitures) ; bone (garde) prend la finition de la lame
   T_KNIFE("t_knife", Map.of("bone4", Part.GRIP, "bone6", Part.GRIP), 0F, 0),
   // lames de mêlée HeralisZ (moteur HeralisZ Tactical) : manche = grip assorti à la finition ; modèles d'un seul bloc -> coupe
   DAGGER("dagger", Map.of(), 0.55F, -1),
   TAC_DAGGER("tac_dagger", Map.of(), 0.4F, -1),
   CREED("creed", Map.of(), 0.5F, -1),
   // manche orné : noir, étoile et pierres (motifs colorés de la texture d'origine) dans la finition de la lame ; le skin
   // utilise polaris_lore_geo (outils/skins_melee.py : une face de lame = un morceau de texture)
   DESMOULINS_EDGE("desmoulins_edge", Map.of("hand", Part.ORNATE), 0F, 0),
   DRAGONS_FANG("dragons_fang", Map.of(), 0.3F, -1),
   EXECUTOR("executor", Map.of("handle", Part.GRIP), 0F, 0),
   FIERY_OWL("fiery_owl", Map.of(), 0.5F, -1),
   MERCY("mercy", Map.of("body", Part.GRIP), 0F, 0),
   SEA_GLEAM("sea_gleam", Map.of("h2", Part.GRIP, "h4", Part.GRIP, "h5", Part.GRIP), 0F, 0),
   // batte : le skin utilise baseball_bat_skin_geo (outils/skins_melee.py), batte refaite propre, manche dans l'os « manche »
   BASEBALL_BAT("baseball_bat", Map.of("manche", Part.GRIP), 0F, 0),
   // couteaux du pack CS2 (21/10, rendus validés) ; skins sur les copies propres écrites par outils/skins_melee.py, qui
   // découpe les pièces en os à elles. Skeleton : corde tressée teintée par la lame, anneau et boucle du pommeau dans la
   // finition ; Push : poignée et garde noir mat, morceaux du dos de la lame dans le skin ; Stiletto : plaquettes et cadre
   // d'origine (bois et métal), embouts et garde violets dans la finition
   CS2_SKELETON("cs2_skeleton", Map.of("corde", Part.CORDE, "pommeau", Part.METAL, "octagon", Part.METAL, "octagon2", Part.METAL,
      "octagon3", Part.METAL, "octagon4", Part.METAL, "octagon5", Part.METAL), 0F, 0),
   CS2_PUSH("cs2_push", Map.of("push_r", Part.NOIR_MAT, "push_l", Part.NOIR_MAT, "bone3", Part.BLADE, "bone2", Part.BLADE,
      "dos_r", Part.BLADE, "dos_l", Part.BLADE), 0F, 0),
   CS2_STILETTO("cs2_stiletto", Map.of("a", Part.ORIGINAL, "a2", Part.ORIGINAL, "embouts", Part.METAL), 0F, 0);

   public final String id;
   private final Map<String, Part> parts;
   /** Cubes de manche plus fins que ça (en x, pixels du modèle) = métal sombre, les plus épais = insert lisse ; 0 = aucun. */
   public final float inlayWidth;
   /** Cubes épais à moins de cette distance (pixels) du pivot de leur manche = métal noir (pivots) au lieu de l'insert. */
   public final float pivotRadius;
   /**
    * Modèles d'un seul bloc (lame et manche dans le même os) : ce qui se trouve avant cette fraction de la longueur, depuis
    * le pommeau, est le manche (texture d'origine) ; 0 = pas de coupe.
    */
   public final float cut;
   /** Côté de la pointe sur l'axe long, dans le repère de l'outil d'aperçu (tools/camo_preview/export_gun.py) : +1 / -1. */
   public final int tip;

   KnifeType(String id, float inlayWidth, float pivotRadius, Map<String, Part> parts) {
      this(id, inlayWidth, pivotRadius, parts, 0F, 0);
   }

   KnifeType(String id, Map<String, Part> parts, float cut, int tip) {
      this(id, 0F, 0F, parts, cut, tip);
   }

   KnifeType(String id, float inlayWidth, float pivotRadius, Map<String, Part> parts, float cut, int tip) {
      this.id = id;
      this.inlayWidth = inlayWidth;
      this.pivotRadius = pivotRadius;
      this.parts = parts;
      this.cut = cut;
      this.tip = tip;
   }

   @Nullable
   public Part partOf(String bone) {
      return this.parts.get(bone);
   }

   /** Types par identifiant (lu à chaque image pour chaque couteau dessiné : plus de copie de values() à chaque appel). */
   private static final class ParId {
      static final java.util.Map<String, KnifeType> MAP = new java.util.HashMap<>();

      static {
         for (KnifeType k : values()) {
            MAP.putIfAbsent(k.id, k);
         }
      }
   }

   @Nullable
   public static KnifeType byId(String id) {
      return id == null ? null : ParId.MAP.get(id);
   }

   /** Valeur écrite dans le canal A de la carte de positions (même codage que les parties d'arme). */
   public enum Part {
      /** lame ; manche caoutchouc ; métal de la couleur du skin (garde, anneau du M9) ; métal noir (pivots, anneau) ;
       *  insert lisse du manche ; manche métal noir. */
      BLADE(1.0F), GRIP(0.75F),
      /** manche orné : fond noir, motifs de la texture d'origine repris dans la finition (camo_gabarit : ornateHandle). */
      ORNATE(0.68F), METAL(0.5F), PIN(0.42F), INLAY(0.31F), HANDLE(0.25F),
      /** corde tressée (paracorde) teintée par la couleur de la lame (Skeleton, camo_gabarit : cordeWrap). */
      CORDE(0.82F),
      /** manche noir mat uniforme (Push, camo_gabarit : acierManche). */
      NOIR_MAT(0.56F),
      /** texture d'origine (jamais camouflée : sous le seuil du shader), mais repère le côté du manche. */
      ORIGINAL(0.1F);

      public final float code;

      Part(float code) {
         this.code = code;
      }
   }
}
