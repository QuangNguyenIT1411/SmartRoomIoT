import { useState } from "react";
import type { Alert } from "../types";
import { formatDateTime } from "../lib/time";
import { EmptyState, ResourceNotice } from "./Status";
import { Icon } from "./Icon";

type Filter = "all" | "active" | "resolved";
export function AlertsPanel({
  alerts,
  loading,
  error,
}: {
  alerts: Alert[];
  loading: boolean;
  error?: string;
}) {
  const [filter, setFilter] = useState<Filter>("all");
  const unresolved = alerts.filter((alert) => !alert.resolved).length;
  const filtered = alerts.filter(
    (alert) =>
      filter === "all" ||
      (filter === "resolved" ? alert.resolved : !alert.resolved),
  );
  return (
    <section
      className="panel alerts-panel"
      id="alerts"
      aria-labelledby="alerts-title"
    >
      <div className="panel-heading">
        <div>
          <span className="eyebrow">AN TÂM MỖI NGÀY</span>
          <h2 id="alerts-title">Cảnh báo</h2>
        </div>
        <span className={`count-badge ${unresolved ? "has-alerts" : ""}`}>
          {unresolved} chưa xử lý
        </span>
      </div>
      <ResourceNotice message={error} />
      <div className="filter-tabs" role="group" aria-label="Lọc cảnh báo">
        {(
          [
            ["all", "Tất cả"],
            ["active", "Chưa xử lý"],
            ["resolved", "Đã xử lý"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={filter === value}
            className={filter === value ? "selected" : ""}
            onClick={() => setFilter(value)}
          >
            {label}
          </button>
        ))}
      </div>
      {loading ? (
        <div
          className="alerts-skeleton skeleton"
          aria-label="Đang tải cảnh báo"
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          error={!!error}
          title={
            error
              ? "Chưa tải được cảnh báo"
              : filter === "all"
                ? "Không có cảnh báo"
                : filter === "active"
                  ? "Không có cảnh báo chưa xử lý"
                  : "Chưa có cảnh báo đã xử lý"
          }
          detail={
            error
              ? "Hệ thống sẽ tự thử lại ở lần cập nhật tiếp theo."
              : "Cảnh báo nhiệt độ sẽ xuất hiện tại đây khi có dữ liệu phù hợp."
          }
        />
      ) : (
        <div className="alert-list">
          {filtered.map((alert) => (
            <article
              className={`alert-item ${alert.resolved ? "is-resolved" : "is-unresolved"}`}
              key={alert.id}
            >
              <span className="alert-symbol">
                <Icon name={alert.resolved ? "check" : "warning"} size={18} />
              </span>
              <div>
                <div className="alert-item-heading">
                  <strong>
                    {alert.alertType === "HIGH_TEMPERATURE"
                      ? "Nhiệt độ cao"
                      : alert.alertType}
                  </strong>
                  <span className="alert-resolution">
                    {alert.resolved ? "Đã xử lý" : "Chưa xử lý"}
                  </span>
                </div>
                <p>{alert.message}</p>
                <div className="alert-meta">
                  <span>{alert.severity}</span>
                  <time>{formatDateTime(alert.createdAt)}</time>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      <div className="panel-footer">
        <Icon name="temperature" size={14} />
        <span>Cảnh báo từ 35°C · Tự xử lý khi dưới 33°C</span>
      </div>
    </section>
  );
}
