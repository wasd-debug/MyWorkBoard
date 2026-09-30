# MCP 连接指南

> 状态：Phase 3C-7（2026-09-29）
> 范围：Streamable HTTP、PAT、OAuth 2.1 Authorization Code + PKCE、账本/工时 read/prepare/commit scope、外部 action、Resources、Prompts、站内确认、R2 单次提交、客户端诊断与运维审计

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

只读工具：

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

当服务端开启 `app.mcp.write-enabled` 且 PAT 具备对应 prepare scope 时，额外暴露：

```text
worktime.record.create.prepare
worktime.record.update.prepare
worktime.record.delete.prepare
ledger.transaction.create.prepare
ledger.transaction.update.prepare
ledger.transaction.delete.prepare

agent.action.get
agent.actions.list
agent.action.cancel
```

prepare 只生成冻结参数、预览和待确认 action，不写业务数据。PAT 同时具备对应 commit scope 时还会暴露：

```text
agent.action.commit
```

服务端不直接暴露任何领域 `*.commit`。`agent.action.commit` 当前只允许 `ledger.transaction.create.prepare` 和 `worktime.record.create.prepare` 两个 R2 action；修改、删除等 R3 及全部 R4 操作仍拒绝外部提交。同步 push/pull、数据库级接口和内部维护接口不作为 MCP Tool。

只读资源与提示模板：

```text
resources/list
resources/templates/list
resources/read
prompts/list
prompts/get
```

首批静态资源为 `workbench://help/mcp`、`workbench://help/tools` 和 `workbench://help/scopes`；账本 read scope 额外提供 `workbench://ledger/books`，工时 read scope 额外提供 `workbench://worktime/settings`。动态模板包括 `workbench://ledger/{bookId}/overview`、`workbench://ledger/{bookId}/reports/{period}` 和 `workbench://worktime/records/{from}/{to}`。提示模板包括 `monthly-review`、`ledger-summary` 和 `worktime-makeup`。

资源读取沿用 PAT/OAuth 用户、scope、账本范围、领域权限和 366 天日期跨度校验；不会返回未授权账本、数据库实体全集、凭据或完整审计参数。Prompts 只生成给 MCP Host 的 PromptMessage，不自动执行查询写入；需要写入时仍必须调用 prepare、站内确认和 commit。动态业务资源读取会写入脱敏的 `resources/read` MCP 审计记录。

认证方式：

- PAT：适合本地开发、脚本和不支持 OAuth 的兼容客户端。
- OAuth 2.1：适合 MCP Inspector、Codex、WorkBuddy 等正式远程客户端；支持 Authorization Code、PKCE S256、动态客户端注册、refresh token 轮换和用户撤销授权。

OAuth discovery：

```text
GET /.well-known/oauth-protected-resource
GET /.well-known/oauth-protected-resource/mcp
GET /.well-known/oauth-authorization-server
GET /.well-known/openid-configuration
```

协议端点：

```text
POST /oauth/register
GET  /oauth/authorize
POST /oauth/token
POST /oauth/revoke
```

## 2. OAuth 2.1 + PKCE

### 2.1 服务端配置

生产环境至少配置：

```text
APP_MCP_ENABLED=true
APP_MCP_OAUTH_ENABLED=true
APP_PUBLIC_BASE_URL=https://work.example.com
```

`APP_PUBLIC_BASE_URL` 必须是客户端实际访问的 HTTPS 根地址，不带结尾 `/`。issuer、resource metadata、authorize/token/register/revoke 和 `WWW-Authenticate` 都以此地址为准。关闭 `APP_MCP_OAUTH_ENABLED` 后 discovery 和 OAuth API 返回 404，已经签发的 OAuth access token 也无法访问 `/mcp`；PAT 继续按自身开关工作。

### 2.2 动态客户端注册

客户端可向 `/oauth/register` 提交：

```json
{
  "client_name": "Codex",
  "redirect_uris": ["http://127.0.0.1:1455/callback"],
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none"
}
```

