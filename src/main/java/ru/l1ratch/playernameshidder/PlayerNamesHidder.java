package ru.l1ratch.playernameshidder;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;

import java.util.HashMap;
import java.util.UUID;

public class PlayerNamesHidder extends JavaPlugin implements Listener {

    private TabAPI tabAPI;
    private NameTagManager nameTagManager;
    private boolean useTab;
    private boolean citizensEnabled;
    private HashMap<UUID, Boolean> showAllPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Проверяем наличие TAB
        useTab = Bukkit.getPluginManager().getPlugin("TAB") != null;
        citizensEnabled = Bukkit.getPluginManager().getPlugin("Citizens") != null;

        // Отложенная инициализация TAB API
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (useTab) {
                try {
                    tabAPI = TabAPI.getInstance();
                    nameTagManager = tabAPI.getNameTagManager();

                    if (nameTagManager != null) {
                        getLogger().info("TAB NameTagManager успешно загружен!");
                    } else {
                        getLogger().warning("TAB NameTagManager недоступен. Возможно, функция отключена в конфиге TAB.");
                        useTab = false;
                    }
                } catch (IllegalStateException e) {
                    useTab = false;
                    getLogger().warning("TAB API недоступен. Переключаемся на fallback-режим.");
                }
            }

            getServer().getPluginManager().registerEvents(this, this);

