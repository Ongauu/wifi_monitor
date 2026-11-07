CREATE TABLE wifi_connection_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    ssid                VARCHAR(255),
    status              VARCHAR(32) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ,
    duration_ms         BIGINT,
    start_ip_address    VARCHAR(64),
    end_ip_address      VARCHAR(64),
    start_signal_percent INTEGER,
    end_signal_percent  INTEGER,
    disconnect_reason   VARCHAR(64),
    event_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_session_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_sessions_started_at ON wifi_connection_sessions (started_at DESC);
CREATE INDEX idx_sessions_status ON wifi_connection_sessions (status);
CREATE INDEX idx_sessions_ssid ON wifi_connection_sessions (ssid);

CREATE TABLE wifi_connection_events (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT REFERENCES wifi_connection_sessions (id),
    event_type              VARCHAR(64) NOT NULL,
    failure_category        VARCHAR(64),
    occurred_at             TIMESTAMPTZ NOT NULL,
    ssid                    VARCHAR(255),
    signal_strength_percent INTEGER,
    ip_address              VARCHAR(64),
    interface_name          VARCHAR(128),
    interface_state         VARCHAR(64),
    wifi_status             VARCHAR(32) NOT NULL,
    gateway_status          VARCHAR(32) NOT NULL,
    internet_status         VARCHAR(32) NOT NULL,
    dns_status              VARCHAR(32) NOT NULL,
    gateway_address         VARCHAR(64),
    gateway_latency_ms      DOUBLE PRECISION,
    internet_latency_ms     DOUBLE PRECISION,
    packet_loss_percent     DOUBLE PRECISION,
    connection_uptime_ms    BIGINT,
    disconnection_duration_ms BIGINT,
    diagnostic_summary      TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_occurred_at ON wifi_connection_events (occurred_at DESC);
CREATE INDEX idx_events_type ON wifi_connection_events (event_type);
CREATE INDEX idx_events_ssid ON wifi_connection_events (ssid);
CREATE INDEX idx_events_session_id ON wifi_connection_events (session_id);
CREATE INDEX idx_events_failure_category ON wifi_connection_events (failure_category);

CREATE TABLE wifi_diagnostic_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    event_id                    BIGINT REFERENCES wifi_connection_events (id),
    captured_at                 TIMESTAMPTZ NOT NULL,
    trigger_event_type          VARCHAR(64) NOT NULL,
    adapter_state               VARCHAR(64),
    radio_type                  VARCHAR(64),
    authentication              VARCHAR(64),
    bssid                       VARCHAR(64),
    channel                     VARCHAR(32),
    receive_rate_mbps           VARCHAR(32),
    transmit_rate_mbps          VARCHAR(32),
    netsh_interfaces_output     TEXT,
    wlan_autoconfig_events      TEXT,
    wlan_report_path            VARCHAR(1024),
    inferred_causes             TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_captured_at ON wifi_diagnostic_snapshots (captured_at DESC);
CREATE INDEX idx_snapshots_event_id ON wifi_diagnostic_snapshots (event_id);
