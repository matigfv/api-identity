<div align="center">

# Identity API

**El servicio de identidad de la suite M2: usuarios, workspaces e invitaciones.**
Una sola fuente de verdad para quién es cada persona y con quién comparte datos, para api-movements y api-keep.

![Java](https://img.shields.io/badge/Java-25-ed8b00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479a1?logo=mysql&logoColor=white)
![Keycloak](https://img.shields.io/badge/Auth-Keycloak%20JWT-4d4d4d?logo=keycloak&logoColor=white)

<img src="docs/architecture.svg" alt="Arquitectura de api-identity: Spring Security, controllers, servicios de dominio, rate limiter con Redis, auditoría, MySQL y eventos por RabbitMQ" width="900" />

</div>

---

## Qué hace

| Área | Para qué sirve |
| --- | --- |
| **Usuarios** | Se crean solos en el primer login; la app llamante se resuelve desde el claim `app` del JWT (se define por cliente en Keycloak), no desde un header. |
| **Workspaces** | Crear, listar y salir de workspaces, con roles `OWNER`, `COLLABORATOR` y `READ_ONLY`. Traspaso de titularidad y expulsión de miembros. |
| **Invitaciones** | Invitá por email, aceptá o rechazá. Vencen a los 7 días y se limitan a 10 lotes por hora por usuario. |
| **Onboarding** | `POST /v1/onboarding/start` crea usuario y primer workspace en una sola transacción; además registra primer login y tour. |
| **Auditoría** | Quién invitó, aceptó, rechazó, entró o salió de un workspace (solo `OWNER` y `COLLABORATOR`). |
| **Resumen admin** | Totales de la instancia (usuarios, workspaces, invitaciones pendientes) para el panel de fe-identity (`ROLE_ADMIN`). |
| **Rate limiting** | Redis con ventana fija: 10 lotes de invitaciones/hora y 20 altas de usuario/hora. Si Redis falla, deja pasar (fail-open). |
| **Eventos** | Publica `invitation.sent`, `invitation.accepted` y `member.removed` en RabbitMQ para api-movements y api-keep. |

No está expuesto a internet: lo consumen los otros backends y el frontend de administración.

---

## Tutorial: levantarlo en tu máquina

### Requisitos

- **Java 25** (JDK; GraalVM para el build nativo) y Gradle (se usa el wrapper `./gradlew`).
- **Docker**, para MySQL, RabbitMQ y Redis.
- Un Keycloak accesible (el issuer de los JWT está en `application.yaml`).

### Pasos

```bash
# 1. Clonar
git clone https://github.com/matiasferrerovilas/api-identity.git
cd api-identity

# 2. Levantar las dependencias (MySQL y RabbitMQ)
docker compose -f docker-compose/docker-compose.yml up -d

# 3. Correr la API con el perfil dev
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Hace falta además un Redis en `localhost:6379`. Si necesitás credenciales propias, creá `src/main/resources/application-dev.yaml` con tu `spring.datasource`, `spring.rabbitmq` y `spring.data.redis`. La API queda en <http://localhost:8081> (variable `PORT`) y Liquibase crea el schema solo.

### Probala con datos de ejemplo

El perfil `demo` (`DemoDataSeeder`) carga un usuario `demo@example.com` y un workspace con **id 1**:

```bash
./gradlew bootRun --args='--spring.profiles.active=demo'
```

- Solo corre con el perfil `demo` y es idempotente: reiniciar no duplica datos.
- El id 1 es una convención de la suite: los perfiles `demo` de api-movements y api-keep asumen que ese workspace ya existe, así que levantá los tres con `demo`.

### Documentación interactiva

- Swagger UI: <http://localhost:8081/docs>

### Autenticación

Pedí un token a tu realm de Keycloak y mandalo en cada request:

```
Authorization: Bearer <token>
```

Los roles salen de `realm_access` (`ROLE_ADMIN`, `ROLE_FAMILY`, `ROLE_GUEST`). `/v1/admin` y `/actuator/**` exigen admin.

---

## Tutorial: probar un endpoint

```bash
# Tu usuario (se crea en la primera llamada)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/v1/users/me

# Miembros de tus workspaces
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/v1/workspaces/members

# Auditoría de un workspace
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/v1/workspaces/1/audit-log
```

La lista completa de endpoints está en Swagger.

---

## Scripts útiles

| Comando | Qué hace |
| --- | --- |
| `./gradlew bootRun --args='--spring.profiles.active=dev'` | Levanta la API en desarrollo. |
| `./gradlew test` | Corre los tests (Spock + Testcontainers). |
| `./gradlew checkstyleMain checkstyleTest` | Corre checkstyle. |
| `./gradlew test checkstyleMain checkstyleTest` | Las dos cosas; es lo que se espera antes de dar un cambio por terminado. |
| `docker build -t api-identity .` | Construye la imagen nativa con GraalVM. |

---

## Cómo está armado

El diagrama de arriba resume el flujo: cada request pasa por Spring Security (JWT validado con el JWKS de Keycloak), los controllers `/v1` y los servicios de dominio, que escriben en MySQL. Las invitaciones y las expulsiones se publican en el exchange `identity.topic` de RabbitMQ; api-identity solo publica, cada consumidor declara su propia cola.

- **Stack:** Java 25 · Spring Boot 4.1 · Spring Data JPA · Liquibase · MapStruct · Redis (rate limit) · RabbitMQ · GraalVM native · Spock.
- **Base de datos:** el schema lo maneja Liquibase (`ddl-auto: none`), con migraciones en `src/main/resources/db/changelog/`.
- **Trazabilidad:** `CorrelationIdFilter` propaga `X-Correlation-Id` en cada request.
- **Monitoreo:** `/actuator/health`, `/actuator/info` y `/actuator/metrics` son públicos; el resto de `/actuator/**` requiere admin.

```
src/main/java/com/api/identity/
├── controllers/    # REST bajo /v1: users, onboarding, workspaces, invitations, admin
├── services/       # Dominio: usuarios, workspaces, invitaciones, auditoría, rate limit
├── entities/       # Entidades JPA
├── events/         # Eventos publicados en RabbitMQ
├── security/       # Resource server JWT y roles
├── configuration/  # RabbitMQ, OpenAPI, CORS, hints de GraalVM
└── demo/           # Seeder del perfil demo
docs/               # Diagrama de arquitectura del README
```

## Despliegue

La CI construye una imagen nativa y la publica. En producción hacen falta, entre otras, las credenciales de MySQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), RabbitMQ, Redis (`REDIS_PORT`) y el issuer de Keycloak; `CORS_ALLOWED_ORIGINS` define el origen del frontend. Revisá `application-prod.yaml` para la lista completa.

## Contribuir

- Todo cambio de código lleva su test (Spock) y pasa `checkstyle`.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/) (`feat:`, `fix:`, `docs:`...).
- Cada cambio se anota en el [CHANGELOG](CHANGELOG.md).

## Licencia

MIT — ver [LICENSE](LICENSE).
