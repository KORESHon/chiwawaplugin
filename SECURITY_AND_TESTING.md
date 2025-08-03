# ChiwawaPlugin - Руководство по безопасности и тестированию

## 🔒 Рекомендации по безопасности

### 1. Хранение API-ключа
```yaml
# ❌ НИКОГДА не делайте так:
api:
  api-key: "my-secret-key-123"  # Открытый текст в конфиге

# ✅ Рекомендуемые способы:
```

**Способ 1: Переменные окружения**
```bash
# В startup скрипте сервера
export CHIWAWA_API_KEY="your-secret-api-key"
```

**Способ 2: Отдельный защищенный файл**
```yaml
# config.yml
api:
  api-key-file: "/secure/path/api-key.txt"
```

**Способ 3: Шифрование в конфиге**
```yaml
# config.yml
api:
  api-key-encrypted: "U2FsdGVkX1+vupppZksvRf5pq5g5XjFRIipRkwB0K1Y96Qsv2Lm+31cmzaAILwyt"
security:
  encrypt-sensitive-data: true
```

### 2. Настройки безопасности
```yaml
# config.yml
security:
  # Обязательная валидация SSL
  validate-ssl: true
  
  # Whitelist IP адресов для API (если нужно)
  allowed-api-ips:
    - "192.168.1.100"
    - "10.0.0.50"
  
  # Rate limiting
  rate-limit:
    enabled: true
    max-requests-per-minute: 60
    
  # Таймауты
  timeouts:
    connect: 10
    read: 30
    write: 30
```

### 3. Логирование и мониторинг
```yaml
logging:
  # НЕ логируйте чувствительные данные
  log-api-requests: true
  log-errors-to-file: true
  
  # Уровни логирования для продакшена
  level: "WARN"  # или "ERROR"
```

## 🧪 Тестирование интеграции

### Локальная настройка для тестирования

#### 1. Запуск тестового API сервера (Node.js)
```javascript
// test-server.js
const express = require('express');
const app = express();
app.use(express.json());

// Middleware для авторизации
app.use((req, res, next) => {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token || token !== 'test-api-key') {
    return res.status(401).json({ success: false, message: 'Unauthorized' });
  }
  next();
});

// Health check
app.get('/api/health', (req, res) => {
  res.json({ success: true, message: 'API is running' });
});

// Player data endpoints
app.get('/api/players/:uuid', (req, res) => {
  res.json({
    success: true,
    data: {
      uuid: req.params.uuid,
      username: 'TestPlayer',
      trust_level: 1,
      is_whitelisted: true,
      playtime_minutes: 120
    }
  });
});

app.put('/api/players/:uuid', (req, res) => {
  console.log('Player data updated:', req.body);
  res.json({ success: true });
});

// Whitelist endpoint
app.get('/api/whitelist', (req, res) => {
  res.json({
    success: true,
    players: ['TestPlayer1', 'TestPlayer2', 'TestPlayer3'],
    timestamp: Date.now()
  });
});

app.listen(3000, () => {
  console.log('Test API server running on http://localhost:3000');
});
```

#### 2. Настройка конфига для тестирования
```yaml
# config.yml (для локального тестирования)
api:
  base-url: "http://localhost:3000/api"
  api-key: "test-api-key"
  
  timeout:
    connect: 5
    read: 10
    write: 10
    
logging:
  level: "DEBUG"
  log-api-requests: true
```

#### 3. Запуск тестов
```bash
# Установка зависимостей для тестового сервера
npm install express

# Запуск тестового API сервера
node test-server.js

# В другом терминале - запуск Minecraft сервера с плагином
java -jar paper-1.21.jar

# Или запуск unit тестов
mvn test
```

### Проверочный чек-лист для тестирования

#### ✅ API Соединение
- [ ] Плагин успешно подключается к API при старте
- [ ] Корректная обработка ошибок соединения
- [ ] Retry логика работает при временных сбоях
- [ ] Rate limiting не блокирует нормальные запросы

#### ✅ Данные игроков
- [ ] При входе игрока данные отправляются на сервер
- [ ] При выходе статистика корректно обновляется
- [ ] Trust Level применяется правильно
- [ ] Whitelist проверка работает

#### ✅ Команды
- [ ] `/chiwawa status` показывает корректную информацию
- [ ] `/chiwawa sync players` запускает синхронизацию
- [ ] `/chiwawa test` проверяет соединение с API
- [ ] Права доступа к командам работают

#### ✅ Производительность
- [ ] Асинхронные запросы не блокируют сервер
- [ ] Периодические задачи не вызывают лагов
- [ ] Память не течет при длительной работе

## 🐛 Отладка

### Включение debug логирования
```yaml
# config.yml
logging:
  level: "DEBUG"
  log-api-requests: true
  log-errors-to-file: true
```

### Полезные команды для отладки
```bash
# Проверка статуса плагина
/chiwawa status

# Тест API соединения
/chiwawa test

# Принудительная синхронизация
/chiwawa sync all

# Информация об игроке
/chiwawa player PlayerName info
```

### Анализ логов
```bash
# Поиск ошибок API в логах
grep -i "api" logs/latest.log | grep -i "error"

# Мониторинг запросов в реальном времени
tail -f logs/latest.log | grep "ApiClient"
```

### Типичные проблемы и решения

#### 1. "API недоступен"
- Проверьте URL в конфиге
- Убедитесь что веб-сервер запущен
- Проверьте firewall настройки

#### 2. "Ошибка авторизации"
- Проверьте правильность API ключа
- Убедитесь что ключ не содержит лишних пробелов
- Проверьте формат заголовка Authorization

#### 3. "Timeout ошибки"
- Увеличьте таймауты в конфиге
- Проверьте сетевую задержку до API сервера
- Оптимизируйте SQL запросы на стороне API

#### 4. "Rate limit exceeded"
- Уменьшите частоту синхронизации
- Увеличьте лимиты на стороне API
- Используйте batch запросы для массовых операций

## 📊 Мониторинг в продакшене

### Метрики для отслеживания
- Количество успешных/неуспешных API запросов
- Среднее время отклика API
- Количество rate limit ошибок
- Использование памяти плагином

### Настройка алертов
- API недоступен более 5 минут
- Ошибки API превышают 10% от общего количества запросов
- Время отклика API превышает 5 секунд

### Логирование для анализа
```yaml
# Структурированное логирование для анализа
logging:
  format: json
  fields:
    - timestamp
    - level
    - message
    - api_endpoint
    - response_time
    - player_uuid
```
