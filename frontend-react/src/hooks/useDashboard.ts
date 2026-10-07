import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import type { DashboardData, Resource } from "../types";

const emptyData = (): DashboardData => ({
  devices: [],
  device: null,
  latest: null,
  telemetry: [],
  state: null,
  commands: [],
  alerts: [],
});

export function useDashboard(deviceId: string) {
  const [data, setData] = useState<DashboardData>(emptyData);
  const [errors, setErrors] = useState<Partial<Record<Resource, string>>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const refreshRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    let disposed = false;
    let pending = false;
    let version = 0;
    let controller = new AbortController();
    setData((previous) => ({ ...emptyData(), devices: previous.devices }));
    setErrors({});
    setLoading(true);
    setUpdatedAt(null);

    async function load(force = false): Promise<void> {
      if (disposed || (pending && !force)) return;
      if (force) controller.abort();
      controller = new AbortController();
      const signal = controller.signal;
      const requestVersion = ++version;
      pending = true;
      setRefreshing(true);
      const results = await Promise.allSettled([
        api.devices(signal),
        api.device(deviceId, signal),
        api.latest(deviceId, signal),
        api.telemetry(deviceId, signal),
        api.state(deviceId, signal),
        api.commands(deviceId, signal),
        api.alerts(deviceId, signal),
      ] as const);
      if (disposed || signal.aborted || requestVersion !== version) return;

      const updates: Partial<DashboardData> = {};
      const failures: Partial<Record<Resource, string>> = {};
      function collect<K extends Resource>(
        key: K,
        result: PromiseSettledResult<DashboardData[K]>,
      ) {
        if (result.status === "fulfilled") updates[key] = result.value;
        else
          failures[key] =
            result.reason instanceof Error
              ? result.reason.message
              : "Không tải được dữ liệu.";
      }
      collect("devices", results[0]);
      collect("device", results[1]);
      collect("latest", results[2]);
      collect("telemetry", results[3]);
      collect("state", results[4]);
      collect("commands", results[5]);
      collect("alerts", results[6]);
      // Failed resources keep their last good data, always accompanied by an error notice.
      setData((previous) => ({ ...previous, ...updates }));
      setErrors(failures);
      if (Object.keys(failures).length === 0) setUpdatedAt(new Date());
      setLoading(false);
      setRefreshing(false);
      pending = false;
    }

    refreshRef.current = () => load(true);
    void load();
    const timer = window.setInterval(() => {
      if (!document.hidden) void load();
    }, 3000);
    const resume = () => {
      if (!document.hidden) void load(true);
    };
    document.addEventListener("visibilitychange", resume);
    return () => {
      disposed = true;
      controller.abort();
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", resume);
      refreshRef.current = async () => {};
    };
  }, [deviceId]);

  const refresh = useCallback(() => refreshRef.current(), []);
  return { data, errors, loading, refreshing, updatedAt, refresh };
}
