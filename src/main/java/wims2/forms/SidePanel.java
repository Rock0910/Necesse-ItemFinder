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
    private FormFairTypeLabel pageLabel;
    private FormTextButton prevBtn, nextBtn;
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
        // Title text is shorter than the 36px close button; extra padding
        // so the icon wall starts below the button, not under it.
        flow.nextY(titleLabel, 16);

        box = new FormContentBox(5, 0, getWidth() - 10, 150);
        box.alwaysShowVerticalScrollBar = false;
        addComponent(box);
        flow.nextY(box, 5);

        int pr = flow.next();
        prevBtn = new FormTextButton(
            wims2.L.t("prev"), 5, pr, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(prevBtn);
        prevBtn.onClicked(e -> {
            if (page > 0) { page--; render(); }
        });
        nextBtn = new FormTextButton(
            wims2.L.t("next"), getWidth() / 2 + 5, pr, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(nextBtn);
        nextBtn.onClicked(e -> {
            if (page < totalPages() - 1) { page++; render(); }
        });
        flow.nextY(nextBtn, 5);

        pageLabel = new FormFairTypeLabel("", 5, 0);
        pageLabel.setFontOptions(new FontOptions(14));
        addComponent(pageLabel);
        flow.nextY(pageLabel, 5);

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
        // Page indicator + disable paging when there is only one page
        try {
            if (pageLabel != null) {
                pageLabel.setText(wims2.L.msg("pageno",
                    "p", String.valueOf(page + 1),
                    "t", String.valueOf(totalPages())));
            }
            if (prevBtn != null) prevBtn.setActive(page > 0);
            if (nextBtn != null) nextBtn.setActive(page < totalPages() - 1);
        } catch (Exception ignored) {}

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
                try {
                    empty.setMaxWidth(getWidth() - 15);
                    empty.setMaxLines(2, false);
                } catch (Exception ignored) {}
                box.addComponent(empty);
            } catch (Exception ignored) {}
        }
    }

    private int lastNx = Integer.MIN_VALUE, lastNy = Integer.MIN_VALUE;

    /** Pin to the right of the main window, clamped on screen.
     * Only moves when the target actually changed (avoids allocating a
     * position object every frame). */
    public void followMain(GameWindow window) {
        try {
            int mx = main.getX(), my = main.getY(), mw = main.getWidth();
            int winW = window.getWidth(), winH = window.getHeight();
            int nx = mx + mw + 10;
            if (nx + getWidth() > winW) nx = Math.max(0, mx - getWidth() - 10);
            int ny = Math.max(0, Math.min(my, winH - getHeight()));
            if (nx == lastNx && ny == lastNy && getX() == nx && getY() == ny) return;
            lastNx = nx;
            lastNy = ny;
            setPosition(new necesse.gfx.forms.position.FormFixedPosition(nx, ny));
        } catch (Exception ignored) {}
    }
}
