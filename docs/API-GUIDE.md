# FlexBenefits — Technical API Guide

> **What is this?** A complete walkthrough of every module in FlexBenefits — what each layer does, how requests flow, and how to test every endpoint.
>
> **Who is this for?** You (coming back after a break), a teammate joining the project, or an interviewer who wants to see you understand your own code.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [How a Request Flows (End-to-End)](#2-how-a-request-flows-end-to-end)
3. [Module Guide: Authentication](#3-module-guide-authentication)
4. [Module Guide: Claims](#4-module-guide-claims)
5. [Module Guide: Claim Documents](#5-module-guide-claim-documents)
6. [Module Guide: Benefit Plans](#6-module-guide-benefit-plans)
7. [Module Guide: Enrollments](#7-module-guide-enrollments)
8. [Module Guide: Tenants](#8-module-guide-tenants)
9. [Cross-Cutting Concerns](#9-cross-cutting-concerns)
10. [Testing with curl](#10-testing-with-curl)
11. [Unit Tests](#11-unit-tests)
12. [Docker & Deployment](#12-docker--deployment)

---

## 1. Architecture Overview

### Layered Architecture

Every feature follows the same 5-layer stack:

```
┌─────────────────────────────────────────────┐
│  Controller (REST layer)                     │ ← Receives HTTP, validates input, returns JSON
│  @RestController + @RequestMapping           │
├─────────────────────────────────────────────┤
│  DTO (Data Transfer Objects)                 │ ← Request/Response shapes (Java records)
│  CreateXxxRequest, XxxResponse               │
├─────────────────────────────────────────────┤
│  Service (Business Logic)                    │ ← Validates rules, coordinates work
│  @Service + @Transactional                   │
├─────────────────────────────────────────────┤
│  Mapper (Entity ↔ DTO conversion)            │ ← MapStruct auto-generated code
│  @Mapper(componentModel = "spring")          │
├─────────────────────────────────────────────┤
│  Repository (Data Access)                    │ ← Spring Data JPA, auto-generated SQL
│  extends JpaRepository<Entity, UUID>         │
├─────────────────────────────────────────────┤
│  Entity (Database mapping)                   │ ← JPA @Entity, maps to DB table
│  @Entity + @Table                            │
└─────────────────────────────────────────────┘
```

### File Naming Convention

For each module (e.g., Claims), the files are:

| Layer | File | Package |
|-------|------|---------|
| Entity | `Claim.java` | `entity/` |
| Enum | `ClaimStatus.java` | `entity/enums/` |
| Repository | `ClaimRepository.java` | `repository/` |
| DTO (input) | `CreateClaimRequest.java` | `dto/` |
| DTO (output) | `ClaimResponse.java` | `dto/` |
| Mapper | `ClaimMapper.java` | `mapper/` |
| Service | `ClaimService.java` | `service/` |
| Controller | `ClaimController.java` | `controller/` |

### Multi-Tenancy — How It Works Everywhere

Every request carries a tenant identity through JWT:

```
Client sends: Authorization: Bearer <jwt>
                    ↓
JwtAuthenticationFilter:
  1. Parses JWT → extracts tenantId claim
  2. Calls TenantContext.setTenantId(tenantId)    ← ThreadLocal
                    ↓
Controller:
  UUID tenantId = TenantContext.getTenantId();
  service.doSomething(tenantId, ...);
                    ↓
Service:
  repository.findByTenantId(tenantId, ...);       ← every query is scoped
                    ↓
Finally block:
  TenantContext.clear();                           ← prevents memory leaks
```

**Rule:** Every service method takes `tenantId` as its first parameter. Every repository query filters by it. No exceptions.

---

## 2. How a Request Flows (End-to-End)

Let's trace `POST /api/v1/claims` from HTTP to database and back:

```
1. HTTP Request arrives
   POST /api/v1/claims
   Headers: Authorization: Bearer eyJ..., Content-Type: application/json
   Body: {"employeeId":"...", "enrollmentId":"...", "serviceDate":"2026-06-10", ...}

2. Security Filter Chain (in order):
   a. JwtAuthenticationFilter
      → Extracts JWT from "Bearer ..." header
      → Calls jwtService.extractEmail(jwt) → "john@acme.com"
      → Loads user from DB: userDetailsService.loadUserByUsername("john@acme.com")
      → Validates signature + expiry
      → Sets TenantContext.setTenantId(tenantId)  ← ThreadLocal
      → Sets SecurityContextHolder.getContext().setAuthentication(...)

   b. IdempotencyFilter
      → Checks for "Idempotency-Key" header
      → If present: redis.setIfAbsent("idempotency:<key>", "processing", 24h)
      → If key already exists → 409 Conflict (stops here)
      → If no header → passes through

3. Spring DispatcherServlet
   → Matches POST /api/v1/claims → ClaimController.createClaim()

4. Controller Layer
   → @Valid validates CreateClaimRequest (checks @NotNull, @NotBlank, @Positive)
   → If validation fails → MethodArgumentNotValidException → GlobalExceptionHandler → 400
   → Gets tenantId from TenantContext.getTenantId()
   → Calls claimService.createClaim(tenantId, request)

5. Service Layer
   → Loads Tenant, Employee, Enrollment from DB (throws 404 if not found)
   → Verifies employee + enrollment belong to this tenant (throws 409 if not)
   → Creates Claim entity, generates claim number (CLM-2026-000001)
   → Calls claimRepository.save(claim) → Hibernate INSERT
   → Maps entity to DTO via claimMapper.toResponse(claim)
   → Returns ClaimResponse

6. Back to Controller
   → Wraps in ResponseEntity.status(201).body(response)
   → Spring serializes to JSON via Jackson

7. Cleanup
   → JwtAuthenticationFilter.finally → TenantContext.clear()

8. HTTP Response
   201 Created
   {"id":"...","tenantId":"...","claimNumber":"CLM-2026-000001","status":"DRAFT",...}
```

---

## 3. Module Guide: Authentication

### What It Does
Handles user registration, login, token refresh, and "who am I" queries. Issues JWT tokens that all other endpoints require.

### Files Involved

```
entity/User.java                    ← implements UserDetails (Spring Security)
entity/enums/Role.java              ← EMPLOYEE, HR_ADMIN, SUPER_ADMIN
repository/UserRepository.java
dto/RegisterRequest.java
dto/LoginRequest.java
dto/AuthResponse.java
service/JwtService.java              ← generates/validates JWT tokens
service/CustomUserDetailsService.java ← loads user for Spring Security
controller/AuthController.java
config/JwtAuthenticationFilter.java   ← extracts JWT on every request
config/SecurityConfig.java            ← which endpoints need auth
config/TenantContext.java             ← ThreadLocal for tenant ID
```

### Database Table

```sql
-- V3__create_users_table.sql
users (id, tenant_id, email, password, first_name, last_name, role, active, created_at, updated_at)
  - UNIQUE(tenant_id, email)    -- same email OK across tenants
  - password stored as BCrypt hash
```

### Endpoints

#### POST `/api/v1/auth/register` — Create Account
```
Auth Required: No
Who calls it: New users (or admin creating accounts)

Request:
{
  "tenantId": "uuid-of-tenant",        ← which company
  "email": "john@acme.com",
  "password": "secret123",             ← min 6 chars
  "firstName": "John",
  "lastName": "Doe"
}

What happens:
  1. Validates tenant exists → 404 if not
  2. Checks email not taken for this tenant → 409 if duplicate
  3. Hashes password with BCrypt (12 rounds)
  4. Saves user with role=EMPLOYEE, active=true
  5. Generates JWT token
  6. Returns token + user info

Response (201):
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "uuid",
  "tenantId": "uuid",
  "email": "john@acme.com",
  "role": "EMPLOYEE"
}

Errors:
  404 → Tenant not found
  409 → Email already registered for this tenant
  400 → Validation error (missing fields, password too short)
```

#### POST `/api/v1/auth/login` — Get JWT Token
```
Auth Required: No

Request:
{
  "email": "john@acme.com",
  "password": "secret123"
}

What happens:
  1. AuthenticationManager delegates to DaoAuthenticationProvider
  2. Provider calls CustomUserDetailsService.loadUserByUsername(email)
  3. Provider compares BCrypt hash of provided password vs stored hash
  4. If match → load user → generate JWT → return
  5. If no match → BadCredentialsException → 401

Response (200): same shape as register

Errors:
  401 → Invalid email or password
```

#### POST `/api/v1/auth/refresh` — Get New Token
```
Auth Required: Yes (valid JWT in Authorization header)

Request body: none

What happens:
  1. JwtAuthenticationFilter validates existing (not-yet-expired) JWT
  2. Reads authenticated User from SecurityContext
  3. Generates fresh JWT with new expiration (24h from now)
  4. Returns new token

Response (200): same shape as login

When to use: Call this before your current token expires (e.g., 5 min before)
```

#### GET `/api/v1/auth/me` — Who Am I?
```
Auth Required: Yes

What happens:
  1. Reads User from SecurityContextHolder
  2. Returns user info (no token in response — you already have one)

Response (200):
{
  "token": null,
  "userId": "uuid",
  "tenantId": "uuid",
  "email": "john@acme.com",
  "role": "EMPLOYEE"
}
```

### JWT Token Structure

```
Header:  {"alg":"HS256"}
Payload: {
  "sub": "john@acme.com",          ← subject (used for lookup)
  "tenantId": "uuid-of-tenant",    ← custom claim
  "userId": "uuid-of-user",        ← custom claim
  "role": "EMPLOYEE",              ← custom claim
  "iat": 1718600000,               ← issued at (epoch seconds)
  "exp": 1718686400                 ← expires (24h later)
}
Signature: HMAC-SHA256(header + payload, secret)
```

Config in `application.properties`:
```properties
jwt.secret=<base64-encoded-256-bit-key>
jwt.expiration=86400000              # 24 hours in ms
```

---

## 4. Module Guide: Claims

### What It Does
The core feature. Employees create insurance claims for reimbursement. Claims go through a lifecycle: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED.

### Files Involved

```
entity/Claim.java
entity/enums/ClaimStatus.java        ← DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, APPEALED
repository/ClaimRepository.java
dto/CreateClaimRequest.java
dto/UpdateClaimRequest.java
dto/ClaimResponse.java
mapper/ClaimMapper.java
service/ClaimService.java
controller/ClaimController.java
```

### Database Table

```sql
-- Part of V2__create_domain_tables.sql
claims (
  id, tenant_id, employee_id, enrollment_id,
  claim_number (UNIQUE),              ← human-readable: "CLM-2026-000001"
  status,                             ← enum as STRING
  service_date, provider_name, diagnosis_code,
  claimed_amount (DECIMAL 12,2),
  approved_amount, rejection_reason,
  submitted_at, adjudicated_at,
  created_at, updated_at
)

Indexes: tenant_id, employee_id, status, claim_number
```

### Entity Relationships

```
Claim → Tenant    (@ManyToOne)  ← which company
Claim → Employee  (@ManyToOne)  ← who filed it
Claim → Enrollment (@ManyToOne) ← under which plan
```

### Endpoints

#### POST `/api/v1/claims` — Create a Claim
```
Auth Required: Yes

Request:
{
  "employeeId": "uuid",
  "enrollmentId": "uuid",
  "serviceDate": "2026-06-10",
  "providerName": "Dr. Smith",
  "diagnosisCode": "J06.9",          ← optional
  "claimedAmount": 5000.00
}

Business Rules:
  - Employee must belong to the authenticated tenant
  - Enrollment must belong to the authenticated tenant
  - Status is always set to DRAFT
  - Claim number auto-generated: "CLM-{year}-{sequence}"

Response (201):
{
  "id": "uuid",
  "tenantId": "uuid",
  "employeeId": "uuid",
  "claimNumber": "CLM-2026-000001",
  "status": "DRAFT",
  "serviceDate": "2026-06-10",
  "providerName": "Dr. Smith",
  "diagnosisCode": "J06.9",
  "claimedAmount": 5000.00,
  "approvedAmount": null,
  "rejectionReason": null,
  "submittedAt": null,
  "createdAt": "2026-06-10T14:30:00"
}

Errors:
  404 → Tenant/Employee/Enrollment not found
  409 → Employee or Enrollment doesn't belong to tenant
  400 → Validation error
```

#### GET `/api/v1/claims` — List Claims (Paginated)
```
Auth Required: Yes
Query params: ?page=0&size=20&sort=createdAt,desc (Spring Pageable)

What happens:
  - Fetches all claims for the authenticated tenant
  - Returns Page<ClaimResponse> with pagination metadata

Response (200):
{
  "content": [ {...}, {...} ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,                        ← current page
  "size": 20
}
```

#### GET `/api/v1/claims/{id}` — Get One Claim
```
Auth Required: Yes

Business Rules:
  - Claim must belong to authenticated tenant (or 404)

Response (200): ClaimResponse

Errors:
  404 → Claim not found (or belongs to different tenant)
```

#### PUT `/api/v1/claims/{id}` — Update a Claim
```
Auth Required: Yes

Request:
{
  "serviceDate": "2026-06-12",        ← all fields optional
  "providerName": "Dr. Jones",
  "diagnosisCode": "J06.9",
  "claimedAmount": 6000.00
}

Business Rules:
  - Can ONLY update claims in DRAFT status
  - Only non-null fields are updated (partial update)

Errors:
  409 → "Can only update claims in DRAFT status"
  404 → Claim not found
```

#### DELETE `/api/v1/claims/{id}` — Delete a Claim
```
Auth Required: Yes

Business Rules:
  - Can ONLY delete claims in DRAFT status
  - Currently hard-deletes (TODO: soft delete in future)

Response: 204 No Content

Errors:
  409 → "Can only delete claims in DRAFT status"
```

#### PATCH `/api/v1/claims/{id}/submit` — Submit a Claim
```
Auth Required: Yes
Request body: none

What happens:
  - Transitions status: DRAFT → SUBMITTED
  - Sets submittedAt = now()
  - (Phase 2: will publish Kafka event "claim.submitted")

Business Rules:
  - Claim must be in DRAFT status

Response (200): ClaimResponse with status="SUBMITTED"

Errors:
  409 → "Can only submit claims in DRAFT status"
```

### Claim Lifecycle (State Machine)

```
  ┌───────┐  submit   ┌───────────┐  (Phase 2)  ┌──────────────┐
  │ DRAFT │ ────────→ │ SUBMITTED │ ──────────→ │ UNDER_REVIEW │
  └───────┘           └───────────┘              └──────┬───────┘
      │                                                  │
      │ delete                              approve ─────┤───── reject
      ▼                                        │         │        │
   (removed)                              ┌────▼───┐  ┌──▼─────┐  │
                                          │APPROVED│  │REJECTED│←─┘
                                          └────────┘  └───┬────┘
                                                          │ appeal
                                                     ┌────▼────┐
                                                     │APPEALED │
                                                     └─────────┘
```

Currently implemented: DRAFT → SUBMITTED (via PATCH submit). Other transitions will be added in Phase 2 (adjudication worker).

---

## 5. Module Guide: Claim Documents

### What It Does
Employees upload receipts, bills, and supporting documents to their claims. Files are stored in MinIO (S3-compatible object storage), metadata is stored in PostgreSQL.

### Files Involved

```
entity/ClaimDocument.java
repository/ClaimDocumentRepository.java
dto/DocumentResponse.java
service/DocumentService.java            ← handles MinIO upload/download
controller/DocumentController.java
config/MinioConfig.java                 ← MinIO client bean + bucket creation
```

### Database Table

```sql
-- V4__create_claim_documents_table.sql
claim_documents (
  id, tenant_id, claim_id,
  file_name,                           ← original filename: "receipt.pdf"
  content_type,                        ← MIME type: "application/pdf"
  file_size,                           ← bytes (BIGINT)
  storage_key,                         ← MinIO path: "tenantId/claimId/uuid-receipt.pdf"
  uploaded_by,                         ← FK to users table
  created_at
)
```

### Storage Architecture

```
MinIO Bucket: "claim-documents"
  └── {tenantId}/
       └── {claimId}/
            ├── a1b2c3d4-receipt.pdf
            └── e5f6g7h8-xray-report.jpg
```

- Files organized by tenant → claim → UUID-prefixed filename
- UUID prefix prevents filename collisions
- Tenant-level folder enables easy data isolation

### Endpoints

#### POST `/api/v1/claims/{claimId}/documents` — Upload File
```
Auth Required: Yes
Content-Type: multipart/form-data

Request: form field "file" = the file binary

Business Rules:
  - File must not be empty
  - File must be ≤ 10 MB
  - Claim must belong to authenticated tenant

What happens:
  1. Validates file size + claim ownership
  2. Generates storage key: {tenantId}/{claimId}/{uuid}-{filename}
  3. Uploads file bytes to MinIO via putObject()
  4. Saves metadata row to claim_documents table
  5. Returns document metadata (NOT the file)

Response (201):
{
  "id": "uuid",
  "claimId": "uuid",
  "fileName": "receipt.pdf",
  "contentType": "application/pdf",
  "fileSize": 234567,
  "createdAt": "2026-06-10T14:35:00"
}

Errors:
  409 → "File is empty" or "File size exceeds maximum of 10 MB"
  404 → Claim not found
```

#### GET `/api/v1/claims/{claimId}/documents` — List Documents
```
Auth Required: Yes

Response (200): List<DocumentResponse>
```

#### GET `/api/v1/claims/{claimId}/documents/{docId}` — Get Document Metadata
```
Auth Required: Yes
Response (200): DocumentResponse
```

#### GET `/api/v1/claims/{claimId}/documents/{docId}/download` — Download File
```
Auth Required: Yes

Response (200):
  Content-Type: application/pdf (or whatever the file type is)
  Content-Disposition: attachment; filename="receipt.pdf"
  Body: raw file bytes (streamed from MinIO)
```

#### DELETE `/api/v1/claims/{claimId}/documents/{docId}` — Delete Document
```
Auth Required: Yes

What happens:
  1. Deletes file from MinIO (removeObject)
  2. Deletes metadata row from DB
  3. If MinIO delete fails → logs error but still deletes from DB

Response: 204 No Content
```

---

## 6. Module Guide: Benefit Plans

### What It Does
HR Admins create insurance plans (Medical, Dental, Vision, Life) that employees can enroll in. Plans define premiums, deductibles, and maximum coverage amounts.

### Files Involved

```
entity/BenefitPlan.java
entity/enums/PlanType.java              ← MEDICAL, DENTAL, VISION, LIFE
entity/enums/CoverageTier.java          ← EMPLOYEE_ONLY, EMPLOYEE_SPOUSE, EMPLOYEE_FAMILY
repository/BenefitPlanRepository.java
dto/CreateBenefitPlanRequest.java
dto/BenefitPlanResponse.java
mapper/BenefitPlanMapper.java
service/BenefitPlanService.java
controller/BenefitPlanController.java
```

### Database Table

```sql
-- Part of V2__create_domain_tables.sql
benefit_plans (
  id, tenant_id,
  name,                                ← "Gold Medical"
  type,                                ← MEDICAL, DENTAL, VISION, LIFE
  description,                         ← TEXT: plan details
  coverage_tier,                       ← EMPLOYEE_ONLY, EMPLOYEE_SPOUSE, EMPLOYEE_FAMILY
  monthly_premium DECIMAL(10,2),       ← ₹500.00/month
  deductible DECIMAL(10,2),            ← ₹1000.00 per year
  max_coverage DECIMAL(12,2),          ← ₹50,000.00 lifetime max
  plan_year INTEGER,                   ← 2026
  active BOOLEAN DEFAULT TRUE,
  created_at, updated_at
)
```

### Endpoints

#### POST `/api/v1/plans` — Create Plan (Admin)
```
Auth Required: Yes

Request:
{
  "name": "Gold Medical",
  "type": "MEDICAL",                   ← must match PlanType enum
  "description": "Comprehensive medical coverage with low deductible",
  "coverageTier": "EMPLOYEE_ONLY",     ← must match CoverageTier enum
  "monthlyPremium": 500.00,
  "deductible": 1000.00,
  "maxCoverage": 50000.00,
  "planYear": 2026                     ← optional
}

Business Rules:
  - Tenant must exist
  - Type and coverageTier must be valid enum values
  - Plan is set to active=true on creation

Response (201):
{
  "id": "uuid",
  "tenantId": "uuid",
  "name": "Gold Medical",
  "type": "MEDICAL",
  "description": "...",
  "coverageTier": "EMPLOYEE_ONLY",
  "monthlyPremium": 500.00,
  "deductible": 1000.00,
  "maxCoverage": 50000.00,
  "planYear": 2026,
  "active": true,
  "createdAt": "2026-06-10T10:00:00"
}

Errors:
  404 → Tenant not found
  400 → Validation error or invalid enum value
```

#### GET `/api/v1/plans` — List Active Plans
```
Auth Required: Yes

What happens:
  - Fetches plans where tenant_id = current tenant AND active = true
  - Uses BenefitPlanRepository.findByTenantIdAndActiveTrue()

Response (200): List<BenefitPlanResponse>
```

#### GET `/api/v1/plans/{id}` — Get Plan Details
```
Auth Required: Yes

Business Rules:
  - Plan must belong to authenticated tenant

Response (200): BenefitPlanResponse

Errors:
  404 → Plan not found (or belongs to different tenant)
```

### How Plans Connect to Other Modules

```
BenefitPlan ← enrolled via → Enrollment → used for → Claim
  "Gold Medical"               "John enrolled"       "John claims ₹5000"
```

An employee must be enrolled in a plan before they can file a claim under it.

---

## 7. Module Guide: Enrollments

### What It Does
Connects employees to benefit plans. An enrollment says "John Doe chose Gold Medical starting Jan 1, 2026." Enrollments can be pending, active, or cancelled.

### Files Involved

```
entity/Enrollment.java
entity/enums/EnrollmentStatus.java      ← PENDING, ACTIVE, CANCELLED
repository/EnrollmentRepository.java
dto/CreateEnrollmentRequest.java
dto/EnrollmentResponse.java
mapper/EnrollmentMapper.java
service/EnrollmentService.java
controller/EnrollmentController.java
```

### Database Table

```sql
-- Part of V2__create_domain_tables.sql
enrollments (
  id, tenant_id, employee_id, benefit_plan_id,
  status DEFAULT 'PENDING',            ← PENDING → ACTIVE → CANCELLED
  enrollment_date DATE NOT NULL,        ← when they enrolled
  effective_date DATE,                  ← when coverage starts
  termination_date DATE,               ← when coverage ended (set on cancel)
  created_at, updated_at
)
```

### Entity Relationships

```
Enrollment → Tenant      (@ManyToOne)  ← which company
Enrollment → Employee    (@ManyToOne)  ← who enrolled
Enrollment → BenefitPlan (@ManyToOne)  ← in which plan
```

### Endpoints

#### POST `/api/v1/enrollments` — Enroll in a Plan
```
Auth Required: Yes

Request:
{
  "employeeId": "uuid",
  "benefitPlanId": "uuid",
  "enrollmentDate": "2026-01-01",
  "effectiveDate": "2026-01-15"        ← optional
}

Business Rules:
  - Employee must belong to authenticated tenant
  - Plan must belong to authenticated tenant
  - Employee cannot be enrolled in the same plan twice
    (unless previous enrollment is CANCELLED)
  - Status always starts as PENDING

Duplicate check:
  enrollmentRepository.existsByEmployeeIdAndBenefitPlanIdAndStatusNot(
      employeeId, planId, EnrollmentStatus.CANCELLED)
  → if true: "Employee is already enrolled in this plan" (409)

Response (201):
{
  "id": "uuid",
  "tenantId": "uuid",
  "employeeId": "uuid",
  "benefitPlanId": "uuid",
  "status": "PENDING",
  "enrollmentDate": "2026-01-01",
  "effectiveDate": "2026-01-15",
  "terminationDate": null,
  "createdAt": "2026-06-10T10:00:00"
}

Errors:
  404 → Employee or BenefitPlan not found
  409 → Employee doesn't belong to tenant / already enrolled / plan not in tenant
```

#### GET `/api/v1/enrollments` — List All Enrollments
```
Auth Required: Yes

What happens:
  - Fetches all enrollments for the authenticated tenant
  - Uses EnrollmentRepository.findByTenantId()

Response (200): List<EnrollmentResponse>
```

#### GET `/api/v1/enrollments/{id}` — Get Enrollment Details
```
Auth Required: Yes

Business Rules:
  - Enrollment must belong to authenticated tenant

Response (200): EnrollmentResponse

Errors:
  404 → Enrollment not found (or different tenant)
```

#### DELETE `/api/v1/enrollments/{id}` — Cancel Enrollment
```
Auth Required: Yes
Request body: none

What happens:
  1. Loads enrollment, verifies tenant ownership
  2. Checks not already cancelled
  3. Sets status = CANCELLED
  4. Sets terminationDate = today
  5. Saves

Business Rules:
  - Cannot cancel an already cancelled enrollment (409)

Response: 204 No Content

Errors:
  409 → "Enrollment is already cancelled"
  404 → Not found
```

### Enrollment Lifecycle

```
  ┌─────────┐  (future: HR approves)  ┌────────┐
  │ PENDING │ ───────────────────────→ │ ACTIVE │
  └────┬────┘                          └───┬────┘
       │ cancel                            │ cancel
       ▼                                   ▼
  ┌───────────┐                       ┌───────────┐
  │ CANCELLED │                       │ CANCELLED │
  └───────────┘                       └───────────┘
       ↓                                   ↓
  terminationDate = today             terminationDate = today
```

Currently: enrollment starts as PENDING. PENDING → ACTIVE transition will be added when HR approval workflow is built. Cancel works from any non-cancelled status.

---

## 8. Module Guide: Tenants

### What It Does
Manages company/organization records. Each tenant is an isolated company on the platform. Tenants are the root entity — everything else belongs to a tenant.

### Files Involved

```
entity/Tenant.java
repository/TenantRepository.java
dto/CreateTenantRequest.java
dto/TenantResponse.java
mapper/TenantMapper.java
service/TenantService.java
controller/TenantController.java
```

### Database Table

```sql
-- Part of V2__create_domain_tables.sql
tenants (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,          ← "Acme Corporation"
  code VARCHAR(50) NOT NULL UNIQUE,    ← "ACME" (short identifier)
  contact_email VARCHAR(255),
  active BOOLEAN DEFAULT TRUE,
  created_at, updated_at
)
```

### Endpoints

#### POST `/api/v1/tenants` — Create Company
```
Auth Required: Yes (future: SUPER_ADMIN only)

Request:
{
  "name": "Acme Corporation",
  "code": "ACME",                      ← auto-uppercased
  "contactEmail": "admin@acme.com"     ← optional
}

Business Rules:
  - Code must be unique (409 if duplicate)
  - Code is always stored uppercase

Response (201):
{
  "id": "uuid",
  "name": "Acme Corporation",
  "code": "ACME",
  "contactEmail": "admin@acme.com",
  "active": true,
  "createdAt": "2026-06-10T10:00:00"
}

Errors:
  409 → "Tenant with code already exists: ACME"
  400 → Validation error (missing name or code)
```

#### GET `/api/v1/tenants/{id}` — Get Company Details
```
Auth Required: Yes

Response (200): TenantResponse

Errors:
  404 → Tenant not found
```

### How Tenants Relate to Everything Else

```
Tenant (root)
  ├── Employees (belong to this company)
  ├── Users (login accounts for this company)
  ├── BenefitPlans (insurance plans this company offers)
  ├── Enrollments (employees signed up for plans)
  ├── Claims (reimbursement requests)
  └── ClaimDocuments (files attached to claims)
```

Every table has `tenant_id` as a foreign key back to `tenants.id`.

---

## 9. Cross-Cutting Concerns

### Exception Handling (`GlobalExceptionHandler`)

All exceptions thrown from any controller are caught centrally:

| Exception | HTTP Status | When |
|-----------|------------|------|
| `ResourceNotFoundException` | 404 | findById returns empty |
| `BadCredentialsException` | 401 | Wrong password on login |
| `IllegalStateException` | 409 | Business rule violation (wrong status, duplicate, etc.) |
| `MethodArgumentNotValidException` | 400 | @Valid fails (@NotNull, @NotBlank, etc.) |
| `Exception` (catch-all) | 500 | Unexpected errors |

Every error response has the same shape:
```json
{ "status": 404, "message": "Claim not found with id: uuid", "timestamp": "2026-06-10T14:30:00Z" }
```

### Idempotency (`IdempotencyFilter`)

Prevents duplicate mutations from network retries:

```
Client → POST /api/v1/claims + Idempotency-Key: abc-123
  ↓
IdempotencyFilter:
  redis.setIfAbsent("idempotency:abc-123", "processing", 24h)
  ↓
  First time? → wasSet=true → proceed → create claim
  Second time? → wasSet=false → 409 Conflict (no duplicate created)
```

- **Opt-in:** No header = no idempotency check
- **Only on POST/PUT/PATCH:** GET and DELETE are already idempotent
- **24h TTL:** Keys auto-expire from Redis

### Security (`SecurityConfig`)

```
Public (no JWT):              Protected (JWT required):
  /api/v1/auth/**               /api/v1/claims/**
  /api/v1/ping                  /api/v1/plans/**
  /actuator/**                  /api/v1/enrollments/**
  /swagger-ui/**                /api/v1/tenants/**
  /v3/api-docs/**               everything else
```

### Multi-Tenancy Pattern

```java
// Controller gets tenant from JWT (via ThreadLocal)
UUID tenantId = TenantContext.getTenantId();

// Service validates ownership
Claim claim = claimRepository.findById(claimId)
    .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
if (!claim.getTenant().getId().equals(tenantId)) {
    throw new ResourceNotFoundException("Claim", claimId);  // hide existence
}
```

**Security note:** When a resource belongs to a different tenant, we throw 404 (not 403). This prevents information leakage — the attacker can't tell if the resource exists.

---

## 10. Testing with curl

### Prerequisites

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start the app
.\gradlew bootRun

# 3. Verify
curl http://localhost:8080/api/v1/ping
# → {"status":"UP","service":"flexbenefits","timestamp":"..."}
```

### Step-by-Step Flow (Full Happy Path)

```bash
# ============================================
# STEP 1: Create a tenant
# ============================================
curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme Corporation","code":"ACME","contactEmail":"admin@acme.com"}'
# → Note the tenant "id" from response

# ============================================
# STEP 2: Register a user for that tenant
# ============================================
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"<TENANT_ID>","email":"john@acme.com","password":"secret123","firstName":"John","lastName":"Doe"}'
# → Note the "token" from response

# ============================================
# STEP 3: Use the token for all subsequent requests
# ============================================
TOKEN="<paste token here>"

# ============================================
# STEP 4: Create a benefit plan
# ============================================
curl -s -X POST http://localhost:8080/api/v1/plans \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Gold Medical","type":"MEDICAL","coverageTier":"EMPLOYEE_ONLY","monthlyPremium":500,"deductible":1000,"maxCoverage":50000,"planYear":2026}'
# → Note the plan "id"

# ============================================
# STEP 5: Insert an employee (via SQL — no employee endpoint yet)
# ============================================
# docker exec -it flexbenefits-postgres-1 psql -U flexuser -d flexbenefits
# INSERT INTO employees (id, tenant_id, first_name, last_name, email, employee_code)
# VALUES (gen_random_uuid(), '<TENANT_ID>', 'John', 'Doe', 'john@acme.com', 'EMP001');
# → Note the employee id

# ============================================
# STEP 6: Create an enrollment
# ============================================
curl -s -X POST http://localhost:8080/api/v1/enrollments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"<EMPLOYEE_ID>","benefitPlanId":"<PLAN_ID>","enrollmentDate":"2026-01-01","effectiveDate":"2026-01-15"}'
# → Note the enrollment "id"

# ============================================
# STEP 7: Create a claim
# ============================================
curl -s -X POST http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"<EMPLOYEE_ID>","enrollmentId":"<ENROLLMENT_ID>","serviceDate":"2026-06-10","providerName":"Dr. Smith","diagnosisCode":"J06.9","claimedAmount":5000.00}'
# → Note the claim "id" and "claimNumber"

# ============================================
# STEP 8: Upload a document to the claim
# ============================================
curl -s -X POST http://localhost:8080/api/v1/claims/<CLAIM_ID>/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@receipt.pdf"

# ============================================
# STEP 9: Submit the claim
# ============================================
curl -s -X PATCH http://localhost:8080/api/v1/claims/<CLAIM_ID>/submit \
  -H "Authorization: Bearer $TOKEN"
# → status changes from DRAFT to SUBMITTED

# ============================================
# STEP 10: List everything
# ============================================
curl -s http://localhost:8080/api/v1/plans -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/enrollments -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/claims -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/v1/claims/<CLAIM_ID>/documents -H "Authorization: Bearer $TOKEN"
```

### Swagger UI

Open `http://localhost:8080/swagger-ui.html` in your browser to see all endpoints with an interactive test UI. No curl needed — just fill in the fields and click "Try it out."

---

## Quick Reference: All 27 Endpoints

| # | Method | Path | Auth | Module |
|---|--------|------|------|--------|
| 1 | GET | `/api/v1/ping` | No | Health |
| 2 | GET | `/actuator/health` | No | Health |
| 3 | GET | `/actuator/info` | No | Health |
| 4 | POST | `/api/v1/auth/register` | No | Auth |
| 5 | POST | `/api/v1/auth/login` | No | Auth |
| 6 | POST | `/api/v1/auth/refresh` | Yes | Auth |
| 7 | GET | `/api/v1/auth/me` | Yes | Auth |
| 8 | POST | `/api/v1/tenants` | Yes | Tenant |
| 9 | GET | `/api/v1/tenants/{id}` | Yes | Tenant |
| 10 | POST | `/api/v1/plans` | Yes | Plan |
| 11 | GET | `/api/v1/plans` | Yes | Plan |
| 12 | GET | `/api/v1/plans/{id}` | Yes | Plan |
| 13 | POST | `/api/v1/enrollments` | Yes | Enrollment |
| 14 | GET | `/api/v1/enrollments` | Yes | Enrollment |
| 15 | GET | `/api/v1/enrollments/{id}` | Yes | Enrollment |
| 16 | DELETE | `/api/v1/enrollments/{id}` | Yes | Enrollment |
| 17 | POST | `/api/v1/claims` | Yes | Claim |
| 18 | GET | `/api/v1/claims` | Yes | Claim |
| 19 | GET | `/api/v1/claims/{id}` | Yes | Claim |
| 20 | PUT | `/api/v1/claims/{id}` | Yes | Claim |
| 21 | DELETE | `/api/v1/claims/{id}` | Yes | Claim |
| 22 | PATCH | `/api/v1/claims/{id}/submit` | Yes | Claim |
| 23 | POST | `/api/v1/claims/{id}/documents` | Yes | Document |
| 24 | GET | `/api/v1/claims/{id}/documents` | Yes | Document |
| 25 | GET | `/api/v1/claims/{id}/documents/{docId}` | Yes | Document |
| 26 | GET | `/api/v1/claims/{id}/documents/{docId}/download` | Yes | Document |
| 27 | DELETE | `/api/v1/claims/{id}/documents/{docId}` | Yes | Document |

---

## 11. Unit Tests

### Test Strategy

All services are tested with **pure unit tests** — no Spring context, no database, no Docker needed. Tests use:
- **JUnit 5** — test framework
- **Mockito** — mocks all dependencies (repositories, mappers)
- **AssertJ** — fluent assertions (`assertThat(result).isNotNull()`)

### Test Structure

Each test class mirrors a service and uses `@Nested` classes for grouping:

```
ClaimServiceTest
├── createClaim (6 tests)
│   ├── should create claim successfully with valid data
│   ├── should throw when tenant not found
│   ├── should throw when employee not found
│   ├── should throw when enrollment not found
│   ├── should throw when employee belongs to different tenant
│   └── should throw when enrollment belongs to different tenant
├── getClaims (1 test)
├── getClaimById (3 tests)
├── updateClaim (3 tests)
│   ├── should update DRAFT claim
│   ├── should only update non-null fields (partial update)
│   └── should throw when updating non-DRAFT claim
├── deleteClaim (2 tests)
└── submitClaim (2 tests)
```

### Test Coverage Summary

| Test Class | Tests | What's Verified |
|-----------|-------|-----------------|
| `ClaimServiceTest` | 17 | CRUD + submit + tenant isolation + status guards |
| `BenefitPlanServiceTest` | 8 | Create + list + get + tenant isolation + invalid enum |
| `EnrollmentServiceTest` | 11 | Create + list + get + cancel + duplicate prevention |
| `TenantServiceTest` | 5 | Create + get + duplicate code + uppercase |
| `FlexbenefitsApplicationTests` | 1 | Spring context loads |
| **Total** | **43** | **All passing ✅** |

### Running Tests

```bash
# Run all tests
.\gradlew test

# Run a specific test class
.\gradlew test --tests "com.flexbenefits.service.ClaimServiceTest"

# Run with verbose output
.\gradlew test --info

# View HTML report
# build/reports/tests/test/index.html
```

### Test Configuration

Tests use H2 in-memory database (no Docker needed):
```properties
# src/test/resources/application.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
```

External services (MinIO, Redis) are mocked via `TestConfig.java`:
```java
@TestConfiguration
public class TestConfig {
    @Bean @Primary
    public MinioClient testMinioClient() { return Mockito.mock(MinioClient.class); }
    @Bean @Primary
    public StringRedisTemplate testStringRedisTemplate() { return Mockito.mock(StringRedisTemplate.class); }
}
```

---

## 12. Docker & Deployment

### Architecture in Docker

```
┌─────────────────────────────────────────────────────────┐
│                  Docker Compose Network                   │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌───────┐  ┌──────────┐   │
│  │   app    │  │ postgres │  │ redis │  │  minio   │   │
│  │ :8080   │→│  :5432   │  │ :6379 │  │ :9000/01 │   │
│  │ (Spring) │  │ (DB)     │  │(cache)│  │ (files)  │   │
│  └──────────┘  └──────────┘  └───────┘  └──────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Multi-Stage Dockerfile

```
Stage 1 (builder):  eclipse-temurin:21-jdk-alpine  (~800MB)
  → Copies Gradle files (dependency cache layer)
  → Copies source code
  → Runs: ./gradlew bootJar -x test
  → Produces: build/libs/flexbenefits-0.0.1-SNAPSHOT.jar

Stage 2 (runner):   eclipse-temurin:21-jre-alpine  (~200MB)
  → Copies only the JAR from stage 1
  → Runs as non-root user (security)
  → Health check via /actuator/health
  → ENTRYPOINT: java -jar app.jar
```

### Running with Docker

```bash
# Full stack (build app + start everything)
docker compose up --build

# Background mode
docker compose up --build -d

# View app logs
docker compose logs app -f

# Stop everything
docker compose down

# Full reset (delete all data)
docker compose down -v
```

### Local Development (without Docker for the app)

```bash
# Start only infrastructure
docker compose up postgres redis minio -d

# Run app locally (uses localhost defaults)
.\gradlew bootRun

# App connects to localhost:5432, localhost:6379, localhost:9000
```

### Environment Variables

All config reads from environment variables with localhost fallbacks:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/flexbenefits}
spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}
minio.endpoint=${MINIO_ENDPOINT:http://localhost:9000}
jwt.secret=${JWT_SECRET:base64-key-here}
```

| Context | How it works |
|---------|-------------|
| **Local dev** | No env vars needed → defaults to `localhost` |
| **Docker Compose** | `environment:` block sets vars → uses service names (`postgres`, `redis`, `minio`) |
| **Production** | Platform (Render/AWS) sets env vars → uses real URLs |

### Service Startup Order

```yaml
app:
  depends_on:
    postgres:
      condition: service_healthy    # waits for pg_isready check
    redis:
      condition: service_started
    minio:
      condition: service_started
```

Postgres has a healthcheck — the app won't start until Postgres is accepting connections. This prevents Flyway migration failures.

---

*Last updated: June 17, 2026*

