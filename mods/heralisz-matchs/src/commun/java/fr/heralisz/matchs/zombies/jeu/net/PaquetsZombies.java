package fr.heralisz.matchs.zombies.jeu.net;

import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.ZombiesJeu;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquets du jeu zombies. S2C : etat du HUD, invite d'achat, evenements ponctuels, ecran de lancement, ecran de fin,
 * objectif, lore d'intro, ecran de l'etabli. C2S : LANCER (ecran /zombies), touche d'interaction (F enfoncee /
 * relachee), coup de couteau (fenetres), fabrication a l'etabli. Les C2S sont revalides cote serveur
 * ({@link ZombiesJeu}). Traitements client dans {@link ZombiesJeuClient}.
 */
public final class PaquetsZombies {
    /** Réception côté client des paquets serveur -> client (client/ReceptionPaquetsZombies) : branchée par la partie client. */
    public static final fr.heralisz.matchs.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    private PaquetsZombies() {
    }

    /** Ordre fixe (identifiants de paquets) : n'ajouter qu'a la fin. */
    public static void enregistrer(MatchsNet.Registre r) {
        r.s2c(EtatS2C.class, EtatS2C::write, EtatS2C::read, EtatS2C::handle);
        r.s2c(InviteS2C.class, InviteS2C::write, InviteS2C::read, InviteS2C::handle);
        r.s2c(EvenementS2C.class, EvenementS2C::write, EvenementS2C::read, EvenementS2C::handle);
        r.s2c(OuvrirS2C.class, OuvrirS2C::write, OuvrirS2C::read, OuvrirS2C::handle);
        r.s2c(FinS2C.class, FinS2C::write, FinS2C::read, FinS2C::handle);
        r.c2s(LancerC2S.class, LancerC2S::write, LancerC2S::read, LancerC2S::handle);
        r.c2s(ToucheC2S.class, ToucheC2S::write, ToucheC2S::read, ToucheC2S::handle);
        // v2
        r.s2c(ObjectifS2C.class, ObjectifS2C::write, ObjectifS2C::read, ObjectifS2C::handle);
        r.s2c(IntroS2C.class, IntroS2C::write, IntroS2C::read, IntroS2C::handle);
        r.s2c(AtelierS2C.class, AtelierS2C::write, AtelierS2C::read, AtelierS2C::handle);
        r.c2s(FabriquerC2S.class, FabriquerC2S::write, FabriquerC2S::read, FabriquerC2S::handle);
        r.c2s(CoupC2S.class, CoupC2S::write, CoupC2S::read, CoupC2S::handle);
        // v3
        r.s2c(LieuS2C.class, LieuS2C::write, LieuS2C::read, LieuS2C::handle);
    }

    // ------------------------------------------------------------------ S2C
    /** Etat complet du HUD (envoye aux changements, au plus toutes les 2 ticks). {@code actif} faux = effacer. */
    public record EtatS2C(boolean actif, int manche, boolean speciale, int restants, boolean courant, List<HudZombies.Joueur> joueurs, int[] bonus) {
        public static EtatS2C vide() {
            return new EtatS2C(false, 0, false, 0, false, List.of(), new int[0]);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(actif);
            buf.writeVarInt(manche);
            buf.writeBoolean(speciale);
            buf.writeVarInt(restants);
            buf.writeBoolean(courant);
            int n = Math.min(4, joueurs.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                HudZombies.Joueur j = joueurs.get(i);
                buf.writeUUID(j.id());
                buf.writeUtf(j.nom(), 32);
                buf.writeInt(j.couleur());
                buf.writeVarInt(j.points());
                buf.writeVarInt(j.kills());
                buf.writeVarInt(j.tetes());
                buf.writeVarInt(j.atouts());
                buf.writeVarInt(j.etat());
                buf.writeVarInt(j.secondes());
                buf.writeVarInt(j.materiaux());
                buf.writeVarInt(j.autoReanimations());
                buf.writeVarInt(j.plaques());
            }
            int b = Math.min(8, bonus.length);
            buf.writeVarInt(b);
            for (int i = 0; i < b; i++) buf.writeVarInt(bonus[i]);
        }

