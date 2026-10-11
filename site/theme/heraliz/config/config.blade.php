@extends('admin.layouts.admin')

@section('title', 'Thème HeraliZ')

@section('content')
    <div class="card shadow mb-4">
        <div class="card-body">
            <p class="text-body-secondary">Réglages du thème HeraliZ. Les textes de l’accueil et l’apparence se changent dans le dépôt du serveur (dossier <code>site/theme/heraliz</code>).</p>

            <form action="{{ route('admin.themes.config', $theme) }}" method="POST">
                @csrf

                <div class="row g-3">
                    <div class="col-md-6">
                        <label class="form-label" for="discord">Lien du Discord</label>
                        <input type="url" class="form-control @error('discord') is-invalid @enderror" id="discord" name="discord" value="{{ old('discord', theme_config('discord')) }}" placeholder="https://discord.gg/heraliz">
                        @error('discord')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>

                    <div class="col-md-6">
                        <label class="form-label" for="launcher">Lien de téléchargement du launcher</label>
                        <input type="url" class="form-control @error('launcher') is-invalid @enderror" id="launcher" name="launcher" value="{{ old('launcher', theme_config('launcher')) }}" placeholder="https://heraliz.fr/telecharger/HeraliZ-Launcher-Setup.exe">
                        @error('launcher')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>

                    <div class="col-md-4">
                        <label class="form-label" for="saison">Saison</label>
                        <input type="text" class="form-control @error('saison') is-invalid @enderror" id="saison" name="saison" value="{{ old('saison', theme_config('saison')) }}" placeholder="Saison 1">
                        @error('saison')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>

                    <div class="col-md-4">
                        <label class="form-label" for="version">Version</label>
                        <input type="text" class="form-control @error('version') is-invalid @enderror" id="version" name="version" value="{{ old('version', theme_config('version')) }}" placeholder="Version 1">
                        @error('version')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>

                    <div class="col-md-4">
                        <label class="form-label" for="saison_debut">Début de la saison</label>
                        <input type="date" class="form-control @error('saison_debut') is-invalid @enderror" id="saison_debut" name="saison_debut" value="{{ old('saison_debut', theme_config('saison_debut')) }}">
                        <div class="form-text">Rempli : le haut du site affiche « jour 1, jour 2... » depuis cette date.</div>
                        @error('saison_debut')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>

                    <div class="col-md-4">
                        <label class="form-label" for="places">Places sur le serveur</label>
                        <input type="number" min="1" max="10000" class="form-control @error('places') is-invalid @enderror" id="places" name="places" value="{{ old('places', theme_config('places')) }}">
                        <div class="form-text">Affiché quand le site ne peut pas interroger le serveur.</div>
                        @error('places')<span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>@enderror
                    </div>
                </div>

                <button type="submit" class="btn btn-primary mt-4">
                    <i class="bi bi-save"></i> {{ trans('messages.actions.save') }}
                </button>
            </form>
        </div>
    </div>
@endsection
