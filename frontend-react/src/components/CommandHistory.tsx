import type { Command } from "../types";
import { formatDateTime } from "../lib/time";
import { EmptyState, ResourceNotice, Status } from "./Status";
import { Icon } from "./Icon";

export function CommandHistory({
  commands,
  loading,
  error,
}: {
  commands: Command[];
  loading: boolean;
  error?: string;
}) {
  return (
    <section
      className="panel history-panel"
      id="commands"
      aria-labelledby="commands-title"
    >
      <div className="panel-heading">
        <div>
          <span className="eyebrow">NHẬT KÝ HOẠT ĐỘNG</span>
          <h2 id="commands-title">Lịch sử điều khiển</h2>
        </div>
        <span className="subtle-chip">
          <Icon name="history" size={15} />
          {commands.length} lệnh gần nhất
        </span>
      </div>
      <ResourceNotice message={error} />
      {loading ? (
        <div
          className="table-skeleton skeleton"
          aria-label="Đang tải lịch sử lệnh"
        />
      ) : commands.length === 0 ? (
        <EmptyState
          error={!!error}
          title={
            error ? "Chưa tải được lịch sử lệnh" : "Chưa có lệnh điều khiển"
          }
          detail="Các lệnh fan và light sẽ xuất hiện ở đây sau khi gửi."
        />
      ) : (
        <div
          className="table-scroll"
          tabIndex={0}
          role="region"
          aria-label="Bảng lịch sử lệnh, có thể cuộn ngang"
        >
          <table>
            <caption className="sr-only">
              Lịch sử các lệnh gửi tới thiết bị, mới nhất trước
            </caption>
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Thiết bị / Lệnh</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Thời gian tạo</th>
                <th scope="col">Thời gian thực thi</th>
              </tr>
            </thead>
            <tbody>
              {commands.map((command) => (
                <tr key={command.id}>
                  <td className="mono muted">#{command.id}</td>
                  <td>
                    <span className="table-actuator">
                      <Icon
                        name={command.deviceType === "fan" ? "fan" : "light"}
                        size={17}
                      />
                      {command.deviceType === "fan"
                        ? "Fan"
                        : command.deviceType === "light"
                          ? "Light"
                          : command.deviceType}
                      <span className="action-label">{command.action}</span>
                    </span>
                  </td>
                  <td>
                    <Status value={command.status} />
                  </td>
                  <td className="table-time">
                    {formatDateTime(command.createdAt)}
                  </td>
                  <td className="table-time muted">
                    {command.executedAt ? (
                      formatDateTime(command.executedAt)
                    ) : (
                      <span title="Chưa có thời gian thực thi được xác nhận">
                        —
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="panel-footer">
        <span>
          SENT: broker đã nhận lệnh; xem trạng thái để xác nhận thiết bị.
        </span>
      </div>
    </section>
  );
}
