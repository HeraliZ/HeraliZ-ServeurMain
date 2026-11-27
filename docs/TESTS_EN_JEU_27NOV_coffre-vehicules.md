# Essais en jeu — coffre des véhicules (27/11, Monde 1.4.0)

Demande du propriétaire (validée) : Maj + clic droit sur un véhicule ouvre son coffre, pour emporter le butin d'un raid.
Tailles : moto 27 cases (9 × 3), buggy 72 (12 × 6), fourgon 90 (15 × 6), camion 144 (24 × 6). Ouvrable même en combat.
Refusé : à terre, en match, spectateur, à bord, sur une épave. Véhicule détruit : tout le contenu tombe au sol.

Rien n'a été essayé en jeu par Claude. Jars **joueurs ET serveur** de la même fabrication (nouveau menu
`heraliz_vehicules:coffre`, écran en verre chez le joueur).

## Ce qui a été fait

- Coffre à nous, gardé DANS le véhicule (sauvegardé avec lui) : il le suit quand le coin de carte se décharge, quand
  le véhicule change de monde et pendant `/ywzj_vehicle reload`. Le vieux coffre de 54 cases du moteur des véhicules
  reste retiré (réglage `coffre_retire`), aucun entonnoir n'a accès au nouveau.
- Écran en verre (comme les coffres des bases et la fouille) : nom du coffre en haut, grille, inventaire en bas. Le
  camion (24 × 6) fait 550 × 300 unités : il tient à l'échelle 2 dans une fenêtre 1366 × 768.
- Tous les joueurs qui ouvrent le même véhicule voient LE MÊME coffre. L'écran se ferme tout seul si : le véhicule roule
  (plus de 4 km/h), est détruit, disparaît (déchargé, rechargé, changé de monde), le joueur s'éloigne (6 blocs), monte à
  bord, tombe à terre ou meurt.
- Objets refusés dans le coffre : objets prêtés par un match, objets à vie (couteaux, skins à vie), sacs à dos pleins.
- Réglages : section `[coffre]` de `heraliz_vehicules-server.toml` (`actif`, `tailles`, `distance`, `vitesse_max_kmh`).

## À essayer

1. **Ouvrir** chaque véhicule (moto, buggy, fourgon, camion) avec Maj + clic droit : bon titre (« Coffre de la moto »…),
   bonne taille, aucune montée dans le véhicule. Sans Maj : on monte comme avant. Avec le jerrican ou le chalumeau en
   main (Maj + clic droit) : ils marchent comme avant (le coffre ne s'ouvre pas).
2. **Ranger / prendre** : clic, Maj + clic, glisser, touches 1 à 9, double clic. Essayer de ranger un couteau, un objet
   à vie, un sac plein : refusé. Un sac VIDE : accepté.
3. **Deux joueurs en même temps** sur le même véhicule : chacun voit les changements de l'autre aussitôt ; les deux
   prennent le même objet au même moment : un seul l'obtient.
4. **Refus** : à terre (message), en match (message), à bord d'un véhicule (message), véhicule qui roule (message),
   épave (message), en spectateur (rien). **En combat : ça s'ouvre.**
5. **Déconnexion écran ouvert** (avec un objet tenu à la souris) : à la reconnexion, l'objet est dans l'inventaire du
   joueur, et le coffre a tout le reste. Rien en double.
6. **Véhicule détruit** pendant que quelqu'un a le coffre ouvert : l'écran se ferme, tout le contenu tombe au sol à
   côté de l'épave (une seule fois), rien ne reste dans l'épave. Taper l'épave ensuite : rien de plus ne tombe.
7. **`/ywzj_vehicle reload`** avec un coffre plein (et un écran ouvert) : l'écran se ferme, le véhicule revient avec
   son contenu, RIEN ne tombe au sol (sinon : copie).
8. **Véhicule qui roule** avec un coffre ouvert par un autre joueur : l'écran se ferme dès qu'il dépasse 4 km/h.
9. **Débris** (morceaux après une explosion) : Maj + clic droit dessus ne fait rien de spécial.
10. **Coin de carte déchargé** (s'éloigner loin, revenir) et **redémarrage du serveur** : le contenu est toujours là.
11. **`/kill`** sur un véhicule plein : le contenu tombe au sol (une seule fois).
12. Le journal du serveur note chaque contenu posé au sol (`[vehicules] coffre retire : ... poses au sol`).
