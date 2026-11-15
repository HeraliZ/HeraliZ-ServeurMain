# Essais en jeu — chantier « économie et ClashWar » (audit du 15/11)

ClashWar **1.1.3**, Boutique **1.5.8** (grades, hôtel des ventes, casino, caisses), Quêtes **1.21.3** (Mila, marchands,
recycleurs) ; réglages `serveur/config/heraliz_grades/hdv.json` (clé `plafond_pb` retirée) et
`serveur/config/heralizcasino/casino.json` (une ligne de la caisse retirée). Rien n'a été essayé en jeu par Claude, ni
fabriqué ici (Minecraft et Forge ne se téléchargent pas sur la machine de travail) : chaque fichier Java changé a été passé
au compilateur Java seul (aucune faute d'écriture) ; les noms de Minecraft et de Forge ont été relus à la main, ceux de
TaCZ vérifiés dans son jar. La fabrication complète se fera au contrôle de branche. `python outils/verifier_langues.py` :
0 erreur.

Jars : serveur ET joueurs pour les trois mods, comme d'habitude. Un ancien jar joueur ne plante pas mais montre des
textes faux : clé brute au pied de l'écran d'inscription du ClashWar, clé brute au recycleur pour un objet « À vider »,
infobulle de Mila sans la raison, « Prix maximal pour cette rareté » à l'hôtel des ventes.

## Ce qui change

| Constat | Pour le joueur |
|---|---|
| ClashWar, inventaire mis de côté (M4) | Au départ vers la salle, l'inventaire mis de côté est rendu **une seule fois**, quoi qu'il arrive : fin normale, abandon, déconnexion, mort, plantage, redémarrage. Avant : un plantage dans les minutes qui suivaient le départ rendait le dépôt à un joueur qui avait encore tout (objets à vie doublés). Un objet qu'un écran ouvert aurait fait tomber au sol au départ (inventaire plein) est gardé dans le dépôt et rendu avec le reste |
| ClashWar, règles du spawn (propriétaire) | Une équipe ne s'inscrit que si tous ses membres connectés sont au spawn (sinon refus qui les nomme). Pendant les inscriptions, un inscrit qui quitte le spawn retire son équipe : toute l'équipe est prévenue et peut se réinscrire depuis le spawn tant que c'est ouvert. Au départ, une équipe dont un joueur connecté n'est pas au spawn est retirée du tournoi : personne de l'équipe n'est vidé ni téléporté. Un clan peut toujours inscrire plusieurs équipes (voulu) |
| Objets en attente pendant un match (CRITIQUE n° 1) | Plus rien n'est livré pendant un match, dans la salle d'attente, dans les arènes, ni tant que l'inventaire est mis de côté (même à la reconnexion après un plantage) : caisses, kits, bons, caisse du casino, `/trade`, hôtel des ventes, quêtes, marchands, recycleur du spawn. Message « En match : … t'attend ; il te sera donné à ton retour. » ; livré dans les 5 s après le retour, s'il y a la place. Plus rien n'est effacé à la fin du match |
| Hôtel des ventes, prix en PB (décision du propriétaire, commit à part) | Plus **aucun** maximum selon la rareté : 1 000 000 PB est accepté pour n'importe quel objet. Seul reste le maximum technique du site : 100 000 000 PB payés par l'acheteur, frais compris (90 909 090 PB avec 10 % de frais). Le maximum en hérals ne change pas |
| Commandes des capsules, clés et caisses (n° 3) | `/casino jetons donner\|retirer`, `/casino jackpot`, `/casino compte donner\|retirer`, `/heraliz givekey\|keyall\|cles retirer\|givecrate\|interface` : op 4 ET connecté sur AuthMe, comme `/eco donner` (avant : op 2). La console (site, AzLink, boutique) ne change pas. Lecture (`/casino jetons voir`, `/heraliz cles voir`) : op 2 comme avant |
| Caisse du casino (n° 8) | L'« Attelle et bandage » (retirée du jeu le 03/11) n'est plus vendue. `outils/caisses.py --verifier` (lancé par `outils/verifier.py`) signale maintenant un objet retiré vendu à la caisse du casino |
| Mila, recycleurs, échanges (n° 2) | Une arme chargée, une arme avec des accessoires montés, une boîte de munitions pleine ou un sac plein n'est plus racheté par Mila, ni accepté par un recycleur (spawn et carte), un échange ou une livraison de quête du jour : il reste au joueur, rien n'est perdu, et l'écran dit quoi vider. Avant : balles et accessoires détruits sans prévenir. Hôtel des ventes inchangé |
| Hérals du recycleur (n° 5) | Quand rien ne sort, les hérals suivent l'état de l'objet, comme chez Mila (10 % au moins) : une arme Légendaire ruinée ne rapporte plus 63 hérals à coup sûr (contre 25 chez Mila). Objet sans usure : comme avant |
| Recycleur de la carte, quête du jour (n° 4) | Une case « à recycler » n'est qu'à un joueur à la fois : on ne pose plus rien sur la pile d'un autre. Chacun touche les hérals et la quête de ce qu'il a déposé. Quête « Recycler N objets » : les munitions comptent par paquet entier de 15 (avant : 8 balles = 8 objets), au spawn comme sur la carte |
| Hôtel des ventes, écritures (performances n° 3a) | Rien de visible : à chaque mise en vente, achat, retrait ou expiration, le serveur n'écrit plus que ce qui a changé ; le fichier complet est réécrit à part, au plus une fois par minute. Rien n'est perdu à un redémarrage ni à un plantage |
| Casino après un plantage (n° 6) | Une manche déjà payée n'est plus jamais rejouée ni payée deux fois : une machine dont l'état rechargé est plus ancien qu'un versement est remise à neuf. Une mise prise après la dernière sauvegarde du monde peut être perdue (« un plantage perd, il ne copie jamais ») |

Pas appliqué (décision du propriétaire) : le n° 9 (boîtes de munitions à l'hôtel des ventes et au recycleur). Le n° 7
(`/clan don`) est fait par un autre chantier.

## Essais hors du jeu

- Hôtel des ventes : le vrai code (écriture, journal d'écriture, relecture au démarrage, écriture en fond) recopié avec
  Gson et de petites copies des offres : plantages sans fichier complet, ligne coupée puis nouvelles écritures, ligne
  illisible, arrêt normal, fichier d'avant cette version, 200 écritures pendant une écriture en fond : état relu
  identique à chaque fois. Un vrai défaut trouvé ainsi a été corrigé avant le commit (une ligne coupée dans un ancien
  morceau du journal aurait fermé l'hôtel au redémarrage suivant).
- ClashWar : tout le cycle de l'inventaire mis de côté relu pas à pas (fin normale, abandon, déconnexion, reconnexion
  pendant et après le match, mort, plantage à chaque étape, `/wipe`, garde hors match).
- Le reste : relu, et passé au compilateur Java seul (écriture seulement, sans les jars du jeu).

## À vérifier en jeu

### ClashWar : spawn (deux clans d'essai, deux équipes chacun)
- [ ] tous au spawn : inscription acceptée, comme avant ; deux équipes du même clan : acceptées ;
- [ ] un membre sur la carte : inscription refusée ; le pied de l'écran dit « Pas au spawn : <pseudo>. Toute l'équipe
      doit y être. » et le chat donne la liste entière ;
- [ ] équipe inscrite, un membre part sur la carte : dans la seconde, toute l'équipe reçoit « <pseudo> a quitté le
      spawn : votre équipe [TAG] n est retirée de l'inscription… » ; l'écran ne la montre plus ; de retour au spawn,
      réinscription possible ; l'autre équipe du clan, restée au spawn, reste inscrite ;
- [ ] départ avec un membre sur la carte : son équipe est retirée (« … n'était pas au spawn au départ du ClashWar… »),
      personne de cette équipe n'est vidé ni téléporté ; les autres équipes partent normalement ;
- [ ] (ClashWar 1.1.4, règle du propriétaire du 15/11) équipe inscrite, un membre se déconnecte : au moment même, ses
      coéquipiers connectés reçoivent « <pseudo> s'est déconnecté : votre équipe [TAG] n est retirée de l'inscription… » ;
      l'écran ne la montre plus ; réinscription possible depuis le spawn, seulement avec des membres connectés (un membre
      hors ligne : « Seuls les membres connectés peuvent être inscrits. ») ; l'autre équipe du clan reste inscrite ;
- [ ] déconnexion entre la fin des inscriptions et l'entrée dans la salle : l'équipe est retirée du tournoi (« … s'est
      déconnecté : votre équipe … est retirée du ClashWar. »), personne de cette équipe n'est vidé ni téléporté ;
      déconnexion après l'entrée dans la salle : règles du match, comme avant.

### ClashWar : inventaire mis de côté (noter avant l'inventaire exact : sac, armes, objets à vie, Curios)
- [ ] fin normale du match puis retour : tout revient, une fois ;
- [ ] abandon, éliminé (tué) dans l'arène, déconnexion pendant le match puis reconnexion pendant, puis après le match :
      tout revient une fois ;
- [ ] **plantage** : arrêt brutal du serveur (`kill -9`, ou « Kill » du Ptero) 5 à 10 s après le départ vers la salle,
      puis redémarrage et reconnexion : tout revient une fois, rien en double (compter les objets à vie) ;
- [ ] même essai en plein match ;
- [ ] départ avec un écran ouvert (sac ou coffre), un objet tenu à la souris et l'inventaire plein : rien au sol au
      spawn, l'objet revient avec le reste ;
- [ ] journal du serveur : « coffre de … depose » puis « coffre de … rendu » ; jamais « enquete » ni « sans_marque » en
      jeu normal ; le dossier `heraliz_clashwar/coffres/enquete/` reste vide.

### Objets en attente pendant un match
- [ ] inventaire plein, recevoir quelque chose qui se met en attente (récompense de caisse, kit, achat de la boutique,
      récompense de quête), puis jouer un ClashWar : rien n'apparaît pendant le match (message « En match : … t'attend ;
      il te sera donné à ton retour. ») ;
- [ ] après le retour : l'inventaire revient d'abord ; en faisant de la place, l'objet arrive dans les 5 s ; rien n'a
      été effacé à la fin du match ;
- [ ] plantage pendant le match avec un objet en attente, redémarrage, reconnexion : inventaire rendu, puis l'objet ;
      rien perdu, rien en double ;
- [ ] hors match : livraisons comme avant (caisses, kits, `/trade`, hôtel des ventes, quêtes, recycleur du spawn).

### Hôtel des ventes : prix en PB
- [ ] un objet Commun mis en vente à 1 000 000 PB : accepté ; sous la durée, la ligne montre le maximum en hérals ;
- [ ] 95 000 000 PB (frais de 10 %) : champ rouge, « Prix maximal : 90 909 090 PB », vente impossible ; s'il passait
      quand même, le serveur refuse : « Prix trop élevé : au-dessus du maximum autorisé. » ;
- [ ] achat d'une offre à 1 000 000 PB par un autre joueur : débit et crédit du site justes (frais compris) ;
- [ ] maximum en hérals : inchangé ;
- [ ] sur le Ptero, `config/heraliz_grades/hdv.json` sans la clé `plafond_pb` après l'envoi (une vieille copie qui la
      garderait est sans effet : la clé est ignorée).

### Hôtel des ventes : écritures
- [ ] mettre en vente, acheter (hérals et PB), retirer, laisser expirer, récupérer : comme avant ;
- [ ] juste après ces actions (moins d'une minute), arrêt brutal du serveur, redémarrage : tout est là (offres, achats,
      objets et hérals à récupérer, historique), rien en double ;
- [ ] arrêt normal : plus aucun fichier `hdv.json.wal.*` dans `heraliz_grades/hdv/` ; aucune erreur « hôtel des
      ventes » au journal du serveur ;
- [ ] `/diag profil` pendant des ventes : l'écriture de l'hôtel des ventes ne pèse plus sur le fil du serveur.

### Commandes (op 4 + AuthMe)
- [ ] un op 2 : `/casino jetons donner`, `/casino jackpot`, `/heraliz givekey`, `givecrate`, `keyall`, `cles retirer`,
      `interface` : refusés ; un op 4 connecté : acceptés, avec une ligne au journal du serveur ;
- [ ] (AuthMe bloque déjà les commandes avant `/login` : le refus « Connecte-toi d'abord (/login)… » est un garde-fou,
      comme pour `/eco donner`) ;
- [ ] console, AzLink et achats sur le site (clés, capsules, caisses) : livrés comme avant ;
- [ ] `/casino jetons voir`, `/heraliz cles voir` : toujours permis à l'op 2.

### Caisse du casino
- [ ] plus d'« Attelle et bandage » ; les autres lignes inchangées ;
- [ ] `python outils/verifier.py boutique` : 0 problème.

### Mila, recycleurs, échanges
- [ ] arme chargée chez Mila : pas rachetée, infobulle « Arme chargée : décharge-la d'abord… » ; déchargée (touche N,
      « Décharger l'arme ») : rachetée comme avant ;
- [ ] arme avec une lunette ou un silencieux : « Accessoires montés : retire-les d'abord… » ; sans : rachetée ;
- [ ] boîte de munitions pleine dans un recycleur (spawn et carte) : refusée, aperçu « À vider » et « Boîte de munitions
      pleine : vide-la d'abord… » ; vide : recyclée comme avant ;
- [ ] sac plein : « Sac plein : vide-le d'abord… » ;
- [ ] échange avec un PNJ, livraison d'une quête du jour : arme chargée refusée, déchargée acceptée ;
- [ ] hôtel des ventes : inchangé (l'arme est déchargée à la mise en vente, les accessoires partent avec elle).

### Recycleur : hérals selon l'état
- [ ] objet usé (par exemple 30 %) qui ne rend rien : environ 30 % des hérals pleins (10 % au moins) ; l'aperçu et le
      paiement disent le même chiffre ; objet neuf : comme avant.

### Recycleur de la carte et quête du jour
- [ ] A pose 10 balles dans une case ; B essaie d'en poser 1 sur cette pile : refusé, message « Cette case est à un
      autre joueur : prends une case libre (chacun touche ce qu'il dépose). » ; dans une case vide : accepté ;
- [ ] B, Maj + clic sur ses balles : elles vont dans une case vide ou sur sa propre pile, jamais sur celle de A ;
      glisser sur plusieurs cases et touches 1 à 9 : la case de A refuse aussi ;
- [ ] tout le monde peut toujours PRENDRE dans la machine (contenu partagé, comme avant) ;
- [ ] hérals « rien » et quête : à celui qui a déposé (A), même hors ligne pour les hérals ;
- [ ] quête « Recycler N objets » : 8 balles ne comptent plus ; 15 balles = 1 objet ; une arme = 1 objet ; au spawn
      aussi ; aide de la quête : « Au recycleur (munitions : par paquet entier) ».

### Casino après un plantage
- [ ] jeu normal sur chaque machine (machines à sous, roulette, crash, course, etc.) : comme avant ; un redémarrage normal
      n'écrit aucune ligne « Remise a neuf » ;
- [ ] **plantage** : miser à la roulette, gagner (gain versé sur le compte de capsules), puis tout de suite arrêt brutal
      (avant la sauvegarde automatique du monde, 5 min par défaut) ; redémarrage : la machine est vide, la manche n'est
      pas rejouée ni payée deux fois ; journal : « [HeralizCasino] machine … plus ancien qu'un versement deja fait …
      Remise a neuf » ; le solde est celui d'après le gain, une fois ;
- [ ] le fichier `heralizcasino/manches.log` existe sur le serveur (une ligne par machine après un redémarrage) ;
- [ ] à savoir : une machine cassée puis reposée au même endroit écrit aussi une ligne « Remise a neuf » au journal, sans
      conséquence (elle est déjà neuve, elle marche dès le tick suivant).

## Ce qui n'a pas été vérifié

- Rien n'a été fabriqué avec Minecraft et Forge : une faute de nom (méthode de Minecraft mal recopiée) ne se verra qu'à la
  fabrication du contrôle de branche ; rien n'a été essayé en jeu.
- ClashWar : depuis la 1.1.4 (15/11), un inscrit qui se déconnecte retire son équipe au moment même (pendant les
  inscriptions, ou entre leur fin et l'entrée dans la salle). Le refus de l'écran d'inscription coupe une longue liste de
  pseudos (le chat la donne en entier).
- Plantage réel du serveur (casino, hôtel des ventes, ClashWar) : raisonné et, pour l'hôtel des ventes, essayé hors du
  jeu ; jamais avec le vrai serveur.
- Casino : une mise prise après la dernière sauvegarde du monde peut être perdue au plantage ; le jackpot (pot commun)
  n'est pas remis en arrière.
- Hôtel des ventes : les lignes de `journal.log` écrites en fond dans les dernières secondes avant un plantage peuvent
  manquer (le journal sert à lire ce qui s'est passé ; l'état des ventes, lui, est complet).
- `docs/COMMANDES_INVENTAIRE.md` n'a pas été refait (l'outil ramenait des changements d'autres chantiers) : à relancer
  après l'intégration (`python outils/commandes.py`).
