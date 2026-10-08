package fr.heralisz.matchs.zombies.compat;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.n07.api.DamageRemap;
import fr.heralisz.n07.api.Encounter;
import fr.heralisz.n07.api.EncounterSpec;
import fr.heralisz.n07.api.HallLayout;
import fr.heralisz.n07.api.N07Api;
import fr.heralisz.n07.api.N07Events;
import fr.heralisz.n07.api.PlacedHall;
import fr.heralisz.n07.api.ZombiesBalance;
import fr.heralisz.n07.api.scene.SceneHandle;
import fr.heralisz.n07.api.scene.SkipMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptateur du boss final de The Giant (acte IV, D-057) vers l'API publique du mod {@code heralisz_n07} 1.1.0
 * ({@code fr.heralisz.n07.api}, contrat {@code N07Api.VERSION} 1, work/zombies_v3/boss_n07/API.md). Seules les classes de
 * ce paquet touchent au mod du boss : sans lui, rien n'est charge et l'acte IV acheve la quete a la porte, sans combat.
 * <ul>
 *     <li>Salle : hall + puits poses avec l'instance (structures annexes de la carte) ; ici on les lie
 *     ({@code bindHall}) et on les remet a zero par tranches ({@code reset} : seules les cases differentes du gabarit sont
 *     reecrites, 6000 cases lues par tick, ~15 ticks, sans gel) au debut de chaque partie et apres chaque combat.</li>
 *     <li>Combat : equilibrage Zombies du boss (PV selon joueurs et manche, Pack-a-Punch applique par le jeu aux tirs,
 *     degats ramenes aux 20 / 50 PV des joueurs), pas de laisse ni de butin, statue gardee pour l'outro, musique jouee par
 *     le jeu, joueurs a terre ni vises ni blesses, chaque entite marquee pour la purge de fin de partie.</li>
 *     <li>Scenes : intro de The Giant (passable a la majorite, ESPACE maintenu), outro apres la mort ; le teleporteur 935
 *     renvoie les joueurs au jeu ({@link ZombiesApi.EvenementsBoss#retour}).</li>
 * </ul>
 */
public final class N07Final implements ZombiesApi.BossFinal {
    /** Cases posees / comparees par tick pour une remise a zero de la salle (~90 000 cases : 15 ticks). */
    private static final int PAR_TICK = 6000;

    private N07Final() {
    }

    /** Enregistrement (setup commun, mod du boss present) : verifie la version du contrat a l'execution. */
    public static void enregistrer() {
        if (!ModList.get().isLoaded("heralisz_n07")) return;
        int v;
        try {
            // lu par reflexion : N07Api.VERSION est une constante, javac la recopierait ici
            v = Class.forName("fr.heralisz.n07.api.N07Api").getField("VERSION").getInt(null);
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[zombies] heralisz_n07 present mais API absente ({}) : acte IV sans boss", ex.toString());
            return;
        }
        if (v != 1) {
            HeraliszMatchs.LOG.warn("[zombies] heralisz_n07 : API version {} (attendue 1) : acte IV sans boss", v);
            return;
        }
        ZombiesApi.bossFinal(new N07Final());
    }

    @Nullable
    @Override
    public ZombiesApi.SalleBoss salle(ServerLevel niveau, ZombiesApi.PlanSalle plan) {
        HallLayout l = N07Api.layout(plan.variante());
        if (l == null) {
            HeraliszMatchs.LOG.warn("[zombies] N-07 : variante de salle {} inconnue", plan.variante());
            return null;
        }
        PlacedHall h = N07Api.bindHall(niveau, plan.variante(), plan.origine(), plan.rotation());
        if (h == null) return null;
        // la salle doit etre posee (instance posee avec les annexes) : son terminal a un bloc-entite
        if (niveau.getBlockEntity(h.world(l.terminal)) == null) {
            N07Api.unbindHall(h);
            HeraliszMatchs.LOG.warn("[zombies] N-07 : pas de salle en {} (instance posee sans le mod du boss ?) : acte IV sans boss",
                    plan.origine().toShortString());
            return null;
        }
        Salle s = new Salle(h);
        h.events(s.ecouteur);
        // etat propre a chaque partie (meme apres un arret brutal en plein combat) : boss, statue et objets retires, blocs
        // differents du gabarit reecrits, buses coupees, rigoles seches, grille ouverte, teleporteur eteint
        h.reset(PAR_TICK, () -> HeraliszMatchs.LOG.info("[zombies] N-07 : salle {} prete", plan.origine().toShortString()));
        return s;
    }

    /** Une salle liee a une instance, et le combat qui s'y joue. */
    private static final class Salle implements ZombiesApi.SalleBoss {
        final PlacedHall h;
        @Nullable
        Encounter enc;
        @Nullable
        ZombiesApi.ContexteBoss ctx;
        /** Evenements du jeu (combat en cours ou dernier combat) : le teleporteur reste branche apres la victoire. */
        ZombiesApi.EvenementsBoss ev = new ZombiesApi.EvenementsBoss() {
        };
        boolean outro;
        final N07Events ecouteur = new N07Events() {
            @Override
            public void onCinematicStart(Encounter e, int kind, int ticks, List<ServerPlayer> viewers) {
                ev.cinematique(true);
            }

            @Override
            public void onCinematicEnd(Encounter e, int kind) {
                ev.cinematique(false);
            }

            @Override
            public void onHook(Encounter e, String hook) {
                ev.crochet(hook);
            }

            @Override
            public void onDeathStart(Encounter e) {
                ev.cinematique(true);
                ev.mortDebut();
            }

            @Override
            public void onDeath(Encounter e) {
                ev.victoire();
                jouerOutro(e);
            }

            @Override
            public void onAborted(Encounter e, String reason) {
                ev.cinematique(false);
                ev.abandon(reason);
            }

            @Override
            public void onReturnRequested(ServerPlayer player, ReturnReason reason) {
                ev.retour(player, reason == ReturnReason.EVACUATION);
            }
        };

        Salle(PlacedHall h) {
            this.h = h;
        }

        @Override
        public boolean lancer(ZombiesApi.ContexteBoss c) {
            if (enc != null && enc.active()) return false;
            ctx = c;
            ev = c.evenements();
            outro = false;
            // degats Zombies du boss (table ZombiesBalance x echelle de la manche) ; le montant exact est confie au jeu, qui
            // l'applique tel quel (sans le reglage de difficulte du serveur)
            DamageRemap table = ZombiesBalance.remap(ZombiesBalance.damageScaleForRound(c.manche()), ZombiesBalance.MAX_HIT);
            ZombiesApi.EvenementsBoss e0 = ev;
            DamageRemap remap = (player, attack, base, current, phase) -> {
                float d = table.remap(player, attack, base, current, phase);
                if (player instanceof ServerPlayer sp && d > 0) e0.degats(sp, d);
                return d;
            };
            EncounterSpec spec;
            try {
                spec = EncounterSpec.builder(c.niveau()).hall(h)
                        .players(() -> new ArrayList<>(c.joueurs().get()))
                        .players(c.nbJoueurs(), c.manche())
                        .papOnBossHits(true)
                        .damageRemap(remap)
                        .targetable(p -> p instanceof ServerPlayer sp && c.cible().test(sp))
                        .marker(c.marqueur())
                        .skipMode(SkipMode.MAJORITY)
                        .loot(false).music(false).leash(false).blackoutPhase3(true)
                        .events(ecouteur)
                        .build();
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] N-07 : reglages du combat refuses", ex);
                return false;
            }
            Encounter e = N07Api.prepare(spec);
            if (e == null) {
                HeraliszMatchs.LOG.warn("[zombies] N-07 : combat impossible (boss non cree)");
                return false;
            }
            enc = e;
            ev.cinematique(true);
            AtomicBoolean main = new AtomicBoolean();
            SceneHandle intro = N07Api.playGiantIntro(e, null, SkipMode.MAJORITY, r -> {
                if (main.compareAndSet(false, true)) {
                    ev.cinematique(false);
                    ev.main();
                }
            });
            if (intro == null) {
                // pas d'intro (aucun spectateur, salle absente) : reveil direct, combat
                HeraliszMatchs.LOG.warn("[zombies] N-07 : intro impossible, reveil direct");
                e.wake();
                if (main.compareAndSet(false, true)) {
                    ev.cinematique(false);
                    ev.main();
                }
            }
            HeraliszMatchs.LOG.info("[zombies] N-07 : combat prepare ({} joueur(s), manche {}, {} PV virtuels)", c.nbJoueurs(), c.manche(), e.virtualHp());
            return true;
        }

        /** Outro apres la mort (une seule fois) ; si la scene ne peut pas se jouer, le teleporteur s'allume tout de suite. */
        void jouerOutro(Encounter e) {
            if (outro) return;
            outro = true;
            AtomicBoolean fini = new AtomicBoolean();
            ev.cinematique(true);
            SceneHandle o = N07Api.playGiantOutro(e, SkipMode.MAJORITY, r -> {
                if (fini.compareAndSet(false, true)) {
                    ev.cinematique(false);
                    ev.outroFinie();
                }
            });
            if (o == null && fini.compareAndSet(false, true)) {
                h.teleporter(true);
                ev.cinematique(false);
                ev.outroFinie();
            }
        }

        @Override
        public boolean enCombat() {
            return enc != null && enc.active();
        }

        @Override
        public List<Vec3> renforts() {
            return h.reinforcementSpawns();
        }

        @Override
        public Vec3 largage() {
            return h.dropPoint();
        }

        @Override
        public AABB interieur() {
            return h.interior();
        }

        @Override
        public AABB volume() {
            return h.bounds();
        }

        @Override
        public void evacuer() {
            h.evacuate();
        }

        @Override
        public void remettre() {
            if (enc != null && enc.active()) enc.abort("fin de partie");
            enc = null;
            h.reset(PAR_TICK, () -> HeraliszMatchs.LOG.info("[zombies] N-07 : salle remise a zero apres le combat"));
        }

        @Override
        public void oublier() {
            ev = new ZombiesApi.EvenementsBoss() {
            };
            h.events(null);
            N07Api.unbindHall(h);
        }

        @Override
        public void devVictoire() {
            Encounter e = enc;
            Mob b = e == null ? null : e.boss();
            if (b == null || e.state() != Encounter.State.FIGHT) return;
            b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
        }

        @Override
        public String resume() {
            Encounter e = enc;
            if (e == null) return "salle liee, pas de combat";
            return String.format(Locale.ROOT, "%s phase %d PV %.0f%% (%d virtuels) teleporteur=%b", e.state(), e.phase(), e.healthFraction() * 100,
                    e.virtualHp(), h.teleporterActive());
        }
    }
}
