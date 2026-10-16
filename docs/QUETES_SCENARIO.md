# HeraliZ — Scénario des PNJ et des quêtes

> Document de conception (16/10). **Rien n'est codé.** C'est le scénario à valider par les propriétaires avant
> d'écrire le mod `heraliz-quetes`. Tout ce qui est marqué **[À VALIDER]** est une proposition : à garder, changer ou
> retirer. Les noms de lieux viennent des zones de loot de la nouvelle carte (`belcourt`, `clairval`, `prison`...) :
> les endroits exacts (le bureau, l'armoire, la balise) seront posés **en jeu** par le staff, jamais dans les fichiers
> du monde (règle 10).

---

## Sommaire

0. La version « spawn » (PNJ placés par le staff, interactions sur la carte)
1. L'idée en une page
2. L'histoire de la Zone (le « lore »)
3. Les 8 PNJ
4. Les briques de quête (tout ce qu'un PNJ peut demander)
5. L'histoire principale : « Le Dernier Convoi » (Prologue + 4 actes)
6. Les chaînes de quêtes de chaque PNJ
7. Les contrats du jour et de la semaine (pour ne jamais s'ennuyer)
8. Quêtes de clan, événements du serveur, secrets
9. Réputation, récompenses et équilibre
10. Comment le joueur le vit (écrans, carte, journal)
11. Règles contre la triche et l'abus
12. Ce qu'il faudra construire (aperçu pour la phase code)
13. Questions pour les propriétaires

---

## 0. La version « spawn » (demande du propriétaire du 16/10) — c'est elle qui compte

> Précision du propriétaire : les PNJ sont **au spawn**, **placés par lui où il veut** (camp d'entraînement, etc.),
> chacun a **son nom**, une **phrase d'ambiance** liée à son coin, se **présente** au nouveau joueur, puis lui
> **demande de l'aide**. Les quêtes envoient sur la carte chercher des **objets de quête posés par le staff dans les
> villes**. Le reste du document (histoire, actes) devient un fil rouge facultatif qu'on pourra brancher plus tard.

### 0.1 Un PNJ, en 4 temps (exemple : Jack, au camp d'entraînement)

| Moment | Ce qui se passe | Exemple |
|---|---|---|
| **Ambiance** | quand un joueur passe à moins de 8 blocs, une petite bulle au-dessus du PNJ (au plus une fois par minute et par joueur) | « Si tu tiens ton arme comme ça, c'est toi que tu vas toucher. » / « On vise la cible, pas le copain. » |
| **Présentation** | 1re fois qu'on lui parle (clic droit) : écran de dialogue | « Hé, toi. Attends. T'es nouveau, je te connais pas. Moi c'est Jack, je m'occupe du camp. Ici on apprend à pas mourir bêtement. » |
| **Demande** | il propose une quête ; « J'accepte » / « Plus tard » | « Tu veux te rendre utile ? Un gars à moi a laissé son carnet de tir à **Belcourt**, dans le **commissariat**. Ramène-le-moi. » |
| **Ensuite** | à chaque visite : une phrase « on se connaît », l'avancement, la quête suivante | « Alors, ce carnet ? » → « Bien joué. Tiens, 200 hérals. J'aurai autre chose demain. » |

Chaque PNJ a donc : un **nom**, un **skin**, une **liste de phrases d'ambiance**, une **présentation**, des
**phrases d'accueil** (inconnu / connu / ami) et **ses quêtes** (dans l'ordre, ou au hasard pour les contrats du jour).

### 0.2 Ce que le staff fait EN JEU (aucun fichier du monde touché)

**Poser un PNJ** : se placer à l'endroit, regarder dans la bonne direction, puis
`/pnj creer jack "Jack"` → le PNJ apparaît, invincible, regarde les joueurs qui passent. Ensuite
`/pnj skin jack <pseudo ou fichier>`, `/pnj deplacer jack` (à l'endroit où l'on est), `/pnj geste jack <emote>`,
`/pnj retirer jack`. Ses textes et ses quêtes sont dans son fichier (`serveur/config/heraliz_quetes/pnj/jack.json`),
qu'on remplit avec Claude en lui disant simplement en français ce qu'on veut.

**Poser les interactions sur la carte** avec une **baguette de quête** (comme la baguette des salles à carte) :
on se met en créatif, on prend la baguette, on choisit le point de quête à poser (`/quete baguette carnet_jack`), puis
on fait **clic droit sur le bloc** voulu (un bureau, un tiroir, un casier, un ordinateur, un cadavre, une étagère...).
Ce bloc devient un **point de quête**, sans être modifié (le point est gardé à part, comme les salles à carte). Sortes
de points :

| Point | Ce que voit / fait le joueur qui a la quête | Les autres joueurs |
|---|---|---|
| **Objet à ramasser** | une petite lueur sur le bloc ; clic droit = « Vous trouvez : Carnet de tir de Jack » (objet de quête dans l'inventaire) | rien, le bloc est normal |
| **Indice à lire** | clic droit = une lettre, une note, une photo s'affiche (ex. le code d'un coffre) | rien |
| **Endroit à atteindre** | zone (rayon réglable) : « Vous êtes arrivé au commissariat » | rien |
| **Coffre à code** | clic droit = petit clavier, il faut taper le code trouvé ailleurs | rien |
| **Objet à déposer** | clic droit avec l'objet en main = il est déposé (« la radio est réparée ») | rien |
| **Fouille par le loot** | un conteneur HeraliZ Loot précis donne l'objet de quête en plus de son loot, seulement à celui qui a la quête | loot normal |

`/quete points` liste les points posés, `/quete point voir <id>` y emmène, `/quete point retirer <id>` l'efface.
En créatif, le staff voit tous les points (petites étiquettes) pour vérifier son travail.

**Les objets de quête** sont des objets génériques du mod, renommés selon la quête : document, carnet, lettre,
photo, clé, clé USB, colis, échantillon, plaque d'identité, pièce mécanique (modèles à faire valider en rendus
d'abord). Le nom et la description viennent de la quête (« Carnet de tir de Jack »). Ils ne servent qu'à la quête.

### 0.3 Écrire une quête (sans être développeur)

On dit à Claude, en français : « Jack demande de ramener son carnet de tir, au commissariat de Belcourt, dans le
tiroir du bureau du chef ; récompense 200 hérals. » Claude écrit la quête dans le fichier de Jack ; il reste au staff
à poser le point avec la baguette, à l'endroit exact, puis `/quete recharger`. Plus tard, si besoin : un écran
d'édition en jeu.

Exemple de ce que donne le fichier (pour info, c'est Claude qui l'écrit) :

```
PNJ « jack » — Jack, instructeur du camp
  ambiance : « On vise la cible, pas le copain. » | « Recharge AVANT d'être à sec. » | « Moins de bruit, plus de balles. »
  presentation : « Hé, toi. Attends. T'es nouveau, je te connais pas. Moi c'est Jack... »
  quete « carnet_jack » :
    demande : « Un gars à moi a laissé son carnet de tir à Belcourt, au commissariat. Ramène-le-moi. »
    etape 1 : RAMASSER au point « carnet_jack » → objet « Carnet de tir de Jack »
    etape 2 : RAPPORTER le carnet à Jack
    fin : « Bien joué. Tiens. J'aurai autre chose demain. » → 200 hérals
```

### 0.4 Idées de PNJ du spawn (noms et places à choisir par le propriétaire)

| PNJ | Où au spawn | Phrase d'ambiance | Genre de quêtes |
|---|---|---|---|
| **Jack**, instructeur | camp d'entraînement | « On vise la cible, pas le copain. » | carnets et caisses de munitions à retrouver, tuer des zombies avec telle arme, apprendre à se soigner |
| la **docteure** | infirmerie | « Si tu saignes, assieds-toi. Si tu tousses, va-t'en. » | médicaments à ramener d'un hôpital, échantillons, réanimer quelqu'un |
| le **mécano** | atelier / garage | « T'aurais pas un ressort ? Non ? T'as une tête à en trouver. » | pièces (composants), réparer un objet posé sur la carte, ramener un véhicule |
| l'**opératrice radio** | poste radio | « Fréquence 104.7... toujours rien. » | documents dans des bureaux, coffres à code, balises à relever |
| la **contrebandière** | marché | « Tout se vend. Même toi. » | objets de valeur à revendre, colis à livrer |
| le **patron du casino** | entrée du casino | « Ici on meurt de rire, pas de faim. » | essayer les jeux, petits paris |
| le **chef du refuge** | au centre du spawn | « Doucement, petit. T'es au terminus. » | le premier qu'on voit : il envoie vers les autres |

On peut en ajouter autant qu'on veut : un PNJ = un fichier + `/pnj creer` en jeu.

---

## 1. L'idée en une page

- **8 PNJ** vivent au **Refuge** (le spawn, la grotte du terminus du train) et un peu partout sur la carte. Chacun a
  sa personnalité, sa façon de parler, son métier, **sa propre réputation** avec le joueur et **ses propres quêtes**.
- **Trois sortes de quêtes** :
  1. **L'histoire principale** « Le Dernier Convoi » : ce qui s'est passé dans la Zone, pourquoi le train a déraillé,
     ce que cache le Secteur 7. Un prologue (tutoriel caché) puis 4 actes. Elle se termine par **un vrai choix** qui
     change la suite pour le joueur.
  2. **Les chaînes des PNJ** : 6 à 10 quêtes écrites à la main par personnage, débloquées par la réputation.
  3. **Les contrats** : quêtes courtes **fabriquées toutes seules** à partir de modèles (« ramène-moi 3 {objet} »,
     « va relever la balise de {lieu} »...). 3 par PNJ et par jour, plus des primes de la semaine. C'est ce qui fait
     qu'il y a **toujours** quelque chose à faire.
- **Un PNJ peut demander n'importe quoi** : n'importe quel objet du jeu (même d'un autre mod, même un objet précis
  d'une rareté minimum ou en bon état), aller quelque part, fouiller un bureau précis, tuer, survivre dans le gaz,
  jouer au casino, gagner un duel, s'extraire, tenir un territoire, réanimer un joueur, prendre le train... Toutes ces
  « briques » se combinent (section 4).
- **Tout est en fichiers de réglage** (`serveur/config/heraliz_quetes/`) : on peut ajouter un PNJ, une quête ou un
  contrat **sans refaire le mod**, et le staff peut poser en jeu les points de quête (le « bureau du directeur »).
- **Esprit DayZ** : un renseignement ou un colis **ne compte que s'il revient au Refuge**. On peut mourir avec, se le
  faire voler, devoir s'extraire. Le risque fait le plaisir. **[À VALIDER : voir question 1]**
- **Pas de pay2win, rien de nouveau dans le loot ni dans le craft** (choix du propriétaire, section 10 de CLAUDE.md) :
  les récompenses sont des hérals, de la réputation, des titres, des cosmétiques, des accès et des réductions.

---

## 2. L'histoire de la Zone

Ce que les joueurs découvrent petit à petit (jamais tout d'un coup : par des documents, des enregistrements, les PNJ).

**Avant.** La région s'appelait la **Zone d'exploitation Héral** : des mines, une raffinerie (`petrole`), une base
aérienne (`aerodrome`), une prison de haute sécurité (`prison`), un port (`port`, `jetee`), des villes ouvrières
(Belcourt, Clairval, Marbois, Valmont, Tcherno). Sous la Zone, relié au réseau ferré par une voie secrète : le
**Secteur 7**, un laboratoire militaire. La Compagnie Héral payait ses ouvriers en **jetons gravés** : les « hérals »
(c'est pour ça que la monnaie du serveur s'appelle comme ça : au Refuge, ces vieux jetons sont redevenus de l'argent).

**Le Secteur 7.** Le docteur **Aurèle Kessel** y menait le **Projet Racine** : des sujets capables de survivre au gaz,
au froid et aux radiations. Le meilleur résultat : le **Sujet N-07**. Pour nourrir le projet, Kessel prélevait des
souches anciennes partout dans la Zone :
- dans le grand nord (`arctique`), sous la glace, le corps d'un mammouth vénéré par une ancienne tribu : **Ymirak** ;
- dans la forêt, l'esprit-cerf que les bûcherons appelaient **Sylvorn** ;
- dans les ruines, un nid d'araignées géantes : la future **Mère des Ruines** ;
- et des détenus de la prison, utilisés comme cobayes « volontaires ».

**La Fièvre.** Un des cobayes s'est échappé en portant la souche : le **Porteur de Peste**. En trois semaines, la
Fièvre a changé la population en **goules** (les zombies). Le gaz s'est répandu dans les vallées (`gaz`), la faille
s'est ouverte (`faille`), les radiations du silo (`silo`) ont fui.

**Le Dernier Convoi.** L'armée a évacué par le train. Le **Convoi 12** était le dernier. Le joueur était dedans. Dans le
tunnel, les goules ont attaqué (la cinématique d'arrivée), le convoi a déraillé au **terminus** : une grotte qui sert
maintenant de **Refuge des survivants**. Le joueur se réveille dans l'épave. Il ne se souvient presque de rien.

**Le secret que l'histoire révèle à la fin** : le Convoi 12 n'évacuait pas des civils. Il transportait **le cœur du
Projet Racine** (le sérum « Racine ») vers l'extérieur, et le joueur n'était pas un passager comme les autres : il
était l'**escorte** (ou un cobaye, selon le choix final). Kessel est peut-être encore vivant, quelque part dans le
Secteur 7.

---

## 3. Les 8 PNJ

Chaque PNJ : un nom, un rôle, une voix (sa façon de parler dans les dialogues), un lieu, ce qu'il demande, ce qu'il
donne. Les skins seront des modèles de joueur (rendus à valider avant, règle 9). Ils sont **invincibles**, ne bougent
pas (ou très peu : ils regardent le joueur, gestes d'emote), et parlent par un **écran de dialogue au style des
clans**.

| # | PNJ | Rôle | Où | Sa voix | Ce qu'il demande |
|---|---|---|---|---|---|
| 1 | **Le Doyen** (Augustin Marel) | ancien chef de gare, chef du Refuge | Refuge, près de l'épave | calme, paternel, phrases courtes | histoire principale, premiers pas, gros choix |
| 2 | **Docteure Vasseur** (Iris) | médecin | Refuge, infirmerie | sèche, précise, humour noir | soins, échantillons, gaz, peste, réanimer |
| 3 | **Ferraille** (Bastien Roque) | mécanicien, armurier | Refuge, atelier près des rails | gouailleur, argot, parle vite | composants, armes usées, véhicules, train |
| 4 | **La Voix** (Nadia Orlov) | opératrice radio | Refuge, poste radio (+ antenne de `radio`) | mystérieuse, parle en codes, fréquences | renseignements, documents, balises, énigmes |
| 5 | **Commandant Ressac** | ancien officier du Convoi 12 | Refuge, poste de garde | militaire, ordres, vouvoie | nettoyer des zones, boss, salles à carte, armes |
| 6 | **Mila la Contrebandière** | marchande, passeuse | Refuge, coin du marché (+ `port` la nuit) | charmeuse, menteuse, tout se négocie | objets de valeur, livraisons, vols, clans |
| 7 | **Monsieur Lucky** | patron du casino | au casino | showman, flamboyant, aime le risque | jouer, parier, gagner, perdre avec panache |
| 8 | **Le Vieil Oskar** | ermite, dernier de la tribu du nord | **pas au Refuge** : cabane cachée (`arctique` ou forêt) | lent, mystique, parle par images | légendes, Ymirak, Sylvorn, secrets, énigmes |

Et un « PNJ » qu'on ne voit jamais : **le docteur Kessel**, qui parle par des **enregistrements** et des **terminaux**
retrouvés sur la carte (et, au dernier acte, par la radio de La Voix).

### Fiches détaillées

**1. Le Doyen** — Chef de gare depuis 30 ans, il a vu passer tous les trains. Il garde le Refuge en vie.
- *Accueil* : « Doucement. Tu as pris un sacré coup. Tu es au terminus, petit. Le Convoi 12 n'ira pas plus loin. »
- Donne : l'histoire principale, les choix, l'ouverture des autres PNJ.
- Spécial : c'est lui qui « valide » ce qu'on rapporte au Refuge (les renseignements de La Voix passent par lui à la
  fin de l'acte II).

**2. Docteure Vasseur** — Médecin militaire du Convoi 12, seule survivante de l'équipe médicale. Cherche un remède à
la Fièvre (et soupçonne le Convoi d'avoir transporté autre chose que des blessés).
- *Accueil* : « Ne touche à rien. Si tu saignes, assieds-toi. Si tu tousses, va-t'en. »
- Demande : objets de soin (bandages, attelles, Surv12...), **échantillons** (spores du Porteur de Peste, venin de la
  Mère des Ruines, sang de goule), mesures dans le gaz (avec masque), réanimer des inconnus.
- Donne : hérals, réduction sur les soins (si un jour la boutique du Refuge existe), **titre « Infirmier de la Zone »**.

**3. Ferraille** — Il répare tout ce qui a un moteur ou une gâchette. Rêve de remettre en route **la vieille
locomotive** du terminus.
- *Accueil* : « Ah, un vivant ! Super. T'as pas un ressort sur toi ? Non ? Tant pis, t'as une tête à en trouver. »
- Demande : **composants** de HeraliZ Composants (canon, ressort, kit de pièces, batterie, circuit imprimé, plaques...),
  armes très usées (l'usure d'Utils), pièces de véhicule, ferraille en quantité, aller réparer des choses sur la carte.
- Donne : hérals, **réparation offerte** d'une arme (si le système d'usure le permet), titre « Mécano ».

**4. La Voix** — Elle capte tout. Elle sait des choses qu'elle ne devrait pas savoir. Elle ne sort jamais du poste.
- *Accueil* : « Fréquence 104.7. Si tu m'entends, c'est que tu es vivant. Si tu es vivant, j'ai du travail pour toi. »
- Demande : **renseignements** (documents dans des bureaux, coffres-forts, ordinateurs), relever des **balises**,
  écouter une fréquence à un endroit précis, trouver des **codes**, observer un lieu aux **jumelles**.
- Donne : hérals, **pistes** (elle montre sur la carte des endroits secrets), les codes des terminaux du Secteur 7.
- C'est elle qui donne la quête de l'exemple : « va à Clairval, trouve le dossier dans le bureau du directeur ».

**5. Commandant Ressac** — Commandait l'escorte du Convoi 12. Il sait ce que transportait le train et ne le dit pas.
Veut reprendre la Zone aux goules « secteur par secteur ».
- *Accueil* : « Nom, matricule. ... Pas de matricule ? Alors vous êtes civil. Les civils, ici, ça porte des caisses. »
- Demande : nettoyer une zone de ses goules, tenir une position, vaincre un boss (en groupe), ouvrir une **salle à
  carte**, rapporter des **plaques d'identité** de soldats morts, des armes militaires précises.
- Donne : hérals, **grades fictifs** dans « l'Escorte » (Recrue → Caporal → Sergent → Lieutenant), titre affiché.

**6. Mila la Contrebandière** — Elle vendait déjà des choses interdites avant la Fièvre. Maintenant, elle vend tout.
- *Accueil* : « Tu as des poches ? Alors on va bien s'entendre. »
- Demande : **objets de valeur à revendre** (la catégorie « À vendre » des composants : montre à gousset, bague en
  argent, diamant, crâne de cristal, couronne, calice...), **livraisons** risquées (un colis à porter à un PNJ ou à un
  point de la carte, qu'on peut se faire voler), récupérer un objet chez un clan rival (sans tricher : dans une zone
  ouverte), faire passer un message.
- Donne : hérals (souvent **plus** que les autres, mais avec un **piège** de temps en temps), rachat des objets de
  valeur au meilleur prix pour les joueurs qui ont sa confiance.

**7. Monsieur Lucky** — « Le seul endroit de la Zone où on meurt de rire et pas de faim. »
- *Accueil* : « Bienvenue, bienvenue ! Un nouveau visage ! J'adore les nouveaux visages, ils perdent toujours. »
- Demande : **découvrir chaque jeu du casino** (roulette, blackjack, crash, mines, plinko, course, bataille navale,
  lumières, pierre-feuille-ciseaux, machines « Portes de l'Olympe », « Souffle du loup », « La Niche »...), faire un
  gros gain, **perdre une mise d'un coup** (avec panache), gagner sur un multiplicateur précis, jouer contre un autre
  joueur.
- Donne : petits hérals, **jetons offerts** (jamais plus que ce qu'il a fait miser), titre « Flambeur », « Chanceux »,
  « Ruiné magnifique ».
- **Règle** : jamais une quête qui oblige à **perdre** beaucoup d'argent ; les mises demandées sont petites et la
  récompense couvre la mise.

**8. Le Vieil Oskar** — Le dernier de la tribu qui priait Ymirak. Il vit seul, loin. Il faut le **trouver** (sa cabane
n'est pas marquée sur la carte : La Voix donne un indice à l'acte II).
- *Accueil* : « Tu as marché longtemps. C'est bien. Les gens qui marchent longtemps écoutent mieux. »
- Demande : énigmes, **lieux sacrés** à visiter la nuit, objets anciens (amulette, idole dorée, statuette de jade),
  reliques des boss (corne de guerre d'Ymirak, bois de Sylvorn), offrandes.
- Donne : le **lore**, les secrets, les meilleures récompenses cosmétiques (titre « Gardien du Nord »).

---

## 4. Les briques de quête

Une quête = une suite d'**étapes**. Chaque étape = **une brique** avec ses réglages. On peut tout combiner : c'est ce
qui permet de « demander n'importe quoi ». Toutes sont vérifiées **par le serveur**.

| Brique | Ce que fait le joueur | Réglages possibles | Mod relié |
|---|---|---|---|
| **PARLER** | parler à un PNJ (ou choisir une réponse) | PNJ, réponse attendue | quêtes |
| **APPORTER** | donner des objets au PNJ (ils sont pris) | **n'importe quel objet** (id), ou un **groupe** (tag : `heraliz_composants:armes`, `#heraliz_accessoires:viseurs`...), quantité, **rareté minimum**, **état minimum** (usure), un nom ou un camo précis | tous |
| **MONTRER** | avoir l'objet sur soi (pas pris) | même chose | tous |
| **ALLER** | atteindre un lieu | point + rayon, ou zone de loot (`prison`, `belcourt`...), de nuit ou de jour | carte |
| **EXPLORER** | visiter N lieux d'une liste | liste de lieux, ordre libre ou imposé | carte |
| **RÉCUPÉRER** | trouver un **objet de quête** posé à un endroit précis (le dossier du directeur) | point posé en jeu par le staff, objet visible **seulement** pour ceux qui ont la quête, une fois par joueur | quêtes |
| **INSPECTER** | clic droit sur un point d'indice : lire une lettre, écouter un enregistrement, regarder une photo | texte / son, point posé en jeu | quêtes |
| **FOUILLER** | ouvrir des conteneurs de loot | nombre, palier (T1 à T7), zone, modèle précis (« 3 frigos ») | HeraliZ Loot |
| **TUER** | tuer des créatures | type (goule, mutant, araignée...), nombre, zone, arme (mêlée, couteau, pistolet, silencieux, grenade) | tous |
| **BOSS** | participer à la mort d'un boss | Ymirak, Mère des Ruines, N-07, Porteur de Peste, Sylvorn | HeraliZ Boss |
| **TENIR** | rester dans une zone | durée, zone, conditions (dans le gaz avec un masque, dans le froid avec une combinaison) | Décors (zones), Armurerie |
| **SURVIVRE** | rester en vie un certain temps | durée de jeu sans mourir | — |
| **EXTRAIRE** | réussir une extraction | n'importe laquelle ou une précise, avec un objet sur soi | Extraction |
| **LIVRER** | porter un colis de A à B | colis (objet de quête qu'on **perd à la mort**, que d'autres peuvent ramasser), délai | quêtes |
| **CASINO** | jouer au casino | jeu, nombre de parties, mise minimum, gain minimum, multiplicateur | Casino |
| **MATCH** | duel, ClashWar, Zombies | gagner un duel, finir un ClashWar, atteindre la manche N aux Zombies, ouvrir le Pack-a-Punch | Matchs |
| **CLAN** | action de clan | capturer / tenir un territoire, lancer un ravitaillement, verser des hérals à la banque | Clans |
| **SALLE** | entrer dans une salle à carte | couleur (verte, bleue, rouge), salle précise | Bunker |
| **RÉANIMER** | relever un joueur à terre | nombre, joueur d'un autre clan | Réanimation |
| **SOIGNER** | utiliser un soin | type (attelle, bandage...), se soigner d'une jambe cassée | Tactique |
| **CONSTRUIRE** | poser des pièces de CRac | type (armoire à outils, fondation, mur), nombre | CRac |
| **TRAIN** | prendre le train | de la gare A à la gare B, sans descendre | Utils (train) |
| **OBSERVER** | regarder un point aux jumelles | point, durée (3 s) | Airstrike (jumelles) |
| **PAYER** | donner des hérals | somme | Économie |
| **CODE** | taper un code dans un terminal ou dans le dialogue | code trouvé ailleurs dans la carte | quêtes |
| **ATTENDRE** | revenir plus tard | durée réelle (« reviens dans 2 h ») | — |
| **CHOIX** | choisir une réponse qui change la suite | branches, réputation gagnée / perdue | quêtes |

Options de chaque étape : **en groupe** (le clan ou les joueurs proches avancent ensemble), **ordre libre** entre
plusieurs étapes, **étape cachée** (on ne sait pas ce qui vient), **marqueur sur la carte** (oui / zone floue / non),
**limite de temps**.

---

## 5. L'histoire principale : « Le Dernier Convoi »

Durée visée : **25 à 40 heures** de jeu au total, à son rythme. Chaque acte se débloque par le précédent **et** par un
minimum de réputation (pour pousser à faire les chaînes des PNJ entre deux actes).

### Prologue — « Réveil » (tutoriel caché, ~30 min)

Le joueur sort de la cinématique, dans l'épave. Rien ne lui dit « tutoriel » : c'est le Refuge qui l'accueille.

| # | Quête | PNJ | Étapes | Ce que ça apprend |
|---|---|---|---|---|
| P1 | **Encore vivant** | Doyen | PARLER au Doyen | les dialogues |
| P2 | **Les premiers soins** | Vasseur | SOIGNER (un bandage donné par elle) | les soins, l'inventaire (E, Curios) |
| P3 | **Les poches des morts** | Doyen | FOUILLER 3 conteneurs T1 près du Refuge... | la fouille, les raretés |
| P4 | **Ce qu'il faut savoir** | Ressac | lire la carte (touche M), poser un point | la carte du monde |
| P5 | **Le grand saut** | Doyen | utiliser la **borne de départ** | quitter le spawn |
| P6 | **Ramène-moi des nouvelles** | La Voix | ALLER au village le plus proche de son point d'arrivée, INSPECTER un poste radio abandonné | explorer |
| P7 | **Le chemin du retour** | Doyen | EXTRAIRE (n'importe quelle zone ouverte, `/extractions`) | l'extraction = revenir au Refuge |

Fin du prologue : le Doyen donne **500 hérals** et présente les autres PNJ. Les contrats du jour s'ouvrent.

### Acte I — « Les vivants » (~5 h)

*But : connaître les 8 PNJ, comprendre que le Refuge ne tiendra pas longtemps sans aide.*

1. **Un Refuge à faire tourner** (Doyen) : rapporter de quoi tenir — 10 objets de nourriture, 4 objets de soin,
   2 planches et du scotch (APPORTER, ordre libre). Le Doyen répartit : chaque objet donné fait monter la réputation
   avec le PNJ qui en avait besoin.
2. **La caisse du Convoi** (Ressac) : ALLER au point où la voiture de queue du Convoi 12 s'est détachée (dans le
   tunnel, à poser en jeu), RÉCUPÉRER la **caisse scellée n° 12-B**, la rapporter (LIVRER, perdue à la mort). Ressac
   la prend et refuse de l'ouvrir devant le joueur. *Premier mystère.*
3. **Les plaques** (Ressac) : retrouver 5 **plaques d'identité** de soldats du Convoi sur des cadavres de soldats posés
   le long de la voie (RÉCUPÉRER × 5, ordre libre, zones floues sur la carte).
4. **Le signal** (La Voix) : un appel au secours en boucle sur 98.1. OBSERVER la tour de `radio` aux jumelles depuis la
   colline, puis y ALLER et INSPECTER l'enregistrement : c'est la voix d'un soldat de l'escorte, « ...la cargaison
   Racine est intacte, je répète, intacte... ». *Deuxième mystère.*
5. **Premier remède** (Vasseur) : rapporter **du sang de goule** (TUER 10 goules avec une arme de mêlée : le sang ne
   se garde que frais) et un **flacon d'alcool** (composant). Elle commence ses recherches.
6. **La loco** (Ferraille) : la locomotive du terminus peut-elle repartir ? Inspection (INSPECTER 3 points de la loco),
   liste de pièces : 2 kits de pièces, 1 batterie, 1 circuit imprimé (APPORTER). *Lance l'événement serveur « Remettre
   la loco en route », section 8.*
7. **Bienvenue chez Lucky** (Lucky) : découvrir le casino, jouer **3 jeux différents** (CASINO), mise de 10 hérals au
   moins. Lucky rembourse les mises.
8. **Ce qui se vend** (Mila) : lui vendre un objet de la catégorie « À vendre » (APPORTER, n'importe lequel). Elle
   paie le double cette fois « pour la première fois ».
9. **Fin de l'acte — Le conseil** (Doyen) : réunion au Refuge (dialogue à choix : à qui le joueur fait-il le plus
   confiance ? Ressac / Vasseur / La Voix → +réputation). Le Doyen avoue : « Le Convoi 12 ne transportait pas que des
   gens. »

### Acte II — « Ce que cachait la Zone » (~8 h)

*But : enquêter. Comprendre le Projet Racine. C'est l'acte des renseignements et des énigmes.*

1. **Le bureau du directeur** (La Voix) — *la quête de l'exemple.*
   - « Il y avait un homme à Clairval, le directeur de l'usine. Il signait les bons de transport du Secteur 7. Son
     bureau est au dernier étage. Rapporte-moi tout ce qui porte le tampon “R”. »
   - ALLER à Clairval → trouver l'usine → RÉCUPÉRER le **dossier « Bons de transport — R »** dans le bureau (objet posé
     par le staff, visible seulement pour ceux qui ont la quête) → INSPECTER le coffre-fort du bureau : il faut un code
     → le code est écrit au dos d'une **photo** dans l'appartement de la secrétaire (autre point de Clairval, pas
     marqué : indice dans le dossier « elle habitait au-dessus de la pharmacie ») → CODE → RÉCUPÉRER la **clé USB** →
     **revenir vivant au Refuge** avec les deux (EXTRAIRE ou marcher jusqu'au Refuge).
   - Si on meurt : le dossier et la clé tombent sur le cadavre (un autre joueur peut les ramasser, mais ils ne lui
     servent à rien s'il n'a pas la quête : il peut les **revendre à Mila**...). Le joueur qui les perd peut retourner
     les chercher (nouvelle copie dans le bureau au bout de 30 min).
2. **Le directeur de la prison** (Ressac) : à la `prison`, le registre des « transferts médicaux » vers le Secteur 7.
   Le registre est dans la **salle à carte bleue** du bloc administratif (SALLE + RÉCUPÉRER). Ressac veut le détruire,
   La Voix veut le lire. **CHOIX** : le donner à l'un ou l'autre (+ / − réputation). Les deux versions donnent la même
   information pour l'histoire (pas de cul-de-sac).
3. **Patient zéro** (Vasseur) : retrouver le dossier médical du premier évadé (hôpital de Belcourt, INSPECTER 3 lits,
   RÉCUPÉRER la fiche). Révélation : il s'appelait « Sujet P-02 ». C'est le **Porteur de Peste**.
4. **Les enregistrements de Kessel** (La Voix) : 6 **bandes audio** cachées sur la carte (silo, raffinerie, aérodrome,
   jetée, faille, une gare). Ordre libre ; La Voix ne donne qu'une **zone floue** pour chacune. Chaque bande écoutée se
   rejoue dans le journal. Ensemble, elles racontent le Projet Racine (textes en annexe à écrire).
5. **Trouver l'ermite** (La Voix → Oskar) : une bande parle d'un « vieil homme du nord qui savait ». La Voix capte une
   prière sur une fréquence morte. Le joueur doit **triangulariser** : ALLER à 3 antennes et noter la force du signal
   (texte dans le dialogue) → en déduire la cabane d'Oskar (aucun marqueur) → PARLER à Oskar. *Débloque toute la
   chaîne d'Oskar.*
6. **La voie secrète** (Ferraille) : sur les plans du Convoi, une **voie de service** qui part sous la `faille`. Trouver
   l'aiguillage (EXPLORER 3 points du réseau du train), le débloquer (APPORTER 3 ferrailles + 1 kit de pièces sur
   place).
7. **Fin de l'acte — La caisse 12-B** (Doyen + Ressac) : avec tous les renseignements, le Doyen force Ressac à ouvrir
   la caisse : **vide**. Le sérum Racine a disparu. Quelqu'un l'a pris pendant le déraillement... *ou quelqu'un l'a
   sur lui depuis le début* (le joueur : flash-back en dialogue, quelques lignes). Le Doyen : « Il faut aller au
   Secteur 7. »

### Acte III — « Les gardiens » (~10 h, en groupe conseillé)

*But : le Secteur 7 est scellé par 4 « verrous biologiques ». Chaque verrou s'ouvre avec un prélèvement sur un des
boss. C'est l'acte des boss et du jeu en groupe.*

- **Quatre quêtes en ordre libre**, chacune donnée par un PNJ différent, chacune avec un avant / un pendant / un après :

| Quête | PNJ | Avant (préparer) | Pendant | Après (rapporter) |
|---|---|---|---|---|
| **Le dieu sous la glace** | Oskar | apprendre le chant (dialogue), rapporter la **corne de guerre** sinon en fabriquer une avec Ferraille, TENIR 2 min dans le froid en combinaison | BOSS Ymirak | le **cœur d'hiver** (objet du boss) → Oskar, qui le « rend » au verrou |
| **La mère** | Vasseur | trouver le **Nid** (EXPLORER les ruines), récupérer un **œuf de couvée** | BOSS Mère des Ruines | **venin des ruines** → Vasseur |
| **Le cerf de la forêt** | Oskar | INSPECTER 3 autels de bois la **nuit** | BOSS Sylvorn | **éclat du masque** de Sylvorn → Oskar |
| **Le porteur** | Vasseur + Ressac | TENIR 3 min dans le **gaz** avec un masque, relever les mesures | BOSS Porteur de Peste | **spores** dans une **cartouche scellée** (perdue à la mort) → Vasseur |

- Chaque boss : la quête compte pour **tous les joueurs du groupe** présents à la mort du boss (BOSS, en groupe).
- Entre deux boss, Kessel « parle » : à chaque verrou ouvert, un nouveau message apparaît sur le terminal du Secteur 7
  (« Vous ne savez pas ce que vous ouvrez. »).
- **Fin de l'acte — Les portes** : les 4 verrous ouverts, la **porte du Secteur 7** s'ouvre (pour le joueur ; voir
  question 5 pour savoir si c'est par joueur ou pour tout le serveur).

### Acte IV — « Racine » (~5 h, final)

1. **La descente** (Ressac) : entrer dans le Secteur 7 par la voie secrète (TRAIN de service ou à pied), 3 **salles à
   carte** (verte, bleue, rouge) à traverser, chaque carte trouvée dans la salle d'avant.
2. **Le laboratoire** (La Voix) : rallumer le courant (APPORTER 2 batteries à 2 points), lire le terminal de Kessel :
   il est mort depuis longtemps, ce qui parlait était un **programme** qui gardait le **Sujet N-07** endormi.
3. **N-07** : la cuve de confinement s'ouvre. Combat contre le Sujet N-07 (lié au boss de Matchs / The Giant **ou** une
   version « carte » : à décider, question 6). Ou fuite : TENIR 90 s puis EXTRAIRE.
4. **Le sérum** : dans le casier du joueur (au nom de son personnage), le **sérum Racine**. C'était lui l'escorte.
5. **Le choix** (au Refuge, devant les PNJ) — **définitif pour le personnage** :
   - **Le donner à Vasseur** → « Le remède » : elle commence à guérir les goules. Titre **« Porteur d'espoir »**,
     contrats de Vasseur mieux payés, un **skin de PNJ** (une goule guérie) apparaît au Refuge pour ce joueur.
   - **Le donner à Ressac** → « L'arme » : l'Escorte devient une armée. Titre **« Officier de l'Escorte »**, contrats
     de Ressac mieux payés, accès à des contrats de combat spéciaux.
   - **Le vendre à Mila** → « Le marché » : beaucoup de hérals tout de suite (gros montant, **[À VALIDER]**). Titre
     **« Vendu »**, Mila devient « associée » (meilleurs prix), Vasseur et Ressac perdent de la réputation.
   - **Le garder / le détruire** (option cachée si réputation maximale avec Oskar) → « Les anciens » : rendu à la
     glace. Titre **« Gardien du Nord »**, la chaîne secrète d'Oskar s'ouvre.
6. **Épilogue** : le Doyen raconte ce que le choix a changé. **Compteur serveur** affiché au Refuge : combien de joueurs
   ont choisi chaque fin (pour que les joueurs en parlent entre eux).

---

## 6. Les chaînes de quêtes de chaque PNJ

Quêtes écrites à la main, en plus de l'histoire. Elles se débloquent par la **réputation** (section 9). Exemples
complets ; les autres sont à écrire de la même façon.

### Docteure Vasseur — « Le serment »
1. **Inventaire de pharmacie** : APPORTER 5 objets de soin différents (n'importe lesquels, état neuf).
2. **Garde de nuit** : RÉANIMER 2 joueurs à terre (n'importe où).
3. **L'attelle** : se soigner une jambe cassée avec « Attelle et bandage » (SOIGNER), puis en rapporter une.
4. **L'air qui tue** : TENIR 60 s dans une zone de **gaz** avec un masque à gaz, puis rapporter le **filtre usé**.
5. **Radioactif** : TENIR 30 s près du `silo` en combinaison Hazmat, RÉCUPÉRER un dosimètre.
6. **L'hôpital de campagne** : retrouver 3 **caisses médicales de l'ONU** (conteneurs T5 de la prison : FOUILLER
   3 « aide humanitaire » / « glacière à vaccins »).
7. **Les cobayes** (après l'acte II) : INSPECTER les cellules de la prison et lire les noms ; CHOIX : les publier
   (La Voix) ou les garder secrets (Ressac).

### Ferraille — « Les mains dans le cambouis »
1. **Petites pièces** : APPORTER 3 ressorts.
2. **Rouillé mais vivant** : APPORTER une arme à **moins de 20 % d'usure** (il la répare, la rend réparée : MONTRER
   puis rendu).
3. **Pneus** : RÉCUPÉRER 4 roues sur des épaves de la carte (points posés en jeu).
4. **Le camion d'Hector** : réparer un véhicule précis abandonné (APPORTER sur place), puis le **conduire** jusqu'au
   Refuge (ALLER en véhicule).
5. **Silencieux maison** : TUER 10 goules avec une arme silencieuse (tester son « prototype »).
6. **La loco, deuxième partie** : suite de l'événement serveur (section 8).
7. **Le grand projet** : rapporter des **plaques** de chaque type (recyclée, soudée, métal, renforcée) pour blinder
   le Refuge. Débloque le titre « Mécano ».

### La Voix — « Fréquences »
1. **Balises** : relever 3 balises météo (ALLER + INSPECTER), zone floue.
2. **Le photographe** : RÉCUPÉRER un **appareil photo argentique** (composant « À vendre »), puis OBSERVER 3 lieux aux
   jumelles (« photos »).
3. **Message chiffré** : un message en code dans le dialogue ; la clé est sur un tableau noir de l'école de Valmont
   (INSPECTER) → CODE.
4. **Les oreilles du clan** : écouter (TENIR 2 min, sans tirer) près d'un territoire tenu par un clan.
5. **La station du nord** : remettre en marche l'antenne de `radio` (APPORTER 1 batterie + 1 circuit imprimé sur place,
   TENIR 3 min pendant qu'elle redémarre : les goules arrivent).
6. **Qui écoute qui ?** (après l'acte III) : trouver le **mouchard** caché au Refuge (INSPECTER : il est sur le poste
   de Ressac...).

### Commandant Ressac — « L'Escorte »
1. **Recrue** : TUER 25 goules.
2. **Secteur Bravo** : nettoyer Marbois (TUER 40 créatures dans la zone `marbois`).
3. **Tenir la gare** : TENIR 5 min une gare du train (en groupe possible).
4. **La salle verte** : ouvrir une salle à carte verte et en rapporter une preuve (SALLE + RÉCUPÉRER).
5. **Discipline** : gagner un **duel** (MATCH).
6. **Exercice** : participer à un **ClashWar** (MATCH).
7. **Opération Racine** : BOSS (n'importe lequel des 5) en groupe. Grade « Sergent ».
8. **Les déserteurs** : retrouver 3 soldats déserteurs (cadavres avec leurs carnets, RÉCUPÉRER) ; le dernier carnet
   accuse... Ressac.

### Mila la Contrebandière — « Tout a un prix »
1. **Brocante** : APPORTER 3 objets « À vendre » différents.
2. **Le colis** : LIVRER un colis au **port**, de nuit, en moins de 30 min (perdu à la mort, ramassable).
3. **Le grand bijou** : APPORTER un objet « À vendre » **Rare ou mieux** (couronne, crâne de cristal, sceptre...).
4. **Faux papiers** : RÉCUPÉRER des tampons officiels à la mairie de Belcourt.
5. **Concurrence** : vendre à Mila **plutôt** qu'à… (dialogue : elle fait monter les enchères contre Lucky).
6. **Le casse** (en groupe) : ouvrir la **salle à carte rouge** de l'aérodrome et en rapporter la caisse (LIVRER,
   un seul porteur, les autres l'escortent).
7. **Le piège** : un contrat trop beau. Si le joueur accepte sans réfléchir, il est envoyé dans une zone de goules ;
   s'il pose les bonnes questions (dialogue), il découvre l'arnaque et gagne plus de réputation.

### Monsieur Lucky — « La maison gagne toujours »
1. **Tour de piste** : jouer à **tous** les jeux du casino au moins une fois (liste cochée dans le journal).
2. **Le flambeur** : gagner un multiplicateur ×5 ou plus, une fois.
3. **Le stoïque** : perdre 5 parties d'affilée sans quitter la table (petite mise). Lucky adore.
4. **Le duel de cartes** : battre un autre joueur à pierre-feuille-ciseaux ou bataille navale.
5. **Le jackpot** : être à la machine quand le jackpot tombe (ou s'en approcher : gain ≥ 10×).
6. **La dette** : un joueur (PNJ fictif) doit de l'argent à Lucky : retrouver son cadavre et sa **montre à gousset**.
7. **All-in** : miser **toute** une petite somme (100 hérals max) d'un coup : gagné ou perdu, Lucky paie le panache.

### Le Vieil Oskar — « Les anciens »
1. **L'écoute** : passer 5 minutes assis près de sa cabane sans rien faire (TENIR, aucune action).
2. **Les pierres qui parlent** : trouver 4 pierres gravées dans l'`arctique` (INSPECTER), recopier les symboles
   (CODE dans le dialogue).
3. **L'offrande** : APPORTER une **amulette** et une **idole dorée**.
4. **La nuit la plus froide** : TENIR une nuit complète (en jeu) dans l'arctique sans mourir.
5. **Le chant d'Ymirak** (acte III).
6. **La forêt qui regarde** (acte III, Sylvorn).
7. **Le dernier de la tribu** (secret, fin « Les anciens ») : rendre le sérum à la glace, au fond de la `faille`.

---

## 7. Les contrats du jour et de la semaine

C'est le cœur du « on ne s'ennuie jamais ». Chaque PNJ propose **3 contrats par jour** (remis à zéro à 6 h du matin),
fabriqués **au hasard** à partir de **modèles** remplis avec des **listes**. Le joueur en accepte autant qu'il veut,
mais **3 actifs au plus par PNJ**.

### Comment ça marche

Un modèle = un texte + des trous + des listes où tirer. Exemple :

```
Modèle « ferraille_pieces »
  Texte : « J'ai besoin de {n} {objet}. Pas pour demain : pour tout à l'heure. »
  {objet} = au hasard dans le groupe heraliz_composants:armes
  {n}     = 2 à 4 (plus avec la réputation)
  Récompense : 40 hérals par pièce, +15 réputation Ferraille
```

```
Modèle « voix_releve »
  Texte : « Il y a un relais à {lieu}. Va voir s'il émet encore. »
  {lieu}  = au hasard parmi les points « relais » posés en jeu par le staff
  Étapes  : ALLER {lieu} → INSPECTER le relais → revenir vivant
  Récompense : 150 hérals (+50 si le lieu est T4 ou plus), +20 réputation La Voix
```

### Les modèles de départ (au moins 6 par PNJ, à enrichir)

| PNJ | Modèles |
|---|---|
| **Vasseur** | rapporter {n} soins de {catégorie} · rapporter un objet de soin **neuf** {rareté} · réanimer {n} joueurs · tuer {n} goules en mêlée (sang) · tenir {t} dans le gaz de {zone} · fouiller {n} conteneurs médicaux (pharmacie, armoire à pharmacie...) |
| **Ferraille** | {n} {composant} · une arme {famille} quelle que soit son usure · {n} ferrailles · réparer {objet} sur place à {lieu} · tuer {n} avec {famille d'arme} · ramener un véhicule au Refuge |
| **La Voix** | relever le relais de {lieu} · inspecter {n} postes radio · observer {lieu} aux jumelles · retrouver le carnet d'un explorateur à {zone floue} · trouver le code du jour (énigme tirée d'une liste) · rapporter une radio cassée / un téléphone cassé |
| **Ressac** | tuer {n} {créature} à {ville} · tenir {lieu} {t} min · ouvrir une salle à carte {couleur} · s'extraire par {extraction} · rapporter {n} plaques d'identité · gagner {n} duels |
| **Mila** | rapporter {objet de valeur} · livrer un colis à {PNJ ou lieu} en {t} min · vendre {n} objets « À vendre » · rapporter {n} {objet de n'importe quel mod} (liste « marché du jour », section suivante) · ramener le colis tombé d'un autre joueur |
| **Jack** | jouer {n} parties de {jeu} · gagner ×{m} à {jeu} · jouer contre un joueur · miser au total {s} hérals (petit) · faire 3 jeux différents dans la soirée |
| **Oskar** | (peu de contrats, mais rares et bien payés) visiter {lieu sacré} la nuit · rapporter {objet ancien} · tenir {t} dans le froid · tuer {n} créatures sans arme à feu |
| **Doyen** | contrats « du Refuge » : nourriture, planches, hérals pour le Refuge (alimente les événements serveur) |

### « Le marché du jour » : demander N'IMPORTE QUEL objet

Chaque jour, Mila (et Ferraille pour les pièces) affiche **5 objets recherchés** tirés dans une **grande liste** qu'on
remplit librement (n'importe quel id d'objet de n'importe quel mod : armes TaCZ, accessoires, objets cfm, nourriture,
composants, sacs, armures, même des blocs). Chaque objet de la liste a un poids (chance d'être tiré), un prix et une
quantité. Exemple :

```
tacz:modern_kinetic_gun avec l'arme "ak47"  → 1, 600 hérals, rare
heraliz_composants:montre_gousset          → 1, 220 hérals
minecraft:golden_apple                     → 2, 120 hérals
backpacked:backpack                        → 1, 300 hérals
heraliz_armory:<masque à gaz>              → 1, 250 hérals, état ≥ 50 %
```

Le staff peut aussi **ajouter un contrat à la main en jeu** (commande, section 12) : « Ferraille veut 1 lance-roquettes
avant dimanche, 2 000 hérals au premier qui l'apporte » (**contrat unique**, premier arrivé).

### Les primes de la semaine

3 par semaine (lundi 6 h), plus dures, **en groupe** conseillé, bien payées :
- « **Grand ménage** » : tuer 500 goules dans une ville tirée au hasard (compteur commun du groupe) ;
- « **Le convoi fantôme** » : livrer 3 colis à 3 PNJ différents sans mourir ;
- « **Chasse au boss** » : vaincre le boss de la semaine (tiré au hasard) ;
- « **Le rat de bibliothèque** » : retrouver 10 documents perdus dans 10 lieux (ordre libre) ;
- « **Le maître du jeu** » : finir 1er d'un ClashWar, atteindre la manche 20 aux Zombies...

### Pour que ça ne se répète pas

- Jamais deux fois le même modèle chez le même PNJ le même jour ; un lieu tiré ne revient pas avant 3 jours.
- Les modèles suivent la **réputation** : plus on est connu, plus les contrats sont durs **et** mieux payés.
- Certains contrats n'apparaissent **que la nuit**, **que sous la pluie**, ou **que si un événement est en cours**.
- **Contrats « en chaîne »** : 1 fois sur 10, un contrat réussi en ouvre un deuxième (« Le relais émettait... un
  message. Va voir d'où il vient. »).

---

## 8. Quêtes de clan, événements du serveur, secrets

### Quêtes de clan
Données par **Ressac** (militaires) et **Mila** (commerce) au chef du clan ; tout le clan avance ensemble ; récompense
à la **banque du clan** + **XP de clan** (classement).
- Tenir **2 territoires** en même temps pendant 1 h.
- Lancer **3 ravitaillements** dans la semaine.
- Livrer en convoi **10 colis** (plusieurs membres) au Refuge.
- Vaincre **2 boss** dans la semaine.
- Verser **5 000 hérals** au « fonds du Refuge » (alimente un événement).

### Événements du serveur (tous les joueurs ensemble)
Une **barre commune** au Refuge (et dans le journal) : tout le monde contribue, tout le monde gagne.
1. **« Remettre la loco en route »** (Ferraille) : 2 000 kits de pièces, 1 500 ressorts, 500 batteries... rapportés par
   tous. À 100 % : cinématique de la loco qui démarre, **titre « Cheminot »** pour tous les participants (au moins
   10 objets donnés), et un **nouveau trajet** du train (à voir avec le réseau du train).
2. **« La grande antenne »** (La Voix) : rallumer l'antenne de `radio`. À 100 % : La Voix annonce en direct les
   largages et les extractions ouvertes (message aux joueurs).
3. **« La Saison de la Fièvre »** (Vasseur) : semaine de goules plus nombreuses ; les contrats de Vasseur paient double.
4. **« Le grand tournoi de Lucky »** : un week-end de défis casino, classement, titre au premier.
5. **« Le choix de la Zone »** : quand 100 joueurs ont fini l'acte IV, la fin la plus choisie change le Refuge
   (décor, réplique des PNJ). **[À VALIDER]**

### Secrets (aucune quête ne les annonce)
- **Le carnet de Kessel** : 12 pages cachées sur toute la carte, sans marqueur. Les 12 = titre « Archiviste » et la
  vraie fin de l'histoire de Kessel.
- **La tombe du chef de gare** : un nom gravé dans le tunnel, celui du Doyen… (il est mort dans le déraillement ?
  Fantôme ? Le dialogue change si on lui en parle).
- **Le 13e jeton** : une machine du casino a un message caché quand on y gagne exactement 1 313 hérals.
- **Le train de minuit** : à minuit (heure du jeu), un passager PNJ apparaît dans une voiture et donne une énigme.
- **Les dessins d'enfant** : 7 dessins dans 7 maisons ; ensemble, ils montrent la carte du Secteur 7.

---

## 9. Réputation, récompenses et équilibre

### Réputation (une par PNJ)

| Niveau | Points | Ce que ça ouvre |
|---|---|---|
| **Inconnu** | 0 | contrats faciles |
| **Toléré** | 100 | 1re partie de sa chaîne |
| **Allié** | 400 | contrats normaux, meilleurs prix (Mila) |
| **De confiance** | 1 000 | contrats difficiles, suite de la chaîne, secrets |
| **Frère d'armes** | 2 500 | fin de chaîne, titre du PNJ, réplique spéciale à chaque visite |

- Certains choix font **perdre** de la réputation avec un PNJ (Ressac contre La Voix, Mila contre Vasseur).
- La réputation **ne se perd pas** à la mort, ni au changement de carte (gardée hors du monde, comme les clans).

### Ordres de grandeur des hérals **[À VALIDER]**

Repères du serveur : créer un clan = 2 500 hérals, une zone tenue = 100 hérals/h, entrée du casino = 100 hérals.

| Quoi | Hérals |
|---|---|
| Contrat du jour facile / moyen / dur | 80–150 / 150–300 / 300–600 |
| Quête de chaîne d'un PNJ | 200–1 000 |
| Fin d'un acte | 1 000–2 500 |
| Prime de la semaine | 1 000–3 000 (partagés dans le groupe) |
| Choix « Le marché » (vendre le sérum à Mila) | 10 000 (une fois par personnage) |

Plafond conseillé : **un joueur actif gagne environ 1 500 à 2 500 hérals par jour avec les quêtes** (à régler après
les premiers jours, comme les armes).

### Autres récompenses (sans pay2win, sans toucher au loot ni au craft)
- **Titres** (affichés à côté du pseudo, après le grade et le tag de clan ; un seul choisi à la fois).
- **Cosmétiques** : camos de quête (pas les camos du Pack-a-Punch, pas ceux de la boutique), emotes, skins pour les
  PNJ du Refuge vus par le joueur.
- **Bonus de loot** temporaire (l'objet « Bonus de loot » existe déjà ; plafond 25 % inchangé).
- **Réductions** et **meilleurs prix de revente** chez Mila.
- **Accès** : la chaîne d'Oskar, la voie secrète, certaines salles.
- **Objets** (armes, armures, soins) : **pas maintenant**. À décider avec la refonte du loot et du craft.

---

## 10. Comment le joueur le vit

- **Parler à un PNJ** : clic droit → **écran de dialogue** au style des clans (panneaux de verre, police DM Sans) :
  à gauche le portrait 3D du PNJ qui bouge (gestes), à droite son texte qui s'écrit, en dessous **les réponses** du
  joueur (2 à 4 choix). En bas : « Quêtes », « Contrats », « Donner », « Au revoir ».
- **Donner des objets** : bouton « Donner » → une case où poser les objets ; le PNJ dit tout de suite ce qui compte
  et ce qui ne compte pas (« Un ressort ? Il est tordu. Il me le faut droit. » si l'état est trop bas).
- **Journal de quêtes** (touche **J**, à confirmer avec la table des touches) : onglets **En cours / Disponibles /
  Contrats / Terminées / Archives** (documents lus, bandes audio à réécouter, carnet de Kessel), réputation de chaque
  PNJ, titres.
- **Suivi à l'écran** : en haut à droite, petit, la quête suivie et son étape (« Clairval — Bureau du directeur
  (0/1) »), masquable.
- **Carte du monde (M)** : nouveau calque à cocher **« Quêtes »** : points exacts, **zones floues** (cercle) pour les
  enquêtes, rien du tout pour les secrets.
- **Objets de quête** : nom en **doré**, infobulle « Objet de quête — Le bureau du directeur », ne se vendent pas aux
  autres PNJ (sauf Mila, qui rachète tout pour presque rien).
- **Notifications** : petites, en haut à droite (comme les défis de duel) : « Quête terminée », « Réputation :
  Ferraille — Allié ».
- **3 langues** : tous les textes de quêtes, dialogues et PNJ en français, anglais et russe (règle du serveur).
- **Voix des PNJ** : texte seulement au début ; si les propriétaires fournissent des voix, on les branchera
  (question 3).

---

## 11. Règles contre la triche et l'abus

- **Tout est décidé par le serveur** (le jeu du joueur ne fait qu'afficher) : progression, objets pris, récompenses,
  tirage des contrats. Rien de la logique dans le jar `-client` (méthode du serveur).
- **Objets donnés = vraiment pris** (déplacés, jamais copiés ; `spartan_uid` respecté).
- **Objets de quête** : un par joueur et par étape ; marqués au nom du joueur ; inutiles à un autre joueur (sauf pour
  les vendre à Mila).
- **Pas de farm entre amis** : RÉANIMER / duels / casino entre les mêmes joueurs comptent une fois par jour par paire ;
  les quêtes « tuer des joueurs » n'existent pas (on évite d'encourager le kill de spawn).
- **Pas de quête dans le monde du spawn** sauf au Refuge lui-même (parler, donner).
- **Casino** : jamais de quête qui oblige à perdre beaucoup ; les mises demandées sont petites et remboursées.
- **Limites** : 3 contrats actifs par PNJ, plafonds de hérals par jour (réglables).
- **Journal du serveur** : chaque récompense est notée (`heraliz_quetes/journal.log`), comme les grades.

---

## 12. Ce qu'il faudra construire (aperçu pour la phase code)

*Pour plus tard, après validation du scénario. Rien n'est fait.*

- **Nouveau mod** `mods/heraliz-quetes` (modId `heraliz_quetes`), coupé en commun / serveur / client comme les autres.
- **PNJ** : une entité « PNJ » (modèle de joueur, skin, gestes par Player Animator s'il est là), posée par le staff :
  `/pnj creer <id>`, `/pnj deplacer`, `/pnj skin`, `/pnj retirer`.
- **Points de quête** posés **en jeu** (le bureau, le coffre-fort, la balise) : `/quete point ajouter <id>` à l'endroit
  du staff, gardés avec le monde (comme les lieux de la carte). Jamais de retouche des fichiers du monde.
- **Fichiers de réglage** (`serveur/config/heraliz_quetes/`) : `pnj/*.json` (fiches et dialogues), `histoire/*.json`
  (prologue et actes), `chaines/*.json`, `contrats/*.json` (modèles et listes), `marche.json` (le marché du jour),
  `evenements.json`. Relus sans redémarrer (`/quete recharger`).
- **Progression des joueurs** gardée **hors du monde** (`heraliz_quetes/joueurs/`), elle survit aux changements de
  carte.
- **Commandes du staff** : `/quete donner|finir|reset <joueur> <quete>`, `/quete voir <joueur>`, `/contrat ajouter ...`
  (contrat à la main), `/evenement ...`.
- **Branchements** aux autres mods par leurs API (section 5 de CLAUDE.md) : Économie (payer), Loot (conteneur fouillé),
  Extraction, Casino, Matchs, Clans, Bunker (salles), Boss, Réanimation, Tactique (soins), CRac, Utils (train, carte du
  monde : calque « Quêtes »), Airstrike (jumelles). Les mods qui ne donnent pas encore l'information (ex. « partie de
  casino jouée ») recevront un petit événement à écouter.
- **Écrans** (rendus à valider d'abord, règle 9) : dialogue, journal, case « Donner », suivi à l'écran.
- **Ordre conseillé** : 1) moteur + PNJ + PARLER / APPORTER / ALLER / RÉCUPÉRER + journal + prologue ; 2) contrats
  du jour ; 3) les autres briques et l'acte I ; 4) actes II à IV, événements, secrets.

---

## 13. Questions pour les propriétaires

1. **Objets de quête à la mort** : on les **perd** sur le cadavre (risque façon DayZ, d'autres peuvent les voler) ou
   ils sont **protégés** ? *(Proposé : perdus, avec une nouvelle copie au point de départ au bout de 30 min.)*
2. **Récompenses** : seulement hérals + réputation + titres + cosmétiques pour l'instant (proposé), ou aussi de
   l'équipement ?
3. **Voix des PNJ** : texte seulement, ou vous avez / voulez des voix enregistrées ?
4. **Noms et lieux** : les noms des PNJ, des villes (Clairval, Belcourt, Marbois, Valmont...) et l'histoire vous
   vont ? Il y a des villes de la nouvelle carte dont vous voulez le vrai nom dans les quêtes ?
5. **Le Secteur 7** : la porte s'ouvre **pour chaque joueur** (chacun son histoire) ou **une fois pour tout le serveur**
   (grand événement) ?
6. **Le Sujet N-07 à l'acte IV** : on réutilise le boss des Zombies (The Giant) dans un « souvenir », ou on fait une
   salle sur la carte ?
7. **Les fins** : le choix final est **définitif** pour le personnage (proposé), ou on peut le refaire ?
8. **Combien de PNJ au début** : les 8 d'un coup, ou d'abord 3 (Doyen, Ferraille, La Voix) pour essayer ?
