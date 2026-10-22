<?php

/*
 * Pages légales du site HeraliZ (créées une seule fois par reglages.php, ensuite modifiables dans l'administration →
 * Pages). Les passages entre crochets [ ... ] sont à compléter par le propriétaire (identité de l'éditeur, hébergeur) :
 * ils ne doivent jamais être écrits dans le dépôt. Modèles de départ à faire relire par un professionnel du droit.
 */

function pagesLegales(): array
{
    $maj = 'Dernière mise à jour : [date de mise en ligne].';

    $cgv = '<p><em>'.$maj.'</em></p>'
        .'<h2>1. Objet</h2><p>Les présentes conditions générales de vente (CGV) s’appliquent à tout achat fait sur la boutique du site heraliz.fr (« la Boutique ») pour le serveur Minecraft HeraliZ (« le Serveur »). Tout achat vaut acceptation pleine et entière des présentes CGV, des conditions d’utilisation et du Règlement du Serveur.</p>'
        .'<h2>2. Vendeur</h2><p>La Boutique est éditée par [nom ou raison sociale, statut, adresse, numéro SIREN le cas échéant], joignable sur le Discord officiel (discord.gg/heraliz) et à l’adresse [e-mail de contact].</p>'
        .'<h2>3. Produits</h2><p>La Boutique vend exclusivement des <strong>contenus numériques non physiques</strong>, utilisables uniquement sur le Serveur : crédits du site, grades (Rescapé, Traqueur, Roi de la Zone, BETA), clés de caisses cosmétiques et autres objets virtuels. Ces contenus n’ont <strong>aucune valeur monétaire</strong>, ne sont ni échangeables contre de l’argent, ni revendables, ni transférables à un autre compte. Le contenu de chaque grade est décrit sur sa fiche et peut évoluer avec les mises à jour du Serveur.</p>'
        .'<p>HeraliZ n’est ni affilié à, ni approuvé par Mojang Studios ou Microsoft. Minecraft est une marque de Mojang Studios.</p>'
        .'<h2>4. Prix et paiement</h2><p>Les prix sont indiqués en euros toutes taxes comprises (ou en crédits du site, 1 crédit = 1 €). Le paiement se fait par les moyens proposés sur la Boutique ; il est exigible immédiatement. Il n’y a <strong>aucun abonnement ni prélèvement automatique</strong>.</p>'
        .'<h2>5. Durée des grades</h2><p>Les grades « 1 mois » et « 3 mois » sont valables respectivement 30 et 90 jours à compter de leur livraison. À la fin de cette durée, le grade est retiré automatiquement et <strong>n’est jamais renouvelé tout seul</strong>, même si des crédits restent sur le compte : pour le garder, il faut le racheter. Racheter le même grade avant la fin ajoute la durée achetée. Passer à un grade supérieur se paie <strong>au prix plein</strong> du nouveau grade, sans déduction. Le grade BETA est attribué « à vie », c’est-à-dire pour toute la durée d’exploitation du Serveur.</p>'
        .'<h2>6. Livraison</h2><p>Le contenu est livré automatiquement en jeu sur le compte du site utilisé pour l’achat (pseudo du compte), en principe dans les minutes qui suivent, le joueur devant être connecté au Serveur. Un défaut de livraison est à signaler au staff sur Discord dans les 7 jours, avec la preuve d’achat.</p>'
        .'<h2>7. Absence de droit de rétractation et de remboursement</h2><p>Conformément à l’article L221-28 13° du Code de la consommation, le droit de rétractation ne peut être exercé pour la fourniture d’un contenu numérique sans support matériel dont l’exécution a commencé avec l’accord préalable exprès du consommateur. <strong>En validant sa commande, l’acheteur demande la livraison immédiate du contenu et renonce expressément à son droit de rétractation.</strong> En conséquence, <strong>aucun achat n’est remboursable</strong>, en tout ou partie, quel qu’en soit le motif (changement d’avis, non-utilisation, fin ou expiration du grade, perte d’objets en jeu, réinitialisation, changement de carte, évolution du contenu, interruption du Serveur). Tout achat doit donc être bien réfléchi.</p>'
        .'<h2>8. Sanctions</h2><p>L’achat d’un contenu ne donne aucune immunité. Le staff se réserve le droit de sanctionner (avertissement, mute, exclusion temporaire ou bannissement définitif) tout joueur, <strong>même s’il a payé</strong>, en cas de non-respect du Règlement ou des présentes CGV. Une sanction entraîne la perte, totale ou pour sa durée, des avantages achetés, <strong>sans aucun remboursement ni compensation</strong>.</p>'
        .'<h2>9. Contestation de paiement et fraude</h2><p>Toute contestation de paiement abusive (rejet de paiement, « chargeback ») ou tout achat frauduleux entraîne le retrait des contenus achetés et le bannissement définitif des comptes liés, sans préjudice de poursuites.</p>'
        .'<h2>10. Mineurs</h2><p>Un mineur doit avoir l’autorisation de son représentant légal avant tout achat ; l’achat est réputé fait avec cet accord.</p>'
        .'<h2>11. Disponibilité du Serveur</h2><p>Le Serveur est fourni « en l’état », notamment pendant la bêta. HeraliZ ne garantit pas une disponibilité permanente et peut modifier, équilibrer, réinitialiser ou retirer tout contenu du jeu. Les interruptions (maintenance, panne, mise à jour) ne donnent droit à aucun remboursement ni prolongation. En cas de fermeture définitive du Serveur, aucun remboursement n’est dû.</p>'
        .'<h2>12. Créations sur mesure</h2><p>Les emotes, skins et finishers personnalisés (pour un joueur ou un clan) sont faits sur devis, après échange avec le staff sur Discord, dans les limites techniques de Minecraft et de nos développeurs. Le rendu final peut différer de l’exemple fourni. Une fois le travail commencé, aucun remboursement n’est possible. Les créations restent la propriété d’HeraliZ, qui en concède l’usage sur le Serveur.</p>'
        .'<h2>13. Responsabilité</h2><p>La responsabilité d’HeraliZ est limitée, en tout état de cause, au montant payé pour le contenu concerné. HeraliZ n’est pas responsable de la perte d’accès due au joueur (mot de passe, partage de compte, piratage de son ordinateur).</p>'
        .'<h2>14. Modifications et droit applicable</h2><p>HeraliZ peut modifier les présentes CGV ; la version applicable est celle en vigueur au jour de l’achat. Les CGV sont soumises au droit français. En cas de litige, une solution amiable sera recherchée avant toute action ; le consommateur peut recourir gratuitement à un médiateur de la consommation : [nom et site du médiateur].</p>';

    $mentions = '<p><em>'.$maj.'</em></p>'
        .'<h2>Éditeur du site</h2><p>[Nom et prénom, ou raison sociale et forme juridique]<br>[Adresse postale]<br>[Numéro SIREN / RCS le cas échéant]<br>Contact : [adresse e-mail] · Discord : discord.gg/heraliz<br>Directeur de la publication : [nom].</p>'
        .'<h2>Hébergement</h2><p>Site : [nom de l’hébergeur, adresse, téléphone].<br>Serveur de jeu : [nom de l’hébergeur, adresse, téléphone].</p>'
        .'<h2>Propriété intellectuelle</h2><p>Les textes, images, logos, modèles et créations d’HeraliZ sont protégés ; toute reproduction sans autorisation est interdite. Les contenus des autres auteurs restent la propriété de leurs auteurs. HeraliZ n’est ni affilié à, ni approuvé par Mojang Studios ou Microsoft. Le site est propulsé par Azuriom.</p>';

    $confidentialite = '<p><em>'.$maj.'</em></p>'
        .'<h2>Responsable du traitement</h2><p>[Nom ou raison sociale de l’éditeur], contact : [adresse e-mail].</p>'
        .'<h2>Données collectées</h2><ul><li>Compte : pseudo, adresse e-mail, mot de passe (chiffré, jamais lisible), double authentification si activée.</li><li>Jeu : identifiant Minecraft, adresse IP, historique de connexion, sanctions, données de l’anticheat (empreinte du matériel et des fichiers du jeu) servant à lutter contre la triche et les doubles comptes.</li><li>Achats : historique des commandes ; les données bancaires sont traitées par le prestataire de paiement, jamais par HeraliZ.</li></ul>'
        .'<h2>Finalités et bases légales</h2><p>Gestion du compte et des achats (exécution du contrat), sécurité du serveur et lutte contre la triche (intérêt légitime), obligations comptables (obligation légale).</p>'
        .'<h2>Durée de conservation</h2><p>Compte : jusqu’à sa suppression, puis 1 an. Sanctions et données de l’anticheat : jusqu’à 3 ans. Factures : 10 ans.</p>'
        .'<h2>Destinataires</h2><p>Le staff d’HeraliZ (selon ses fonctions), les hébergeurs, le prestataire de paiement et, le cas échéant, le service d’envoi d’e-mails. Aucune donnée n’est vendue.</p>'
        .'<h2>Vos droits</h2><p>Accès, rectification, effacement, opposition, limitation et portabilité : écrire à [adresse e-mail]. Réclamation possible auprès de la CNIL (cnil.fr).</p>'
        .'<h2>Cookies</h2><p>Seuls les cookies nécessaires au fonctionnement du site (session, sécurité) sont utilisés.</p>';

    $surMesure = '<p>Tu veux une <strong>emote</strong>, un <strong>skin d’arme</strong> ou un <strong>finisher</strong> rien qu’à toi, ou pour <strong>tout ton clan</strong> (emote de clan, skin de clan, finisher de clan) ?</p>'
        .'<ol><li>Envoie un message privé au staff sur Discord (discord.gg/heraliz).</li><li>Joins un exemple : image ou vidéo du rendu voulu.</li><li>Le staff te répond avec un devis, dans les limites de ce qui est possible sur Minecraft et de ce que nos développeurs peuvent faire.</li></ol>'
        .'<p>Les acheteurs du grade BETA ont une emote et un skin d’arme personnalisés inclus. Voir les Conditions de vente, article 12.</p>';

    return [
        'cgv' => ['Conditions de vente', 'Conditions générales de vente de la boutique HeraliZ.', $cgv],
        'mentions-legales' => ['Mentions légales', 'Éditeur et hébergeurs du site HeraliZ.', $mentions],
        'confidentialite' => ['Confidentialité', 'Données personnelles et cookies sur HeraliZ.', $confidentialite],
        'sur-mesure' => ['Créations sur mesure', 'Emotes, skins et finishers personnalisés, pour toi ou ton clan, sur devis.', $surMesure],
    ];
}
