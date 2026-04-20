CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    pnr                 VARCHAR(15) NOT NULL UNIQUE,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    train_run_id        BIGINT NOT NULL REFERENCES train_runs(id),
    coach_type          VARCHAR(20) NOT NULL,
    from_station_id     BIGINT NOT NULL REFERENCES stations(id),
    to_station_id       BIGINT NOT NULL REFERENCES stations(id),
    booking_status      VARCHAR(20) NOT NULL,
    total_fare          DECIMAL(12, 2) NOT NULL,
    passenger_count     INT NOT NULL,
    booked_at           TIMESTAMPTZ,
    idempotency_key     VARCHAR(64) UNIQUE,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_pnr ON bookings(pnr);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_train_run ON bookings(train_run_id);
CREATE INDEX idx_bookings_status ON bookings(booking_status);

CREATE TABLE booking_passengers (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES bookings(id),
    name            VARCHAR(255) NOT NULL,
    age             INT NOT NULL,
    gender          VARCHAR(10) NOT NULL,
    berth_preference VARCHAR(20),
    seat_number     VARCHAR(20),
    coach_number    VARCHAR(10),
    status          VARCHAR(20) NOT NULL,
    waitlist_number INT,
    rac_number      INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bp_booking ON booking_passengers(booking_id);

CREATE TABLE seat_allocations (
    id                      BIGSERIAL PRIMARY KEY,
    train_run_id            BIGINT NOT NULL REFERENCES train_runs(id),
    coach_id                BIGINT NOT NULL REFERENCES coaches(id),
    seat_number             VARCHAR(10) NOT NULL,
    booking_passenger_id    BIGINT REFERENCES booking_passengers(id),
    from_station_id         BIGINT NOT NULL REFERENCES stations(id),
    to_station_id           BIGINT NOT NULL REFERENCES stations(id),
    is_occupied             BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(train_run_id, coach_id, seat_number, from_station_id, to_station_id)
);
