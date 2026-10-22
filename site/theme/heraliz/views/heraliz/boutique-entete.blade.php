{{-- En-tête de la boutique : le site ne vend que des Points Boutique, solde du compte du site (22/10) --}}
<div class="bandeau hz-bandeau-pb">
    <div>
        <h1>Points Boutique</h1>
        <p>Recharge ton compte en PB ici, dépense-les en jeu avec <b>/boutique</b> : grades, clés, cosmétiques.</p>
    </div>
    @auth
        <div class="hz-solde"><span>Ton solde</span><b>{{ number_format((float) auth()->user()->money, 0, ',', ' ') }} <small>PB</small></b></div>
    @endauth
</div>
