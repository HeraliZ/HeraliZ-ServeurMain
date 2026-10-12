@extends('layouts.base')

@section('title', trans('messages.home'))
@section('classe-page', 'accueil')

@php
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
    $saison = theme_config('saison') ?: 'Saison 1';
    // état du serveur gardé 1 minute (même clé que le bandeau d'état, heraliz/etat.blade.php)
    $etatServeur = $server ? cache()->remember('heraliz.etat-serveur.'.$server->id, 60, function () use ($server) {
        $enLigne = $server->isOnline();
        return ['en_ligne' => $enLigne, 'joueurs' => $enLigne ? $server->getOnlinePlayers() : 0, 'max' => $enLigne ? $server->getMaxPlayers() : 0];
    }) : null;
    $places = ($etatServeur['max'] ?? 0) > 0 ? $etatServeur['max'] : (theme_config('places') ?: 50);
    $rapports = $posts->take(3);
    $boutique = plugins()->isEnabled('shop');
    $votes = plugins()->isEnabled('vote');
@endphp

@section('app')
    <section class="hero">
        @include('heraliz.carte', ['notes' => true])

        <div class="wrap">
            <span class="etiquette">{{ $saison }} · {{ theme_config('version') ?: 'Version 1' }}</span>
            <img class="logo" src="{{ theme_asset('img/logo-560.webp') }}" srcset="{{ theme_asset('img/logo-560.webp') }} 560w, {{ theme_asset('img/logo-1120.webp') }} 1120w"
                 sizes="(max-width: 600px) 92vw, 540px" width="560" height="183" alt="{{ site_name() }}" fetchpriority="high">
            <h1 class="titre">Pille. Construis. <span>Survis.</span></h1>
            <p class="pitch">Pille les villes, construis ta base, survis aux hordes. Seul ou en clan, chaque nuit peut être la dernière.</p>
            <div class="actions" id="telecharger">
                @include('heraliz.telecharger')
                <a class="btn discord grand" href="{{ $discord }}" target="_blank" rel="noopener noreferrer">@include('heraliz.icone', ['nom' => 'discord', 'style' => 'width:22px;height:22px'])Rejoindre le Discord</a>
            </div>
            <p class="note-os">Linux : fichier AppImage, ou <a href="https://heraliz.fr/telecharger/HeraliZ-Launcher.deb">paquet .deb</a>. Mac : au premier lancement, clic droit sur l'application puis « Ouvrir ».</p>
        </div>
    </section>

    <section class="chiffres">
        <div class="wrap">
            <div class="chiffre"><span class="n">7</span><span class="l">niveaux<br>de loot</span></div>
            <div class="chiffre"><span class="n">5</span><span class="l">boss<br>à abattre</span></div>
            <div class="chiffre"><span class="n">3</span><span class="l">extractions<br>ouvertes</span></div>
            <div class="chiffre"><span class="n">{{ $places }}</span><span class="l">survivants<br>en même temps</span></div>
        </div>
    </section>

    <div class="wrap alerts" style="position: relative">
        @include('elements.session-alerts')
        @if($message)
            <div class="coins page-texte" style="margin-top: 40px">{{ $message }}</div>
        @endif
    </div>

    @if(! $rapports->isEmpty())
        <section class="section">
            <div class="wrap">
                <div class="tete-section">
                    <div><span class="etiquette">Rapports de terrain</span><h2 class="titre">Nouveautés</h2></div>
                    <p class="aside">Les dernières nouvelles de la zone, directement de l’équipe {{ site_name() }}. <a href="{{ route('posts.index') }}">Toutes les actualités</a></p>
                </div>
                <div class="rapports n{{ $rapports->count() }}">
                    @foreach($rapports as $post)
                        @php
                            $recent = $post->published_at !== null && $post->published_at->gt(now()->subDays(15));
                            $tampon = $post->is_pinned ? ['À la une', 'laiton'] : ($recent ? ['Nouveau', ''] : null);
                        @endphp
                        <a class="rapport" href="{{ route('posts.show', $post) }}">
                            <span class="scotch"></span>
                            <span class="photo d-block">
                                @if($post->hasImage())
                                    <img src="{{ $post->imageUrl() }}" alt="" loading="lazy">
                                @else
                                    <span class="vide"><img src="{{ theme_asset('img/embleme-192.png') }}" alt=""></span>
                                @endif
                                @if($tampon)<span class="tampon {{ $tampon[1] }}">{{ $tampon[0] }}</span>@endif
                            </span>
                            <span class="meta"><span>{{ optional($post->published_at)->format('d.m.Y') }}</span><span>· Équipe {{ site_name() }}</span></span>
                            <h3>{{ $post->title }}</h3>
                            <p>{{ $post->description ?: Str::limit(strip_tags($post->content), 120) }}</p>
                        </a>
                    @endforeach
                </div>
            </div>
        </section>
    @endif

    <section class="section">
        <div class="wrap">
            <div class="tete-section">
                <div><span class="etiquette">Dossiers</span><h2 class="titre">La zone {{ site_name() }}</h2></div>
                <p class="aside">Une survie exigeante, pensée pour les joueurs qui aiment le risque.</p>
            </div>
            <div class="dossiers">
                <div class="dossier coins">
                    <span class="num">01</span>
                    <div class="ic">@include('heraliz.icone', ['nom' => 'crane'])</div>
                    <h3>Survie sans pitié</h3>
                    <p>Zombies, faim et blessures : chaque sortie se prépare.</p>
                    <ul><li>Loot en 7 niveaux, du village à l’aérodrome</li><li>5 boss et des zones contaminées</li><li>Extractions à tenir pour rentrer</li></ul>
                </div>
                <div class="dossier coins">
                    <span class="num">02</span>
                    <div class="ic">@include('heraliz.icone', ['nom' => 'drapeau'])</div>
                    <h3>Clans &amp; territoires</h3>
                    <p>Prends des villes, tiens-les, fais grimper ton clan.</p>
                    <ul><li>3 territoires par clan au plus</li><li>Des hérals versés chaque heure</li><li>Classement des clans</li></ul>
                </div>
                <div class="dossier coins">
                    <span class="num">03</span>
                    <div class="ic">@include('heraliz.icone', ['nom' => 'marteau'])</div>
                    <h3>Bases &amp; raids</h3>
                    <p>Construis, protège, et pille les bases des autres.</p>
                    <ul><li>Construction façon survie, pièce par pièce</li><li>Armoire à outils et terrain protégé</li><li>Raids au C4 seulement</li></ul>
                </div>
            </div>
        </div>
    </section>

    <section class="section">
        <div class="wrap">
            <div class="vitrine-grades coins">
                <div class="txt">
                    <span class="etiquette">Soutenir le serveur</span>
                    <h2 class="titre">Grades de la {{ Str::lower($saison) }}</h2>
                    <p>Ton grade en couleur devant ton pseudo : sur le site, dans le chat et dans la liste des joueurs. 100 % cosmétique : aucun avantage en combat ni en loot.</p>
                    <div class="boutons">
                        @if($boutique)<a class="btn rouge" href="{{ route('shop.home') }}">Voir la boutique @include('heraliz.icone', ['nom' => 'fleche'])</a>@endif
                        @if($votes)<a class="btn os" href="{{ route('vote.home') }}">Voter pour le serveur</a>@endif
                    </div>
                </div>
                <div class="noms-grades">
                    <div style="--c:#E39B5E"><span>Grade 1</span><b>Rescapé</b></div>
                    <div style="--c:#8FD3FF"><span>Grade 2</span><b>Traqueur</b></div>
                    <div style="--c:#FFC23A"><span>Grade 3</span><b>Roi de la Zone</b></div>
                </div>
            </div>
        </div>
    </section>

    <section class="section">
        <div class="wrap">
            <div class="tete-section">
                <div><span class="etiquette">Procédure</span><h2 class="titre">Rejoindre la zone</h2></div>
            </div>
            <div class="procedure">
                <div class="pas actif">
                    <div class="rond">1</div>
                    <h3>Installe le launcher</h3>
                    <p><a href="#telecharger">Télécharge-le</a> pour Windows, Mac ou Linux : il installe le jeu et les mods du serveur tout seul, et les garde à jour.</p>
                </div>
                <div class="pas">
                    <div class="rond">2</div>
                    <h3>Choisis ton pseudo</h3>
                    <p>Dans le launcher, choisis ton pseudo, accepte le règlement et clique sur Jouer.</p>
                </div>
                <div class="pas">
                    <div class="rond">3</div>
                    <h3>Entre dans la zone</h3>
                    <p>En jeu : /register la première fois, /login ensuite. Bonne survie.</p>
                </div>
            </div>
        </div>
    </section>

    <section class="appel">
        @include('heraliz.carte')
        <div class="wrap">
            <div>
                <span class="etiquette">{{ $saison }} en cours</span>
                <h2 class="titre">La zone <span>t’attend.</span></h2>
                <p>{{ $places }} places. Une seule vie à chaque sortie.</p>
            </div>
            <div class="boutons">
                @include('heraliz.telecharger')
                <a class="btn discord grand" href="{{ $discord }}" target="_blank" rel="noopener noreferrer">@include('heraliz.icone', ['nom' => 'discord', 'style' => 'width:22px;height:22px'])Discord</a>
            </div>
        </div>
    </section>
@endsection
