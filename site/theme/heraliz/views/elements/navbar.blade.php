{{-- En-tête des maquettes : marque, menu (géré dans l'administration : Navigation), Discord et compte --}}
@php
    $discord = theme_config('discord') ?: 'https://discord.gg/heraliz';
@endphp
<nav class="entete navbar navbar-expand-lg" aria-label="Menu principal">
    <a class="marque" href="{{ route('home') }}">
        <img src="{{ theme_asset('img/embleme-80.png') }}" alt="" width="36" height="36">
        <span>HERALI<b>Z</b></span>
    </a>

    <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#menu-principal" aria-controls="menu-principal" aria-expanded="false" aria-label="{{ trans('messages.nav.toggle') }}">
        @include('heraliz.icone', ['nom' => 'menu'])
    </button>

    <div class="collapse navbar-collapse" id="menu-principal">
        <ul class="menu">
            @foreach($navbar as $element)
                @if(! $element->isDropdown())
                    <li>
                        <a class="@if($element->isCurrent()) on @endif" href="{{ $element->getLink() }}" @if($element->new_tab) target="_blank" rel="noopener noreferrer" @endif>{{ $element->name }}</a>
                    </li>
                @else
                    <li class="dropdown">
                        <button type="button" class="dropdown-toggle @if($element->isCurrent()) on @endif" id="menu-deroulant-{{ $element->id }}" data-bs-toggle="dropdown" aria-expanded="false">{{ $element->name }}</button>
                        <div class="dropdown-menu" aria-labelledby="menu-deroulant-{{ $element->id }}">
                            @foreach($element->elements as $enfant)
                                <a class="dropdown-item @if($enfant->isCurrent()) active @endif" href="{{ $enfant->getLink() }}" @if($enfant->new_tab) target="_blank" rel="noopener noreferrer" @endif>{{ $enfant->name }}</a>
                            @endforeach
                        </div>
                    </li>
                @endif
            @endforeach
        </ul>

        <div class="droite">
            <a class="btn discord" href="{{ $discord }}" target="_blank" rel="noopener noreferrer">@include('heraliz.icone', ['nom' => 'discord'])Discord</a>

            @guest
                <a class="btn os" href="{{ route('login') }}">Se connecter</a>
                @if(Route::has('register'))
                    <a class="btn rouge" href="{{ route('register') }}">S’inscrire</a>
                @endif
            @else
                <div class="dropdown notifications-dropdown">
                    <a class="cloche dropdown-toggle" href="#" id="notificationsDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false" title="{{ trans('messages.notifications.notifications') }}">
                        <i class="bi bi-bell"></i>
                        @if(! $notifications->isEmpty())
                            <span class="badge bg-danger" id="notificationsCounter">{{ $notifications->count() }}</span>
                        @endif
                    </a>
                    <div class="dropdown-menu dropdown-menu-end" aria-labelledby="notificationsDropdown">
                        <h6 class="dropdown-header">{{ trans('messages.notifications.notifications') }}</h6>
                        @if(! $notifications->isEmpty())
                            <div id="notifications">
                                @foreach($notifications as $notification)
                                    <a href="{{ $notification->link ? url($notification->link) : '#' }}" class="dropdown-item">
                                        <span class="badge text-bg-{{ $notification->level }}"><i class="bi bi-{{ $notification->icon() }}"></i></span>
                                        <span>
                                            <span class="d-block small text-body-secondary">{{ format_date($notification->created_at, true) }}</span>
                                            {{ $notification->content }}
                                        </span>
                                    </a>
                                @endforeach
                                <a href="{{ route('notifications.read.all') }}" id="readNotifications" class="dropdown-item justify-content-center small">
                                    <span class="d-none spinner-border spinner-border-sm load-spinner" role="status"></span>
                                    {{ trans('messages.notifications.read') }}
                                </a>
                            </div>
                        @endif
                        <a id="noNotificationsLabel" href="{{ route('notifications.index') }}" class="dropdown-item justify-content-center small text-success @if(! $notifications->isEmpty()) d-none @endif">
                            <i class="bi bi-check-lg"></i> {{ trans('messages.notifications.empty') }}
                        </a>
                    </div>
                </div>

                <div class="dropdown">
                    <a id="userDropdown" class="compte dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                        <img class="tete" src="{{ Auth::user()->getAvatar(64) }}" alt="" width="34" height="34">
                        <span>
                            <span class="n d-block">{{ Auth::user()->name }}</span>
                            @if(plugins()->isEnabled('shop'))
                                <span class="c"><span class="piece p"></span>{{ format_money(Auth::user()->money) }}</span>
                            @endif
                        </span>
                    </a>
                    <div class="dropdown-menu dropdown-menu-end" aria-labelledby="userDropdown">
                        <a class="dropdown-item" href="{{ route('profile.index') }}">
                            <i class="bi bi-person"></i> Mon compte
                        </a>
                        @foreach(plugins()->getUserNavItems() ?? [] as $navId => $navItem)
                            <a class="dropdown-item" href="{{ route($navItem['route']) }}">
                                <i class="{{ $navItem['icon'] ?? 'bi bi-three-dots' }}"></i> {{ $navItem['name'] }}
                            </a>
                        @endforeach
                        @if(Auth::user()->hasAdminAccess())
                            <a class="dropdown-item" href="{{ route('admin.dashboard') }}">
                                <i class="bi bi-speedometer2"></i> {{ trans('messages.nav.admin') }}
                            </a>
                        @endif
                        <div class="dropdown-divider"></div>
                        <a class="dropdown-item" href="{{ route('logout') }}" onclick="event.preventDefault(); document.getElementById('logout-form').submit();">
                            <i class="bi bi-box-arrow-right"></i> {{ trans('auth.logout') }}
                        </a>
                        <form id="logout-form" action="{{ route('logout') }}" method="POST" class="d-none">
                            @csrf
                        </form>
                    </div>
                </div>
            @endguest
        </div>
    </div>
</nav>
