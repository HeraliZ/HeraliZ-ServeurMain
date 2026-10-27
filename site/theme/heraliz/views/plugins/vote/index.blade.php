@extends('layouts.app')

@section('title', trans('vote::messages.title'))

{{-- Page des votes (maquette « Vote pour HeraliZ ») ; les attributs data-vote-*, #vote-card, #voteNameForm, #vote-goal
     et #server-select sont ceux qu'utilise le script de l'extension Vote : ne pas les renommer. --}}
@php
    $gains = $sites->flatMap(fn ($site) => $site->rewards->pluck('money'))->filter(fn ($m) => $m > 0);
    $gainCommun = $gains->isNotEmpty() && $gains->unique()->count() === 1 && $sites->every(fn ($site) => $site->rewards->isNotEmpty()) ? $gains->first() : null;
    $podium = collect([2, 1, 3])->map(fn ($rang) => $votes->get($rang));
    // votes protégés (extension HeraliZ, Votes\Garde) : ce qui empêche ce compte de voter, montré AVANT le clic, pour
    // qu'un joueur ne gâche pas son vote sur le site de vote pour rien
    $Garde = \Azuriom\Plugin\Heraliz\Votes\Garde::class;
    $gardeActive = $user !== null && class_exists($Garde);
    $garde = $gardeActive ? rescue(fn () => $Garde::compte($user, $request), null, false) : null;
@endphp

@section('content')
    <div class="bandeau">
        <span class="etiquette">Soutien</span>
        <h1>Vote pour {{ site_name() }}</h1>
        <p>Chaque vote fait monter le serveur dans les classements{{ $gains->isNotEmpty() ? ' et te rapporte des '.money_name().', livrés sur ton compte' : '' }}.</p>
    </div>

    <div class="votes">
        <div>
            <div class="recompense coins">
                @if($gainCommun !== null)
                    <div class="gros"><span class="piece"></span>+{{ $gainCommun }}</div>
                    <p><b>{{ money_name($gainCommun) }} par vote</b><br>Sur chaque site, dès que son délai est passé.</p>
                @else
                    <div class="gros">@include('heraliz.icone', ['nom' => 'cible', 'style' => 'width:38px;height:38px'])</div>
                    <p><b>Un vote par site</b><br>Sur chaque site, dès que son délai est passé.</p>
                @endif

                @if($goalEnabled)
                    <div class="jauge-vote" id="vote-goal">
                        <div class="l"><span>{{ trans('vote::messages.sections.goal') }}</span><b class="text-white">{{ $goalProgress }} / {{ $goalTarget }}</b></div>
                        <div class="progress"><div class="progress-bar" role="progressbar" style="width: {{ min($goalPercentage, 100) }}%" aria-valuenow="{{ $goalProgress }}" aria-valuemin="0" aria-valuemax="{{ $goalTarget }}"></div></div>
                        <p class="small mt-2 mb-0" id="goal-text">{{ trans('vote::messages.goal', ['current' => $goalProgress, 'target' => $goalTarget]) }}</p>
                    </div>
                @elseif($userVotes >= 0)
                    <div class="jauge-vote">
                        <div class="l"><span>Tes votes ce mois-ci</span><b class="text-white">{{ $userVotes }}</b></div>
                    </div>
                @endif
            </div>

            <div id="vote-card">
                <div class="spinner-parent"><div class="spinner-border text-white" role="status"></div></div>

                <div class="@auth d-none @endauth" data-vote-step="1">
                    <div class="etape-vote coins mt-3">
                        @if(! $authRequired)
                            <h2 class="h4 text-uppercase">Ton pseudo</h2>
                            <p class="texte-mute mb-0">Entre ton pseudo de jeu pour voter (ou connecte-toi).</p>
                            <form action="{{ route('vote.verify-user', '/') }}" id="voteNameForm">
                                <div class="champ">
                                    <div class="saisie">
                                        @include('heraliz.icone', ['nom' => 'joueur'])
                                        <input type="text" id="stepNameInput" name="name" class="form-control" value="{{ $name }}" placeholder="{{ trans('messages.fields.name') }}" required>
                                    </div>
                                </div>
                                <button type="submit" class="btn rouge mt-3">
                                    {{ trans('messages.actions.continue') }}
                                    <span class="d-none spinner-border spinner-border-sm load-spinner" role="status"></span>
                                </button>
                            </form>
                        @else
                            {{-- #voteNameForm toujours présent, comme dans la page d'origine : après un vote, le script de
                                 l'extension Vote y lit l'adresse des délais (sinon il s'arrête sur une erreur) --}}
                            <form action="{{ route('vote.verify-user', '/') }}" id="voteNameForm">
                                <h2 class="h4 text-uppercase">Connecte-toi pour voter</h2>
                                <p class="texte-mute">Les récompenses arrivent sur ton compte du site.</p>
                                <a href="{{ route('login') }}" class="btn rouge">{{ trans('auth.login') }}</a>
                            </form>
                        @endif
                    </div>
                </div>

                <div class="@guest d-none @endguest" data-vote-step="2">
                    @if($garde !== null)
                        <div class="etape-vote coins mt-3 garde-vote">
                            <h2 class="h4 text-uppercase">Avant de voter</h2>
                            <p class="texte-mute mb-0">{{ $garde['message'] }}</p>
                            @if($garde['lien'] !== null)
                                <a href="{{ $garde['lien'] }}" class="btn rouge mt-3" @if(! str_starts_with($garde['lien'], url('/'))) target="_blank" rel="noopener noreferrer" @endif>{{ $garde['bouton'] }}</a>
                            @endif
                        </div>
                    @endif
                    @forelse($sites as $site)
                        @php
                            $gainSite = $site->rewards->pluck('money')->filter(fn ($m) => $m > 0);
                            $delai = (int) $site->vote_delay;
                            $prochain = $user !== null ? $site->getNextVoteTime($user, $request) : null;
                            // connexion déjà récompensée pour un autre compte
                            $raisonSite = $gardeActive && $garde === null && $prochain === null
                                ? rescue(fn () => $Garde::pourSite($site, $user, $request), null, false) : null;
                            $bloque = $garde !== null || $raisonSite !== null;
                            // site sans vérification possible (Top-Serveurs sans jeton) : clic « Voter » noté par le site,
                            // anti-robot compris (Votes\Clic)
                            $avecClic = $gardeActive && ! rescue(fn () => $Garde::verifiable($site), false, false);
                            $clics = ($clics ?? false) || ($avecClic && ! $bloque);
                        @endphp
                        <div class="mission coins">
                            <span class="num">{{ str_pad($loop->iteration, 2, '0', STR_PAD_LEFT) }}</span>
                            <div>
                                <div class="t">{{ $site->name }}</div>
                                @if($delai > 0)
                                    <div class="d">Toutes les {{ $delai >= 60 ? intdiv($delai, 60).' h'.($delai % 60 ? ' '.str_pad($delai % 60, 2, '0', STR_PAD_LEFT) : '') : $delai.' min' }}</div>
                                @endif
                                @if($raisonSite !== null)
                                    <div class="d raison">{{ $raisonSite }}</div>
                                @endif
                            </div>
                            <div class="r">
                                @if($gainSite->isNotEmpty())
                                    <span class="gain"><span class="piece p"></span>@if($gainSite->unique()->count() > 1)jusqu’à @endif+{{ $gainSite->max() }}</span>
                                @endif
                                @if($bloque)
                                    {{-- sans data-vote-url : le script de l'extension Vote n'y touche pas, et le site de vote ne s'ouvre pas --}}
                                    <span class="btn rouge disabled" aria-disabled="true"><span class="lab-attente">Indisponible</span></span>
                                @else
                                    <a class="btn rouge" href="{{ $site->url }}" target="_blank" rel="noopener noreferrer"
                                       data-vote-id="{{ $site->id }}"
                                       data-vote-url="{{ route('vote.vote', $site) }}"
                                       @if($avecClic) data-heraliz-clic="{{ route('heraliz.vote.clic', $site) }}" @endif
                                       @auth data-vote-time="{{ $prochain?->valueOf() }}" @endauth>
                                        <span class="lab-voter">Voter</span><span class="lab-attente">Prochain vote</span><span class="vote-timer"></span>
                                    </a>
                                @endif
                            </div>
                        </div>
                    @empty
                        <div class="coins vide-panneau mt-3">
                            <div class="pastille">@include('heraliz.icone', ['nom' => 'cible'])</div>
                            <h2>Les votes arrivent bientôt</h2>
                            <p>{{ trans('vote::messages.errors.site') }}</p>
                        </div>
                    @endforelse
                    @php
                        $turnstile = ($clics ?? false) ? rescue(fn () => \Azuriom\Plugin\Heraliz\Votes\Clic::turnstile(), null, false) : null;
                    @endphp
                    @if($turnstile !== null)
                        {{-- anti-robot de Cloudflare : invisible tant qu'il ne demande rien au joueur --}}
                        <div id="heraliz-turnstile" class="turnstile-vote" data-sitekey="{{ $turnstile }}"></div>
                    @endif
                    @if($gardeActive && $sites->isNotEmpty())
                        <p class="note-vote">Clique sur « Voter » ici, vote sur le site qui s’ouvre, puis reviens sur cette page : la récompense arrive toute seule. Vote depuis la même connexion internet que cette page (pas en 4G si cette page est en Wi-Fi). Une récompense par connexion internet et par site.</p>
                    @endif
                </div>

                <div class="d-none" data-vote-step="3">
                    <div class="etape-vote coins mt-3"><p id="vote-result" class="mb-0"></p></div>
                </div>

                <div class="d-none" data-vote-step="server">
                    <div class="etape-vote coins mt-3">
                        <p class="mb-0">{{ trans('vote::messages.server') }}</p>
                        <div id="server-select"></div>
                    </div>
                </div>
            </div>
        </div>

        <aside class="honneur coins">
            <span class="etiquette">{{ ucfirst(now()->translatedFormat('F Y')) }}</span>
            <h3>Tableau d’honneur</h3>
            <p class="st">Les meilleurs votants du mois.</p>

            <div class="podium">
                @foreach($podium as $i => $place)
                    @php
                        $rang = [2, 1, 3][$i];
                    @endphp
                    <div class="{{ ['argent', 'or', 'bronze'][$i] }} @if($place === null || $place->user === null) libre @endif">
                        <div class="rang">{{ $rang }}</div>
                        @if($place !== null && $place->user !== null)
                            <div class="n">{{ $place->user->name }}</div>
                            <div class="nb">{{ $place->votes }} {{ $place->votes > 1 ? 'votes' : 'vote' }}</div>
                        @else
                            <div class="n">—</div>
                            <div class="nb">Place libre</div>
                        @endif
                    </div>
                @endforeach
            </div>

            <div class="classement">
                @foreach($votes as $position => $vote)
                    @continue($position <= 3 || $vote->user === null)
                    <div class="ligne-rang @if($user !== null && $vote->user->is($user)) moi @endif">
                        <span class="p">{{ $position }}</span>
                        <span class="nom">{{ $vote->user->name }}</span>
                        <span class="nb">{{ $vote->votes }}</span>
                    </div>
                @endforeach
            </div>

            @if($userVotes >= 0)
                <p class="mes-votes">Tu as voté {{ $userVotes }} fois ce mois-ci.</p>
            @endif
        </aside>
    </div>

    @if($displayRewards && ! $rewards->isEmpty())
        <div class="card recompenses-vote">
            <div class="card-header">{{ trans('vote::messages.sections.rewards') }}</div>
            <div class="table-responsive">
                <table class="table mb-0">
                    <thead><tr><th scope="col">{{ trans('messages.fields.name') }}</th><th scope="col">{{ trans('vote::messages.fields.chances') }}</th></tr></thead>
                    <tbody>
                        @foreach($rewards as $reward)
                            <tr>
                                <td>
                                    @if($reward->image)<img src="{{ $reward->imageUrl() }}" alt="" width="32" class="me-2">@endif
                                    {{ $reward->name }}
                                </td>
                                <td>{{ $reward->chances }} %</td>
                            </tr>
                        @endforeach
                    </tbody>
                </table>
            </div>
        </div>
    @endif
@endsection

@push('scripts')
    @include('vote::partials._scripts')
    @if($clics ?? false)
        <script>
            // Votes protégés (extension HeraliZ, Votes\Clic) : pour un site de vote sans vérification, le clic sur « Voter »
            // est d'abord noté par le site (anti-robot Turnstile s'il est réglé), puis le site de vote s'ouvre et la page
            // demande la récompense comme d'habitude (fonctions de vote.js de l'extension Vote).
            (function () {
                let jeton = null;
                let widget = null;

                function preparer() {
                    const boite = document.getElementById('heraliz-turnstile');
                    if (!boite || !window.turnstile || widget !== null) {
                        return;
                    }
                    widget = window.turnstile.render(boite, {
                        sitekey: boite.dataset.sitekey,
                        theme: 'dark',
                        appearance: 'interaction-only',
                        'refresh-expired': 'auto',
                        callback: function (t) { jeton = t; },
                        'expired-callback': function () { jeton = null; },
                        'error-callback': function () { jeton = null; },
                    });
                }

                window.heralizTurnstile = function () {
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', preparer);
                    } else {
                        preparer();
                    }
                };

                document.addEventListener('DOMContentLoaded', function () {
                    const boite = document.getElementById('heraliz-turnstile');

                    document.querySelectorAll('[data-heraliz-clic]').forEach(function (lien) {
                        const surClic = function (ev) {
                            if (ev.type === 'auxclick' && ev.button !== 1) {
                                return;
                            }
                            // délai pas fini, ou script de l'extension Vote absent : l'extension Vote s'en occupe
                            if (lien.classList.contains('disabled') || typeof refreshVote !== 'function') {
                                return;
                            }
                            ev.preventDefault();
                            ev.stopImmediatePropagation();

                            if (boite && !jeton) {
                                displayVoteAlert('Vérification anti-robot en cours : réessaie dans une seconde.', 'warning');
                                return;
                            }

                            clearVoteAlert();
                            window.open(lien.href, '_blank', 'noopener');
                            lien.classList.add('disabled');
                            document.getElementById('vote-card').classList.add('voting');

                            const corps = boite ? {'cf-turnstile-response': jeton} : {};
                            jeton = null;
                            axios.post(lien.dataset.heralizClic, corps)
                                .then(function () {
                                    refreshVote(lien.dataset.voteUrl);
                                })
                                .catch(function (erreur) {
                                    document.getElementById('vote-card').classList.remove('voting');
                                    lien.classList.remove('disabled');
                                    catchVoteError(erreur);
                                })
                                .finally(function () {
                                    if (widget !== null) {
                                        window.turnstile.reset(widget);
                                    }
                                });
                        };

                        lien.addEventListener('click', surClic, true);
                        lien.addEventListener('auxclick', surClic, true);
                    });
                });
            })();
        </script>
        @if($turnstile ?? null)
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=heralizTurnstile" async defer></script>
        @endif
    @endif
@endpush
