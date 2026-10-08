package fr.heraliz.armory.armor;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Objet repris de HeralisZ Armor (0.9.0, catalogue GearCatalog genere par tools/fusion_090.py) : ghillie, brassard,
 * masque a gaz ou objet event. Meme rendu GeckoLib (geo/<geo>.geo.json, textures/armor/<tex>.png, animations/<anim>)
 * et memes stats qu'avant ; la protection passe par le calcul commun de l'Armurerie ({@link Protection}).
 *
 * @param id         identifiant (heraliz_armory:<id>, ancien heralisz_armor:<id>)
 * @param tab        « equipement » (onglet principal) ou « event » (onglet event)
 * @param fam        famille (ghillie, armband, gas_mask, halloween, head_bag)
 * @param kind       « armor » (slot d'armure vanilla : tete) ou « curio »
 * @param slot       zone : head, face, body, bracelet
 * @param curio      slot Curios (mask, body, bracelet) ou null
 * @param rarity     rarete (couleur du nom) : common, uncommon, rare, epic, legendary, event
 * @param bullet     % balles (explosion : la moitie, comme avant)
 * @param melee      % corps a corps
 * @param bite       % morsure
 * @param radiation  % radiation
 * @param durability durabilite
 * @param anim       animations (idle / move / sprint) ou null
 * @param gh         ghillie modelisee (« full » integrale, « leafy » incomplete) : superposition (client.GearLayering)
 * @param parts      parties du modele rendues en Curios (head, chest, legs, feet)
 * @param bonus      « camouflage » (antizombie), « poison » (filtre), ou null
 * @param glow       texture lumineuse (_glowmask)
 */
public record GearDef(String id, String tab, String fam, String kind, String slot, @Nullable String curio, String rarity, int bullet,
                      int melee, int bite, int radiation, int durability, String geo, String tex, @Nullable String anim,
                      @Nullable String gh, List<String> parts, @Nullable String bonus, boolean glow) implements Guard {

    public boolean armor() {
        return "armor".equals(kind);
    }

    /** Explosion = moitie des balles (regle de HeralisZ Armor, gardee telle quelle). */
    @Override
    public float explosion() {
        return bullet * 0.5F;
    }

    @Override
    public boolean gas() {
        return "poison".equals(bonus);
    }

    @Override
    public boolean protectsHead() {
        return "head".equals(slot) || "face".equals(slot);
    }

    @Override
    public boolean camouflage() {
        return "camouflage".equals(bonus);
    }

    public boolean event() {
        return "event".equals(tab);
    }
}
