<?php

/*
 * Pages légales du site HeraliZ (créées par reglages.php ; une page encore jamais modifiée dans l'administration est
 * remplacée quand VERSION_PAGES change, sinon l'administration → Pages fait foi). Les passages entre crochets [ ... ]
 * sont à compléter par le propriétaire dans l'administration (identité de l'éditeur, hébergeurs) : jamais dans le
 * dépôt. Modèles de départ à faire relire par un professionnel du droit.
 *
 * Modèle de la boutique (22/10) : le site ne vend QUE des Points Boutique (PB, 100 PB = 1 €) ; tout le reste (grades,
 * clés, cosmétiques) s'achète en jeu avec des PB (/boutique). Les PB s'échangent entre joueurs au spawn (/trade), taxe
 * de 10 % détruite.
 */

const VERSION_PAGES = '2026-10-23d';
const REPERE_PAGES = '<!-- heraliz-pages:';

function pagesLegales(): array
{
    $maj = REPERE_PAGES.VERSION_PAGES.' --><p><em>Dernière mise à jour : 23 octobre 2026.</em></p>';
    $editeur = 'la société HeraliZ (personne morale en cours de création)';
    // adresse postale et e-mail : secrets GitHub SITE_ADRESSE_LEGALE et SITE_MAIL_CONTACT, posés à l'envoi (jamais dans le dépôt)
    $adresse = '__ADRESSE_LEGALE__';
    $mail = '__MAIL_CONTACT__';

    $cgu = $maj
        .'<h2>1. Objet</h2><p>Les présentes conditions générales d’utilisation (CGU) encadrent l’accès au site heraliz.fr (« le Site »), au launcher HeraliZ (« le Launcher ») et au serveur Minecraft HeraliZ (« le Serveur »), ensemble « les Services ». Créer un compte, lancer le Launcher ou se connecter au Serveur vaut acceptation pleine et entière des CGU, des Conditions de vente et du Règlement du Serveur.</p>'
        .'<h2>2. Éditeur</h2><p>Les Services sont édités par '.$editeur.' (voir les Mentions légales), joignable à '.$mail.' et sur le Discord officiel (discord.gg/heraliz). HeraliZ n’est ni affilié à, ni approuvé par Mojang Studios ou Microsoft. Minecraft est une marque de Mojang Studios.</p>'
        .'<h2>3. Compte</h2><p>Un compte du Site est obligatoire pour jouer. Il est strictement personnel : son titulaire est responsable de tout ce qui est fait avec, de la confidentialité de son mot de passe et de ses codes de double authentification. Le partage, le prêt, la vente ou l’achat de comptes sont interdits. Les informations données doivent être exactes. Un mineur doit avoir l’accord de son représentant légal.</p>'
        .'<h2>4. Comportement</h2><p>Sont notamment interdits : la triche sous toute forme (client modifié, macros, logiciels tiers, exploitation de bugs), la duplication d’objets, les doubles comptes pour contourner une sanction, le harcèlement, les propos haineux, discriminatoires ou à caractère sexuel, l’usurpation d’identité (y compris du staff), la publicité pour d’autres serveurs, les escroqueries entre joueurs et les attaques contre les Services. Tout bug doit être signalé au staff, jamais exploité.</p>'
        .'<h2>5. Échanges entre joueurs et argent réel</h2><p>Les échanges d’objets et de Points Boutique (PB) entre joueurs se font uniquement en jeu, par le système d’échange du Serveur (/trade au spawn), aux risques des joueurs. Il est <strong>interdit de vendre ou d’acheter contre de l’argent réel</strong>, en dehors de la boutique officielle, des objets, des PB, des hérals, des comptes ou des services liés au Serveur. Le staff ne rembourse pas un échange regretté ; une escroquerie prouvée est sanctionnée.</p>'
        .'<h2>6. Anticheat</h2><p>Le Launcher et le jeu intègrent un anticheat (Spartan) indispensable à l’équité. En jouant, l’utilisateur accepte les contrôles décrits dans la page Confidentialité (empreintes des fichiers du jeu, identifiant matériel chiffré, contrôles d’intégrité, captures d’écran du jeu avec son accord). Refuser ces contrôles empêche de jouer. Contourner, désactiver ou étudier l’anticheat pour le tromper est sanctionné comme de la triche.</p>'
        .'<h2>7. Sanctions</h2><p>Le staff peut, selon la gravité et sans préavis : avertir, rendre muet, exclure, bannir temporairement ou définitivement un compte, son adresse IP et son ordinateur, retirer des objets, des hérals ou des PB obtenus de façon irrégulière, et annuler des échanges. Une sanction ne donne lieu à <strong>aucun remboursement ni compensation</strong>, y compris pour les PB et contenus achetés. Une contestation se fait calmement sur le Discord (ticket).</p>'
        .'<h2>8. Contenus du jeu</h2><p>Les objets, hérals, PB, grades, bases et progressions n’ont aucune valeur monétaire et restent la propriété d’HeraliZ, qui en concède un droit d’usage personnel sur le Serveur. HeraliZ peut à tout moment les modifier, équilibrer, réinitialiser (nouvelle saison, nouvelle carte) ou retirer.</p>'
        .'<h2>9. Disponibilité</h2><p>Les Services sont fournis « en l’état », notamment pendant la bêta, sans garantie de disponibilité permanente. Maintenances, pannes, pertes de données et mises à jour ne donnent droit à aucune indemnité.</p>'
        .'<h2>10. Propriété intellectuelle</h2><p>Les créations d’HeraliZ (mods, modèles, textures, sons, textes, logos, Launcher, site) sont protégées. Il est interdit de les copier, extraire, décompiler, modifier ou redistribuer sans autorisation écrite. Les contenus des autres auteurs restent les leurs, sous leurs licences.</p>'
        .'<h2>11. Responsabilité</h2><p>HeraliZ n’est pas responsable des dommages indirects, de la perte d’accès due à l’utilisateur (mot de passe, partage, ordinateur infecté) ni des échanges entre joueurs.</p>'
        .'<h2>12. Modifications et droit applicable</h2><p>Les CGU peuvent être modifiées ; la version en vigueur est celle publiée sur le Site. Elles sont soumises au droit français ; une solution amiable est recherchée avant toute action.</p>';

    $cgv = $maj
        .'<h2>1. Objet</h2><p>Les présentes conditions générales de vente (CGV) s’appliquent à tout achat fait sur la boutique du Site. Tout achat vaut acceptation des CGV, des CGU et du Règlement du Serveur.</p>'
        .'<h2>2. Vendeur</h2><p>'.$editeur.', joignable à '.$mail.' et sur le Discord officiel (discord.gg/heraliz).</p>'
        .'<h2>3. Produit vendu : les Points Boutique</h2><p>Le Site vend <strong>uniquement des Points Boutique (« PB »)</strong>, une monnaie virtuelle du Serveur (100 PB = 1 €, avec des points offerts sur certains lots). Les PB servent, <strong>en jeu</strong>, dans la boutique du Serveur (/boutique) : grades, clés de caisses cosmétiques et autres contenus virtuels, au prix affiché en jeu au moment de l’achat. Les PB et ce qu’ils achètent sont des <strong>contenus numériques sans support matériel</strong>, sans aucune valeur monétaire : ni remboursables, ni convertibles en argent, ni utilisables hors du Serveur. Les grades et cosmétiques ne donnent jamais d’avantage en combat.</p>'
        .'<h2>4. Prix et paiement</h2><p>Les prix des lots de PB sont en euros toutes taxes comprises. Le paiement, exigible immédiatement, est traité par le prestataire de paiement choisi (HeraliZ ne voit jamais les données bancaires). <strong>Aucun abonnement, aucun prélèvement automatique.</strong></p>'
        .'<h2>5. Livraison</h2><p>Les PB sont crédités automatiquement en jeu sur le compte du Site utilisé pour l’achat, en principe dans les minutes qui suivent (être connecté au Serveur). Un défaut de livraison se signale au staff sur Discord dans les 7 jours, avec la preuve d’achat.</p>'
        .'<h2>6. Achats en jeu avec des PB</h2><p>Les grades achetés en jeu durent le temps affiché (par exemple 30 ou 90 jours), puis sont retirés <strong>sans renouvellement automatique</strong> ; racheter le même grade ajoute la durée ; passer à un grade supérieur se paie au prix plein. Un grade « à vie » vaut pour toute la durée d’exploitation du Serveur. Un achat en jeu est définitif.</p>'
        .'<h2>7. Échanges et taxe</h2><p>Les PB peuvent être donnés ou échangés entre joueurs uniquement par le système d’échange du Serveur (/trade, au spawn). <strong>Une taxe de 10 % est prélevée et détruite</strong> sur chaque envoi de PB (par exemple : 1 000 PB envoyés, 900 PB reçus). Un échange validé des deux côtés est définitif ; HeraliZ ne rembourse pas un échange regretté. La revente de PB contre de l’argent réel est interdite.</p>'
        .'<h2>8. Pas de rétractation ni de remboursement</h2><p>Conformément à l’article L221-28 13° du Code de la consommation, le droit de rétractation ne s’applique pas à un contenu numérique fourni immédiatement avec l’accord exprès du consommateur. <strong>En validant sa commande, l’acheteur demande la livraison immédiate des PB et renonce expressément à son droit de rétractation.</strong> <strong>Aucun achat n’est remboursable</strong>, en tout ou partie, quel qu’en soit le motif (changement d’avis, PB non dépensés, grade expiré, perte en jeu, échange, réinitialisation, nouvelle carte, évolution du contenu, interruption ou fermeture du Serveur).</p>'
        .'<h2>9. Sanctions</h2><p>Payer ne donne aucune immunité. Un joueur sanctionné (exclusion, bannissement) perd, pour la durée de la sanction ou définitivement, l’usage de ses PB et contenus, <strong>sans remboursement</strong>. Les PB obtenus de façon irrégulière (triche, bug, escroquerie) sont retirés.</p>'
        .'<h2>10. Contestation de paiement et fraude</h2><p>Toute contestation de paiement abusive (« chargeback ») ou tout paiement frauduleux entraîne le retrait des PB et contenus concernés (y compris ceux déjà échangés ou dépensés) et le bannissement définitif des comptes liés, sans préjudice de poursuites.</p>'
        .'<h2>11. Mineurs</h2><p>Un mineur doit avoir l’autorisation de son représentant légal avant tout achat ; l’achat est réputé fait avec cet accord.</p>'
        .'<h2>12. Créations sur mesure</h2><p>Emotes, skins et finishers personnalisés (joueur ou clan) sont faits sur devis après échange sur Discord, dans les limites techniques de Minecraft. Le rendu peut différer de l’exemple. Une fois le travail commencé, aucun remboursement. Les créations restent la propriété d’HeraliZ, qui en concède l’usage sur le Serveur.</p>'
        .'<h2>13. Responsabilité, modifications, litiges</h2><p>La responsabilité d’HeraliZ est limitée au montant payé pour la commande concernée. Les CGV applicables sont celles en vigueur au jour de l’achat. Droit français ; solution amiable recherchée d’abord.</p>';

    $mentions = $maj
        .'<h2>Éditeur</h2><p>HeraliZ, société en cours de création (personne morale en formation, forme juridique et capital précisés à l’immatriculation)<br>Siège : '.$adresse.'<br>SIREN / RCS : en cours d’immatriculation<br>Contact : '.$mail.' · Discord : discord.gg/heraliz<br>Directeur de la publication : le représentant légal de la société HeraliZ.</p>'
        .'<h2>Hébergement</h2><p>Le site et le serveur de jeu sont hébergés par HeraliZ elle-même, sur ses propres serveurs (siège : '.$adresse.' ; contact : '.$mail.').</p>'
        .'<h2>Propriété intellectuelle</h2><p>Textes, images, logos, modèles et créations d’HeraliZ sont protégés ; toute reproduction sans autorisation est interdite. Les contenus des autres auteurs restent les leurs. HeraliZ n’est ni affilié à, ni approuvé par Mojang Studios ou Microsoft. Site propulsé par Azuriom.</p>';

    $confidentialite = $maj
        .'<h2>1. Responsable du traitement</h2><p>'.$editeur.', contact : '.$mail.'.</p>'
        .'<h2>2. Données collectées</h2><ul>'
        .'<li><strong>Compte</strong> : pseudo, adresse e-mail, mot de passe (chiffré, jamais lisible), double authentification si activée, date d’inscription.</li>'
        .'<li><strong>Connexion</strong> : adresse IP, dates de connexion au Site, au Launcher et au Serveur, version du Launcher.</li>'
        .'<li><strong>Jeu</strong> : pseudo et identifiant de joueur, inventaires, progression, messages du chat et commandes (modération), sanctions.</li>'
        .'<li><strong>Boutique</strong> : historique des achats, PB reçus, dépensés et échangés ; les données bancaires sont traitées par le prestataire de paiement, jamais par HeraliZ.</li>'
        .'<li><strong>Discord</strong> (si le joueur lie son compte avec /lier) : identifiant Discord.</li>'
        .'<li><strong>Anticheat</strong> (voir point 3).</li></ul>'
        .'<h2>3. Anticheat (Spartan)</h2><p>Pour lutter contre la triche, le jeu et le Launcher relèvent, <strong>uniquement pendant que le jeu HeraliZ tourne</strong> :</p><ul>'
        .'<li>les empreintes (sommes de contrôle) des fichiers du jeu et des mods chargés, la liste des modules de code et des bibliothèques chargés par le jeu, et des contrôles d’intégrité réguliers ;</li>'
        .'<li>un identifiant de l’ordinateur calculé à partir de caractéristiques du matériel puis <strong>chiffré</strong> (il sert à appliquer un bannissement d’ordinateur, jamais à identifier la personne) ;</li>'
        .'<li>les touches réservées aux menus de triche quand elles sont pressées en jeu (pas le reste du clavier) ;</li>'
        .'<li>des <strong>captures d’écran de la fenêtre du jeu seulement</strong>, si et seulement si le joueur l’a accepté dans le Launcher (refus = pas de partie) : lors des contrôles de l’anticheat ou à la demande du staff ; rien n’est enregistré sur l’ordinateur du joueur. Les captures sont conservées 3 jours, puis supprimées définitivement ; seuls les fondateurs et les administrateurs du serveur y ont accès. Le joueur peut à tout moment demander à un fondateur l’accès aux captures disponibles le concernant.</li></ul>'
        .'<p>Aucun autre fichier personnel, aucun mot de passe, aucun historique de navigation n’est lu. Les alertes et preuves sont visibles du seul staff (salon privé Discord).</p>'
        .'<h2>4. Finalités et bases légales</h2><p>Gestion du compte et des achats (exécution du contrat) ; sécurité, anticheat, modération et lutte contre les doubles comptes (intérêt légitime) ; captures d’écran (consentement, retirable en ne jouant plus) ; comptabilité (obligation légale).</p>'
        .'<h2>5. Durées de conservation</h2><p>Compte : jusqu’à sa suppression, puis 1 an. Journaux de connexion : 1 an. Sanctions, données et captures de l’anticheat : jusqu’à 3 ans (5 ans pour un bannissement définitif). Achats et factures : 10 ans.</p>'
        .'<h2>6. Destinataires</h2><p>Le staff d’HeraliZ selon ses fonctions, les hébergeurs du Site et du Serveur, le prestataire de paiement, Discord (alertes du staff) et, le cas échéant, le service d’envoi d’e-mails. Certains prestataires peuvent être hors de l’Union européenne, avec des garanties appropriées. Aucune donnée n’est vendue.</p>'
        .'<h2>7. Sécurité</h2><p>Mots de passe chiffrés, connexion HTTPS, accès limités, limitation des tentatives de connexion, double authentification proposée.</p>'
        .'<h2>8. Vos droits</h2><p>Accès, rectification, effacement, opposition, limitation, portabilité et retrait du consentement : écrire à '.$mail.' (une preuve d’identité peut être demandée). Réclamation possible auprès de la CNIL (cnil.fr).</p>';

    $cookies = $maj
        .'<h2>Cookies utilisés</h2><p>Le Site n’utilise <strong>que des cookies nécessaires</strong> à son fonctionnement : session de connexion, protection des formulaires (CSRF), mémorisation de la connexion si demandée, et éventuellement le captcha anti-robots. Ils sont exemptés de consentement.</p>'
        .'<h2>Pas de pistage</h2><p>Aucun cookie publicitaire ni de mesure d’audience tierce. Les sites de vote et le prestataire de paiement, ouverts depuis le Site, ont leurs propres règles.</p>'
        .'<h2>Gérer les cookies</h2><p>Le navigateur permet de les supprimer ou de les bloquer ; sans eux, la connexion au Site ne fonctionne plus.</p>';

    $surMesure = '<p>Tu veux une <strong>emote</strong>, un <strong>skin d’arme</strong> ou un <strong>finisher</strong> rien qu’à toi, ou pour <strong>tout ton clan</strong> ?</p>'
        .'<ol><li>Envoie un message privé au staff sur Discord (discord.gg/heraliz).</li><li>Joins un exemple : image ou vidéo du rendu voulu.</li><li>Le staff te répond avec un devis, dans les limites de ce qui est possible sur Minecraft.</li></ol>'
        .'<p>Voir les Conditions de vente, article 12.</p>';

    return [
        'cgu' => ['Conditions d’utilisation', 'Règles d’utilisation du site, du launcher et du serveur HeraliZ.', $cgu],
        'cgv' => ['Conditions de vente', 'Conditions de vente des Points Boutique HeraliZ.', $cgv],
        'mentions-legales' => ['Mentions légales', 'Éditeur et hébergeurs du site HeraliZ.', $mentions],
        'confidentialite' => ['Confidentialité', 'Données personnelles et anticheat sur HeraliZ.', $confidentialite],
        'cookies' => ['Cookies', 'Cookies utilisés par le site HeraliZ.', $cookies],
        'sur-mesure' => ['Créations sur mesure', 'Emotes, skins et finishers personnalisés, pour toi ou ton clan, sur devis.', $surMesure],
    ];
}

