package wims2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Item history + item favorites, persisted in the client cfg folder.
 * Both store item stringIDs (stable across languages/sessions).
 * History: most-recent-first item icons (from icon clicks).
 * Favorites: middle-click (or U key) toggles.
 */
public class WimsData {

    private static final int HISTORY_CAP = 48;
    private static final ArrayList<String> history = new ArrayList<>();
    private static final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private static boolean loaded = false;

    private static String cfg(String name) {
        try { return necesse.engine.GlobalData.cfgPath() + name; }
        catch (Exception e) { return null; }
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        String hf = cfg("itemfinderhistitems.cfg");
        if (hf != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(hf), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && history.size() < HISTORY_CAP) {
                    line = line.trim();
                    if (!line.isEmpty() && !history.contains(line)) history.add(line);
                }
            } catch (Exception ignored) {}
        }
        String ff = cfg("itemfinderfav.cfg");
        if (ff != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(ff), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) favorites.add(line);
                }
            } catch (Exception ignored) {}
        }
    }

    /** Record a viewed item (most recent first, capped). Returns true if list changed. */
    public static synchronized boolean addHistoryItem(String stringID) {
        load();
        if (stringID == null || stringID.isEmpty()) return false;
        history.remove(stringID);
        history.add(0, stringID);
        while (history.size() > HISTORY_CAP) history.remove(history.size() - 1);
        saveHistory();
        return true;
    }

    public static synchronized List<String> getHistoryItems() {
        load();
        return new ArrayList<>(history);
    }

    public static synchronized void clearHistory() {
        load();
        history.clear();
        saveHistory();
    }

    private static synchronized void saveHistory() {
        String hf = cfg("itemfinderhistitems.cfg");
        if (hf == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(hf), StandardCharsets.UTF_8))) {
            for (String s : history) pw.println(s);
        } catch (Exception ignored) {}
    }

    /** Toggle favorite. Returns true if now favorited. */
    public static synchronized boolean toggleFavorite(String stringID) {
        load();
        if (stringID == null || stringID.isEmpty()) return false;
        boolean added;
        if (favorites.contains(stringID)) {
            favorites.remove(stringID);
            added = false;
        } else {
            favorites.add(stringID);
            added = true;
        }
        saveFavorites();
        return added;
    }

    public static synchronized boolean isFavorite(String stringID) {
        load();
        return stringID != null && favorites.contains(stringID);
    }

    public static synchronized List<String> getFavorites() {
        load();
        return new ArrayList<>(favorites);
    }

    private static synchronized void saveFavorites() {
        String ff = cfg("itemfinderfav.cfg");
        if (ff == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(ff), StandardCharsets.UTF_8))) {
            for (String s : favorites) pw.println(s);
        } catch (Exception ignored) {}
    }

    /** Resolve a stored stringID to a displayable InventoryItem (null if unknown). */
    public static necesse.inventory.InventoryItem resolveItem(String stringID) {
        if (stringID == null || stringID.isEmpty()) return null;
        try {
            necesse.inventory.item.Item item =
                necesse.engine.registries.ItemRegistry.getItem(stringID);
            if (item != null) return new necesse.inventory.InventoryItem(item);
        } catch (Exception ignored) {}
        return null;
    }
}
