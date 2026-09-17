package wims2;

public interface ItemMatcher {
    boolean matches(necesse.inventory.InventoryItem invItem);

    /**
     * Three-track matching, so both languages work no matter the game language:
     * 1. Current-language display name (e.g. Chinese when playing in Chinese)
     * 2. English display name via translateDebug(English) (base language, always present)
     * 3. Internal stringID, e.g. copperbar, bread, healthpotion
     * 4. Category names incl. sub-categories (any language + ID), e.g. "ore", "礦石"
     * Spaces are ignored on both sides, so "copperbar" matches "Copper Bar".
     */
    static ItemMatcher byName(String keyword) {
        String k = keyword.toLowerCase().trim();
        String kFlat = k.replace(" ", "");
        java.util.Set<necesse.inventory.item.ItemCategory> cats = CategorySearch.find(k, kFlat);
        return invItem -> {
            if (invItem == null || invItem.item == null) return false;
            // 1. current language
            try {
                String display = invItem.getItemDisplayName();
                if (display != null) {
                    String d = display.toLowerCase();
                    if (d.contains(k) || d.replace(" ", "").contains(kFlat)) return true;
                }
            } catch (Exception ignored) {}
            // 2. English (base language, works even when the game is in Chinese)
            try {
                necesse.engine.localization.message.GameMessage loc =
                    invItem.item.getLocalization(invItem);
                if (loc != null) {
                    String en = loc.translateDebug(necesse.engine.localization.Localization.English);
                    if (en != null) {
                        String e = en.toLowerCase();
                        if (e.contains(k) || e.replace(" ", "").contains(kFlat)) return true;
                    }
                }
            } catch (Exception ignored) {}
            // 3. internal ID
            try {
                String sid = invItem.item.getStringID();
                if (sid != null && sid.toLowerCase().replace(" ", "").contains(kFlat)) return true;
            } catch (Exception ignored) {}
            // 4. category (or any parent) name matched the keyword
            if (!cats.isEmpty()) {
                try {
                    necesse.inventory.item.ItemCategory c =
                        necesse.inventory.item.ItemCategory.getItemsCategory(invItem.item);
                    if (c != null) {
                        for (necesse.inventory.item.ItemCategory mc : cats) {
                            if (c == mc || c.isOrHasParent(mc.stringID)
                                || mc.containsItemOrInChildren(invItem.item)) return true;
                        }
                    }
                } catch (Exception ignored) {}
            }
            return false;
        };
    }

    static ItemMatcher byCategory(necesse.inventory.item.ItemCategory category) {
        return invItem -> {
            if (invItem == null || invItem.item == null || category == null) return false;
            necesse.inventory.item.ItemCategory c =
                necesse.inventory.item.ItemCategory.getItemsCategory(invItem.item);
            if (c == null) return false;
            // include sub-categories
            return c == category || c.isOrHasParent(category.stringID)
                || category.containsItemOrInChildren(invItem.item);
        };
    }

    static ItemMatcher combine(ItemMatcher a, ItemMatcher b) {
        if (a == null) return b;
        if (b == null) return a;
        return invItem -> a.matches(invItem) && b.matches(invItem);
    }
}