// crée les pages qui manquent et remplace celles encore jamais modifiées dans l'administration (même texte qu'à leur
// création, ou première version sans repère et encore avec ses crochets d'origine)
function majPagesLegales(): void
{
    foreach (pagesLegales() as $slug => [$titre, $resume, $texte]) {
        $page = \Azuriom\Models\Page::where('slug', $slug)->first();
        if ($page === null) {
            \Azuriom\Models\Page::create(['title' => $titre, 'slug' => $slug, 'description' => $resume, 'content' => $texte, 'is_enabled' => true]);
            dire('Page créée : '.$titre.' (à compléter : les passages entre crochets)');
            continue;
        }
        $c = (string) $page->content;
        $vierge = str_contains($c, REPERE_PAGES)
            ? ! str_contains($c, REPERE_PAGES.VERSION_PAGES) && (str_contains($c, '[adresse e-mail de contact]') || str_contains($c, 'personne morale en cours de création'))
            : str_contains($c, '[date de mise en ligne]');
        if ($vierge) {
            $page->update(['title' => $titre, 'description' => $resume, 'content' => $texte]);
            dire('Page mise à jour : '.$titre);
        } elseif (! str_contains($c, REPERE_PAGES.VERSION_PAGES)) {
            dire('Page « '.$titre.' » déjà modifiée dans l’administration : gardée telle quelle (nouveau modèle dans site/vm/pages_legales.php)');
        }
    }
}
