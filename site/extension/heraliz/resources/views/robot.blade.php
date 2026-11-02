{{-- Anti-robot de la création de compte depuis le launcher (RobotController) : page seule, sans le thème, ouverte dans
     une petite fenêtre du launcher. Turnstile passé : départ vers /heraliz/robot/fait#<réponse>, intercepté par le launcher. --}}
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex">
    <title>Vérification anti-robot · HeraliZ</title>
    @if($cle)
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
    @endif
    <style>
        html, body { margin: 0; height: 100%; background: #06070a; color: #e6e1d3; font: 15px/1.5 system-ui, -apple-system, 'Segoe UI', sans-serif; }
        main { min-height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; padding: 24px; box-sizing: border-box; text-align: center; }
        h1 { margin: 0; font-size: 20px; letter-spacing: .04em; text-transform: uppercase; }
        p { margin: 0; max-width: 340px; color: #b9b3a3; }
        .cf-turnstile { min-height: 65px; }
    </style>
</head>
<body>
<main>
    @if($fini ?? false)
        <h1>Vérification faite</h1>
        <p>Tu peux fermer cette fenêtre et revenir au launcher HeraliZ.</p>
    @elseif($cle)
        <h1>Vérification anti-robot</h1>
        <p>Une seconde : on vérifie que c’est bien toi qui crées ton compte HeraliZ.</p>
        <div class="cf-turnstile" data-sitekey="{{ $cle }}" data-callback="heralizRobotOk" data-theme="dark" data-language="fr"></div>
        <script>
            function heralizRobotOk(reponse) {
                window.location.href = @json(route('heraliz.robot.fait')) + '#' + encodeURIComponent(reponse);
            }
        </script>
    @else
        <h1>Pas de vérification</h1>
        <p>L’anti-robot n’est pas réglé sur le site : ferme cette fenêtre et crée ton compte dans le launcher.</p>
    @endif
</main>
</body>
</html>
