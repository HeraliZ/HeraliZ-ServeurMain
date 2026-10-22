{{-- Pied de page des maquettes ; « Propulsé par Azuriom » est exigé par la licence d'Azuriom (ne pas le retirer) --}}
@php
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
    $pagesInfo = \Azuriom\Models\Page::enabled()->whereIn('slug', ['reglement', 'cgu', 'cgv', 'confidentialite', 'cookies', 'mentions-legales'])->pluck('title', 'slug');
@endphp
<footer class="pied">
    <div class="wrap">
        <div class="grille">
            <div>
                <a class="marque" href="{{ route('home') }}"><img src="{{ theme_asset('img/embleme-80.png') }}" alt="" width="36" height="36"><span>HERALI<b>Z</b></span></a>
                <p class="desc">Serveur Minecraft de survie : loot, zombies, clans, bases et raids. {{ theme_config('saison') ?: 'Saison 1' }} en cours.</p>
                @if(! social_links()->isEmpty())
                    <div class="reseaux">
                        @foreach(social_links() as $lien)
                            <a href="{{ $lien->value }}" title="{{ $lien->title }}" target="_blank" rel="noopener noreferrer"><i class="{{ $lien->icon }}"></i></a>
                        @endforeach
                    </div>
                @endif
            </div>
            <div>
                <h4>Jouer</h4>
                <ul>
                    <li><a href="{{ route('home') }}#telecharger">Télécharger le launcher</a></li>
                    @guest
                        @if(Route::has('register'))<li><a href="{{ route('register') }}">Créer un compte</a></li>@endif
                    @else
                        <li><a href="{{ route('profile.index') }}">Mon compte</a></li>
                    @endguest
                    @if(isset($pagesInfo['reglement']))<li><a href="{{ route('pages.show', 'reglement') }}">Règlement</a></li>@endif
                </ul>
            </div>
            <div>
                <h4>Communauté</h4>
                <ul>
                    <li><a href="{{ $discord }}" target="_blank" rel="noopener noreferrer">Discord</a></li>
                    <li><a href="{{ route('posts.index') }}">Actualités</a></li>
                    @if(plugins()->isEnabled('vote'))<li><a href="{{ route('vote.home') }}">Votes</a></li>@endif
                    @if(plugins()->isEnabled('shop'))<li><a href="{{ route('shop.home') }}">Boutique</a></li>@endif
                </ul>
            </div>
            <div>
                <h4>Informations</h4>
                <ul>
                    @foreach(['cgu' => 'Conditions d’utilisation', 'cgv' => 'Conditions de vente', 'confidentialite' => 'Confidentialité', 'cookies' => 'Cookies', 'mentions-legales' => 'Mentions légales'] as $slug => $nom)
                        @if(isset($pagesInfo[$slug]))<li><a href="{{ route('pages.show', $slug) }}">{{ $nom }}</a></li>@endif
                    @endforeach
                    <li><a href="{{ $discord }}" target="_blank" rel="noopener noreferrer">Contact (Discord)</a></li>
                    {{-- lien demandé par serveurs-minecraft.org pour figurer dans son classement --}}
                    <li><a href="http://www.serveurs-minecraft.org/">Serveur Minecraft</a></li>
                </ul>
            </div>
        </div>
        <div class="bas">
            <span>© {{ date('Y') }} {{ site_name() }} · Tous droits réservés · @lang('messages.copyright')</span>
            <span>{{ site_name() }} n’est pas affilié à Mojang Studios ni à Microsoft.</span>
        </div>
    </div>
</footer>
