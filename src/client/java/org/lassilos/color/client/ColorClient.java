package org.lassilos.color.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;

public class ColorClient implements ClientModInitializer {

    // Try multiple plausible Axiom class names (UK/US spellings and picker variants)
    private static final String[] AXIOM_SCREEN_CANDIDATES = new String[] {
            "com.moulberry.axiom.screen.CreativeColourScreen",
            "com.moulberry.axiom.screen.CreativeColorScreen",
            "com.moulberry.axiom.screen.BlockColourPickerScreen",
            "com.moulberry.axiom.screen.BlockColorPickerScreen",
            "com.moulberry.axiom.screen.CreativeColourPickerScreen",
            "com.moulberry.axiom.screen.CreativeColorPickerScreen"
    };

    private String lastOverlayMessage = null;
    private long lastOverlayTime = 0;

    @Override
    public void onInitializeClient() {
        // Ensure configuration is loaded (creates config file if missing)
        try { ColorConfig.load(); } catch (Throwable ignored) {}

        // NOTE: All hotkeys / keybindings have been removed intentionally.
        // Previous behavior registered GLFW callbacks and polled key state to open screens.
        // That logic was removed so the mod no longer responds to keyboard shortcuts.

        // HUD overlay to show last messages when a screen is open
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (lastOverlayMessage != null && mc != null && mc.currentScreen != null) {
                long now = System.currentTimeMillis();
                if (now - lastOverlayTime < 3000) {
                    drawContext.drawTextWithShadow(
                        mc.textRenderer,
                        "[color] " + lastOverlayMessage,
                        10, 10, 0xFFFFFF
                    );
                } else {
                    lastOverlayMessage = null;
                }
            }
        });

    }

    private void openAxiomScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Run on client thread
        mc.execute(() -> {
            boolean opened = false;
            for (String candidate : AXIOM_SCREEN_CANDIDATES) {
                try {
                    Class<?> cls = Class.forName(candidate);

                    // If this candidate looks like the CreativeColour/CreativeColor screen, ensure vanilla creative inventory screen is open
                    boolean isCreativeColourCandidate = candidate.endsWith("CreativeColourScreen") || candidate.endsWith("CreativeColorScreen");
                    if (isCreativeColourCandidate) {
                        try {
                            // Detect mapped or obfuscated creative inventory class, but do NOT try to instantiate it here
                            Class<?> creativeCls = null;
                            try { creativeCls = Class.forName("net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen"); } catch (ClassNotFoundException ignored) {}
                            if (creativeCls == null) {
                                try { creativeCls = Class.forName("net.minecraft.class_481"); } catch (ClassNotFoundException ignored) {}
                            }
                            if (creativeCls != null) {
                                if (mc.currentScreen == null || !creativeCls.isAssignableFrom(mc.currentScreen.getClass())) {
                                    sendFeedback(mc, "CreativeColourScreen requires the vanilla Creative Inventory open — open it (press 'E' in creative) and press the key again.");
                                    continue;
                                }
                            }
                            // If we can't find the vanilla creative class, fall back to the generic instantiate attempt below
                        } catch (Throwable ignored) {
                        }
                    }

                    Object screenObj = null;
                    try {
                        screenObj = tryInstantiate(cls, mc);
                    } catch (Throwable t) {
                        sendFeedback(mc, "Exception while constructing " + cls.getName() + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
                        continue;
                    }

                    if (screenObj instanceof Screen) {
                        // Extra safety: for CreativeColour/CreativeColorScreen, ensure the internal creativeScreen field is initialized
                        if (isCreativeColourCandidate) {
                            try {
                                java.lang.reflect.Field f = cls.getDeclaredField("creativeScreen");
                                f.setAccessible(true);
                                Object creativeScreenField = f.get(screenObj);
                                if (creativeScreenField == null) {
                                    sendFeedback(mc, "Constructed CreativeColourScreen, but its internal creativeScreen is null. Open the vanilla Creative Inventory (press 'E' in creative mode) and try again.");
                                    continue;
                                }
                            } catch (NoSuchFieldException nsf) {
                                // field not found, proceed anyway
                            }
                        }

                        mc.setScreen((Screen) screenObj);
                        if (ColorConfig.isScreenOpenNotificationEnabled()) sendFeedback(mc, "Opened Axiom screen: " + candidate);
                        opened = true;
                        break;
                    } else {
                        if (screenObj == null) {
                            sendFeedback(mc, "Reflection failed to construct a " + candidate + " (result was null).");
                        } else {
                            sendFeedback(mc, "Reflection created object but it's not a Screen: " + candidate + " -> " + screenObj);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // ignore and try next
                } catch (Throwable t) {
                    sendFeedback(mc, "Failed to open candidate " + candidate + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
            }
            if (!opened) {
                sendFeedback(mc, "Could not find or open any Axiom CreativeColourScreen candidate. Is the Axiom mod loaded?");
            }
        });
    }

    private void sendFeedback(MinecraftClient mc, String msg) {
        System.out.println(msg);
        try {
            if (mc.player != null && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[color] " + msg));
            }
        } catch (Throwable ignored) {
        }
        // Store for overlay
        lastOverlayMessage = msg;
        lastOverlayTime = System.currentTimeMillis();
    }

    private Object tryInstantiate(Class<?> cls, MinecraftClient mc) {
        String className = cls.getName();
        try {
            // Null checks for player
            if (mc.player == null) {
                sendFeedback(mc, "Player is null, cannot open Axiom screen.");
                return null;
            }
            // Quick attempt: prefer the known CreativeColour/CreativeColorScreen constructor
            if (className.endsWith("CreativeColourScreen") || className.endsWith("CreativeColorScreen")) {
                try {
                    // Try to find a 2-arg constructor and only use it if the current screen matches the second parameter
                    for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                        Class<?>[] params = ctor.getParameterTypes();
                        if (params.length == 2) {
                            Class<?> p0 = params[0];
                            Class<?> p1 = params[1];
                            // check first param can accept mc.player
                            if (mc.player != null && p0.isAssignableFrom(mc.player.getClass())) {
                                // If the current screen matches the expected second parameter type use it
                                if (mc.currentScreen != null && p1.isAssignableFrom(mc.currentScreen.getClass())) {
                                    ctor.setAccessible(true);
                                    return ctor.newInstance(mc.player, mc.currentScreen);
                                }
                                // Otherwise, see if passing null is acceptable (some constructors accept null), try that
                                try {
                                    ctor.setAccessible(true);
                                    return ctor.newInstance(mc.player, null);
                                } catch (Throwable ignored) {
                                     // If that fails, we'll attempt to auto-create a vanilla CreativeInventoryScreen earlier in the caller
                                }
                                // If we couldn't construct with current screen or null, inform user and abort
                                sendFeedback(mc, "CreativeColourScreen requires the vanilla Creative Inventory open — open it (press 'E' in creative) and press the key again.");
                                return null;
                             }
                         }
                    }
                } catch (Exception ignored) {
                }
            }

            // Generic: try to find a constructor where we can supply runtime instances
            Object[] candidates = new Object[] { mc.player, mc.player.getInventory(), mc.currentScreen, mc };

            for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                try {
                    ctor.setAccessible(true);
                    Class<?>[] params = ctor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    for (int i = 0; i < params.length; i++) {
                        Class<?> p = params[i];
                        // primitives
                        if (p.isPrimitive()) {
                            args[i] = defaultFor(p, mc);
                            continue;
                        }
                        boolean found = false;
                        for (Object cand : candidates) {
                            if (cand != null && p.isAssignableFrom(cand.getClass())) {
                                args[i] = cand;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            // allow null for non-primitive
                            args[i] = null;
                        }
                    }
                    // try instantiate
                    return ctor.newInstance(args);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            // fallback below
        }

        // If we reach here, no constructor worked — enumerate available constructors for debugging
        StringBuilder sb = new StringBuilder();
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            sb.append("(");
            Class<?>[] params = c.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                sb.append(params[i].getName());
                if (i < params.length - 1) sb.append(", ");
            }
            sb.append(") ");
        }
        sendFeedback(mc, "No suitable constructor found for " + cls.getName() + ". Available: " + sb);
         // Return null to indicate we couldn't instantiate the class instead of throwing; caller will handle feedback.
         return null;
     }

    // Helper to provide default values for primitive parameter types when instantiating via reflection
    private Object defaultFor(Class<?> primitive, MinecraftClient mc) {
        if (primitive == boolean.class) return false;
        if (primitive == byte.class) return (byte)0;
        if (primitive == short.class) return (short)0;
        if (primitive == int.class) return 0;
        if (primitive == long.class) return 0L;
        if (primitive == float.class) return 0f;
        if (primitive == double.class) return 0d;
        if (primitive == char.class) return '\0';
        // Fallback - should not happen for primitives
        return 0;
    }

    // Show (chat + overlay) a description of the current screen to the user
    private void showCurrentScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        try {
            Screen s = mc.currentScreen;
            if (s == null) {
                sendFeedback(mc, "Current screen: <none>");
                return;
            }
            String name = s.getClass().getName();
            // If the screen has a title, include that (best-effort reflection)
            String extra = "";
            try {
                // Many Screens expose a title field or method; try common approaches
                try {
                    java.lang.reflect.Field titleField = s.getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    Object t = titleField.get(s);
                    if (t != null) extra = " - title=" + t.toString();
                } catch (NoSuchFieldException nsf) {
                    // ignore
                }
            } catch (Throwable ignored) {}
            sendFeedback(mc, "Current screen: " + name + extra);
        } catch (Throwable t) {
            sendFeedback(mc, "Exception while retrieving current screen: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    // Helper to get singleton instance
    private static ColorClient instance;
    public ColorClient() { instance = this; }

    // Public wrapper for external callers (mixins / widgets) to open the Axiom screen
    // This simply defers to the instance method if available and runs safely on the client thread.
    public static void openAxiomScreenStatic() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    try {
                        ColorClient inst = instance;
                        if (inst != null) {
                            inst.openAxiomScreen();
                        } else {
                            // instance not yet initialized; log so user/mixins can see what's happening
                            System.out.println("[color] ColorClient instance not yet initialized when trying to open Axiom screen.");
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    // New: open the CreativeGradientScreen (Axiom) similarly
    public static void openGradientScreenStatic() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    try {
                        ColorClient inst = instance;
                        if (inst != null) {
                            inst.openGradientScreen();
                        } else {
                            System.out.println("[color] ColorClient instance not yet initialized when trying to open Gradient screen.");
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    // Instance method to attempt to construct and open the CreativeGradientScreen reflectively
    private void openGradientScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            boolean opened = false;
            String candidate = "com.moulberry.axiom.screen.CreativeGradientScreen";
            try {
                Class<?> cls = Class.forName(candidate);
                Object screenObj = null;
                try {
                    screenObj = tryInstantiate(cls, mc);
                } catch (Throwable t) {
                    sendFeedback(mc, "Exception while constructing " + candidate + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
                if (screenObj instanceof net.minecraft.client.gui.screen.Screen) {
                    mc.setScreen((net.minecraft.client.gui.screen.Screen) screenObj);
                    if (ColorConfig.isScreenOpenNotificationEnabled()) sendFeedback(mc, "Opened Axiom screen: " + candidate);
                    opened = true;
                } else {
                    if (screenObj == null) sendFeedback(mc, "Reflection failed to construct a " + candidate + " (result was null).");
                    else sendFeedback(mc, "Reflection created object but it's not a Screen: " + candidate + " -> " + screenObj);
                }
            } catch (ClassNotFoundException e) {
                sendFeedback(mc, "Axiom CreativeGradientScreen class not found. Is the Axiom mod installed?");
            } catch (Throwable t) {
                sendFeedback(mc, "Failed to open gradient screen: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
            if (!opened) {
                // no further action
            }
        });
    }

    // Single getInstance definition
    private static ColorClient getInstance() { return instance; }

}
