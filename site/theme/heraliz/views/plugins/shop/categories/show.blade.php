@extends('layouts.app')

@section('title', $category->name)

{{-- catégorie d'articles (s'il en reste) : cartes simples, fiche d'Azuriom au clic --}}
@push('footer-scripts')
    <script>
        document.querySelectorAll('[data-package-url]').forEach(function (el) {
            el.addEventListener('click', function (ev) {
                ev.preventDefault();
                axios.get(el.dataset['packageUrl']).then(function (r) {
                    const m = document.getElementById('itemModal');
                    m.innerHTML = r.data;
                    new bootstrap.Modal(m).show();
                }).catch(function (e) { createAlert('danger', e, true); });
            });
        });
    </script>
@endpush

@section('content')
    @include('heraliz.boutique-entete')

    <div class="hz-trois">
        @forelse($category->packages as $package)
            <a href="#" class="hz-carte lien" data-package-url="{{ route('shop.packages.show', $package) }}">
                <h3>{{ $package->name }}</h3>
                @if($package->short_description)<p>{{ $package->short_description }}</p>@endif
                <small>{{ shop_format_amount($package->getPrice()) }}</small>
            </a>
        @empty
            <div class="hz-carte">{{ trans('shop::messages.categories.empty') }}</div>
        @endforelse
    </div>

    <div class="modal fade" id="itemModal" tabindex="-1" role="dialog" aria-hidden="true"></div>
@endsection
