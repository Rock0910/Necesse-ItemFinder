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
    private int page;
    private FormContentBox box;
    private FormFairTypeLabel pageLabel;
    private FormTextButton prevBtn, nextBtn;

    public ContainerContentsForm(SearchForm main, String title, List<InventoryItem> contents) {
        super("itemfindercontents", 320, 100);
        this.main = main;
        this.title = title == null ? "" : title;
        if (contents != null) items.addAll(contents);
        try { drawBaseAlpha = main.getOpacityPercent() / 100f; } catch (Exception ignored) {}
        FormFlow flow = new FormFlow(5);

        int ty = flow.next();
        FormFairTypeLabel head = new FormFairTypeLabel(this.title, 5, ty);
        head.setFontOptions(new FontOptions(18));
        try { head.setMaxWidth(getWidth() - 55); head.setMaxLines(1, false, true); }
        catch (Exception ignored) {}
        addComponent(head);
        try {
            necesse.gfx.ui.GameInterfaceStyle ui = necesse.engine.Settings.UI;
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

    /** Keep the popup next to the main window and on screen. */
    public void followMain(GameWindow window) {
        try {
            int winW = window.getWidth(), winH = window.getHeight();
            int nx = main.getX() + main.getWidth() + 10;
            if (nx + getWidth() > winW) nx = Math.max(0, main.getX() - getWidth() - 10);
            int ny = Math.max(0, Math.min(main.getY(), winH - getHeight()));
            setPosition(new necesse.gfx.forms.position.FormFixedPosition(nx, ny));
        } catch (Exception ignored) {}
    }

    @Override
    public void draw(TickManager tm, necesse.entity.mobs.PlayerMob player, java.awt.Rectangle r) {
        super.draw(tm, player, r);
    }
}
