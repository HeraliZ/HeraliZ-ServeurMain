@extends('layouts.base')

@section('app')
    <div class="page-interieure">

        <main class="contenu wrap">
            <div class="alerts">
                @include('elements.session-alerts')
            </div>

            @yield('content')
        </main>
    </div>
@endsection
