{{-- Lien Discord du compte HeraliZ (Discord obligatoire pendant la bêta) : au style des pages Connexion / Inscription du thème --}}
@extends('layouts.app')

@section('title', 'Discord')

@section('content')
    <div class="page-compte" style="justify-content: center;">
        <div class="formulaire coins">
            <div class="entete-f">
                <div><span class="etiquette">Compte {{ site_name() }}</span><h2>{{ $titre }}</h2></div>
                <div class="ref">Discord<br>Bêta</div>
            </div>

            <p class="intro">{{ $texte }}</p>

            @if($pseudo)
                <p class="intro">
                    Compte du site : <strong>{{ $pseudo }}</strong>
                    @if($discord)
                        <br>Discord lié : <strong>{{ $discord }}</strong>
                    @endif
                </p>
            @endif

            @foreach($boutons as [$texteBouton, $adresse, $style])
                <a class="btn {{ $style }} w-100 mt-3" href="{{ $adresse }}" @if(! str_starts_with($adresse, url('/'))) target="_blank" rel="noopener noreferrer" @endif>{{ $texteBouton }}</a>
            @endforeach
        </div>
    </div>
@endsection
