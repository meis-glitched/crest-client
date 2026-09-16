package com.crest.client.core;

import com.crest.client.core.SkinChanger;
import com.crest.client.core.concurrent.CrestExecutors;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.core.network.HttpClient;
import com.crest.client.core.performance.FramePacer;
import com.crest.client.catstrokes.CatStrokesModule;
import com.crest.client.core.event.TickEvent;
import com.crest.client.core.hud.editor.HudEditorV2;
import com.crest.client.music.MusicModule;
import com.crest.client.music.MusicScreen;
import com.crest.client.core.CrestBrandManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class CrestClient implements ClientModInitializer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("CrestClient");
    private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath("crest-client", "hud_renderer");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Crest Client...");
        
        // Initialize core infrastructure
        CrestExecutors.initialize();
        HttpClient.initialize();
        
        // Initialize frame pacer for smooth rendering at high FPS
        FramePacer.setEnabled(true);
        // No FPS cap by default - let VSync or user settings handle it
        
        LOGGER.info("Core infrastructure initialized");
        
        CrestModules.init();
        CrestBrandManager.init();

        final boolean firstRunOpen = CrestModules.isFirstRun();
        final java.util.concurrent.atomic.AtomicBoolean welcomePending = new java.util.concurrent.atomic.AtomicBoolean(firstRunOpen);

        CrestModules.register(new HudAppearanceModule());
        CrestModules.register(new ZoomModule());
        CrestModules.register(new ScoreboardModule());
        CrestModules.register(new CoordsModule());
        CrestModules.register(new FpsModule());
        CrestModules.register(new PerfModule());
        CrestModules.register(new ArmorHudModule());
        CrestModules.register(new PotionHudModule());
        CrestModules.register(new CrosshairModule());
        CrestModules.register(new NoFogModule());
        CrestModules.register(new NoHurtCamModule());
        CrestModules.register(new DynamicFovModule());
        CrestModules.register(new LowFireModule());
        CrestModules.register(new EntityCullingModule());
        
        CrestModules.register(new CatStrokesModule());
        CrestModules.register(new TimeChangerModule());
        CrestModules.register(new WeatherChangerModule());
        CrestModules.register(new NoOverlayModule());
        CrestModules.register(new HideScoreboardModule());
        CrestModules.register(new ItemCounterModule());
        CrestModules.register(new ServerAddressModule());
        CrestModules.register(new ToggleNotificationsModule());
        CrestModules.register(new SpeedometerModule());
        CrestModules.register(new BlockOutlineModule());

        CrestModules.register(new FreelookModule());
        CrestModules.register(new WaypointsModule());
        CrestModules.register(new MouseTracesModule());
        CrestModules.register(new DynamicLightsModule());
        CrestModules.register(new AntiAfkModule());
        CrestModules.register(new CrestThemeModule());
        CrestModules.register(new RecorderModule());
        CrestModules.register(new RecorderIndicator());
        CrestModules.register(new StreamerModule());
        CrestModules.register(new StreamerIndicator());
        CrestModules.register(new ReplayBufferModule());
        CrestModules.register(new WhoamiModule());
        CrestModules.register(new ChatAnimationModule());
        CrestModules.register(new AppleSkinModule());

        CrestModules.register(new FastRightClickModule());
        CrestModules.register(new BlockLodModule());
        CrestModules.register(new AdaptiveRenderDistanceModule());
        CrestModules.register(new BiomeModule());
        CrestModules.register(new CpsModule());
        CrestModules.register(new TogglesModule());
        CrestModules.register(new StopwatchModule());
        CrestModules.register(new AutoTextHotkeyModule());
        CrestModules.register(new ReachDisplayModule());
        CrestModules.register(new PvpInfoModule());
        CrestModules.register(new TeamViewModule());
        CrestModules.register(new HitboxModule());
        CrestModules.register(new ParticleChangerModule());
        CrestModules.register(new ShulkerPreviewModule());
        CrestModules.register(new ItemPhysicsModule());
        CrestModules.register(new ScrollableTooltipsModule());
        CrestModules.register(new ColorSaturationModule());
        CrestModules.register(new GlintColorizerModule());
        CrestModules.register(new PlayerHealthIndicatorModule());
        CrestModules.register(new TntTimerModule());
        CrestModules.register(new SymbolChatModule());
        CrestModules.register(new SkinLayers3dModule());
        CrestModules.register(new MouseTweaksModule());
        CrestModules.register(new CornerTextModule());
        CrestModules.register(new ChatHeadsModule());
        CrestModules.register(new ShieldStatusModule());
        CrestModules.register(new BackgroundResourceLoaderModule());
        CrestModules.register(new ViewModelModule());

        CrestModules.register(new KeystrokesModule());
        CrestModules.register(new ComboModule());
        CrestModules.register(new ChatTimestampModule());
        CrestModules.register(new ToggleSneakModule());
        CrestModules.register(new FullbrightModule());

        CrestModules.register(new CrestNametagModule());

        ClientTickEvents.END_CLIENT_TICK.register(client -> SkinChanger.loadPersisted());

        MusicModule.init();

        KeybindManager.registerAction(GLFW.GLFW_KEY_F7, HudEditorV2::open);

        if (CrestModules.isFirstRun()) {
            CrestModules.applyDefaults();
            CrestModules.markInitialized();
        }

        KeybindManager.registerAction(GLFW.GLFW_KEY_M, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MusicScreen) {
                mc.screen.onClose();
            } else {
                mc.setScreen(new MusicScreen(MusicModule.getPlayer()));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CrestModules.getEventBus().post(new TickEvent(client));
            CpsTracker.tick();
            ComboTracker.tick();
            KeybindManager.processTick();
            com.crest.client.core.dynlight.DynamicLightEngine.get().tick(client);
            if (welcomePending.get()) {
                welcomePending.set(false);
                if (client.player != null && client.screen == null) {
                    client.setScreen(new CrestMenu());
                    client.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "[Crest] Welcome! Press the grave key (`) to open the menu. F7 to edit HUD."));
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Shutting down Crest Client...");
            CrestModules.getConfigManager().save();
            HudSettings.save();
            MusicModule.saveNow();
            
            // Shutdown thread pools gracefully
            CrestExecutors.shutdown();
            LOGGER.info("Crest Client shutdown complete");
        });

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            HUD_LAYER,
            (GuiGraphicsExtractor g, DeltaTracker d) -> renderHud(g, d)
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, cmdCtx) -> {
            dispatcher.register(ClientCommands.literal("streamurl")
                .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                    .executes(c -> {
                        String url = StringArgumentType.getString(c, "url");
                        if (!Streamer.isUrlAllowed(url)) {
                            c.getSource().sendError(net.minecraft.network.chat.Component.literal("Invalid RTMP URL"));
                            return 0;
                        }
                        var mod = CrestModules.get("streamer");
                        if (mod instanceof StreamerModule sm) {
                            sm.setStreamUrl(url);
                            c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Stream URL set"));
                        }
                        return 1;
                    })
                )
                .executes(c -> {
                    var mod = CrestModules.get("streamer");
                    if (mod instanceof StreamerModule sm) {
                        String url = sm.getStreamUrl();
                        c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Current URL: " + url));
                    }
                    return 1;
                })
            );
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (ShieldStatusModule.isActive()
                    && player instanceof Player attacker && entity instanceof Player victim) {
                boolean axe = attacker.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem
                        || attacker.getOffhandItem().getItem() instanceof net.minecraft.world.item.AxeItem;
                if (axe && victim.isBlocking()) {
                    ShieldDisableTracker.markDisabled(victim);
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        if (mc.screen instanceof CrestMenu) return;

        NotificationToast.render(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());

        for (RenderableModule renderable : CrestModules.getRenderableModules()) {
            try {
                if (renderable instanceof HudModule hud) {
                    hud.renderScaled(g, mc, d);
                } else {
                    renderable.render(g, mc, d);
                }
            } catch (Exception e) {
                String id = renderable instanceof CrestModule cm ? cm.getId() : renderable.getClass().getSimpleName();
                LOGGER.error("HUD render error in '{}', disabling module", e, id);
                CrestModules.setEnabled(id, false);
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "[Crest] Disabled '" + id + "' due to a render error. Check logs."));
                }
            }
        }
    }
}
