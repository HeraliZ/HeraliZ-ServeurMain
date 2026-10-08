package fr.heralisz.economie.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Compte d'un joueur : solde entier (unite = 1 héral), dernier nom connu, journal des 50 dernieres transactions
 * (la plus recente en tete). Les mutations passent par {@link EconomieApi} / le store ; les autres mods ne lisent
 * que les accesseurs.
 */
public final class Compte {
    /** Nombre de transactions conservees par compte. */
    public static final int JOURNAL_MAX = 50;

    private final UUID uuid;
    private long solde;
    private String dernierNom;
    private final Deque<Transaction> journal = new ArrayDeque<>(JOURNAL_MAX + 1);

    public Compte(UUID uuid, long solde, @Nullable String dernierNom) {
        this.uuid = uuid;
        this.solde = Math.max(0, solde);
        this.dernierNom = dernierNom == null ? "" : dernierNom;
    }

    public UUID uuid() {
        return uuid;
    }

    public long solde() {
        return solde;
    }

    /** Dernier nom de joueur vu pour ce compte (vide si inconnu). */
    public String dernierNom() {
        return dernierNom;
    }

    /** Copie du journal, la transaction la plus recente en premier. */
    public List<Transaction> journal() {
        return Collections.unmodifiableList(new ArrayList<>(journal));
    }

    /** Les n dernieres transactions (les plus recentes en premier). */
    public List<Transaction> journal(int n) {
        List<Transaction> l = new ArrayList<>(Math.min(n, journal.size()));
        for (Transaction t : journal) {
            if (l.size() >= n) break;
            l.add(t);
        }
        return l;
    }

    // ------------------------------------------------------------------ mutations (store uniquement)
    /** Applique un delta et journalise. Le solde final est suppose deja valide (>= 0, <= plafond). */
    public void appliquer(long delta, String motif, @Nullable UUID autre) {
        solde += delta;
        journal.addFirst(new Transaction(System.currentTimeMillis(), delta, solde, motif, autre));
        while (journal.size() > JOURNAL_MAX) journal.removeLast();
    }

    public void setDernierNom(String nom) {
        if (nom != null && !nom.isEmpty()) dernierNom = nom;
    }

    // ------------------------------------------------------------------ json
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("solde", solde);
        o.addProperty("dernierNom", dernierNom);
        JsonArray a = new JsonArray();
        for (Transaction t : journal) a.add(t.toJson());
        o.add("journal", a);
        return o;
    }

    @Nullable
    public static Compte fromJson(UUID uuid, JsonObject o) {
        try {
            Compte c = new Compte(uuid, o.has("solde") ? o.get("solde").getAsLong() : 0,
                    o.has("dernierNom") ? o.get("dernierNom").getAsString() : "");
            if (o.has("journal") && o.get("journal").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("journal")) {
                    if (!e.isJsonObject()) continue;
                    Transaction t = Transaction.fromJson(e.getAsJsonObject());
                    if (t != null && c.journal.size() < JOURNAL_MAX) c.journal.addLast(t);
                }
            }
            return c;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
