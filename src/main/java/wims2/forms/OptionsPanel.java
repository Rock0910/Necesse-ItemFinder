package wims2.forms;

import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFairTypeLabel;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormSlider;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.util.function.Consumer;

/**
 * Gear panel holding every checkbox option plus the key binding shortcut,
 * so the main window stays compact.
 */
public class OptionsPanel extends Form {

    private final SearchForm main;

    public OptionsPanel(SearchForm main) {
        super("itemfinderoptions", 250, 60);
        this.main = main;
        try { drawBaseAlpha = 0.45f; } catch (Exception ignored) {}
        FormFlow flow = new FormFlow(5);

        int ty = flow.next();
        FormFairTypeLabel title = new FormFairTypeLabel(wims2.L.t("opttitle"), 5, ty);
        title.setFontOptions(new FontOptions(18));
        addComponent(title);
        try {
            necesse.gfx.ui.GameInterfaceStyle ui = necesse.engine.Settings.UI;
            FormContentIconButton x = new FormContentIconButton(
                getWidth() - 46, ty, 36, FormInputSize.SIZE_32, ButtonColor.BASE,
                ui.button_cross, wims2.L.m("close"));
            addComponent(x);
            x.onClicked(e -> main.hideOptions());
        } catch (Exception ignored) {}
        flow.nextY(title, 16); // clear the 36px close button

        addCheck(flow, "optrescan", main.isRescanOnSearch(), main::setRescanOnSearch);
        addCheck(flow, "optautofocus", main.isAutofocus(), main::setAutofocus);
        addCheck(flow, "optenter", main.isEnterDefocus(), main::setEnterDefocus);
        addCheck(flow, "optdebug", wims2.ModMain.debugLog, main::setDebugLog);

        // Opacity slider: 15% .. 100% (default = previous 45%)
        try {
            FormSlider slider = new FormSlider(wims2.L.t("optopacity"), 5, flow.next(),
                main.getOpacityPercent(), 15, 100, getWidth() - 10);
            addComponent(slider);
            slider.onChanged(e -> {
                try { main.setOpacityPercent(slider.getValue()); } catch (Exception ignored) {}
            });
            flow.nextY(slider, 5);
        } catch (Exception ignored) {}

        FormTextButton keys = new FormTextButton(
            wims2.L.t("openkeys"), 5, flow.next(), getWidth() - 10,
            FormInputSize.SIZE_32, ButtonColor.BASE);
        addComponent(keys);
        keys.onClicked(e -> main.openKeyBindings());
        flow.nextY(keys, 5);

        setHeight(flow.next() + 5);
    }

    private void addCheck(FormFlow flow, String key, boolean initial, Consumer<Boolean> setter) {
        try {
            FormCheckBox box = new FormCheckBox(wims2.L.t(key), 5, flow.next());
            addComponent(box);
            box.checked = initial;
            box.onClicked(e -> {
                try { setter.accept(box.checked); } catch (Exception ignored) {}
            });
            flow.nextY(box, 5);
        } catch (Exception ignored) {}
    }

    private int lastNx = Integer.MIN_VALUE, lastNy = Integer.MIN_VALUE;

    /** Same follow behaviour as the side panel; only moves when needed. */
    public void followMain(GameWindow window) {
        try {
            int mx = main.getX(), my = main.getY(), mw = main.getWidth();
            int winW = window.getWidth(), winH = window.getHeight();
            int nx = mx + mw + 10;
            if (nx + getWidth() > winW) nx = Math.max(0, mx - getWidth() - 10);
            // place below the side panel if it is open
            int ny = my;
            try {
                SidePanel sp = main.getPanel();
                if (sp != null) ny = Math.min(winH - getHeight(), sp.getY() + sp.getHeight() + 5);
            } catch (Exception ignored) {}
            ny = Math.max(0, Math.min(ny, winH - getHeight()));
            if (nx == lastNx && ny == lastNy && getX() == nx && getY() == ny) return;
            lastNx = nx;
            lastNy = ny;
            setPosition(new necesse.gfx.forms.position.FormFixedPosition(nx, ny));
        } catch (Exception ignored) {}
    }
}
