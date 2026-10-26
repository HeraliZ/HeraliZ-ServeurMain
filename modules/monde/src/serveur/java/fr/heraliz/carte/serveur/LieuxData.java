package fr.heraliz.carte.serveur;

import fr.heraliz.carte.CarteNet;
import fr.heraliz.carte.Lieu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lieux nommés de la carte et nom de la carte, gardés AVEC LE MONDE (data/heraliz_carte_lieux.dat du monde principal) :
 * posés en jeu par le staff ({@link CarteCommandes}), ils suivent la carte quand elle est copiée. Envoyés à chaque joueur
 * à sa connexion et à tous à chaque changement.
 */
public final class LieuxData extends SavedData {
    public static final String FICHIER = "heraliz_carte_lieux";
    /** Lieux au plus (la liste voyage en un seul paquet). */
    public static final int MAX = CarteNet.LieuxS2C.MAX;

    private String nom = "HeraliZ";
    private final List<Lieu> lieux = new ArrayList<>();

    public static LieuxData de(MinecraftServer serveur) {
        return serveur.overworld().getDataStorage().computeIfAbsent(LieuxData::charger, LieuxData::new, FICHIER);
    }

    public String nom() {
        return nom;
    }

    public List<Lieu> lieux() {
        return Collections.unmodifiableList(lieux);
    }

    public void nommer(String n) {
        nom = Lieu.nettoyer(n);
        setDirty();
    }

    @Nullable
    public Lieu trouver(String n) {
        String cle = Lieu.nettoyer(n).toLowerCase(Locale.ROOT);
        for (Lieu l : lieux) if (l.nom().toLowerCase(Locale.ROOT).equals(cle)) return l;
        return null;
    }

    public boolean ajouter(Lieu l) {
        if (lieux.size() >= MAX || trouver(l.nom()) != null) return false;
        lieux.add(l);
        setDirty();
        return true;
    }

    public boolean retirer(Lieu l) {
        boolean ok = lieux.remove(l);
        if (ok) setDirty();
        return ok;
    }

    public CarteNet.LieuxS2C paquet() {
        return new CarteNet.LieuxS2C(nom, List.copyOf(lieux));
    }

    /** Envoie la liste à un joueur (connexion). */
    public void envoyer(ServerPlayer p) {
        CarteNet.versJoueur(p, paquet());
    }

    /** Envoie la liste à tous les joueurs connectés (après un changement). */
    public void diffuser(MinecraftServer serveur) {
        CarteNet.LieuxS2C m = paquet();
        for (ServerPlayer p : serveur.getPlayerList().getPlayers()) CarteNet.versJoueur(p, m);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("nom", nom);
        ListTag l = new ListTag();
        for (Lieu li : lieux) {
            CompoundTag t = new CompoundTag();
            t.putString("nom", li.nom());
            t.putString("type", li.type().id);
            t.putString("dim", li.dimension().toString());
            t.putInt("x", li.x());
            t.putInt("z", li.z());
            l.add(t);
        }
        tag.put("lieux", l);
        return tag;
    }

    public static LieuxData charger(CompoundTag tag) {
        LieuxData d = new LieuxData();
        if (tag.contains("nom", Tag.TAG_STRING)) d.nom = Lieu.nettoyer(tag.getString("nom"));
        ListTag l = tag.getList("lieux", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && d.lieux.size() < MAX; i++) {
            CompoundTag t = l.getCompound(i);
            Lieu.Type type = Lieu.Type.parId(t.getString("type"));
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            String n = Lieu.nettoyer(t.getString("nom"));
            if (type == null || dim == null || n.isEmpty()) continue;
            d.lieux.add(new Lieu(n, type, dim, t.getInt("x"), t.getInt("z")));
        }
        return d;
    }
}
