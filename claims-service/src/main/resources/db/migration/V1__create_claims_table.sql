-- Claims Service Schema
-- This service owns the claims table independently

CREATE TABLE IF NOT EXISTS claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    claim_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    service_date DATE,
    provider_name VARCHAR(255),
    diagnosis_code VARCHAR(20),
    claimed_amount NUMERIC(12, 2) NOT NULL,
    approved_amount NUMERIC(12, 2),
    rejection_reason TEXT,
    submitted_at TIMESTAMP,
    adjudicated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_claims_tenant_id ON claims(tenant_id);
CREATE INDEX idx_claims_employee_id ON claims(employee_id);
CREATE INDEX idx_claims_enrollment_id ON claims(enrollment_id);
CREATE INDEX idx_claims_status ON claims(tenant_id, status);
CREATE INDEX idx_claims_claim_number ON claims(claim_number);

