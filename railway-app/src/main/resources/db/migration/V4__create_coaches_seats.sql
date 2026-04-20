CREATE TABLE coaches (
    id                  BIGSERIAL PRIMARY KEY,
    train_id            BIGINT NOT NULL REFERENCES trains(id),
    coach_number        VARCHAR(10) NOT NULL,
    coach_type          VARCHAR(20) NOT NULL,
    total_seats         INT NOT NULL,
    total_berths        INT NOT NULL,
    sequence_in_train   INT NOT NULL,
    UNIQUE(train_id, coach_number)
);

CREATE INDEX idx_coaches_train ON coaches(train_id);

CREATE TABLE seat_inventory (
    id              BIGSERIAL PRIMARY KEY,
    train_run_id    BIGINT NOT NULL REFERENCES train_runs(id),
    coach_type      VARCHAR(20) NOT NULL,
    from_station_id BIGINT NOT NULL REFERENCES stations(id),
    to_station_id   BIGINT NOT NULL REFERENCES stations(id),
    total_seats     INT NOT NULL,
    available_seats INT NOT NULL,
    rac_seats       INT NOT NULL DEFAULT 0,
    waitlist_count  INT NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE(train_run_id, coach_type, from_station_id, to_station_id)
);

CREATE INDEX idx_seat_inv_run ON seat_inventory(train_run_id);
CREATE INDEX idx_seat_inv_lookup ON seat_inventory(train_run_id, coach_type, from_station_id, to_station_id);