首版只支持无客户端密钥的公共客户端。redirect URI 必须是 HTTPS、环回 HTTP 或安全自定义 scheme；授权时必须与登记值精确相等，不能使用前缀或通配匹配。动态注册按来源 IP 限制为每小时最多 20 次、最多保留 100 个未撤销客户端，超过限制返回 HTTP 429 和标准 OAuth `rate_limited` 错误。

### 2.3 授权与 Token 生命周期

authorize 请求必须包含 `response_type=code`、`client_id`、精确 `redirect_uri`、空格分隔的 `scope`、`code_challenge`、`code_challenge_method=S256` 和指向当前站点 `/mcp` 的 `resource`。推荐始终传递并校验 `state`。

服务端先在公共端点校验客户端、redirect URI、scope、PKCE 和 resource，再跳转站内 `/oauth/consent`。浏览器未登录时先显示本站登录界面；登录后授权页从受认证 API 加载用户可访问账本，账本 scope 至少选择一个账本。批准后返回 5 分钟有效、只能使用一次的授权码。

Token 策略：

- access token：`wbo_` 前缀，1 小时有效。
- refresh token：`wbr_` 前缀，30 天有效，每次刷新同时轮换 access 和 refresh。
- authorization code：`wbc_` 前缀，只保存 SHA-256 hash，5 分钟有效且只能消费一次。
- refresh 时 scope 只能保持或缩小，不能扩大。
- 用户在“设置 → 外部 Agent / MCP → OAuth 已授权应用”撤销 grant 后，该 grant 的全部 access/refresh token 立即失效。
- PAT 和 OAuth token 分开列出与撤销；OAuth token 不出现在 PAT 列表。

OAuth token 最终仍通过标准头访问 MCP：

```text
Authorization: Bearer wbo_...
```

工具目录、账本范围、领域权限、限流和审计与 PAT 相同；认证方式不会绕过 R2/R3/R4 风险策略。

### 2.4 诊断、客户端与协议事件

登录网站后，“设置 → 外部 Agent / MCP”使用以下用户隔离接口：

```text
GET    /api/v1/mcp/diagnostics
GET    /api/v1/mcp/oauth/clients
DELETE /api/v1/mcp/oauth/clients/{clientId}
GET    /api/v1/mcp/events?limit=30
```

- 诊断结果包含 MCP 与 discovery 地址、功能开关、当前用户有效 PAT/授权数、最近 24 小时调用与失败数、支持的协议版本和公开地址告警。
- 未配置 `APP_PUBLIC_BASE_URL`、配置值与当前访问 Origin 不一致，或远程 OAuth 使用 HTTP 时会出现告警；告警不会自动修改配置。
- 客户端列表显示注册时间、授权时间、最近使用时间、最近 User-Agent、scope 和账本范围。
- “断开客户端”只撤销当前用户授予该客户端的 grant、关联 access/refresh token 和未使用授权码；不会全局删除动态客户端，也不会影响其他用户对同一客户端的授权。
- 协议事件最多返回 100 条，只展示当前用户事件；IP 在响应中脱敏，Token 原文、密钥和完整业务数据不进入事件详情。
- 每天北京时间 03:35 清理过期或已消费超过 1 天的授权码、超过 90 天的协议事件，并清除已撤销或过期超过 30 天的 OAuth 凭据 hash。

## 3. 创建 PAT

1. 登录网站。
2. 打开“设置 → 外部 Agent / MCP”。
3. 填写 Token 名称和有效期。
4. 选择至少一个 scope：
   - `mcp:ledger:read`
   - `mcp:worktime:read`
   - `mcp:ledger:prepare`
   - `mcp:worktime:prepare`
   - `mcp:ledger:commit`
   - `mcp:worktime:commit`
   commit scope 必须与同领域 prepare scope 同时授予。
5. 可选指定账本范围；不选择表示允许访问当前用户本来就有权读取的全部账本。
6. 点击“创建 Token”，立即复制完整 Token。

