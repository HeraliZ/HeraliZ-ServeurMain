package fr.heralisz.bases.dev;

import fr.heralisz.bases.client.BasesClient;
import fr.heralisz.bases.client.ClientBases;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.PillableVue;
import fr.heralisz.bases.model.Pillage;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.model.Banniere;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Etats factices du client pour le banc (captures des ecrans sans serveur prepare) : « base » (base prete, 2 salles
 * achetees, attaquee, journal de pillage), « pose » (pose a 40 %), « sansbase », « sansclan », « pillables » (8 bases,
 * fenetre ouverte), « pillables_ferme » (fenetre fermee). Interfaces : « sansbase_fonds » (banque insuffisante),
 * « sansbase_recrue » (sans BASE_GERER), « lecture » (base BUNK, recrue : entrer seulement, 4 salles achetees),
 * « dedans » (dans la base, salle 3), « pillables_vide ». Le plan vient du serveur (demande si absent).
 */
public final class FauxBases {
    private FauxBases() {
    }

    private static final UUID CLAN = UUID.nameUUIDFromBytes("heraliz.bases.faux".getBytes());

    private static FenetreVue fenetre(boolean ouverte) {
        return new FenetreVue(ouverte, 18, 23, ouverte ? 2L * 3600_000 + 14 * 60_000 : 5L * 3600_000 + 31 * 60_000, FenetreVue.Mode.AUTO);
    }

    private static List<BaseVue.SalleVue> salles(int achetees) {
        List<BaseVue.SalleVue> l = new ArrayList<>();
        Plan p = ClientBases.plan();
        long[] couts = {0, 5_000, 10_000, 20_000, 35_000, 50_000};
        int n = p == null ? 6 : p.salles.size();
        int vendues = 0;
        for (int i = 0; i < n; i++) {
            Plan.Salle s = p == null ? null : p.salles.get(i);
            boolean depart = s == null ? i == 0 : s.depart();
            String id = s == null ? String.valueOf(i + 1) : s.id();
            boolean ouverte = depart || vendues++ < achetees;
            l.add(new BaseVue.SalleVue(id, ouverte, depart, depart ? 0 : couts[Math.min(i, couts.length - 1)]));
        }
        return l;
    }

    private static Pillage pillage(long ilYaMs, String tag, int joueurs, Pillage.Issue issue) {
        Pillage p = new Pillage(System.currentTimeMillis() - ilYaMs, null, tag);
        p.fin = issue == Pillage.Issue.EN_COURS ? 0 : p.debut + 9 * 60_000;
        p.joueurs = joueurs;
        p.issue = issue;
        return p;
    }

    private static Banniere banniere(int a, int b) {
        return Banniere.fond(a, b, Banniere.Degrade.DIAGONAL);
    }

    public static void appliquer(String nom) {
        if (ClientBases.plan() == null) BasesClient.demander(Packets.ActionC2S.Genre.PLAN, "");
        ClientBases.setFaux(true);
        int toutes = (1 << BaseVue.Droit.values().length) - 1;
        switch (nom) {
            case "sansclan" -> ClientBases.setEtat(BaseVue.sansClan(10_000, fenetre(false)));
            case "sansbase" -> ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D), toutes, false,
                    Base.Etat.PRETE, 1f, 10_000, 14_250, salles(0), fenetre(false), List.of(), 0, List.of(), false, 0));
            case "pose" -> ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D), toutes, true,
                    Base.Etat.POSE, 0.4f, 10_000, 4_250, salles(0), fenetre(false), List.of(), 0, List.of(), false, 0));
            // ---- etats ajoutes pour les captures des interfaces
            case "sansbase_fonds" -> ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D), toutes, false,
                    Base.Etat.PRETE, 1f, 10_000, 4_250, salles(0), fenetre(false), List.of(), 0, List.of(), false, 0));
            case "sansbase_recrue" -> ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D),
                    1 << BaseVue.Droit.ENTRER.ordinal() | 1 << BaseVue.Droit.PORTES.ordinal(), false,
                    Base.Etat.PRETE, 1f, 10_000, 14_250, salles(0), fenetre(false), List.of(), 0, List.of(), false, 0));
            case "lecture" -> ClientBases.setEtat(new BaseVue(CLAN, "BUNK", "Les Terriers", banniere(0x2A2F3A, 0xE8B34A),
                    1 << BaseVue.Droit.ENTRER.ordinal() | 1 << BaseVue.Droit.PORTES.ordinal(), true,
                    Base.Etat.PRETE, 1f, 10_000, 2_100, salles(4), fenetre(false), List.of(), 0, List.of(), false, 0));
            case "dedans" -> {
                ClientBases.setHud(new Packets.HudS2C(Packets.HudS2C.Mode.BASE, "NOVA", 0xB8402E, "3", 0));
                ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D), toutes, true,
                        Base.Etat.PRETE, 1f, 10_000, 38_400, salles(2), fenetre(false), List.of(), 0,
                        List.of(pillage(26 * 3600_000L, "CRPR", 5, Pillage.Issue.DEFENDUE)), true, 0));
            }
            case "pillables_vide" -> ClientBases.setPillables(List.of(), fenetre(true));
            case "pillables", "pillables_ferme" -> {
                boolean ouverte = nom.equals("pillables");
                String[][] c = {{"RVN", "Ravenhold"}, {"CRPR", "Les Creepers"}, {"WOLF", "Meute Grise"}, {"ASH", "Cendres"},
                        {"K9", "Unite K9"}, {"OMEN", "Presage"}, {"SALT", "Sel de Terre"}, {"VLK", "Volkov"}};
                int[][] couleurs = {{0x1B1F2A, 0x8A2BE2}, {0x4FA63A, 0x4FA63A}, {0x5A5F69, 0xD9DDE3}, {0x3B2A20, 0xE07B39},
                        {0x10331F, 0x5FD08A}, {0x2B0F14, 0xC0283A}, {0xE8E2D0, 0x8F95A0}, {0x0D2A4A, 0x6FA8FF}};
                List<PillableVue> l = new ArrayList<>();
                for (int i = 0; i < c.length; i++) {
                    String raison = !ouverte ? "gui.heralisz_bases.raison.fenetre" : i == 2 ? "gui.heralisz_bases.raison.pleine" : "";
                    l.add(new PillableVue(UUID.nameUUIDFromBytes(c[i][0].getBytes()), c[i][0], c[i][1], banniere(couleurs[i][0], couleurs[i][1]),
                            4 + i % 5, i % 4, raison.isEmpty(), raison, i == 4, i == 2 ? 8 : i % 3));
                }
                ClientBases.setPillables(l, fenetre(ouverte));
            }
            default -> ClientBases.setEtat(new BaseVue(CLAN, "NOVA", "Les Charognards", banniere(0xB8402E, 0x2A1B3D), toutes, true,
                    Base.Etat.PRETE, 1f, 10_000, 38_400, salles(2), fenetre(true), List.of("RVN"), 3,
                    List.of(pillage(4 * 60_000, "RVN", 3, Pillage.Issue.EN_COURS), pillage(26 * 3600_000L, "CRPR", 5, Pillage.Issue.DEFENDUE),
                            pillage(50 * 3600_000L, "WOLF", 2, Pillage.Issue.PILLEE)), false, 0));
        }
    }
}