            // Скрываем ники для всех онлайн-игроков
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isNPC(player)) {
                    hidePlayerName(player);
                }
            }
        }, 20L);

        setupCommand();
    }

    private void setupCommand() {
        getCommand("pnh").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда только для игроков.");
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("pnh.showall")) {
                player.sendMessage(ChatColor.RED + "У вас нет прав на эту команду.");
                return true;
            }

            UUID playerId = player.getUniqueId();
            boolean currentState = showAllPlayers.getOrDefault(playerId, false);
            boolean newState = !currentState;
            showAllPlayers.put(playerId, newState);

            if (newState) {
                // Включаем показ ников для всех игроков только для этого зрителя
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!isNPC(target)) {
                        showPlayerNameForViewer(target, player);
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Режим показа всех ников включен.");
            } else {
                // Выключаем показ ников для всех игроков только для этого зрителя
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!isNPC(target)) {
                        hidePlayerNameForViewer(target, player);
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Режим показа всех ников выключен.");
            }
            return true;
        });
    }

    private boolean isNPC(Player player) {
        return citizensEnabled && player.hasMetadata("NPC");
    }

    // Скрыть ник игрока для всех
    private void hidePlayerName(Player player) {
        if (isNPC(player)) return;

        if (useTab && nameTagManager != null) {
            try {
                TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    nameTagManager.hideNameTag(tabPlayer);
                }
            } catch (Exception ignored) {}
        } else {
            // Fallback на scoreboard
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    // Показать ник игрока для всех
    private void showPlayerName(Player player) {
        if (isNPC(player)) return;

        if (useTab && nameTagManager != null) {
            try {
                TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    nameTagManager.showNameTag(tabPlayer);
                }
            } catch (Exception ignored) {}
        } else {
            // Fallback на scoreboard
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // Показать ник игрока только для определенного зрителя (используем API TAB)
    private void showPlayerNameForViewer(Player target, Player viewer) {
        if (isNPC(target)) return;

        if (useTab && nameTagManager != null) {
            try {
                TabPlayer tabTarget = tabAPI.getPlayer(target.getUniqueId());
                TabPlayer tabViewer = tabAPI.getPlayer(viewer.getUniqueId());
                if (tabTarget != null && tabViewer != null) {
                    nameTagManager.showNameTag(tabTarget, tabViewer);
                }
            } catch (Exception ignored) {}
        } else {
            // Fallback: для viewer показываем стандартную scoreboard
            viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // Скрыть ник игрока только для определенного зрителя (используем API TAB)
    private void hidePlayerNameForViewer(Player target, Player viewer) {
        if (isNPC(target)) return;

        if (useTab && nameTagManager != null) {
            try {
                TabPlayer tabTarget = tabAPI.getPlayer(target.getUniqueId());
                TabPlayer tabViewer = tabAPI.getPlayer(viewer.getUniqueId());
                if (tabTarget != null && tabViewer != null) {
                    nameTagManager.hideNameTag(tabTarget, tabViewer);
                }
            } catch (Exception ignored) {}
        } else {
            // Fallback: для viewer показываем скрывающую scoreboard
            viewer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();
        if (!isNPC(joinedPlayer)) {
            hidePlayerName(joinedPlayer);

            // Если есть игроки с включенным showall, показываем им ник нового игрока
            for (UUID viewerId : showAllPlayers.keySet()) {
                if (showAllPlayers.get(viewerId)) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        showPlayerNameForViewer(joinedPlayer, viewer);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        showAllPlayers.remove(playerId);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player clickedPlayer = (Player) event.getRightClicked();
        if (isNPC(clickedPlayer)) return;

        Player interactingPlayer = event.getPlayer();
        UUID interactingId = interactingPlayer.getUniqueId();

        // Если у взаимодействующего игрока включен showall, ники уже видны
        boolean showallEnabled = showAllPlayers.getOrDefault(interactingId, false) &&
                interactingPlayer.hasPermission("pnh.showall");

        if (!showallEnabled && !interactingPlayer.hasPermission("pnh.show")) {
            interactingPlayer.sendMessage(ChatColor.RED + "У вас нет прав на это действие.");
            return;
        }

        double maxDistance = getConfig().getDouble("max-distance");
        double distance = interactingPlayer.getLocation().distance(clickedPlayer.getLocation());

        if (distance > maxDistance) {
            if (!interactingPlayer.hasMetadata("distanceWarningSent")) {
                interactingPlayer.sendMessage(ChatColor.RED + "Вы слишком далеко, чтобы увидеть ник.");
                interactingPlayer.setMetadata("distanceWarningSent", new FixedMetadataValue(this, true));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        interactingPlayer.removeMetadata("distanceWarningSent", PlayerNamesHidder.this);
                    }
                }.runTaskLater(this, 20L);
            }
            return;
        }

        interactingPlayer.removeMetadata("distanceWarningSent", this);

        // Всегда показываем эффекты и actionbar (если настроено)
        displayPlayerName(clickedPlayer, interactingPlayer, getConfig().getInt("display-time") * 20L, showallEnabled);
    }

    private void displayPlayerName(Player target, Player viewer, long displayTicks, boolean showallEnabled) {
        String displayType = getConfig().getString("display-type", "ACTION_BAR").toUpperCase();

        // Всегда показываем actionbar, если настроено (даже при включенном showall)
        if (displayType.equals("ACTION_BAR") || displayType.equals("BOTH")) {
            String format = getConfig().getString("actionbar-format", "Игрок: %player_name%");
            String message = format.replace("%player_name%", target.getName());

            // Поддержка PlaceholderAPI
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                message = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, message);
            }

            viewer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
        }

        // Если showall выключен, показываем ник над головой на время
        if (!showallEnabled && (displayType.equals("PLAYER_TAG") || displayType.equals("BOTH"))) {
            showPlayerName(target);

            // Скрываем ник после времени
            if (displayTicks > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        hidePlayerName(target);
                    }
                }.runTaskLater(this, displayTicks);
            }
        }

        // Всегда показываем эффекты
        if (getConfig().getBoolean("effects.enabled")) {
            try {
                Particle particle = Particle.valueOf(getConfig().getString("effects.particle-type"));
                int amount = getConfig().getInt("effects.particle-amount");
                target.getWorld().spawnParticle(particle, target.getLocation().add(0, 2, 0), amount);
            } catch (Exception e) {
                getLogger().warning("Неверный тип частицы в конфиге: " + getConfig().getString("effects.particle-type"));
            }
        }

        if (getConfig().getBoolean("effects.sound-enabled")) {
            try {
                Sound sound = Sound.valueOf(getConfig().getString("effects.sound-type"));
                float volume = (float) getConfig().getDouble("effects.sound-volume");
                float pitch = (float) getConfig().getDouble("effects.sound-pitch");
                target.getWorld().playSound(target.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                getLogger().warning("Неверный тип звука в конфиге: " + getConfig().getString("effects.sound-type"));
            }
        }
    }
}