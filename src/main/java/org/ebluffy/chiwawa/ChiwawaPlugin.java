package org.ebluffy.chiwawa;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.ebluffy.chiwawa.api.ApiClient;
import org.ebluffy.chiwawa.commands.*;
import org.ebluffy.chiwawa.config.ConfigManager;
import org.ebluffy.chiwawa.listeners.PlayerListener;
import org.ebluffy.chiwawa.listeners.AuthenticationListener;
import org.ebluffy.chiwawa.managers.*;

import java.util.logging.Logger;

/**
 * Главный класс плагина ChiwawaPlugin для интеграции с сайтом ChiwawaMine
 */
public class ChiwawaPlugin extends JavaPlugin {
    private static ChiwawaPlugin instance;

    // Менеджеры
    private ConfigManager configManager;
    private ApiClient apiClient;
    private UserManager userManager;
    private PlaytimeManager playtimeManager;
    private ReputationManager reputationManager;
    private StatsManager statsManager;

    // Логгер
    private Logger logger;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();

        try {
            logger.info("Запуск ChiwawaPlugin v1.0...");

            // 1. Загрузка конфигурации
            configManager = new ConfigManager(this);
            if (!configManager.validateConfig()) {
                logger.severe("Конфигурация содержит ошибки! Плагин отключается.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // 2. Инициализация API клиента
            apiClient = new ApiClient(this,
                configManager.getApiBaseUrl(),
                configManager.getAdminToken(),
                configManager.getApiTimeout(),
                configManager.getRetryAttempts());

            // 3. Инициализация менеджеров
            userManager = new UserManager(this, apiClient);
            playtimeManager = new PlaytimeManager(this, apiClient, userManager, configManager);
            reputationManager = new ReputationManager(this, apiClient, userManager, configManager);
            statsManager = new StatsManager(this, apiClient, userManager, configManager);

            // 4. Регистрация команд
            registerCommands();

            // 5. Регистрация слушателей событий
            registerListeners();

            // 6. Запуск менеджеров
            startManagers();

            // 7. Тест соединения с API
            testApiConnection();

            logger.info("ChiwawaPlugin успешно включен!");
            logger.info("API URL: " + configManager.getApiBaseUrl());
            logger.info("Whitelist: " + (configManager.isWhitelistEnabled() ? "включен" : "отключен"));
            logger.info("Лимит времени: " + (configManager.isTimeLimitEnabled() ? "включен" : "отключен"));

        } catch (Exception e) {
            logger.severe("Критическая ошибка при включении плагина: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            logger.info("Отключение ChiwawaPlugin...");

            // Остановка менеджеров
            if (playtimeManager != null) {
                playtimeManager.stop();
            }
            
            if (statsManager != null) {
                statsManager.stop();
            }

            // Сохранение данных всех онлайн игроков
            if (playtimeManager != null && userManager != null) {
                playtimeManager.saveAllPlaytimes().join();
                logger.info("Сохранено время игры всех игроков");
            }

            // Очистка кешей
            if (userManager != null) {
                userManager.clearCache();
            }

            logger.info("ChiwawaPlugin отключен!");

        } catch (Exception e) {
            logger.severe("Ошибка при отключении плагина: " + e.getMessage());
            e.printStackTrace();
        } finally {
            instance = null;
        }
    }

    /**
     * Регистрация команд плагина
     */
    private void registerCommands() {
        // Главная админ команда
        ChiwawaCommand chiwawaCommand = new ChiwawaCommand(this, configManager, userManager, playtimeManager, reputationManager);
        getCommand("chiwawa").setExecutor(chiwawaCommand);
        getCommand("chiwawa").setTabCompleter(chiwawaCommand);

        // Команды для игроков
        getCommand("profile").setExecutor(new ProfileCommand(configManager, userManager, playtimeManager));
        getCommand("playtime").setExecutor(new PlaytimeCommand(configManager, playtimeManager, userManager));
        getCommand("applications").setExecutor(new ApplicationsCommand(configManager, userManager));
        getCommand("discord").setExecutor(new DiscordCommand(configManager));
        getCommand("login").setExecutor(new LoginCommand(configManager, apiClient, userManager, logger));

        ReputationCommand repCommand = new ReputationCommand(configManager, reputationManager, userManager);
        getCommand("rep").setExecutor(repCommand);
        getCommand("rep").setTabCompleter(repCommand);

        logger.info("Команды зарегистрированы");
    }

    /**
     * Регистрация слушателей событий
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerListener(this, configManager, userManager, playtimeManager), this);
        
        getServer().getPluginManager().registerEvents(
            new AuthenticationListener(this, userManager), this);

        logger.info("Слушатели событий зарегистрированы");
    }

    /**
     * Запуск менеджеров
     */
    private void startManagers() {
        if (configManager.isTimeLimitEnabled()) {
            playtimeManager.start();
            logger.info("Менеджер времени игры запущен");
        }
        
        // Запускаем менеджер статистики
        statsManager.start();
        logger.info("Менеджер статистики запущен");
    }

    /**
     * Тест соединения с API
     */
    private void testApiConnection() {
        apiClient.testConnection()
            .thenAccept(connected -> {
                if (connected) {
                    logger.info("Соединение с API установлено успешно");
                } else {
                    logger.warning("Не удалось подключиться к API!");
                    logger.warning("Проверьте настройки api.base_url и api.admin_token в config.yml");
                }
            })
            .exceptionally(throwable -> {
                logger.severe("Ошибка тестирования API: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * Перезагрузить плагин
     */
    public void reloadPlugin() {
        logger.info("Перезагрузка плагина...");

        // Остановка менеджеров
        if (playtimeManager != null) {
            playtimeManager.stop();
        }

        // Перезагрузка конфигурации
        configManager.reloadConfig();

        // Перезапуск менеджеров
        if (configManager.isTimeLimitEnabled()) {
            playtimeManager.start();
        }

        logger.info("Плагин перезагружен!");
    }

    // Геттеры для доступа к менеджерам
    public static ChiwawaPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }
    
    public StatsManager getStatsManager() {
        return statsManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    public ReputationManager getReputationManager() {
        return reputationManager;
    }
}
