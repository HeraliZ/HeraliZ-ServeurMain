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
     milieu, puis les contenus personnalisés. Personnages et images faits par le propriétaire (assets/img/grades,
     assets/img/avantages). --}}
@php
    $grades = [
        ['rescape', 'Rescapé', '#E39B5E'],
        ['traqueur', 'Traqueur', '#8FD3FF'],
        ['roi_de_la_zone', 'Roi de la Zone', '#FFC23A'],
    ];
    $avantages = [
        ['skin', 'Camo', '#B57BFF', '50% 45%'],
        ['finisher', 'Finisher', '#FF5A4E', '50% 45%'],
        ['emote', 'Emote', '#7FA8FF', '50% 22%'],
    ];
@endphp
<div class="hz-dispo">
    <h3>Disponible avec tes PB</h3>
    <div class="grades">
        @foreach($grades as [$id, $nom, $couleur])
            <div class="carte-grade" style="--g: {{ $couleur }}">
                <img src="{{ theme_asset('img/grades/'.$id.'.webp') }}" alt="" loading="lazy">
                <span class="g">{{ $nom }}</span>
            </div>
        @endforeach
    </div>
    <div class="grades beta">
        <div class="carte-grade" style="--g: #A970FF">
            <img src="{{ theme_asset('img/grades/beta.webp') }}" alt="" loading="lazy">
            <span class="g">BETA<small>Uniquement pendant la bêta</small></span>
        </div>
    </div>
    <h3 class="sous">Personnalisé à vie</h3>
    <div class="avantages">
        @foreach($avantages as [$id, $nom, $couleur, $cadrage])
            <div class="carte-avantage" style="--g: {{ $couleur }}">
                <img src="{{ theme_asset('img/avantages/'.$id.'.webp') }}" alt="" loading="lazy" style="object-position: {{ $cadrage }}">
                <span class="g">{{ $nom }}</span>
            </div>
        @endforeach
    </div>
    <p class="perso">Camo, finisher ou emote <b>personnalisé à vie</b> : disponible sur demande auprès d’un staff sur Discord.</p>
    <p class="perso"><b>Clés de caisses</b> : skins, emotes et autres cosmétiques à gagner.</p>
</div>
