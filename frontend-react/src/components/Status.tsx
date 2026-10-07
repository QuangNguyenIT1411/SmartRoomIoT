import { Icon } from "./Icon";

export function Status({ value }: { value: string | null | undefined }) {
  const positive =
    value === "ONLINE" ||
    value === "ON" ||
    value === "SENT" ||
    value === "RESOLVED";
  const negative = value === "FAILED" || value === "UNRESOLVED";
  const tone = positive
    ? "positive"
    : negative
      ? "negative"
      : value === "PENDING"
        ? "pending"
        : "neutral";
  return (
    <span className={`status status-${tone}`}>
      <span className="status-dot" />
      {value ?? "UNKNOWN"}
    </span>
  );
}

export function EmptyState({
  title,
  detail,
  error = false,
}: {
  title: string;
  detail?: string;
  error?: boolean;
}) {
  return (
    <div className={`empty-state ${error ? "empty-error" : ""}`}>
      <span className="empty-icon">
        <Icon name={error ? "warning" : "signal"} size={24} />
      </span>
      <strong>{title}</strong>
      {detail ? <p>{detail}</p> : null}
    </div>
  );
}

export function ResourceNotice({ message }: { message?: string }) {
  return message ? (
    <p className="resource-notice">
      <Icon name="warning" size={15} />
      {message} Dữ liệu cũ có thể đang được hiển thị.
    </p>
  ) : null;
}
