{{-- Page « Launcher » (page du site au lien launcher, maquette v4) : un bouton par système, l'avertissement de Windows
     en images, les étapes et les liens utiles. Liens fixes de heraliz.fr/telecharger (workflow « Launcher HeraliZ »). --}}
@php
    $base = 'https://heraliz.fr/telecharger/';
    $ferme = setting('heraliz.telechargement', 'ouvert') === 'ferme';
    $systemes = [
        ['win', 'windows', 'Windows', 'Windows 10 et 11 · .exe', theme_config('launcher') ?: $base.'HeraliZ-Launcher-Setup.exe'],
        ['mac', 'apple', 'macOS', 'Apple Silicon et Intel · .dmg', $base.'HeraliZ-Launcher.dmg'],
        ['lin', 'linux', 'Linux', 'AppImage · ou le paquet .deb', $base.'HeraliZ-Launcher.AppImage'],
    ];
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
@endphp
<div class="bandeau">
    <h1>Launcher</h1>
    <p>Le seul moyen de jouer sur {{ site_name() }} : il installe le jeu et les mods, et les garde à jour tout seul.</p>
</div>

<div class="hz-os">
    @foreach($systemes as [$classe, $icone, $nom, $detail, $lien])
        @if($ferme)
            <div class="hz-osc {{ $classe }} off">@include('heraliz.icone', ['nom' => $icone])<b>{{ $nom }}</b><span>{{ $detail }}</span><em>Bientôt disponible</em></div>
        @else
            <a class="hz-osc {{ $classe }}" href="{{ $lien }}">@include('heraliz.icone', ['nom' => $icone])<b>{{ $nom }}</b><span>{{ $detail }}</span><em>Télécharger</em></a>
        @endif
    @endforeach
</div>
@unless($ferme)<p class="hz-note">Linux : aussi en <a href="{{ $base }}HeraliZ-Launcher.deb">paquet .deb</a>. Mac : au premier lancement, clic droit sur l'application puis « Ouvrir ».</p>@endunless

<h2 class="hz-sec">Windows bloque le launcher ?</h2>
<p class="hz-note">C'est normal : le launcher n'est pas encore signé (un certificat coûte cher). Il ne contient rien de dangereux. Deux clics suffisent :</p>
<div class="hz-ss">
    <figure>
        <div class="img"><img src="{{ theme_asset('img/smartscreen-1.webp') }}" alt="Fenêtre « Windows a protégé votre ordinateur »" loading="lazy" width="531" height="494"><span class="fl" style="left:4%;top:24%">1</span></div>
        <figcaption><b>1.</b> Clique sur <b>« Informations complémentaires »</b>.</figcaption>
    </figure>
    <div class="fleche" aria-hidden="true">@include('heraliz.icone', ['nom' => 'fleche'])</div>
    <figure>
        <div class="img"><img src="{{ theme_asset('img/smartscreen-2.webp') }}" alt="Bouton « Exécuter quand même »" loading="lazy" width="525" height="490"><span class="fl" style="left:30%;bottom:6%">2</span></div>
        <figcaption><b>2.</b> Clique sur <b>« Exécuter quand même »</b>. L'installation démarre.</figcaption>
    </figure>
</div>

<div class="hz-trois mt-4">
    <div class="hz-carte"><small>1</small><h3>Installe</h3><p>Lance le fichier téléchargé et suis l'installation.</p></div>
    <div class="hz-carte"><small>2</small><h3>Connecte-toi</h3><p>Avec ton compte {{ site_name() }}, ou crée-le directement dans le launcher.</p></div>
    <div class="hz-carte"><small>3</small><h3>Joue</h3><p>Le launcher télécharge le jeu et les mods, puis te connecte à play.heraliz.fr.</p></div>
</div>

<h2 class="hz-sec">Liens utiles</h2>
<ul class="hz-liens">
    <li><a href="{{ route('pages.show', 'reglement') }}">Règlement</a></li>
    <li><a href="{{ $discord }}" target="_blank" rel="noopener noreferrer">Support sur Discord</a></li>
</ul>
