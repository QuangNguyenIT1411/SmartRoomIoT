import { useEffect, useRef, useState } from "react";
import { AlertsPanel } from "./components/AlertsPanel";
import { CommandHistory } from "./components/CommandHistory";
import { Controls } from "./components/Controls";
import type { Feedback, PendingCommand } from "./components/Controls";
import { Icon } from "./components/Icon";
import type { IconName } from "./components/Icon";
import { Status } from "./components/Status";
import { TelemetryChart } from "./components/TelemetryChart";
import { useDashboard } from "./hooks/useDashboard";
import { api, configurationError } from "./lib/api";
import { formatDateTime, formatTime, measurement } from "./lib/time";
import type { Actuator, OutputState, Resource } from "./types";

const navigation: { id: string; label: string; icon: IconName }[] = [
  { id: "overview", label: "Tổng quan", icon: "grid" },
  { id: "telemetry", label: "Dữ liệu cảm biến", icon: "chart" },
  { id: "commands", label: "Lịch sử điều khiển", icon: "history" },
  { id: "alerts", label: "Cảnh báo", icon: "bell" },
];
const resourceNames: Record<Resource, string> = {
  devices: "danh sách thiết bị",
  device: "thiết bị",
  latest: "mẫu đo mới nhất",
  telemetry: "biểu đồ",
  state: "trạng thái",
  commands: "lịch sử lệnh",
  alerts: "cảnh báo",
};

