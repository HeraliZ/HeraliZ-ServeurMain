@extends('layouts.app')

@section('title', trans('shop::messages.title'))

@section('content')
    @include('heraliz.boutique-entete')

    @if($categories->isEmpty())
        {{-- boutique pas encore ouverte : les grades de la saison, sans prix --}}
        <div class="grades">
            @foreach([['Rescapé', '#E39B5E'], ['Traqueur', '#8FD3FF'], ['Roi de la Zone', '#FFC23A']] as [$nom, $couleur])
                <div class="grade coins" style="--c: {{ $couleur }}">
                    <div class="nom-grade">
                        <div class="niv">Grade {{ $loop->iteration }}</div>
                        <h3>{{ $nom }}</h3>
                        <div class="barre-c"></div>
                    </div>
                    <div class="duree">Cosmétique</div>
                    <ul>
                        <li>@include('heraliz.icone', ['nom' => 'coche'])<span>{{ $nom }} en couleur devant ton pseudo</span></li>
                        <li>@include('heraliz.icone', ['nom' => 'coche'])<span>Sur le site, dans le chat et la liste des joueurs</span></li>
                    </ul>
                    <div class="prix"><span class="bientot">Bientôt</span></div>
                </div>
            @endforeach
        </div>

        <div class="coins vide-panneau mt-4">
            <div class="pastille">@include('heraliz.icone', ['nom' => 'panier'])</div>
            <h2>La boutique ouvre bientôt</h2>
            <p>Les grades arrivent avec la {{ Str::lower(theme_config('saison') ?: 'Saison 1') }}. Rejoins le Discord pour être prévenu de l’ouverture.</p>
            <a class="btn discord" href="{{ theme_config('discord') ?: 'https://discord.gg/heraliz' }}" target="_blank" rel="noopener noreferrer">@include('heraliz.icone', ['nom' => 'discord'])Rejoindre le Discord</a>
        </div>
    @elseif(setting('shop.home'))
        <div class="coins page-texte">{{ $welcome }}</div>
    @else
        <div class="coins vide-panneau">
            <div class="pastille">@include('heraliz.icone', ['nom' => 'panier'])</div>
            <h2>Choisis une catégorie</h2>
            <p>Les articles sont rangés dans les onglets ci-dessus.</p>
        </div>
    @endif

    @include('heraliz.boutique-infos')
@endsection
