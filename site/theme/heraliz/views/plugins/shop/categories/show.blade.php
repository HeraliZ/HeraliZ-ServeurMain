@extends('layouts.app')

@section('title', $category->name)

@section('content')
    @include('heraliz.boutique-entete')

    @if($category->packages->isEmpty())
        <div class="hz-carte">{{ trans('shop::messages.categories.empty') }}</div>
    @else
        @include('heraliz.lots-pb', ['packages' => $category->packages])
    @endif
@endsection
