package fr.heralisz.matchs.zombies.client.musique;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.boisson.Boisson;
import fr.heralisz.matchs.zombies.contenu.ZbConfigClient;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Musique du mode zombies cote client (ZOMBIES_V2_SPEC §2, RETOURS §4.15) : le jeu n'envoie que des moments
 * ({@link MomentMusique}) et ses jingles courts ; ici on choisit et on lit :
 * <ul>
 *     <li>fond d'ambiance en boucle pendant toute la partie : {@code ambiance.ogg} du dossier de l'utilisateur, sinon
 *     l'ambiance originale du laboratoire ({@code zb_ambiance}) ;</li>
 *     <li>playlist : la musique « 115 » integree au mod ({@code heralisz_matchs:zombies.musique_115}, reglage
 *     {@code musique_115}), puis {@code heralisz_matchs:zombies.musique_1..8} (fichiers du dossier, {@link PackMusiques}) ;
 *     mode « continue » (de la 1re manche a la fin, morceaux enchaines) ou « manches » (un morceau par manche, fondu a la
 *     fin de la manche) ; baisse de volume pendant les jingles de manche ;</li>
 *     <li>jingles : ceux du jeu, remplaces par les fichiers de l'utilisateur ({@code manche_debut.ogg}...), sinon par les
 *     jingles classiques integres ({@code zombies.jingle_manche_debut|jingle_manche_fin|jingle_boite}, reglage
 *     {@code jingles_classiques}) ; easter egg : {@code easter_egg.ogg} ou « 115 ». Les fichiers de l'utilisateur passent
 *     toujours avant les sons integres ;</li>
 *     <li>The Giant, acte IV : intro du boss = silence de la musique du jeu ; combat = musique du boss en boucle
 *     ({@code boss_n07.ogg}, sinon celle du mod {@code heralisz_n07}, sinon « 115 ») ; fin = retour a la playlist ;</li>
 *     <li>volume : curseurs « Musique » et « Ambiance » du jeu, {@link ZbConfigClient}, commande {@code /zbmusique}.</li>
 * </ul>
 * Un emplacement vide = silence propre (rien n'est tente). Les moments arrivent par
 * {@link #moment} (evenement du jeu) et sont aussi deduits des jingles recus du serveur (robuste si l'un manque).
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, value = Dist.CLIENT)
public final class MusiqueZombies {
    private MusiqueZombies() {
    }

    /** Sons integres au mod ({@code assets/heralisz_matchs/sounds/zombies/}) : musique « 115 » et jingles classiques. */
    public static final ResourceLocation M_115 = HeraliszMatchs.id("zombies.musique_115");
    private static final ResourceLocation J_DEBUT = HeraliszMatchs.id("zombies.jingle_manche_debut");
    private static final ResourceLocation J_FIN = HeraliszMatchs.id("zombies.jingle_manche_fin");
    private static final ResourceLocation J_BOITE = HeraliszMatchs.id("zombies.jingle_boite");
    /** Le nounours de la boite (rire puis depart), son d'origine fourni par l'utilisateur (05/10). */
    private static final ResourceLocation J_PELUCHE = HeraliszMatchs.id("zombies.peluche");
    private static final List<ResourceLocation> INTEGRES = List.of(M_115, J_DEBUT, J_FIN, J_BOITE, J_PELUCHE);

    private static Piste ambiance, piste, morceau;
    /** The Giant, acte IV : musique du boss N-07 en boucle ; mode boss = ni playlist ni fond d'ambiance. */
    private static Piste boss;
    private static boolean modeBoss;
    /** Musique du mod du boss (jouee si l'utilisateur n'a pas mis de boss_n07.ogg). */
    public static final ResourceLocation M_BOSS_N07 = new ResourceLocation("heralisz_n07", "music.sujet_n07");
    private static final List<ResourceLocation> PLAYLIST = new ArrayList<>();
    private static final Set<ResourceLocation> ECHECS = new HashSet<>();
    private static int prochain, pause;
    private static boolean partie, lancee, enManche;
    private static long etouffeJusqua;
    @Nullable
    private static MomentMusique dernier;
    private static long dernierMs;

    // ------------------------------------------------------------------ moments
    /** Moment musical (evenement du jeu, ou deduit d'un jingle). Les doublons rapproches sont ignores. */
    public static void moment(MomentMusique m) {
        if (m == null) return;
        long t = Util.getMillis();
        if (m == dernier && t - dernierMs < 2000) return;
        dernier = m;
        dernierMs = t;
        switch (m) {
            case PARTIE_DEBUT -> {
                partie = true;
                lancee = false;
                enManche = false;
                reconstruire();
            }
            case MANCHE_DEBUT, SPECIALE_DEBUT -> {
                enManche = true;
                etouffer(5500);
                if (!lancee) {
                    lancee = true;
                    pause = 40;
                }
            }
            case MANCHE_FIN, SPECIALE_FIN, MANCHE_PASSEE -> {
                enManche = false;
                etouffer(5000);
                if ("manches".equals(ZbConfigClient.mode()) && piste != null) {
                    piste.arreter(80);
                    piste = null;
                    prochain++;
                }
            }
            case COURANT -> etouffer(4000);
            case PACK_A_PUNCH -> {
                if (PackMusiques.present("pack_a_punch")) jouerMorceau(PackMusiques.evenement("pack_a_punch"));
                else etouffer(3500);
            }
            case EASTER_EGG -> {
                ResourceLocation r = PackMusiques.present("easter_egg") ? PackMusiques.evenement("easter_egg")
                        : ZbConfigClient.musique115() ? M_115 : null;
                if (r != null) jouerMorceau(r);
            }
            case BEAUTY_OF_ANNIHILATION -> {
                // The Giant : les 3 bocaux a moelle (fichier de l'utilisateur, sinon « 115 » integree)
                ResourceLocation r = PackMusiques.present("beauty_of_annihilation") ? PackMusiques.evenement("beauty_of_annihilation") : M_115;
                jouerMorceau(r);
            }
            case PARTIE_FIN -> {
                lancee = false;
                if (piste != null) piste.arreter(50);
                if (morceau != null) morceau.arreter(50);
                piste = morceau = null;
                arreterBoss(50);
            }
            case SILENCE -> toutArreter(15);
            case BOSS_INTRO -> {
                // la scene de l'intro a ses propres sons : playlist, morceau et fond d'ambiance se taisent
                modeBoss = true;
                if (piste != null) {
                    piste.arreter(40);
                    piste = null;
                    prochain++;
                }
                if (morceau != null) morceau.arreter(40);
                if (ambiance != null) ambiance.arreter(60);
                if (boss != null) boss.arreter(60);
                morceau = ambiance = boss = null;
            }
            case BOSS_N07 -> {
                modeBoss = true;
                if (piste != null) {
                    piste.arreter(30);
                    piste = null;
                    prochain++;
                }
                if (morceau != null) morceau.arreter(30);
                morceau = null;
                if (boss == null || boss.sortie) {
                    boss = new Piste(musiqueBoss(), SoundSource.MUSIC, true, false, false);
                    boss.fondu(1, 0.03F);
                    lancer(boss);
                }
            }
            case BOSS_FIN -> {
                arreterBoss(80);
                modeBoss = false;
                pause = 100;
            }
        }
    }

    /** Deduit les moments des jingles de manche (meme sans evenement du jeu). */
    public static void manche(int numero, boolean speciale, boolean debut) {
        if (debut) moment(speciale ? MomentMusique.SPECIALE_DEBUT : MomentMusique.MANCHE_DEBUT);
        else moment(speciale ? MomentMusique.SPECIALE_FIN : MomentMusique.MANCHE_FIN);
    }

    private static void etouffer(long ms) {
        etouffeJusqua = Math.max(etouffeJusqua, Util.getMillis() + ms);
    }

    private static void jouerMorceau(ResourceLocation r) {
        if (morceau != null) morceau.arreter(20);
        if (piste != null) {
            piste.arreter(40);
            piste = null;
            prochain++;
        }
        morceau = new Piste(r, SoundSource.MUSIC, false, false, false);
        morceau.fondu(1, 0.05F);
        lancer(morceau);
    }

    private static void toutArreter(int ticks) {
        if (ambiance != null) ambiance.arreter(ticks);
        if (piste != null) piste.arreter(ticks);
        if (morceau != null) morceau.arreter(ticks);
        ambiance = piste = morceau = null;
        arreterBoss(ticks);
        partie = lancee = enManche = false;
    }

    private static void arreterBoss(int ticks) {
        if (boss != null) boss.arreter(ticks);
        boss = null;
        modeBoss = false;
    }

    /** Musique du boss : fichier de l'utilisateur, sinon celle du mod du boss (s'il est installe), sinon « 115 » integree. */
    private static ResourceLocation musiqueBoss() {
        if (PackMusiques.present("boss_n07")) return PackMusiques.evenement("boss_n07");
        if (Minecraft.getInstance().getSoundManager().getSoundEvent(M_BOSS_N07) != null) return M_BOSS_N07;
        return M_115;
    }

    /** Playlist : « 115 » integree (si reglage) puis musique_1..8 presents, moins les echecs. */
    private static void reconstruire() {
        PLAYLIST.clear();
        if (ZbConfigClient.musique115()) PLAYLIST.add(M_115);
        for (int i = 1; i <= 8; i++) if (PackMusiques.present("musique_" + i)) PLAYLIST.add(PackMusiques.evenement("musique_" + i));
        PLAYLIST.removeAll(ECHECS);
        prochain = 0;
    }

    /**
     * Etat des sons integres pour le journal du banc : pour chacun, l'evenement est-il connu du gestionnaire de sons
     * et son fichier OGG est-il present dans les ressources ?
     */
    public static String etatIntegres() {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        for (ResourceLocation r : INTEGRES) {
            var ev = mc.getSoundManager().getSoundEvent(r);
            if (ev == null) {
                out.add(r + "=INCONNU");
                continue;
            }
            ResourceLocation f = ev.getSound(RandomSource.create()).getPath();
            out.add(r + "=" + f + (mc.getResourceManager().getResource(f).isPresent() ? " ok" : " FICHIER ABSENT"));
        }
        return String.join(", ", out);
    }

    // ------------------------------------------------------------------ boucle
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean actif = HudZombies.actif() && mc.level != null && mc.player != null;
        if (!actif) {
            if (partie || ambiance != null || piste != null || morceau != null) toutArreter(25);
            return;
        }
        if (!partie) moment(MomentMusique.PARTIE_DEBUT);
        mc.getMusicManager().stopPlaying();
        SoundManager sm = mc.getSoundManager();
        if (modeBoss) {
            // acte IV de The Giant : seule la musique du boss (relancee si son flux s'est arrete ; introuvable : « 115 »)
            if (boss != null && !boss.sortie && boss.fini(sm)) {
                boolean echec = boss.duree() < 40;
                if (echec && boss.id.equals(M_115)) {
                    boss = null;
                    return;
                }
                boss = new Piste(echec ? M_115 : boss.id, SoundSource.MUSIC, true, false, false);
                boss.fondu(1, 0.03F);
                lancer(boss);
            }
            return;
        }
        // fond d'ambiance
        ResourceLocation amb = PackMusiques.present("ambiance") ? PackMusiques.evenement("ambiance") : ZbSons.AMBIANCE.getId();
        if (ambiance != null && !ambiance.id.equals(amb)) {
            ambiance.arreter(20);
            ambiance = null;
        }
        if (ambiance == null || ambiance.fini(sm)) {
            if (mc.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0 && !ECHECS.contains(amb)) {
                ambiance = new Piste(amb, SoundSource.AMBIENT, true, true, false);
                ambiance.fondu(1, 0.012F);
                lancer(ambiance);
            }
        }
        // morceau ponctuel (easter egg, jingle long)
        if (morceau != null && morceau.fini(sm)) {
            morceau = null;
            pause = 60;
        }
        // playlist
        String mode = ZbConfigClient.mode();
        boolean veut = lancee && !"aucune".equals(mode) && ("continue".equals(mode) || enManche) && morceau == null;
        if (piste != null && piste.fini(sm)) {
            if (piste.duree() < 40) ECHECS.add(piste.id);
            piste = null;
            prochain++;
            pause = 70;
        }
        if (!veut) {
            if (piste != null) {
                piste.arreter(60);
                piste = null;
                prochain++;
            }
            return;
        }
        if (piste == null && --pause <= 0 && mc.options.getSoundSourceVolume(SoundSource.MUSIC) > 0) {
            if (PLAYLIST.isEmpty()) reconstruire();
            PLAYLIST.removeAll(ECHECS);
            if (PLAYLIST.isEmpty()) {
                pause = 200;
                return;
            }
            ResourceLocation r = PLAYLIST.get(Math.floorMod(prochain, PLAYLIST.size()));
            piste = new Piste(r, SoundSource.MUSIC, false, false, true);
            piste.fondu(1, 0.025F);
            lancer(piste);
        }
    }

    private static void lancer(Piste p) {
        try {
            if (p.getSource() == SoundSource.MUSIC) HeraliszMatchs.LOG.info("[zombies] musique : {}", p.id);
            Minecraft.getInstance().getSoundManager().play(p);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[zombies] musique {} : {}", p.id, ex.toString());
            ECHECS.add(p.id);
        }
    }

    // ------------------------------------------------------------------ jingles du serveur
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void son(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null) return;
        ResourceLocation id = s.getLocation();
        if (!HeraliszMatchs.MODID.equals(id.getNamespace())) return;
        if (Boisson.etoufferSonServeur(id)) {
            e.setSound(null);
            return;
        }
        String p = id.getPath();
        ResourceLocation r = null;
        switch (p) {
            case "zb_manche_debut" -> {
                moment(MomentMusique.MANCHE_DEBUT);
                r = choisir("manche_debut", J_DEBUT);
            }
            case "zb_manche_fin" -> {
                moment(MomentMusique.MANCHE_FIN);
                r = choisir("manche_fin", J_FIN);
            }
            case "zb_speciale_debut" -> {
                moment(MomentMusique.SPECIALE_DEBUT);
                r = choisir("speciale_debut", null);
                // v3 : l'annonce des chiens de l'utilisateur (« Fetch me their souls! ») si pas de jingle a lui
                if (r == null) r = choisir("annonce_chiens", null);
            }
            case "zb_teleporteur" -> r = choisir("teleporteur", null);
            case "zb_partie_fin" -> {
                moment(MomentMusique.PARTIE_FIN);
                r = choisir("partie_fin", null);
            }
            case "zb_courant" -> {
                moment(MomentMusique.COURANT);
                r = choisir("courant", null);
            }
            case "zb_boite_roulette" -> r = choisir(null, J_BOITE);
            case "zb_boite_peluche" -> r = choisir(null, J_PELUCHE);
            default -> {
            }
        }
        if (r == null) return;
        try {
            s.resolve(Minecraft.getInstance().getSoundManager());
            e.setSound(new SimpleSoundInstance(r, s.getSource(), s.getVolume(), s.getPitch(), RandomSource.create(), false, 0,
                    s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative()));
            HeraliszMatchs.LOG.info("[zombies] jingle {} -> {}", id, r);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[zombies] remplacement de {} impossible : {}", id, ex.toString());
        }
    }

    /** Fichier de l'utilisateur s'il est present, sinon le jingle classique integre (si reglage), sinon rien. */
    @Nullable
    private static ResourceLocation choisir(@Nullable String fichier, @Nullable ResourceLocation integre) {
        if (fichier != null && PackMusiques.present(fichier)) return PackMusiques.evenement(fichier);
        if (integre != null && ZbConfigClient.jinglesClassiques()) return integre;
        return null;
    }

    // ------------------------------------------------------------------ commandes client
    @SubscribeEvent
    public static void commandes(RegisterClientCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("zbmusique")
                .then(Commands.literal("volume").then(Commands.argument("pourcent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    int v = IntegerArgumentType.getInteger(c, "pourcent");
                    ZbConfigClient.VOLUME_MUSIQUE.set(v / 100.0);
                    ZbConfigClient.SPEC.save();
                    retour(Component.translatable("zombies.client.musique.volume", v));
                    return v;
                })))
                .then(Commands.literal("suivante").executes(c -> {
                    if (piste != null) {
                        piste.arreter(15);
                        piste = null;
                        prochain++;
                        pause = 20;
                    }
                    retour(Component.translatable("zombies.client.musique.suivante"));
                    return 1;
                }))
                .then(Commands.literal("liste").executes(c -> {
                    reconstruire();
                    List<String> noms = new ArrayList<>();
                    for (ResourceLocation r : PLAYLIST) noms.add(r.equals(M_115) ? "115" : r.getPath().replace("zombies.", ""));
                    for (String s : PackMusiques.presents()) if (!s.startsWith("musique_")) noms.add(s);
                    String l = noms.isEmpty() ? "-" : String.join(", ", noms);
                    retour(Component.translatable("zombies.client.musique.liste", l, PackMusiques.dossier().toString()));
                    return noms.size();
                }))
                .then(Commands.literal("dossier").executes(c -> {
                    PackMusiques.preparerDossier();
                    retour(Component.translatable("zombies.client.musique.dossier", PackMusiques.dossier().toString()));
                    return 1;
                })));
    }

    /** Reponse d'une commande client : affichee directement dans le chat du joueur (pas de sendSuccess). */
    private static void retour(Component msg) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(msg, false);
    }

    // ------------------------------------------------------------------ piste
    /**
     * Son non positionnel en flux avec fondu (niveau -> cible), volume = niveau x reglage x baisse pendant les jingles.
     * Peut demarrer en silence (fondu d'entree).
     */
    static final class Piste extends AbstractTickableSoundInstance {
        final ResourceLocation id;
        private final boolean fondAmbiance, baisse;
        private float niveau, cible, vitesse = 0.02F;
        private boolean sortie, lancee;
        private int ticks, essais;

        Piste(ResourceLocation id, SoundSource source, boolean boucle, boolean fondAmbiance, boolean baisse) {
            super(SoundEvent.createVariableRangeEvent(id), source, SoundInstance.createUnseededRandom());
            this.id = id;
            this.fondAmbiance = fondAmbiance;
            this.baisse = baisse;
            this.looping = boucle;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.0001F;
        }

        void fondu(float c, float v) {
            cible = c;
            vitesse = v;
        }

        void arreter(int ticksFondu) {
            sortie = true;
            cible = 0;
            vitesse = Math.max(0.01F, niveau / Math.max(1, ticksFondu));
        }

        int duree() {
            return ticks;
        }

        /** Vrai si la piste a joue puis s'est arretee (fin du flux, arret, son introuvable). */
        boolean fini(SoundManager sm) {
            if (isStopped()) return true;
            if (sm.isActive(this)) {
                lancee = true;
                return false;
            }
            // jamais lancee (son introuvable, volume coupe) : abandon apres quelques images
            return lancee || ++essais > 3;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            ticks++;
            lancee = true;
            if (niveau < cible) niveau = Math.min(cible, niveau + vitesse);
            else if (niveau > cible) niveau = Math.max(cible, niveau - vitesse);
            if (sortie && niveau <= 0.0005F) {
                stop();
                return;
            }
            float reglage = fondAmbiance ? ZbConfigClient.volumeAmbiance() : ZbConfigClient.volumeMusique();
            float b = baisse && Util.getMillis() < etouffeJusqua ? 0.3F : 1F;
            // l'ambiance s'efface un peu sous la musique
            if (fondAmbiance && piste != null && !piste.sortie) b = 0.7F;
            this.volume = Math.max(0.0001F, niveau * reglage * b);
        }
    }
}
