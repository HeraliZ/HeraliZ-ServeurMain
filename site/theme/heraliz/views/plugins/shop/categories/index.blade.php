@extends('layouts.app')

@section('title', trans('shop::messages.title'))

{{-- une seule catégorie (Points Boutique) : ses lots directement ; sinon un lien par catégorie --}}
@section('content')
    @include('heraliz.boutique-entete')

    @php
        $lots = $categories->count() === 1 ? \Azuriom\Plugin\Shop\Models\Package::where('is_enabled', true)->where('category_id', $categories->first()->id)->orderBy('position')->get() : null;
    @endphp

    @if($categories->isEmpty() || ($lots !== null && $lots->isEmpty()))
        <div class="hz-carte"><h3>La boutique ouvre bientôt</h3><p>Les Points Boutique arrivent avec /boutique en jeu. Rejoins le Discord pour être prévenu de l’ouverture.</p></div>
    @elseif($lots !== null)
        @include('heraliz.lots-pb', ['packages' => $lots])
    @else
        <div class="hz-trois">
            @foreach($categories as $c)
                <a class="hz-carte lien" href="{{ route('shop.categories.show', $c) }}"><h3>{{ $c->name }}</h3></a>
            @endforeach
        </div>
    @endif
@endsection
