package wims2;

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.GlobalData;
import necesse.engine.input.Control;

@ModEntry
public class ModMain {

    public static Control openSearchControl;
    public static Control favControl;

    public void init() {
    }

    public void postInit() {
        if (GlobalData.isServer()) {
            System.out.println("ItemFinder: client-only, skip server init.");
            return;
        }
        System.out.println("ItemFinder: registering control.");
        // Mod controls are rebindable in Settings > Controls (mod section).
        // Display name + tooltip so players can find and rebind it there.
        openSearchControl = Control.addModControl(new Control(89, "itemfinderopensearch",
            new necesse.engine.localization.message.StaticMessage("ItemFinder search")));
        try {
            openSearchControl.tooltip =
                new necesse.engine.localization.message.StaticMessage("Open finder");
        } catch (Exception ignored) {}
        // Favorite toggle for the hovered icon. U is free in vanilla defaults;
        // rebindable in Settings > Controls like the search key above.
        favControl = Control.addModControl(new Control(85, "itemfinderfavorite",
            new necesse.engine.localization.message.StaticMessage("ItemFinder favorite")));
        try {
            favControl.tooltip =
                new necesse.engine.localization.message.StaticMessage("Favorite icon");
        } catch (Exception ignored) {}
        // 1.3 ??ChatCommand 簽�?跟�??�差很�?（�?實�? getUsage/getAction/run/autocomplete...）�?
        // v1 ?��?註�??��??�令，只??UI，避?�編譯地?�。v2 要�??��? WimsCommand.java??    }

    public void initResources() {
        ParticleSpawner.initResources();
    }
}
