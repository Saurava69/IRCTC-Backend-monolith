-- V6: Payments table
CREATE TABLE payments (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT NOT NULL REFERENCES bookings(id),
    pnr                     VARCHAR(15) NOT NULL,
    amount                  DECIMAL(12,2) NOT NULL,
    payment_status          VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    payment_method          VARCHAR(30),
    gateway_transaction_id  VARCHAR(100),
    gateway_response        TEXT,
    failure_reason          VARCHAR(255),
    idempotency_key         VARCHAR(64) UNIQUE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_pnr ON payments(pnr);
CREATE INDEX idx_payments_status ON payments(payment_status);
