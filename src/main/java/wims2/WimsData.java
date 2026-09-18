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
        // One-time migration from pre-rebrand wims2* files, so old
        // favorites/history survive the rename instead of looking lost.
        migrate("wims2fav.cfg", "itemfinderfav.cfg");
        migrate("wims2histitems.cfg", "itemfinderhistitems.cfg");
        migrate("wims2pos.cfg", "itemfinderpos.cfg");
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
        System.out.println("ItemFinder: loaded " + history.size()
            + " history, " + favorites.size() + " favorites.");
    }

    /** Copy old file to new path once, if the new one is missing/empty. */
    private static void migrate(String oldName, String newName) {
        try {
            String o = cfg(oldName), n = cfg(newName);
            if (o == null || n == null || o.equals(n)) return;
            java.io.File of = new java.io.File(o);
            java.io.File nf = new java.io.File(n);
            if (!of.exists() || of.length() == 0) return;
            if (nf.exists() && nf.length() > 0) return; // new data wins
            try (java.io.FileInputStream in = new java.io.FileInputStream(of);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(nf)) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            System.out.println("ItemFinder: migrated " + oldName + " -> " + newName);
        } catch (Exception ignored) {}
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
        writeLines(cfg("itemfinderhistitems.cfg"), history);
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
        writeLines(cfg("itemfinderfav.cfg"), new ArrayList<>(favorites));
    }

    /**
     * Atomic write (tmp + rename) so a crash mid-save can never leave
     * a truncated empty file behind.
     */
    private static void writeLines(String path, java.util.Collection<String> lines) {
        if (path == null) return;
        try {
            java.io.File tmp = new java.io.File(path + ".tmp");
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                for (String s : lines) pw.println(s);
            }
            java.io.File dst = new java.io.File(path);
            if (!tmp.renameTo(dst)) {
                // Fallback for filesystems where rename fails: direct overwrite
                try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(dst), StandardCharsets.UTF_8))) {
                    for (String s : lines) pw.println(s);
                }
            }
        } catch (Exception e) {
            System.out.println("ItemFinder: save failed for " + path + " (" + e + ")");
        }
    }

    // ---- UI options (checkbox states) ----

    private static final java.util.Properties opts = new java.util.Properties();
    private static boolean optsLoaded = false;

    private static synchronized void loadOpts() {
        if (optsLoaded) return;
        optsLoaded = true;
        String f = cfg("itemfinderopts.cfg");
        if (f == null) return;
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            opts.load(in);
        } catch (Exception ignored) {}
    }

    public static synchronized boolean getOpt(String key, boolean def) {
        loadOpts();
        try {
            String v = opts.getProperty(key);
            return v == null ? def : Boolean.parseBoolean(v);
        } catch (Exception e) {
            return def;
        }
    }

    public static synchronized void setOpt(String key, boolean value) {
        loadOpts();
        try {
            opts.setProperty(key, String.valueOf(value));
            String f = cfg("itemfinderopts.cfg");
            if (f == null) return;
            try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                opts.store(out, "ItemFinder options");
            }
        } catch (Exception e) {
            System.out.println("ItemFinder: save failed for options (" + e + ")");
        }
    }

    public static synchronized int getOptInt(String key, int def) {
        loadOpts();
        try {
            String v = opts.getProperty(key);
            return v == null ? def : Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    public static synchronized void setOptInt(String key, int value) {
        loadOpts();
        try {
            opts.setProperty(key, String.valueOf(value));
            String f = cfg("itemfinderopts.cfg");
            if (f == null) return;
            try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                opts.store(out, "ItemFinder options");
            }
        } catch (Exception e) {
            System.out.println("ItemFinder: save failed for options (" + e + ")");
        }
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
