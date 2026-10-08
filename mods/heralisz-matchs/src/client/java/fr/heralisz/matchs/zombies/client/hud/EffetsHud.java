package fr.heralisz.matchs.zombies.client.hud;

import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.zombies.client.boisson.Boisson;
import fr.heralisz.matchs.zombies.client.musique.MusiqueZombies;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.contenu.bloc.AtoutBE;
import fr.heralisz.matchs.zombies.jeu.api.EvenementsHud;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.MotifPoints;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Effets passagers du HUD zombies, alimentes par les evenements du jeu ({@link EvenementsHud}, enregistre aupres de
 * {@link HudZombies#ecouteur}) : « +60 » flottants (anneau de 16), transition de manche a la craie, banniere (bonus
 * ramasse, courant retabli, Pack-a-Punch, fabrication, easter egg), atout qui apparait apres la boisson (et animation de
 * boisson du joueur local, machine qui sert), refus qui secoue l'invite, achat, resultat de la boite (rarete),
 * materiaux recoltes, objectif rempli ; transmet aussi les moments musicaux a {@link MusiqueZombies}. Tout est stocke
 * dans des champs et des tableaux fixes ; les chaines sont fabriquees a la reception (jamais par image). Fil client.
 */
public final class EffetsHud implements EvenementsHud {
    public static final EffetsHud INSTANCE = new EffetsHud();

    private EffetsHud() {
    }

    // ------------------------------------------------------------------ « +60 » flottants
    public static final int MAX_POINTS = 16;
    public final UUID[] ptJoueur = new UUID[MAX_POINTS];
    public final Component[] ptTexte = new Component[MAX_POINTS];
    public final int[] ptCouleur = new int[MAX_POINTS];
    public final long[] ptDebut = new long[MAX_POINTS];
    public final float[] ptDecalage = new float[MAX_POINTS];
    public final boolean[] ptFort = new boolean[MAX_POINTS];
    private int ptProchain;

    /** Indice du dernier « +60 » recu (les precedents sont avant lui dans l'anneau). */
    public int dernierPoint() {
        return (ptProchain - 1 + MAX_POINTS) % MAX_POINTS;
    }

    // ------------------------------------------------------------------ manche
    /** Manche affichee avant la transition, nouvelle manche, debut de la transition (ms), vrai = debut de manche. */
    public int mancheAvant, mancheApres;
    public long mancheDebutMs = -100000;
    public boolean mancheDebut, mancheSpeciale;

    // ------------------------------------------------------------------ banniere centrale
    public String banTitre = "", banSous = "";
    public int banCouleur;
    public long banDebutMs = -100000;
    public int banVersion;

    // ------------------------------------------------------------------ divers
    /** Instant ou chaque atout apparait dans le HUD (fin de l'animation de boisson pour le joueur local). */
    public final long[] atoutMs = new long[TypeAtout.values().length];
    public long refusMs = -100000, achatMs = -100000;
    public String refusTexte = "", achatTexte = "";
    public int refusVersion, achatVersion;
    /** Materiaux : instant et quantite de la derniere recolte (ou depense) du joueur local. */
    public long materiauxMs = -100000;
    public int materiauxDelta;
    /** Objectif rempli (coche) : instant de l'annonce. */
    public long objectifRempliMs = -100000;
    /** v3 : instant de la derniere teleportation du joueur local (eclair a l'ecran). */
    public long teleportMs = -100000;
    /** Resultat de la boite pour le joueur local. */
    public long boiteMs = -100000;
    public String boiteTexte = "";
    public int boiteCouleur, boiteVersion;

    private static long maintenant() {
        return Util.getMillis();
    }

    private static boolean moi(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(id);
    }

    @Override
    public void points(UUID joueur, int delta, MotifPoints motif) {
        if (delta == 0) return;
        int i = ptProchain;
        ptProchain = (ptProchain + 1) % MAX_POINTS;
        ptJoueur[i] = joueur;
        ptTexte[i] = Component.literal((delta > 0 ? "+" : "−") + Math.abs(delta)).withStyle(st -> st.withFont(Ui.BOLD));
        boolean doubles = HudZombies.bonusSecondes(TypeBonus.POINTS_DOUBLES) > 0;
        ptCouleur[i] = delta < 0 ? 0xFF6B6B : switch (motif) {
            case TETE, COUTEAU -> 0xFFD24A;
            case KILL -> doubles ? 0xFFD24A : 0xF2E6B8;
            case BONUS -> 0x7CE3A0;
            default -> doubles ? 0xFFE38A : 0xECEEF2;
        };
        ptFort[i] = motif == MotifPoints.TETE || motif == MotifPoints.COUTEAU || motif == MotifPoints.BONUS;
        ptDebut[i] = maintenant();
        ptDecalage[i] = (float) (Math.random() * 6 - 3);
    }

    @Override
    public void manche(int manche, boolean speciale, boolean debut) {
        mancheAvant = mancheApres;
        mancheApres = manche;
        mancheDebutMs = maintenant();
        mancheDebut = debut;
        mancheSpeciale = speciale;
        MusiqueZombies.manche(manche, speciale, debut);
        // pas de banniere ici : le numero a la craie (haut droite) marque chaque manche ; le serveur fait la grande
        // annonce centrale aux manches 5, 10, 15, 20 puis toutes les 10
    }

    @Override
    public void bonus(TypeBonus type, String parNom) {
        banniere(Component.translatable(type.cle()).getString(), parNom == null || parNom.isEmpty() ? ""
                : Component.translatable("zombies.hud.par", parNom).getString(), type.couleur);
    }

    @Override
    public void atout(UUID joueur, TypeAtout type) {
        servir(joueur, type);
        if (!moi(joueur)) return;
        atoutMs[type.ordinal()] = maintenant() + Boisson.DUREE_MS;
        Boisson.boire(type);
    }

    /** La machine de cet atout la plus proche du buveur s'illumine et sert une bouteille (son local). */
    private static void servir(UUID joueur, TypeAtout type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player p = mc.level.getPlayerByUUID(joueur);
        if (p == null) return;
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 1, 3))) {
            if (mc.level.getBlockEntity(q) instanceof AtoutBE be && be.type() == type) {
                be.servirClient();
                mc.getSoundManager().play(new SimpleSoundInstance(ZbSons.MACHINE_SERVIR.get(), SoundSource.BLOCKS, 0.9F, 1.0F,
                        RandomSource.create(), q.getX() + 0.5, q.getY() + 0.6, q.getZ() + 0.5));
                return;
            }
        }
    }

    @Override
    public void etatJoueur(UUID joueur, String nom, int etat) {
        if (moi(joueur)) return;
        if (etat == HudZombies.A_TERRE) banniere(Component.translatable("zombies.hud.coequipier_a_terre", nom).getString(), "", 0xFF6B6B);
    }

    @Override
    public void refus(Component raison) {
        refusTexte = raison == null ? "" : raison.getString();
        refusMs = maintenant();
        refusVersion++;
    }

    @Override
    public void achat(Component quoi, int cout) {
        achatTexte = quoi == null ? "" : quoi.getString();
        achatMs = maintenant();
        achatVersion++;
    }

    @Override
    public void teleport() {
        teleportMs = maintenant();
    }

    @Override
    public void courant() {
        MusiqueZombies.moment(MomentMusique.COURANT);
        banniere(Component.translatable("zombies.hud.courant").getString(), Component.translatable("zombies.hud.courant.sous").getString(),
                0x7CE3FF);
    }

    // ------------------------------------------------------------------ v2
    @Override
    public void musique(MomentMusique moment, int manche) {
        MusiqueZombies.moment(moment);
    }

    @Override
    public void objectif(Component texte, boolean nouveau) {
        if (nouveau) objectifRempliMs = maintenant();
    }

    @Override
    public void intro(List<Component> lignes) {
        // le lore s'ecrit en haut a gauche, sous l'objectif (HudZombies.intro), rien a garder ici
    }

    @Override
    public void packAPunch(UUID joueur, Component arme) {
        packAPunch(joueur, arme, 1);
    }

    /** Banniere du Pack-a-Punch : une etoile par niveau, sous-titre « Arme amelioree · niveau II ». */
    @Override
    public void packAPunch(UUID joueur, Component arme, int niveau) {
        if (!moi(joueur)) return;
        int n = Math.max(1, Math.min(ZombiesApi.PAP_NIVEAU_MAX, niveau));
        banniere("★".repeat(n) + " " + (arme == null ? "" : arme.getString().replace("★", "").trim()),
                Component.translatable("zombies.hud.pap_niveau", romain(n)).getString(), n >= 3 ? 0xFF6BD5 : n == 2 ? 0xE08BFF : 0xC77DFF);
    }

    private static String romain(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    @Override
    public void fabrication(UUID joueur, Component objet) {
        if (!moi(joueur)) return;
        achat(Component.translatable("zombies.hud.fabrication", objet == null ? "" : objet.getString()), 0);
    }

    @Override
    public void materiaux(UUID joueur, int delta) {
        if (!moi(joueur) || delta == 0) return;
        materiauxDelta = delta;
        materiauxMs = maintenant();
    }

    @Override
    public void easterEgg(int etape, int total, boolean termine) {
        if (termine) {
            banniere(Component.translatable("zombies.hud.easter_egg.fin").getString(),
                    Component.translatable("zombies.hud.easter_egg.fin.sous").getString(), 0x6CFFB0);
        } else {
            banniere(Component.translatable("zombies.hud.easter_egg", etape, total).getString(), "", 0x6CFFB0);
        }
    }

    @Override
    public void boite(Rarete rarete, Component arme) {
        if (rarete == null) return;
        boiteTexte = Component.translatable(rarete.cle()).getString().toUpperCase(java.util.Locale.ROOT) + " · "
                + (arme == null ? "" : arme.getString());
        boiteCouleur = rarete.couleur;
        boiteMs = maintenant();
        boiteVersion++;
    }

    private void banniere(String titre, String sous, int couleur) {
        banTitre = titre;
        banSous = sous;
        banCouleur = couleur;
        banDebutMs = maintenant();
        banVersion++;
    }

    /** Remise a zero (fin de partie). */
    public void vider() {
        for (int i = 0; i < MAX_POINTS; i++) ptJoueur[i] = null;
        mancheDebutMs = banDebutMs = refusMs = achatMs = materiauxMs = objectifRempliMs = boiteMs = -100000;
        mancheAvant = mancheApres = 0;
        java.util.Arrays.fill(atoutMs, 0);
    }
}
