CREATE TABLE transfer (
    id BIGSERIAL PRIMARY KEY,
    sender_account_id BIGINT NOT NULL,
    receiver_account_id BIGINT NOT NULL,
    transfer_date_time TIMESTAMPTZ,
    value DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_sender_ac_id FOREIGN KEY (sender_account_id) REFERENCES account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiver_ac_id FOREIGN KEY (receiver_account_id) REFERENCES account(id) ON DELETE RESTRICT
);