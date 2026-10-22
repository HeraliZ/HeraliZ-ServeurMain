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
            <span class="bonus">@if($offerts > 0)+{{ number_format($offerts, 0, ',', ' ') }} offerts@endif</span>
            <span class="e">{{ $lot['prix'] }}</span>
        </a>
    @endforeach
</div>
<p class="hz-note">Paiement sécurisé, aucun abonnement. Les PB vont sur ton compte {{ site_name() }} : le même solde sur le site, dans le launcher et en jeu (<b>/boutique</b>). Échanger ou offrir des PB : <b>/trade</b> au spawn (10 % de taxe).</p>
