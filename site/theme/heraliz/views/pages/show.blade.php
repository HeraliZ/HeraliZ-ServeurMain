@extends('layouts.app')

@section('title', $page->title)
@section('description', $page->description)

{{-- launcher : page à part ; pages légales : menu des autres pages à gauche --}}
@php
    $legales = ['cgv' => 'Conditions de vente', 'cgu' => 'Conditions d’utilisation', 'confidentialite' => 'Confidentialité', 'cookies' => 'Cookies', 'mentions-legales' => 'Mentions légales'];
@endphp

@section('content')
    @if($page->slug === 'launcher')
        @include('heraliz.launcher')
    @elseif(isset($legales[$page->slug]))
        <div class="bandeau"><h1>Infos légales</h1></div>
        <div class="hz-leg">
            <nav aria-label="Pages légales">
                @foreach($legales as $slug => $nom)
                    <a href="{{ route('pages.show', $slug) }}" class="@if($slug === $page->slug) on @endif">{{ $nom }}</a>
                @endforeach
            </nav>
            <article class="page-texte">
                <h2 class="mt-0">{{ $page->title }}</h2>
                {!! $page->content !!}
            </article>
        </div>
    @else
        <div class="bandeau">
            <h1>{{ $page->title }}</h1>
            @if($page->description)<p>{{ $page->description }}</p>@endif
        </div>
        <div class="page-texte">{!! $page->content !!}</div>
    @endif
@endsection
