package com.heraliz.extraction.client;

import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.client.TerminalModel.ScreenLine;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Contenu affiché sur l'écran du terminal. */
public final class TerminalScreen {
    private static final int GREEN = 0x39FF6A;
    private static final int DIM = 0x1E8A3C;
    private static final int AMBER = 0xFFB030;
    private static final int RED = 0xFF4040;

    private static final String HEADER = "HERALIZ-OS v0.3";
    /** Nombre de lignes visibles sur l'écran (voir TerminalModel.TEXT_SCALE). */
    private static final int MAX_LINES = 8;

    private TerminalScreen() {
    }

    private static boolean blink(float time) {
        return (time % 12.0F) < 6.0F;
    }

    /** Écran au repos (inventaire, en main). */
    public static List<ScreenLine> idle(float time) {
        List<ScreenLine> lines = new ArrayList<>();
        lines.add(new ScreenLine(HEADER, DIM));
        lines.add(new ScreenLine(I18n.get("heraliz_extraction.ecran.relais"), DIM));
        lines.add(new ScreenLine(blink(time) ? "> _" : ">", GREEN));
        return lines;
    }

    /**
     * Pendant que le joueur tape la séquence de lancement. L'écran se comporte comme un terminal :
     * les lignes s'ajoutent en bas et les plus anciennes défilent vers le haut.
     *
     * @param error raison de l'échec (toit, pas au sol), ou null. En cas d'erreur, tant que le joueur
     *              continue de taper, l'écran enchaîne "nouvel essai..." / "ACCÈS REFUSÉ".
     */
    public static List<ScreenLine> typing(float useTicks, float time, @Nullable ExtractionTerminalItem.LaunchError error) {
        float t = useTicks;
        float end = TerminalGeometry.TYPING_TICKS;
        float barStart = end * 0.8F;
        String cursor = blink(time) ? "_" : "";
        List<ScreenLine> log = new ArrayList<>();
        log.add(new ScreenLine(HEADER, DIM));
        log.add(typed("> exfil --launch", t, 0.0F, end * 0.3F, cursor, GREEN));
        if (t >= end * 0.32F) {
            log.add(typed(I18n.get("heraliz_extraction.ecran.lancement_1"), t, end * 0.32F, end * 0.55F, cursor, GREEN));
        }
        if (t >= end * 0.55F) {
            log.add(typed(I18n.get("heraliz_extraction.ecran.lancement_2"), t, end * 0.55F, barStart, cursor, GREEN));
        }
        if (t >= barStart) {
            if (error == null) {
                log.add(new ScreenLine(bar(Mth.clamp((t - barStart) / (end - barStart), 0.0F, 1.0F)), AMBER));
            } else {
                log.add(new ScreenLine("ERR: " + I18n.get(error.key), RED));
                if (t >= barStart + 6) {
                    log.add(new ScreenLine(I18n.get("heraliz_extraction.ecran.interrompu"), RED));
                }
                // le joueur insiste : nouvel essai, refusé, nouvel essai, refusé...
                float retryStart = barStart + 16;
                if (t >= retryStart) {
                    int steps = (int) ((t - retryStart) / 7.0F) + 1;
                    for (int i = 0; i < steps; i++) {
                        log.add(i % 2 == 0
                                ? new ScreenLine(I18n.get("heraliz_extraction.ecran.nouvel_essai"), AMBER)
                                : new ScreenLine(I18n.get("heraliz_extraction.ecran.refuse"), RED));
                    }
                }
            }
        }
        // seules les dernières lignes tiennent sur l'écran
        return log.size() > MAX_LINES ? new ArrayList<>(log.subList(log.size() - MAX_LINES, log.size())) : log;
    }

    /** Terminal posé au sol pendant l'extraction. */
    public static List<ScreenLine> ground(ExtractionTerminalEntity terminal, float partialTick, float time) {
        List<ScreenLine> lines = new ArrayList<>();
        lines.add(new ScreenLine(HEADER, DIM));
        if (terminal.getState() != ExtractionTerminalEntity.STATE_ACTIVE) {
            lines.add(new ScreenLine(blink(time) ? I18n.get("heraliz_extraction.ecran.zone_fermee") : "", RED));
            lines.add(new ScreenLine(I18n.get("heraliz_extraction.ecran.signal_eteint"), RED));
            return lines;
        }
        boolean deployed = terminal.tickCount >= TerminalGeometry.DEPLOY_END;
        int closing = Mth.ceil(terminal.getTicksLeft() / 20.0F);
        int count = terminal.getExtractingCount();
        lines.add(new ScreenLine(I18n.get("heraliz_extraction.ecran.zone_ouverte"), AMBER));
        lines.add(new ScreenLine(deployed ? I18n.get("heraliz_extraction.ecran.fusee_lancee")
                : I18n.get("heraliz_extraction.ecran.fusee_ouverture") + (blink(time) ? "_" : ""), GREEN));
        lines.add(new ScreenLine(I18n.get("heraliz_extraction.ecran.fermeture",
                ExtractionTerminalItem.formatTime(Math.max(0, closing))), GREEN));
        lines.add(new ScreenLine(count == 0 ? I18n.get("heraliz_extraction.ecran.attente") + (blink(time) ? "_" : "")
                : I18n.get("heraliz_extraction.ecran.extraction", count), GREEN));
        lines.add(new ScreenLine(bar(terminal.getProgress(partialTick)), AMBER));
        return lines;
    }

    private static ScreenLine typed(String text, float now, float start, float end, String cursor, int color) {
        if (now < start) {
            return new ScreenLine("", color);
        }
        float t = Mth.clamp((now - start) / (end - start), 0.0F, 1.0F);
        int count = Math.round(text.length() * t);
        String shown = text.substring(0, count);
        return new ScreenLine(t < 1.0F ? shown + cursor : shown, color);
    }

    private static String bar(float progress) {
        int filled = Mth.clamp(Math.round(progress * 10.0F), 0, 10);
        return "[" + "#".repeat(filled) + ".".repeat(10 - filled) + "] " + Math.round(progress * 100.0F) + "%";
    }
}
