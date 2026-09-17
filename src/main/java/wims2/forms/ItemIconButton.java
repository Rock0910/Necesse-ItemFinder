package wims2.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;

/**
 * A real button that shows an item sprite instead of a UI icon.
 * Looks pressable, eats controller focus/confirm like any button,
 * and keeps the compact hover card (name / ID / EN / category).
 * Clicks are consumed so they never fall through to the game.
 */
public class ItemIconButton extends FormContentIconButton {

    private final InventoryItem item;
    private PlayerMob lastPlayer;

    public ItemIconButton(int x, int y, FormInputSize size, ButtonColor color,
                          InventoryItem item,
                          necesse.gfx.ui.ButtonTexture dummyIcon,
                          necesse.engine.localization.message.GameMessage tooltip) {
        super(x, y, size, color, dummyIcon, tooltip);
        this.item = item;
    }

    public InventoryItem getItem() {
        return item;
    }

    @Override
    public void draw(TickManager tm, PlayerMob player, java.awt.Rectangle r) {
        lastPlayer = player;
        super.draw(tm, player, r);
    }

    @Override
    protected void drawContent(int x, int y, int w, int h) {
        try {
            if (item != null && lastPlayer != null) {
                // Center the 32px sprite in the content area
                item.draw(lastPlayer, x + (w - 32) / 2, y + (h - 32) / 2, false);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void addTooltips(PlayerMob player) {
        // Compact card: display name / stringID / English original / category.
        try {
            if (!isHovering() || item == null || item.item == null) return;
            try {
                String display = item.getItemDisplayName();
                if (display != null && !display.isEmpty()) addLine(display);
            } catch (Exception ignored) {}
            try {
                String sid = item.item.getStringID();
                if (sid != null && !sid.isEmpty()) addLine("ID: " + sid);
            } catch (Exception ignored) {}
            try {
                necesse.engine.localization.message.GameMessage loc =
                    item.item.getLocalization(item);
                if (loc != null) {
                    String en = loc.translateDebug(
                        necesse.engine.localization.Localization.English);
                    if (en != null && !en.isEmpty()) addLine("EN: " + en);
                }
            } catch (Exception ignored) {}
            try {
                necesse.inventory.item.ItemCategory c =
                    necesse.inventory.item.ItemCategory.getItemsCategory(item.item);
                if (c != null) {
                    String path;
                    try {
                        path = String.join(" > ", c.getStringIDTree(true));
                    } catch (Exception ex) {
                        path = c.stringID;
                    }
                    if (path != null && !path.isEmpty()) addLine("Category: " + path);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void addLine(String text) {
        necesse.gfx.gameTooltips.GameTooltipManager.addTooltip(
            new necesse.gfx.gameTooltips.StringTooltips(text),
            necesse.gfx.gameTooltips.TooltipLocation.FORM_FOCUS);
    }
}
