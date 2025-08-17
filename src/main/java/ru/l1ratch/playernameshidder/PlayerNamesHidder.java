package ru.l1ratch.playernameshidder;

import me.neznamy.tab.api.nametag.NameTagManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;

public class PlayerNamesHidder extends JavaPlugin implements Listener {

    private TabAPI tabAPI;
    private boolean useTab;
    private boolean citizensEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Проверяем наличие TAB
        if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
            tabAPI = TabAPI.getInstance();
            useTab = true;
            getLogger().info("TAB обнаружен! Используем его API для управления никами.");
        } else {
            getLogger().warning("TAB не найден. Плагин будет работать в урезанном режиме (Scoreboard).");
            useTab = false;
        }

        // Проверяем наличие Citizens
        citizensEnabled = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        if (citizensEnabled) {
            getLogger().info("Citizens обнаружен. NPC не будут скрываться.");
        }

        getServer().getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isNPC(player)) { // Игнорируем NPC
                hidePlayerName(player);
            }
        }

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Добавляем всех онлайн-игроков в скрытие
        for (Player player : Bukkit.getOnlinePlayers()) {
            hidePlayerName(player);
        }
    }

    // Проверка, является ли игрок NPC из Citizens
    private boolean isNPC(Player player) {
        if (!citizensEnabled) return false; // Citizens не установлен
        return player.hasMetadata("NPC"); // Citizens добавляет метаданные "NPC" для своих NPC
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isNPC(event.getPlayer())) { // Игнорируем NPC
            hidePlayerName(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player clickedPlayer = (Player) event.getRightClicked();
        if (isNPC(clickedPlayer)) return; // Игнорируем NPC

        Player interactingPlayer = event.getPlayer();

        // Проверка прав
        if (!interactingPlayer.hasPermission("pnh.show")) {
            interactingPlayer.sendMessage(ChatColor.RED + "У вас нет прав на это действие.");
            return;
        }

        // Проверка расстояния
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

        // Отображаем ник (через TAB или ActionBar)
        if (getConfig().getBoolean("use-action-bar")) {
            showPlayerNameInActionBar(clickedPlayer, interactingPlayer);
        } else {
            showPlayerName(clickedPlayer);
        }

        // Эффекты и звуки
        if (getConfig().getBoolean("effects.enabled")) {
            Particle particle = Particle.valueOf(getConfig().getString("effects.particle-type"));
            int amount = getConfig().getInt("effects.particle-amount");
            clickedPlayer.getWorld().spawnParticle(particle, clickedPlayer.getLocation().add(0, 2, 0), amount);
        }

        if (getConfig().getBoolean("effects.sound-enabled")) {
            Sound sound = Sound.valueOf(getConfig().getString("effects.sound-type"));
            float volume = (float) getConfig().getDouble("effects.sound-volume");
            float pitch = (float) getConfig().getDouble("effects.sound-pitch");
            clickedPlayer.getWorld().playSound(clickedPlayer.getLocation(), sound, volume, pitch);
        }

        // Скрываем ник через время
        long delay = getConfig().getInt("display-time") * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                hidePlayerName(clickedPlayer);
            }
        }.runTaskLater(this, delay);
    }

    // Скрытие ника через TAB или Scoreboard
    private void hidePlayerName(Player player) {
        if (isNPC(player)) return; // Не скрываем NPC

        if (useTab) {
            TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
            if (tabPlayer != null) {
                NameTagManager nameTagManager = tabAPI.getNameTagManager();
                if (nameTagManager != null) {
                    nameTagManager.hideNameTag(tabPlayer);
                }
            }
        } else {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    // Показ ника через TAB или Scoreboard
    private void showPlayerName(Player player) {
        if (isNPC(player)) return; // Не показываем NPC

        if (useTab) {
            TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
            if (tabPlayer != null) {
                NameTagManager nameTagManager = tabAPI.getNameTagManager();
                if (nameTagManager != null) {
                    nameTagManager.showNameTag(tabPlayer);
                }
            }
        } else {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // Показ ника в ActionBar
    private void showPlayerNameInActionBar(Player target, Player viewer) {
        String message = ChatColor.GREEN + "Игрок: " + target.getName();
        viewer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
}