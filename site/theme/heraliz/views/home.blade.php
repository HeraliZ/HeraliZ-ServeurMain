@extends('layouts.base')

@section('title', trans('messages.home'))
@section('classe-page', 'accueil')

{{-- Accueil (maquette v4 validée le 22/10) : accroche et état du serveur, comment jouer, actualités. Aucune image de
     fond (choix du propriétaire). --}}
@php
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
    $saison = theme_config('saison') ?: 'Saison 1';
    $rapports = $posts->take(3);
    $pageLauncher = \Azuriom\Models\Page::enabled()->where('slug', 'launcher')->exists() ? route('pages.show', 'launcher') : '#';
@endphp

@section('app')
    <div class="wrap hz">
        <div class="alerts">@include('elements.session-alerts')</div>

        <section class="hz-hero">
            <div>
                <span class="etiquette">{{ $saison }} · bêta</span>
                <h1>Survis. Raid. <em>Défends.</em></h1>
                <p>Serveur Minecraft de survie façon DayZ : loot par paliers, bases à la Rust, clans, extractions.</p>
                <div class="hz-cta">
                    <a class="btn rouge" href="{{ $pageLauncher }}">Télécharger le launcher</a>
                    <a class="btn os" href="{{ $discord }}" target="_blank" rel="noopener noreferrer">@include('heraliz.icone', ['nom' => 'discord'])Discord</a>
                </div>
            </div>
        </section>

        <div class="hz-trois">
            <div class="hz-carte"><small>Étape 1</small><h3>Crée ton compte</h3><p>Dans le launcher, ou ici. Le même compte sert partout.</p></div>
            <div class="hz-carte"><small>Étape 2</small><h3>Lance le jeu</h3><p>Le launcher télécharge tout seul les mods à jour. Rien à installer à la main.</p></div>
            <div class="hz-carte"><small>Étape 3</small><h3>Vote chaque jour</h3><p>2 PB, une clé de vote et 10 hérals par vote.</p></div>
        </div>

        @if($message)
            <div class="hz-carte mt-4">{{ $message }}</div>
        @endif

        @if(! $rapports->isEmpty())
            <h2 class="hz-sec">Actualités</h2>
            <div class="hz-trois">
                @foreach($rapports as $post)
                    <div class="hz-carte">
                        <small>{{ optional($post->published_at)->translatedFormat('d M') }}</small>
                        <h3>{{ $post->title }}</h3>
                        <p>{{ $post->description ?: Str::limit(strip_tags($post->content), 110) }}</p>
                    </div>
                @endforeach
            </div>
        @endif
    </div>
@endsection
