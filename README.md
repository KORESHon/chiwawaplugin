# ChiwawaPlugin - Архитектура интеграции с веб-сайтом

## 📋 Обзор архитектуры

Создана полноценная архитектура для интеграции Minecraft плагина (Spigot/Paper 1.21.x) с веб-сайтом через REST API.

### 🏗️ Структура проекта
```
ChiwawaPlugin/
├── pom.xml                      # Maven конфигурация с JDK 21
├── SECURITY_AND_TESTING.md     # Руководство по безопасности
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── Main.java                    # Главный класс плагина
    │   │   ├── api/
    │   │   │   ├── ApiClient.java           # HTTP клиент для API
    │   │   │   └── dto/                     # Data Transfer Objects
    │   │   │       ├── PlayerData.java     # Данные игрока
    │   │   │       ├── WhitelistResponse.java
    │   │   │       └── ApiResponse.java    # Базовый ответ API
    │   │   ├── config/
    │   │   │   └── ConfigManager.java      # Управление конфигурацией
    │   │   ├── listeners/
    │   │   │   └── PlayerListener.java     # События игроков
    │   │   ├── commands/
    │   │   │   └── ChiwawaCommand.java     # Команды плагина
    │   │   └── utils/
    │   │       ├── SecurityUtils.java      # Утилиты безопасности
    │   │       └── ApiRequestExamples.java # Примеры запросов
    │   └── resources/
    │       ├── plugin.yml          # Метаданные плагина
    │       └── config.yml          # Конфигурация
    └── test/
        └── java/org/example/
            └── IntegrationTest.java # Тесты интеграции
```

## 🔗 API Endpoints и функциональность

### Основные эндпоинты:
- `GET /api/health` - Проверка состояния API
- `GET /api/players/{uuid}` - Получение данных игрока
- `PUT /api/players/{uuid}` - Обновление данных игрока
- `POST /api/players/{uuid}/stats` - Отправка статистики
- `GET /api/whitelist` - Получение списка whitelist
- `POST /api/server/stats` - Отправка статистики сервера

### Функции плагина:
✅ **Авторизация**: Bearer токен в заголовках  
✅ **Синхронизация whitelist**: Автоматическое обновление  
✅ **Trust Level система**: Лимиты по уровням доверия  
✅ **Статистика игроков**: Время игры, вход/выход  
✅ **Rate limiting**: Защита от злоупотреблений  
✅ **Retry логика**: Устойчивость к сетевым сбоям  
✅ **Асинхронные запросы**: Без блокировки сервера  

## ⚙️ Конфигурация (config.yml)

```yaml
api:
  base-url: "https://your-website.com/api"
  api-key: "your-secret-api-key-here"
  timeout:
    connect: 10
    read: 30
    write: 30
  retry:
    max-attempts: 3
    delay-seconds: 5
  sync-interval: 5  # минуты

whitelist:
  auto-sync: true
  enforce: true
  kick-message: "&cВы не в whitelist! Подайте заявку на сайте."

trust-level:
  enabled: true
  limits:
    0: # Новичок
      max-claims: 1
      max-homes: 1
      daily-playtime-hours: 4
    1: # Доверенный  
      max-claims: 3
      max-homes: 2
      daily-playtime-hours: 8
    # ... и т.д.

security:
  validate-ssl: true
  allowed-api-ips: []
  encrypt-sensitive-data: false

performance:
  async-requests: true
  player-cache-duration: 30
  max-concurrent-requests: 5
```

## 🛠️ Сборка и установка

### Требования:
- **JDK 21**
- **Maven 3.6+**
- **Spigot/Paper 1.21.x**

### Команды сборки:
```bash
# Компиляция
mvn clean compile

# Сборка fat-jar с зависимостями
mvn clean package

# Запуск тестов
mvn test

# Установка в локальный репозиторий
mvn install
```

### Результат сборки:
`target/ChiwawaPlugin-1.0-SNAPSHOT.jar` - готовый плагин для сервера

## 🔒 Безопасность

