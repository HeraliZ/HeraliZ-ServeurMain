{{-- Recharges de Points Boutique (maquette v4) : $lots = [[pb, prix affiché, lien ou formulaire]] ; icône selon le rang
     (petite pile → coffre au trésor), PB offerts calculés sur 100 PB = 1 €. --}}
@php
    $icones = ['petit', 'tas', 'sac', 'coffre', 'tresor'];
    $nb = count($lots);
@endphp
<div class="hz-lots" id="shop">
    @foreach(array_values($lots) as $i => $lot)
        @php
            $offerts = $lot['pb'] - (int) round($lot['euros'] * 100);
            $ruban = $nb >= 3 && $i === 2 ? 'Le plus choisi' : ($nb >= 4 && $i === $nb - 1 ? 'Meilleur prix' : null);
        @endphp
        <a href="{{ $lot['lien'] }}" class="hz-lot" @if(! empty($lot['payer'])) data-payer @endif>
            @if($ruban)<span class="ruban">{{ $ruban }}</span>@endif
            <img src="{{ theme_asset('img/pb/'.$icones[min($i, 4)].'.webp') }}" alt="" width="512" height="512" loading="lazy">
            <span class="n">{{ number_format($lot['pb'], 0, ',', ' ') }}<small>PB</small></span>
            <span class="bonus">@if($offerts > 0)+{{ number_format($offerts, 0, ',', ' ') }} offerts @endif</span>
            <span class="e">{{ $lot['prix'] }}</span>
        </a>
    @endforeach
</div>
<p class="hz-note">Paiement sécurisé, aucun abonnement. Les PB vont sur ton compte {{ site_name() }} : le même solde sur le site, dans le launcher et en jeu (<b>/boutique</b>). Échanger ou offrir des PB : <b>/trade</b> au spawn (10 % de taxe).</p>
{{-- Ce que les PB permettent d'avoir (demande du propriétaire, 23/10) : les 3 grades sur une ligne, BETA dessous au
     milieu, puis les contenus personnalisés et les clés. Personnages et images faits par le propriétaire
     (assets/img/grades, assets/img/avantages). --}}
@php
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
    $grades = [
        ['rescape', 'Rescapé', '#E39B5E', 1],
        ['traqueur', 'Traqueur', '#8FD3FF', 2],
        ['roi_de_la_zone', 'Roi de la Zone', '#FFC23A', 3],
    ];
    $avantages = [
        ['skin', 'Camo', '#B57BFF', '50% 45%', 'Ton arme avec un camo unique, fait pour toi.'],
        ['finisher', 'Finisher', '#FF5A4E', '50% 40%', 'Ton exécution signature pour achever un joueur.'],
        ['emote', 'Emote', '#7FA8FF', '50% 20%', 'Ta propre animation, rien qu’à toi.'],
    ];
@endphp
<section class="hz-dispo">
    <header class="tete">
        <span class="sur">Avec tes PB</span>
        <h2>Ce que tu débloques</h2>
    </header>

    <div class="grades">
        @foreach($grades as [$id, $nom, $couleur, $rang])
            <article class="grade" style="--g: {{ $couleur }}">
                <div class="haut">
                    <span class="sur">Grade</span>
                    <h3>{{ $nom }}</h3>
                    <span class="rang">@for($r = 1; $r <= 3; $r++)<i @class(['on' => $r <= $rang])></i>@endfor</span>
                </div>
                <img src="{{ theme_asset('img/grades/'.$id.'.webp') }}" alt="" loading="lazy">
            </article>
        @endforeach
    </div>

    <article class="grade beta" style="--g: #A970FF">
        <img src="{{ theme_asset('img/grades/beta.webp') }}" alt="" loading="lazy">
        <div class="haut">
            <span class="pastille">Édition limitée</span>
            <span class="sur">Grade</span>
            <h3>BETA</h3>
            <p>Uniquement pendant la bêta.</p>
        </div>
    </article>

    <header class="tete">
        <span class="sur">Sur demande</span>
        <h2>Personnalisé à vie</h2>
    </header>
    <div class="avantages">
        @foreach($avantages as [$id, $nom, $couleur, $cadrage, $texte])
            <article class="avantage" style="--g: {{ $couleur }}">
                <div class="image"><img src="{{ theme_asset('img/avantages/'.$id.'.webp') }}" alt="" loading="lazy" style="object-position: {{ $cadrage }}"></div>
                <div class="corps">
                    <h3>{{ $nom }}</h3>
                    <p>{{ $texte }}</p>
                </div>
            </article>
        @endforeach
    </div>

    <div class="bandeaux">
        <a class="bandeau discord" href="{{ $discord }}" target="_blank" rel="noopener noreferrer">
            <span class="ico">@include('heraliz.icone', ['nom' => 'discord'])</span>
            <span><b>Demande-le à un staff sur Discord</b><small>Camo, finisher ou emote créé pour toi, à vie.</small></span>
            <span class="fleche">→</span>
        </a>
        <div class="bandeau cles">
            <span class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="15.5" r="4.5"/><path d="m10.7 12.3 9.3-9.3M16 7l3 3M14 9l2 2"/></svg></span>
            <span><b>Clés de caisses</b><small>Skins, emotes et autres cosmétiques à gagner.</small></span>
        </div>
    </div>
</section>
