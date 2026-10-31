# Essais en jeu du 31/10 — chantier « clans » (Clans 3.9.0)

Rien de ceci n'a été testé en jeu. Le jar n'a pas pu être fabriqué ici (dépôt Maven de Forge bloqué) : seules la courbe,
les plafonds, les dons et le recalcul des anciens clans ont été essayés hors du jeu (classes compilées à part).
**Canal réseau des clans passé en version 6** : jars joueurs et serveur de la même fabrication, puis redémarrage.
Règles et chiffres : `docs/SAISON_CLANS.md` §6 bis.

## 1. Recalcul des clans existants (au premier démarrage)
- [ ] Journal du serveur : une ligne `[clans] [TAG] nouvelle courbe des niveaux : niveau X -> niveau Y` par clan, puis
      « N clan(s) passe(s) sur la nouvelle courbe ». Au redémarrage suivant : plus aucune ligne (une seule fois).
- [ ] Un clan ancien niveau 10 passe niveau 2, 15 → 5, 20 → 9 (ordre d'idée) ; `/clan admin info` le montre.
- [ ] Ses améliorations achetées sont toutes là (onglet Niveau) ; « 0 POINT LIBRE » s'il en avait dépensé plus que son
      nouveau niveau n'en donne ; ses membres en trop restent dans le clan.
- [ ] Un membre qui se connecte reçoit une fois le message « Les niveaux de clan sont devenus plus durs… » ; une ligne
      « Nouvelle échelle des niveaux » est dans le journal du clan (onglet Aperçu). Pas de message pour un clan resté niveau 1.

## 2. Courbe et plafonds
- [ ] Clan neuf : onglet Niveau « encore 3 600 XP » pour le niveau 2.
- [ ] Tuer des zombies : +1 XP chacun jusqu'à 400 dans la journée ; à 400, message « Plafond du jour atteint pour « Tuer
      un zombie » (400 XP) » une seule fois ; ensuite l'XP ne bouge plus ; le lendemain (minuit, heure de Paris) ça repart.
- [ ] Onglet Niveau, colonne « Gagner de l'XP » : les gains affichés sont ceux du serveur ; au survol d'une ligne :
      « Plafond : 400 XP par jour pour le clan », « Aujourd'hui : x / 400 XP » ; ligne grisée quand le plafond est atteint ;
      « Quêtes de clan » : « Pas de plafond par jour ». Textes en français, anglais, russe : rien ne déborde de l'infobulle.
- [ ] Capture : 150 XP, au plus 450 par jour ; zone tenue : au plus 600 ; ravitaillement : au plus 300 ; joueur : 150.
- [ ] Une quête finie donne toujours son XP entière, même après tous les plafonds.
- [ ] `/clan admin xp TAG 5000` : jamais plafonné.
- [ ] `/clan admin recharger` relit `niveaux.json` (changer un plafond, recharger, vérifier au survol).

## 3. Dons (`/clan don`)
- [ ] Objet Épique à revendre en main (lingot d'argent, chronographe, collier de perles, statuette de jade) : +150 XP par
      objet ; Légendaire (diamant, lingot d'or, couronne, idole dorée, œuf orné) : +1 000 ; Unique (crâne de cristal,
      sceptre, calice) : +2 500.
- [ ] Objet Rare ou moins, arme, armure : refusé avec le message qui donne les 3 montants.
- [ ] En créatif : refusé.
- [ ] Au-delà de 3 000 XP de dons dans la journée : message « Plafond du jour des dons atteint… » et l'objet reste en main ;
      `/clan don confirmer` dans les 30 s : donné, 25 % de l'XP au-delà du plafond.
- [ ] Anti-copie : un objet Légendaire gardé 20 s dans l'inventaire (hors main) reçoit le numéro de Spartan ; le donner :
      accepté ; une copie de ce même objet (même numéro, par exemple donnée par le staff avec `/give` d'une copie NBT) :
      refusée « déjà donné à un clan », ligne d'avertissement dans le journal du serveur.
- [ ] Spartan : aucune fausse alerte de duplication sur ces objets en jeu normal (revente à Mila, coffre du clan,
      hôtel des ventes).

## 4. Captures au niveau 5, 2 zones au plus
- [ ] Clan niveau 1 à 4 au drapeau d'une zone libre : rien ne se capture, HUD « Capture verrouillée », message dans le chat
      (une fois toutes les 5 min) « Votre clan est niveau X : la capture des zones s'ouvre au niveau 5… ».
- [ ] Le même clan avec un clan capable de capturer au drapeau : l'autre clan capture normalement (pas de « Contesté »).
- [ ] Niveau 5 atteint : message « Niveau 5 : votre clan peut maintenant débloquer la capture des zones… ».
- [ ] Niveau 5 sans amélioration : HUD « Capture verrouillée », message « doit d'abord acheter l'amélioration Territoires ».
- [ ] Onglet Niveau, branche Territoires : « 0 zone(s) à la fois → 1 zone(s) à la fois », coût « Niv. 5 · 1 pt · … » en ambre
      avant le niveau 5 ; palier 2 demande le niveau 10 ; bouton grisé tant que le niveau manque.
- [ ] Palier 1 acheté : une zone se capture ; une 2e zone : HUD « Limite de territoires », message « tient déjà 1 zone(s)
      sur 1 » ; palier 2 (niveau 10) : 2 zones ; jamais une 3e.
- [ ] Un clan qui capture deux zones en même temps alors qu'il ne lui en reste qu'une : seule la première passe.
- [ ] Clan ancien qui tenait 3 zones : il les garde jusqu'à la fin de leur tenue (2 h), puis elles redeviennent libres.
- [ ] `/territoire capturer <zone> <tag>` (staff) : refusé pour une 3e zone ; accepté avec un avertissement orange pour un
      clan qui n'aurait pas pu la prendre lui-même.
- [ ] Onglet Aperçu, tuile Territoires : « Niv. 5 » avant le niveau 5, puis « x / max ».
- [ ] Panneau du drapeau (zone libre) : le texte dit « clan niveau 5 et amélioration Territoires, 2 zones au plus » sans
      déborder (3 langues).
- [ ] Quêtes de clan : un clan qui ne peut pas tenir de zone ne reçoit plus « Capturer une zone », « Tenir une zone »,
      « Lancer un ravitaillement » ; celles déjà tirées sont remplacées dans les 20 s.
