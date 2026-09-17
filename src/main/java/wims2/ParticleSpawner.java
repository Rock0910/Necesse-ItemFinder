package wims2;

import necesse.engine.GlobalData;
import necesse.engine.state.MainGame;
import necesse.engine.state.State;
import necesse.entity.DrawOnMapEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.manager.EntityManager;
import necesse.entity.particle.Particle;
import necesse.engine.util.GameRandom;

import java.awt.Point;

/**
 * World markers. success() draws a tall star pillar at the container so it
 * is visible from afar (much easier to spot than the old 2-second sparkle).
 * Called once per search action, not every tick, so no spam.
 */
public class ParticleSpawner {

    public static EntityManager getEntityManager() {
        try {
            State s = GlobalData.getCurrentState();
            if (s instanceof MainGame) {
                MainGame mg = (MainGame) s;
                if (mg.getClient() != null && mg.getClient().getLevel() != null) {
                    return mg.getClient().getLevel().entityManager;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** One flat ring + center marker on the container tile, ~3s, no drift.
     * NOTE: batch clearing happens once per search in SearchEngine.filter,
     * not here (this runs once per container). */
    public static void success(DrawOnMapEntity target) {
        EntityManager em = getEntityManager();
        if (em == null || target == null) return;
        try {
            Point p = target.getMapPos();
            markStatic(em, p.x, p.y);
        } catch (Exception ignored) {}
    }

    /** Same static marker by tile coords (used for icon clicks) */
    public static void markStatic(necesse.level.maps.Level level, int tileX, int tileY) {
        if (level == null || level.entityManager == null) return;
        markStatic(level.entityManager, tileX * 32 + 16, tileY * 32 + 16);
    }

    private static void markStatic(EntityManager em, float cx, float cy) {
        flatRing(em, cx, cy, 1.1f);
        GameRandom r = GameRandom.globalRandom;
        for (int i = 0; i < 6; i++) {
            try {
                boolean gold = (i % 2 == 0);
                MarkerRegistry.track(em.addTopParticle(cx + r.getIntBetween(-6, 6), cy + r.getIntBetween(-4, 4),
                    Particle.GType.COSMETIC)
                    .sprite(gold
                        ? necesse.gfx.GameResources.starParticles
                        : necesse.gfx.GameResources.sapphireShardParticles)
                    .ignoreLight(true)
                    .alpha(0.9f)
                    .sizeFadesInAndOut(12, 18, 0.5f)
                    .lifeTime(3000)
                    .height(10.0f)); // fixed: sits on the container, no drift
            } catch (Exception ignored) {}
        }
    }

    private static void flatRing(EntityManager em, float cx, float cy, float rTiles) {
        int points = 22;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            float px = cx + (float) (Math.cos(a) * rTiles * 32);
            float py = cy + (float) (Math.sin(a) * rTiles * 32);
            try {
                boolean gold = (i % 2 == 0);
                MarkerRegistry.track(em.addTopParticle(px, py, Particle.GType.COSMETIC)
                    .sprite(gold
                        ? necesse.gfx.GameResources.starParticles
                        : necesse.gfx.GameResources.sapphireShardParticles)
                    .ignoreLight(true)
                    .alpha(0.85f)
                    .sizeFadesInAndOut(10, 18, 0.5f)
                    .lifeTime(3000)
                    .height(3.0f)); // fixed height: stays on the tile
            } catch (Exception ignored) {}
        }
    }

    /** Red burst at the player when nothing found (stays low, no pillar) */
    public static void fail(PlayerMob player) {
        EntityManager em = getEntityManager();
        if (em == null || player == null) return;
        try {
            Point p = player.getMapPos();
            GameRandom r = GameRandom.globalRandom;
            for (int i = 0; i < 10; i++) {
                MarkerRegistry.track(em.addTopParticle(p.x + r.getIntBetween(-15, 15), p.y + 10, Particle.GType.COSMETIC)
                    .sprite(necesse.gfx.GameResources.starParticles)
                    .ignoreLight(true)
                    .color(1.0f, 0.2f, 0.2f, 1.0f)
                    .sizeFadesInAndOut(10, 20, 0.5f)
                    .lifeTime(r.getIntBetween(800, 1500))
                    .height(10.0f)); // fixed: no upward drift
            }
        } catch (Exception ignored) {}
    }

    public static void initResources() {
        // No custom textures: star sprite is tinted red for fail markers.
    }
}
