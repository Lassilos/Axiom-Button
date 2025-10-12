package org.lassilos.color.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;

import java.lang.reflect.Constructor;

public class ColorClient implements ClientModInitializer {

    private static KeyBinding KEY_SHOW_SCREEN;
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
    private boolean showScreenKeyWasDown = false;
    private GLFWKeyCallback previousKeyCallback = null;
    private GLFWKeyCallback ourKeyCallback = null;
    private long lastWindowHandle = 0;

    @Override
    public void onInitializeClient() {
        KEY_SHOW_SCREEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.color.show_current_screen",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.categories.color"
        ));

        // Poll key each client tick and show the current screen when pressed
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Install GLFW key callback once the window is available to reliably detect keys in GUIs
            try {
                if (client.getWindow() != null) {
                    long handle = client.getWindow().getHandle();
                    if (handle != lastWindowHandle) {
                        // create and store our callback to avoid GC
                        ourKeyCallback = new GLFWKeyCallback() {
                            @Override
                            public void invoke(long window, int key, int scancode, int action, int mods) {
                                // chain to previous callback if present
                                try {
                                    if (previousKeyCallback != null) previousKeyCallback.invoke(window, key, scancode, action, mods);
                                } catch (Throwable ignored) {}
                                // handle both PRESS and REPEAT so quick taps and held keys work
                                if ((key == GLFW.GLFW_KEY_H) && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc != null) mc.execute(() -> {
                                        try {
                                            ColorClient inst = getInstance();
                                            if (inst != null) inst.showCurrentScreen();
                                        } catch (Throwable ignored) {}
                                    });
                                }
                            }
                        };
                        // install and keep previous
                        previousKeyCallback = GLFW.glfwSetKeyCallback(handle, ourKeyCallback);
                        lastWindowHandle = handle;
                    }
                }
            }
            catch (Throwable ignored) {
            }
            // Handle KEY_SHOW_SCREEN in all contexts (including GUIs)
            boolean keyDown;
            try {
                if (client.currentScreen != null && client.getWindow() != null) {
                    long handle = client.getWindow().getHandle();
                    keyDown = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_H);
                } else {
                    keyDown = KEY_SHOW_SCREEN.isPressed();
                }
            } catch (Throwable t) {
                keyDown = KEY_SHOW_SCREEN.isPressed();
            }
            if (keyDown && !showScreenKeyWasDown) {
                // show current screen to user (overlay/chat)
                 showCurrentScreen();
             }
             showScreenKeyWasDown = keyDown;
         });

         HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
             MinecraftClient mc = MinecraftClient.getInstance();
             // Debug HUD: show key detection status when any GUI is open
             try {
                // no per-frame debug overlay
             } catch (Throwable ignored) {
             }

             if (lastOverlayMessage != null && mc.currentScreen != null) {
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

            // Removed magenta debug cube rendering
            // try { ColorButton.debugDraw(drawContext); } catch (Throwable ignored) {}
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
                            } else {
                                // If we can't find the vanilla creative class, fall back to the generic instantiate attempt below
                            }
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
                        sendFeedback(mc, "Opened Axiom screen: " + candidate);
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
                                    return ctor.newInstance(mc.player, (Object) null);
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
                    boolean ok = true;
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
        sendFeedback(mc, "No suitable constructor found for " + cls.getName() + ". Available: " + sb.toString());
        // Return null to indicate we couldn't instantiate the class instead of throwing; caller will handle feedback.
        return null;
    }

    private Object defaultFor(Class<?> t, MinecraftClient mc) {
        if (!t.isPrimitive()) {
            // common helpful substitutions
            if (t.isAssignableFrom(Screen.class)) return mc.currentScreen;
            if (t.isAssignableFrom(MinecraftClient.class)) return mc;
            return null;
        }
        // primitives
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == boolean.class) return false;
        if (t == char.class) return '\0';
        return 0; // fallback
    }

    private void showCurrentScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen screen = mc.currentScreen;
        String msg;
        if (screen == null) {
            msg = "No screen is currently open.";
        } else {
            msg = "Current screen: " + screen.getClass().getName();
        }
        sendFeedback(mc, msg);
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
                    sendFeedback(mc, "Opened Axiom screen: " + candidate);
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
