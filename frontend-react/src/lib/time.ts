// pgjdbc/JVM use UTC; JSON LocalDateTime has no suffix. Never treat it as browser-local time.
export function parseUtc(value: string): Date {
  return new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`);
}

const dateTime = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});
const time = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "Chưa ghi nhận";
  const date = parseUtc(value);
  return Number.isNaN(date.getTime())
    ? "Không rõ thời gian"
    : dateTime.format(date);
}
export function formatTime(value: string | Date): string {
  const date = typeof value === "string" ? parseUtc(value) : value;
  return Number.isNaN(date.getTime()) ? "—" : time.format(date);
}
export function measurement(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value)
    ? "—"
    : value.toLocaleString("vi-VN", {
        maximumFractionDigits: 1,
        minimumFractionDigits: 1,
      });
}
