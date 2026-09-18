package wims2;

/**
 * Tracks our static marker particles so a new search can remove the
 * previous batch immediately instead of waiting out their lifetime.
 * Only static marks are tracked (Scan hits, icon clicks, fail burst);
 * the animated Ping rings and the range border are short-lived anyway.
 */
public class MarkerRegistry {

    private static final java.util.ArrayList<necesse.entity.particle.ParticleOption> live =
        new java.util.ArrayList<>();

    public static void track(necesse.entity.particle.ParticleOption o) {
        if (o == null) return;
        synchronized (live) {
            live.add(o);
            // Prune dead ones periodically so we never hold many older
            // ParticleOption objects (avoids pinning their level refs).
            if (live.size() > 64) {
                java.util.Iterator<necesse.entity.particle.ParticleOption> it = live.iterator();
                while (it.hasNext()) {
                    necesse.entity.particle.ParticleOption p = it.next();
                    boolean dead = false;
                    try { dead = p.isRemoved(); } catch (Exception ignored) { dead = true; }
                    if (dead) it.remove();
                }
            }
            while (live.size() > 400) live.remove(0);
        }
    }

    public static void clear() {
        synchronized (live) {
            for (necesse.entity.particle.ParticleOption o : live) {
                try { o.remove(); } catch (Exception ignored) {}
            }
            live.clear();
        }
    }
}
