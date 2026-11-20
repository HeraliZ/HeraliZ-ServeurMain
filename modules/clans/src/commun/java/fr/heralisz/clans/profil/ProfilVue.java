package fr.heralisz.clans.profil;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Grade;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Ce que montre l'écran du profil ({@code /profil}, {@code /stats} ; 19/11, demande du propriétaire, maquettes validées) :
 * relevé par le serveur ({@code profil.Profils}), envoyé au joueur dans {@code Packets.ProfilS2C}, dessiné par
 * {@code client.gui.EcranProfil}. Le profil d'un AUTRE joueur ({@link #moi} faux) ne porte jamais son argent, ce qu'il a
 * sur lui ni l'endroit où il est : solde à -1, {@link #surLui} et rang en hérals absents (rien n'est envoyé).
 * <p>Les noms (grade de la boutique, titre de Varga) arrivent dans les trois langues : le jeu du joueur prend la sienne
 * ({@link Noms#choisir}). Les autres textes sont des clés traduites chez lui.
 *
 * @param depuis     première connexion connue (ms epoch), 0 : inconnue
 * @param tempsJeuMs temps de jeu (sessions finies et session en cours)
 * @param solde      hérals du compte, -1 : pas montré (profil d'un autre joueur)
 * @param quetes     quêtes de Varga finies (suite et contrats), -1 : inconnu
 * @param clan       null : sans clan
 * @param surLui     null : pas montré (profil d'un autre joueur)
 */
public record ProfilVue(UUID joueur, String pseudo, boolean moi, Noms grade, int gradeCouleur, long depuis, long tempsJeuMs,
                        long solde, Compteurs compteurs, int quetes, Noms titre, @Nullable ClanVue clan, Rangs rangs,
                        @Nullable SurLui surLui) {

    /** Longueur lue au plus d'un nom (le serveur coupe avant l'envoi : {@link #nom}). */
    private static final int NOM_MAX = 64;

    /** Un nom dans les trois langues ; vide partout : rien à montrer. */
    public record Noms(String fr, String en, String ru) {
        public static final Noms VIDE = new Noms("", "", "");

        public boolean vide() {
            return fr.isEmpty() && en.isEmpty() && ru.isEmpty();
        }

        /** Le nom dans la langue du jeu (« fr_fr », « ru_ru »…), sinon l'anglais, sinon le français. */
        public String choisir(@Nullable String langue) {
            String l = langue == null ? "" : langue;
            if (l.startsWith("fr") && !fr.isEmpty()) return fr;
            if (l.startsWith("ru") && !ru.isEmpty()) return ru;
            if (!en.isEmpty()) return en;
            return !fr.isEmpty() ? fr : ru;
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(fr, NOM_MAX);
            b.writeUtf(en, NOM_MAX);
            b.writeUtf(ru, NOM_MAX);
        }

        static Noms read(FriendlyByteBuf b) {
            return new Noms(b.readUtf(NOM_MAX), b.readUtf(NOM_MAX), b.readUtf(NOM_MAX));
        }
    }

    /**
     * Ce qui est compté pour le joueur (survie seulement : ni ClashWar, ni tutoriel des bases, ni bots de charge).
     *
     * @param mortsJoueur morts dont l'auteur est un autre joueur
     * @param serieMax    meilleure série de kills sans mourir
     * @param vieMaxMs    plus longue présence d'affilée sur la carte, sans mourir (vie en cours comprise)
     */
    public record Compteurs(int kills, int morts, int mortsJoueur, int zombies, int extractions, int conteneurs, int serieMax,
                            long vieMaxMs) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(Math.max(0, kills));
            b.writeVarInt(Math.max(0, morts));
            b.writeVarInt(Math.max(0, mortsJoueur));
            b.writeVarInt(Math.max(0, zombies));
            b.writeVarInt(Math.max(0, extractions));
            b.writeVarInt(Math.max(0, conteneurs));
            b.writeVarInt(Math.max(0, serieMax));
            b.writeVarLong(Math.max(0L, vieMaxMs));
        }

        static Compteurs read(FriendlyByteBuf b) {
            return new Compteurs(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarLong());
        }

        /** Kills / morts (morts comptées au moins 1), comme le K/D des clans. */
        public double kd() {
            return kills / (double) Math.max(1, morts);
        }
    }

    /**
     * Le clan du joueur et sa saison.
     *
     * @param depuis       arrivée du joueur dans le clan (ms epoch)
     * @param saisonRang   place au classement général de la saison, 0 : pas classé (sans cœur posé, ou hors saison)
     * @param saisonPoints points de la saison (les trois catégories, arrondis)
     */
    public record ClanVue(String nom, String tag, Grade grade, long depuis, int saison, boolean saisonActive, int saisonRang,
                          long saisonPoints, Banniere banniere) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(nom, NOM_MAX);
            b.writeUtf(tag, 16);
            b.writeByte(grade.ordinal());
            b.writeVarLong(Math.max(0L, depuis));
            b.writeVarInt(Math.max(0, saison));
            b.writeBoolean(saisonActive);
            b.writeVarInt(Math.max(0, saisonRang));
            b.writeVarLong(Math.max(0L, saisonPoints));
            banniere.write(b);   // en dernier : sa lecture s'arrête à 12 calques
        }

        static ClanVue read(FriendlyByteBuf b) {
            String nom = b.readUtf(NOM_MAX), tag = b.readUtf(16);
            int g = b.readUnsignedByte();
            Grade grade = g < Grade.values().length ? Grade.values()[g] : Grade.RECRUE;
            long depuis = b.readVarLong();
            int saison = b.readVarInt();
            boolean active = b.readBoolean();
            int rang = b.readVarInt();
            long points = b.readVarLong();
            return new ClanVue(nom, tag, grade, depuis, saison, active, rang, points, Banniere.read(b));
        }
    }

    /**
     * Places du joueur parmi les joueurs connus (0 : pas classé, rien de compté).
     *
     * @param herals  place en hérals, seulement sur son propre profil (0 sinon)
     * @param joueurs joueurs connus (kills, zombies)
     * @param comptes comptes de hérals (place en hérals)
     */
    public record Rangs(int kills, int zombies, int herals, int joueurs, int comptes) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(Math.max(0, kills));
            b.writeVarInt(Math.max(0, zombies));
            b.writeVarInt(Math.max(0, herals));
            b.writeVarInt(Math.max(0, joueurs));
            b.writeVarInt(Math.max(0, comptes));
        }

        static Rangs read(FriendlyByteBuf b) {
            return new Rangs(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
    }

    /**
     * Ce que le joueur a sur lui (son propre profil seulement ; -1 : inconnu, le mod qui le tient est absent).
     *
     * @param bourses        hérals des bourses de son inventaire et de son sac à dos
     * @param nombreBourses  nombre de ces bourses
     * @param capsulesCompte capsules du casino sur son compte
     * @param capsulesSurLui capsules du casino dans son inventaire
     */
    public record SurLui(long bourses, int nombreBourses, long capsulesCompte, long capsulesSurLui) {
        void write(FriendlyByteBuf b) {
            b.writeLong(bourses);
            b.writeVarInt(Math.max(0, nombreBourses));
            b.writeLong(capsulesCompte);
            b.writeLong(capsulesSurLui);
        }

        static SurLui read(FriendlyByteBuf b) {
            return new SurLui(b.readLong(), b.readVarInt(), b.readLong(), b.readLong());
        }
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(joueur);
        b.writeUtf(pseudo, NOM_MAX);
        b.writeBoolean(moi);
        grade.write(b);
        b.writeInt(gradeCouleur);
        b.writeVarLong(Math.max(0L, depuis));
        b.writeVarLong(Math.max(0L, tempsJeuMs));
        b.writeLong(solde);
        compteurs.write(b);
        b.writeVarInt(quetes + 1);   // -1 (inconnu) part comme 0
        titre.write(b);
        rangs.write(b);
        b.writeBoolean(surLui != null);
        if (surLui != null) surLui.write(b);
        // le clan en dernier : sa bannière s'arrête à 12 calques
        b.writeBoolean(clan != null);
        if (clan != null) clan.write(b);
    }

    public static ProfilVue read(FriendlyByteBuf b) {
        UUID joueur = b.readUUID();
        String pseudo = b.readUtf(NOM_MAX);
        boolean moi = b.readBoolean();
        Noms grade = Noms.read(b);
        int couleur = b.readInt() & 0xFFFFFF;
        long depuis = b.readVarLong(), temps = b.readVarLong(), solde = b.readLong();
        Compteurs c = Compteurs.read(b);
        int quetes = b.readVarInt() - 1;
        Noms titre = Noms.read(b);
        Rangs rangs = Rangs.read(b);
        SurLui surLui = b.readBoolean() ? SurLui.read(b) : null;
        ClanVue clan = b.readBoolean() ? ClanVue.read(b) : null;
        return new ProfilVue(joueur, pseudo, moi, grade, couleur, depuis, temps, solde, c, quetes, titre, clan, rangs, surLui);
    }

    /** Un nom coupé à la longueur permise par le réseau (pseudo, clan, grade, titre), sans espaces autour. */
    public static String nom(@Nullable String s) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() <= NOM_MAX ? t : t.substring(0, NOM_MAX);
    }
}
