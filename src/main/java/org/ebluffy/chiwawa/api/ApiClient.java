package org.ebluffy.chiwawa.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import org.ebluffy.chiwawa.api.dto.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * API клиент для взаимодействия с сайтом ChiwawaMine
 */
public class ApiClient {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String apiKey;
    private final int timeout;
    private final int maxRetries;

    public ApiClient(JavaPlugin plugin, String baseUrl, String apiKey, int timeout, int maxRetries) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxRetries = maxRetries;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Получить пользователя по никнейму
     */
    public CompletableFuture<ChiwawaUser> getUserByNickname(String nickname) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users?nickname=" + nickname;
                HttpResponse<String> response = makeRequest(url, "GET", null);

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("users") && json.getAsJsonArray("users").size() > 0) {
                        JsonObject userJson = json.getAsJsonArray("users").get(0).getAsJsonObject();
                        return gson.fromJson(userJson, ChiwawaUser.class);
                    }
                }
                return null;
            } catch (Exception e) {
                logger.severe("Ошибка получения пользователя " + nickname + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Получить статистику игрока
     */
    public CompletableFuture<PlayerStats> getPlayerStats(int userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/stats";
                HttpResponse<String> response = makeRequest(url, "GET", null);

                if (response.statusCode() == 200) {
                    return gson.fromJson(response.body(), PlayerStats.class);
                }
                return null;
            } catch (Exception e) {
                logger.severe("Ошибка получения статистики игрока " + userId + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Проверить игровой токен
     */
    public CompletableFuture<TokenVerificationResult> verifyGameToken(String token, String nickname) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/auth/verify-game-token";
                JsonObject body = new JsonObject();
                body.addProperty("token", token);
                body.addProperty("nickname", nickname);

                HttpResponse<String> response = makeRequest(url, "POST", body.toString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("valid") && json.get("valid").getAsBoolean()) {
                        JsonObject userJson = json.getAsJsonObject("user");
                        return new TokenVerificationResult(
                            true,
                            userJson.get("id").getAsInt(),
                            userJson.get("nickname").getAsString(),
                            userJson.get("role").getAsString(),
                            userJson.get("trust_level").getAsInt(),
                            json.get("message").getAsString()
                        );
                    }
                }
                
                JsonObject errorJson = JsonParser.parseString(response.body()).getAsJsonObject();
                String errorMessage = errorJson.has("error") ? errorJson.get("error").getAsString() : "Неизвестная ошибка";
                return new TokenVerificationResult(false, 0, null, null, 0, errorMessage);
                
            } catch (Exception e) {
                logger.severe("Ошибка проверки игрового токена: " + e.getMessage());
                return new TokenVerificationResult(false, 0, null, null, 0, "Ошибка соединения с сервером");
            }
        });
    }

    /**
     * Проверить доступ к серверу
     */
    public CompletableFuture<Boolean> checkServerAccess(String nickname) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/plugin/server-access?nickname=" + nickname;
                HttpResponse<String> response = makeRequest(url, "GET", null);

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.has("hasAccess") && json.get("hasAccess").getAsBoolean();
                }
                return false;
            } catch (Exception e) {
                logger.severe("Ошибка проверки доступа для " + nickname + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Обновить время игры
     */
    public CompletableFuture<Boolean> updatePlaytime(int userId, int minutes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/playtime";
                JsonObject body = new JsonObject();
                body.addProperty("playtime_minutes", minutes);

                HttpResponse<String> response = makeRequest(url, "PUT", body.toString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка обновления времени игры для пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Записать активность игрока
     */
    public CompletableFuture<Boolean> recordActivity(int userId, String activityType, String description, String metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/user-activity";
                JsonObject body = new JsonObject();
                body.addProperty("user_id", userId);
                body.addProperty("activity_type", activityType);
                body.addProperty("description", description);
                if (metadata != null) {
                    body.addProperty("metadata", metadata);
                }

                HttpResponse<String> response = makeRequest(url, "POST", body.toString());
                return response.statusCode() == 201 || response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка записи активности для пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Забанить игрока
     */
    public CompletableFuture<Boolean> banPlayer(int userId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/ban";
                JsonObject body = new JsonObject();
                body.addProperty("reason", reason);

                HttpResponse<String> response = makeRequest(url, "PUT", body.toString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка бана пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Разбанить игрока
     */
    public CompletableFuture<Boolean> unbanPlayer(int userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/unban";
                HttpResponse<String> response = makeRequest(url, "PUT", null);
                return response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка разбана пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Изменить trust level
     */
    public CompletableFuture<Boolean> updateTrustLevel(int userId, int trustLevel) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/trust-level";
                JsonObject body = new JsonObject();
                body.addProperty("trust_level", trustLevel);

                HttpResponse<String> response = makeRequest(url, "PUT", body.toString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка изменения trust level для пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Обновить репутацию
     */
    public CompletableFuture<Boolean> updateReputation(int userId, int reputationChange, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/admin/users/" + userId + "/reputation";
                JsonObject body = new JsonObject();
                body.addProperty("reputation_change", reputationChange);
                body.addProperty("reason", reason);

                HttpResponse<String> response = makeRequest(url, "PUT", body.toString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                logger.severe("Ошибка изменения репутации для пользователя " + userId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Получить статус сервера
     */
    public CompletableFuture<JsonObject> getServerInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/plugin/server-info";
                HttpResponse<String> response = makeRequest(url, "GET", null);

                if (response.statusCode() == 200) {
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                }
                return new JsonObject();
            } catch (Exception e) {
                logger.severe("Ошибка получения информации о сервере: " + e.getMessage());
                return new JsonObject();
            }
        });
    }

    /**
     * Выполнить HTTP запрос с повторными попытками
     */
    private HttpResponse<String> makeRequest(String url, String method, String body) throws IOException, InterruptedException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "ChiwawaPlugin/1.0")
                        .timeout(Duration.ofMillis(timeout));

                HttpRequest request = switch (method.toUpperCase()) {
                    case "GET" -> requestBuilder.GET().build();
                    case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "")).build();
                    case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : "")).build();
                    case "DELETE" -> requestBuilder.DELETE().build();
                    default -> throw new IllegalArgumentException("Неподдерживаемый HTTP метод: " + method);
                };

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Если статус успешный или клиентская ошибка (не требует повтора)
                if (response.statusCode() < 500) {
                    return response;
                }

                logger.warning("Попытка " + attempt + " неуспешна. Статус: " + response.statusCode() + ". URL: " + url);

            } catch (Exception e) {
                lastException = e;
                logger.warning("Попытка " + attempt + " провалена для " + url + ": " + e.getMessage());
            }

            // Пауза перед следующей попыткой
            if (attempt < maxRetries) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000 * attempt); // Экспоненциальная задержка
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw new IOException("Все " + maxRetries + " попыток провалены для " + url, lastException);
    }

    /**
     * Создание игровой сессии после успешной авторизации
     */
    public CompletableFuture<Boolean> createGameSession(String nickname, String playerUuid, String ipAddress, String userAgent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/auth/create-game-session";
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("nickname", nickname);
                requestBody.addProperty("player_uuid", playerUuid);
                requestBody.addProperty("ip_address", ipAddress);
                requestBody.addProperty("user_agent", userAgent);

                HttpResponse<String> response = makeRequest(url, "POST", requestBody.toString());
                
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    return jsonResponse.get("success").getAsBoolean();
                } else {
                    logger.warning("Не удалось создать игровую сессию: " + response.statusCode() + " - " + response.body());
                    return false;
                }
            } catch (Exception e) {
                logger.severe("Ошибка создания игровой сессии: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Проверка активной игровой сессии
     */
    public CompletableFuture<SessionCheckResult> checkGameSession(String nickname, String playerUuid, String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/auth/check-game-session";
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("nickname", nickname);
                requestBody.addProperty("player_uuid", playerUuid);
                requestBody.addProperty("ip_address", ipAddress);

                HttpResponse<String> response = makeRequest(url, "POST", requestBody.toString());
                
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    
                    if (jsonResponse.get("session_valid").getAsBoolean()) {
                        JsonObject user = jsonResponse.getAsJsonObject("user");
                        
                        return new SessionCheckResult(
                            true,
                            user.get("id").getAsInt(),
                            user.get("nickname").getAsString(),
                            user.get("role").getAsString(),
                            user.get("trust_level").getAsInt(),
                            "Сессия действительна"
                        );
                    } else {
                        return new SessionCheckResult(false, 0, "", "", 0, 
                            jsonResponse.get("error").getAsString());
                    }
                } else {
                    return new SessionCheckResult(false, 0, "", "", 0, 
                        "Сессия не найдена или истекла");
                }
            } catch (Exception e) {
                logger.severe("Ошибка проверки игровой сессии: " + e.getMessage());
                return new SessionCheckResult(false, 0, "", "", 0, 
                    "Ошибка соединения с сервером");
            }
        });
    }

    /**
     * Отправка детальной статистики игрока на сервер
     */
    public CompletableFuture<Boolean> updatePlayerStats(String minecraftNick, java.util.Map<String, Object> stats) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/profile/update-stats";
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("minecraft_nick", minecraftNick);
                // Убираем admin_token из тела - теперь используется заголовок Authorization
                
                // Конвертируем статистику в JSON
                JsonObject statsJson = gson.toJsonTree(stats).getAsJsonObject();
                requestBody.add("stats", statsJson);
                
                HttpResponse<String> response = makeRequest(url, "POST", requestBody.toString());
                
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                    
                    if (success) {
                        logger.fine("Статистика игрока " + minecraftNick + " успешно обновлена");
                    } else {
                        String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Неизвестная ошибка";
                        logger.warning("Ошибка обновления статистики игрока " + minecraftNick + ": " + error);
                    }
                    
                    return success;
                } else {
                    logger.warning("Ошибка HTTP при обновлении статистики игрока " + minecraftNick + ": " + response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                logger.severe("Ошибка отправки статистики игрока " + minecraftNick + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Проверка соединения с API
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/plugin/server-info";
                HttpResponse<String> response = makeRequest(url, "GET", null);
                
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.has("success") && json.get("success").getAsBoolean();
                }
                return false;
            } catch (Exception e) {
                logger.severe("Ошибка проверки соединения с API: " + e.getMessage());
                return false;
            }
        });
    }
}
