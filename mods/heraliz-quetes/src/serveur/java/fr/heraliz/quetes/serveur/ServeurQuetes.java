package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.objet.Baguette;
import fr.heraliz.quetes.pont.Partie;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur de HeraliZ Quêtes : fichiers des PNJ, progression des joueurs, dialogues, commandes, et les événements
 * du jeu qui font avancer les quêtes (créatures tuées, clics sur les points, endroits atteints, extraction, mort).
 */
public final class ServeurQuetes implements Partie {
    private static int ticks;
    /** Dernières lueurs envoyées à chaque joueur (pour ne renvoyer que ce qui change). */
    private static final Map<UUID, Integer> LUEURS = new HashMap<>();

    @Override
    public void init(IEventBus busMod) {
        HeralizQuetes.PNJ_USINE.serveur(PnjServeur::new);
        Baguette.SERVEUR = ServeurQuetes::baguette;
        Reseau.CHOIX.brancher(r -> Dialogues.choix(r.joueur(), r.message()));
        Reseau.DEMANDE_JOURNAL.brancher(r -> Moteur.envoyerJournal(r.joueur(), r.message().ouvrir()));
        Reseau.ACTION.brancher(r -> action(r.joueur(), r.message()));
        Reseau.ACHAT.brancher(r -> Marchands.achat(r.joueur(), r.message()));
        Reseau.VENTE.brancher(r -> Marchands.vente(r.joueur(), r.message()));
        Reseau.MODIF.brancher(r -> EditionMarchands.modif(r.joueur(), r.message()));
        Reseau.CODE.brancher(r -> Moteur.code(r.joueur(), r.message().point(), r.message().code()));

        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::arret);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commandes.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::connexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::changementMonde);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServeurQuetes::mort);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServeurQuetes::blessure);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ServeurQuetes::clicBloc);
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::tickJoueur);
        MinecraftForge.EVENT_BUS.addListener(ServeurQuetes::tickServeur);
        MinecraftForge.EVENT_BUS.addListener(SaisonQuetes::ramassage);
    }

    private static void demarrage(ServerStartedEvent e) {
        Moteur.demarrer(e.getServer());
        Donnees.demarrer(e.getServer().getServerDirectory().toPath());
        EditionMarchands.demarrer(e.getServer().getServerDirectory().toPath());
        Catalogue.charger();
        Ponts.relireArmes();
        LUEURS.clear();
    }

    private static void arret(ServerStoppingEvent e) {
        Donnees.arreter();
        Dialogues.vider();
        Marchands.vider();
        Moteur.arreter();
        LUEURS.clear();
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || Moteur.serveur == null) return;
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        j.nom = p.getGameProfile().getName();
        Moteur.nettoyer(p);
        Moteur.envoyerSuivi(p);
        WipeServeur.connexion(p);
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (Moteur.serveur == null) return;
        Dialogues.oublier(e.getEntity().getUUID());
        LUEURS.remove(e.getEntity().getUUID());
        Moteur.oublier(e.getEntity().getUUID());
        Marchands.oublier(e.getEntity().getUUID());
        Accueil.oublier(e.getEntity().getUUID());
        SaisonQuetes.oublier(e.getEntity().getUUID());
        Donnees.liberer(e.getEntity().getUUID());
    }

    private static void changementMonde(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || Moteur.serveur == null || !p.isAlive()) return;
        if (e.getTo().location().toString().equals(Moteur.SPAWN) && !e.getFrom().location().toString().equals(Moteur.SPAWN)) {
            Moteur.arriveeSpawn(p);
        } else Moteur.envoyerSuivi(p);
    }

    private static void mort(LivingDeathEvent e) {
        if (Moteur.serveur == null || e.getEntity().level().isClientSide) return;
        if (e.getEntity() instanceof ServerPlayer p) {
            if (p.getTags().contains("heraliz_bot")) return;   // bots de charge (/bots) : ni étape « exécuter » ni mort comptée
            // achevé à terre (exécution de Réanimation ou coup de grâce) : l'état « à terre » dure jusqu'à la mort
            if (Ponts.aTerre(p) && e.getSource().getEntity() instanceof ServerPlayer tueur) Moteur.execution(p, tueur);
            Moteur.mortJoueur(p);
            return;
        }
        if (e.getEntity() instanceof Pnj) return;
        ServerPlayer tueur = e.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        Moteur.mort(e.getEntity(), tueur);
    }

    private static void blessure(LivingHurtEvent e) {
        if (Moteur.serveur == null || e.getAmount() <= 0 || !(e.getEntity() instanceof ServerPlayer p)) return;
        Moteur.blesse(p);
    }

    private static void clicBloc(PlayerInteractEvent.RightClickBlock e) {
        if (Moteur.serveur == null || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.getMainHandItem().getItem() instanceof Baguette) return;
        BlockPos pos = e.getPos();
        if (Moteur.clic(p, pos, p.level().getBlockState(pos))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void tickJoueur(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Moteur.serveur == null || !(e.player instanceof ServerPlayer p)) return;
        int t = p.tickCount;
        if (t % 10 == 0) Moteur.tick(p);
        if (t % 10 == 3) SaisonQuetes.fabrication(p);
        if (t % 20 == 5) {
            Donnees.DJoueur j = Donnees.de(p.getUUID());
            if (!j.suivie.isEmpty()) Moteur.suiviBientot(p);
        }
        if (t % 20 == 13) Accueil.seconde(p);
        if (t % 40 == 7) lueurs(p);
    }

    private static void tickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Moteur.serveur == null) return;
        Moteur.envoyerSuivisEnAttente();
        WipeServeur.tick(e.getServer());
        if (++ticks % 600 == 0) Donnees.toutEcrire();
    }

    private static void action(ServerPlayer p, Reseau.Action a) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        switch (a.type()) {
            case 1 -> {
                if (j.actives.containsKey(a.cle())) j.suivie = a.cle();
            }
            case 2 -> {
                if (a.cle().equals(j.suivie)) j.suivie = "";
            }
            case 3 -> Moteur.abandonner(p, a.cle(), false);
            case 4 -> {
                // Espace maintenu pendant la visite du Guide (1.10.4) : visite passée, comme finie
                Accueil.passerVisite(p);
                return;
            }
            default -> {
                return;
            }
        }
        Donnees.sale(p.getUUID());
        Moteur.envoyerSuivi(p);
        Moteur.envoyerJournal(p, false);
    }

    private static void lueurs(ServerPlayer p) {
        List<BlockPos> quete = Moteur.lueursQuete(p);
        Accueil.lueurs(p, quete);
        List<BlockPos> staff = new ArrayList<>();
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof Baguette && p.hasPermissions(2)) {
            String monde = p.level().dimension().location().toString();
            for (Points.Point pt : Moteur.points().tous()) {
                if (pt.monde().equals(monde) && pt.pos().distToCenterSqr(p.position()) < 48 * 48) staff.add(pt.pos());
            }
        }
        int h = quete.hashCode() * 31 + staff.hashCode();
        Integer avant = LUEURS.put(p.getUUID(), h);
        if (avant != null && avant == h) return;
        Reseau.envoyer(p, new Reseau.Lueurs(quete, staff));
    }

    /** Baguette du staff : clic droit = poser un point du groupe ; accroupi = retirer le point du bloc. */
    private static InteractionResult baguette(UseOnContext c) {
        if (!(c.getPlayer() instanceof ServerPlayer p) || !p.hasPermissions(2) || Moteur.serveur == null) return InteractionResult.PASS;
        Points pts = Moteur.points();
        BlockPos pos = c.getClickedPos();
        var monde = c.getLevel().dimension().location();
        if (p.isShiftKeyDown()) {
            Points.Point pt = pts.auBloc(monde, pos);
            if (pt != null && pts.retirer(pt.id())) {
                p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.cmd.point_retire", "Point %s retiré.", pt.id()));
            }
            LUEURS.remove(p.getUUID());
            return InteractionResult.SUCCESS;
        }
        String groupe = c.getItemInHand().hasTag() ? c.getItemInHand().getTag().getString(Baguette.GROUPE) : "";
        if (groupe.isEmpty()) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.cmd.baguette_vide",
                    "Cette baguette n'a pas de groupe : /quete baguette <groupe>."));
            return InteractionResult.SUCCESS;
        }
        Points.Point deja = pts.auBloc(monde, pos);
        Points.Point pt = pts.ajouter(groupe, monde, pos, "");
        p.sendSystemMessage(deja != null
                ? Component.translatableWithFallback("heraliz_quetes.cmd.point_deja", "Ce bloc est déjà le point %s.", deja.id())
                : Component.translatableWithFallback("heraliz_quetes.cmd.point_ajoute", "Point %s posé en %s %s %s.", pt.id(),
                pos.getX(), pos.getY(), pos.getZ()));
        LUEURS.remove(p.getUUID());
        return InteractionResult.SUCCESS;
    }
}
