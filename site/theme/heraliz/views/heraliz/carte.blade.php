{{-- Fond « carte tactique » : courbes de niveau dessinées par heraliz.js ; annotations au feutre sur l'accueil --}}
<div class="carte-tac {{ $classe ?? '' }}" aria-hidden="true">
    <canvas data-topo></canvas>
    <div class="teinte"></div>
    @if($notes ?? false)
        <svg class="notes" viewBox="0 0 1440 760" preserveAspectRatio="xMaxYMid slice">
            <defs><pattern id="hachures" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="3" height="8" fill="#C8B24A" opacity=".35"/></pattern></defs>
            <path d="M1150 610 C1110 560 1090 520 1120 470 C1150 430 1215 440 1240 480 C1262 518 1240 570 1205 600 Z" fill="url(#hachures)" stroke="#C8B24A" stroke-opacity=".55" stroke-width="1.2" stroke-dasharray="4 4"/>
            <text x="1128" y="640" class="lbl" fill="#D8C46A" opacity=".85">ZONE CONTAMINÉE</text>
            <path d="M690 760 C760 690 820 640 900 600 C960 570 1000 520 1030 440 C1045 400 1050 360 1052 330" fill="none" stroke="#E23B2D" stroke-width="2.4" stroke-dasharray="10 8" opacity=".85"/>
            <path d="M1052 330 m-12 -8 l12 -14 l10 16" fill="none" stroke="#E23B2D" stroke-width="2.4" opacity=".85"/>
            <path d="M1003 318 C1006 268 1058 250 1092 268 C1128 288 1124 340 1088 360 C1052 378 1000 365 1003 318 Z" fill="none" stroke="#E23B2D" stroke-width="2.6" opacity=".9"/>
            <circle cx="1050" cy="320" r="6" fill="#FF3B2F"/><circle cx="1050" cy="320" r="16" fill="none" stroke="#FF3B2F" stroke-opacity=".5"/>
            <text x="1104" y="300" class="lbl" fill="#FFB3AC">ZONE HERALIZ</text>
            <rect x="1258" y="208" width="12" height="12" fill="none" stroke="#E6E1D3" stroke-width="1.6"/><text x="1280" y="219" class="lbl" fill="#E6E1D3" opacity=".8">AÉRODROME · T6</text>
            <path d="M848 228 l9 -15 l9 15 Z" fill="none" stroke="#9BD07F" stroke-width="1.8"/><text x="874" y="227" class="lbl" fill="#9BD07F" opacity=".85">EXTRACTION 02</text>
            <path d="M1300 420 l14 14 M1314 420 l-14 14" stroke="#E23B2D" stroke-width="2.6"/><text x="1324" y="433" class="lbl" fill="#FFB3AC" opacity=".85">BASE ENNEMIE</text>
            <circle cx="940" cy="470" r="5" fill="none" stroke="#E6E1D3" stroke-width="1.6"/><text x="954" y="474" class="lbl" fill="#E6E1D3" opacity=".7">VILLE · T4</text>
        </svg>
    @endif
    <div class="voile"></div>
</div>
