@extends('layouts.app')

@section('title', trans('auth.register'))

@section('content')
    <div class="page-compte">
        @include('heraliz.compte-gauche', ['etiquette' => 'Enrôlement', 'titre' => 'Crée ton<br>compte'])

        <div class="formulaire coins">
            <div class="entete-f">
                <div><span class="etiquette">Nouveau survivant</span><h2>Crée ton compte</h2></div>
                <div class="ref">{{ site_name() }}<br>{{ theme_config('saison') ?: 'Saison 1' }}</div>
            </div>

            <div class="bascule">
                <a href="{{ route('login') }}">Connexion</a>
                <span class="on">Créer un compte</span>
            </div>

            <form method="POST" action="{{ route('register') }}" id="captcha-form">
                @csrf

                <div class="champ">
                    <label for="name">Pseudo Minecraft</label>
                    <div class="saisie">
                        @include('heraliz.icone', ['nom' => 'joueur'])
                        <input id="name" type="text" class="form-control @error('name') is-invalid @enderror" name="name" value="{{ old('name') }}" required autocomplete="nickname" autofocus>
                    </div>
                    @error('name')
                        <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="champ">
                    <label for="email">{{ trans('auth.email') }}</label>
                    <div class="saisie">
                        @include('heraliz.icone', ['nom' => 'mail'])
                        <input id="email" type="email" class="form-control @error('email') is-invalid @enderror" name="email" value="{{ old('email') }}" required autocomplete="email">
                    </div>
                    @error('email')
                        <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="deux">
                    <div class="champ">
                        <label for="password">{{ trans('auth.password') }}</label>
                        <div class="saisie">
                            @include('heraliz.icone', ['nom' => 'cadenas'])
                            <input id="password" type="password" class="form-control @error('password') is-invalid @enderror" name="password" required autocomplete="new-password">
                        </div>
                    </div>
                    <div class="champ">
                        <label for="password-confirm">Confirmer</label>
                        <div class="saisie">
                            @include('heraliz.icone', ['nom' => 'cadenas'])
                            <input id="password-confirm" type="password" class="form-control" name="password_confirmation" required autocomplete="new-password">
                        </div>
                    </div>
                </div>
                @error('password')
                    <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                @enderror

                @if($registerConditions !== null)
                    <div class="form-check">
                        <input class="form-check-input @error('conditions') is-invalid @enderror" type="checkbox" name="conditions" id="conditions" required @checked(old('conditions'))>
                        <label class="form-check-label" for="conditions">{{ $registerConditions }}</label>
                        @error('conditions')
                            <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                        @enderror
                    </div>
                @endif

                <div class="captcha">@include('elements.captcha', ['center' => true])</div>

                <button type="submit" class="btn rouge grand">Créer mon compte @include('heraliz.icone', ['nom' => 'fleche'])</button>
            </form>

            <div class="bas-f">Déjà un compte ? <a href="{{ route('login') }}">Se connecter</a></div>
        </div>
    </div>
@endsection
