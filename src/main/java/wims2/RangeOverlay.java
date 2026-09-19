package wims2;

import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;

/**
 * Range overlay: square border around the snapshot center, drawn with
 * world particles. Pure client-side, stops when the form closes.
 *
 * NOTE: the border is radius tiles away from the player, so with a big
 * radius most of it is off-screen. Default radius is 16 so the border
 * sits roughly at the screen edge and is actually visible.
 */
public class RangeOverlay {

    private static int centerX, centerY, radius;
    private static boolean visible;
    private static long lastSpawn;

    public static void show(int x, int y, int r) {
        centerX = x; centerY = y; radius = r;
        visible = true;
        lastSpawn = 0; // draw immediately on next tick
    }

    public static void hide() { visible = false; }

    public static boolean isVisible() { return visible; }

    /** Called every frame from SearchForm.frameTick while the form is open */
    public static void tick(Client client) {
        if (!visible || client == null) return;
        PlayerMob player = client.getPlayer();
        Level level = client.getLevel();
        if (player == null || level == null || level.entityManager == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSpawn < 1000) return; // particle life 2s, refresh every 1s
        lastSpawn = now;
        int x0 = centerX - radius, x1 = centerX + radius;
        int y0 = centerY - radius, y1 = centerY + radius;
        // 16/32: full density (every tile). 64/128: dashed (every other
        // tile) to halve particles. Each edge toggles its own colors.
        int step = radius <= 32 ? 1 : 2;
        boolean g = Math.floorMod(x0, 2) == 0;
        for (int x = x0; x <= x1; x += step) {
            spawn(level, x, y0, false, g);
            spawn(level, x, y1, false, g);
            g = !g;
        }
        g = Math.floorMod(y0 + 1, 2) == 0;
        for (int y = y0 + 1; y <= y1 - 1; y += step) {
            spawn(level, x0, y, false, g);
            spawn(level, x1, y, false, g);
            g = !g;
        }
        // Corners get an extra bright marker
        spawn(level, x0, y0, true, true);
        spawn(level, x1, y0, true, false);
        spawn(level, x0, y1, true, false);
        spawn(level, x1, y1, true, true);
    }

    private static void spawn(Level level, int tileX, int tileY, boolean corner, boolean gold) {
        try {
            float px = tileX * 32 + 16, py = tileY * 32 + 16;
            if (corner) {
                level.entityManager.addTopParticle(px, py,
                    necesse.entity.particle.Particle.GType.CRITICAL)
                    .sprite(gold
                        ? necesse.gfx.GameResources.starParticles
                        : necesse.gfx.GameResources.sapphireShardParticles)
                    .ignoreLight(true)
                    .alpha(0.9f)
                    .sizeFadesInAndOut(16, 26, 0.5f)
                    .lifeTime(2000)
                    .height(30.0f);
            } else {
                level.entityManager.addTopParticle(px, py,
                    necesse.entity.particle.Particle.GType.CRITICAL)
                    .sprite(gold
                        ? necesse.gfx.GameResources.starParticles
                        : necesse.gfx.GameResources.sapphireShardParticles)
                    .ignoreLight(true)
                    .alpha(0.85f)
                    .sizeFadesInAndOut(10, 18, 0.5f)
                    .lifeTime(2000)
                    .height(4.0f);
            }
        } catch (Exception ignored) {}
    }
}
