package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.DuelBlockEntity;
import fr.heraliz.casino.block.entity.NavalBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.render.scene.machines.NavalDesign;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.duel.NavalBoard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Bataille navale a la table. Placement : les navires attendent dans le bac sur le cote de la table ; clic gauche
 * pour en prendre un (il se souleve et suit la souris), clic droit (ou R) pour le tourner, clic gauche sur la grille
 * pour le poser (ou sur le bac pour l'y remettre). Bataille : on tire en cliquant une case de la grille du paravent.
 */
public class NavalScreen extends DuelScreen<NavalBlockEntity> {
    private static final int N = NavalBoard.SIZE;

    public NavalScreen(NavalBlockEntity be) {
        super(be);
    }

    @Override
    protected DuelRules rules() {
        return cfg(false);
    }

    private boolean ready(int side) {
        return side == 1 ? be.readyA : be.readyB;
    }

    /** Placement en cours pour moi. */
    private boolean placing() {
        int side = mySide();
        return side != 0 && be.phase == DuelBlockEntity.PLAYING && be.step == NavalBlockEntity.SETUP && !ready(side)
                && be.clientBoard.length == NavalBoard.CELLS;
    }

    private boolean fleetComplete() {
        return be.clientBoard.length == NavalBoard.CELLS && NavalBoard.complete(be.clientBoard);
    }

    @Override
    protected int layoutKey() {
        return super.layoutKey() + be.step * 1000 + (ready(mySide()) ? 10000 : 0);
    }

    @Override
    public void removed() {
        drop();
        be.clientHoldX = be.clientHoldZ = Float.NaN;
        super.removed();
    }

    /** Lache le navire tenu (il retourne a sa place). */
    private void drop() {
        be.clientHeld = 0;
        be.clientSnap = -1;
    }

    // =================================================================== interface allegee
    @Override
    protected boolean showBalance() {
        return be.phase != DuelBlockEntity.PLAYING || mySide() == 0;
    }

    @Override
    protected boolean showPot() {
        return false;
    }

    @Override
    protected void gameDock(Dock d, int side) {
        if (be.step != NavalBlockEntity.SETUP) return;
        d.button(Component.translatable("screen.heralizcasino.naval.shuffle"), UiAtlas.DARK, 80, () -> {
            drop();
            send(NavalBlockEntity.ACTION_SHUFFLE);
            click();
        }).when(() -> !ready(side)).tip(() -> Component.translatable("screen.heralizcasino.naval.shuffle_tip"));
        d.button(Component.translatable("screen.heralizcasino.naval.ready"), UiAtlas.GREEN, 64, () -> {
            drop();
            send(NavalBlockEntity.ACTION_READY);
            click();
        }).when(() -> !ready(side) && be.clientHeld == 0 && fleetComplete()).tip(() -> Component.translatable("screen.heralizcasino.naval.ready_tip"));
    }

    @Override
    protected Component gameStatus(int side) {
        if (be.step == NavalBlockEntity.SETUP) {
            if (side != 0 && !ready(side)) {
                if (be.clientHeld > 0) return Component.translatable("screen.heralizcasino.naval.place_held", secondsLeft());
                if (fleetComplete()) return Component.translatable("screen.heralizcasino.naval.place_done", secondsLeft());
                return Component.translatable("screen.heralizcasino.naval.setup", secondsLeft());
            }
            return Component.translatable("screen.heralizcasino.naval.setup_wait", secondsLeft());
        }
        if (side != 0 && be.turn == side) return Component.translatable("screen.heralizcasino.naval.your_turn", secondsLeft());
        return Component.translatable("screen.heralizcasino.naval.turn_of", be.name(be.turn), secondsLeft());
    }

    @Override
    protected int statusColor() {
        if (be.phase == NavalBlockEntity.PLAYING && be.step == NavalBlockEntity.BATTLE && be.turn == mySide()) {
            return secondsLeft() <= 5 ? RED : GREEN;
        }
        return super.statusColor();
    }

    // =================================================================== placement a la souris
    /** Point du plan horizontal y du modele sous la souris : {x, z}, ou null. */
    private float[] onPlane(int mx, int my, float y) {
        float[] r = mouseRay(mx, my);
        if (r == null || Math.abs(r[4]) < 1e-4f) return null;
        float t = (y - r[1]) / r[4];
        if (t <= 0) return null;
        return new float[]{r[0] + r[3] * t, r[2] + r[5] * t};
    }

    /** Met a jour le navire tenu (position sous la souris, emplacement propose) ou le navire survole. */
    private void updatePlacement(int mx, int my) {
        if (!placing()) {
            be.clientHoldX = be.clientHoldZ = Float.NaN;
            be.clientSnap = -1;
            return;
        }
        int side = mySide();
        float[] board = onPlane(mx, my, NavalDesign.BOARD_PLANE);
        if (be.clientHeld == 0) {
            be.clientHoldX = board == null ? Float.NaN : board[0];
            be.clientHoldZ = board == null ? Float.NaN : board[1];
            be.clientSnap = -1;
            return;
        }
        // le navire souleve reste sous le curseur ; son emplacement est calcule au niveau du plateau
        float[] lifted = onPlane(mx, my, NavalDesign.BOARD_PLANE + 2.2f);
        be.clientHoldX = lifted == null ? Float.NaN : lifted[0];
        be.clientHoldZ = lifted == null ? Float.NaN : lifted[1];
        be.clientSnap = -1;
        be.clientSnapOk = false;
        if (board == null || NavalDesign.overDock(side, board[0], board[1])) return;
        float[] c = NavalDesign.boardCellAt(side, board[0], board[1]);
        int len = NavalBoard.SHIPS[be.clientHeld - 1];
        int bow = NavalDesign.snapBow(c[0], c[1], len, be.clientHeldH);
        if (bow < 0) return;
        be.clientSnap = bow;
        be.clientSnapOk = NavalBoard.fits(be.clientBoard, be.clientHeld, bow % N, bow / N, be.clientHeldH, false);
    }

    /** Tests automatiques : un point de l'ecran qui vise le point (x, z) de la table (a 0,4 px pres), ou null. */
    public int[] findTablePoint(float x, float z) {
        int[] best = null;
        float bestD = 0.4f;
        for (int sy = 4; sy < height; sy += 3) {
            for (int sx = 4; sx < width; sx += 3) {
                float[] q = onPlane(sx, sy, NavalDesign.BOARD_PLANE);
                if (q == null) continue;
                float d = (float) Math.hypot(q[0] - x, q[1] - z);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{sx, sy};
                }
            }
        }
        return best;
    }

    /** Navire sous la souris (grille ou bac), 0 aucun. */
    private int shipUnderMouse() {
        if (Float.isNaN(be.clientHoldX)) return 0;
        int side = mySide();
        float[] c = NavalDesign.boardCellAt(side, be.clientHoldX, be.clientHoldZ);
        int gx = Math.round(c[0]), gy = Math.round(c[1]);
        if (gx >= 0 && gy >= 0 && gx < N && gy < N && be.clientBoard[gy * N + gx] != 0) return be.clientBoard[gy * N + gx];
        return NavalDesign.dockShipAt(side, be.clientHoldX, be.clientHoldZ, be.clientBoard, 0);
    }

    private void rotateHeld() {
        if (be.clientHeld == 0) return;
        be.clientHeldH = !be.clientHeldH;
        click();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!placing()) return super.mouseClicked(mx, my, button);
        // boutons de l'interface d'abord
        for (GuiEventListener l : children()) {
            if (l.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        }
        int[] m = testMouse != null ? testMouse : new int[]{(int) mx, (int) my};
        updatePlacement(m[0], m[1]);
        int side = mySide();
        if (button == 1) {
            if (be.clientHeld > 0) {
                rotateHeld();
                updatePlacement(m[0], m[1]);
            } else {
                // clic droit sur un navire pose : il pivote sur place
                int k = shipUnderMouse();
                int[] s = k > 0 ? NavalBoard.ship(be.clientBoard, k) : null;
                if (s != null) {
                    boolean h = s[2] == 0;
                    if (NavalBoard.fits(be.clientBoard, k, s[0], s[1], h, false)) {
                        NavalBoard.move(be.clientBoard, k, s[0], s[1], h);
                        send(NavalBlockEntity.ACTION_PLACE, k, s[0], s[1], h ? 1 : 0);
                        click();
                    } else {
                        notice(Component.translatable("screen.heralizcasino.naval.no_room"));
                    }
                }
            }
            return true;
        }
        if (button != 0) return true;
        if (be.clientHeld == 0) {
            int k = shipUnderMouse();
            if (k > 0) {
                int[] s = NavalBoard.ship(be.clientBoard, k);
                be.clientHeld = k;
                be.clientHeldH = s == null || s[2] == 1;
                updatePlacement(m[0], m[1]);
                click();
            }
            return true;
        }
        int k = be.clientHeld;
        float[] board = onPlane(m[0], m[1], NavalDesign.BOARD_PLANE);
        if (board != null && NavalDesign.overDock(side, board[0], board[1])) {
            // retour dans le bac
            NavalBoard.remove(be.clientBoard, k);
            send(NavalBlockEntity.ACTION_PLACE, k, -1, 0, 0);
            drop();
            click();
            return true;
        }
        if (be.clientSnap >= 0 && be.clientSnapOk) {
            int x = be.clientSnap % N, y = be.clientSnap / N;
            NavalBoard.move(be.clientBoard, k, x, y, be.clientHeldH);
            send(NavalBlockEntity.ACTION_PLACE, k, x, y, be.clientHeldH ? 1 : 0);
            drop();
            chipSound();
        } else {
            notice(Component.translatable("screen.heralizcasino.naval.no_room"));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_R && placing() && be.clientHeld > 0 && (betBox == null || !betBox.isFocused())) {
            rotateHeld();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // =================================================================== tir sur la grille du paravent
    @Override
    protected boolean hotspotEnabled(int id) {
        int side = mySide();
        if (side == 0 || be.phase != NavalBlockEntity.PLAYING) return false;
        if (id >= NavalDesign.SHOOT && id < NavalDesign.SHOOT + NavalBoard.CELLS) {
            int c = id - NavalDesign.SHOOT;
            long shots = side == 1 ? be.shotsOnB : be.shotsOnA;
            return be.step == NavalBlockEntity.BATTLE && be.turn == side && (shots & (1L << c)) == 0
                    && partialNow() >= be.lastShotTick + NavalBlockEntity.SHOT_TICKS;
        }
        return false;
    }

    @Override
    protected void onHotspot(int id) {
        if (id >= NavalDesign.SHOOT && id < NavalDesign.SHOOT + NavalBoard.CELLS) {
            watch = false;
            send(NavalBlockEntity.ACTION_SHOOT, id - NavalDesign.SHOOT);
            click();
        }
    }

    @Override
    protected Component hotspotTip(int id) {
        if (id >= NavalDesign.SHOOT && id < NavalDesign.SHOOT + NavalBoard.CELLS) {
            return Component.translatable("screen.heralizcasino.naval.tip_shoot", NavalBoard.cellName(id - NavalDesign.SHOOT));
        }
        return null;
    }

    @Override
    protected int hotspotColor(int id) {
        return 0xFF6A4A;
    }

    // =================================================================== interface 2D : flotte adverse restante
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int[] m = testMouse != null ? testMouse : new int[]{mx, my};
        updatePlacement(m[0], m[1]);
        super.render(g, mx, my, partial);
    }

    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float partial) {
        if (be.step != NavalBlockEntity.BATTLE && be.phase != DuelBlockEntity.DONE) return;
        int side = mySide();
        int other = side == 0 ? 2 : 3 - side;
        fleetPanel(g, 10, 26, other);
        if (side == 0) fleetPanel(g, 10, 26 + 70, 1);
    }

    /** Petit tableau des navires d'un joueur : ceux qui restent a flot, ceux qui sont coules (barres). */
    private void fleetPanel(GuiGraphics g, int x, int y, int side) {
        int[] wrecks = side == 1 ? be.wrecksA : be.wrecksB;
        int left = NavalBoard.SHIPS.length - be.wrecks(side);
        int w = 104, h = 16 + NavalBoard.SHIPS.length * 10;
        g.fill(x, y, x + w, y + h, 0x90101418);
        g.fill(x, y, x + w, y + 1, 0x805FB8FF);
        Component title = Component.translatable("screen.heralizcasino.naval.fleet_left", be.name(side), left);
        UiKit.outlinedFit(g, title, x + 4, y + 4, w - 8, left <= 1 ? RED : CREAM, 0.7f, -1);
        for (int k = 1; k <= NavalBoard.SHIPS.length; k++) {
            int len = NavalBoard.SHIPS[k - 1];
            boolean sunk = k - 1 < wrecks.length && wrecks[k - 1] >= 0;
            int ry = y + 14 + (k - 1) * 10;
            int col = sunk ? 0xFF5A2A26 : 0xFF9FB8CC, edge = sunk ? 0xFF2A1210 : 0xFF3A4A5A;
            // coque : une case par longueur, proue en pointe
            int cx = x + 6;
            for (int i = 0; i < len; i++) {
                g.fill(cx + i * 9, ry, cx + i * 9 + 8, ry + 7, edge);
                g.fill(cx + i * 9 + 1, ry + 1, cx + i * 9 + 7, ry + 6, col);
            }
            int bx = cx + len * 9;
            for (int j = 0; j < 4; j++) g.fill(bx + j - 1, ry + j, bx + j, ry + 7 - j, col);
            UiKit.outlined(g, Component.literal(Integer.toString(len)), x + w - 6, ry, sunk ? GREY : CREAM, 0.7f, 1);
            if (sunk) g.fill(cx - 2, ry + 3, bx + 4, ry + 4, 0xFFFF4A3A);
        }
    }

    @Override
    protected java.util.List<Component> infoExtra() {
        java.util.List<Component> out = new java.util.ArrayList<>(super.infoExtra());
        out.add(Component.translatable("screen.heralizcasino.naval.info_ships"));
        return out;
    }
}
