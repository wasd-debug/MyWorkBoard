#!/usr/bin/env python3
"""把滴答清单（dida-cli）的习惯打卡记录导入本项目。

数据来源：dida-cli 的 `habit list --json` 与 `habit checkins --json`。
写入通道：本项目对外的 `POST /api/tasks/sync/push`，与前端同一套同步协议，
因此 revision / oplog / 幂等语义都由服务端负责，不直接改库。

幂等：每条打卡的 opId 由 (习惯 publicId, 日期) 决定，重复执行时服务端返回
DUPLICATE，不会产生脏数据。entityId 用 uuid5 派生，同样稳定。

示例：
  # 只看计划，不写库
  APP_ACCESS_TOKEN=xxx python3 import-dida-habit-checkins.py --dry-run

  # 真正导入（跳过滴答里的「未完成」记录）
  APP_ACCESS_TOKEN=xxx python3 import-dida-habit-checkins.py --missed skip
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone

DIDA_DEFAULT = os.environ.get(
    "DIDA_BIN",
    "/Users/jianshengnan/.workbuddy/binaries/node/versions/22.22.2-6/bin/dida",
)
TZ_SHANGHAI = timezone(timedelta(hours=8))

# 滴答打卡 status：2=完成，1=未完成（当天结束时自动补记），0=未知
STATUS_DONE = 2
STATUS_MISS = 1


def log(msg: str) -> None:
    print(msg, flush=True)


def run_dida(dida: str, args: list[str]) -> object:
    env = dict(os.environ)
    env.setdefault("PATH", os.path.dirname(dida) + ":" + env.get("PATH", ""))
    completed = subprocess.run(
        [dida, *args], capture_output=True, text=True, env=env, timeout=180
    )
    if completed.returncode != 0:
        raise SystemExit(f"dida 调用失败：{' '.join(args)}\n{completed.stderr[:2000]}")
    return json.loads(completed.stdout)


def fetch_dida_habits(dida: str) -> list[dict]:
    return run_dida(dida, ["habit", "list", "--json"])  # type: ignore[return-value]


def fetch_dida_checkins(dida: str, habit_ids: list[str], start: str, end: str) -> dict[str, dict[int, dict]]:
    """按习惯聚合打卡，key 为 20261005 形式的整数日期戳。

    CLI 返回的是按年切块的数组，且同一年可能出现多块，所以按 stamp 去重合并。
    """
    if not habit_ids:
        return {}
    payload = run_dida(
        dida,
        ["habit", "checkins", "--habits", ",".join(habit_ids), "--from", start, "--to", end, "--json"],
    )
    merged: dict[str, dict[int, dict]] = defaultdict(dict)
    for chunk in payload:  # type: ignore[union-attr]
        habit_id = chunk.get("habitId")
        for item in chunk.get("checkins") or []:
            merged[habit_id][int(item["stamp"])] = item
    return merged


def api_request(base_url: str, token: str, method: str, path: str, body: object | None = None):
    url = base_url.rstrip("/") + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:1000]
        raise SystemExit(f"{method} {path} 失败：HTTP {error.code} {detail}")
    return json.loads(raw)


def stamp_to_date(stamp: int) -> date:
    return date(stamp // 10000, stamp // 100 % 100, stamp % 100)


def checkin_uuid(habit_public_id: str, day: date) -> str:
    """由 (习惯, 日期) 派生稳定 UUID，重复导入不会换 id。"""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"salary-sync/dida/habit-checkin/{habit_public_id}/{day.isoformat()}"))


def weekday_days(dida_habit: dict) -> list[int]:
    """把滴答的 RRULE 转成本项目的 customDays（1=周一 ... 7=周日）。"""
    rule = (dida_habit.get("repeatRule") or "").upper()
    if "FREQ=DAILY" in rule:
        return []
    if "BYDAY=" not in rule:
        return []
    mapping = {"MO": 1, "TU": 2, "WE": 3, "TH": 4, "FR": 5, "SA": 6, "SU": 7}
    raw = rule.split("BYDAY=")[1].split(";")[0]
    days = [mapping[token.strip()] for token in raw.split(",") if token.strip() in mapping]
    return sorted(days)


def build_plan(dida_habits: list[dict], dida_checkins: dict[str, dict[int, dict]],
               app_habits: list[dict], only_names: set[str], missed_mode: str,
               create_missing: bool) -> tuple[list[dict], list[dict]]:
    """返回 (习惯写入操作, 打卡写入操作)。"""
    by_name: dict[str, list[dict]] = defaultdict(list)
    for habit in app_habits:
        by_name[habit["name"]].append(habit)

    habit_ops: list[dict] = []
    checkin_ops: list[dict] = []
    report: list[dict] = []

    for dida_habit in dida_habits:
        name = dida_habit["name"]
        if only_names and name not in only_names:
            continue
        records = dida_checkins.get(dida_habit["id"], {})
        if not records:
            report.append({"name": name, "note": "滴答侧无打卡记录", "habitId": None})
            continue

        stamps = sorted(records)
        done = [s for s in stamps if records[s].get("status") == STATUS_DONE]
        miss = [s for s in stamps if records[s].get("status") == STATUS_MISS]
        unknown = [s for s in stamps if records[s].get("status") not in (STATUS_DONE, STATUS_MISS)]

        chosen = list(done)
        if missed_mode == "import":
            chosen += miss
        chosen.sort()
        if not chosen:
            report.append({"name": name, "note": "无极可导入记录", "habitId": None})
            continue

        app_habit = by_name.get(name, [None])[0]
        first_day = stamp_to_date(chosen[0])
        if app_habit is None:
            if not create_missing:
                report.append({"name": name, "note": "本项目没有同名习惯（--create-missing 可自动建）", "habitId": None})
                continue
            public_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"salary-sync/dida/habit/{dida_habit['id']}"))
            created_at = (dida_habit.get("createdTime") or "")[:10] or first_day.isoformat()
            days = weekday_days(dida_habit)
            habit_ops.append({
                "opId": f"dida-habit:{dida_habit['id']}",
                "entityType": "habit",
                "entityId": public_id,
                "operation": "UPSERT",
                "baseRevision": 0,
                "payload": {"id": public_id, "extra": {
                    "name": name,
                    "icon": "check",
                    "color": dida_habit.get("color") or "#2f746f",
                    "frequency": "CUSTOM" if days else "DAILY",
                    "targetCount": 1,
                    "customDays": days,
                    "startDate": min(first_day.isoformat(), created_at),
                    "groupKey": "other",
                    "checkMode": "AUTO",
                }},
            })
            app_habit = {"publicId": public_id, "name": name, "startDate": min(first_day.isoformat(), created_at)}
        elif first_day.isoformat() < app_habit["startDate"]:
            habit_ops.append({
                "opId": f"dida-habit-start:{app_habit['publicId']}:{first_day.isoformat()}",
                "entityType": "habit",
                "entityId": app_habit["publicId"],
                "operation": "UPSERT",
                "baseRevision": app_habit["revision"],
                "payload": {"id": app_habit["publicId"], "extra": {"startDate": first_day.isoformat()}},
            })

        for stamp in chosen:
            day = stamp_to_date(stamp)
            # startDate 可能在上面的习惯操作里被前移，这里以服务端最终值为准
            status = "DONE" if records[stamp].get("status") == STATUS_DONE else "MISS"
            checkin_ops.append({
                "opId": f"dida:{app_habit['publicId']}:{day.isoformat()}",
                "entityType": "habit-checkin",
                "entityId": checkin_uuid(app_habit["publicId"], day),
                "operation": "UPSERT",
                "baseRevision": 0,
                "payload": {"id": checkin_uuid(app_habit["publicId"], day), "extra": {
                    "habitId": app_habit["publicId"],
                    "date": day.isoformat(),
                    "count": 1 if status == "DONE" else 0,
                    "status": status,
                    "amount": 0,
                    "mood": "",
                    "note": "",
                    "skipReason": "",
                }},
            })

        report.append({
            "name": name,
            "habitId": app_habit["publicId"],
            "startDate": app_habit["startDate"],
            "newStartDate": min(first_day.isoformat(), app_habit["startDate"]),
            "done": len(done),
            "miss": len(miss if missed_mode == "import" else []),
            "ignoredMiss": len(miss if missed_mode == "skip" else []),
            "unknown": len(unknown),
            "range": f"{chosen[0]}-{chosen[-1]}",
        })

    return habit_ops, checkin_ops, report


def push_batches(base_url: str, token: str, operations: list[dict], batch: int) -> dict[str, int]:
    tally: dict[str, int] = defaultdict(int)
    messages: dict[str, str] = {}
    for index in range(0, len(operations), batch):
        chunk = operations[index:index + batch]
        response = api_request(base_url, token, "POST", "/api/v1/tasks/sync/push", chunk)
        data = response.get("data") or {}
        for result in data.get("results") or []:
            status = result.get("status") or "UNKNOWN"
            tally[status] += 1
            if status not in ("APPLIED", "DUPLICATE"):
                key = f"{status}: {result.get('message') or ''}"
                messages[key] = messages.get(key, 0) + 1
        done = min(index + batch, len(operations))
        log(f"  已推送 {done}/{len(operations)}")
    if messages:
        log("  非 APPLIED 结果：")
        for key, count in sorted(messages.items(), key=lambda item: -item[1])[:10]:
            log(f"    {count:5d} × {key}")
    return tally


def main() -> int:
    parser = argparse.ArgumentParser(description="导入滴答清单习惯打卡到本项目")
    parser.add_argument("--base-url", default=os.environ.get("APP_BASE_URL", "http://jsn1024.cn"))
    parser.add_argument("--token", default=os.environ.get("APP_ACCESS_TOKEN", ""))
    parser.add_argument("--dida", default=DIDA_DEFAULT)
    parser.add_argument("--from", dest="start", default="20200101")
    parser.add_argument("--to", dest="end", default=datetime.now(TZ_SHANGHAI).strftime("%Y%m%d"))
    parser.add_argument("--missed", choices=["import", "skip"], default="import",
                        help="滴答里的「未完成」记录是否一起导入为 MISS")
    parser.add_argument("--habits", default="", help="只处理指定习惯名，逗号分隔")
    parser.add_argument("--create-missing", action="store_true", help="本项目缺同名习惯时自动创建")
    parser.add_argument("--dry-run", action="store_true", help="只打印计划，不写库")
    parser.add_argument("--batch", type=int, default=200)
    args = parser.parse_args()

    if not args.token and not args.dry_run:
        raise SystemExit("缺少 --token（或环境变量 APP_ACCESS_TOKEN）")

    only_names = {item.strip() for item in args.habits.split(",") if item.strip()}

    log("1/4 读取滴答清单习惯…")
    dida_habits = fetch_dida_habits(args.dida)
    log(f"    滴答习惯 {len(dida_habits)} 个")

    log("2/4 拉取滴答打卡记录…")
    dida_checkins = fetch_dida_checkins(args.dida, [h["id"] for h in dida_habits], args.start, args.end)
    log(f"    覆盖 {len(dida_checkins)} 个习惯，共 {sum(len(v) for v in dida_checkins.values())} 条记录")

    log("3/4 读取本项目习惯…")
    app_habits = []
    if args.token:
        # archived 参数是「只看归档」而不是「包含归档」，两个都要拉
        for flag in ("false", "true"):
            response = api_request(args.base_url, args.token, "GET", f"/api/v1/tasks/habits?archived={flag}")
            app_habits.extend(response.get("data") or [])
    else:
        app_habits = []
    log(f"    本项目习惯 {len(app_habits)} 个")

    habit_ops, checkin_ops, report = build_plan(
        dida_habits, dida_checkins, app_habits, only_names, args.missed, args.create_missing
    )

    log("\n导入计划")
    log(f"{'习惯':<8}{'完成':>6}{'未完成':>7}{'忽略':>6}{'起始日期':>13}  目标习惯")
    for row in report:
        if not row.get("habitId"):
            log(f"{row['name']:<8}{'-':>6}{'-':>7}{'-':>6}{'-':>13}  {row['note']}")
            continue
        log(f"{row['name']:<8}{row['done']:>6}{row['miss']:>7}{row['ignoredMiss']:>6}"
            f"{row['newStartDate']:>13}  {row['habitId']}")
    log(f"\n习惯操作 {len(habit_ops)} 条（改开始日期/建习惯），打卡操作 {len(checkin_ops)} 条")

    if args.dry_run:
        log("\n--dry-run：未写入任何数据。")
        return 0

    log("\n4/4 写入…")
    if habit_ops:
        log(f"  先写习惯 {len(habit_ops)} 条")
        push_batches(args.base_url, args.token, habit_ops, 50)
    tally = push_batches(args.base_url, args.token, checkin_ops, args.batch)
    log("\n结果：" + "，".join(f"{key}={value}" for key, value in sorted(tally.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
