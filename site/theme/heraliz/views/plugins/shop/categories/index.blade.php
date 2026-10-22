@extends('layouts.app')

@section('title', 'Points Boutique')

{{-- PB = monnaie du compte du site : les recharges sont les offres d'Azuriom (choix du moyen de paiement ensuite) --}}
@section('content')
    @include('heraliz.boutique-entete')

    @php
        $offres = use_site_money() ? \Azuriom\Plugin\Shop\Models\Offer::where('is_enabled', true)->orderBy('price')->get() : collect();
        $lots = $offres->map(fn ($o) => ['pb' => (int) $o->money, 'euros' => (float) $o->price, 'prix' => rtrim(rtrim(number_format((float) $o->price, 2, ',', ' '), '0'), ',').' €', 'lien' => route('shop.offers.select')])->all();
    @endphp

    @if(empty($lots))
        <div class="hz-carte"><h3>La boutique ouvre bientôt</h3><p>Rejoins le Discord pour être prévenu de l’ouverture.</p></div>
    @else
        @guest<p class="hz-note mb-3">Connecte-toi d’abord : les PB vont sur ton compte.</p>@endguest
        @include('heraliz.lots-pb')
    @endif
@endsection
