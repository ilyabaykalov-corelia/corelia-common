# corelia-common

Общая библиотека самостоятельных сервисов Corelia. Содержит transport/mTLS,
проверку JWT, `AuthContext`, runtime configuration, HTTP error mapping,
cache/observability utilities и общие DTO. Не является приложением, provider
или местом для document/workflow business rules.

Сервисы получают общую библиотеку через Maven reactor; межсервисный вызов всё
равно проходит по явному HTTP-контракту. Runtime параметры включают адреса
внутренних сервисов, TLS-material, issuer/JWKS/audiences JWT, таймауты и
необязательный Redis cache. Секреты читаются из environment/configtree, не из
исходников.

```bash
mvn -pl corelia-common -am test
```

См. [docs/README.md](docs/README.md) и [архитектуру](../docs/architecture.md).
