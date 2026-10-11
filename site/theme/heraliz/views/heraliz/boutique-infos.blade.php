{{-- Bas de la boutique : objectif du mois, meilleur acheteur, achats récents (si activés dans l'administration) --}}
@if($goal >= 0 || $topCustomer !== null || $recentPayments !== null)
    <div class="boutique-cote">
        @if($goal >= 0)
            <div class="card">
                <div class="card-header">{{ trans('shop::messages.goal.title') }}</div>
                <div class="card-body">
                    <div class="progress mb-2"><div class="progress-bar" role="progressbar" aria-valuenow="{{ $goal }}" aria-valuemin="0" aria-valuemax="100" style="width: {{ min($goal, 100) }}%"></div></div>
                    <p class="card-text text-center mb-0">{{ trans_choice('shop::messages.goal.progress', $goal) }}</p>
                </div>
            </div>
        @endif

        @if($topCustomer !== null)
            <div class="card">
                <div class="card-header">{{ trans('shop::messages.top.title') }}</div>
                <div class="card-body d-flex align-items-center gap-3">
                    <img class="rounded" src="{{ $topCustomer->user->getAvatar(64) }}" alt="" width="48" height="48">
                    <div>
                        <div class="h5 mb-0">{{ $topCustomer->user->name }}</div>
                        @if($displaySidebarAmount)<small class="text-body-secondary">{{ $topCustomer->formatPrice() }}</small>@endif
                    </div>
                </div>
            </div>
        @endif

        @if($recentPayments !== null)
            <div class="card">
                <div class="card-header">{{ trans('shop::messages.recent.title') }}</div>
                <div class="list-group list-group-flush">
                    @forelse($recentPayments as $payment)
                        <div class="list-group-item d-flex align-items-center gap-3">
                            <img class="rounded" src="{{ $payment->user->getAvatar(48) }}" alt="" width="32" height="32">
                            <div>
                                <div>{{ $payment->user->name }}</div>
                                <small class="text-body-secondary">@if($displaySidebarAmount){{ $payment->formatPrice() }} · @endif{{ format_date($payment->created_at) }}</small>
                            </div>
                        </div>
                    @empty
                        <div class="list-group-item">{{ trans('shop::messages.recent.empty') }}</div>
                    @endforelse
                </div>
            </div>
        @endif
    </div>
@endif

<div class="bande-info coins">
    <div>@include('heraliz.icone', ['nom' => 'camion']) Livré en jeu, même si tu n’es pas connecté</div>
    <div>@include('heraliz.icone', ['nom' => 'bouclier']) 100 % cosmétique : aucun avantage en combat ni en loot</div>
    <div>@include('heraliz.icone', ['nom' => 'drapeau']) Chaque achat aide le serveur à vivre</div>
</div>