完整 Token 只返回一次，格式类似 `wbt_...`。数据库只保存 SHA-256 hash；忘记后不能找回，只能撤销并重新创建。默认有效期 90 天，最长 366 天。

不再使用的 PAT 先点击“撤销”，使外部请求立即失效；已撤销条目随后显示“删除”，确认后可从凭据列表永久移除。有效 PAT、其他用户 PAT 和 OAuth token 不能通过永久删除接口移除。删除 PAT 不删除既有工具调用或协议事件审计，相关审计的凭据引用会置空并继续保留其他脱敏字段。

权限模板：只读（READ_ONLY）、可准备（PREPARE）、可提交（COMMIT）和完整工作台（FULL_WORKSPACE）。完整工作台会暴露当前用户有权限的全部非 commit 领域工具，包括账本资源管理、周期任务、回收站、批量流水、导入预览和导出准备；实际写入仍通过 prepare → 站内审批 → `agent.action.commit`，R3/R4 不会绕过审批。每个 Token 还可设置高风险策略（站内审批或禁用）和每分钟 1-600 次限流，账本范围、有效期和撤销继续独立生效。

## 4. PAT 客户端配置

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
- 默认只授予必要的只读 scope；只有需要生成写入预览时才增加 prepare scope，确需外部提交 R2 操作时再增加 commit scope。账本 Token 尽量限制到需要的账本。
- 生产环境必须使用 HTTPS。

## 5. 协议联调

### 5.1 只读 smoke test

仓库提供不依赖第三方库的只读协议脚本。脚本不会创建 Token、批准 action 或修改业务数据；临时 Token 应在设置页创建并在测试后撤销：

```bash
export WORKBOARD_MCP_TOKEN='wbt_替换为完整PAT'
export WORKBOARD_MCP_URL='http://127.0.0.1:8080/mcp'
# 可选：仅在该 Token 确实允许访问时启用账本资源检查
export MCP_BOOK_ID='账本 public id'
python3 backend/scripts/mcp-smoke-test.py
```

验证前端 Vite 代理链路时只替换地址，其他参数保持不变：

```bash
export WORKBOARD_MCP_URL='http://127.0.0.1:5173/mcp'
python3 backend/scripts/mcp-smoke-test.py
```

脚本会检查 `initialize` 的 resources/prompts 能力、`resources/list`、`resources/templates/list`、帮助资源、工时补录 Prompt；设置 `MCP_BOOK_ID` 后额外检查账本资源和摘要 Prompt。脚本不会宣称完成 MCP Inspector、Codex 或 WorkBuddy 的客户端 UI 验收。

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

初始化时可以不带 `MCP-Protocol-Version`，由 initialize 完成版本协商；后续显式发送时当前只接受 `2025-06-18`。显式不支持的版本返回 HTTP 400 `unsupported_protocol_version`，不会误报成认证失败；无效 Token 返回 HTTP 401 并通过 `WWW-Authenticate` 提供 `resource_metadata`，限流返回 HTTP 429。

预期行为：

- 双 read scope：`tools/list` 返回 8 个只读工具。
- 仅 `mcp:worktime:read`：只返回 2 个工时工具。
- `mcp:worktime:read` + `mcp:worktime:prepare`：返回 2 个只读、3 个工时 prepare 和 3 个 action 管理工具。
- 再增加 `mcp:worktime:commit`：额外出现 `agent.action.commit`；仍不得出现领域 `*.commit`。
- 全部 6 个 scope：返回 8 个只读、6 个 prepare 和 4 个 action 工具，共 18 个。
- 无效、过期或撤销 Token：HTTP 401。
- 请求未授权账本：工具结果为 denied/failed，不返回业务数据。
- 单次日期跨度超过 366 天：返回参数错误。
- 单 Token 每分钟超过 120 次请求：HTTP 429，并带 `Retry-After: 60`。

## 6. prepare 与站内确认

外部 Agent 调用 `*.prepare` 后，统一结果包含：

