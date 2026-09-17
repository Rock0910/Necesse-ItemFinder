package wims2;

/** Category name lookup: walks every ItemCategoryManager tree. */
class CategorySearch {

    static java.util.Set<necesse.inventory.item.ItemCategory> find(String k, String kFlat) {
        java.util.Set<necesse.inventory.item.ItemCategory> out = new java.util.HashSet<>();
        try {
            necesse.inventory.item.ItemCategoryManager[] mgrs = {
                necesse.inventory.item.ItemCategory.masterManager,
                necesse.inventory.item.ItemCategory.equipmentManager,
                necesse.inventory.item.ItemCategory.craftingManager,
                necesse.inventory.item.ItemCategory.foodQualityManager,
            };
            java.util.ArrayDeque<necesse.inventory.item.ItemCategory> stack = new java.util.ArrayDeque<>();
            for (necesse.inventory.item.ItemCategoryManager mgr : mgrs) {
                if (mgr == null || mgr.masterCategory == null) continue;
                stack.push(mgr.masterCategory);
                while (!stack.isEmpty()) {
                    necesse.inventory.item.ItemCategory c = stack.pop();
                    if (c.stringID != null) {
                        String id = c.stringID.toLowerCase();
                        if (id.contains(kFlat)) { out.add(c); }
                        else {
                            // current-language + English display names
                            boolean hit = false;
                            try {
                                if (c.displayName != null) {
                                    String d = c.displayName.translate();
                                    if (d != null) {
                                        String dl = d.toLowerCase();
                                        if (dl.contains(k) || dl.replace(" ", "").contains(kFlat)) hit = true;
                                    }
                                    if (!hit) {
                                        String en = c.displayName.translateDebug(
                                            necesse.engine.localization.Localization.English);
                                        if (en != null) {
                                            String el = en.toLowerCase();
                                            if (el.contains(k) || el.replace(" ", "").contains(kFlat)) hit = true;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                            if (hit) out.add(c);
                        }
                    }
                    try {
                        for (necesse.inventory.item.ItemCategory child : c.getChildren()) {
                            if (child != null) stack.push(child);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
