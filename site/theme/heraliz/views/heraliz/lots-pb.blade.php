{{-- Lots de Points Boutique (maquette v4) : icône selon le rang du lot (petite pile → coffre au trésor), PB offerts
     calculés sur 100 PB = 1 €. Le clic ouvre la fiche de l'article d'Azuriom (ajout au panier, paiement). --}}
@php
    $icones = ['petit', 'tas', 'sac', 'coffre', 'tresor'];
    $lots = $packages->values();
    $nb = $lots->count();
@endphp
<div class="hz-lots" id="shop">
    @foreach($lots as $i => $package)
        @php
            $pb = (int) preg_replace('/\D/', '', $package->name);
            $euros = (float) $package->getPrice();
            $offerts = $pb > 0 ? $pb - (int) round($euros * 100) : 0;
            $icone = $icones[min($i, count($icones) - 1)];
            $ruban = $nb >= 3 && $i === 2 ? 'Le plus choisi' : ($nb >= 4 && $i === $nb - 1 ? 'Meilleur prix' : null);
        @endphp
        <a href="#" class="hz-lot" data-package-url="{{ route('shop.packages.show', $package) }}">
            @if($ruban)<span class="ruban">{{ $ruban }}</span>@endif
            <img src="{{ theme_asset('img/pb/'.$icone.'.webp') }}" alt="" width="512" height="512" loading="lazy">
            <span class="n">{{ $pb > 0 ? number_format($pb, 0, ',', ' ') : $package->name }}@if($pb > 0)<small>PB</small>@endif</span>
            <span class="bonus">@if($offerts > 0)+{{ number_format($offerts, 0, ',', ' ') }} offerts@endif</span>
            <span class="e">{{ shop_format_amount($package->getPrice()) }}</span>
        </a>
    @endforeach
</div>
<p class="hz-note">Paiement sécurisé, aucun abonnement. Les PB arrivent en jeu sur ton pseudo dès que tu es connecté. Échanger ou offrir des PB : <b>/trade</b> au spawn (10 % de taxe).</p>

<div class="modal fade" id="itemModal" tabindex="-1" role="dialog" aria-labelledby="itemModalLabel" aria-hidden="true"></div>

@push('footer-scripts')
    <script>
        document.querySelectorAll('[data-package-url]').forEach(function (el) {
            el.addEventListener('click', function (ev) {
                ev.preventDefault();
                axios.get(el.dataset['packageUrl']).then(function (response) {
                    const itemModal = document.getElementById('itemModal');
                    itemModal.innerHTML = response.data;
                    new bootstrap.Modal(itemModal).show();
                }).catch(function (error) {
                    createAlert('danger', error, true);
                });
            });
        });
    </script>
@endpush
