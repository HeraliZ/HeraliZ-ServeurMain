@extends('layouts.app')

@section('title', $page->title)
@section('description', $page->description)

@section('content')
    <div class="bandeau">
        <span class="etiquette">{{ $page->slug === 'reglement' ? 'Code des survivants' : 'Informations' }}</span>
        <h1>{{ $page->title }}</h1>
        @if($page->description)
            <p>{{ $page->description }}</p>
        @endif
    </div>

    <div class="coins page-texte">
        {!! $page->content !!}
    </div>
@endsection
