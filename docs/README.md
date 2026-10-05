# Документация common

`corelia-common` задаёт технические, а не доменные границы: внутренний
`ServiceClient`, mTLS caller identity, JWT verification и transport errors.
Внутренний HTTP вызов сохраняет пользовательский `AuthContext`; сертификат
указывает вызывающий сервис. Domain authorization по permission и состоянию
остаётся у сервисов-владельцев.

Настройки по умолчанию находятся в `corelia-defaults.properties`, а service
properties переопределяют TLS, port и зависимости. `REDIS_URL` включает cache,
`REDIS_CONNECT_TIMEOUT_MS` задаёт подключение, task cache использует fresh/stale
TTL. Подробная топология: [архитектура](../../docs/architecture.md); security:
[operations](../../docs/operations.md).
