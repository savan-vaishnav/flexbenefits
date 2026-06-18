# FlexBenefits

> Multi-tenant employee benefits & insurance claims platform built with Spring Boot 4, PostgreSQL, Redis, and MinIO.

<!--
![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Tests](https://img.shields.io/badge/tests-43%20passing-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green)
-->

---

## What Is This?

FlexBenefits is a **SaaS backend** where companies manage their employees' health insurance plans and reimbursement claims. Think Workday Benefits or Pluxee — simplified.

**Key flows:**
1. Company (tenant) registers on the platform
2. HR creates benefit plans (Medical, Dental, Vision)
3. Employees enroll in plans
4. Employees submit insurance claims with receipts
5. Claims go through a lifecycle: DRAFT → SUBMITTED → APPROVED/REJECTED

**Built as a portfolio project** demonstrating production-grade backend engineering: multi-tenancy, JWT auth, file storage, idempotency, containerization, and comprehensive testing.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Framework** | Spring Boot 4.1, Spring Security, Spring Data JPA |
| **Language** | Java 21 |
| **Database** | PostgreSQL 16 + Flyway migrations |
| **Cache** | Redis 7 (idempotency keys) |
| **File Storage** | MinIO (S3-compatible object storage) |
| **Auth** | JWT (jjwt) with tenant-scoped tokens |
| **API Docs** | SpringDoc OpenAPI 3 (Swagger UI) |
| **Mapping** | MapStruct (compile-time entity ↔ DTO) |
| **Testing** | JUnit 5 + Mockito + AssertJ (43 unit tests) |
| **Containerization** | Docker multi-stage build + Docker Compose |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Client / Swagger UI                   │
└───────────────────────────────┬─────────────────────────────┘
                                │ HTTP + JWT
┌───────────────────────────────▼─────────────────────────────┐
│                    Spring Boot Application                    │
│                                                              │
│  ┌────────────┐  ┌────────────┐  ┌─────────────────────┐    │
│  │  Security  │  │Idempotency │  │    REST Controllers  │    │
│  │   Filter   │→│   Filter   │→│  (7 controllers)     │    │
│  │  (JWT)     │  │  (Redis)   │  │                     │    │
│  └────────────┘  └────────────┘  └──────────┬──────────┘    │
│                                              │               │
│  ┌───────────────────────────────────────────▼──────────┐    │
│  │              Service Layer (business logic)           │    │
│  │        Multi-tenancy enforced at every query          │    │
│  └───────────────────────────────────────────┬──────────┘    │
│                                              │               │
│  ┌────────────┐  ┌────────────┐  ┌──────────▼──────────┐    │
│  │   MinIO    │  │   Redis    │  │   PostgreSQL        │    │
│  │  (files)   │  │  (cache)   │  │   (data + Flyway)   │    │
│  └────────────┘  └────────────┘  └─────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### Multi-Tenancy

Every request carries a tenant identity via JWT. All database queries are scoped by `tenant_id`. Tenant A can never see Tenant B's data — enforced at the service layer.

---

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Option 1: Full Docker Stack (recommended)

```bash
# Clone the repo
git clone https://github.com/<your-username>/flexbenefits.git
cd flexbenefits

# Start everything (builds app + infra)
docker compose up --build -d

# Verify
curl http://localhost:8080/api/v1/ping
# → {"status":"UP","service":"flexbenefits","timestamp":"..."}

# Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

### Option 2: Local Development

```bash
# Start infrastructure only
docker compose up postgres redis minio -d

# Run the app
./gradlew bootRun

# Run tests (no Docker needed)
./gradlew test
```

---

## API Endpoints (27 total)

### Health
| Method | Endpoint | Auth |
|--------|----------|------|
| GET | `/api/v1/ping` | No |
| GET | `/actuator/health` | No |

### Authentication
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/auth/register` | No | Create account |
| POST | `/api/v1/auth/login` | No | Get JWT token |
| POST | `/api/v1/auth/refresh` | Yes | Refresh token |
| GET | `/api/v1/auth/me` | Yes | Current user info |

### Tenants
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/tenants` | Create company |
| GET | `/api/v1/tenants/{id}` | Get company details |

### Benefit Plans
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/plans` | Create plan (admin) |
| GET | `/api/v1/plans` | List active plans |
| GET | `/api/v1/plans/{id}` | Get plan details |

### Enrollments
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/enrollments` | Enroll in plan |
| GET | `/api/v1/enrollments` | List enrollments |
| GET | `/api/v1/enrollments/{id}` | Get enrollment |
| DELETE | `/api/v1/enrollments/{id}` | Cancel enrollment |

### Claims ⭐
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/claims` | Create claim (DRAFT) |
| GET | `/api/v1/claims` | List claims (paginated) |
| GET | `/api/v1/claims/{id}` | Get claim details |
| PUT | `/api/v1/claims/{id}` | Update claim (DRAFT only) |
| DELETE | `/api/v1/claims/{id}` | Delete claim (DRAFT only) |
| PATCH | `/api/v1/claims/{id}/submit` | Submit claim |

### Claim Documents
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/claims/{id}/documents` | Upload file |
| GET | `/api/v1/claims/{id}/documents` | List documents |
| GET | `/api/v1/claims/{id}/documents/{docId}` | Get metadata |
| GET | `/api/v1/claims/{id}/documents/{docId}/download` | Download file |
| DELETE | `/api/v1/claims/{id}/documents/{docId}` | Delete document |

> All endpoints except auth/health require JWT. Use Swagger UI's **Authorize** button to paste your token.

---

## Project Structure

```
src/main/java/com/flexbenefits/
├── config/          # Security, JWT filter, MinIO, Redis idempotency
├── controller/      # 7 REST controllers
├── dto/             # Request/Response records (validated)
├── entity/          # JPA entities (7 tables)
├── exception/       # Global error handling
├── mapper/          # MapStruct entity↔DTO mappers
├── repository/      # Spring Data JPA repositories
└── service/         # Business logic (tenant-scoped)

src/main/resources/
├── application.properties    # Config with env var support
└── db/migration/             # Flyway SQL migrations (V1-V4)

src/test/java/com/flexbenefits/
├── config/TestConfig.java    # Mocks for MinIO + Redis
└── service/                  # 42 unit tests (Mockito)
```

---

## Testing

```bash
# Run all 43 tests
./gradlew test

# View HTML report
open build/reports/tests/test/index.html
```

| Test Suite | Tests | Coverage |
|-----------|-------|----------|
| ClaimServiceTest | 17 | CRUD + submit + tenant isolation |
| EnrollmentServiceTest | 11 | Create + cancel + duplicate prevention |
| BenefitPlanServiceTest | 8 | CRUD + invalid enum + tenant isolation |
| TenantServiceTest | 5 | Create + duplicate code + uppercase |
| ApplicationContextTest | 1 | Spring context loads |

---

## Key Design Decisions

| Decision | Why |
|----------|-----|
| **Multi-tenant via `tenant_id` column** | Simple, scales well, no schema-per-tenant complexity |
| **JWT (not sessions)** | Stateless, microservice-ready, carries tenant context |
| **Flyway (not Hibernate ddl-auto)** | Version-controlled schema, safe for production |
| **MapStruct (not ModelMapper)** | Compile-time code gen, zero reflection, type-safe |
| **Records for DTOs** | Immutable, concise, perfect for request/response objects |
| **Idempotency via Redis** | Sub-ms lookups, atomic SETNX, auto-expiring TTL |
| **MinIO for files** | S3-compatible API, same code works with AWS S3 |
| **Multi-stage Docker** | ~200MB image (JRE only), no build tools in prod |
| **ResourceNotFoundException → 404** | Hides resource existence from other tenants |

---

## Database Schema

```
tenants (1) ←── (N) employees
tenants (1) ←── (N) benefit_plans
tenants (1) ←── (N) users

employees (1) ←── (N) enrollments ──→ (1) benefit_plans
employees (1) ←── (N) claims ──→ (1) enrollments

claims (1) ←── (N) claim_documents
```

4 Flyway migrations: `V1` (UUID extension), `V2` (domain tables), `V3` (users), `V4` (documents)

---

## Future (Phase 2)

- [ ] Microservices split (benefits-service + claims-service)
- [ ] Kafka for async claim adjudication
- [ ] Spring Cloud Gateway + Eureka
- [ ] Resilience4j circuit breakers
- [ ] Redis caching with Kafka-driven invalidation
- [ ] Prometheus + Grafana observability
- [ ] Load testing with k6

---

## Author

Built as a portfolio project demonstrating enterprise Spring Boot patterns. Domain: employee benefits administration (7 years industry experience).

---

## License

MIT

