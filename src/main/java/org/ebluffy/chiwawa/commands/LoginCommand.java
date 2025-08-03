package org.ebluffy.chiwawa.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.ebluffy.chiwawa.api.ApiClient;
import org.ebluffy.chiwawa.api.dto.TokenVerificationResult;
import org.ebluffy.chiwawa.config.ConfigManager;
import org.ebluffy.chiwawa.managers.UserManager;

import java.util.logging.Logger;

/**
 * Команда для авторизации игрока с помощью токена
 */
public class LoginCommand implements CommandExecutor {
    private final ConfigManager configManager;
    private final ApiClient apiClient;
    private final UserManager userManager;
    private final Logger logger;

    public LoginCommand(ConfigManager configManager, ApiClient apiClient, UserManager userManager, Logger logger) {
        this.configManager = configManager;
        this.apiClient = apiClient;
        this.userManager = userManager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("§cИспользование: /login <токен>");
            player.sendMessage("§7Получите токен на сайте: §b§nhttps://chiwawa.site");
            return true;
        }

        String token = args[0];
        String nickname = player.getName();

        player.sendMessage("§7Проверка токена...");

        apiClient.verifyGameToken(token, nickname)
            .thenAccept(result -> {
                if (result.isValid()) {
                    // Токен действителен, авторизуем игрока
                    userManager.authorizePlayer(player, result.getUserId());
                    
                    // Создаем игровую сессию
                    apiClient.createGameSession(
                        nickname, 
                        player.getUniqueId().toString(),
                        player.getAddress().getAddress().getHostAddress(),
                        "Minecraft Client"
                    ).thenAccept(sessionResult -> {
                        if (sessionResult) {
                            logger.info("Игровая сессия создана для игрока " + nickname);
                        } else {
                            logger.warning("Не удалось создать игровую сессию для игрока " + nickname);
                        }
                    }).exceptionally(throwable -> {
                        logger.warning("Ошибка создания игровой сессии для " + nickname + ": " + throwable.getMessage());
                        return null;
                    });
                    
                    player.sendMessage("§a§l✓ Авторизация успешна!");
                    player.sendMessage("§7Добро пожаловать, §a" + result.getNickname() + "§7!");
                    player.sendMessage("§7Роль: §a" + getRoleDisplayName(result.getRole()));
                    player.sendMessage("§7Trust Level: §a" + result.getTrustLevel());
                    player.sendMessage("§7Сессия создана на 7 дней");
                    
                    logger.info("Игрок " + nickname + " успешно авторизовался с токеном (ID: " + result.getUserId() + ")");
                } else {
                    // Токен недействителен
                    player.sendMessage("§c§l✗ Ошибка авторизации!");
                    player.sendMessage("§7" + result.getMessage());
                    player.sendMessage("§7Получите новый токен на сайте: §b§nhttps://chiwawa.site");
                    
                    logger.warning("Неудачная попытка авторизации игрока " + nickname + ": " + result.getMessage());
                }
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c§l✗ Ошибка соединения с сервером!");
                player.sendMessage("§7Попробуйте позже или обратитесь к администрации.");
                
                logger.severe("Ошибка проверки токена для игрока " + nickname + ": " + throwable.getMessage());
                return null;
            });

        return true;
    }

    /**
     * Получить отображаемое название роли
     */
    private String getRoleDisplayName(String role) {
        switch (role) {
            case "admin": return "Администратор";
            case "moderator": return "Модератор";
            case "user": return "Пользователь";
            default: return "Неизвестная роль";
        }
    }
}
