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
            System.out.println("WIMS2: client-only, skip server init.");
            return;
        }
        System.out.println("WIMS2: registering control.");
        // Mod controls are rebindable in Settings > Controls (mod section).
        // Display name + tooltip so players can find and rebind it there.
        openSearchControl = Control.addModControl(new Control(89, "wims2opensearch",
            new necesse.engine.localization.message.StaticMessage("WIMS2 search")));
        try {
            openSearchControl.tooltip =
                new necesse.engine.localization.message.StaticMessage("Open finder");
        } catch (Exception ignored) {}
        // Favorite toggle for the hovered icon. U is free in vanilla defaults;
        // rebindable in Settings > Controls like the search key above.
        favControl = Control.addModControl(new Control(85, "wims2favorite",
            new necesse.engine.localization.message.StaticMessage("WIMS2 favorite")));
        try {
            favControl.tooltip =
                new necesse.engine.localization.message.StaticMessage("Favorite icon");
        } catch (Exception ignored) {}
        // 1.3 的 ChatCommand 簽名跟舊版差很多（要實作 getUsage/getAction/run/autocomplete...），
        // v1 先不註冊文字指令，只留 UI，避免編譯地雷。v2 要加再補 WimsCommand.java。
    }

    public void initResources() {
        ParticleSpawner.initResources();
    }
}