### Хранение API-ключа:
```bash
# ❌ Не храните ключ открытым текстом в config.yml
# ✅ Используйте переменные окружения:
export CHIWAWA_API_KEY="your-secret-key"

# ✅ Или отдельный защищенный файл:
api:
  api-key-file: "/secure/path/api-key.txt"
```

### Защитные механизмы:
- **Rate limiting**: 60 запросов в минуту на IP
- **SSL валидация**: Обязательная проверка сертификатов
- **Input валидация**: UUID, имена игроков, IP адреса
- **Шифрование**: AES для чувствительных данных
- **Логирование**: Безопасное логирование без секретов

## 🎮 Команды плагина

```bash
/chiwawa reload              # Перезагрузить конфиг
/chiwawa status              # Статус плагина и API
/chiwawa sync <type>         # Синхронизация (players/whitelist/all)
/chiwawa test                # Тест соединения с API
/chiwawa player <name> info  # Информация об игроке
```

**Права доступа**: `chiwawa.admin` (по умолчанию для OP)

## 🧪 Тестирование локально

### 1. Запуск тестового API сервера:
```javascript
// test-server.js (Node.js + Express)
const express = require('express');
const app = express();

app.get('/api/health', (req, res) => {
  res.json({ success: true, message: 'API работает' });
});

app.listen(3000, () => console.log('Test API на http://localhost:3000'));
```

### 2. Настройка конфига для тестов:
```yaml
api:
  base-url: "http://localhost:3000/api"
  api-key: "test-api-key"
  
logging:
  level: "DEBUG"
  log-api-requests: true
```

### 3. Проверка работы:
```bash
# В игре или консоли сервера
/chiwawa test
/chiwawa status
/chiwawa sync all
```

## 📊 Мониторинг и отладка

### Логирование:
```yaml
logging:
  level: "INFO"           # DEBUG для отладки
  log-api-requests: true  # Логировать все API запросы
  log-errors-to-file: true
```

### Отладочные команды:
```bash
# Анализ логов
grep -i "ApiClient" logs/latest.log
tail -f logs/latest.log | grep "ERROR"

# Проверка состояния
/chiwawa status
/chiwawa player PlayerName info
```

## 🚀 Рекомендации для продакшена

### API сервер (Node.js/Express):
```javascript
// Пример эндпоинта для получения данных игрока
app.get('/api/players/:uuid', authenticateToken, async (req, res) => {
  try {
    const player = await db.query('SELECT * FROM players WHERE uuid = ?', [req.params.uuid]);
    res.json({
      success: true,
      data: {
        uuid: player.uuid,
        username: player.username,
        trust_level: player.trust_level,
        is_whitelisted: player.is_whitelisted,
        playtime_minutes: player.playtime_minutes
      }
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});
```

### База данных (PostgreSQL):
```sql
-- Таблица игроков
CREATE TABLE players (
    uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    trust_level INTEGER DEFAULT 0,
    is_whitelisted BOOLEAN DEFAULT false,
    playtime_minutes BIGINT DEFAULT 0,
    last_login TIMESTAMP,
    last_logout TIMESTAMP,
    ip_address INET,
    first_join TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Индексы для производительности
CREATE INDEX idx_players_username ON players(username);
CREATE INDEX idx_players_trust_level ON players(trust_level);
CREATE INDEX idx_players_last_login ON players(last_login);
```

### Nginx конфигурация:
```nginx
# Проксирование API запросов
location /api/ {
    proxy_pass http://localhost:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    
    # Rate limiting
    limit_req zone=api burst=10 nodelay;
    
    # Timeout настройки
    proxy_connect_timeout 5s;
    proxy_send_timeout 10s;
    proxy_read_timeout 30s;
}
```

## 📝 Следующие шаги

1. **Установите Maven и JDK 21** для сборки проекта
2. **Настройте веб-сервер** с API эндпоинтами
3. **Создайте базу данных** с таблицами игроков
4. **Настройте конфигурацию** с правильными URL и API ключами
5. **Проведите тестирование** на development окружении
6. **Настройте мониторинг** для production

Архитектура готова к использованию и легко расширяема для добавления новых функций!
