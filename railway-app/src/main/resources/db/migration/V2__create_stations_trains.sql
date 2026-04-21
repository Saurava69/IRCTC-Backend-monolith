CREATE TABLE stations (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(10) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    city            VARCHAR(100),
    state           VARCHAR(100),
    zone            VARCHAR(50),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stations_code ON stations(code);
CREATE INDEX idx_stations_name ON stations USING gin(to_tsvector('english', name));

CREATE TABLE trains (
    id                  BIGSERIAL PRIMARY KEY,
    train_number        VARCHAR(10) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    train_type          VARCHAR(30) NOT NULL,
    source_station_id   BIGINT NOT NULL REFERENCES stations(id),
    dest_station_id     BIGINT NOT NULL REFERENCES stations(id),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trains_number ON trains(train_number);
