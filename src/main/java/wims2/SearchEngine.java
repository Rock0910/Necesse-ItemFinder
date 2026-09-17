package wims2;

import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.List;

public class SearchEngine {

    public static class Hit {
        public final int tileX, tileY;
        public final String containerName;
        public final String objectStringID; // e.g. storagebox: resolves the container's item icon
        public int totalAmount;
        public int distance;
        /** 每種找到的物品各一組，給 UI 畫圖示用（FormItemIcon 需要 InventoryItem） */
        public final java.util.ArrayList<InventoryItem> samples = new java.util.ArrayList<>();
        public Hit(int x, int y, String name, String objectID) {
            this.tileX = x; this.tileY = y; this.containerName = name; this.objectStringID = objectID;
        }
        @Override public String toString() {
            return containerName + " @(" + tileX + "," + tileY + ") x" + totalAmount + " (" + distance + "格)";
        }
    }

    /** 範圍快照：一格容器 + 庫存拷貝。UI 的關鍵字/分類過濾只跑這份，不再碰 level。 */
    public static class Snapshot {
        public final int centerX, centerY, radius;
        public final long takenAt = System.currentTimeMillis();
        public final List<Entry> entries = new ArrayList<>();
        Snapshot(int x, int y, int r) { centerX = x; centerY = y; radius = r; }
    }

    public static class Entry {
        public final int tileX, tileY;
        public final String containerName;
        public final String objectStringID;
        public final ObjectEntity entity; // 只拿來噴粒子用，不讀即時庫存
        public final List<InventoryItem> items = new ArrayList<>(); // copy()，可安全畫圖示
        Entry(int x, int y, String n, String objectID, ObjectEntity e) {
            tileX = x; tileY = y; containerName = n; objectStringID = objectID; entity = e;
        }
    }

    /**
     * 快照：一次拿範圍內容器 + 內容物拷貝。
     * 用 region 範圍查詢（getInRegionRangeByTile），不是全圖 foreach。
     * 失敗才退回舊式 iterator 全掃。
     */
    public static Snapshot snapshot(Client client, int radius) {
        PlayerMob player = client == null ? null : client.getPlayer();
        Level level = client == null ? null : client.getLevel();
        if (player == null || level == null) return new Snapshot(0, 0, radius);
        int px = player.getTileX(), py = player.getTileY();
        Snapshot snap = new Snapshot(px, py, radius);
        List<?> inRange = null;
        try {
            // 1.3 TileEntityList 有這個方法：只回範圍內的，不用全圖比座標
            inRange = level.entityManager.objectEntities.getInRegionRangeByTile(px, py, radius);
        } catch (Exception ignored) {}
        if (inRange != null) {
            for (Object o : inRange) collect(snap, o);
        } else {
            // 舊版相容退路：全圖 iterator + 座標 early-out
            for (Object o : level.entityManager.objectEntities) {
                if (!(o instanceof ObjectEntity)) continue;
                ObjectEntity oe = (ObjectEntity) o;
                if (Math.abs(oe.tileX - px) > radius || Math.abs(oe.tileY - py) > radius) continue;
                collect(snap, o);
            }
        }
        return snap;
    }

    private static void collect(Snapshot snap, Object o) {
        if (!(o instanceof ObjectEntity)) return;
        ObjectEntity oe = (ObjectEntity) o;
        if (!(oe instanceof OEInventory)) return; // Vault / 模組箱都吃得到
        necesse.inventory.Inventory inv = ((OEInventory) oe).getInventory();
        if (inv == null) return;
        // 顯示名：不要用 oe.toString()（會吐出類別路徑）。
        // 優先自訂名（玩家改過的箱子名），再退到物件本身的本地化名。
        String name = null;
        try {
            if (oe instanceof necesse.entity.objectEntity.InventoryObjectEntity) {
                necesse.entity.objectEntity.InventoryObjectEntity ioe =
                    (necesse.entity.objectEntity.InventoryObjectEntity) oe;
                if (ioe.canSetInventoryName()) {
                    try {
                        necesse.engine.localization.message.GameMessage gm = ioe.getInventoryName();
                        if (gm != null) {
                            String t = gm.translate();
                            if (t != null && !t.isEmpty()) name = t;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (name == null) {
            try {
                necesse.level.gameObject.GameObject go = oe.getObject();
                if (go != null) name = go.getDisplayName();
            } catch (Exception ignored) {}
        }
        if (name == null || name.isEmpty()) name = "Container";
        String objectID = null;
        try {
            if (oe.getObject() != null) objectID = oe.getObject().getStringID();
        } catch (Exception ignored) {}
        Entry e = new Entry(oe.tileX, oe.tileY, name, objectID, oe);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.isSlotClear(i)) continue;
            InventoryItem item = inv.getItem(i);
            if (item == null || item.item == null) continue;
            try { e.items.add(item.copy()); } catch (Exception ex) { e.items.add(item); }
        }
        if (!e.items.isEmpty()) snap.entries.add(e);
    }

    /** 在快照上過濾（打字、切分類都走這裡，不碰 level，所以即時）。 */
    public static List<Hit> filter(Snapshot snap, ItemMatcher matcher, boolean spawnParticles) {
        List<Hit> hits = new ArrayList<>();
        if (snap == null || matcher == null) return hits;
        // New search action: wipe the previous batch first (once, not per container)
        if (spawnParticles) {
            try { MarkerRegistry.clear(); } catch (Exception ignored) {}
        }
        for (Entry e : snap.entries) {
            int found = 0;
            java.util.HashMap<Integer, InventoryItem> sampleById = new java.util.HashMap<>();
            for (InventoryItem item : e.items) {
                if (matcher.matches(item)) {
                    found += item.getAmount();
                    sampleById.putIfAbsent(item.item.getID(), item);
                }
            }
            if (found > 0) {
                Hit h = new Hit(e.tileX, e.tileY, e.containerName, e.objectStringID);
                h.totalAmount = found;
                h.distance = Math.max(Math.abs(e.tileX - snap.centerX), Math.abs(e.tileY - snap.centerY));
                h.samples.addAll(sampleById.values());
                hits.add(h);
                if (spawnParticles) { try { ParticleSpawner.success(e.entity); } catch (Exception ignored) {} }
            }
        }
        hits.sort((a, b) -> Integer.compare(a.distance, b.distance));
        return hits;
    }

    /** 舊版相容：按一下直接掃 + 噴粒子（不留快照）。新 UI 請用 snapshot()+filter()。 */
    public static List<Hit> query(Client client, ItemMatcher matcher, int radius) {
        Snapshot snap = snapshot(client, radius);
        List<Hit> hits = filter(snap, matcher, true);
        if (hits.isEmpty() && client != null && client.getPlayer() != null) {
            try { ParticleSpawner.fail(client.getPlayer()); } catch (Exception ignored) {}
        }
        return hits;
    }
}
