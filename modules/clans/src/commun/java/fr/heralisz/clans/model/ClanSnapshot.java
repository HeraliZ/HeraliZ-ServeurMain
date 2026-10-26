package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tout ce que l'ecran du clan affiche, calcule par le serveur pour UN joueur (le destinataire : « moi »). Immuable,
 * serialisable en FriendlyByteBuf. Le client ne connait rien d'autre de son clan.
 *
 * @param id              identifiant du clan
 * @param tag             tag en majuscules
 * @param nom             nom
 * @param description     description (0 a 3 lignes separees par \n)
 * @param banniere        banniere
 * @param chef            uuid du chef
 * @param creeLe          creation (ms epoch)
 * @param banque          solde de la banque
 * @param niveau          niveau (1..20)
 * @param xp              xp dans le niveau courant
 * @param xpSuivant       xp necessaire pour le niveau suivant (0 au niveau max)
 * @param membresMax      limite de membres du niveau
 * @param territoiresMax  limite de territoires du niveau
 * @param membres         membres avec en ligne et solde personnel
 * @param perms           permissions des grades modifiables (masques)
 * @param moi             uuid du destinataire
 * @param monGrade        grade du destinataire
 * @param monSolde        solde personnel du destinataire (economie active)
 * @param symbole         symbole de la monnaie (« $ »)
 * @param cdNomMs         ms avant de pouvoir changer le nom (0 = possible)
 * @param cdTagMs         idem tag
 * @param cdDescMs        idem description
 * @param cdBanniereMs    idem banniere
 * @param journalBanque   30 derniers mouvements (le plus recent en premier)
 * @param quetesJour      quetes du jour
 * @param quetesSemaine   quetes de la semaine
 * @param resetJourMs     ms avant le prochain tirage du jour
 * @param resetSemaineMs  ms avant le prochain tirage de la semaine
 * @param territoires     zones tenues
 * @param journal         20 derniers evenements (le plus recent en premier)
 * @param maintenant      horloge serveur a l'envoi (ms epoch) : le client en deduit « il y a 3 h » sans dependre de
 *                        son horloge
 * @param reglages        reglages individuels de permissions (membre -> masques ; absents = aucun) : tous les membres
 *                        si le destinataire peut GERER_PERMISSIONS, sinon seulement les siens (zero confiance). Ecrit en
 *                        dernier : un paquet d'une version precedente (sans ce champ) se lit avec une table vide.
 */
