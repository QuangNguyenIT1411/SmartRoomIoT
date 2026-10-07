import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { formatTime, measurement, parseUtc } from "../lib/time";
import type { Telemetry } from "../types";
import { EmptyState, ResourceNotice } from "./Status";
import { Icon } from "./Icon";

function SensorPlot({
  samples,
  metric,
  title,
  unit,
  color,
}: {
  samples: Telemetry[];
  metric: "temperature" | "humidity";
  title: string;
  unit: string;
  color: string;
}) {
  const gradientId = useId().replace(/:/g, "");
  const [hovered, setHovered] = useState<number | null>(null);
  const container = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(600);
  const height = 160,
    left = 37,
    right = 14,
    top = 16,
    bottom = 30;
  useEffect(() => {
    if (!container.current) return;
    const observer = new ResizeObserver((entries) =>
      setWidth(Math.max(180, Math.round(entries[0].contentRect.width))),
    );
    observer.observe(container.current);
    return () => observer.disconnect();
  }, []);
  const valid = samples.filter(
    (sample) => sample[metric] !== null && Number.isFinite(sample[metric]),
  );
  if (valid.length === 0)
    return (
      <div className="sensor-plot" ref={container}>
        <div className="plot-label">
          {title} <span>{unit}</span>
        </div>
        <EmptyState
          title={`Chưa có dữ liệu ${title.toLowerCase()}`}
          detail="Chờ mẫu đo tiếp theo từ thiết bị."
        />
      </div>
    );
  const values = valid.map((sample) => sample[metric] as number);
  const minimum = Math.min(...values),
    maximum = Math.max(...values);
  const padding = Math.max(
    (maximum - minimum) * 0.2,
    metric === "temperature" ? 1 : 2,
  );
  const min = Math.max(metric === "humidity" ? 0 : -40, minimum - padding);
  const max = Math.min(metric === "humidity" ? 100 : 80, maximum + padding);
  const start = parseUtc(samples[0].createdAt).getTime();
  const end = parseUtc(samples[samples.length - 1].createdAt).getTime();
  const x = (sample: Telemetry) =>
    left +
    (end === start
      ? 0.5
      : (parseUtc(sample.createdAt).getTime() - start) / (end - start)) *
      (width - left - right);
  const y = (value: number) =>
    top + (1 - (value - min) / (max - min || 1)) * (height - top - bottom);
  // Preserve gaps for null sensor values instead of drawing fabricated data between samples.
  const segments: Telemetry[][] = [];
  let current: Telemetry[] = [];
  for (const sample of samples) {
    if (sample[metric] === null || !Number.isFinite(sample[metric])) {
      if (current.length) segments.push(current);
      current = [];
    } else current.push(sample);
  }
  if (current.length) segments.push(current);
  const selected =
    hovered == null
      ? valid[valid.length - 1]
      : valid[Math.min(hovered, valid.length - 1)];
  const last = valid[valid.length - 1];
  const ticks = [0, 0.5, 1];
  return (
    <div
      className="sensor-plot"
      ref={container}
      style={{ "--plot-color": color } as CSSProperties}
    >
      <div className="plot-heading">
        <div className="plot-label">
          <span className="legend-dot" />
          {title} <span>{unit}</span>
        </div>
        <div className="plot-reading">
          {measurement(selected[metric])}
          <small>{unit}</small>
          <span>{formatTime(selected.createdAt)}</span>
        </div>
      </div>
      <svg
        className="plot-svg"
        style={{ height }}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`${title}: ${valid.length} mẫu, mới nhất ${measurement(last[metric])} ${unit}`}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity=".16" />
            <stop offset="100%" stopColor={color} stopOpacity=".01" />
          </linearGradient>
        </defs>
        {ticks.map((tick) => {
          const value = min + (max - min) * tick;
          return (
            <g key={tick}>
              <line
                x1={left}
                x2={width - right}
                y1={y(value)}
                y2={y(value)}
                stroke="#e8ece9"
                strokeDasharray="3 5"
              />
              <text
                x={left - 8}
                y={y(value) + 4}
                textAnchor="end"
                className="axis-label"
              >
                {value.toFixed(1)}
              </text>
            </g>
          );
        })}
        {segments.map((segment) => {
          const line = segment
            .map(
              (point, i) =>
                `${i ? "L" : "M"}${x(point).toFixed(2)},${y(point[metric] as number).toFixed(2)}`,
            )
            .join(" ");
          return (
            <g key={segment[0].id}>
              <path
                d={`${line} L${x(segment[segment.length - 1])},${height - bottom} L${x(segment[0])},${height - bottom} Z`}
                fill={`url(#${gradientId})`}
              />
              <path
                d={line}
                fill="none"
                stroke={color}
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              {segment.length === 1 ? (
                <circle
                  cx={x(segment[0])}
                  cy={y(segment[0][metric] as number)}
                  r="3"
                  fill={color}
                />
              ) : null}
            </g>
          );
        })}
        {hovered != null ? (
          <line
            x1={x(selected)}
            x2={x(selected)}
            y1={top}
            y2={height - bottom}
            stroke={color}
            strokeDasharray="4 4"
            opacity=".5"
          />
        ) : null}
        <circle
          cx={x(selected)}
          cy={y(selected[metric] as number)}
          r="4"
          fill={color}
          stroke="white"
          strokeWidth="2"
        />
        <text x={left} y={height - 7} className="axis-label">
          {formatTime(samples[0].createdAt)}
        </text>
        <text
          x={width - right}
          y={height - 7}
          textAnchor="end"
          className="axis-label"
        >
          {formatTime(samples[samples.length - 1].createdAt)}
        </text>
        <rect
          x={left}
          y={top}
          width={width - left - right}
          height={height - top - bottom}
          fill="transparent"
          onPointerLeave={() => setHovered(null)}
          onPointerMove={(event) => {
            const bounds =
              event.currentTarget.ownerSVGElement!.getBoundingClientRect();
            const pointerX =
              ((event.clientX - bounds.left) / bounds.width) * width;
            let closest = 0;
            valid.forEach((sample, i) => {
              if (
                Math.abs(x(sample) - pointerX) <
                Math.abs(x(valid[closest]) - pointerX)
              )
                closest = i;
            });
            setHovered(closest);
          }}
        />
      </svg>
      <label className="sr-only" htmlFor={`${gradientId}-sample`}>
        Chọn mẫu {title.toLowerCase()} để xem số đo và thời gian
      </label>
      <input
        id={`${gradientId}-sample`}
        className="sample-range"
        type="range"
        min="0"
        max={valid.length - 1}
        value={hovered ?? valid.length - 1}
        onChange={(event) => setHovered(Number(event.target.value))}
        aria-valuetext={`${measurement(selected[metric])} ${unit}, ${formatTime(selected.createdAt)}`}
      />
    </div>
  );
}

