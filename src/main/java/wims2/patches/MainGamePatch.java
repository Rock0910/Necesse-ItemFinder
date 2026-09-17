package wims2.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;

// 跟舊版 WIMS 一樣掛 MainGame.frameTick，1.3 的 ModMethodPatch 依然可用
@ModMethodPatch(target = MainGame.class, name = "frameTick",
    arguments = {TickManager.class, GameWindow.class})
public class MainGamePatch {
    @Advice.OnMethodExit
    static void onExit(@Advice.This MainGame mainGame,
                       @Advice.Argument(0) TickManager tickManager,
                       @Advice.Argument(1) GameWindow window) {
        wims2.forms.SearchForm.frameTick(mainGame, tickManager, window);
    }
}
