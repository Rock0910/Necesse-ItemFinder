package wims2.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.state.MainGame;

import java.util.ArrayList;
import java.util.List;

/**
 * Side panel floating right of the main window: icon walls for
 * history (click = load into search box and search) and
 * favorites (click = remove). Paged, follows the main window.
 */
public class SidePanel extends Form {

    public enum PanelMode { HISTORY, FAVS }

    private static final int COLS = 6;
    private static final int PAGE_SIZE = 18; // 6 cols x 3 rows

    private final SearchForm main;
    private PanelMode panelMode = PanelMode.HISTORY;
    private int page;
    private FormContentBox box;
    private FormFairTypeLabel titleLabel;
    private final List<String> ids = new ArrayList<>();

    public SidePanel(SearchForm main) {
        super("itemfinderside", 240, 120);
        this.main = main;
        try { drawBaseAlpha = 0.45f; } catch (Exception ignored) {}
        FormFlow flow = new FormFlow(5);

        int ty = flow.next();
        titleLabel = new FormFairTypeLabel(wims2.L.t("history"), 5, ty);
        titleLabel.setFontOptions(new FontOptions(18));
        addComponent(titleLabel);
        try {
            necesse.gfx.ui.GameInterfaceStyle uiStyle = necesse.engine.Settings.UI;
            FormContentIconButton x = new FormContentIconButton(
                getWidth() - 46, ty, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
                uiStyle.button_cross, wims2.L.m("close"));
            addComponent(x);
            x.onClicked(e -> hidePanel());
        } catch (Exception ignored) {}
        flow.nextY(titleLabel, 5);

        box = new FormContentBox(5, 0, getWidth() - 10, 150);
        box.alwaysShowVerticalScrollBar = false;
        addComponent(box);
        flow.nextY(box, 5);

        int pr = flow.next();
        FormTextButton prev = new FormTextButton(
            wims2.L.t("prev"), 5, pr, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(prev);
        prev.onClicked(e -> {
            if (page > 0) { page--; render(); }
        });
        FormTextButton next = new FormTextButton(
            wims2.L.t("next"), getWidth() / 2 + 5, pr, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(next);
        next.onClicked(e -> {
            if (page < totalPages() - 1) { page++; render(); }
        });
        flow.nextY(next, 5);

        setHeight(flow.next() + 5);
        render();
    }

    public PanelMode getMode() {
        return panelMode;
    }

    public void showMode(PanelMode m) {
        panelMode = m;
        page = 0;
        render();
    }

    public FormContentBox getBox() {
        return box;
    }

    private void hidePanel() {
        try {
            main.hidePanel();
        } catch (Exception ignored) {}
    }

    private int totalPages() {
        return Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Re-read data and redraw (called on toggle/record/remove). */
    public void render() {
        box.clearComponents();
        ids.clear();
        try {
            if (panelMode == PanelMode.HISTORY) {
                titleLabel.setText(wims2.L.msg("panelhist", "n",
                    String.valueOf(wims2.WimsData.getHistoryItems().size())));
                ids.addAll(wims2.WimsData.getHistoryItems());
            } else {
                titleLabel.setText(wims2.L.msg("panelfav", "n",
                    String.valueOf(wims2.WimsData.getFavorites().size())));
                ids.addAll(wims2.WimsData.getFavorites());
            }
        } catch (Exception ignored) {}
        if (page > totalPages() - 1) page = totalPages() - 1;
        if (page < 0) page = 0;

        int y = 0;
        // History gets a clear-all button as its first row
        if (panelMode == PanelMode.HISTORY) {
            try {
                FormTextButton clear = new FormTextButton(
                    wims2.L.t("clearhistory"), 0, y, getWidth() - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
                box.addComponent(clear);
                clear.onClicked(e -> {
                    wims2.WimsData.clearHistory();
                    page = 0;
                    render();
                });
            } catch (Exception ignored) {}
            y += 36;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, ids.size());
        int col = 0;
        int iy = y;
        for (int i = from; i < to; i++) {
            final String sid = ids.get(i);
            try {
                necesse.inventory.InventoryItem inv = wims2.WimsData.resolveItem(sid);
                if (inv == null) continue;
                final int cx = (col % COLS) * 36;
                // Click = load into search box and search (both modes)
                ItemIconButton ib = new ItemIconButton(cx, iy, FormInputSize.SIZE_32,
                    ButtonColor.BASE, inv,
                    necesse.engine.Settings.UI.button_search_24,
                    wims2.L.m("scan"));
                box.addComponent(ib);
                ib.onClicked(e -> {
                    try { main.searchFromHistory(sid); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
            col++;
            if (col % COLS == 0) iy += 36;
        }
        if (ids.isEmpty()) {
            try {
                String msg = panelMode == PanelMode.HISTORY
                    ? wims2.L.t("sidehistempty")
                    : wims2.L.t("sidefavempty");
                FormFairTypeLabel empty = new FormFairTypeLabel(msg, 0, y);
                empty.setFontOptions(new FontOptions(14));
                box.addComponent(empty);
            } catch (Exception ignored) {}
        }
    }

    /** Pin to the right of the main window, clamped on screen. */
    public void followMain(GameWindow window) {
        try {
            MainGame mg = main.getMainGame();
            int mx = main.getX(), my = main.getY(), mw = main.getWidth();
            int winW = window.getWidth(), winH = window.getHeight();
            int nx = mx + mw + 10;
            if (nx + getWidth() > winW) nx = Math.max(0, mx - getWidth() - 10);
            int ny = Math.max(0, Math.min(my, winH - getHeight()));
            setPosition(new necesse.gfx.forms.position.FormFixedPosition(nx, ny));
        } catch (Exception ignored) {}
    }
}
