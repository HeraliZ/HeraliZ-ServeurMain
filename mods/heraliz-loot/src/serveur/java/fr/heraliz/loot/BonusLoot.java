package fr.heraliz.loot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bonus de loot d'un joueur (%), appliqué au premier tirage d'un conteneur qu'il ouvre (T1 à T7) : chaque objet a ce
 * pourcentage de chances d'être tiré une seconde fois, le plus rare des deux est gardé, et les quantités montent d'autant
 * ({@link TablesCoffres#tirer(TablesCoffres.Table, net.minecraft.util.RandomSource, int)}). Les sources s'additionnent,
 * plafond : réglage bonus.max (25 %).
 * <p>
 * Sources : données persistantes du joueur, {@code PlayerPersisted} (gardé à la mort) puis compound {@value #CLE}, une
 * entrée par source {@code {Pct: <pourcent>, Fin: <heure système en ms>}} ou {@code {Pct: <pourcent>, Restant: <ticks de
 * jeu>}}. Une source s'éteint seule à son heure de fin ; un temps restant est décompté par le mod qui l'a posé. C'est le
 * contrat entre nos mods, sans dépendance (CLAUDE.md §5) :
 * <ul><li>« clan » : membre présent dans une zone que son clan tient (HeraliZ Clans, renouvelée chaque minute) ;</li>
 * <li>« objet » : objet « Bonus de loot » de la boutique (HeraliZ Utils, temps de jeu décompté seulement en ligne).</li></ul>
 */
public final class BonusLoot {
    public static final String PERSISTE = "PlayerPersisted";
    public static final String CLE = "HeralizBonusLoot";

    private BonusLoot() {
    }

    /** Bonus actuel du joueur (somme des sources encore valables, plafonnée) ; nettoie les sources éteintes. */
    static int pourcent(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(PERSISTE, Tag.TAG_COMPOUND)) return 0;
        CompoundTag persiste = d.getCompound(PERSISTE);
        if (!persiste.contains(CLE, Tag.TAG_COMPOUND)) return 0;
        CompoundTag sources = persiste.getCompound(CLE);
        long now = System.currentTimeMillis();
        int total = 0;
        List<String> eteintes = new ArrayList<>();
        for (String s : sources.getAllKeys()) {
            CompoundTag t = sources.getCompound(s);
            if (t.contains("Fin") && t.getLong("Fin") < now || t.contains("Restant") && t.getLong("Restant") <= 0) {
                eteintes.add(s);
                continue;
            }
            total += Math.max(0, t.getInt("Pct"));
        }
        eteintes.forEach(sources::remove);
        return Math.min(total, ConfigLoot.bonusMax());
    }

    /** « Bonus de loot +10 % » au-dessus de la barre d'objets, quand il a servi. */
    static void annoncer(ServerPlayer p, int bonus) {
        if (bonus > 0) {
            p.displayClientMessage(Component.literal("Bonus de loot +" + bonus + " %").withStyle(ChatFormatting.GOLD), true);
        }
    }
}
