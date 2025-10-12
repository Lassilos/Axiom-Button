package org.lassilos.color.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lassilos.color.client.ColorButton;
import org.lassilos.color.client.ColorClient;
import org.lassilos.color.client.ColorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

@Mixin(CreativeInventoryScreen.class)
public class CreativeInventoryScreenMixin {

    // store position so we can draw the icon in render
    @Unique
    private int colorAxiomBtnX = -1;
    @Unique
    private int colorAxiomBtnY = -1;
    @Unique
    private boolean colorAxiomBtnAdded = false;
    @Unique
    private ColorButton colorAxiomBtn = null;

    // SECOND button (gradient)
    @Unique
    private int colorGradientBtnX = -1;
    @Unique
    private int colorGradientBtnY = -1;
    @Unique
    private boolean colorGradientBtnAdded = false;
    @Unique
    private ColorButton colorGradientBtn = null;

    // Tunable placement constants
    @Unique
    private static final int BUTTON_MARGIN = 23; // distance from inventory left to the button (to the left)
    @Unique
    private static final int BUTTON_VERTICAL_OFFSET = -20; // vertical offset relative to center
    @Unique
    private static final int BUTTON_GAP = 15; // gap between buttons

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        try {
            // Respect configuration for showing buttons in singleplayer/multiplayer
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isSingleplayer = (mc != null && mc.getServer() != null);
            if (mc != null) {
                if (isSingleplayer && !ColorConfig.shouldShowInSingleplayer()) {
                    this.colorAxiomBtnAdded = false;
                    this.colorAxiomBtn = null;
                    this.colorGradientBtnAdded = false;
                    this.colorGradientBtn = null;
                    return;
                }
                if (!isSingleplayer && !ColorConfig.shouldShowInMultiplayer()) {
                    this.colorAxiomBtnAdded = false;
                    this.colorAxiomBtn = null;
                    this.colorGradientBtnAdded = false;
                    this.colorGradientBtn = null;
                    return;
                }
            }

            CreativeInventoryScreen self = (CreativeInventoryScreen) (Object) this;
            int btnSize = 20;

            // Compute inventory panel left/top by reflection: prefer backgroundWidth/backgroundHeight fields
            int invLeft = -1;
            int invTop = -1;
            int bgW = -1;
            int bgH = -1;
            try {
                Class<?> cls = self.getClass();
                // try direct names first
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
            } catch (Throwable ignored) {
            }

            // Use sensible defaults if reflection failed or produced no reliable values
            if (bgW <= 0) bgW = 176; // common vanilla gui width
            if (bgH <= 0) bgH = 166; // common vanilla gui height

            invLeft = (self.width - bgW) / 2;
            invTop = (self.height - bgH) / 2;

            // Place the first button flush to the left of the inventory panel with a small margin
            int x = invLeft - btnSize - BUTTON_MARGIN;
            int y;
            if (bgH > 0) {
                // center relative to the inventory panel
                y = invTop + (bgH - btnSize) / 2 + BUTTON_VERTICAL_OFFSET;
            } else {
                // center relative to the screen as a fallback
                y = (self.height - btnSize) / 2 + BUTTON_VERTICAL_OFFSET;
            }

            ColorButton btn = new ColorButton(x, y, btnSize, btnSize, Text.literal(""), (button) -> {
                ColorClient.openAxiomScreenStatic();
            });

            // add via invoker so we don't need protected access
            try {
                ((ScreenAccessor) self).callAddDrawableChild(btn);
                this.colorAxiomBtnX = x;
                this.colorAxiomBtnY = y;
                this.colorAxiomBtnAdded = true;
                this.colorAxiomBtn = btn;
                System.out.println("CreativeInventoryScreenMixin: added ColorButton at x=" + x + " y=" + y + " size=" + btnSize);
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // Create the gradient button below the yellow-dye button (~BUTTON_GAP px below)
            try {
                int gx = x;
                int gy = y + btnSize + BUTTON_GAP;
                ColorButton gbtn = new ColorButton(gx, gy, btnSize, btnSize, Text.literal(""), (button) -> {
                    ColorClient.openGradientScreenStatic();
                });
                try {
                    ((ScreenAccessor) self).callAddDrawableChild(gbtn);
                    this.colorGradientBtnX = gx;
                    this.colorGradientBtnY = gy;
                    this.colorGradientBtnAdded = true;
                    this.colorGradientBtn = gbtn;
                    System.out.println("CreativeInventoryScreenMixin: added Gradient Button at x=" + gx + " y=" + gy + " size=" + btnSize);
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
            if (!this.colorAxiomBtnAdded || this.colorAxiomBtn == null) return; // nothing to draw

            if (this.colorGradientBtn == null || !this.colorGradientBtnAdded) {
                // still allow the first path, but gradient won't be rendered
            }

            if (colorAxiomBtn != null) {
                // Recompute and update button positions each frame to avoid timing/heuristic issues
                try {
                    CreativeInventoryScreen self = (CreativeInventoryScreen) (Object) this;
                    int btnSize = colorAxiomBtn.getWidth();
                    int bgW = -1, bgH = -1, invLeft = -1, invTop = -1;

                    // 1) Prefer container left/top fields (common in ContainerScreen)
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

                    // 2) Fallback: use backgroundWidth/backgroundHeight when left/top not available
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

                    // compute gradient button target below
                    int targetX2 = targetX;
                    int targetY2 = targetY + btnSize + BUTTON_GAP;

                    // Write first button x/y reflectively (fields are private in ClickableWidget)
                     try {
                        Field bx = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("x");
                        Field by = colorAxiomBtn.getClass().getSuperclass().getDeclaredField("y");
                        bx.setAccessible(true); by.setAccessible(true);
                        bx.setInt(colorAxiomBtn, targetX);
                        by.setInt(colorAxiomBtn, targetY);
                        // read back widget fields; if that fails, fall back to target values
                        try {
                            this.colorAxiomBtnX = bx.getInt(colorAxiomBtn);
                            this.colorAxiomBtnY = by.getInt(colorAxiomBtn);
                        } catch (Throwable ignored) {
                            this.colorAxiomBtnX = targetX;
                            this.colorAxiomBtnY = targetY;
                        }
                     } catch (NoSuchFieldException nsf) {
                         // try lookup directly on ButtonWidget class if superclass field names differ
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

                    // Write second (gradient) button x/y reflectively if present
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

                    // Defensive: ensure stored coords are at least the computed target
                    if (this.colorAxiomBtnX == -1) this.colorAxiomBtnX = targetX;
                    if (this.colorAxiomBtnY == -1) this.colorAxiomBtnY = targetY;
                    if (this.colorGradientBtnX == -1) this.colorGradientBtnX = targetX2;
                    if (this.colorGradientBtnY == -1) this.colorGradientBtnY = targetY2;
                 } catch (Throwable ignored) {}

                 // Use vanilla button rendering
                 colorAxiomBtn.render(drawContext, mouseX, mouseY, delta);
                 if (this.colorGradientBtn != null && this.colorGradientBtnAdded) {
                     colorGradientBtn.render(drawContext, mouseX, mouseY, delta);
                 }

                // After rendering the widgets, read their actual x/y to ensure icons are precisely centered
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
                            // fall back to previously stored coordinates
                        }
                    }

                    // Update stored coords for later frames
                    this.colorAxiomBtnX = actualX;
                    this.colorAxiomBtnY = actualY;

                    // Draw yellow dye on top of the first button
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

                    // Now draw brush icon on the gradient button (fallback to stick if brush not present)
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
                                // fall back to stored
                            }
                        }

                        this.colorGradientBtnX = gActualX;
                        this.colorGradientBtnY = gActualY;

                        // Try to obtain a brush item from the registry (minecraft:brush), else fall back to stick
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
