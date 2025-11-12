package com.darkblade12.itemslotmachine.plugin;

import com.darkblade12.itemslotmachine.plugin.command.CommandHandler;
import com.darkblade12.itemslotmachine.plugin.command.CommandRegistrationException;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class PluginBase extends JavaPlugin {
    private static final Comparator<Manager<?>> MANAGER_COMPARATOR = Comparator.comparingInt(Manager::getLoadIndex);
    protected final int projectId;
    protected final int pluginId;
    protected final Logger logger;
    protected final File config;
    protected final ClassToInstanceMap<Manager<?>> managers;
    protected final ClassToInstanceMap<CommandHandler<?>> commandHandlers;
    protected final Map<String, OfflinePlayer> playerCache;
    private int managerLoadIndex;

    protected PluginBase(int projectId, int pluginId, Locale... locales) {
        this.projectId = projectId;
        this.pluginId = pluginId;
        logger = getLogger();
        config = new File(getDataFolder(), "config.yml");
        playerCache = new ConcurrentHashMap<>();
        managers = MutableClassToInstanceMap.create();
        managers.putInstance(MessageManager.class, new MessageManager(this, locales));
        commandHandlers = MutableClassToInstanceMap.create();
    }

    private static int[] splitVersion(String version) {
        String[] split = version.split("\\.");
        int[] numbers = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                numbers[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return numbers;
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        boolean success;
        try {
            success = load();
        } catch (Exception e) {
            logException(e, "An error occurred while loading components!");
            success = false;
        }

        if (!success) {
            disable();
            return;
        }

        try {
            for (CommandHandler<?> handler : commandHandlers.values()) {
                handler.enable();
            }
        } catch (CommandRegistrationException e) {
            logException(e, "Failed to enable all command handlers!");
            disable();
            return;
        }

        try {
            managers.values().stream().sorted(MANAGER_COMPARATOR).forEach(Manager::enable);
        } catch (Exception e) {
            logException(e, "Failed to enable all managers!");
            disable();
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        logInfo("Plugin version %s enabled! (%d ms)", getVersion(), duration);
    }

    @Override
    public void onDisable() {
        try {
            managers.values().forEach(Manager::disable);
            unload();
        } catch (Exception e) {
            logException(e, "An error occurred while unloading components!");
        }

        logInfo("Plugin version %s disabled.", getVersion());
    }

    public boolean onReload() {
        logInfo("Reloading plugin version %s...", getVersion());

        boolean success;
        try {
            success = reload();
        } catch (Exception e) {
            logException(e, "Failed to reload plugin!");
            success = false;
        }

        if (!success) {
            disable();
            return false;
        }

        try {
            managers.values().stream().sorted(MANAGER_COMPARATOR).forEach(Manager::reload);
        } catch (Exception e) {
            logException(e, "Failed to reload all managers!");
            disable();
            return false;
        }

        logInfo("Plugin version %s reloaded!", getVersion());
        return true;
    }

    public abstract boolean load();

    public abstract void unload();

    public abstract boolean reload();

    public void disable() {
        logInfo("Plugin will disable...");
        getServer().getPluginManager().disablePlugin(this);
    }

    public void logInfo(String message) {
        logger.info(message);
    }

    public void logInfo(String message, Object... args) {
        logger.info(String.format(message, args));
    }

    public void logWarning(String message) {
        logger.warning(message);
    }

    public void logWarning(String message, Object... args) {
        logger.warning(String.format(message, args));
    }

    public void logException(Exception exception, String message) {
        logger.log(Level.SEVERE, message, exception);
    }

    public void logException(Exception exception, String message, Object... args) {
        logger.log(Level.SEVERE, String.format(message, args), exception);
    }

    public String formatMessage(Message message, Object... args) {
        return getManager(MessageManager.class).formatMessage(message, args);
    }

    public void sendMessage(CommandSender sender, Message message, Object... args) {
        String text = getPrefix() + " " + ChatColor.RESET + getManager(MessageManager.class).formatMessage(message, args);
        if (sender instanceof ConsoleCommandSender) {
            text = ChatColor.stripColor(text);
        }
        sender.sendMessage(text);
    }

    @SuppressWarnings("unchecked")
    protected void registerManager(Manager<?> manager) {
        manager.setLoadIndex(managerLoadIndex++);
        managers.putInstance((Class<Manager<?>>) manager.getClass(), manager);
    }

    @SuppressWarnings("unchecked")
    protected void registerCommandHandler(CommandHandler<?> commandHandler) {
        commandHandlers.putInstance((Class<CommandHandler<?>>) commandHandler.getClass(), commandHandler);
    }

    protected String getPrefix() {
        MessageManager manager = getManager(MessageManager.class);
        if (!manager.hasMessage(Message.MESSAGE_PREFIX)) {
            return "[" + getName() + "]";
        }

        return manager.formatMessage(Message.MESSAGE_PREFIX);
    }

    public abstract Locale getCurrentLocale();

    public String getVersion() {
        return getDescription().getVersion();
    }

    public <T extends Manager<?>> T getManager(Class<T> managerClass) {
        return managers.getInstance(managerClass);
    }

    public <T extends CommandHandler<?>> T getCommandHandler(Class<T> commandHandlerClass) {
        return commandHandlers.getInstance(commandHandlerClass);
    }

    @SuppressWarnings("deprecation")
    public OfflinePlayer getPlayer(String name) {
        String key = name.toLowerCase();
        if (playerCache.containsKey(key)) {
            return playerCache.get(key);
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.hasPlayedBefore()) {
            return null;
        }

        playerCache.put(key, player);
        return player;
    }

    @Override
    public FileConfiguration getConfig() {
        if (!config.exists()) {
            saveDefaultConfig();
        }

        return super.getConfig();
    }

    public abstract boolean isDebugEnabled();
}
