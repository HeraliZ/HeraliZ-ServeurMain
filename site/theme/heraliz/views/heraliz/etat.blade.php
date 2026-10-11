{{-- Bandeau d'état en haut de chaque page : serveur (par Azuriom, si un serveur est relié), version, saison.
     L'état du serveur est gardé 1 minute, même « hors ligne » : sans ça, Azuriom réessaie de joindre un serveur éteint
     à chaque page (jusqu'à 3 s d'attente). --}}
@php
    $etatServeur = $server ? cache()->remember('heraliz.etat-serveur.'.$server->id, 60, function () use ($server) {
        $enLigne = $server->isOnline();
        return ['en_ligne' => $enLigne, 'joueurs' => $enLigne ? $server->getOnlinePlayers() : 0, 'max' => $enLigne ? $server->getMaxPlayers() : 0];
    }) : null;
    $saison = theme_config('saison') ?: 'Saison 1';
    $versionJeu = theme_config('version') ?: 'Version 1';
    $jourSaison = null;
    try {
        if ($debut = theme_config('saison_debut')) {
            $debut = \Illuminate\Support\Carbon::parse($debut)->startOfDay();
            $jourSaison = $debut->isPast() ? (int) $debut->diffInDays(now()->startOfDay()) + 1 : null;
        }
    } catch (\Throwable) {
        $jourSaison = null;
    }
@endphp
<div class="etat-barre">
    @if($etatServeur !== null)
        @if($etatServeur['en_ligne'])
            <span><span class="point"></span><b class="vert">Serveur en ligne</b></span>
            <span><b>{{ $etatServeur['joueurs'] }}</b>&nbsp;/ {{ $etatServeur['max'] }} survivants</span>
        @else
            <span><span class="point eteint"></span><b class="rouge">Serveur hors ligne</b></span>
        @endif
    @endif
    <span class="facultatif">Minecraft 1.20.1</span>
    <div class="d">
        <span>{{ $saison }} ·&nbsp;<b>@if($jourSaison) jour {{ $jourSaison }} @else {{ $versionJeu }} @endif</b></span>
    </div>
</div>