```json
{
  "status": "needs_input|needs_confirmation|conflict|denied|failed",
  "summary": "人类可读说明",
  "structuredContent": {},
  "actionId": "服务端 action ID",
  "confirmationUrl": "需要站内确认时返回",
  "expiresAt": "action 过期时间"
}
```

标准流程：

1. 外部 Agent 调用 prepare；参数不足时根据 `needs_input` 追问后重新 prepare。
2. 返回 `needs_confirmation` 时向用户展示摘要和 `confirmationUrl`。
3. 用户在网站登录；确认页会重新校验当前用户、PAT 状态、action 状态和有效期。
4. 用户批准或拒绝。批准只进入 `APPROVED`，本步骤本身不写业务数据；拒绝进入 `DENIED`。
5. 外部 Agent 使用 `agent.action.get` 或 `agent.actions.list` 查询结果；不应轮询 confirmation token。
6. 对于 R2 action，原 PAT 同时具备对应 commit scope 时，可显式调用 `agent.action.commit`。服务端再次校验用户、具体 PAT、scope、账本范围、action 有效期、权限和领域资源。
7. 首次提交结果会保存到外部 action；重复调用返回首次结果并标记 `replayed=true`，不会产生第二次业务写入。
8. 不再需要的待确认 action 可由创建它的同一 PAT 调用 `agent.action.cancel`。

安全边界：

- action 同时绑定用户和创建它的具体 PAT；同一用户的另一个 PAT 也不能查询或取消。
- confirmation token 只保存 SHA-256 hash，过期或 PAT 撤销后链接立即失效。
- 账本范围在 prepare 解析出真实账本后再次校验；越界 action 会被取消。
- 外部 Agent 传入“用户已确认”字段没有授权效力。
- `APPROVED` 不等于业务写入成功；只有独立 `agent.action.commit` 返回 `completed` 后才算完成。
- commit scope 不会扩大 prepare 能力，且必须和同领域 prepare scope 绑定在同一 PAT 上。
- R3/R4 action 即使已经站内批准，也会被 MCP commit 拒绝。
- 账本新增流水复用现有领域服务并写入 `ledger_sync_oplog`，Web/IndexedDB 可继续通过既有增量同步链刷新。

## 7. Codex、WorkBuddy 与 Inspector 验收

每个客户端都要记录：客户端名称和版本、传输类型、URL、认证头配置方式、initialize 协议版本、可见工具列表、成功调用工具、撤销后的行为和已知限制。

最低验收流程：

1. 优先使用 OAuth 连接；若客户端暂不支持，再使用独立 PAT 记录兼容限制。
2. OAuth 场景确认客户端能发现 resource metadata、完成 DCR/PKCE 回调，并在刷新 access token 后继续连接。
3. 确认只显示 scope 对应工具；无 commit scope 时不显示 `agent.action.commit`，任何情况下都不显示领域 `*.commit`。
4. 调用 `ledger.books.list` 或 `worktime.settings.get`。
5. 调用一个需要参数的查询工具并核对当前用户和账本隔离。
6. 在网站撤销 OAuth grant 或 PAT。
7. 再次调用，确认立即失败且没有返回缓存业务数据。

Phase 3C-6 已完成本地协议联调：临时双 read scope PAT 的 `initialize` 能力包含 resources/prompts；`resources/list` 返回 5 项，`resources/templates/list` 返回 3 项，静态与动态 `resources/read` 均成功；`prompts/list` 返回 3 项，`worktime-makeup` 与有权账本的 `ledger-summary` 均成功，资源读取写入脱敏审计且临时 Token 已清理。后端全量 `mvn test` 为 222 项，220 项通过、2 项按既有规则跳过。根据项目规则，MCP Inspector、Codex、WorkBuddy 的真实 UI 连接、浏览器授权页和视觉检查仍由用户按 [`前端手工检查清单.md`](前端手工检查清单.md) 执行，当前不宣称已自动完成。

