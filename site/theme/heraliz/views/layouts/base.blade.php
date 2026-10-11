<!DOCTYPE html>
@include('elements.base')
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}" data-bs-theme="dark">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="@yield('description', setting('description', ''))">
    <meta name="theme-color" content="#0a0b09">
    <meta name="author" content="{{ site_name() }}">

    <meta property="og:title" content="@yield('title')">
    <meta property="og:type" content="@yield('type', 'website')">
    <meta property="og:url" content="{{ url()->current() }}">
    <meta property="og:image" content="{{ setting('icon') ? favicon() : theme_asset('img/embleme-192.png') }}">
    <meta property="og:description" content="@yield('description', setting('description', ''))">
    <meta property="og:site_name" content="{{ site_name() }}">
    @stack('meta')

    <meta name="csrf-token" content="{{ csrf_token() }}">

    {{-- fichiers du thème : la date du fichier dans l'adresse, pour que les navigateurs prennent la nouvelle version --}}
    @php
        $fichierTheme = fn (string $f) => theme_asset($f.'?v'.(@filemtime(themes()->path('assets/'.$f)) ?: '1'));
    @endphp

    <title>@yield('title') | {{ site_name() }}</title>

    @if(setting('icon'))
        <link rel="shortcut icon" href="{{ favicon() }}">
    @else
        <link rel="icon" type="image/png" sizes="32x32" href="{{ theme_asset('img/favicon-32.png') }}">
        <link rel="apple-touch-icon" href="{{ theme_asset('img/embleme-192.png') }}">
    @endif

    <link rel="preload" href="{{ theme_asset('fonts/oswald-700.woff2') }}" as="font" type="font/woff2" crossorigin>
    <link rel="preload" href="{{ theme_asset('fonts/dm-sans-500.woff2') }}" as="font" type="font/woff2" crossorigin>

    <script src="{{ asset('vendor/bootstrap/js/bootstrap.bundle.min.js') }}" defer></script>
    <script src="{{ asset('vendor/axios/axios.min.js') }}" defer></script>
    <script src="{{ asset('js/script.js') }}" defer></script>
    <script src="{{ $fichierTheme('js/heraliz.js') }}" defer></script>

    @stack('scripts')

    <link href="{{ asset('vendor/bootstrap-icons/bootstrap-icons.css') }}" rel="stylesheet">
    <link href="{{ asset('vendor/bootstrap/css/bootstrap.min.css') }}" rel="stylesheet">
    <link href="{{ $fichierTheme('css/heraliz.css') }}" rel="stylesheet">
    @stack('styles')
</head>

<body class="heraliz @yield('classe-page')" data-bs-theme="dark">
<div class="site" id="app">
    @include('heraliz.etat')

    @include('elements.navbar')

    @yield('app')
</div>

@include('heraliz.pied')

<div class="grain-page" aria-hidden="true"></div>

@stack('footer-scripts')

</body>
</html>
