CREATE TABLE routes (
    id              BIGSERIAL PRIMARY KEY,
    train_id        BIGINT NOT NULL REFERENCES trains(id),
    route_name      VARCHAR(255),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE route_stations (
    id                      BIGSERIAL PRIMARY KEY,
    route_id                BIGINT NOT NULL REFERENCES routes(id),
    station_id              BIGINT NOT NULL REFERENCES stations(id),
    sequence_number         INT NOT NULL,
    arrival_time            TIME,
    departure_time          TIME,
    halt_minutes            INT DEFAULT 0,
    distance_from_origin_km INT NOT NULL DEFAULT 0,
    day_offset              INT NOT NULL DEFAULT 0,
    UNIQUE(route_id, sequence_number),
    UNIQUE(route_id, station_id)
);

CREATE INDEX idx_route_stations_route ON route_stations(route_id);
CREATE INDEX idx_route_stations_station ON route_stations(station_id);

CREATE TABLE schedules (
    id                  BIGSERIAL PRIMARY KEY,
    train_id            BIGINT NOT NULL REFERENCES trains(id),
    route_id            BIGINT NOT NULL REFERENCES routes(id),
    runs_on_monday      BOOLEAN DEFAULT FALSE,
    runs_on_tuesday     BOOLEAN DEFAULT FALSE,
    runs_on_wednesday   BOOLEAN DEFAULT FALSE,
    runs_on_thursday    BOOLEAN DEFAULT FALSE,
    runs_on_friday      BOOLEAN DEFAULT FALSE,
    runs_on_saturday    BOOLEAN DEFAULT FALSE,
    runs_on_sunday      BOOLEAN DEFAULT FALSE,
    effective_from      DATE NOT NULL,
    effective_until     DATE,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE train_runs (
    id              BIGSERIAL PRIMARY KEY,
    schedule_id     BIGINT NOT NULL REFERENCES schedules(id),
    train_id        BIGINT NOT NULL REFERENCES trains(id),
    route_id        BIGINT NOT NULL REFERENCES routes(id),
    run_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    UNIQUE(schedule_id, run_date)
);

CREATE INDEX idx_train_runs_date ON train_runs(run_date);
CREATE INDEX idx_train_runs_train_date ON train_runs(train_id, run_date);
