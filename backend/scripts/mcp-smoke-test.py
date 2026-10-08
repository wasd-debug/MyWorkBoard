#!/usr/bin/env python3
"""Read-only MCP Streamable HTTP smoke test.

Required environment variables:
  WORKBOARD_MCP_TOKEN  A PAT or OAuth access token.
Optional:
  WORKBOARD_MCP_URL    Defaults to http://127.0.0.1:8080/mcp.
  MCP_BOOK_ID           An authorized book public id for ledger resource checks.
"""

import json
import os
import sys
import urllib.error
import urllib.request


def fail(message):
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def decode_response(raw):
    text = raw.decode("utf-8")
    data_lines = [line[5:].strip() for line in text.splitlines() if line.startswith("data:")]
    return json.loads(data_lines[-1] if data_lines else text)


class McpClient:
    def __init__(self, url, token):
        self.url = url
        self.token = token
        self.session_id = None
        self.request_id = 0

    def call(self, method, params=None, notification=False):
        self.request_id += 1
        payload = {"jsonrpc": "2.0", "method": method}
        if not notification:
            payload["id"] = self.request_id
        if params is not None:
            payload["params"] = params
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "MCP-Protocol-Version": "2025-06-18",
        }
        if self.session_id:
            headers["Mcp-Session-Id"] = self.session_id
        request = urllib.request.Request(self.url, data=json.dumps(payload).encode(), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.headers.get("Mcp-Session-Id"):
                    self.session_id = response.headers["Mcp-Session-Id"]
                if notification:
                    return None
                result = decode_response(response.read())
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            fail(f"{method} returned HTTP {error.code}: {body[:240]}")
        except urllib.error.URLError as error:
            fail(f"{method} connection failed: {error.reason}")
        if "error" in result:
            fail(f"{method} returned JSON-RPC error: {result['error']}")
        return result.get("result", {})


def require(condition, message):
    if not condition:
        fail(message)


def check_text_tool_result(client, name, arguments):
    result = client.call("tools/call", {"name": name, "arguments": arguments})
    require(not result.get("isError"), f"{name} failed")
    text_items = [item.get("text", "") for item in result.get("content", []) if item.get("type") == "text"]
    require(bool(text_items), f"{name} did not return text")
    try:
        summary, raw_json = text_items[0].split("\n", 1)
        payload = json.loads(raw_json)
    except (ValueError, TypeError):
        fail(f"{name} text did not contain a complete JSON result")
    require(payload == result.get("structuredContent"), f"{name} text and structured results differ")
    require(summary == payload.get("summary"), f"{name} summary differs")
    items = payload.get("structuredContent")
    require(isinstance(items, list), f"{name} did not return a list")
    require(all(item.get("id") and item.get("name") for item in items), f"{name} resource id/name missing")
    if name == "ledger.category.list":
        require(all(item.get("kind") for item in items), "category kind missing")
    print(f"{name}: text/structured JSON match, {len(items)} resources with id/name")


def main():
    url = os.environ.get("WORKBOARD_MCP_URL", "http://127.0.0.1:8080/mcp")
    token = os.environ.get("WORKBOARD_MCP_TOKEN")
    if not token:
        fail("WORKBOARD_MCP_TOKEN is required")
    client = McpClient(url, token)

    initialize = client.call("initialize", {
        "protocolVersion": "2025-06-18",
        "capabilities": {"resources": {}, "prompts": {}},
        "clientInfo": {"name": "salary-sync-smoke-test", "version": "1.0"},
    })
    capabilities = initialize.get("capabilities", {})
    require("resources" in capabilities, "initialize did not advertise resources")
    require("prompts" in capabilities, "initialize did not advertise prompts")
    client.call("notifications/initialized", notification=True)

    resources = client.call("resources/list").get("resources", [])
    templates = client.call("resources/templates/list").get("resourceTemplates", [])
    prompts = client.call("prompts/list").get("prompts", [])
    require(len(resources) >= 3, f"expected help resources, got {len(resources)}")
    require(len(templates) >= 1, "expected at least one resource template")
    require(len(prompts) >= 1, "expected at least one prompt")

    resource_uris = {item.get("uri") for item in resources}
    client.call("resources/read", {"uri": "workbench://help/mcp"})
    require("workbench://help/mcp" in resource_uris, "help resource missing")

    client.call("prompts/get", {
        "name": "worktime-makeup",
        "arguments": {"from": "2026-09-01", "to": "2026-09-29"},
    })

    book_id = os.environ.get("MCP_BOOK_ID")
    if book_id:
        require("workbench://ledger/books" in resource_uris, "ledger scope does not expose ledger books")
        check_text_tool_result(client, "ledger.books.list", {})
        check_text_tool_result(client, "ledger.account.list", {"bookId": book_id})
        check_text_tool_result(client, "ledger.category.list", {"bookId": book_id})
        client.call("resources/read", {"uri": "workbench://ledger/books"})
        client.call("resources/read", {"uri": f"workbench://ledger/{book_id}/overview"})
        client.call("prompts/get", {
            "name": "ledger-summary",
            "arguments": {"bookId": book_id},
        })

    print(f"MCP smoke test passed: url={url}, resources={len(resources)}, templates={len(templates)}, prompts={len(prompts)}")


if __name__ == "__main__":
    main()
