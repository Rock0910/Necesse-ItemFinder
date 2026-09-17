package wims2;

/** Mod localization shortcut: category "itemfinder" (locale/*.lang). */
public class L {
    public static necesse.engine.localization.message.GameMessage m(String key) {
        return new necesse.engine.localization.message.LocalMessage("itemfinder", key);
    }

    public static necesse.engine.localization.message.GameMessage msg(String key, String... reps) {
        return new necesse.engine.localization.message.LocalMessage("itemfinder", key, reps);
    }

    public static String t(String key) {
        try {
            return m(key).translate();
        } catch (Exception e) {
            return key;
        }
    }
}
