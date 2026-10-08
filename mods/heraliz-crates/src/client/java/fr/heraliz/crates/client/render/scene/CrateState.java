package fr.heraliz.crates.client.render.scene;

/**
 * Etat d'un coffre pour une image : horloge, sequence d'ouverture, informations de la roue.
 */
public final class CrateState {
    /** Horloge des animations (ticks) : repart de 0 a l'ouverture (la caisse part de sa pose figee). */
    public float time;
    /** Horloge du monde (ticks), continue meme pendant l'ouverture : parties animees au repos (Design.idle). */
    public float clock;
    /** Pose figee (caisse cuite au repos) : les parties animees au repos n'y sont pas, Design.idle les dessine. */
    public boolean frozen;
    /** Sequence d'ouverture en cours. */
    public boolean active;
    /** Ticks depuis le debut de la sequence. */
    public float t;
    /** Valeur pseudo-aleatoire propre au coffre (0..1) pour desynchroniser les animations. */
    public float seed;
    /** Rendu en tant qu'objet (inventaire, main) : pas d'effets au sol. */
    public boolean item;
    /** Ticks depuis le dernier clic sans cle (tremblement), ou -1. */
    public float shake = -1;
    /** Roue d'objets. */
    public int ringSize = 8, winIndex;
    public int rarityRgb = 0xFFFFFF;
    /** 0 commun, 1 rare, 2 legendaire (sons et effets). */
    public int rarityTier;
    /** Couleur et rang (0 commun .. 5 mythique) de chaque case de la roue, ou null. */
    public int[] ringRgb, ringRank;
    /** Reglages client. */
    public boolean beams = true, runes = true;
    /** Rendu en jeu : les commandes ITEM seront dessinees (sinon, dessiner un modele de remplacement). */
    public boolean realItems;
    /** Destination de l'objet gagne (px, repere du modele), ou null. */
    public float[] target;
    /** Position de la camera (px, repere du modele), pour le niveau de detail. */
    public float camX = 8, camY = 24, camZ = 60;

    public float pulse(float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed + seed * 6.2831f);
    }

    /** Distance camera -> centre du coffre (px). */
    public float camDist() {
        float dx = camX - 8, dy = camY - 12, dz = camZ - 8;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public CrateState copy() {
        CrateState c = new CrateState();
        c.time = time;
        c.clock = clock;
        c.frozen = frozen;
        c.active = active;
        c.t = t;
        c.seed = seed;
        c.item = item;
        c.shake = shake;
        c.ringSize = ringSize;
        c.winIndex = winIndex;
        c.rarityRgb = rarityRgb;
        c.rarityTier = rarityTier;
        c.ringRgb = ringRgb;
        c.ringRank = ringRank;
        c.beams = beams;
        c.runes = runes;
        c.realItems = realItems;
        c.target = target;
        c.camX = camX;
        c.camY = camY;
        c.camZ = camZ;
        return c;
    }
}
