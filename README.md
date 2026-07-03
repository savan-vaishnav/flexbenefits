# FlexBenefits

> Multi-tenant employee benefits & claims platform built with **microservices architecture** — Spring Boot 3.4, Kafka, Redis, Prometheus, Grafana, and 10 Docker containers.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green)
![Build](https://img.shields.io/badge/build-passing-brightgreen)

---

## What Is This?

A **SaaS backend** where companies manage employee health insurance plans and reimbursement claims. Two independently deployable microservices communicate via REST and Kafka events, with full observability and resilience patterns.

**Key flows:**
1. Company (tenant) registers → HR creates benefit plans (Medical, Dental, Vision)
2. Employees enroll in plans → submit insurance claims with receipts
3. Claims lifecycle: DRAFT → SUBMITTED → APPROVED/REJECTED
4. Enrollment cancellation → Kafka event → auto-rejects pending claims

---

## Architecture

```
                              ┌──────────────────────┐
                              │   API Gateway (:8080) │
                              │  Spring Cloud Gateway │
                              └──────────┬───────────┘
                                         │
                    ┌────────────────────┼─────────────────────┐
                    │                    │                      │
          ┌─────────▼──────────┐  ┌──────▼────────────┐  ┌────▼──────────┐
          │ benefits-service   │  │ claims-service     │  │ Eureka Server │
          │     (:8082)        │  │    (:8081)         │  │   (:8761)     │
          │                    │  │                    │  └───────────────┘
          │ Tenants, Employees │  │ Claims CRUD        │
          │ Plans, Enrollments │  │ Claim submission   │
          │ Auth (JWT)         │  │ Auto-rejection     │
          │ Documents (MinIO)  │  │                    │
          └──┬──┬──┬──┬───────┘  └──┬──┬──┬──┬───────┘
             │  │  │  │              │  │  │  │
     ┌───────┘  │  │  └──────┐  ┌───┘  │  │  └────────┐
     ▼          ▼  ▼         ▼  ▼      ▼  ▼           ▼
 PostgreSQL  Redis MinIO   Kafka    PostgreSQL  Redis  Kafka
 (flexbenefits)            │        (claimsdb)         │
                           │                           │
                    enrollment-events ──────────────────┘
                    (async event-driven communication)

          ┌───────────────┐    ┌───────────────┐
          │  Prometheus   │───▶│    Grafana     │
          │   (:9090)     │    │   (:3000)      │
          │ Scrapes /actuator/prometheus from both services │
          └───────────────┘    └───────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.1, Spring Cloud 2024.0.0 |
| **Architecture** | Microservices (2 services + gateway + service registry) |
| **Service Discovery** | Netflix Eureka |
| **API Gateway** | Spring Cloud Gateway |
| **Messaging** | Apache Kafka (KRaft mode, async events) |
| **Database** | PostgreSQL 16, Flyway migrations, database-per-service |
| **Caching** | Redis 7 (`@Cacheable` with Kafka-driven eviction) |
| **Resilience** | Resilience4j (circuit breaker, retry, rate limiter) |
| **Auth** | JWT with tenant-scoped tokens |
| **File Storage** | MinIO (S3-compatible) |
| **Observability** | Micrometer + Prometheus + Grafana, structured JSON logging |
| **API Docs** | SpringDoc OpenAPI 2.8 (Swagger UI via Gateway aggregation) |
| **Testing** | JUnit 5, Mockito, AssertJ (42 unit tests) |
| **Containerization** | Docker multi-stage builds, Docker Compose (10 containers) |

---

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Run Everything

```bash
git clone https://github.com/<your-username>/flexbenefits.git
cd flexbenefits

# Start all 10 containers
docker compose up --build -d

# Wait ~90 seconds, then verify
curl http://localhost:8080/api/v1/ping
```

### Quick Test (demo data is auto-seeded)

```bash
# 1. Login as HR admin (seeded automatically)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.com","password":"admin123"}' | jq .token

# 2. List benefit plans (use the token from step 1)
curl -s http://localhost:8080/api/v1/plans \
  -H "Authorization: Bearer <TOKEN>" | jq .

# 3. List enrollments (grab an enrollmentId for claims)
curl -s http://localhost:8080/api/v1/enrollments \
  -H "Authorization: Bearer <TOKEN>" | jq .

# 4. Create a claim
curl -s -X POST http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"<EMPLOYEE_ID>","enrollmentId":"<ENROLLMENT_ID>","serviceDate":"2026-07-01","providerName":"Dr. Smith","diagnosisCode":"J06.9","claimedAmount":1500.00}'
```

### Key URLs

| Service | URL |
|---------|-----|
| Swagger UI (Gateway) | http://localhost:8080/swagger-ui.html |
| Swagger UI (benefits) | http://localhost:8082/swagger-ui.html |
| Swagger UI (claims) | http://localhost:8081/swagger-ui.html |
| Eureka Dashboard | http://localhost:8761 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| MinIO Console | http://localhost:9001 (minioadmin/minioadmin) |

### Run Tests

```bash
./gradlew test
```

---

## API Endpoints

All traffic goes through the API Gateway on port 8080.

| Area | Method | Endpoint | Description |
|------|--------|----------|-------------|
| Auth | POST | `/api/v1/auth/register` | Create account |
| Auth | POST | `/api/v1/auth/login` | Get JWT token |
| Auth | POST | `/api/v1/auth/refresh` | Refresh token |
| Auth | GET | `/api/v1/auth/me` | Current user info |
| Tenants | POST | `/api/v1/tenants` | Create company |
| Tenants | GET | `/api/v1/tenants/{id}` | Get company |
| Plans | POST | `/api/v1/plans` | Create plan |
| Plans | GET | `/api/v1/plans` | List plans (cached) |
| Plans | GET | `/api/v1/plans/{id}` | Get plan (cached) |
| Enrollments | POST | `/api/v1/enrollments` | Enroll in plan |
| Enrollments | GET | `/api/v1/enrollments` | List enrollments |
| Enrollments | GET | `/api/v1/enrollments/{id}` | Get enrollment |
| Enrollments | DELETE | `/api/v1/enrollments/{id}` | Cancel enrollment (publishes Kafka event) |
| Claims | POST | `/api/v1/claims` | Create claim (validates enrollment via inter-service call) |
| Claims | GET | `/api/v1/claims` | List claims (paginated) |
| Claims | GET | `/api/v1/claims/{id}` | Get claim |
| Claims | PUT | `/api/v1/claims/{id}` | Update (DRAFT only) |
| Claims | DELETE | `/api/v1/claims/{id}` | Delete (DRAFT only) |
| Claims | PATCH | `/api/v1/claims/{id}/submit` | Submit (rate limited) |
| Documents | POST | `/api/v1/claims/{id}/documents` | Upload file |
| Documents | GET | `/api/v1/claims/{id}/documents` | List documents |
| Documents | GET | `/api/v1/claims/{id}/documents/{docId}` | Get metadata |
| Documents | GET | `/api/v1/claims/{id}/documents/{docId}/download` | Download |
| Documents | DELETE | `/api/v1/claims/{id}/documents/{docId}` | Delete |
| Health | GET | `/api/v1/ping` | Service health check |

---

## Project Structure

```
flexbenefits/
├── benefits-service/          # Tenants, Employees, Plans, Enrollments, Auth, Documents
│   └── src/main/java/com/flexbenefits/
│       ├── config/            # Security, JWT, Redis, MinIO, Cache, Idempotency
│       ├── controller/        # REST controllers + internal API for claims-service
│       ├── event/             # Kafka event publisher (enrollment events)
│       ├── dto/               # Request/Response records
│       ├── entity/            # JPA entities + enums
│       ├── mapper/            # MapStruct entity-to-DTO mappers
│       ├── repository/        # Spring Data JPA repositories
│       └── service/           # Business logic (with @Cacheable)
│
├── claims-service/            # Claims lifecycle management
│   └── src/main/java/com/claimsservice/
│       ├── client/            # REST client to benefits-service (@Cacheable + circuit breaker)
│       ├── config/            # Security, JWT, Kafka DLT, Redis cache, cache eviction
│       ├── event/             # Kafka consumer (auto-rejects claims on enrollment cancel)
│       ├── controller/        # Claims REST controller
│       ├── dto/               # Request/Response records
│       ├── entity/            # Claim entity (references by UUID, no cross-DB FK)
│       ├── repository/        # Spring Data JPA repository
│       └── service/           # Claims logic (with custom Micrometer metrics)
│
├── eureka-server/             # Netflix Eureka service registry
├── api-gateway/               # Spring Cloud Gateway (routes + Swagger UI aggregation + CORS)
├── prometheus/                # Prometheus scrape configuration
│   └── prometheus.yml
├── docker-compose.yml         # 10 containers: 4 services + Postgres + Redis + MinIO + Kafka + Prometheus + Grafana
├── Dockerfile.*               # Multi-stage builds per service
└── init-db.sql                # Creates both databases
```

---

## Key Design Decisions

| Decision | Why |
|----------|-----|
| **Database-per-service** | True data isolation; services can scale independently |
| **Kafka for enrollment events** | Loose coupling — benefits-service doesn't know about claims-service |
| **Redis caching + Kafka eviction** | Cache inter-service calls (2-min TTL) + immediate invalidation on status change |
| **Circuit breaker on REST calls** | Claims-service degrades gracefully when benefits-service is down |
| **Dead Letter Topic** | Poison messages don't block the Kafka consumer |
| **JWT with tenant context** | Stateless auth, every request carries tenant identity |
| **Flyway migrations** | Version-controlled schema, safe for production |
| **Idempotency via Redis** | Prevents duplicate claim creation on network retries |
| **Structured JSON logging** | Production-ready for ELK/CloudWatch ingestion |
| **Custom Micrometer metrics** | Business KPIs: claims created, submitted, processing time |
| **Gateway CORS** | Allows Swagger UI and frontends to call APIs without browser CORS blocks |
| **Forward headers** | Services respect gateway-forwarded headers for correct URL generation |

---

## Observability

- **Prometheus** scrapes `/actuator/prometheus` from both services every 15s
- **Grafana dashboards**: request rate, error rate, p95 latency, JVM memory, custom business metrics
- **Custom metrics**: `claims.created`, `claims.submitted`, `claims.processing.time`
- **Structured logging**: JSON in Docker, human-readable in local dev

---

## Resilience Patterns

- **Circuit Breaker**: Opens after 50% failure rate (10-call window), 30s recovery
- **Retry**: 3 attempts with exponential backoff for transient failures
- **Rate Limiter**: 10 req/sec on claim submission endpoint
- **Dead Letter Topic**: Failed Kafka messages retry 3x then go to `.DLT`

---

## Testing

```bash
./gradlew test                    # All tests
./gradlew :benefits-service:test  # 26 tests
./gradlew :claims-service:test    # 14 tests
```

| Suite | Tests | What It Covers |
|-------|-------|----------------|
| ClaimServiceTest | 14 | CRUD, submit, tenant isolation, enrollment validation |
| EnrollmentServiceTest | 11 | Create, cancel, duplicate prevention, Kafka event |
| BenefitPlanServiceTest | 8 | CRUD, cache, invalid enum, tenant isolation |
| TenantServiceTest | 5 | Create, duplicate code, uppercase normalization |

---

## Author

Built as a portfolio project demonstrating production-grade microservices architecture. Domain: employee benefits administration.

## License

MIT
