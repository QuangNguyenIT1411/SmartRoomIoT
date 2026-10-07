// Contract read from DeviceController, entity getters, BaseEntity and DTO records.
// Backend LocalDateTime values are UTC without a timezone suffix.
export type OutputState = "ON" | "OFF";
export type Actuator = "fan" | "light";

export interface Device {
  id: number;
  deviceId: string;
  name: string;
  status: "ONLINE" | "OFFLINE";
  lastSeen: string | null;
  createdAt: string;
}

export interface Telemetry {
  id: number;
  deviceId: string;
  temperature: number | null;
  humidity: number | null;
  lightState: "ACTIVE" | "INACTIVE" | null;
  fanState: OutputState | null;
  lightOutputState: OutputState | null;
  createdAt: string;
}

export interface DeviceState {
  deviceId: string;
  fan: OutputState | null;
  light: OutputState | null;
  observedAt: string | null;
  source: "STATE" | "TELEMETRY" | "UNKNOWN";
}

export interface CommandRequest {
  device: Actuator;
  action: OutputState;
}

export interface Command {
  id: number;
  deviceId: string;
  deviceType: string;
  action: string;
  status: string;
  createdAt: string;
  executedAt: string | null;
}

export interface Alert {
  id: number;
  deviceId: string;
  alertType: string;
  message: string;
  severity: string;
  resolved: boolean;
  createdAt: string;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface DashboardData {
  devices: Device[];
  device: Device | null;
  latest: Telemetry | null;
  telemetry: Telemetry[];
  state: DeviceState | null;
  commands: Command[];
  alerts: Alert[];
}
export type Resource = keyof DashboardData;
