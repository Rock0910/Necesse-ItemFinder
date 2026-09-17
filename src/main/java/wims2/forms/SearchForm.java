package wims2.forms;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.state.MainGame;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.item.ItemCategory;
import wims2.ItemMatcher;
import wims2.ModMain;
import wims2.RangeOverlay;
import wims2.SearchEngine;
import wims2.TargetMarker;

import java.util.List;

/**
 * NOTE: all visible strings are English-only. The game FairType font has no
 * CJK glyphs (Chinese shows as boxes), so never put Chinese in UI text.
 *
 * Layout uses FormFlow like the original WIMS: every component is placed
 * with flow.nextY(...) or flow.next(), otherwise they all stack at (0,0).
 */
public class SearchForm extends Form {

    private static SearchForm instance;
    private final MainGame mainGame;
    private FormTextInput textInput;
    private FormDropdownSelectionButton<String> categoryDropdown;
    private FormDropdownSelectionButton<Integer> radiusDropdown;
    private FormContentBox resultBox;
    private FormFairTypeLabel statusLabel;

    private SearchEngine.Snapshot snapshot;
    private int currentRadius = 16;

    public SearchForm(MainGame mainGame) {
        super("itemfindersearch", 460, 120);
        this.mainGame = mainGame;
        // Semi-transparent window background so the game is visible behind it
        try { drawBaseAlpha = 0.45f; } catch (Exception ignored) {}
        FormFlow flow = new FormFlow(5);

        // Title + mode icons + close (X) at top-right
        necesse.gfx.ui.GameInterfaceStyle uiStyle = necesse.engine.Settings.UI;
        int titleY = flow.next();
        FormFairTypeLabel title = new FormFairTypeLabel("ItemFinder", 5, titleY);
        title.setFontOptions(new FontOptions(20));
        addComponent(title);
        FormContentIconButton histBtn = new FormContentIconButton(
            getWidth() - 118, titleY, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
            uiStyle.rotate_counterclockwise_32, new StaticMessage("History"));
        addComponent(histBtn);
        histBtn.onClicked(e -> { defocusInput(); togglePanel(SidePanel.PanelMode.HISTORY); });
        FormContentIconButton favBtn = new FormContentIconButton(
            getWidth() - 82, titleY, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
            uiStyle.firework_star, new StaticMessage("Favorites"));
        addComponent(favBtn);
        favBtn.onClicked(e -> { defocusInput(); togglePanel(SidePanel.PanelMode.FAVS); });
        FormContentIconButton closeBtn = new FormContentIconButton(
            getWidth() - 46, titleY, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
            uiStyle.button_cross, new StaticMessage("Close"));
        addComponent(closeBtn);
        closeBtn.onClicked(e -> onCancel());
        // Title text is shorter than the 32px icon buttons: extra padding
        // so the next row starts below the buttons, not overlapping them.
        flow.nextY(title, 13);

        // Keyword input + icon buttons on the same row (game UI icons)
        int inputY = flow.next();
        textInput = new FormTextInput(5, inputY, FormInputSize.SIZE_32, 350, 200, 50);
        addComponent(textInput);
        textInput.placeHolder = new StaticMessage("copper, bread, potion...");
        textInput.onSubmit(e -> { defocusInput(); applyFilter(true); });
        FormContentIconButton scanBtn = new FormContentIconButton(
            360, inputY, FormInputSize.SIZE_32, ButtonColor.BASE,
            uiStyle.button_search_24, new StaticMessage("Scan"));
        addComponent(scanBtn);
        scanBtn.onClicked(e -> { defocusInput(); resnapshot(); });
        FormContentIconButton clearBtn = new FormContentIconButton(
            400, inputY, FormInputSize.SIZE_32, ButtonColor.BASE,
            uiStyle.button_clear, new StaticMessage("Clear"));
        addComponent(clearBtn);
        clearBtn.onClicked(e -> { defocusInput(); clearTextbox(); });
        flow.nextY(textInput, 5);

        // Category + Range + Reset on the same row
        int dropY = flow.next();
        categoryDropdown = new FormDropdownSelectionButton<String>(5, dropY, FormInputSize.SIZE_32,
            ButtonColor.BASE, 165, new StaticMessage("Category"));
        addComponent(categoryDropdown);
        categoryDropdown.options.add("all", new StaticMessage("All categories"));
        try {
            for (ItemCategory c : ItemCategory.masterManager.masterCategory.getChildren()) {
                GameMessage name = c.displayName != null ? c.displayName : new StaticMessage(c.stringID);
                categoryDropdown.options.add(c.stringID, name);
            }
        } catch (Exception ignored) {}
        categoryDropdown.setSelected("all", new StaticMessage("All categories"));
        categoryDropdown.onSelected(e -> { defocusInput(); applyFilter(true); });
        radiusDropdown = new FormDropdownSelectionButton<Integer>(175, dropY, FormInputSize.SIZE_32,
            ButtonColor.BASE, 135, new StaticMessage("Range"));
        addComponent(radiusDropdown);
        radiusDropdown.options.add(16, new StaticMessage("16 tiles"));
        radiusDropdown.options.add(32, new StaticMessage("32 tiles"));
        radiusDropdown.options.add(64, new StaticMessage("64 tiles"));
        radiusDropdown.options.add(128, new StaticMessage("128 tiles"));
        radiusDropdown.setSelected(16, new StaticMessage("16 tiles"));
        radiusDropdown.onSelected(e -> {
            defocusInput();
            try {
                Integer r = radiusDropdown.getSelected();
                if (r != null) {
                    currentRadius = r;
                    resnapshot();
                }
            } catch (Exception ignored) {}
        });
        FormTextButton resetBtn = new FormTextButton(
            "Reset", 315, dropY, 130, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(resetBtn);
        resetBtn.onClicked(e -> { defocusInput(); resetDropdowns(); });
        flow.nextY(categoryDropdown, 5);

        // Status + results
        statusLabel = new FormFairTypeLabel("Loading...", 5, 0);
        statusLabel.setFontOptions(new FontOptions(16));
        addComponent(statusLabel);
        flow.nextY(statusLabel, 5);

        resultBox = new FormContentBox(5, 0, getWidth() - 10, 170);
        resultBox.alwaysShowVerticalScrollBar = false; // pagination now, no scrolling needed
        addComponent(resultBox);
        flow.nextY(resultBox, 5);

        // One-line affordance hint above the pager
        FormFairTypeLabel iconTip = new FormFairTypeLabel(
            "Click: mark. U: favorite.", 5, 0);
        iconTip.setFontOptions(new FontOptions(14));
        addComponent(iconTip);
        flow.nextY(iconTip, 5);

        // Page row: pagination instead of scrolling. Scrolling (wheel or
        // scrollY) proved unreliable here, paging just rebuilds the list.
        int pageRowY = flow.next();
        FormTextButton prevBtn = new FormTextButton(
            "Prev", 5, pageRowY, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(prevBtn);
        prevBtn.onClicked(e -> {
            defocusInput();
            if (page > 0) { page--; showPage(); }
        });
        FormTextButton nextBtn = new FormTextButton(
            "Next", getWidth() / 2 + 5, pageRowY, getWidth() / 2 - 10, FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(nextBtn);
        nextBtn.onClicked(e -> {
            defocusInput();
            if (page < totalPages() - 1) { page++; showPage(); }
        });
        flow.nextY(nextBtn, 5);

        setHeight(flow.next() + 5);
        // Draggable by the top strip (title area)
        try {
            setDraggingBox(new java.awt.Rectangle(0, 0, getWidth(), 30));
        } catch (Exception ignored) {}
        positionAboveInventory();

        // Snapshot on open
        resnapshot();
    }

    @Override
    public void init() {
        super.init();
        // ?��? WIMS 也�??��?：�?它鍵?��?字進�?了輸?��?
        try { textInput.setTyping(true); } catch (Exception ignored) {}
    }

    /** Any button click hands keyboard focus back (input box stops eating keys) */
    private void defocusInput() {
        try { textInput.setTyping(false); } catch (Exception ignored) {}
    }

    /** Clear: textbox only, keep dropdowns and snapshot */
    private void clearTextbox() {
        try { textInput.setText(""); } catch (Exception ignored) {}
        applyFilter(false);
    }

    /** Reset: dropdowns back to defaults (category All, radius 16) */
    private void resetDropdowns() {
        try {
            categoryDropdown.setSelected("all", new StaticMessage("All categories"));
        } catch (Exception ignored) {}
        boolean radiusChanged = false;
        try {
            Integer r = radiusDropdown.getSelected();
            radiusChanged = r == null || r != 16;
            if (radiusChanged) {
                currentRadius = 16;
                radiusDropdown.setSelected(16, new StaticMessage("16 tiles"));
            }
        } catch (Exception ignored) {}
        // Radius change means the snapshot no longer matches: re-scan.
        // (If setSelected fires onSelected above, this second scan is harmless.)
        if (radiusChanged) resnapshot();
        else applyFilter(false);
    }

    /** Re-fetch containers in range + redraw range overlay + flash matches */
    private void resnapshot() {
        if (mainGame.getClient() == null) return;
        snapshot = SearchEngine.snapshot(mainGame.getClient(), currentRadius);
        try {
            int px = mainGame.getClient().getPlayer().getTileX();
            int py = mainGame.getClient().getPlayer().getTileY();
            RangeOverlay.show(px, py, currentRadius);
        } catch (Exception ignored) {}
        // true: Scan must flash beacons too, not just Enter/category changes.
        // (Opening the window has no keyword/category yet, so this is a no-op there.)
        applyFilter(true);
    }

    /** Filter the snapshot. No level access, so instant. */
    private void applyFilter(boolean spawnParticles) {
        if (snapshot == null) return;
        String keyword = "";
        try { keyword = textInput.getText().trim(); } catch (Exception ignored) {}
        String catID = "all";
        try { catID = categoryDropdown.getSelected(); } catch (Exception ignored) {}

        ItemMatcher m = keyword.isEmpty() ? null : ItemMatcher.byName(keyword);
        if (!"all".equals(catID)) {
            try {
                ItemCategory cat = ItemCategory.getCategory(catID);
                m = ItemMatcher.combine(m, ItemMatcher.byCategory(cat));
            } catch (Exception ignored) {}
        }
        if (m == null) {
            statusBase = snapshot.entries.size()
                + " containers. Type keyword or pick category.";
            currentHits.clear();
            page = 0;
            resultBox.clearComponents();
            rows.clear();
            updateStatus();
            return;
        }
        // A real search is always shown in the main (results) list
        List<SearchEngine.Hit> hits = SearchEngine.filter(snapshot, m, spawnParticles);
        statusBase = currentRadius + " tiles: " + hits.size() + "/" + snapshot.entries.size() + ".";
        refreshResultBox(hits);
    }

    // ---- Side panel (history / favorites icon walls) ----

    private SidePanel panel;

    public MainGame getMainGame() {
        return mainGame;
    }

    /** Toggle the side panel; same mode twice closes it. */
    public void togglePanel(SidePanel.PanelMode m) {
        try {
            if (panel != null) {
                if (panel.getMode() == m) {
                    hidePanel();
                    return;
                }
                panel.showMode(m);
                return;
            }
            panel = (SidePanel) mainGame.formManager.addComponent(new SidePanel(this));
            panel.showMode(m);
        } catch (Exception ignored) {}
    }

    public void hidePanel() {
        try {
            if (panel != null && mainGame.formManager != null) {
                mainGame.formManager.removeComponent(panel);
                try { panel.dispose(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        panel = null;
    }

    private void refreshPanel() {
        try {
            if (panel != null) panel.render();
        } catch (Exception ignored) {}
    }

    /** History icon click: load item ID into the search box and search. */
    public void searchFromHistory(String stringID) {
        defocusInput();
        if (stringID == null) return;
        try { textInput.setText(stringID); } catch (Exception ignored) {}
        try {
            categoryDropdown.setSelected("all", new StaticMessage("All categories"));
        } catch (Exception ignored) {}
        applyFilter(true);
    }

    /** U key: toggle favorite for the hovered icon button. Returns true if handled. */
    private boolean favHovered() {
        try {
            ItemIconButton found = findHoveredIcon(resultBox);
            if (found == null && panel != null) found = findHoveredIcon(panel.getBox());
            if (found == null || found.getItem() == null || found.getItem().item == null) return false;
            toggleFavItem(found.getItem());
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** Shared favorite toggle with status + panel refresh. */
    private void toggleFavItem(necesse.inventory.InventoryItem target) {
        if (target == null || target.item == null) return;
        try {
            String sid = target.item.getStringID();
            if (sid == null) return;
            boolean added = wims2.WimsData.toggleFavorite(sid);
            String name = sid;
            try { name = target.getItemDisplayName(); } catch (Exception ignored) {}
            statusLabel.setText((added ? "+ Fav: " : "- Fav: ") + name);
            refreshPanel();
        } catch (Exception ignored) {}
    }

    /**
     * U key fallback: favorite the item hovered in vanilla UI.
     * Walks every form registered on the form manager (backpack, toolbar,
     * equipment, open containers, crafting stations, modded UIs...),
     * so station fuel/material/output slots work too.
     */
    static boolean favVanilla(MainGame mainGame) {
        try {
            if (mainGame == null || mainGame.formManager == null) return false;
            necesse.gfx.forms.MainGameFormManager fm = mainGame.formManager;
            necesse.inventory.InventoryItem target = null;
            for (Object root : vanillaRoots(fm)) {
                target = findHoveredSlot(root);
                if (target != null) break;
            }
            if (target == null || target.item == null) {
                try {
                    java.util.ArrayList<Object> dbg = vanillaRoots(fm);
                    StringBuilder sb = new StringBuilder("ItemFinder: U-fav found nothing, roots=");
                    sb.append(dbg.size()).append(" [");
                    for (int i = 0; i < Math.min(12, dbg.size()); i++) {
                        if (i > 0) sb.append(',');
                        sb.append(dbg.get(i).getClass().getSimpleName());
                    }
                    sb.append("] hovering=[");
                    java.util.ArrayList<String> hov = new java.util.ArrayList<>();
                    for (Object root : dbg) debugHovering(root, hov);
                    for (int i = 0; i < Math.min(10, hov.size()); i++) {
                        if (i > 0) sb.append(',');
                        sb.append(hov.get(i));
                    }
                    sb.append(']');
                    System.out.println(sb.toString());
                } catch (Exception ignored) {}
                return false;
            }
            String sid = null;
            try { sid = target.item.getStringID(); } catch (Exception ignored) {}
            if (sid == null) return false;
            boolean added = wims2.WimsData.toggleFavorite(sid);
            String name = sid;
            try { name = target.getItemDisplayName(); } catch (Exception ignored) {}
            final String msg = (added ? "+ Fav: " : "- Fav: ") + name;
            try {
                if (instance != null) {
                    instance.statusLabel.setText(msg);
                    instance.refreshPanel();
                }
            } catch (Exception ignored) {}
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** Debug: list components currently reporting hover, with item presence. */
    private static void debugHovering(Object root, java.util.ArrayList<String> out) {
        if (root == null) return;
        final int[] counts = new int[3]; // visited, slots, recipes
        try {
            walkTree(root, new java.util.HashSet<Object>(), comp -> {
                if (out.size() >= 10) return;
                try {
                    counts[0]++;
                    if (comp instanceof necesse.gfx.forms.components.FormContainerRecipe
                        || comp instanceof necesse.gfx.forms.components.containerSlot.FormContainerSlot) {
                        boolean isRecipe = comp instanceof necesse.gfx.forms.components.FormContainerRecipe;
                        if (isRecipe) counts[2]++;
                        else counts[1]++;
                        if (hoverComp(comp)) {
                            out.add(comp.getClass().getSimpleName() + ":"
                                + (slotItem(comp) != null ? "item" : "empty"));
                        }
                    }
                } catch (Exception ignored) {}
            });
            out.add("~visited=" + counts[0] + " slots=" + counts[1] + " recipes=" + counts[2]);
        } catch (Exception ignored) {}
    }

    private static java.lang.reflect.Field[] vanillaRootFields;
    /** Every form/component registered on the form manager (cached field list). */
    private static java.util.ArrayList<Object> vanillaRoots(
        necesse.gfx.forms.MainGameFormManager fm) {
        java.util.ArrayList<Object> roots = new java.util.ArrayList<>();
        // Explicit core roots first: open-container field is typed as the
        // ContainerComponent interface (not FormComponent), so reflection
        // below would skip it. Same for any other interface-typed fields.
        try {
            if (fm.inventory != null) roots.add(fm.inventory);
            if (fm.toolbar != null) roots.add(fm.toolbar);
            if (fm.equipment != null) roots.add(fm.equipment);
            if (fm.focus != null) roots.add(fm.focus);
            if (fm.crafting != null) roots.add(fm.crafting);
        } catch (Exception ignored) {}
        try {
            if (vanillaRootFields == null) {
                java.util.ArrayList<java.lang.reflect.Field> all =
                    new java.util.ArrayList<>();
                Class<?> c = fm.getClass();
                while (c != null) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        try {
                            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            Class<?> t = f.getType();
                            if (necesse.gfx.forms.components.FormComponent.class.isAssignableFrom(t)
                                || necesse.gfx.forms.ContainerComponent.class.isAssignableFrom(t)
                                || java.util.Collection.class.isAssignableFrom(t)) {
                                f.setAccessible(true);
                                all.add(f);
                            }
                        } catch (Exception ignored) {}
                    }
                    c = c.getSuperclass();
                }
                vanillaRootFields = all.toArray(new java.lang.reflect.Field[0]);
            }
            for (java.lang.reflect.Field f : vanillaRootFields) {
                try {
                    Object v = f.get(fm);
                    if (v == null) continue;
                    if (v instanceof java.util.Collection) {
                        for (Object o : (java.util.Collection<?>) v) {
                            if (o != null && !roots.contains(o)) roots.add(o);
                        }
                    } else if (!roots.contains(v)) {
                        roots.add(v);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return roots;
    }

    /** Deep walk: getComponents() children PLUS hidden sub-forms held in fields. */
    private interface CompVisitor {
        void visit(Object comp);
    }

    private static void walkTree(Object root, java.util.Set<Object> visited, CompVisitor v) {
        if (root == null || visited.contains(root)) return;
        visited.add(root);
        try {
            if (root instanceof java.util.Collection) {
                for (Object o : (java.util.Collection<?>) root) walkTree(o, visited, v);
                return;
            }
            if (!(root instanceof necesse.gfx.forms.components.FormComponent)) return;
            // Hidden forms can't be hovered: skip the whole subtree
            // (stale hitboxes would false-positive on the game world behind)
            if (root instanceof necesse.gfx.forms.Form) {
                try {
                    if (((necesse.gfx.forms.Form) root).isHidden()) return;
                } catch (Exception ignored) {}
            }
            v.visit(root);
            if (root instanceof necesse.gfx.forms.ComponentListContainer) {
                try {
                    for (Object ch :
                        ((necesse.gfx.forms.ComponentListContainer<?>) root).getComponents()) {
                        walkTree(ch, visited, v);
                    }
                } catch (Exception ignored) {}
            }
            Class<?> c = root.getClass();
            while (c != null && c != Object.class
                && !c.getName().startsWith("java.")
                && !c.getName().startsWith("javax.")) {
                java.lang.reflect.Field[] fields;
                try { fields = c.getDeclaredFields(); }
                catch (Exception e) { break; }
                for (java.lang.reflect.Field f : fields) {
                    try {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        Class<?> t = f.getType();
                        if (necesse.gfx.forms.components.FormComponent.class.isAssignableFrom(t)
                            || java.util.Collection.class.isAssignableFrom(t)) {
                            f.setAccessible(true);
                            Object val = f.get(root);
                            if (val instanceof java.util.Collection) {
                                for (Object o : (java.util.Collection<?>) val) {
                                    walkTree(o, visited, v);
                                }
                            } else {
                                walkTree(val, visited, v);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {}
    }

    private static necesse.inventory.InventoryItem slotItem(Object comp) {
        try {
            if (comp instanceof necesse.gfx.forms.components.FormContainerRecipe) {
                necesse.gfx.forms.components.FormContainerRecipe rc =
                    (necesse.gfx.forms.components.FormContainerRecipe) comp;
                if (rc.recipe != null && rc.recipe.recipe != null) {
                    return rc.recipe.recipe.resultItem;
                }
                return null;
            }
            if (comp instanceof necesse.gfx.forms.components.containerSlot.FormContainerSlot) {
                necesse.gfx.forms.components.containerSlot.FormContainerSlot slot =
                    (necesse.gfx.forms.components.containerSlot.FormContainerSlot) comp;
                if (slot instanceof necesse.gfx.forms.components.containerSlot.FormContainerGhostItemSlot) {
                    necesse.inventory.InventoryItem ghost =
                        ((necesse.gfx.forms.components.containerSlot.FormContainerGhostItemSlot) slot).ghostItem;
                    if (ghost != null) return ghost;
                }
                try {
                    necesse.inventory.container.slots.ContainerSlot cs = slot.getContainerSlot();
                    if (cs != null && !cs.isClear()) return cs.getItem();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isHoveringComp(Object comp) {
        try {
            if (comp instanceof necesse.gfx.forms.components.FormContainerRecipe) {
                return ((necesse.gfx.forms.components.FormContainerRecipe) comp).isHovering();
            }
            if (comp instanceof necesse.gfx.forms.components.containerSlot.FormContainerSlot) {
                return ((necesse.gfx.forms.components.containerSlot.FormContainerSlot) comp).isHovering();
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Manual hover test: current mouse pos vs component hitboxes (HUD space).
     * Flags alone miss when no mouse-move event fired lately. */
    private static boolean mouseHoverComp(Object comp) {
        try {
            if (!(comp instanceof necesse.gfx.forms.components.FormComponent)) return false;
            necesse.engine.input.InputPosition mp = null;
            try { mp = necesse.engine.input.Input.mousePos; } catch (Exception ignored) {}
            if (mp == null) return false;
            java.util.List<java.awt.Rectangle> boxes =
                ((necesse.gfx.forms.components.FormComponent) comp).getHitboxes();
            if (boxes == null) return false;
            for (java.awt.Rectangle r : boxes) {
                if (r != null && r.contains(mp.hudX, mp.hudY)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hoverComp(Object comp) {
        return isHoveringComp(comp) || mouseHoverComp(comp);
    }

    private static necesse.inventory.InventoryItem findHoveredSlot(Object root) {
        final necesse.inventory.InventoryItem[] hit = { null };
        try {
            walkTree(root, new java.util.HashSet<Object>(), comp -> {
                if (hit[0] != null) return;
                if (!hoverComp(comp)) return;
                necesse.inventory.InventoryItem item = slotItem(comp);
                if (item != null) hit[0] = item;
            });
        } catch (Exception ignored) {}
        return hit[0];
    }

    private static ItemIconButton findHoveredIcon(FormContentBox box) {
        if (box == null) return null;
        try {
            for (Object o : box.getComponents()) {
                if (o instanceof ItemIconButton) {
                    ItemIconButton icon = (ItemIconButton) o;
                    try {
                        if (icon.isHovering()) return icon;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String statusBase = "";
    private void updateStatus() {
        try {
            String s = statusBase;
            if (totalPages() > 1) s += " Page " + (page + 1) + "/" + totalPages();
            statusLabel.setText(s);
        } catch (Exception ignored) {}
    }

    /** Item icons + dir + container icon + name + Ping button per row */
    private static class Row {
        SearchEngine.Hit hit;
        FormFairTypeLabel dirLabel;
        FormFairTypeLabel label;
    }
    private final java.util.ArrayList<Row> rows = new java.util.ArrayList<>();
    private long lastDistRefresh;
    private final java.util.ArrayList<SearchEngine.Hit> currentHits = new java.util.ArrayList<>();
    private int page;
    private static final int PAGE_SIZE = 4;

    private int totalPages() {
        return Math.max(1, (currentHits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** U key or middle-click on a result icon: toggle favorite */
    private void toggleFav(necesse.inventory.InventoryItem sample) {
        defocusInput();
        if (sample == null || sample.item == null) return;
        try {
            String sid = sample.item.getStringID();
            if (sid == null) return;
            boolean added = wims2.WimsData.toggleFavorite(sid);
            String name = sid;
            try { name = sample.getItemDisplayName(); } catch (Exception ignored) {}
            statusLabel.setText((added ? "+ Fav: " : "- Fav: ") + name);
            refreshPanel();
        } catch (Exception ignored) {}
    }

    private void refreshResultBox(List<SearchEngine.Hit> hits) {
        currentHits.clear();
        currentHits.addAll(hits);
        page = 0;
        showPage();
        lastDistRefresh = System.currentTimeMillis();
    }

    private void showPage() {
        if (page < 0) page = 0;
        if (page > totalPages() - 1) page = totalPages() - 1;
        resultBox.clearComponents();
        rows.clear();
        int y = 0;
        int boxW = getWidth() - 10;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, currentHits.size());
        for (int hi = from; hi < to; hi++) {
            final SearchEngine.Hit h = currentHits.get(hi);
            int x = 0;
            // Fixed 3 item slots so every row lines up; missing ones stay blank
            for (int i = 0; i < 3; i++) {
                try {
                    if (i < h.samples.size()) {
                        final necesse.inventory.InventoryItem sample = h.samples.get(i);
                        // Real button with the item sprite: pressable look + controller focus
                        ItemIconButton ib = new ItemIconButton(x, y, FormInputSize.SIZE_32,
                            ButtonColor.BASE, sample,
                            necesse.engine.Settings.UI.button_search_24,
                            new StaticMessage("Locate"));
                        resultBox.addComponent(ib);
                        ib.onClicked(e -> searchExact(sample));
                    } else {
                        resultBox.addComponent(new IconSpacer(x, y));
                    }
                } catch (Exception ignored) {}
                x += 36;
            }
            // Layout: [item][item][item] [dir] [container] name xN (dist) [Ping]
            int dx0 = h.tileX - snapshot.centerX;
            int dy0 = h.tileY - snapshot.centerY;
            FormFairTypeLabel dirLabel = null;
            try {
                dirLabel = new FormFairTypeLabel(dirText(dx0, dy0), x + 2, y + 8);
                dirLabel.setFontOptions(new FontOptions(14));
                dirLabel.setMaxWidth(72);
                resultBox.addComponent(dirLabel);
            } catch (Exception ignored) {}
            try {
                final SearchEngine.Hit ch = h;
                necesse.inventory.InventoryItem cItem = containerItem(h.objectStringID);
                if (cItem != null) {
                    ItemIconButton cb = new ItemIconButton(x + 76, y, FormInputSize.SIZE_32,
                        ButtonColor.BASE, cItem,
                        necesse.engine.Settings.UI.button_search_24,
                        new StaticMessage("Ping"));
                    resultBox.addComponent(cb);
                    cb.onClicked(e -> pingHit(ch));
                } else {
                    resultBox.addComponent(new IconSpacer(x + 76, y));
                }
            } catch (Exception ignored) {}
            x += 114;
            try {
                FormFairTypeLabel label = new FormFairTypeLabel(nameText(h, dx0, dy0), x + 4, y + 2);
                label.setFontOptions(new FontOptions(14));
                label.setMaxWidth(boxW - x - 70); // leave room for Ping button
                resultBox.addComponent(label);
                FormTextButton ping = new FormTextButton(
                    "Ping", boxW - 58, y + 4, 54, FormInputSize.SIZE_32, ButtonColor.BASE);
                resultBox.addComponent(ping);
                final SearchEngine.Hit fh = h;
                ping.onClicked(e -> pingHit(fh));
                Row r = new Row();
                r.hit = h;
                r.dirLabel = dirLabel;
                r.label = label;
                rows.add(r);
            } catch (Exception ignored) {}
            y += 40;
        }
        if (currentHits.isEmpty()) {
            FormFairTypeLabel empty = new FormFairTypeLabel("Nothing found. Try Scan.", 0, 0);
            empty.setFontOptions(new FontOptions(16));
            resultBox.addComponent(empty);
        }
        updateStatus();
        lastDistRefresh = System.currentTimeMillis();
    }

    private static String dirText(int dx, int dy) {
        int dist = Math.max(Math.abs(dx), Math.abs(dy));
        return "[" + dirOf(dx, dy) + " " + dist + "]";
    }

    private static String nameText(SearchEngine.Hit h, int dx, int dy) {
        return h.containerName + " x" + h.totalAmount;
    }

    private static String rowText(SearchEngine.Hit h, int dx, int dy) {
        return dirText(dx, dy) + " " + nameText(h, dx, dy);
    }

    /** Dynamic shrink-ring ping (row Ping button and container icon share this) */
    private void pingHit(SearchEngine.Hit h) {
        defocusInput();
        try {
            TargetMarker.ping(mainGame.getClient().getLevel(), h.tileX, h.tileY);
        } catch (Exception ignored) {}
    }

    /** Resolve the container's own item (e.g. storagebox object -> storagebox item) for its icon */
    private static necesse.inventory.InventoryItem containerItem(String objectStringID) {
        if (objectStringID == null || objectStringID.isEmpty()) return null;
        try {
            necesse.inventory.item.Item item =
                necesse.engine.registries.ItemRegistry.getItem(objectStringID);
            if (item != null) return new necesse.inventory.InventoryItem(item);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Icon click = static Scan-style marker only. The result list is left
     * untouched. Shared by result icons and favorites panel icons.
     */
    public void searchExact(necesse.inventory.InventoryItem sample) {
        defocusInput();
        if (snapshot == null || sample == null || sample.item == null) return;
        try {
            wims2.MarkerRegistry.clear(); // wipe previous batch before marking
            final int id = sample.item.getID();
            try {
                String sid = sample.item.getStringID();
                if (sid != null) {
                    wims2.WimsData.addHistoryItem(sid); // record viewed item
                    refreshPanel();
                }
            } catch (Exception ignored) {}
            ItemMatcher m = inv -> inv != null && inv.item != null && inv.item.getID() == id;
            // spawn=false: no pillars, we mark manually below (max 8)
            List<SearchEngine.Hit> hits = SearchEngine.filter(snapshot, m, false);
            try {
                necesse.level.maps.Level level = mainGame.getClient().getLevel();
                int n = 0;
                for (SearchEngine.Hit h : hits) {
                    if (n++ >= 8) break;
                    wims2.ParticleSpawner.markStatic(level, h.tileX, h.tileY);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /** Live distance: recompute from current player pos while the form is open */
    private void refreshDistances() {
        if (rows.isEmpty() || mainGame.getClient() == null) return;
        try {
            int px = mainGame.getClient().getPlayer().getTileX();
            int py = mainGame.getClient().getPlayer().getTileY();
            for (Row r : rows) {
                try {
                    int dx = r.hit.tileX - px, dy = r.hit.tileY - py;
                    if (r.dirLabel != null) r.dirLabel.setText(dirText(dx, dy));
                    r.label.setText(nameText(r.hit, dx, dy));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Compass from player to container. Unicode arrows were tried and the
     * game font renders them as boxes, so ASCII compass it is. Combined
     * with the gold world pillars, direction is easy to follow.
     */
    private static String dirOf(int dx, int dy) {
        if (dx == 0 && dy == 0) return "HERE";
        int ax = Math.abs(dx), ay = Math.abs(dy);
        String ns = dy < 0 ? "N" : (dy > 0 ? "S" : "");
        String ew = dx > 0 ? "E" : (dx < 0 ? "W" : "");
        if (ax == 0) return ns;
        if (ay == 0) return ew;
        if (ax >= 2 * ay) return ew;
        if (ay >= 2 * ax) return ns;
        return ns + ew; // e.g. NE, SW
    }

    public void onCancel() {
        RangeOverlay.hide();
        TargetMarker.stop();
        hidePanel();
        try { wims2.MarkerRegistry.clear(); } catch (Exception ignored) {}
        try { savedX = getX(); savedY = getY(); } catch (Exception ignored) {}
        savePos();
        if (mainGame.formManager != null) mainGame.formManager.removeComponent(this);
        try { dispose(); } catch (Exception ignored) {} // ?��? WIMS ?��??�件套�?移除+?�放
        instance = null;
    }

    // ---- Window position: clamp on screen, remember, default to left ----

    private static Integer savedX = null, savedY = null;
    private static boolean posLoaded = false;

    private static String posFile() {
        try { return necesse.engine.GlobalData.cfgPath() + "itemfinderpos.cfg"; }
        catch (Exception e) { return null; }
    }

    private static void loadPos() {
        if (posLoaded) return;
        posLoaded = true;
        String f = posFile();
        if (f == null) return;
        try {
            java.util.Properties p = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { p.load(in); }
            savedX = Integer.parseInt(p.getProperty("x"));
            savedY = Integer.parseInt(p.getProperty("y"));
        } catch (Exception ignored) {}
    }

    private static void savePos() {
        if (savedX == null || savedY == null) return;
        String f = posFile();
        if (f == null) return;
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("x", String.valueOf(savedX));
            p.setProperty("y", String.valueOf(savedY));
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                p.store(out, "ItemFinder window pos");
            }
        } catch (Exception ignored) {}
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Called on open: saved pos if any, else left-middle default */
    private void restorePosition(GameWindow window) {
        loadPos();
        int winW = 1280, winH = 720;
        try { winW = window.getWidth(); winH = window.getHeight(); } catch (Exception ignored) {}
        int x, y;
        if (savedX != null && savedY != null) { x = savedX; y = savedY; }
        else { x = 10; y = Math.max(0, winH / 2 - getHeight() / 2); }
        x = clamp(x, 0, Math.max(0, winW - getWidth()));
        y = clamp(y, 0, Math.max(0, winH - getHeight()));
        setPosition(new FormFixedPosition(x, y));
    }

    /** Called every tick while open: never let it leave the screen */
    private void clampToScreen(GameWindow window) {
        try {
            int winW = window.getWidth(), winH = window.getHeight();
            int nx = clamp(getX(), 0, Math.max(0, winW - getWidth()));
            int ny = clamp(getY(), 0, Math.max(0, winH - getHeight()));
            if (nx != getX() || ny != getY()) setPosition(new FormFixedPosition(nx, ny));
            savedX = nx;
            savedY = ny;
        } catch (Exception ignored) {}
    }

    private void positionAboveInventory() {
        // Same as original WIMS: above player inventory, no MainGame.getWindow() (not in 1.3)
        try {
            if (mainGame.formManager != null && mainGame.formManager.inventory != null) {
                int ix = mainGame.formManager.inventory.getX();
                int iy = mainGame.formManager.inventory.getY() - getHeight() - 10;
                setPosition(new FormFixedPosition(ix, iy));
                return;
            }
        } catch (Exception ignored) {}
        setPosMiddle(0, -100);
    }

    public static void frameTick(MainGame mainGame, TickManager tm, GameWindow window) {
        try {
            if (instance != null) {
                RangeOverlay.tick(mainGame.getClient());
                TargetMarker.tick(mainGame.getClient());
                instance.clampToScreen(window);
                if (instance.panel != null) instance.panel.followMain(window);
                // Live distance refresh, twice a second
                if (System.currentTimeMillis() - instance.lastDistRefresh > 500) {
                    instance.lastDistRefresh = System.currentTimeMillis();
                    instance.refreshDistances();
                }
            }
        } catch (Exception ignored) {}
        if (mainGame.getClient() == null || mainGame.getClient().getPlayer() == null) return;
        // U key (works with our window open or closed, skipped while typing):
        // our icons first, else vanilla backpack/chest/cursor item.
        try {
            if (wims2.ModMain.favControl != null && wims2.ModMain.favControl.isPressed()
                && !necesse.gfx.forms.components.FormTypingComponent.isCurrentlyTyping()) {
                boolean done = false;
                if (instance != null) done = instance.favHovered();
                if (!done) favVanilla(mainGame);
            }
        } catch (Exception ignored) {}
        if (!mainGame.formManager.pauseMenu.isHidden()) {
            if (instance != null) instance.onCancel();
            return;
        }
        if (ModMain.openSearchControl != null && ModMain.openSearchControl.isPressed()) {
            if (instance == null) {
                instance = (SearchForm) mainGame.formManager.addComponent(new SearchForm(mainGame));
                instance.restorePosition(window);
            } else {
                instance.onCancel();
            }
        }
    }
}