Phase 3C-5 已完成服务端兼容与运维自动化验证：显式不支持协议版本返回 400、无效凭据返回 401、限流返回 429；DCR 限流、用户隔离诊断、协议事件、客户端级断开和定时清理均有集成测试。Phase 3C-4 的 DCR、PKCE S256、授权码单次消费、refresh 轮换和授权撤销，以及 Phase 3C-3 的 R2 commit、幂等回放、账本同步 oplog 和 R3 拒绝保持不变。

## 8. 故障排查

### 401 invalid_token

- 确认使用 `Authorization: Bearer <PAT>`，不是网站 JWT。
- 确认完整 Token 没有多余空格或换行。
- 在设置页检查是否已撤销或到期。
- 重新创建独立 PAT，不要尝试从数据库恢复原文。
- OAuth 客户端检查 access token 是否过期、refresh 是否已轮换，或用户是否已经在设置页撤销授权。
- 401 的 `WWW-Authenticate` 包含 `resource_metadata`，支持 OAuth 的客户端应重新发现并发起授权。

### 400 unsupported_protocol_version

- 当前服务端仅支持 MCP `2025-06-18`；升级或调整客户端显式发送的 `MCP-Protocol-Version`。
- initialize 首次协商可以不发送该请求头，不要把协议版本错误当作 Token 失效处理。

### 429 rate_limited

- MCP 单 Token 每分钟最多 120 次调用，按 `Retry-After` 等待后重试。
- 动态注册按来源 IP 每小时最多 20 次，同时最多保留 100 个未撤销客户端；不要在每次连接时重复注册新客户端。

### 404

- 检查应用开关 `app.mcp.enabled`。
- 本地 Vite 与部署 Nginx 都必须代理 `/mcp`。
- 客户端 URL 必须以 `/mcp` 结尾，不能写成 `/api/mcp`。
- OAuth discovery 或协议端点 404 时检查 `APP_MCP_OAUTH_ENABLED=true`，并确认 Nginx/Vite 已代理 `/.well-known/*` 与 `/oauth/authorize|token|register|revoke`。

### invalid_target / redirect_uri 未登记 / PKCE 校验失败

- `resource` 必须与 discovery 返回的 MCP 地址完全一致，包括 scheme、host、port 和 `/mcp`。
- `redirect_uri` 必须与动态注册值精确相等，不能更换 localhost/127.0.0.1、端口或路径。
- 只支持 `code_challenge_method=S256`；verifier 必须为 43-128 个 RFC 7636 合法字符。
- 生产环境确认 `APP_PUBLIC_BASE_URL` 是外部 HTTPS 地址，不是 `http://backend:8080` 等容器内地址。

### tools/list 缺少工具

- 检查 PAT scope；工具目录按 scope 在服务端生成。
- 账本 read 与工时 read 相互独立。
- 检查 `app.mcp.write-enabled` / `APP_MCP_WRITE_ENABLED`；关闭时 prepare 和 action 工具会被隐藏。
- commit scope 必须与对应 prepare scope 同时创建；关闭 `APP_MCP_WRITE_ENABLED` 会同时隐藏 prepare、commit 和 action 工具。

### confirmationUrl 无法打开

- 确认使用创建 PAT 的同一网站账号登录。
- 检查 action 是否超过 15 分钟有效期、已取消或已拒绝。
- 检查 PAT 是否被撤销或过期。
- 本地应通过同源 Nginx/Vite 地址调用 `/mcp`，确保返回的确认链接指向可访问的前端域名。

### tools/call 返回 denied

- 检查 PAT 的账本范围。
- 检查 Token 所属用户是否仍是账本成员。
- 即使 Token 允许该账本，领域服务仍会重新执行当前权限校验。

### 连接中断或超时

- 检查 Nginx `/mcp` 是否关闭代理缓冲并使用 HTTP/1.1。
- 检查客户端是否同时发送 `application/json, text/event-stream`。
- 检查设置页诊断摘要和最近协议事件，再检查后端日志与 `mcp_tool_call` 审计状态；审计不保存 PAT 原文或完整敏感内容。
- 复制诊断报告时仍应在对外发送前复核内容；页面只提供脱敏运维信息，不替代服务端日志授权管理。