        public static EtatS2C read(FriendlyByteBuf buf) {
            boolean actif = buf.readBoolean();
            int manche = buf.readVarInt();
            boolean speciale = buf.readBoolean();
            int restants = buf.readVarInt();
            boolean courant = buf.readBoolean();
            int n = Math.min(4, buf.readVarInt());
            List<HudZombies.Joueur> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(new HudZombies.Joueur(buf.readUUID(), buf.readUtf(32), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            int b = Math.min(8, buf.readVarInt());
            int[] bonus = new int[b];
            for (int i = 0; i < b; i++) bonus[i] = buf.readVarInt();
            return new EtatS2C(actif, manche, speciale, restants, courant, l, bonus);
        }

        public static void handle(EtatS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Invite d'achat ou d'action (texte null = aucune), progression 0..100 d'un maintien, -1 sinon. */
    public record InviteS2C(@Nullable Component texte, int cout, boolean abordable, int progression) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(texte != null);
            if (texte != null) buf.writeComponent(texte);
            buf.writeVarInt(cout);
            buf.writeBoolean(abordable);
            buf.writeVarInt(progression + 1);
        }

        public static InviteS2C read(FriendlyByteBuf buf) {
            Component t = buf.readBoolean() ? buf.readComponent() : null;
            return new InviteS2C(t, buf.readVarInt(), buf.readBoolean(), buf.readVarInt() - 1);
        }

        public static void handle(InviteS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Evenement ponctuel pour le HUD.
     *
     * @param type    {@link #POINTS} (valeur = delta, extra = MotifPoints), {@link #MANCHE} (valeur = manche, extra =
     *                1 speciale | 2 debut), {@link #BONUS} (extra = TypeBonus, texte = nom), {@link #ATOUT} (extra =
     *                TypeAtout), {@link #ETAT} (extra = etat HudZombies, texte = nom), {@link #REFUS} (composant),
     *                {@link #ACHAT} (composant, valeur = cout), {@link #COURANT} ; v2 : {@link #MUSIQUE} (extra =
     *                MomentMusique, valeur = manche), {@link #PAP} (joueur, composant = arme, extra = niveau), {@link #FABRICATION}
     *                (joueur, composant = objet), {@link #MATERIAUX} (joueur, valeur = delta), {@link #EASTER_EGG}
     *                (valeur = etape, extra = total | 0x10000 si termine), {@link #BOITE} (extra = Rarete, composant = arme)
     */
    public record EvenementS2C(int type, UUID joueur, int valeur, int extra, String texte, Component composant) {
        public static final int POINTS = 0, MANCHE = 1, BONUS = 2, ATOUT = 3, ETAT = 4, REFUS = 5, ACHAT = 6, COURANT = 7;
        public static final int MUSIQUE = 8, PAP = 9, FABRICATION = 10, MATERIAUX = 11, EASTER_EGG = 12, BOITE = 13;
        /** v3 : le joueur vient d'etre teleporte (eclair bleu-blanc a l'ecran). */
        public static final int TELEPORT = 14;
        private static final UUID ZERO = new UUID(0L, 0L);

        public static EvenementS2C simple(int type, @Nullable UUID joueur, int valeur, int extra, String texte) {
            return new EvenementS2C(type, joueur == null ? ZERO : joueur, valeur, extra, texte == null ? "" : texte, Component.empty());
        }

        public static EvenementS2C message(int type, Component c, int valeur) {
            return new EvenementS2C(type, ZERO, valeur, 0, "", c);
        }

        public static EvenementS2C composant(int type, @Nullable UUID joueur, int valeur, int extra, Component c) {
            return new EvenementS2C(type, joueur == null ? ZERO : joueur, valeur, extra, "", c == null ? Component.empty() : c);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
            buf.writeUUID(joueur);
            buf.writeInt(valeur);
            buf.writeVarInt(extra);
            buf.writeUtf(texte, 64);
            buf.writeComponent(composant);
        }

        public static EvenementS2C read(FriendlyByteBuf buf) {
            return new EvenementS2C(buf.readVarInt(), buf.readUUID(), buf.readInt(), buf.readVarInt(), buf.readUtf(64), buf.readComponent());
        }

        public static void handle(EvenementS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Membre du groupe dans l'ecran de lancement ({@code refus} = cle de traduction d'un empechement, vide = pret). */
    public record Membre(UUID id, String nom, boolean chef, boolean enLigne, String refus) {
    }

    /** Carte proposee avec ses records (manche 0 = aucun). */
    public record CarteInfo(String id, String nomFr, String nomEn, int recordServeur, String recordServeurNoms, int recordGroupe) {
    }

    /** Ouvre l'ecran /zombies. */
    public record OuvrirS2C(List<Membre> membres, boolean chef, List<CarteInfo> cartes) {
        public void write(FriendlyByteBuf buf) {
            int n = Math.min(4, membres.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Membre m = membres.get(i);
                buf.writeUUID(m.id());
                buf.writeUtf(m.nom(), 32);
                buf.writeBoolean(m.chef());
                buf.writeBoolean(m.enLigne());
                buf.writeUtf(m.refus(), 64);
            }
            buf.writeBoolean(chef);
            int c = Math.min(16, cartes.size());
            buf.writeVarInt(c);
            for (int i = 0; i < c; i++) {
                CarteInfo ci = cartes.get(i);
                buf.writeUtf(ci.id(), 64);
                buf.writeUtf(ci.nomFr(), 64);
                buf.writeUtf(ci.nomEn(), 64);
                buf.writeVarInt(ci.recordServeur());
                buf.writeUtf(ci.recordServeurNoms(), 160);
                buf.writeVarInt(ci.recordGroupe());
            }
        }

        public static OuvrirS2C read(FriendlyByteBuf buf) {
            int n = Math.min(4, buf.readVarInt());
            List<Membre> m = new ArrayList<>(n);
            for (int i = 0; i < n; i++) m.add(new Membre(buf.readUUID(), buf.readUtf(32), buf.readBoolean(), buf.readBoolean(), buf.readUtf(64)));
            boolean chef = buf.readBoolean();
            int c = Math.min(16, buf.readVarInt());
            List<CarteInfo> l = new ArrayList<>(c);
            for (int i = 0; i < c; i++) {
                l.add(new CarteInfo(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readUtf(160), buf.readVarInt()));
            }
            return new OuvrirS2C(m, chef, l);
        }

        public static void handle(OuvrirS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Ligne de l'ecran de fin. */
    public record LigneFin(String nom, int couleur, int points, int kills, int tetes, int couteau, int reanimations, int aTerre) {
    }

    /** Ecran de fin de partie (manche atteinte, temps, stats, records). */
    public record FinS2C(String carte, int manche, long dureeMs, List<LigneFin> lignes, boolean recordGroupe, boolean recordServeur, int recordServeurManche) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(carte, 64);
            buf.writeVarInt(manche);
            buf.writeVarLong(dureeMs);
            int n = Math.min(4, lignes.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                LigneFin l = lignes.get(i);
                buf.writeUtf(l.nom(), 32);
                buf.writeInt(l.couleur());
                buf.writeVarInt(l.points());
                buf.writeVarInt(l.kills());
                buf.writeVarInt(l.tetes());
                buf.writeVarInt(l.couteau());
                buf.writeVarInt(l.reanimations());
                buf.writeVarInt(l.aTerre());
            }
            buf.writeBoolean(recordGroupe);
            buf.writeBoolean(recordServeur);
            buf.writeVarInt(recordServeurManche);
        }

        public static FinS2C read(FriendlyByteBuf buf) {
            String carte = buf.readUtf(64);
            int manche = buf.readVarInt();
            long duree = buf.readVarLong();
            int n = Math.min(4, buf.readVarInt());
            List<LigneFin> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(new LigneFin(buf.readUtf(32), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()));
            }
            return new FinS2C(carte, manche, duree, l, buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(FinS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Objectif courant : textes de la carte ({@code fr}/{@code en}, choisis selon la langue du client) ou, s'ils sont
     * vides, cle {@code zombies.jeu.objectif.<id>} ; {@code id} vide = plus d'objectif ; {@code nouveau} = le precedent
     * vient d'etre rempli ; {@code progres} (v3) = texte ajoute apres l'objectif ({@code (2/3)}, {@code A 0:24}).
     */
    /**
     * Objectif courant. 0.2.3 : {@code lieuFr}/{@code lieuEn} et {@code arg} (lettre d'un teleporteur) remplissent la cle
     * {@code zombies.jeu.objectif.<id>} ({@code %1$s} = lieu, {@code %2$s} = arg) ; {@code cible} = repere de la boussole
     * (null = aucun).
     */
    public record ObjectifS2C(String id, String fr, String en, boolean nouveau, String progres, String lieuFr, String lieuEn, String arg,
                              @javax.annotation.Nullable net.minecraft.world.phys.Vec3 cible) {
        public ObjectifS2C(String id, String fr, String en, boolean nouveau) {
            this(id, fr, en, nouveau, "");
        }

        public ObjectifS2C(String id, String fr, String en, boolean nouveau, String progres) {
            this(id, fr, en, nouveau, progres, "", "", "", null);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 32);
            buf.writeUtf(fr, 160);
            buf.writeUtf(en, 160);
            buf.writeBoolean(nouveau);
            buf.writeUtf(progres, 32);
            buf.writeUtf(lieuFr, 64);
            buf.writeUtf(lieuEn, 64);
            buf.writeUtf(arg, 16);
            buf.writeBoolean(cible != null);
            if (cible != null) {
                buf.writeDouble(cible.x);
                buf.writeDouble(cible.y);
                buf.writeDouble(cible.z);
            }
        }

        public static ObjectifS2C read(FriendlyByteBuf buf) {
            String id = buf.readUtf(32), fr = buf.readUtf(160), en = buf.readUtf(160);
            boolean nouveau = buf.readBoolean();
            String progres = buf.readUtf(32), lieuFr = buf.readUtf(64), lieuEn = buf.readUtf(64), arg = buf.readUtf(16);
            net.minecraft.world.phys.Vec3 c = buf.readBoolean() ? new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
            return new ObjectifS2C(id, fr, en, nouveau, progres, lieuFr, lieuEn, arg, c);
        }

        public static void handle(ObjectifS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Lore d'intro (lignes de la carte en francais et en anglais ; vides = texte par defaut {@code zombies.jeu.intro.*}). */
    public record IntroS2C(List<String> fr, List<String> en) {
        public void write(FriendlyByteBuf buf) {
            ecrire(buf, fr);
            ecrire(buf, en);
        }

        private static void ecrire(FriendlyByteBuf buf, List<String> l) {
            int n = Math.min(4, l.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeUtf(l.get(i), 160);
        }

        private static List<String> lire(FriendlyByteBuf buf) {
            int n = Math.min(4, buf.readVarInt());
            List<String> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(buf.readUtf(160));
            return l;
        }

        public static IntroS2C read(FriendlyByteBuf buf) {
            return new IntroS2C(lire(buf), lire(buf));
        }

        public static void handle(IntroS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Recette proposee a l'etabli ({@code possede} = quantite deja detenue, {@code max} = plafond, 0 = sans plafond). */
    public record Recette(String id, Component nom, Component description, int cout, int possede, int max) {
    }

    /** Ouvre (ou rafraichit) l'ecran de l'etabli. */
    public record AtelierS2C(int atelier, int materiaux, List<Recette> recettes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(atelier);
            buf.writeVarInt(materiaux);
            int n = Math.min(12, recettes.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Recette r = recettes.get(i);
                buf.writeUtf(r.id(), 32);
                buf.writeComponent(r.nom());
                buf.writeComponent(r.description());
                buf.writeVarInt(r.cout());
                buf.writeVarInt(r.possede());
                buf.writeVarInt(r.max());
            }
        }

        public static AtelierS2C read(FriendlyByteBuf buf) {
            int a = buf.readVarInt();
            int m = buf.readVarInt();
            int n = Math.min(12, buf.readVarInt());
            List<Recette> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(new Recette(buf.readUtf(32), buf.readComponent(), buf.readComponent(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new AtelierS2C(a, m, l);
        }

        public static void handle(AtelierS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Lieu du joueur (nom de la zone de la carte ou il se trouve, en francais et en anglais, choisi selon la langue du
     * client), affiche sous la boussole. Envoye seulement quand la zone change (verifie toutes les 10 ticks), et
     * renvoye toutes les 10 s (reconnexion).
     */
    public record LieuS2C(String fr, String en) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(fr, 64);
            buf.writeUtf(en, 64);
        }

        public static LieuS2C read(FriendlyByteBuf buf) {
            return new LieuS2C(buf.readUtf(64), buf.readUtf(64));
        }

        public static void handle(LieuS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S
    /** Bouton LANCER de l'ecran /zombies (le serveur revalide : chef, groupe, Coffre, carte). */
    public record LancerC2S(String carte) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(carte, 64);
        }

        public static LancerC2S read(FriendlyByteBuf buf) {
            return new LancerC2S(buf.readUtf(64));
        }

        public static void handle(LancerC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsZombiesServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Touche d'interaction enfoncee (vrai) ou relachee (faux). */
    public record ToucheC2S(boolean enfoncee) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enfoncee);
        }

        public static ToucheC2S read(FriendlyByteBuf buf) {
            return new ToucheC2S(buf.readBoolean());
        }

        public static void handle(ToucheC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsZombiesServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Fabrication d'une recette a l'etabli (le serveur revalide : distance, materiaux, plafond). */
    public record FabriquerC2S(int atelier, String recette) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(atelier);
            buf.writeUtf(recette, 32);
        }

        public static FabriquerC2S read(FriendlyByteBuf buf) {
            return new FabriquerC2S(buf.readVarInt(), buf.readUtf(32));
        }

        public static void handle(FabriquerC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsZombiesServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /**
     * Coup de melee donne par le joueur (clic d'attaque avec une arme blanche) : le serveur verifie s'il vise un zombie
     * colle a une fenetre (les planches cachent le zombie au ciblage normal) et applique le coup a sa place.
     */
    public record CoupC2S() {
        public void write(FriendlyByteBuf buf) {
        }

        public static CoupC2S read(FriendlyByteBuf buf) {
            return new CoupC2S();
        }

        public static void handle(CoupC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsZombiesServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }
}
