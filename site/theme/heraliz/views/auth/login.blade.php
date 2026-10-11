@extends('layouts.app')

@section('title', trans('auth.login'))

@section('content')
    <div class="page-compte">
        @include('heraliz.compte-gauche', ['etiquette' => 'Retour au camp', 'titre' => 'Bon retour,<br>survivant'])

        <div class="formulaire coins">
            <div class="entete-f">
                <div><span class="etiquette">Accès survivant</span><h2>Connexion</h2></div>
                <div class="ref">{{ site_name() }}<br>{{ theme_config('saison') ?: 'Saison 1' }}</div>
            </div>

            @if(Route::has('register'))
                <div class="bascule">
                    <span class="on">Connexion</span>
                    <a href="{{ route('register') }}">Créer un compte</a>
                </div>
            @endif

            <form method="POST" action="{{ route('login') }}" id="captcha-form">
                @csrf

                <div class="champ">
                    <label for="email">{{ trans('auth.email') }}</label>
                    <div class="saisie">
                        @include('heraliz.icone', ['nom' => 'mail'])
                        <input id="email" type="text" class="form-control @error('email') is-invalid @enderror" name="email" value="{{ old('email') }}" required autocomplete="email" autofocus>
                    </div>
                    @error('email')
                        <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="champ">
                    <label for="password">{{ trans('auth.password') }}</label>
                    <div class="saisie">
                        @include('heraliz.icone', ['nom' => 'cadenas'])
                        <input id="password" type="password" class="form-control @error('password') is-invalid @enderror" name="password" required autocomplete="current-password">
                    </div>
                    @error('password')
                        <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="liens">
                    <div class="form-check m-0">
                        <input class="form-check-input" type="checkbox" name="remember" id="remember" @checked(old('remember'))>
                        <label class="form-check-label" for="remember">{{ trans('auth.remember') }}</label>
                    </div>
                    @if(Route::has('password.request'))
                        <a href="{{ route('password.request') }}">{{ trans('auth.forgot_password') }}</a>
                    @endif
                </div>

                @if($captcha)
                    <div class="captcha">@include('elements.captcha', ['center' => true])</div>
                @endif

                <button type="submit" class="btn rouge grand">{{ trans('auth.login') }} @include('heraliz.icone', ['nom' => 'fleche'])</button>
            </form>

            @if(Route::has('register'))
                <div class="bas-f">Pas encore de compte ? <a href="{{ route('register') }}">Créer un compte</a></div>
            @endif
        </div>
    </div>
@endsection
