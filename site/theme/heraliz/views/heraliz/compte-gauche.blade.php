{{-- Colonne de gauche des pages Connexion et Inscription (maquette « Enrôlement ») --}}
<div class="gauche">
    <span class="etiquette">{{ $etiquette }}</span>
    <h1 class="titre">{!! $titre !!}</h1>
    <p class="intro">Ton compte {{ site_name() }} sert pour la boutique, les votes et les actualités. Prends le pseudo que tu utilises en jeu : c’est lui qui reçoit tes achats et tes récompenses.</p>
    <div class="puces">
        <div class="puce">
            <div class="ic">@include('heraliz.icone', ['nom' => 'joueur'])</div>
            <div><div class="t">Ton pseudo de jeu</div><div class="d">Le même pseudo que sur le serveur : les grades et les récompenses arrivent dessus.</div></div>
        </div>
        <div class="puce">
            <div class="ic">@include('heraliz.icone', ['nom' => 'cadenas'])</div>
            <div><div class="t">Mot de passe secret</div><div class="d">Jamais enregistré en clair : même l’équipe ne peut pas le lire. Prends-en un différent de celui du jeu (/login).</div></div>
        </div>
        <div class="puce">
            <div class="ic">@include('heraliz.icone', ['nom' => 'camion'])</div>
            <div><div class="t">Livré même hors ligne</div><div class="d">Achats et récompenses de vote arrivent en jeu à ta prochaine connexion.</div></div>
        </div>
    </div>
</div>