export function TelemetryChart({
  telemetry,
  loading,
  error,
}: {
  telemetry: Telemetry[];
  loading: boolean;
  error?: string;
}) {
  const samples = useMemo(
    () =>
      [...telemetry]
        .slice(0, 100)
        .sort(
          (a, b) =>
            parseUtc(a.createdAt).getTime() - parseUtc(b.createdAt).getTime() ||
            a.id - b.id,
        ),
    [telemetry],
  );
  return (
    <section
      className="panel telemetry-panel"
      id="telemetry"
      aria-labelledby="telemetry-title"
    >
      <div className="panel-heading">
        <div>
          <span className="eyebrow">MÔI TRƯỜNG TRONG PHÒNG</span>
          <h2 id="telemetry-title">Dữ liệu cảm biến</h2>
        </div>
        <span className="subtle-chip">
          <Icon name="chart" size={15} />
          {samples.length} / 100 mẫu
        </span>
      </div>
      <ResourceNotice message={error} />
      {loading ? (
        <div
          className="chart-skeleton skeleton"
          aria-label="Đang tải biểu đồ"
        />
      ) : samples.length === 0 ? (
        <EmptyState
          error={!!error}
          title={error ? "Chưa tải được telemetry" : "Chưa có mẫu đo"}
          detail="Biểu đồ sẽ cập nhật khi thiết bị gửi dữ liệu."
        />
      ) : (
        <div className="plots">
          <SensorPlot
            samples={samples}
            metric="temperature"
            title="Nhiệt độ"
            unit="°C"
            color="#d57d4b"
          />
          <SensorPlot
            samples={samples}
            metric="humidity"
            title="Độ ẩm"
            unit="%"
            color="#428ea3"
          />
        </div>
      )}
      <div className="panel-footer">
        <span>
          <span className="tiny-dot" />
          Dữ liệu từ thiết bị
        </span>
        <span>Giờ Việt Nam · UTC+7</span>
      </div>
    </section>
  );
}
