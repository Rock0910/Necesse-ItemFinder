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
            if (live.size() > 800) {
                // Forget (don't kill) the oldest; they fade on their own.
                // Prefer dropping already-dead ones first.
                java.util.Iterator<necesse.entity.particle.ParticleOption> it = live.iterator();
                while (it.hasNext() && live.size() > 600) {
                    necesse.entity.particle.ParticleOption old = it.next();
                    boolean dead = true;
                    try { dead = old.isRemoved(); } catch (Exception ignored) {}
                    if (dead) it.remove();
                    else break;
                }
                while (live.size() > 800) live.remove(0);
            }
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
