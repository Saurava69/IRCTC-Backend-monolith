ALTER TABLE bookings
    ADD COLUMN cancellation_reason VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN refund_transaction_id VARCHAR(100);

CREATE INDEX idx_bookings_train_status
    ON bookings(train_run_id, booking_status);
