package fr.heralisz.matchs.duels.killcam;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Killcam des duels (MATCHS_SPEC §3), module d'entree appele par {@code Modules} : le serveur enregistre en continu,
 * pour chaque joueur d'une partie, les 5 dernieres secondes (anneau a 20 Hz : position, rotations, posture, objet tenu,
 * tirs TaCZ) ; a une elimination il envoie a la victime les 4 secondes precedentes ; le client les rejoue avec des
 * joueurs factices (skins reels), camera par-dessus l'epaule du tueur, sons de tir et traceurs, bandeau « KILLCAM »,
 * touche pour passer (voir {@code client.KillcamClient}).
 * <p><b>API pour l'agent des duels</b> (fil serveur) :
 * <ul>
 *     <li>{@link #enregistreur(Partie)} au debut de la partie (avant le premier combat) ; s'arrete seul en
 *     {@code TERMINEE}. {@link Enregistreur#suivre(LivingEntity)} pour un bot d'entrainement ;</li>
 *     <li>{@link #surElimination(Partie, ServerPlayer, LivingEntity)} dans {@code Partie.surElimination}, <b>avant</b>
 *     de deplacer la victime (spectateur, reapparition) : capture l'image du tick, envoie la killcam a la victime et la
 *     garde comme « derniere » ;</li>
 *     <li>{@link #rejouerDerniere(Partie, Collection)} en fin de manche (recherche et destruction) ;</li>
 *     <li>{@link #enCours(UUID)} pour attendre la fin de la killcam avant la reapparition (le client previent quand
 *     il a fini ou passe ; garde-fou {@link #DELAI_MAX_MS}) et {@link #arreter(ServerPlayer)} pour l'interrompre.</li>
 * </ul>
 * Sans TaCZ, les tirs ne sont pas enregistres (le reste fonctionne). Commandes op : {@code /killcam essai [rayon]}
 * (enregistrement factice qui tourne autour du joueur), {@code /killcam enregistrer} puis {@code /killcam rejouer}
 * (enregistrement reel de soi-meme), {@code /killcam etat}.
 */
public final class KillcamLogique {
    private KillcamLogique() {
    }

    /** Images envoyees a la victime : 4 s a 20 Hz. */
    public static final int IMAGES_ENVOYEES = 80;
    /** Rayon autour du tueur dans lequel les autres joueurs figurent dans la killcam. */
    public static final double RAYON = 64;
    /** Duree d'une lecture complete cote client (ralenti et fondus compris), a titre indicatif. */
    public static final int DUREE_LECTURE_MS = 5600;
    /** Au-dela, une killcam est consideree finie meme sans nouvelle du client. */
    public static final int DELAI_MAX_MS = 9000;


    private static final Map<Partie, Enregistreur> ENREGISTREURS = new IdentityHashMap<>();
    /** Enregistreurs de {@code /killcam enregistrer} (un par joueur, sans partie). */
    private static final Map<UUID, Enregistreur> ESSAIS = new HashMap<>();
    /** Killcams en cours : joueur -> echeance (ms). */
    private static final Map<UUID, Long> EN_COURS = new HashMap<>();
    /** Tampon reutilise pour parcourir les enregistreurs sans allocation. */
    private static final List<Enregistreur> TAMPON = new ArrayList<>();
    private static boolean taczBranche;


    // ------------------------------------------------------------------ branchement (Modules)
    public static void construire(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(KillcamLogique::tick);
        MinecraftForge.EVENT_BUS.addListener(KillcamLogique::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(KillcamLogique::arret);
        brancherTacz();
    }


    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        d.register(Commands.literal("killcam").requires(s -> s.hasPermission(2))
                .then(Commands.literal("essai")
                        .executes(ctx -> cmdEssai(ctx, 6))
                        .then(Commands.argument("rayon", DoubleArgumentType.doubleArg(2, 24))
                                .executes(ctx -> cmdEssai(ctx, DoubleArgumentType.getDouble(ctx, "rayon")))))
                .then(Commands.literal("enregistrer").executes(KillcamLogique::cmdEnregistrer))
                .then(Commands.literal("rejouer").executes(KillcamLogique::cmdRejouer))
                .then(Commands.literal("etat").executes(ctx -> {
                    Annonces.retour(ctx.getSource(), Component.translatable("killcam.cmd.etat", ENREGISTREURS.size() + ESSAIS.size(),
                            EN_COURS.size()));
                    return 1;
                })));
    }

    // ------------------------------------------------------------------ API
    /** Enregistreur de cette partie (cree et demarre au premier appel). */
    public static Enregistreur enregistreur(Partie partie) {
        return ENREGISTREURS.computeIfAbsent(partie, Enregistreur::new);
    }

    /** Enregistreur de cette partie s'il existe, sinon null. */
    @Nullable
    public static Enregistreur enregistreurDe(Partie partie) {
        return ENREGISTREURS.get(partie);
    }

    /**
     * A appeler dans {@code Partie.surElimination}, avant de deplacer la victime : capture l'image du tick, extrait les
     * 4 dernieres secondes, les envoie a la victime et les garde comme derniere killcam de la partie. Rend faux (rien
     * n'est envoye) sans enregistreur, sans tueur, pour un suicide ou si l'une des pistes manque.
     */
    public static boolean surElimination(Partie partie, ServerPlayer victime, @Nullable LivingEntity tueur) {
        return surElimination(partie, victime, tueur, true);
    }

    /**
     * Variante : {@code envoyer} = faux garde seulement la killcam comme « derniere » (a rejouer en fin de manche avec
     * {@link #rejouerDerniere}).
     */
    public static boolean surElimination(Partie partie, ServerPlayer victime, @Nullable LivingEntity tueur, boolean envoyer) {
        Enregistreur r = ENREGISTREURS.get(partie);
        if (r == null || tueur == null || tueur == victime || !r.actif()) return false;
        r.capturer(victime.server);
        Clip c = r.extraire(victime.getUUID(), tueur.getUUID(), IMAGES_ENVOYEES, RAYON, tueur, victime, Clip.RAISON_MORT);
        if (c == null) return false;
        marquerMort(c);
        r.derniere(c);
        if (envoyer) envoyer(victime, c);
        return true;
    }

    /**
     * Elimination d'une entite suivie qui n'est pas un joueur (cible d'entrainement) : la killcam est seulement gardee
     * comme « derniere » (rejouee en fin de manche par {@link #rejouerDerniere}). Memes refus que
     * {@link #surElimination}.
     */
    public static boolean garderElimination(Partie partie, LivingEntity victime, @Nullable LivingEntity tueur) {
        Enregistreur r = ENREGISTREURS.get(partie);
        if (r == null || tueur == null || tueur == victime || !r.actif() || victime.getServer() == null) return false;
        r.capturer(victime.getServer());
        Clip c = r.extraire(victime.getUUID(), tueur.getUUID(), IMAGES_ENVOYEES, RAYON, tueur, victime, Clip.RAISON_MORT);
        if (c == null) return false;
        marquerMort(c);
        r.derniere(c);
        return true;
    }

    /**
     * Rejoue la derniere killcam de la partie a ces joueurs (bandeau « derniere elimination »). Rend le nombre de
     * joueurs servis (0 s'il n'y en a pas).
     */
    public static int rejouerDerniere(Partie partie, Collection<ServerPlayer> joueurs) {
        Enregistreur r = ENREGISTREURS.get(partie);
        Clip c = r == null ? null : r.derniere();
        if (c == null) return 0;
        Clip d = c.avecRaison(Clip.RAISON_DERNIERE);
        int n = 0;
        for (ServerPlayer p : joueurs) {
            if (p == null || p.hasDisconnected()) continue;
            envoyer(p, d);
            n++;
        }
        return n;
    }

    /** Envoie un clip a ce joueur et le note « en cours ». */
    public static void envoyer(ServerPlayer joueur, Clip c) {
        MatchsNet.versJoueur(joueur, new PaquetsKillcam.ClipS2C(c));
        EN_COURS.put(joueur.getUUID(), System.currentTimeMillis() + DELAI_MAX_MS);
    }

    /** Vrai tant que ce joueur regarde une killcam (jusqu'a sa fin, son passage, ou {@link #DELAI_MAX_MS}). */
    public static boolean enCours(UUID joueur) {
        Long fin = EN_COURS.get(joueur);
        if (fin == null) return false;
        if (System.currentTimeMillis() > fin) {
            EN_COURS.remove(joueur);
            return false;
        }
        return true;
    }

    public static boolean enCours(ServerPlayer joueur) {
        return enCours(joueur.getUUID());
    }

    /** Interrompt la killcam de ce joueur (camera et HUD rendus aussitot). */
    public static void arreter(ServerPlayer joueur) {
        if (EN_COURS.remove(joueur.getUUID()) != null) MatchsNet.versJoueur(joueur, new PaquetsKillcam.ArretS2C());
    }

    /** Fin signalee par le client (paquet {@code FinC2S}). */
    static void finie(ServerPlayer joueur, boolean passee) {
        if (EN_COURS.remove(joueur.getUUID()) != null) {
            HeraliszMatchs.LOG.debug("[killcam] {} : killcam {}", joueur.getGameProfile().getName(), passee ? "passee" : "finie");
        }
    }

    /** La victime est marquee morte a la derniere image (les eliminations en match ne sont pas de vraies morts). */
    private static void marquerMort(Clip c) {
        Clip.Participant v = c.participants.get(c.victime);
        int n = c.images;
        for (int i = n - 1; i >= 0; i--) {
            if (v.present(i)) {
                v.drapeaux[i] |= Clip.MORT;
                break;
            }
        }
    }

    // ------------------------------------------------------------------ evenements serveur
    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (ENREGISTREURS.isEmpty() && ESSAIS.isEmpty()) return;
        TAMPON.clear();
        TAMPON.addAll(ENREGISTREURS.values());
        TAMPON.addAll(ESSAIS.values());
        for (Enregistreur r : TAMPON) {
            if (!r.actif()) {
                if (r.partie() != null) ENREGISTREURS.remove(r.partie());
                ESSAIS.values().remove(r);
                continue;
            }
            try {
                r.capturer(e.getServer());
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[killcam] capture en erreur ({}), enregistreur arrete", r.partie(), ex);
                r.arreter();
            }
        }
        TAMPON.clear();
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        EN_COURS.remove(e.getEntity().getUUID());
        Enregistreur r = ESSAIS.remove(e.getEntity().getUUID());
        if (r != null) r.arreter();
    }

    private static void arret(ServerStoppedEvent e) {
        ENREGISTREURS.clear();
        ESSAIS.clear();
        EN_COURS.clear();
    }

    // ------------------------------------------------------------------ TaCZ (reflexion tolerante)
    /**
     * Ecoute {@code com.tacz.guns.api.event.common.GunFireEvent} (une balle reellement tiree, rafales comprises ;
     * poste cote serveur par {@code ModernKineticGunScriptAPI}) sans dependance de compilation. TaCZ absent = aucun
     * tir enregistre.
     */
    private static void brancherTacz() {
        if (!ModList.get().isLoaded("tacz")) {
            HeraliszMatchs.LOG.info("[killcam] TaCZ absent : tirs non enregistres");
            return;
        }
        try {
            Class<?> k = Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
            Method tireur = k.getMethod("getShooter"), cote = k.getMethod("getLogicalSide");
            @SuppressWarnings("unchecked") Class<Event> ke = (Class<Event>) k;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ke, ev -> surTir(ev, tireur, cote));
            taczBranche = true;
            HeraliszMatchs.LOG.info("[killcam] tirs TaCZ ecoutes (GunFireEvent)");
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ex) {
            HeraliszMatchs.LOG.warn("[killcam] evenement de tir TaCZ introuvable : tirs non enregistres", ex);
        }
    }

    /** Vrai si l'evenement de tir TaCZ est ecoute. */
    public static boolean taczBranche() {
        return taczBranche;
    }

    private static void surTir(Event ev, Method tireur, Method cote) {
        if (ENREGISTREURS.isEmpty() && ESSAIS.isEmpty()) return;
        try {
            if (cote.invoke(ev) != LogicalSide.SERVER) return;
            if (!(tireur.invoke(ev) instanceof LivingEntity le) || le.level().isClientSide) return;
            if (le.getServer() == null || !le.getServer().isSameThread()) return;
            for (Enregistreur r : ENREGISTREURS.values()) r.tir(le);
            for (Enregistreur r : ESSAIS.values()) r.tir(le);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[killcam] tir TaCZ illisible", ex);
        }
    }

    // ------------------------------------------------------------------ commandes et essai
    private static int cmdEssai(CommandContext<CommandSourceStack> ctx, double rayon) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clip c = essai(p, rayon);
        if (c == null) {
            ctx.getSource().sendFailure(Component.translatable("killcam.cmd.echec"));
            return 0;
        }
        int octets = c.octets();
        envoyer(p, c);
        HeraliszMatchs.LOG.info("[killcam] essai envoye a {} : {} images, {} participants, {} tirs, {} objets, {} octets",
                p.getGameProfile().getName(), c.images, c.participants.size(), c.tirs.size(), c.objets.size(), octets);
        Annonces.retour(ctx.getSource(), Component.translatable("killcam.cmd.essai", c.images, c.tirs.size(), octets));
        return 1;
    }

    private static int cmdEnregistrer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Enregistreur r = new Enregistreur(null);
        r.suivre(p);
        Enregistreur ancien = ESSAIS.put(p.getUUID(), r);
        if (ancien != null) ancien.arreter();
        HeraliszMatchs.LOG.info("[killcam] enregistrement reel de {}", p.getGameProfile().getName());
        Annonces.retour(ctx.getSource(), Component.literal("killcam : enregistrement"));
        return 1;
    }

    private static int cmdRejouer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Enregistreur r = ESSAIS.remove(p.getUUID());
        if (r == null) {
            ctx.getSource().sendFailure(Component.translatable("killcam.cmd.echec"));
            return 0;
        }
        r.capturer(p.server);
        Clip c = r.extraire(p.getUUID(), p.getUUID(), IMAGES_ENVOYEES, RAYON, p, null, Clip.RAISON_ESSAI);
        r.arreter();
        if (c == null) {
            ctx.getSource().sendFailure(Component.translatable("killcam.cmd.echec"));
            return 0;
        }
        int octets = c.octets();
        envoyer(p, c);
        HeraliszMatchs.LOG.info("[killcam] rejeu reel envoye a {} : {} images, {} tirs, {} octets", p.getGameProfile().getName(), c.images,
                c.tirs.size(), octets);
        Annonces.retour(ctx.getSource(), Component.translatable("killcam.cmd.essai", c.images, c.tirs.size(), octets));
        return 1;
    }

    /**
     * Enregistrement factice (banc d'essai) : un tueur (profil du joueur, donc son vrai skin) court en cercle autour du
     * joueur pendant 5 s en le visant, s'accroupit, tire des rafales, et l'abat a la derniere image ; la victime
     * (« Dziga ») reste au centre et le suit des yeux. Les images passent par un vrai {@link Enregistreur} (anneau puis
     * extraction des 4 dernieres secondes), comme en match.
     */
    @Nullable
    public static Clip essai(ServerPlayer joueur, double rayon) {
        Enregistreur r = new Enregistreur(null);
        UUID tu = joueur.getUUID();
        UUID vu = UUID.nameUUIDFromBytes("heralisz:killcam:essai:Dziga".getBytes(StandardCharsets.UTF_8));
        String tn = joueur.getGameProfile().getName();
        double cx = joueur.getX(), cy = joueur.getY(), cz = joueur.getZ();
        ItemStack arme = joueur.getMainHandItem();
        int n = Enregistreur.CAPACITE;
        double a0 = Math.toRadians(joueur.getYRot() + 90);
        double[] kx = new double[n], kz = new double[n];
        for (int t = 0; t < n; t++) {
            double ang = a0 + 0.70 * 2 * Math.PI * t / (n - 1);
            kx[t] = cx + Math.cos(ang) * rayon;
            kz[t] = cz + Math.sin(ang) * rayon;
        }
        for (int t = 0; t < n; t++) {
            boolean accroupi = t >= 40 && t < 52;
            float[] vise = viser(kx[t], cy + (accroupi ? 1.27 : 1.62), kz[t], cx, cy + 1.3, cz);
            int dr = Clip.PRESENT | (accroupi ? Clip.ACCROUPI : Clip.SPRINT);
            r.injecter(tu, tn, true, 0x6FA8FF, t, kx[t], cy, kz[t], vise[0], vise[0], vise[1], dr, arme);
            // la victime suit le tueur des yeux avec un temps de retard
            int tl = Math.max(0, t - 6);
            float[] regard = viser(cx, cy + 1.62, cz, kx[tl], cy + 1.4, kz[tl]);
            int dv = Clip.PRESENT;
            if ((t >= 71 && t <= 73) || (t >= 77 && t <= 79) || t >= 91) dv |= Clip.BLESSE;
            if (t == n - 1) dv |= Clip.MORT;
            r.injecter(vu, "Dziga", true, 0xE85D5D, t, cx, cy, cz, regard[0], regard[0], regard[1], dv, ItemStack.EMPTY);
        }
        int[] tirs = {22, 23, 24, 46, 47, 48, 66, 68, 70, 72, 76, 78, 88, 90, 92, 94, 96, 98};
        java.util.Random alea = new java.util.Random(7);
        for (int t : tirs) {
            boolean accroupi = t >= 40 && t < 52;
            float[] vise = viser(kx[t], cy + (accroupi ? 1.27 : 1.62), kz[t], cx, cy + 1.3, cz);
            r.injecterTir(tu, t, vise[0] + (float) alea.nextGaussian() * 1.2f, vise[1] + (float) alea.nextGaussian() * 0.8f);
        }
        Clip c = r.extraire(vu, tu, IMAGES_ENVOYEES, RAYON, joueur, null, Clip.RAISON_ESSAI);
        return c;
    }

    /** Lacet et tangage (degres, conventions Minecraft) pour regarder de (x0, y0, z0) vers (x1, y1, z1). */
    static float[] viser(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double h = Math.sqrt(dx * dx + dz * dz);
        float lacet = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float tangage = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
        return new float[]{Mth.wrapDegrees(lacet), tangage};
    }
}
