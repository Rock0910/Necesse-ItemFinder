package wims2;

import necesse.engine.network.client.Client;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Target ping: flat ring shrinking into a small ring at the tile.
 * Stays on the ground (fixed height, no upward drift), semi-transparent.
 * Supports several simultaneous pings (icon clicks); oldest dropped past cap.
 * Pure client-side. Called every frame from SearchForm.frameTick.
 */
public class TargetMarker {

    private static class Ping {
        int tileX, tileY;
        long startTime;
        long lastSpawn;
    }

    private static final ArrayList<Ping> pings = new ArrayList<>();
    private static final long DURATION = 2000;
    private static final int MAX_PINGS = 8;

    public static void ping(Level level, int tx, int ty) {        if (level == null) return;
        Ping p = new Ping();
        p.tileX = tx;
        p.tileY = ty;
        p.startTime = System.currentTimeMillis();
        p.lastSpawn = 0;
        synchronized (pings) {
            pings.add(p);
            while (pings.size() > MAX_PINGS) pings.remove(0);
        }
    }

    /** Clear all active pings (e.g. when the form closes) */
    public static void stop() {
        synchronized (pings) { pings.clear(); }
    }

    public static void tick(Client client) {
        if (pings.isEmpty() || client == null) return;
        Level level = client.getLevel();
        if (level == null || level.entityManager == null) {
            synchronized (pings) { pings.clear(); }
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (pings) {
            Iterator<Ping> it = pings.iterator();
            while (it.hasNext()) {
                Ping p = it.next();
                float t = (now - p.startTime) / (float) DURATION;
                if (t >= 1.0f) { it.remove(); continue; }
                if (now - p.lastSpawn < 60) continue;
                p.lastSpawn = now;
                // Big -> small: 2.5 tiles down to 0.4 tiles, flat on the ground
                float rTiles = 2.5f - 2.1f * t;
                float cx = p.tileX * 32 + 16, cy = p.tileY * 32 + 16;
                int points = 20;
                int size = (int) (16 - 7 * t);
                for (int i = 0; i < points; i++) {
                    double a = 2 * Math.PI * i / points;
                    float px = cx + (float) (Math.cos(a) * rTiles * 32);
                    float py = cy + (float) (Math.sin(a) * rTiles * 32);
                    try {
                        // Alternating natural sprites: gold star / blue sapphire
                        // (tinting the yellow star blue would multiply dark)
                        boolean gold = (i % 2 == 0);
                        level.entityManager.addTopParticle(px, py,
                            necesse.entity.particle.Particle.GType.COSMETIC)
                            .sprite(gold
                                ? necesse.gfx.GameResources.starParticles
                                : necesse.gfx.GameResources.sapphireShardParticles)
                            .ignoreLight(true)
                            .alpha(0.85f)
                            .sizeFadesInAndOut(Math.max(6, size - 4), size, 0.5f)
                            .lifeTime(450)
                            .height(3.0f); // fixed: stays on the tile, no drift
                    } catch (Exception ignored) {}
                }
                // Center cluster like Scan: static stars on the container middle
                for (int i = 0; i < 4; i++) {
                    try {
                        boolean gold = (i % 2 == 0);
                        level.entityManager.addTopParticle(
                            cx + (i - 1.5f) * 8, cy - 4,
                            necesse.entity.particle.Particle.GType.COSMETIC)
                            .sprite(gold
                                ? necesse.gfx.GameResources.starParticles
                                : necesse.gfx.GameResources.sapphireShardParticles)
                            .ignoreLight(true)
                            .alpha(0.9f)
                            .sizeFadesInAndOut(12, 18, 0.5f)
                            .lifeTime(450)
                            .height(10.0f);
                    } catch (Exception ignored) {}
                }
                // Dotted guide line from the player toward the target:
                // real graphical direction, no font needed
                try {
                    java.awt.Point pp = client.getPlayer().getMapPos();
                    float sx = pp.x, sy = pp.y;
                    float dx = cx - sx, dy = cy - sy;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 32) {
                        // Denser: a dot every half tile so the line reads solid
                        int steps = Math.min(48, Math.max(4, (int) (len / 16)));
                        for (int i = 1; i <= steps; i++) {
                            float f = i / (float) (steps + 1);
                            try {
                                boolean gold = (i % 2 == 0);
                                level.entityManager.addTopParticle(
                                    sx + dx * f, sy + dy * f,
                                    necesse.entity.particle.Particle.GType.COSMETIC)
                                    .sprite(gold
                                        ? necesse.gfx.GameResources.starParticles
                                        : necesse.gfx.GameResources.sapphireShardParticles)
                                    .ignoreLight(true)
                                    .alpha(0.85f)
                                    .sizeFadesInAndOut(8, 12, 0.5f)
                                    .lifeTime(450)
                                    .height(3.0f);
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
