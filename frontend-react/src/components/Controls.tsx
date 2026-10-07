import type { Actuator, DeviceState, OutputState } from "../types";
import { Icon } from "./Icon";
import { Status, ResourceNotice } from "./Status";

export interface PendingCommand {
  device: Actuator;
  action: OutputState;
}
export interface Feedback {
  tone: "success" | "error";
  message: string;
}

export function Controls({
  state,
  online,
  unavailable,
  pending,
  feedback,
  onCommand,
  error,
}: {
  state: DeviceState | null;
  online: boolean;
  unavailable: boolean;
  pending: PendingCommand | null;
  feedback: Feedback | null;
  onCommand: (device: Actuator, action: OutputState) => void;
  error?: string;
}) {
  const disabled = !!pending || !online || unavailable;
  return (
    <section className="panel controls-panel" aria-labelledby="controls-title">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">TRONG TẦM TAY</span>
          <h2 id="controls-title">Điều khiển thiết bị</h2>
        </div>
        <Icon name="power" size={19} />
      </div>
      <ResourceNotice message={error} />
      <div className="control-groups">
        {(["fan", "light"] as const).map((device) => {
          const name = device === "fan" ? "Quạt" : "Đèn";
          const value = state?.[device];
          return (
            <div className="control-group" key={device}>
              <div className="control-label">
                <span className={`icon-tile ${device}`}>
                  <Icon name={device} size={23} />
                </span>
                <div>
                  <h3>{name}</h3>
                  <span>
                    {device === "fan"
                      ? "Thông gió trong phòng"
                      : "Chiếu sáng trong phòng"}
                  </span>
                </div>
                <Status value={value} />
              </div>
              <div
                className="control-buttons"
                role="group"
                aria-label={`Điều khiển ${name.toLowerCase()}`}
              >
                {(["ON", "OFF"] as const).map((action) => (
                  <button
                    key={action}
                    type="button"
                    disabled={disabled}
                    aria-label={`${device === "fan" ? "Fan" : "Light"} ${action}`}
                    aria-pressed={value === action}
                    className={`command-button ${value === action ? "is-current" : ""}`}
                    onClick={() => onCommand(device, action)}
                  >
                    {pending?.device === device && pending.action === action ? (
                      <span className="spinner" />
                    ) : (
                      <Icon name="power" size={16} />
                    )}
                    {pending?.device === device && pending.action === action
                      ? "Đang gửi…"
                      : action}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      <div className="command-feedback" aria-live="polite" aria-atomic="true">
        {feedback ? (
          <p
            className={`feedback feedback-${feedback.tone}`}
            role={feedback.tone === "error" ? "alert" : "status"}
          >
            <Icon
              name={feedback.tone === "error" ? "warning" : "check"}
              size={17}
            />
            {feedback.message}
          </p>
        ) : (
          <p className="control-hint">
            <Icon name="signal" size={15} />
            {unavailable
              ? "Đang chờ kết nối API để điều khiển."
              : !online
                ? "Thiết bị đang OFFLINE. Chờ thiết bị kết nối để điều khiển."
                : "Trạng thái cập nhật sau mỗi lệnh gửi."}
          </p>
        )}
      </div>
    </section>
  );
}
