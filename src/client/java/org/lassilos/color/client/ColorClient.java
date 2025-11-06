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
    private static KeyBinding KEY_DEBUG_O;
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
    private boolean showDebugKeyWasDown = false;
    private GLFWKeyCallback previousKeyCallback = null;
    private GLFWKeyCallback ourKeyCallback = null;
    private long lastWindowHandle = 0;

    @Override
    public void onInitializeClient() {
        // Ensure configuration is loaded (creates config file if missing)
        try { ColorConfig.load(); } catch (Throwable ignored) {}
        // Create a KeyBinding in a way that tolerates signature changes across Minecraft versions.
        KEY_SHOW_SCREEN = createKeyBindingTolerant("key.color.show_current_screen", GLFW.GLFW_KEY_H, "key.categories.color");
        // Debug 'O' key that prints the current screen; can be disabled via config
        KEY_DEBUG_O = createKeyBindingTolerant("key.color.debug_print_screen", GLFW.GLFW_KEY_O, "key.categories.color");

    }

    // Helper: construct a KeyBinding in a tolerant way (try normal constructor, fall back to reflective discovery)
    private static KeyBinding createKeyBindingTolerant(String id, int keyCode, String category) {
        // First, try the normal explicit constructor (may throw NoSuchMethodError on some runtime mappings)
        try {
            return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, keyCode, category));
        } catch (NoSuchMethodError | Exception e) {
            // Fallback: try to reflectively find a compatible constructor and instantiate it
        }

        try {
            Class<?> kbClass = KeyBinding.class;
            for (Constructor<?> ctor : kbClass.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != 4) continue;

                Object[] args = new Object[4];
                // first param -> id
                args[0] = id;
                // find int param index
                int intIdx = -1;
                for (int i = 0; i < 4; i++) if (params[i].isPrimitive() && params[i] == int.class) { intIdx = i; break; }
                if (intIdx == -1) continue;
                args[intIdx] = keyCode;

                // fill remaining params
                for (int i = 0; i < 4; i++) {
                    if (i == 0 || i == intIdx) continue;
                    if (params[i].isAssignableFrom(String.class)) {
                        args[i] = category;
                        continue;
                    }
                    // If it's an enum (likely InputUtil.Type), try to find a KEYSYM constant or fallback to first
                    if (params[i].isEnum()) {
                        Object[] consts = params[i].getEnumConstants();
                        Object pick = null;
                        for (Object c : consts) {
                            if (c.toString().equals("KEYSYM") || c.toString().equals("KEYSYM")) { pick = c; break; }
                        }
                        if (pick == null && consts.length > 0) pick = consts[0];
                        args[i] = pick;
                        continue;
                    }
                    // Last resort: try null (some constructors accept null)
                    args[i] = null;
                }

                try {
                    ctor.setAccessible(true);
                    Object inst = ctor.newInstance(args);
                    if (inst instanceof KeyBinding) {
                        return KeyBindingHelper.registerKeyBinding((KeyBinding) inst);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {}

        // If all else fails, return a dummy registered keybinding by constructing a simple one via the minimal available API.
        try {
            // As a last fallback, register a keybinding via KeyBindingHelper with reflection creating a proxy-like object.
            // Attempt to create using the constructor (String, int) if it exists.
            Class<?> kbClass = KeyBinding.class;
            for (Constructor<?> ctor : kbClass.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && params[0] == String.class && params[1] == int.class) {
                    Object inst = ctor.newInstance(id, keyCode);
                    if (inst instanceof KeyBinding) return KeyBindingHelper.registerKeyBinding((KeyBinding) inst);
                }
            }
        } catch (Throwable ignored) {}

        // Unable to construct a KeyBinding safely; return null (GLFW callback still provides functionality).
        return null;
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

    // New: GLFW key callback for ColorClient (install early in onInitializeClient)
    private void installKeyCallback() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        lastWindowHandle = client.getWindow().getHandle();
        previousKeyCallback = GLFW.glfwSetKeyCallback(lastWindowHandle, new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                // Ignore if not for this window
                if (window != lastWindowHandle) return;

                // Debug: print key events
                // System.out.println("Key: " + key + " Scancode: " + scancode + " Action: " + action + " Mods: " + mods);

                // Handle key actions
                if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                    // Show current screen (overlay/chat)
                    if (key == GLFW.GLFW_KEY_H) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc != null) mc.execute(() -> {
                            try {
                                ColorClient inst = getInstance();
                                if (inst != null) inst.showCurrentScreen();
                            } catch (Throwable ignored) {}
                        });
                    }
                    // Debug 'O' key: print current screen if enabled in config
                    if (key == GLFW.GLFW_KEY_O) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc != null) mc.execute(() -> {
                            try {
                                if (ColorConfig.isDebugKeyOEnabled()) {
                                    ColorClient inst = getInstance();
                                    if (inst != null) inst.showCurrentScreen();
                                }
                            } catch (Throwable ignored) {}
                        });
                    }
                }
            }
        });
    }

    // New: poll key states each client tick (safer for keybinding availability)
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
        if (client.world == null) return; // skip if not in a world (e.g., title screen)

        boolean keyDown;
        try {
            if (client.currentScreen != null && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                keyDown = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_H);
            } else {
                keyDown = isShowKeyPressed();
            }
        } catch (Throwable t) {
            keyDown = isShowKeyPressed();
        }
        // Debug 'O' key poll (works when window unavailable via registered keybinding too)
        boolean debugKeyDown;
        try {
            if (client.currentScreen != null && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                debugKeyDown = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_O);
            } else {
                debugKeyDown = isDebugKeyPressed();
            }
        } catch (Throwable t) {
            debugKeyDown = isDebugKeyPressed();
        }
        if (keyDown && !showScreenKeyWasDown) {
            // show current screen to user (overlay/chat)
             showCurrentScreen();
          }
          showScreenKeyWasDown = keyDown;
        if (debugKeyDown && !showDebugKeyWasDown) {
            if (ColorConfig.isDebugKeyOEnabled()) {
                showCurrentScreen();
            }
        }
        showDebugKeyWasDown = debugKeyDown;
      });
