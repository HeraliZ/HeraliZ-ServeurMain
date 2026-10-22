{{-- Boutons de téléchargement du launcher, un par système : liens fixes de heraliz.fr/telecharger, mis en ligne par le
     workflow « Launcher HeraliZ » (case « publier »). Le réglage « launcher » du thème remplace le lien Windows. --}}
@php
    $base = 'https://heraliz.fr/telecharger/';
    $systemes = [
        ['windows', 'Windows', theme_config('launcher') ?: $base.'HeraliZ-Launcher-Setup.exe'],
        ['apple', 'Mac', $base.'HeraliZ-Launcher.dmg'],
        ['linux', 'Linux', $base.'HeraliZ-Launcher.AppImage'],
    ];
@endphp
@if(setting('heraliz.telechargement', 'ouvert') === 'ferme')
    {{-- téléchargement fermé par theme.sh (TELECHARGEMENT=ferme) : aucun lien vers l'installateur --}}
    <span class="btn rouge grand disabled" aria-disabled="true">Bientôt disponible<small>Le launcher arrive à l'ouverture du serveur</small></span>
@else
    @foreach($systemes as [$icone, $nom, $lien])
        <a class="btn rouge grand" href="{{ $lien }}">@include('heraliz.icone', ['nom' => $icone]){{ $nom }}<small>Télécharger</small></a>
    @endforeach
@endif
