// filepath: /home/lassilos/Axiom-Buttons/Axiom-Buttons-main/src/client/java/org/lassilos/color/mixin/client/SurvivalInventoryMixin.java
package org.lassilos.color.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lassilos.color.client.ColorButton;
import org.lassilos.color.client.ColorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

@Mixin(InventoryScreen.class)
public class SurvivalInventoryMixin {

    @Unique
    private int colorAxiomBtnX = -1;
    @Unique
    private int colorAxiomBtnY = -1;
    @Unique
    private boolean colorAxiomBtnAdded = false;
    @Unique
    private ColorButton colorAxiomBtn = null;

    @Unique
    private int colorGradientBtnX = -1;
    @Unique
    private int colorGradientBtnY = -1;
    @Unique
    private boolean colorGradientBtnAdded = false;
    @Unique
    private ColorButton colorGradientBtn = null;

    @Unique
    private static final int BUTTON_MARGIN = 23;
    @Unique
    private static final int BUTTON_VERTICAL_OFFSET = -20;
    @Unique
    private static final int BUTTON_GAP = 15;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getServer() != null) {
                // singleplayer: skip adding the button(s)
                this.colorAxiomBtnAdded = false;
                this.colorAxiomBtn = null;
                this.colorGradientBtnAdded = false;
                this.colorGradientBtn = null;
                return;
            }

            InventoryScreen self = (InventoryScreen) (Object) this;
            int btnSize = 20;

            int invLeft = -1;
            int invTop = -1;
            int bgW = -1;
            int bgH = -1;
            try {
                Class<?> cls = self.getClass();
                try {
                    Field fW = cls.getDeclaredField("backgroundWidth");
                    fW.setAccessible(true);
                    bgW = fW.getInt(self);
                } catch (NoSuchFieldException ignored) {}
                try {
                    Field fH = cls.getDeclaredField("backgroundHeight");
                    fH.setAccessible(true);
                    bgH = fH.getInt(self);
                } catch (NoSuchFieldException ignored) {}
            } catch (Throwable ignored) {}

            if (bgW <= 0) bgW = 176;
            if (bgH <= 0) bgH = 166;

            invLeft = (self.width - bgW) / 2;
            invTop = (self.height - bgH) / 2;

            int x = invLeft - btnSize - BUTTON_MARGIN;
            int y;
            if (bgH > 0) {
                y = invTop + (bgH - btnSize) / 2 + BUTTON_VERTICAL_OFFSET;
            } else {
                y = (self.height - btnSize) / 2 + BUTTON_VERTICAL_OFFSET;
            }

            ColorButton btn = new ColorButton(x, y, btnSize, btnSize, Text.literal(""), (button) -> ColorClient.openAxiomScreenStatic());

            try {
                ((ScreenAccessor) self).callAddDrawableChild(btn);
                this.colorAxiomBtnX = x;
                this.colorAxiomBtnY = y;
                this.colorAxiomBtnAdded = true;
                this.colorAxiomBtn = btn;
                System.out.println("SurvivalInventoryMixin: added ColorButton at x=" + x + " y=" + y + " size=" + btnSize);
            } catch (Throwable t) {
                t.printStackTrace();
            }

            try {
                int gx = x;
                int gy = y + btnSize + BUTTON_GAP;
                ColorButton gbtn = new ColorButton(gx, gy, btnSize, btnSize, Text.literal(""), (button) -> ColorClient.openGradientScreenStatic());
                try {
                    ((ScreenAccessor) self).callAddDrawableChild(gbtn);
                    this.colorGradientBtnX = gx;
                    this.colorGradientBtnY = gy;
                    this.colorGradientBtnAdded = true;
                    this.colorGradientBtn = gbtn;
                    System.out.println("SurvivalInventoryMixin: added Gradient Button at x=" + gx + " y=" + gy + " size=" + btnSize);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (!this.colorAxiomBtnAdded || this.colorAxiomBtn == null) return;

            if (this.colorGradientBtn == null || !this.colorGradientBtnAdded) {
                // still allow the first path, but gradient won't be rendered
            }

            if (colorAxiomBtn != null) {
                try {
                    InventoryScreen self = (InventoryScreen) (Object) this;
                    int btnSize = colorAxiomBtn.getWidth();
                    int bgW = -1, bgH = -1, invLeft = -1, invTop = -1;

                    try {
                        Field leftField = null;
                        Field topField = null;
                        Class<?> cls = self.getClass();
                        while (cls != null && cls != Object.class && (leftField == null || topField == null)) {
                            try { leftField = cls.getDeclaredField("left"); } catch (Throwable ignored) {}
                            try { topField = cls.getDeclaredField("top"); } catch (Throwable ignored) {}
                            try { if (leftField == null) leftField = cls.getDeclaredField("x"); } catch (Throwable ignored) {}
                            try { if (topField == null) topField = cls.getDeclaredField("y"); } catch (Throwable ignored) {}
                            cls = cls.getSuperclass();
                        }
                        if (leftField != null && topField != null) {
                            leftField.setAccessible(true);
                            topField.setAccessible(true);
                            invLeft = leftField.getInt(self);
                            invTop = topField.getInt(self);
                        }
                    } catch (Throwable ignored) {}

                    if (invLeft < 0 || invTop < 0) {
                        try {
                            Class<?> cls = self.getClass();
                            try {
                                Field fW = cls.getDeclaredField("backgroundWidth");
                                fW.setAccessible(true);
                                bgW = fW.getInt(self);
                            } catch (Throwable ignored) {}
                            try {
                                Field fH = cls.getDeclaredField("backgroundHeight");
                                fH.setAccessible(true);
                                bgH = fH.getInt(self);
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}

                        if (bgW <= 0) bgW = 176;
                        if (bgH <= 0) bgH = 166;
                        invLeft = (self.width - bgW) / 2;
                        invTop = (self.height - bgH) / 2;
                    }

                    int targetX = invLeft - btnSize - BUTTON_MARGIN;
                    int targetY = invTop + (bgH > 0 ? (bgH - btnSize) / 2 : (self.height - btnSize) / 2) + BUTTON_VERTICAL_OFFSET;

                    int targetX2 = targetX;
                    int targetY2 = targetY + btnSize + BUTTON_GAP;

                    try {
                        Field bx = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("x");
                        Field by = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("y");
                        bx.setAccessible(true); by.setAccessible(true);
                        bx.setInt(colorAxiomBtn, targetX);
                        by.setInt(colorAxiomBtn, targetY);
                        try {
                            this.colorAxiomBtnX = bx.getInt(colorAxiomBtn);
                            this.colorAxiomBtnY = by.getInt(colorAxiomBtn);
                        } catch (Throwable ignored) {
                            this.colorAxiomBtnX = targetX;
                            this.colorAxiomBtnY = targetY;
                        }
                     } catch (NoSuchFieldException nsf) {
                         try {
                           Field bx = colorAxiomBtn.getClass().getDeclaredField("x");
                           Field by = colorAxiomBtn.getClass().getDeclaredField("y");
                           bx.setAccessible(true); by.setAccessible(true);
                           bx.setInt(colorAxiomBtn, targetX);
                           by.setInt(colorAxiomBtn, targetY);
                           try {
                               this.colorAxiomBtnX = bx.getInt(colorAxiomBtn);
                               this.colorAxiomBtnY = by.getInt(colorAxiomBtn);
                           } catch (Throwable ignored) {
                               this.colorAxiomBtnX = targetX;
                               this.colorAxiomBtnY = targetY;
                           }
                         } catch (Throwable ignored) {}
                     } catch (Throwable ignored) {}

                    if (this.colorGradientBtn != null && this.colorGradientBtnAdded) {
                        try {
                            Field bx2 = colorGradientBtn.getClass().getSuperclass().getDeclaredField("x");
                            Field by2 = colorGradientBtn.getClass().getSuperclass().getDeclaredField("y");
                            bx2.setAccessible(true); by2.setAccessible(true);
                            bx2.setInt(colorGradientBtn, targetX2);
                            by2.setInt(colorGradientBtn, targetY2);
                            try {
                                this.colorGradientBtnX = bx2.getInt(colorGradientBtn);
                                this.colorGradientBtnY = by2.getInt(colorGradientBtn);
                            } catch (Throwable ignored) {
                                this.colorGradientBtnX = targetX2;
                                this.colorGradientBtnY = targetY2;
                            }
                        } catch (NoSuchFieldException nsf2) {
                            try {
                                Field bx2 = colorGradientBtn.getClass().getDeclaredField("x");
                                Field by2 = colorGradientBtn.getClass().getDeclaredField("y");
                                bx2.setAccessible(true); by2.setAccessible(true);
                                bx2.setInt(colorGradientBtn, targetX2);
                                by2.setInt(colorGradientBtn, targetY2);
                                try {
                                    this.colorGradientBtnX = bx2.getInt(colorGradientBtn);
                                    this.colorGradientBtnY = by2.getInt(colorGradientBtn);
                                } catch (Throwable ignored) {
                                    this.colorGradientBtnX = targetX2;
                                    this.colorGradientBtnY = targetY2;
                                }
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}
                    }

                    if (this.colorAxiomBtnX == -1) this.colorAxiomBtnX = targetX;
                    if (this.colorAxiomBtnY == -1) this.colorAxiomBtnY = targetY;
                    if (this.colorGradientBtnX == -1) this.colorGradientBtnX = targetX2;
                    if (this.colorGradientBtnY == -1) this.colorGradientBtnY = targetY2;
                 } catch (Throwable ignored) {}

                 colorAxiomBtn.render(drawContext, mouseX, mouseY, delta);
                 if (this.colorGradientBtn != null && this.colorGradientBtnAdded) {
                     colorGradientBtn.render(drawContext, mouseX, mouseY, delta);
                 }

                try {
                    int actualX = this.colorAxiomBtnX;
                    int actualY = this.colorAxiomBtnY;
                    try {
                        Field bx = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("x");
                        Field by = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("y");
                        bx.setAccessible(true); by.setAccessible(true);
                        actualX = bx.getInt(colorAxiomBtn);
                        actualY = by.getInt(colorAxiomBtn);
                    } catch (Throwable ignored1) {
                        try {
                            Field bx = colorAxiomBtn.getClass().getDeclaredField("x");
                            Field by = colorAxiomBtn.getClass().getDeclaredField("y");
                            bx.setAccessible(true); by.setAccessible(true);
                            actualX = bx.getInt(colorAxiomBtn);
                            actualY = by.getInt(colorAxiomBtn);
                        } catch (Throwable ignored2) {
                        }
                    }

                    this.colorAxiomBtnX = actualX;
                    this.colorAxiomBtnY = actualY;

                    ItemStack dye = new ItemStack(Items.YELLOW_DYE);
                    int itemX = actualX + (colorAxiomBtn.getWidth() - 16) / 2;
                    int itemY = actualY + (colorAxiomBtn.getHeight() - 16) / 2;
                    try {
                        Method m = DrawContext.class.getMethod("drawItem", ItemStack.class, int.class, int.class);
                        m.invoke(drawContext, dye, itemX, itemY);
                    } catch (NoSuchMethodException e1) {
                        try {
                            Method m2 = DrawContext.class.getMethod("drawItemStack", ItemStack.class, int.class, int.class);
                            m2.invoke(drawContext, dye, itemX, itemY);
                        } catch (NoSuchMethodException e2) {
                            try { drawContext.drawItem(dye, itemX, itemY); } catch (Throwable ignored) {}
                        }
                    }

                    if (this.colorGradientBtn != null && this.colorGradientBtnAdded) {
                        int gActualX = this.colorGradientBtnX;
                        int gActualY = this.colorGradientBtnY;
                        try {
                            Field bx2 = colorGradientBtn.getClass().getSuperclass().getDeclaredField("x");
                            Field by2 = colorGradientBtn.getClass().getSuperclass().getDeclaredField("y");
                            bx2.setAccessible(true); by2.setAccessible(true);
                            gActualX = bx2.getInt(colorGradientBtn);
                            gActualY = by2.getInt(colorGradientBtn);
                        } catch (Throwable ignored1) {
                            try {
                                Field bx2 = colorGradientBtn.getClass().getDeclaredField("x");
                                Field by2 = colorGradientBtn.getClass().getDeclaredField("y");
                                bx2.setAccessible(true); by2.setAccessible(true);
                                gActualX = bx2.getInt(colorGradientBtn);
                                gActualY = by2.getInt(colorGradientBtn);
                            } catch (Throwable ignored2) {
                            }
                        }

                        this.colorGradientBtnX = gActualX;
                        this.colorGradientBtnY = gActualY;

                        Item brushItem = Items.STICK;
                        try {
                            Item reg = Registries.ITEM.get(Identifier.of("minecraft", "brush"));
                            if (reg != null) brushItem = reg;
                        } catch (Throwable ignored) {}

                        ItemStack brushStack = new ItemStack(brushItem);
                        int brushX = gActualX + (colorGradientBtn.getWidth() - 16) / 2;
                        int brushY = gActualY + (colorGradientBtn.getHeight() - 16) / 2;

                        try {
                            Method m = DrawContext.class.getMethod("drawItem", ItemStack.class, int.class, int.class);
                            m.invoke(drawContext, brushStack, brushX, brushY);
                        } catch (NoSuchMethodException e1) {
                            try {
                                Method m2 = DrawContext.class.getMethod("drawItemStack", ItemStack.class, int.class, int.class);
                                m2.invoke(drawContext, brushStack, brushX, brushY);
                            } catch (NoSuchMethodException e2) {
                                try { drawContext.drawItem(brushStack, brushX, brushY); } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}
             }
         } catch (Throwable ignored) {}
     }
}

