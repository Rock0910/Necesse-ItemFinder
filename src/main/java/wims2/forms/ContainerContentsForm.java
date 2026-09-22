package wims2.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFairTypeLabel;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Small popup showing everything inside one container (all slots, not just
 * the matched items). Opened from the "..." button on a result row.
 */
public class ContainerContentsForm extends Form {

    private static final int COLS = 8;
    private static final int PAGE_SIZE = 32;

    private final SearchForm main;
    private final List<InventoryItem> items = new ArrayList<>();
    private final String title;
    private final int tileX, tileY;
    private int page;
    private FormContentBox box;
    private FormFairTypeLabel pageLabel;
    private FormTextButton prevBtn, nextBtn;
    // Ping fade: become semi-transparent, then fade back to opaque
    private boolean fading;
    private long fadeStart;
    private float fadeFrom = 1f;
    private static final long FADE_MS = 3000;

    public ContainerContentsForm(SearchForm main, String title, List<InventoryItem> contents,
                                 int tileX, int tileY) {
        super("itemfindercontents", 320, 100);
        this.main = main;
        this.tileX = tileX;
        this.tileY = tileY;
        this.title = title == null ? "" : title;
        if (contents != null) items.addAll(contents);
        // Opaque by default; only Ping makes it see-through briefly
        try { drawBaseAlpha = 1.0f; } catch (Exception ignored) {}
        try { zIndex = 1000; } catch (Exception ignored) {}
        FormFlow flow = new FormFlow(5);

        int ty = flow.next();
        FormFairTypeLabel head = new FormFairTypeLabel(this.title, 5, ty);
        head.setFontOptions(new FontOptions(18));
        try { head.setMaxWidth(getWidth() - 95); head.setMaxLines(1, false, true); }
        catch (Exception ignored) {}
        addComponent(head);
        try {
            necesse.gfx.ui.GameInterfaceStyle ui = necesse.engine.Settings.UI;
            // Ping this container from the popup itself
            FormTextButton ping = new FormTextButton(
                wims2.L.t("ping"), getWidth() - 90, ty, 40, FormInputSize.SIZE_32, ButtonColor.BASE);
            addComponent(ping);
            ping.onClicked(e -> {
                try { main.pingTile(tileX, tileY); } catch (Exception ignored) {}
                startPingFade();
            });
            FormContentIconButton x = new FormContentIconButton(
                getWidth() - 46, ty, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
                ui.button_cross, wims2.L.m("close"));
            addComponent(x);
            x.onClicked(e -> closeContents());
        } catch (Exception ignored) {}
        flow.nextY(head, 16);

        box = new FormContentBox(5, 0, getWidth() - 10, 150);
        box.alwaysShowVerticalScrollBar = false;
        addComponent(box);
        flow.nextY(box, 5);

        int pr = flow.next();
        prevBtn = new FormTextButton(wims2.L.t("prev"), 5, pr,
            getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(prevBtn);
        prevBtn.onClicked(e -> {
            int pages = totalPages();
            if (pages > 1) { page = (page - 1 + pages) % pages; render(); }
        });
        nextBtn = new FormTextButton(wims2.L.t("next"), getWidth() / 2 + 5, pr,
            getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(nextBtn);
        nextBtn.onClicked(e -> {
            int pages = totalPages();
            if (pages > 1) { page = (page + 1) % pages; render(); }
        });
        flow.nextY(nextBtn, 5);

        pageLabel = new FormFairTypeLabel(wims2.L.msg("pageno", "p", "1", "t", "1"), 5, 0);
        pageLabel.setFontOptions(new FontOptions(14));
        addComponent(pageLabel);
        flow.nextY(pageLabel, 8);

        setHeight(flow.next() + 12);
        render();
    }

    private int totalPages() {
        return Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void render() {
        box.clearComponents();
        if (page > totalPages() - 1) page = totalPages() - 1;
        if (page < 0) page = 0;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, items.size());
        int col = 0, y = 0;
        for (int i = from; i < to; i++) {
            final InventoryItem it = items.get(i);
            try {
                ItemIconButton icon = new ItemIconButton((col % COLS) * 36, y,
                    FormInputSize.SIZE_32, ButtonColor.BASE, it,
                    necesse.engine.Settings.UI.button_search_24, wims2.L.m("scan"));
                box.addComponent(icon);
                icon.onClicked(e -> {
                    try { main.searchExactPublic(it); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
            col++;
            if (col % COLS == 0) y += 36;
        }
        if (items.isEmpty()) {
            try {
                FormFairTypeLabel empty = new FormFairTypeLabel(wims2.L.t("empty"), 0, 0);
                empty.setFontOptions(new FontOptions(14));
                box.addComponent(empty);
            } catch (Exception ignored) {}
        }
        try {
            pageLabel.setText(wims2.L.msg("pageno",
                "p", String.valueOf(page + 1), "t", String.valueOf(totalPages())));
            prevBtn.setActive(totalPages() > 1);
            nextBtn.setActive(totalPages() > 1);
        } catch (Exception ignored) {}
    }

    private void closeContents() {
        try { main.hideContents(); } catch (Exception ignored) {}
    }

    @Override
    public void dispose() {
        try { main.onContentsDisposed(this); } catch (Exception ignored) {}
        super.dispose();
    }

    /** Ping fade: drop to the main window's opacity, then fade back over 3s. */
    private void startPingFade() {
        try { fadeFrom = Math.max(0.15f, main.getOpacityPercent() / 100f); }
        catch (Exception ignored) { fadeFrom = 0.45f; }
        try { drawBaseAlpha = fadeFrom; } catch (Exception ignored) {}
        fadeStart = System.currentTimeMillis();
        fading = true;
    }

    /** Called every frame while the popup is open. */
    public void tick() {
        if (!fading) return;
        try {
            float t = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
            if (t >= 1f) {
                fading = false;
                drawBaseAlpha = 1f;
                return;
            }
            drawBaseAlpha = fadeFrom + (1f - fadeFrom) * t;
        } catch (Exception ignored) {}
    }

    /** Position in the parent form's local coordinates. */
    public void setLocalPosition(int x, int y) {
        try { setPosition(new necesse.gfx.forms.position.FormFixedPosition(x, y)); }
        catch (Exception ignored) {}
    }

    /** Box holding the icon grid (used for U-favorite hover detection). */
    public FormContentBox getBox() {
        return box;
    }
}
