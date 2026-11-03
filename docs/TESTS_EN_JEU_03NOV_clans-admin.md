# Essais en jeu — retours du 03/11, chantier « clans-admin »

Boutique 1.5.0, Quêtes 1.18.0, Clans 3.12.0. Rien de tout cela n'a été testé en jeu ni compilé par Gradle ici (Forge
injoignable) : à faire après la fabrication de GitHub. Canaux changés : panel du staff (3), quêtes (13), clans (10) :
jars joueurs et serveur de la même fabrication.

## 1. Chat Proximité muet
- [ ] Seul à plus de 48 blocs, écrire sur le canal Proximité : le message s'affiche chez soi, et RIEN d'autre (plus de
  « Personne à moins de 48 blocs… »).
- [ ] Avec un joueur à moins de 48 blocs : il lit le message ; celui qui écrit ne voit aucune différence.

## 2. Chat caché pendant la visite et le tutoriel de Varga
Avec un compte neuf, en survie, après /login :
- [ ] Guide, « Fais-moi visiter » : le chat disparaît (ni lignes, ni T, ni /) pendant toute la visite.
- [ ] Espace maintenu (visite passée) : le chat revient tout de suite ; fin normale de la visite : il revient aussi.
- [ ] Varga, « Faire ma base » : chat caché pendant le kit, toute la cabane, puis les mots de la fin de Varga ; il
  revient après son dernier mot.
- [ ] Un autre joueur écrit pendant ce temps : en rouvrant le chat après, ses messages sont là.
- [ ] Se déconnecter en plein tutoriel puis revenir : /login possible (chat ouvrable avant le /login), puis chat de
  nouveau caché (le tutoriel reprend).
- [ ] Staff en créatif (/tuto lancer) : le chat reste là (voulu : il garde ses commandes).
- [ ] Le chat ouvert au moment où la visite commence se ferme.

## 3. Onglet « Économie » du panneau P (Fondateur : grade Fondateur, op 4, /login fait)
- [ ] Un Admin ou un Modérateur ne voit pas l'onglet ; le Fondateur voit 9 onglets sur une rangée (fr, en, ru).
- [ ] Hérals : donner 1 000 à un joueur en ligne (deux clics), la fiche passe au nouveau solde, le joueur reçoit le
  message ; retirer plus qu'il n'a : refus clair ; donner au-delà de 10 millions de solde : refus « plafond ».
- [ ] Hérals à un joueur absent : écrire son pseudo dans la recherche, choisir la ligne « → pseudo » : solde lu, don
  fait ; pseudo jamais venu : « aucun joueur ».
- [ ] Clés : chaque sorte (Vote, Quêtes, Événement, Gemme, Camos, Emotes), donner 5 puis retirer 2 ; vérifier dans
  /boutique, onglet « Mes clés ».
- [ ] PB : donner 100 à un joueur (solde du site à jour), retirer 100 ; montant 20 001 refusé par l'écran ; site coupé
  ou clé absente : refus clair, rien de fait. (Le site refuse au-delà de 50 000 PB créés en 24 h pour tout le serveur.)
- [ ] Points de saison : choisir un clan, PVE + 10, puis PVP - 5 ; /saison journal <tag> montre « correction : panel du
  staff : motif » au nom du Fondateur ; retirer plus que le clan n'a : refus.
- [ ] XP du clan : donner assez pour passer un niveau (annonce au clan) ; retirer : seulement l'XP du niveau en cours
  (refus au-delà, jamais de niveau perdu).
- [ ] Sans motif : refus ; montant vide : refus ; un clic seul ne fait rien (« Clique encore »), le second dans les 5 s.
- [ ] Chaque opération : ligne dans l'onglet Historique (qui, quoi, combien, motif), message sur le salon Discord du
  staff, et dans `heraliz_grades/journal_staff.log` ; hérals aussi dans `heralisz_economie/journal_admin.log`, clés dans
  `heraliz_crates/cles_journal.log`, PB dans le journal de la boutique.

## 4. Infobulle des objets de valeur
- [ ] Objet de valeur Épique / Légendaire / Unique (Mila les rachète) : ligne dorée « Se donne au clan : +150 / +1 000 /
  +2 500 XP (/clan don, au spawn) » ; « … chacun » sur une pile ; rien sur un objet Commun à Rare ni sur les autres.
- [ ] Changer `dons` de `config/heralisz_clans/niveaux.json`, /recharger clans : l'infobulle suit sans se reconnecter.

## 5. Ligne « Membres » de l'arbre (touche K, onglet Niveau)
- [ ] En tête de l'arbre : « Membres  5 membres → 6 », pastilles, « 5 (niv. 2) 6 (niv. 5) 7 (niv. 10) 8 (niv. 15) »
  (dorés si atteints), « Au niveau X » à la place du bouton ; « Tout débloqué » au niveau 15 ; aucun bouton, rien à payer.
- [ ] Au survol : chaque palier débloqué ou bloqué jusqu'au niveau X. Les boutons Améliorer des branches sont bien en
  face de leur ligne ; la saison tient dans la carte.

## 6. Noms des objets dans les messages
- [ ] Caisse qui donne une arme TaCZ : le chat dit le nom de l'arme (pas « item.tacz.modern_kinetic_gun »), titre à
  l'écran aussi ; annonce à tous pour un objet rare.
- [ ] Acheter une arme à un marchand, l'échanger, inventaire plein (« t'attend ») : vrai nom.
- [ ] Hôtel des ventes : acheter / vendre une arme : vrai nom dans les deux messages.
- [ ] /clan don : le chat du clan dit le nom de l'objet traduit (pas une clé).