export default function App() {
  const [deviceId, setDeviceId] = useState(
    () =>
      new URLSearchParams(window.location.search).get("device") ||
      "smartroom-01",
  );
  const [activeNav, setActiveNav] = useState(
    window.location.hash.slice(1) || "overview",
  );
  const [pending, setPending] = useState<PendingCommand | null>(null);
  const [feedback, setFeedback] = useState<
    (Feedback & { deviceId: string }) | null
  >(null);
  const commandController = useRef<AbortController | null>(null);
  const commandLock = useRef(false);
  const { data, errors, loading, refreshing, updatedAt, refresh } =
    useDashboard(deviceId);
  const { device, latest, state } = data;
  const online = device?.status === "ONLINE";
  const errorKeys = Object.keys(errors) as Resource[];
  const unresolved = data.alerts.filter((alert) => !alert.resolved).length;
  const unavailable =
    loading || !!configurationError || !!errors.device || !!errors.state;

  useEffect(() => {
    const onHash = () =>
      setActiveNav(window.location.hash.slice(1) || "overview");
    window.addEventListener("hashchange", onHash);
    return () => {
      window.removeEventListener("hashchange", onHash);
      commandController.current?.abort();
    };
  }, []);

  async function sendCommand(actuator: Actuator, action: OutputState) {
    if (commandLock.current || unavailable || !online) return;
    commandLock.current = true;
    commandController.current = new AbortController();
    const signal = commandController.current.signal;
    setPending({ device: actuator, action });
    setFeedback(null);
    try {
      const record = await api.command(
        deviceId,
        { device: actuator, action },
        signal,
      );
      if (signal.aborted) return;
      setFeedback({
        tone: record.status === "SENT" ? "success" : "error",
        deviceId,
        message:
          record.status === "SENT"
            ? `Đã gửi ${actuator === "fan" ? "Fan" : "Light"} ${action} · Lệnh #${record.id}.`
            : `Lệnh #${record.id}: ${record.status}. Vui lòng kiểm tra trạng thái thiết bị.`,
      });
    } catch (error) {
      if (signal.aborted) return;
      setFeedback({
        tone: "error",
        deviceId,
        message:
          error instanceof Error ? error.message : "Không gửi được lệnh.",
      });
    } finally {
      if (!signal.aborted) {
        // Force a new read after POST, canceling older polling requests to prevent stale state winning.
        await refresh();
        setPending(null);
      }
      commandLock.current = false;
    }
  }

  function selectDevice(nextId: string) {
    setDeviceId(nextId);
    setFeedback(null);
    const url = new URL(window.location.href);
    url.searchParams.set("device", nextId);
    window.history.replaceState(null, "", url);
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Chuyển đến nội dung chính
      </a>
      <aside className="sidebar">
        <a
          className="brand"
          href="#overview"
          aria-label="SmartRoom — Tổng quan"
        >
          <span className="brand-mark">
            <Icon name="room" size={25} />
          </span>
          <span>
            smartroom<span className="brand-dot">.</span>
            <small>CONNECTED LIVING</small>
          </span>
        </a>
        <div className="nav-caption">KHÔNG GIAN LÀM VIỆC</div>
        <nav aria-label="Điều hướng dashboard">
          {navigation.map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              className={`nav-link ${activeNav === item.id ? "active" : ""}`}
              aria-current={activeNav === item.id ? "location" : undefined}
            >
              <Icon name={item.icon} size={19} />
              <span>{item.label}</span>
              {item.id === "alerts" && unresolved > 0 ? (
                <span className="nav-count">{unresolved}</span>
              ) : null}
            </a>
          ))}
        </nav>
        <div className="sidebar-room">
          <div className="room-illustration" aria-hidden="true">
            <svg viewBox="0 0 200 115" fill="none">
              <path
                d="m37 49 63-33 63 33v49l-63 13-63-13V49Z"
                stroke="currentColor"
                strokeWidth="1.2"
              />
              <path
                d="m37 49 63 15 63-15M100 64v47M57 60v20l26 5V66M115 62v27l27-5V57M51 92l49 10 49-10"
                stroke="currentColor"
                strokeWidth="1.2"
              />
              <path
                d="m81 37 19-10 19 10-19 9-19-9Z"
                fill="currentColor"
                opacity=".2"
              />
              <circle cx="100" cy="37" r="4" fill="currentColor" />
              <path
                d="M100 18V5M91 9a13 13 0 0 1 18 0M84 3a24 24 0 0 1 32 0"
                stroke="currentColor"
                strokeWidth="1.2"
              />
            </svg>
          </div>
          <span className="eyebrow">MỘT CĂN PHÒNG THÔNG MINH</span>
          <p>
            Không gian của bạn.
            <br />
            Trong tầm tay.
          </p>
          <span className="sidebar-room-detail">
            Theo dõi · Kết nối · Điều khiển
          </span>
        </div>
        <div className="sidebar-bottom">
          <span className={`tiny-dot ${errorKeys.length ? "muted-dot" : ""}`} />
          {errorKeys.length
            ? "Cần kiểm tra kết nối API"
            : updatedAt
              ? "Đã kết nối backend"
              : "Đang kết nối backend"}
          <small>FINAL PROJECT / INTERNET OF THINGS</small>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            <Icon name="room" size={16} />
            <span>Không gian</span>
            <span>/</span>
            <strong>{device?.name ?? deviceId}</strong>
          </div>
          <div className="live-label">
            <span
              className={`tiny-dot ${errorKeys.length ? "muted-dot" : ""}`}
            />
            Tự cập nhật mỗi 3 giây
          </div>
        </header>
        <main id="main">
          <section className="page-heading" id="overview">
            <div>
              <div className="eyebrow">KẾT NỐI VỚI KHÔNG GIAN CỦA BẠN</div>
              <h1>Smart Room IoT Dashboard</h1>
              <p>Mọi thông số trong phòng, trên cùng một màn hình.</p>
            </div>
            <div className="heading-actions">
              <label className="device-select">
                <Icon name="chip" size={17} />
                <span className="sr-only">Chọn thiết bị</span>
                <select
                  value={deviceId}
                  onChange={(event) => selectDevice(event.target.value)}
                  disabled={!!pending}
                >
                  {!data.devices.some((item) => item.deviceId === deviceId) ? (
                    <option value={deviceId}>{deviceId}</option>
                  ) : null}
                  {data.devices.map((item) => (
                    <option key={item.id} value={item.deviceId}>
                      {item.deviceId}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="refresh-button"
                type="button"
                onClick={() => void refresh()}
                disabled={refreshing}
                aria-label="Làm mới dữ liệu"
              >
                <Icon
                  name="refresh"
                  size={18}
                  className={refreshing ? "rotating" : ""}
                />
              </button>
            </div>
          </section>

          {configurationError || errorKeys.length ? (
            <div className="error-banner" role="alert">
              <Icon name="warning" size={20} />
              <div>
                <strong>
                  {configurationError ?? "Một phần dữ liệu chưa được cập nhật"}
                </strong>
                {!configurationError ? (
                  <p>
                    {errors.device ?? errors[errorKeys[0]]} Mục bị ảnh hưởng:{" "}
                    {errorKeys.map((key) => resourceNames[key]).join(", ")}.
                  </p>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => void refresh()}
                disabled={refreshing}
              >
                Thử lại
              </button>
            </div>
          ) : null}

          <div className="room-strip">
            <div className="room-identity">
              <span className="room-icon">
                <Icon name="chip" size={21} />
              </span>
              <strong>{device?.name ?? deviceId}</strong>
              <span className="device-id mono">{deviceId}</span>
              <Status value={loading ? null : device?.status} />
            </div>
            <span className="last-seen">
              <Icon name="clock" size={15} />
              Last seen <time>{formatDateTime(device?.lastSeen)}</time>
            </span>
          </div>

          <section
            className="metrics"
            aria-label="Thông số hiện tại"
            aria-busy={loading}
          >
            <article
              className={`metric-card temperature-card ${loading ? "loading" : ""}`}
            >
              <div className="metric-top">
                <span>Nhiệt độ</span>
                <span className="icon-tile temperature">
                  <Icon name="temperature" size={21} />
                </span>
              </div>
              <div className="metric-value">
                {loading ? "…" : measurement(latest?.temperature)}
                <span>°C</span>
              </div>
              <div className="metric-bottom">
                <span
                  className={`tiny-dot ${latest?.temperature != null && latest.temperature >= 35 ? "warm-dot" : ""}`}
                />
                {errors.latest
                  ? "Dữ liệu có thể đã cũ"
                  : latest?.temperature == null
                    ? "Chờ dữ liệu cảm biến"
                    : latest.temperature >= 35
                      ? "Nhiệt độ ở mức cảnh báo"
                      : "Cảm biến DHT22"}
              </div>
            </article>
            <article
              className={`metric-card humidity-card ${loading ? "loading" : ""}`}
            >
              <div className="metric-top">
                <span>Độ ẩm</span>
                <span className="icon-tile humidity">
                  <Icon name="droplet" size={21} />
                </span>
              </div>
              <div className="metric-value">
                {loading ? "…" : measurement(latest?.humidity)}
                <span>%</span>
              </div>
              <div className="metric-bottom">
                <Icon name="clock" size={13} />
                {errors.latest
                  ? "Dữ liệu có thể đã cũ"
                  : latest
                    ? `Mẫu đo lúc ${formatTime(latest.createdAt)}`
                    : "Chờ dữ liệu cảm biến"}
              </div>
            </article>
            {(["fan", "light"] as const).map((actuator) => (
              <article
                key={actuator}
                className={`metric-card output-card ${loading ? "loading" : ""}`}
              >
                <div className="metric-top">
                  <span>
                    {actuator === "fan" ? "Trạng thái quạt" : "Trạng thái đèn"}
                  </span>
                  <span className={`icon-tile ${actuator}`}>
                    <Icon name={actuator} size={21} />
                  </span>
                </div>
                <div
                  className={`metric-value output-value ${state?.[actuator] === "ON" ? "output-on" : ""}`}
                >
                  {loading ? "…" : (state?.[actuator] ?? "—")}
                  <span
                    className={`output-indicator ${state?.[actuator] === "ON" ? "is-on" : ""}`}
                  />
                </div>
                <div className="metric-bottom">
                  {errors.state
                    ? "Dữ liệu có thể đã cũ"
                    : state?.[actuator] == null
                      ? "Chưa ghi nhận trạng thái"
                      : state[actuator] === "ON"
                        ? "Thiết bị đang bật"
                        : "Thiết bị đang tắt"}
                </div>
              </article>
            ))}
          </section>

          <div className="dashboard-grid">
            <TelemetryChart
              telemetry={data.telemetry}
              loading={loading}
              error={errors.telemetry}
            />
            <Controls
              state={state}
              online={online}
              unavailable={unavailable}
              pending={pending}
              feedback={feedback?.deviceId === deviceId ? feedback : null}
              error={errors.state}
              onCommand={(actuator, action) =>
                void sendCommand(actuator, action)
              }
            />
            <CommandHistory
              commands={data.commands}
              loading={loading}
              error={errors.commands}
            />
            <AlertsPanel
              key={deviceId}
              alerts={data.alerts}
              loading={loading}
              error={errors.alerts}
            />
          </div>
          <footer className="page-footer">
            <span>
              <span className="tiny-dot" />
              Smart Room IoT <span className="footer-divider">/</span> Kết nối
              để sống thông minh hơn.
            </span>
            <span role="status" aria-live="off">
              {updatedAt
                ? `Đồng bộ lúc ${formatTime(updatedAt)} · Giờ Việt Nam`
                : loading
                  ? "Đang tải dữ liệu…"
                  : "Chưa đồng bộ dữ liệu"}
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
