package dev.onelimit.velocityannouces.announce;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.onelimit.velocityannouces.VelocityAnnoucesPlugin;
import dev.onelimit.velocityannouces.model.AnnounceMode;
import dev.onelimit.velocityannouces.model.AnnouncementEntry;
import dev.onelimit.velocityannouces.model.PluginConfig;
import dev.onelimit.velocityannouces.text.ModernTextFormatter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class AnnouncementService {
    private final VelocityAnnoucesPlugin plugin;
    private final ProxyServer server;
    private final MiniMessage miniMessage;
    private final ModernTextFormatter modernFormatter;
    private final Random random;

    private ScheduledTask autoTask;
    private PluginConfig config;
    private int roundRobinIndex;

    public AnnouncementService(VelocityAnnoucesPlugin plugin, ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
        this.miniMessage = MiniMessage.miniMessage();
        this.modernFormatter = new ModernTextFormatter();
        this.random = new Random();
        this.config = PluginConfig.defaults();
        this.roundRobinIndex = 0;
    }

    public void applyConfig(PluginConfig config) {
        this.config = config;
        restartAutoTask();
    }

    public void shutdown() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    public void broadcast(AnnouncementEntry entry) {
        if (entry == null) {
            return;
        }

        switch (entry.mode()) {
            case CHAT -> broadcastChat(entry.message());
            case ACTIONBAR -> broadcastActionbar(entry.message());
            case TITLE -> broadcastTitle(entry.title(), entry.subtitle(), entry.fadeInMs(), entry.stayMs(), entry.fadeOutMs());
            case BOSSBAR -> broadcastBossbar(entry.message(), entry.progress(), entry.color(), entry.overlay(), entry.durationSeconds());
        }
    }

    public void broadcastChat(String rawMessage) {
        Component message = render(rawMessage);
        for (Player player : server.getAllPlayers()) {
            player.sendMessage(message);
        }
    }

    public void broadcastActionbar(String rawMessage) {
        Component message = render(rawMessage);
        for (Player player : server.getAllPlayers()) {
            player.sendActionBar(message);
        }
    }

    public void broadcastTitle(String rawTitle, String rawSubtitle, int fadeInMs, int stayMs, int fadeOutMs) {
        Component title = render(rawTitle);
        Component subtitle = render(rawSubtitle);

        Title.Times times = Title.Times.times(
            Duration.ofMillis(Math.max(0, fadeInMs)),
            Duration.ofMillis(Math.max(200, stayMs)),
            Duration.ofMillis(Math.max(0, fadeOutMs))
        );

        Title built = Title.title(title, subtitle, times);
        for (Player player : server.getAllPlayers()) {
            player.showTitle(built);
        }
    }

    public void broadcastBossbar(String rawMessage, float progress, String rawColor, String rawOverlay, int durationSeconds) {
        BossBar bossBar = BossBar.bossBar(
            render(rawMessage),
            clamp(progress, 0f, 1f),
            parseColor(rawColor),
            parseOverlay(rawOverlay)
        );

        List<Player> players = List.copyOf(server.getAllPlayers());
        for (Player player : players) {
            player.showBossBar(bossBar);
        }

        long safeDuration = Math.max(1, durationSeconds);
        server.getScheduler()
            .buildTask(plugin, () -> {
                for (Player player : players) {
                    player.hideBossBar(bossBar);
                }
            })
            .delay(safeDuration, TimeUnit.SECONDS)
            .schedule();
    }

    private void restartAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }

        if (!config.autoEnabled() || config.announcements().isEmpty()) {
            return;
        }

        long interval = Math.max(5L, config.intervalSeconds());
        autoTask = server.getScheduler()
            .buildTask(plugin, this::broadcastNextAuto)
            .delay(interval, TimeUnit.SECONDS)
            .repeat(interval, TimeUnit.SECONDS)
            .schedule();
    }

    private void broadcastNextAuto() {
        if (config.announcements().isEmpty()) {
            return;
        }

        AnnouncementEntry picked;
        if (config.randomPick()) {
            int index = random.nextInt(config.announcements().size());
            picked = config.announcements().get(index);
        } else {
            int index = roundRobinIndex % config.announcements().size();
            picked = config.announcements().get(index);
            roundRobinIndex++;
        }

        broadcast(picked);
    }

    private Component render(String rawInput) {
        String processed = modernFormatter.applyModernTag(rawInput == null ? "" : rawInput);
        return miniMessage.deserialize(processed);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private BossBar.Color parseColor(String value) {
        if (value == null) {
            return BossBar.Color.BLUE;
        }

        try {
            return BossBar.Color.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.BLUE;
        }
    }

    private BossBar.Overlay parseOverlay(String value) {
        if (value == null) {
            return BossBar.Overlay.PROGRESS;
        }

        try {
            return BossBar.Overlay.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
