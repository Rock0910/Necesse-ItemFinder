package wims2.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.InputEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.position.FormPosition;
import necesse.gfx.forms.position.FormPositionContainer;

import java.util.Collections;
import java.util.List;

/** Blank 36x32 slot so every result row lines up its columns. */
public class IconSpacer extends FormComponent implements FormPositionContainer {

    private FormPosition position;

    public IconSpacer(int x, int y) {
        this.position = new FormFixedPosition(x, y);
    }

    @Override
    public void handleInputEvent(InputEvent e, TickManager tm, PlayerMob player) {
    }

    @Override
    public void handleControllerEvent(
        necesse.engine.input.controller.ControllerEvent e, TickManager tm, PlayerMob player) {
    }

    @Override
    public void addNextControllerFocus(List<necesse.gfx.forms.controller.ControllerFocus> list,
                                       int x, int y,
                                       necesse.gfx.forms.controller.ControllerNavigationHandler handler,
                                       java.awt.Rectangle rect, boolean b) {
    }

    @Override
    public void draw(TickManager tm, PlayerMob player, java.awt.Rectangle rect) {
    }

    @Override
    public List<java.awt.Rectangle> getHitboxes() {
        return Collections.singletonList(
            new java.awt.Rectangle(position.getX(), position.getY(), 36, 32));
    }

    @Override
    public FormPosition getPosition() {
        return position;
    }

    @Override
    public void setPosition(FormPosition position) {
        this.position = position;
    }
}
