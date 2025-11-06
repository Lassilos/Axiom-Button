package org.lassilos.color.client;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ColorButton extends ButtonWidget {
    public ColorButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }
}
