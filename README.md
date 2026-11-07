# Wi-Fi Connection Monitoring System

Local Spring Boot application that monitors the **Windows Wi-Fi adapter** every 5 seconds, records state transitions (not duplicate snapshots), and stores diagnostics in PostgreSQL.

It distinguishes:

1. Wi-Fi adapter disconnecting from the wireless network
2. Wi-Fi staying associated while internet access is lost
3. DNS failures
4. Default gateway / router failures
5. Weak signal

## Requirements

- Windows 10/11
- Java 21+ (Java 26 is fine)
- Maven 3.9+
- PostgreSQL running locally (no Docker)

## PostgreSQL setup

Create the database once:

```sql
CREATE DATABASE wifi_monitor;
```

Default connection in `src/main/resources/application.yml`:

- URL: `jdbc:postgresql://localhost:5432/wifi_monitor`
- User / password: the same local PostgreSQL account you already use (`DB_USERNAME` / `DB_PASSWORD` override `application.yml`)

Create the empty database, then start the app. Do not commit a real password if you change the defaults.

## Run

```powershell
cd C:\Users\crimson\wifi-monitor
mvn spring-boot:run
```

Monitoring starts automatically. Default API port is `8080`.

Connection, disconnection, uptime, and downtime values in the API are **minutes** (two decimal places). Ping latency stays in milliseconds.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/wifi/status` | Current adapter, gateway, DNS, and internet status |
| GET | `/api/wifi/events` | Historical state-transition events |
| GET | `/api/wifi/sessions` | Connection sessions and durations (minutes) |
| GET | `/api/wifi/statistics` | Disconnect counts, uptime/downtime in minutes, signal, availability |
| GET | `/api/wifi/diagnostics` | Windows diagnostic snapshots from disconnects |
| GET | `/api/wifi/analysis` | Pattern-based possible causes (not a guaranteed root cause) |

Optional query parameters on history endpoints: `from`, `to` (ISO-8601), `page`, `size`, and `type` on events.

Example:

```powershell
curl http://localhost:8080/api/wifi/status
curl http://localhost:8080/api/wifi/events
curl http://localhost:8080/api/wifi/analysis
```

## Configuration

See `wifi.monitor` in `application.yml`:

- `interval-ms` — poll interval (default 5000)
- `signal-low-threshold-percent` / `signal-recover-threshold-percent`
- `internet-hosts` / `dns-hosts`
- `collect-wlan-events-on-disconnect` — read WLAN AutoConfig events via `wevtutil`
- `collect-wlan-report-on-disconnect` — optional, slow `netsh wlan show wlanreport` (off by default)

The app never changes network settings. Commands are read-only (`netsh wlan show`, `ipconfig`, `ping`, `wevtutil qe`).
