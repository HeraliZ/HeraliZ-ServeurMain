package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.objet.Bourse;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * API de HeraliZ Quêtes pour nos autres mods (19/11 : profil des joueurs de HeraliZ Clans, {@code /money} de HeraliZ
 * Grades), appelée PAR RÉFLEXION : ne jamais renommer cette classe ni changer ses signatures. Fil serveur seulement.
 * Lecture seule : rien n'est changé chez le joueur.
 */
public final class QuetesApi {
    private QuetesApi() {
    }

    /**
     * Quêtes de Varga d'un joueur, connecté ou non : {quêtes finies (suite et contrats, comme le journal), dernier titre
     * gagné en français, en anglais, en russe} (titres vides : aucun titre). La progression d'un joueur hors ligne est relue
     * dans son fichier sans être gardée en mémoire. Null si les quêtes ne tournent pas.
     */
    @Nullable
    public static String[] profil(UUID joueur) {
        if (joueur == null || Moteur.serveur == null) return null;
        Donnees.DJoueur j = Donnees.lecture(joueur);
        int finies = j.finies.size() + j.contratsFinis;
        String fr = "", en = "", ru = "";
        if (!j.titres.isEmpty()) {
            Texte t = Texte.json(j.titres.get(j.titres.size() - 1));
            if (t.cleTraduction() == null) {
                Map<String, String> e = t.ecrit();
                fr = e.getOrDefault("fr", "");
                en = e.getOrDefault("en", "");
                ru = e.getOrDefault("ru", "");
            }
        }
        return new String[]{Integer.toString(finies), fr, en, ru};
    }

    /**
     * Bourses de hérals que le joueur a sur lui : inventaire (armure et main gauche comprises) et sac à dos porté (Backpacked,
     * lu par réflexion) : {hérals affichés sur ces bourses, nombre de bourses}. Le montant affiché, pas le plafond du registre
     * ({@link Bourses}) : c'est ce que le joueur voit sur chaque bourse.
     */
    public static long[] bourses(ServerPlayer joueur) {
        long[] r = new long[2];
        if (joueur == null) return r;
        Inventory inv = joueur.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) compter(inv.getItem(i), r);
        ItemStack sac = sacPorte(joueur);
        if (!sac.isEmpty() && sac.getTag() != null) {
            ListTag l = sac.getTag().getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) compter(ItemStack.of(l.getCompound(i)), r);
        }
        return r;
    }

    private static void compter(ItemStack s, long[] r) {
        if (s.isEmpty() || !(s.getItem() instanceof Bourse)) return;
        r[0] += Bourse.herals(s);
        r[1]++;
    }

    // ------------------------------------------------------------------ sac à dos porté (Backpacked, par réflexion)
    private static Object sacs;
    private static Method sacDe;
    private static boolean sacsCherches;

    private static ItemStack sacPorte(Player p) {
        if (!sacsCherches) {
            sacsCherches = true;
            try {
                Object s = Class.forName("com.mrcrayfish.backpacked.platform.Services").getField("BACKPACK").get(null);
                sacDe = s.getClass().getMethod("getBackpackStack", Player.class);
                sacs = s;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                sacs = null;
            }
        }
        if (sacs == null) return ItemStack.EMPTY;
        try {
            return sacDe.invoke(sacs, p) instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return ItemStack.EMPTY;
        }
    }
}
