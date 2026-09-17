package wims2;

/**
 * ItemFinder (Necesse 1.3)
 *
 * 由 Where Is My Stuff?? (W.I.M.S.) 模組啟發
 * 使用 Opencode 開發的 Mod
 * 除非自己用不順或遇到 BUG，否則不會更新
 * 2026-09-18
 */

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.GlobalData;
import necesse.engine.input.Control;

@ModEntry
public class ModMain {

    public static Control openSearchControl;
    public static Control favControl;
    public static Control findControl;

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
            wims2.L.m("ctlsearch")));
        try {
            openSearchControl.tooltip = wims2.L.m("ctlsearchtip");
        } catch (Exception ignored) {}
        // Favorite toggle for the hovered icon. U is free in vanilla defaults;
        // rebindable in Settings > Controls like the search key above.
        favControl = Control.addModControl(new Control(85, "itemfinderfavorite",
            wims2.L.m("ctlfav")));
        try {
            favControl.tooltip = wims2.L.m("ctlfavtip");
        } catch (Exception ignored) {}
        // Hovered-item search: same sources as favorites, opens the
        // window and searches the hovered item. Default P, rebindable.
        findControl = Control.addModControl(new Control(80, "itemfinderfind",
            wims2.L.m("ctlfind")));
        try {
            findControl.tooltip = wims2.L.m("ctlfindtip");
        } catch (Exception ignored) {}
        // 不註冊聊天指令，避免 ChatCommand 簽名變動問題
        // v1 UI only, no chat command to avoid signature issues.
    }

    public void initResources() {
        ParticleSpawner.initResources();
    }
}

