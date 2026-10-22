{{-- En-tête de la boutique : le site ne vend que des Points Boutique (22/10), à dépenser en jeu avec /boutique --}}
<div class="bandeau">
    <h1>Points Boutique</h1>
    <p>Achète des PB ici, dépense-les en jeu avec <b>/boutique</b> : grades, clés, cosmétiques.</p>
</div>

@if($shopUser !== null)
    <nav class="onglets" aria-label="Boutique">
        <span class="fin"></span>
        <a href="{{ route('shop.cart.index') }}">@include('heraliz.icone', ['nom' => 'panier']) {{ trans('shop::messages.cart.title') }}</a>
        @if($userHasPayments)
            <a href="{{ route('shop.profile') }}">@include('heraliz.icone', ['nom' => 'historique']) Mes achats</a>
        @endif
    </nav>
@endif
