@extends('layouts.app')

@section('title', $category->name)

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

@section('content')
    @include('heraliz.boutique-entete')

    {{-- couleurs des grades de la saison (choix du propriétaire), quand l'article ne donne pas de grade du site --}}
    @php
        $couleursGrades = ['rescapé' => '#E39B5E', 'rescape' => '#E39B5E', 'traqueur' => '#8FD3FF', 'roi de la zone' => '#FFC23A'];
    @endphp

    @if($category->description)
        <div class="coins page-texte mb-4">{!! $category->description !!}</div>
    @endif

    <div class="grades" id="shop">
        @forelse($category->packages as $package)
            @php
                $couleur = $package->role?->color ?: ($couleursGrades[Str::lower(trim($package->name))] ?? null);
            @endphp
            <div class="grade coins" @if($couleur) style="--c: {{ $couleur }}" @endif>
                <div class="nom-grade">
                    @if($package->role)<div class="niv">Grade</div>@endif
                    <h3>{{ $package->name }}</h3>
                    <div class="barre-c"></div>
                </div>
                @if($package->isSubscription())
                    <div class="duree">Renouvelé tous les {{ $package->subscriptionPeriodCount() }} {{ trans('shop::messages.periods.'.$package->subscriptionPeriodUnit()) }}</div>
                @endif

                @if($package->hasImage())
                    <a class="image mt-3" href="#" data-package-url="{{ route('shop.packages.show', $package) }}">
                        <img src="{{ $package->imageUrl() }}" alt="{{ $package->name }}" loading="lazy">
                    </a>
                @endif

                @if($package->short_description)
                    <p class="desc">{{ $package->short_description }}</p>
                @endif

                <div class="prix">
                    <span class="v">
                        @if($package->isDiscounted())<del>{{ shop_format_amount($package->getOriginalPrice()) }}</del>@endif
                        {{ shop_format_amount($package->getPrice()) }}
                    </span>
                    <a href="#" class="btn rouge" data-package-url="{{ route('shop.packages.show', $package) }}">{{ trans('shop::messages.buy') }}</a>
                </div>
            </div>
        @empty
            <div class="coins vide-panneau" style="grid-column: 1 / -1">
                <h2>Rien ici pour l’instant</h2>
                <p>{{ trans('shop::messages.categories.empty') }}</p>
            </div>
        @endforelse
    </div>

    @include('heraliz.boutique-infos')

    <div class="modal fade" id="itemModal" tabindex="-1" role="dialog" aria-labelledby="itemModalLabel" aria-hidden="true"></div>
@endsection
