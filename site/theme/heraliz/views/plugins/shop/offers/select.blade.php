@extends('layouts.app')

@section('title', 'Points Boutique')

{{-- Choix de la recharge pour le moyen de paiement choisi ; le formulaire et la route shop.offers.pay sont ceux de l'extension
     Boutique d'Azuriom (ne pas les renommer). --}}
@push('footer-scripts')
    <script>
        document.querySelectorAll('[data-payer]').forEach(function (el) {
            el.addEventListener('click', function (ev) {
                ev.preventDefault();
                const form = document.getElementById('submitForm');
                form.action = el.href;
                form.submit();
            });
        });
    </script>
@endpush

@section('content')
    @include('heraliz.boutique-entete')

    @php
        $lots = $offers->sortBy('price')->map(fn ($o) => ['pb' => (int) $o->money, 'euros' => (float) $o->price, 'prix' => $o->price.' '.currency_display(), 'lien' => route('shop.offers.pay', [$o->id, $gateway->type]), 'payer' => true])->all();
    @endphp

    @if(empty($lots))
        <div class="hz-carte">{{ trans('shop::messages.offers.empty') }}</div>
    @else
        <p class="hz-note mb-3">Paiement par <b>{{ $gateway->name }}</b> : choisis ta recharge.</p>
        @include('heraliz.lots-pb')
    @endif

    <form method="POST" id="submitForm">@csrf</form>
@endsection
