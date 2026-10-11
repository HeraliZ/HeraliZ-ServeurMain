@extends('layouts.app')

@section('title', trans('messages.posts.posts'))

@section('content')
    <div class="bandeau">
        <span class="etiquette">Rapports de terrain</span>
        <h1>Actualités</h1>
        <p>Les nouvelles de la zone, directement de l’équipe {{ site_name() }}.</p>
    </div>

    <form class="mb-4" action="{{ route('posts.index') }}" method="GET" role="search" style="max-width: 360px">
        <label class="visually-hidden" for="searchInput">{{ trans('messages.actions.search') }}</label>
        <div class="input-group">
            <input type="search" class="form-control" id="searchInput" name="q" value="{{ $search ?? '' }}" placeholder="{{ trans('messages.actions.search') }}">
            <button type="submit" class="btn btn-primary" title="{{ trans('messages.actions.search') }}"><i class="bi bi-search"></i></button>
        </div>
    </form>

    @if($posts->isEmpty())
        <div class="coins vide-panneau">
            <h2>Aucun rapport</h2>
            <p>Rien à signaler pour l’instant.</p>
        </div>
    @else
        <div class="rapports uniforme">
            @foreach($posts as $post)
                <a class="rapport mb-4" href="{{ route('posts.show', $post) }}">
                    <span class="scotch"></span>
                    <span class="photo d-block">
                        @if($post->hasImage())
                            <img src="{{ $post->imageUrl() }}" alt="" loading="lazy">
                        @else
                            <span class="vide"><img src="{{ theme_asset('img/embleme-192.png') }}" alt=""></span>
                        @endif
                        @if($post->is_pinned)<span class="tampon laiton">À la une</span>@endif
                    </span>
                    <span class="meta"><span>{{ optional($post->published_at)->format('d.m.Y') }}</span><span>· Équipe {{ site_name() }}</span></span>
                    <h3>{{ $post->title }}</h3>
                    <p>{{ $post->description ?: Str::limit(strip_tags($post->content), 140) }}</p>
                </a>
            @endforeach
        </div>
    @endif
@endsection
