package org.lassilos.color.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ColorConfigScreen extends Screen {
    private final Screen parent;
    private boolean showSingle;
    private boolean showMulti;
    private boolean debugO;
    private boolean notifyScreenOpen;

    public ColorConfigScreen(Screen parent) {
        super(Text.literal("Color Mod Settings"));
        this.parent = parent;
        this.showSingle = ColorConfig.shouldShowInSingleplayer();
        this.showMulti = ColorConfig.shouldShowInMultiplayer();
        this.debugO = ColorConfig.isDebugKeyOEnabled();
        this.notifyScreenOpen = ColorConfig.isScreenOpenNotificationEnabled();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        ColorButton singleBtn = new ColorButton(centerX - 100, centerY - 50, 200, 20,
                Text.literal("Show in Singleplayer: " + showSingle), (button) -> {
            showSingle = !showSingle;
            ColorConfig.setShowInSingleplayer(showSingle);
            button.setMessage(Text.literal("Show in Singleplayer: " + showSingle));
        });

        ColorButton multiBtn = new ColorButton(centerX - 100, centerY - 20, 200, 20,
                Text.literal("Show in Multiplayer: " + showMulti), (button) -> {
            showMulti = !showMulti;
            ColorConfig.setShowInMultiplayer(showMulti);
            button.setMessage(Text.literal("Show in Multiplayer: " + showMulti));
        });

        ColorButton debugBtn = new ColorButton(centerX - 100, centerY + 10, 200, 20,
                Text.literal("Enable debug 'O' key: " + debugO), (button) -> {
            debugO = !debugO;
            ColorConfig.setDebugKeyOEnabled(debugO);
            button.setMessage(Text.literal("Enable debug 'O' key: " + debugO));
        });

        ColorButton notifyBtn = new ColorButton(centerX - 100, centerY + 40, 200, 20,
                Text.literal("Notify when screen opened: " + notifyScreenOpen), (button) -> {
            notifyScreenOpen = !notifyScreenOpen;
            ColorConfig.setScreenOpenNotificationEnabled(notifyScreenOpen);
            button.setMessage(Text.literal("Notify when screen opened: " + notifyScreenOpen));
        });

        ColorButton done = new ColorButton(centerX - 100, centerY + 80, 200, 20,
                Text.literal("Done"), (button) -> {
            // return to parent screen
            if (this.client != null) this.client.setScreen(parent);
        });

        this.addDrawableChild(singleBtn);
        this.addDrawableChild(multiBtn);
        this.addDrawableChild(debugBtn);
        this.addDrawableChild(notifyBtn);
        this.addDrawableChild(done);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float tickDelta) {
        // Let the superclass render background, children and tooltips first
        super.render(drawContext, mouseX, mouseY, tickDelta);

        // Draw the screen title on top so we don't trigger background blur twice
        Text title = this.title;
        int textWidth = this.textRenderer.getWidth(title);
        int x = (this.width - textWidth) / 2;
        drawContext.drawTextWithShadow(this.textRenderer, title, x, this.height / 2 - 90, 0xFFFFFF);
    }
}
