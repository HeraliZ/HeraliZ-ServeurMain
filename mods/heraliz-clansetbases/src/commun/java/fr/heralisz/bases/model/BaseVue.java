package fr.heralisz.bases.model;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Etat de la base de MON clan pour l'onglet / l'ecran BASE (BASES_SPEC §6), calcule par le serveur pour un joueur.
 * Sans clan : {@code clan == null} et le reste vide. Sans base : {@code existe == false} (presentation + cout).
 *
 * @param clan          mon clan (null = pas de clan)
 * @param perms         mes permissions de base (bit = {@link Droit#ordinal()})
 * @param existe        le clan a une base
 * @param etat          etat de la base (POSE, PRETE, EFFACEMENT) ; PRETE si elle n'existe pas (sans objet)
 * @param progression   avancement de la pose ou de l'effacement (0..1)
 * @param coutCreation  cout de creation (config)
 * @param banque        solde de la banque du clan
 * @param salles        salles du modele dans l'ordre (ouverte, cout effectif)
 * @param fenetre       fenetre de pillage
 * @param attaquants    tags des clans qui pillent la base en ce moment
 * @param pillards      pillards dans la base
 * @param pillages      derniers pillages (5 au plus, le plus recent en tete)
 * @param dansLaBase    je suis dans cette base
 * @param planEmpreinte empreinte du plan du bunker (le client redemande le plan si elle change ; 0 = pas de modele)
 */
public record BaseVue(UUID clan, String tag, String nom, Banniere banniere, int perms, boolean existe, Base.Etat etat,
                      float progression, long coutCreation, long banque, List<SalleVue> salles, FenetreVue fenetre,
                      List<String> attaquants, int pillards, List<Pillage> pillages, boolean dansLaBase, int planEmpreinte) {

    /** Droits de base transmis au client (sous-ensemble utile des Perm BASE_*). */
    public enum Droit {ENTRER, CONSTRUIRE, CASSER, COFFRES, POSER_COFFRES, PORTES, GERER}

    /** Salle telle que vue par le client : ouverte ou non, cout effectif de son deblocage. */
    public record SalleVue(String id, boolean ouverte, boolean depart, long cout) {
    }

    public boolean peut(Droit d) {
        return (perms & (1 << d.ordinal())) != 0;
    }

    /** Vue « pas de clan ». */
    public static BaseVue sansClan(long cout, FenetreVue f) {
        return new BaseVue(null, "", "", Banniere.DEFAUT, 0, false, Base.Etat.PRETE, 0f, cout, 0, List.of(), f, List.of(), 0,
                List.of(), false, 0);
    }

    public void write(FriendlyByteBuf b) {
        b.writeBoolean(clan != null);
        if (clan == null) {
            b.writeLong(coutCreation);
            fenetre.write(b);
            return;
        }
        b.writeUUID(clan);
        b.writeUtf(tag, 8);
        b.writeUtf(nom, 32);
        banniere.write(b);
        b.writeVarInt(perms);
        b.writeBoolean(existe);
        b.writeByte(etat.ordinal());
        b.writeFloat(progression);
        b.writeVarLong(coutCreation);
        b.writeVarLong(banque);
        b.writeVarInt(salles.size());
        for (SalleVue s : salles) {
            b.writeUtf(s.id, 32);
            b.writeBoolean(s.ouverte);
            b.writeBoolean(s.depart);
            b.writeVarLong(s.cout);
        }
        fenetre.write(b);
        b.writeVarInt(attaquants.size());
        for (String t : attaquants) b.writeUtf(t, 8);
        b.writeVarInt(pillards);
        b.writeVarInt(pillages.size());
        for (Pillage p : pillages) p.write(b);
        b.writeBoolean(dansLaBase);
        b.writeInt(planEmpreinte);
    }

    public static BaseVue read(FriendlyByteBuf b) {
        if (!b.readBoolean()) {
            long cout = b.readLong();
            return sansClan(cout, FenetreVue.read(b));
        }
        UUID clan = b.readUUID();
        String tag = b.readUtf(8), nom = b.readUtf(32);
        Banniere ban = Banniere.read(b);
        int perms = b.readVarInt();
        boolean existe = b.readBoolean();
        int e = b.readByte();
        Base.Etat etat = e >= 0 && e < Base.Etat.values().length ? Base.Etat.values()[e] : Base.Etat.PRETE;
        float prog = b.readFloat();
        long cout = b.readVarLong(), banque = b.readVarLong();
        int n = Math.min(64, b.readVarInt());
        List<SalleVue> salles = new ArrayList<>(n);
        for (int i = 0; i < n; i++) salles.add(new SalleVue(b.readUtf(32), b.readBoolean(), b.readBoolean(), b.readVarLong()));
        FenetreVue f = FenetreVue.read(b);
        int m = Math.min(32, b.readVarInt());
        List<String> att = new ArrayList<>(m);
        for (int i = 0; i < m; i++) att.add(b.readUtf(8));
        int pillards = b.readVarInt();
        int k = Math.min(Base.PILLAGES_MAX, b.readVarInt());
        List<Pillage> pil = new ArrayList<>(k);
        for (int i = 0; i < k; i++) pil.add(Pillage.read(b));
        return new BaseVue(clan, tag, nom, ban, perms, existe, etat, prog, cout, banque, salles, f, att, pillards, pil,
                b.readBoolean(), b.readInt());
    }
}
