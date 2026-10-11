{{-- En-tête de la boutique (maquette « Intendance ») et onglets des catégories --}}
<div class="bandeau">
    <span class="etiquette">Intendance</span>
    <h1>{{ $titre ?? trans('shop::messages.title') }}</h1>
    <p>Grades et cosmétiques, livrés en jeu tout de suite, ou à ta prochaine connexion. Rien qui donne un avantage en combat ou en loot.</p>

    @if($shopUser !== null && use_site_money())
        <div class="solde coins">
            <div>
                <div class="l">Ton solde</div>
                <div class="v"><span class="piece"></span>{{ format_money($shopUser->money) }}</div>
            </div>
            <a class="btn rouge" href="{{ route('shop.offers.select') }}">Acheter des crédits</a>
        </div>
    @endif
</div>

<nav class="onglets" aria-label="Catégories de la boutique">
    @if($displayHome)
        <a href="{{ route('shop.home') }}" class="@if($category === null) on @endif">{{ trans('messages.home') }}</a>
    @endif
    @foreach($categories as $sousCategorie)
        @php
            $ouverte = $category !== null && ($sousCategorie->is($category) || $sousCategorie->categories->contains(fn ($c) => $c->is($category)));
        @endphp
        <a href="{{ route('shop.categories.show', $sousCategorie) }}" class="@if($ouverte) on @endif">
            @if($sousCategorie->icon)<i class="{{ $sousCategorie->icon }}"></i>@endif
            {{ $sousCategorie->name }}
            @if($sousCategorie->packages_count)<small>{{ $sousCategorie->packages_count }}</small>@endif
        </a>
    @endforeach
    <span class="fin"></span>
    @if($shopUser !== null)
        <a href="{{ route('shop.cart.index') }}">@include('heraliz.icone', ['nom' => 'panier']) {{ trans('shop::messages.cart.title') }}</a>
        @if($userHasPayments)
            <a href="{{ route('shop.profile') }}">@include('heraliz.icone', ['nom' => 'historique']) Mes achats</a>
        @endif
    @else
        <a href="{{ route('shop.login') }}">@include('heraliz.icone', ['nom' => 'joueur']) {{ trans('auth.login') }}</a>
    @endif
</nav>

@if($category !== null)
    @php
        $parent = $category->parent_id !== null ? $categories->first(fn ($c) => $c->categories->contains(fn ($s) => $s->is($category))) : $category;
    @endphp
    @if($parent !== null && ! $parent->categories->isEmpty())
        <nav class="onglets" aria-label="Sous-catégories">
            @foreach($parent->categories as $sous)
                <a href="{{ route('shop.categories.show', $sous) }}" class="sous @if($sous->is($category)) on @endif">
                    @if($sous->icon)<i class="{{ $sous->icon }}"></i>@endif
                    {{ $sous->name }}
                </a>
            @endforeach
        </nav>
    @endif
@endif
