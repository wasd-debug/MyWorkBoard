# MCP 连接指南

> 状态：Phase 3C-1（2026-09-27）
> 范围：只读 Streamable HTTP、Personal Access Token、账本/工时 scope

## 1. 当前能力

网站在同一 Spring Boot 应用内提供 MCP Streamable HTTP：

```text
POST /mcp
```

本地开发地址：

```text
http://127.0.0.1:5173/mcp
```

Docker/Nginx 地址：

```text
http://127.0.0.1/mcp
```

生产环境使用网站公开 HTTPS 域名，例如：

```text
https://work.example.com/mcp
```

首批仅支持只读工具：

```text
worktime.settings.get
worktime.records.search
ledger.books.list
ledger.overview
ledger.transactions.search
ledger.transaction.history
ledger.reports.summary
ledger.budgets.list
```

当前不向 MCP 暴露任何 `prepare`、`commit`、R4 审批、同步 push/pull 或数据库级接口。

## 2. 创建 PAT

1. 登录网站。
2. 打开“设置 → 外部 Agent / MCP”。
3. 填写 Token 名称和有效期。
4. 选择至少一个 scope：
   - `mcp:ledger:read`
   - `mcp:worktime:read`
5. 可选指定账本范围；不选择表示允许访问当前用户本来就有权读取的全部账本。
6. 点击“创建 Token”，立即复制完整 Token。

完整 Token 只返回一次，格式类似 `wbt_...`。数据库只保存 SHA-256 hash；忘记后不能找回，只能撤销并重新创建。默认有效期 90 天，最长 366 天。

## 3. 客户端配置

外部 MCP Host 的界面和配置字段名称可能不同，但核心参数一致：

```json
{
  "transport": "streamable-http",
  "url": "https://work.example.com/mcp",
  "headers": {
    "Authorization": "Bearer wbt_替换为完整PAT"
  }
}
```

安全要求：

- 不把 PAT 写入仓库、聊天记录、截图或前端源码。
- 优先放入客户端密钥存储或环境变量，再在客户端配置中引用。
- 为不同客户端分别创建 Token，便于独立撤销和审计。
- 默认只授予必要的只读 scope；账本 Token 尽量限制到需要的账本。
- 生产环境必须使用 HTTPS。

## 4. 协议联调

以下示例用环境变量保存 Token，避免出现在命令历史正文中：

```bash
export WORKBOARD_MCP_TOKEN='wbt_替换为完整PAT'
```

初始化：

```bash
curl -i \
  -H "Authorization: Bearer $WORKBOARD_MCP_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"initialize",
    "params":{
      "protocolVersion":"2025-06-18",
      "capabilities":{},
      "clientInfo":{"name":"local-check","version":"1.0.0"}
    }
  }' \
  https://work.example.com/mcp
```

服务端会在响应头返回 `Mcp-Session-Id`。后续 `tools/list` 和 `tools/call` 请求必须同时携带 PAT 与该 session ID。

预期行为：

- 双 read scope：`tools/list` 返回 8 个只读工具。
- 仅 `mcp:worktime:read`：只返回 2 个工时工具。
- 无效、过期或撤销 Token：HTTP 401。
- 请求未授权账本：工具结果为 denied/failed，不返回业务数据。
- 单次日期跨度超过 366 天：返回参数错误。
- 单 Token 每分钟超过 120 次请求：HTTP 429，并带 `Retry-After: 60`。

## 5. Codex、WorkBuddy 与 Inspector 验收

每个客户端都要记录：客户端名称和版本、传输类型、URL、认证头配置方式、initialize 协议版本、可见工具列表、成功调用工具、撤销后的行为和已知限制。

最低验收流程：

1. 使用独立 PAT 连接。
2. 确认只显示 scope 对应工具。
3. 调用 `ledger.books.list` 或 `worktime.settings.get`。
4. 调用一个需要参数的查询工具并核对当前用户数据隔离。
5. 在网站撤销 PAT。
6. 再次调用，确认立即失败且没有返回缓存业务数据。

Phase 3C-1 已完成 curl 级 initialize、tools/list、tools/call、单 scope 过滤、无效 Token 和撤销 Token 联调。MCP Inspector、Codex 与 WorkBuddy 的正式版本兼容记录仍是本阶段剩余验收项。

## 6. 故障排查

### 401 invalid_token

- 确认使用 `Authorization: Bearer <PAT>`，不是网站 JWT。
- 确认完整 Token 没有多余空格或换行。
- 在设置页检查是否已撤销或到期。
- 重新创建独立 PAT，不要尝试从数据库恢复原文。

### 404

- 检查应用开关 `app.mcp.enabled`。
- 本地 Vite 与部署 Nginx 都必须代理 `/mcp`。
- 客户端 URL 必须以 `/mcp` 结尾，不能写成 `/api/mcp`。

### tools/list 缺少工具

- 检查 PAT scope；工具目录按 scope 在服务端生成。
- 账本 read 与工时 read 相互独立。
- 当前阶段故意不暴露写工具。

### tools/call 返回 denied

- 检查 PAT 的账本范围。
- 检查 Token 所属用户是否仍是账本成员。
- 即使 Token 允许该账本，领域服务仍会重新执行当前权限校验。

### 连接中断或超时

- 检查 Nginx `/mcp` 是否关闭代理缓冲并使用 HTTP/1.1。
- 检查客户端是否同时发送 `application/json, text/event-stream`。
- 检查后端日志与 `mcp_tool_call` 审计状态；审计不保存 PAT 原文或完整敏感内容。
