package fr.heralisz.matchs.moteur.api;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Equipe d'une {@link Partie} : nom affiche, couleur, joueurs inscrits et joueurs encore en vie. Les joueurs sont des
 * UUID (un joueur deconnecte reste dans {@link #joueurs()} ; c'est la partie qui decide de le retirer).
 * Fil serveur uniquement. Les listes rendues ne sont pas modifiables : passer par les methodes.
 */
public class Equipe {
    private final String id;
    private String nom;
    private int couleur;
    private final List<UUID> joueurs = new ArrayList<>();
    private final Set<UUID> vivants = new LinkedHashSet<>();
    private int score;
    /** Clan represente (ClashWar), ou null. */
    @Nullable
    private UUID clan;

    /**
     * @param id      identifiant court et stable dans la partie ("a", "b", "nova_1"...)
     * @param nom     nom affiche ("[NOVA] Equipe 1", "Attaquants"...)
     * @param couleur couleur RGB 24 bits (pastilles du HUD, fil des eliminations)
     */
    public Equipe(String id, String nom, int couleur) {
        this.id = id;
        this.nom = nom;
        this.couleur = couleur & 0xFFFFFF;
    }

    public String id() {
        return id;
    }

    public String nom() {
        return nom;
    }

    public void nom(String nom) {
        this.nom = nom;
    }

    /** Couleur RGB 24 bits. */
    public int couleur() {
        return couleur;
    }

    public void couleur(int rgb) {
        this.couleur = rgb & 0xFFFFFF;
    }

    /** Joueurs inscrits (ordre d'ajout), non modifiable. */
    public List<UUID> joueurs() {
        return Collections.unmodifiableList(joueurs);
    }

    /** Joueurs en vie, non modifiable. */
    public Set<UUID> vivants() {
        return Collections.unmodifiableSet(vivants);
    }

    /** Ajoute un joueur (vivant). Sans effet s'il y est deja. */
    public void ajouter(UUID joueur) {
        if (!joueurs.contains(joueur)) joueurs.add(joueur);
        vivants.add(joueur);
    }

    /** Retire completement un joueur (forfait, deconnexion definitive). */
    public void retirer(UUID joueur) {
        joueurs.remove(joueur);
        vivants.remove(joueur);
    }

    /** Le joueur n'est plus en vie (spectateur ou en attente de reapparition). */
    public void eliminer(UUID joueur) {
        vivants.remove(joueur);
    }

    /** Le joueur revit (reapparition, nouvelle manche). Sans effet s'il n'est pas inscrit. */
    public void ranimer(UUID joueur) {
        if (joueurs.contains(joueur)) vivants.add(joueur);
    }

    /** Tous les inscrits revivent (nouvelle manche). */
    public void ranimerTous() {
        vivants.addAll(joueurs);
    }

    public boolean contient(UUID joueur) {
        return joueurs.contains(joueur);
    }

    public boolean estVivant(UUID joueur) {
        return vivants.contains(joueur);
    }

    public int nbVivants() {
        return vivants.size();
    }

    public int taille() {
        return joueurs.size();
    }

    /** Vrai s'il reste au moins un joueur en vie. */
    public boolean enVie() {
        return !vivants.isEmpty();
    }

    /** Score libre du mode (manches gagnees, eliminations, points...). */
    public int score() {
        return score;
    }

    public void score(int score) {
        this.score = score;
    }

    public void ajouterScore(int delta) {
        this.score += delta;
    }

    @Nullable
    public UUID clan() {
        return clan;
    }

    public void clan(@Nullable UUID clan) {
        this.clan = clan;
    }

    /** Ecriture reseau minimale (HUD, tableau des scores) : id, nom, couleur, vivants, total, score. */
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 32);
        buf.writeUtf(nom, 64);
        buf.writeInt(couleur);
        buf.writeVarInt(vivants.size());
        buf.writeVarInt(joueurs.size());
        buf.writeVarInt(score);
    }

    @Override
    public String toString() {
        return "Equipe[" + id + " '" + nom + "' " + vivants.size() + "/" + joueurs.size() + " score " + score + "]";
    }
}