public record ClanSnapshot(UUID id, String tag, String nom, String description, Banniere banniere, UUID chef, long creeLe,
                           long banque, int niveau, long xp, long xpSuivant, int membresMax, int territoiresMax,
                           List<MembreVue> membres, EnumMap<Grade, Integer> perms, UUID moi, Grade monGrade, long monSolde,
                           String symbole, long cdNomMs, long cdTagMs, long cdDescMs, long cdBanniereMs,
                           List<Mouvement> journalBanque, List<QueteEtat> quetesJour, List<QueteEtat> quetesSemaine,
                           long resetJourMs, long resetSemaineMs, List<TerritoireVue> territoires, List<Evenement> journal,
                           long maintenant, Map<UUID, ReglagesPerms> reglages) {
    /** Reglages individuels plafonnes a la lecture (un par membre). */
    private static final int REGLAGES_MAX = 64;

    public ClanSnapshot {
        reglages = reglages == null ? Map.of() : Map.copyOf(reglages);
    }

    /** Sans reglages individuels (banc d'essai, anciens appels). */
    public ClanSnapshot(UUID id, String tag, String nom, String description, Banniere banniere, UUID chef, long creeLe,
                        long banque, int niveau, long xp, long xpSuivant, int membresMax, int territoiresMax,
                        List<MembreVue> membres, EnumMap<Grade, Integer> perms, UUID moi, Grade monGrade, long monSolde,
                        String symbole, long cdNomMs, long cdTagMs, long cdDescMs, long cdBanniereMs,
                        List<Mouvement> journalBanque, List<QueteEtat> quetesJour, List<QueteEtat> quetesSemaine,
                        long resetJourMs, long resetSemaineMs, List<TerritoireVue> territoires, List<Evenement> journal,
                        long maintenant) {
        this(id, tag, nom, description, banniere, chef, creeLe, banque, niveau, xp, xpSuivant, membresMax, territoiresMax, membres,
                perms, moi, monGrade, monSolde, symbole, cdNomMs, cdTagMs, cdDescMs, cdBanniereMs, journalBanque, quetesJour,
                quetesSemaine, resetJourMs, resetSemaineMs, territoires, journal, maintenant, Map.of());
    }

    /** Copie avec d'autres permissions de grades et d'autres reglages individuels (banc d'essai, affichage). */
    public ClanSnapshot avecPerms(EnumMap<Grade, Integer> nouvellesPerms, Map<UUID, ReglagesPerms> nouveauxReglages) {
        return new ClanSnapshot(id, tag, nom, description, banniere, chef, creeLe, banque, niveau, xp, xpSuivant, membresMax,
                territoiresMax, membres, nouvellesPerms, moi, monGrade, monSolde, symbole, cdNomMs, cdTagMs, cdDescMs, cdBanniereMs,
                journalBanque, quetesJour, quetesSemaine, resetJourMs, resetSemaineMs, territoires, journal, maintenant, nouveauxReglages);
    }

    /** Un membre vu par le client. */
    public record MembreVue(UUID uuid, String nom, Grade grade, long rejointLe, long vuLe, Stats stats, boolean enLigne, long solde) {
        public void write(FriendlyByteBuf b) {
            b.writeUUID(uuid);
            Texte.utf(b, nom, 48);
            b.writeVarInt(grade.ordinal());
            b.writeVarLong(rejointLe);
            b.writeVarLong(vuLe);
            stats.write(b);
            b.writeBoolean(enLigne);
            b.writeLong(solde);
        }

        public static MembreVue read(FriendlyByteBuf b) {
            return new MembreVue(b.readUUID(), b.readUtf(48), Grade.values()[Math.min(3, b.readVarInt())], b.readVarLong(), b.readVarLong(),
                    Stats.read(b), b.readBoolean(), b.readLong());
        }

        public static MembreVue de(Membre m, boolean enLigne, long solde) {
            return new MembreVue(m.uuid, m.dernierNom, m.grade, m.rejointLe, m.vuLe, m.stats.copie(), enLigne, solde);
        }
    }

    /** Une zone tenue par le clan. */
    public record TerritoireVue(String id, String nom, long depuis) {
        public void write(FriendlyByteBuf b) {
            Texte.utf(b, id, 32);
            Texte.utf(b, nom, 48);
            b.writeVarLong(depuis);
        }

        public static TerritoireVue read(FriendlyByteBuf b) {
            return new TerritoireVue(b.readUtf(32), b.readUtf(48), b.readVarLong());
        }
    }

    // ------------------------------------------------------------------ aides d'affichage
    public int enLigne() {
        int n = 0;
        for (MembreVue m : membres) if (m.enLigne()) n++;
        return n;
    }

    public Stats totaux() {
        Stats t = new Stats();
        for (MembreVue m : membres) t.ajouter(m.stats());
        return t;
    }

    @Nullable
    public MembreVue membre(UUID uuid) {
        for (MembreVue m : membres) if (m.uuid().equals(uuid)) return m;
        return null;
    }

    /** Permissions effectives d'un grade (le chef : toutes). */
    public java.util.EnumSet<Perm> permsDe(Grade g) {
        if (g == Grade.CHEF) return java.util.EnumSet.allOf(Perm.class);
        Integer m = perms.get(g);
        return m == null ? g.permsParDefaut() : Perm.depuisMasque(m);
    }

    /** Reglages individuels connus d'un membre (AUCUN si absents). */
    public ReglagesPerms reglagesDe(UUID uuid) {
        ReglagesPerms r = uuid == null ? null : reglages.get(uuid);
        return r == null ? ReglagesPerms.AUCUN : r;
    }

    /** Permission effective d'un membre vue par le client (chef = tout ; reglage individuel ; sinon son grade). */
    public boolean peut(UUID uuid, Perm p) {
        if (uuid != null && uuid.equals(moi)) return jePeux(p);
        MembreVue m = membre(uuid);
        if (m == null) return false;
        if (m.grade() == Grade.CHEF) return true;
        return reglagesDe(uuid).effectif(p, permsDe(m.grade()).contains(p));
    }

    /** Le destinataire a-t-il cette permission ? (son grade, corrige par ses reglages individuels) */
    public boolean jePeux(Perm p) {
        if (monGrade == Grade.CHEF) return true;
        return reglagesDe(moi).effectif(p, permsDe(monGrade).contains(p));
    }

    public boolean jeSuisChef() {
        return monGrade == Grade.CHEF;
    }

    public int accent() {
        return banniere.accent();
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf b) {
        b.writeUUID(id);
        Texte.utf(b, tag, 8);
        Texte.utf(b, nom, 32);
        Texte.utf(b, description, 160);
        banniere.write(b);
        b.writeUUID(chef);
        b.writeVarLong(creeLe);
        b.writeLong(banque);
        b.writeVarInt(niveau);
        b.writeVarLong(xp);
        b.writeVarLong(xpSuivant);
        b.writeVarInt(membresMax);
        b.writeVarInt(territoiresMax);
        b.writeVarInt(membres.size());
        for (MembreVue m : membres) m.write(b);
        for (Grade g : Grade.modifiables()) b.writeVarInt(perms.getOrDefault(g, Perm.masque(g.permsParDefaut())));
        b.writeUUID(moi);
        b.writeVarInt(monGrade.ordinal());
        b.writeLong(monSolde);
        Texte.utf(b, symbole, 8);
        b.writeVarLong(cdNomMs);
        b.writeVarLong(cdTagMs);
        b.writeVarLong(cdDescMs);
        b.writeVarLong(cdBanniereMs);
        b.writeVarInt(journalBanque.size());
        for (Mouvement m : journalBanque) m.write(b);
        b.writeVarInt(quetesJour.size());
        for (QueteEtat q : quetesJour) q.write(b);
        b.writeVarInt(quetesSemaine.size());
        for (QueteEtat q : quetesSemaine) q.write(b);
        b.writeVarLong(resetJourMs);
        b.writeVarLong(resetSemaineMs);
        b.writeVarInt(territoires.size());
        for (TerritoireVue t : territoires) t.write(b);
        b.writeVarInt(journal.size());
        for (Evenement e : journal) e.write(b);
        b.writeVarLong(maintenant);
        // ajout (reglages individuels) : toujours en dernier, lu seulement s'il reste des octets
        int n = Math.min(REGLAGES_MAX, reglages.size());
        b.writeVarInt(n);
        for (Map.Entry<UUID, ReglagesPerms> e : reglages.entrySet()) {
            if (n-- <= 0) break;
            b.writeUUID(e.getKey());
            e.getValue().write(b);
        }
    }

    public static ClanSnapshot read(FriendlyByteBuf b) {
        UUID id = b.readUUID();
        String tag = b.readUtf(8), nom = b.readUtf(32), desc = b.readUtf(160);
        Banniere ban = Banniere.read(b);
        UUID chef = b.readUUID();
        long creeLe = b.readVarLong(), banque = b.readLong();
        int niveau = b.readVarInt();
        long xp = b.readVarLong(), xpSuivant = b.readVarLong();
        int membresMax = b.readVarInt(), territoiresMax = b.readVarInt();
        int n = Math.min(64, b.readVarInt());
        List<MembreVue> membres = new ArrayList<>(n);
        for (int i = 0; i < n; i++) membres.add(MembreVue.read(b));
        EnumMap<Grade, Integer> perms = new EnumMap<>(Grade.class);
        for (Grade g : Grade.modifiables()) perms.put(g, b.readVarInt());
        UUID moi = b.readUUID();
        Grade monGrade = Grade.values()[Math.min(3, b.readVarInt())];
        long monSolde = b.readLong();
        String symbole = b.readUtf(8);
        long cdNom = b.readVarLong(), cdTag = b.readVarLong(), cdDesc = b.readVarLong(), cdBan = b.readVarLong();
        n = Math.min(Mouvement.MAX, b.readVarInt());
        List<Mouvement> jb = new ArrayList<>(n);
        for (int i = 0; i < n; i++) jb.add(Mouvement.read(b));
        n = Math.min(8, b.readVarInt());
        List<QueteEtat> qj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) qj.add(QueteEtat.read(b));
        n = Math.min(8, b.readVarInt());
        List<QueteEtat> qs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) qs.add(QueteEtat.read(b));
        long rj = b.readVarLong(), rs = b.readVarLong();
        n = Math.min(16, b.readVarInt());
        List<TerritoireVue> terr = new ArrayList<>(n);
        for (int i = 0; i < n; i++) terr.add(TerritoireVue.read(b));
        n = Math.min(Evenement.MAX, b.readVarInt());
        List<Evenement> journal = new ArrayList<>(n);
        for (int i = 0; i < n; i++) journal.add(Evenement.read(b));
        long maintenant = b.readVarLong();
        Map<UUID, ReglagesPerms> reglages = new HashMap<>();
        if (b.isReadable()) {
            n = Math.min(REGLAGES_MAX, b.readVarInt());
            for (int i = 0; i < n; i++) {
                UUID u = b.readUUID();
                ReglagesPerms r = ReglagesPerms.read(b);
                if (!r.vide()) reglages.put(u, r);
            }
        }
        return new ClanSnapshot(id, tag, nom, desc, ban, chef, creeLe, banque, niveau, xp, xpSuivant, membresMax, territoiresMax,
                membres, perms, moi, monGrade, monSolde, symbole, cdNom, cdTag, cdDesc, cdBan, jb, qj, qs, rj, rs, terr, journal, maintenant,
                reglages);
    }
}
