import type {
  Alert,
  ApiErrorBody,
  Command,
  CommandRequest,
  Device,
  DeviceState,
  Telemetry,
} from "../types";

const baseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/+$/, "");
export const configurationError = !baseUrl
  ? "Thiếu VITE_API_BASE_URL. Hãy tạo .env từ .env.example rồi khởi động lại frontend."
  : !/^https?:\/\//i.test(baseUrl)
    ? "VITE_API_BASE_URL phải là URL HTTP hoặc HTTPS hợp lệ."
    : null;

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(
  path: string,
  signal: AbortSignal,
  body?: CommandRequest,
): Promise<T> {
  if (configurationError) throw new ApiError(configurationError, 0);
  const timeout = AbortSignal.timeout(8000);
  const combined = AbortSignal.any([signal, timeout]);
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: body ? "POST" : "GET",
      signal: combined,
      cache: "no-store",
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new ApiError(
      timeout.aborted
        ? "API phản hồi quá lâu. Vui lòng thử lại."
        : "Không kết nối được API. Kiểm tra backend, mạng và cấu hình CORS.",
      0,
    );
  }
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const detail = payload as
      | (Partial<Omit<ApiErrorBody, "status">> & {
          status?: string | number;
          id?: number;
        })
      | null;
    const message =
      detail?.status === "FAILED"
        ? `Lệnh #${detail.id} gửi thất bại. MQTT có thể đang mất kết nối.`
        : (detail?.message ?? `API trả HTTP ${response.status}.`);
    throw new ApiError(message, response.status, payload);
  }
  if (payload === null)
    throw new ApiError("API không trả về JSON hợp lệ.", response.status);
  return payload as T;
}

const devicePath = (id: string) => `/api/devices/${encodeURIComponent(id)}`;
export const api = {
  devices: (signal: AbortSignal) => request<Device[]>("/api/devices", signal),
  device: (id: string, signal: AbortSignal) =>
    request<Device>(devicePath(id), signal),
  latest: async (
    id: string,
    signal: AbortSignal,
  ): Promise<Telemetry | null> => {
    try {
      return await request<Telemetry>(
        `${devicePath(id)}/telemetry/latest`,
        signal,
      );
    } catch (error) {
      // The device endpoint independently distinguishes a missing device from an empty history.
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
  telemetry: (id: string, signal: AbortSignal) =>
    request<Telemetry[]>(`${devicePath(id)}/telemetry?limit=100`, signal),
  state: (id: string, signal: AbortSignal) =>
    request<DeviceState>(`${devicePath(id)}/state`, signal),
  commands: (id: string, signal: AbortSignal) =>
    request<Command[]>(`${devicePath(id)}/commands`, signal),
  alerts: (id: string, signal: AbortSignal) =>
    request<Alert[]>(`${devicePath(id)}/alerts`, signal),
  command: (id: string, body: CommandRequest, signal: AbortSignal) =>
    request<Command>(`${devicePath(id)}/commands`, signal, body),
};
