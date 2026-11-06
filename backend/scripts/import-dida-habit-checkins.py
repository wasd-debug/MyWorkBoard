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

  # 滴答里是「每日重复任务」而不是习惯的（如写日记），按已完成任务反推打卡
  APP_ACCESS_TOKEN=xxx python3 import-dida-habit-checkins.py --task-habits 写日记

注意：滴答 Open API 只返回未归档的习惯；习惯在滴答里处于归档状态时，
`habit list` 看不到它，需要先在滴答里恢复该习惯。
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


def checkin_op(habit_public_id: str, day: date, status: str, prefix: str) -> dict:
    entity_id = checkin_uuid(habit_public_id, day)
    return {
        "opId": f"{prefix}:{habit_public_id}:{day.isoformat()}",
        "entityType": "habit-checkin",
        "entityId": entity_id,
        "operation": "UPSERT",
        "baseRevision": 0,
        "payload": {"id": entity_id, "extra": {
            "habitId": habit_public_id,
            "date": day.isoformat(),
            "count": 1 if status == "DONE" else 0,
            "status": status,
            "amount": 0,
            "mood": "",
            "note": "",
            "skipReason": "",
        }},
    }


def append_start_op(habit_ops: list[dict], app_habit: dict, first_day: date) -> None:
    """补卡不能早于习惯开始日期，所以先把 startDate 前移到最早一条记录。"""
    if first_day.isoformat() >= app_habit["startDate"]:
        return
    habit_ops.append({
        "opId": f"dida-habit-start:{app_habit['publicId']}:{first_day.isoformat()}",
        "entityType": "habit",
        "entityId": app_habit["publicId"],
        "operation": "UPSERT",
        "baseRevision": app_habit["revision"],
        "payload": {"id": app_habit["publicId"], "extra": {"startDate": first_day.isoformat()}},
    })
    app_habit["startDate"] = first_day.isoformat()


def parse_dida_time(value: str) -> date:
    return datetime.strptime(value[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc) \
        .astimezone(TZ_SHANGHAI).date()


def fetch_completed_tasks(dida: str, project_ids: list[str], start: str, end: str, cap: int = 200) -> list[dict]:
    """已完成任务接口每次最多 200 条，命中上限就把时间窗对半切开递归取，避免漏项。

    滴答对查询有 300 次 / 5 分钟的限流，细分查询很容易打满，所以每个窗口的结果
    落在 ~/.cache/salary-sync/dida 里复用；只有「没触顶」的窗口才落缓存。
    """
    import hashlib
    from pathlib import Path

    cache_dir = Path(os.environ.get("DIDA_CACHE_DIR", os.path.expanduser("~/.cache/salary-sync/dida")))
    cache_dir.mkdir(parents=True, exist_ok=True)
    scope = ",".join(project_ids)

    def query(lo: str, hi: str) -> list[dict]:
        key = hashlib.sha1(f"{scope}|{lo}|{hi}".encode()).hexdigest()
        cached = cache_dir / f"{key}.json"
        if cached.exists():
            try:
                return json.loads(cached.read_text("utf-8"))
            except json.JSONDecodeError:
                cached.unlink(missing_ok=True)
        result = subprocess.run(
            [dida, "task", "completed", "--projects", scope,
             "--start-date", lo, "--end-date", hi, "--json"],
            capture_output=True, text=True, timeout=180,
        )
        if result.returncode != 0:
            raise SystemExit(f"dida 拉取已完成任务失败：{result.stderr.strip()[:400]}")
        try:
            rows = json.loads(result.stdout)
        except json.JSONDecodeError:
            return []
        if len(rows) < cap:
            cached.write_text(json.dumps(rows, ensure_ascii=False), "utf-8")
        return rows

    def walk(lo: str, hi: str) -> list[dict]:
        rows = query(lo, hi)
        lo_dt = datetime.fromisoformat(lo.replace("Z", "+00:00"))
        hi_dt = datetime.fromisoformat(hi.replace("Z", "+00:00"))
        if len(rows) < cap or (hi_dt - lo_dt) <= timedelta(days=1):
            return rows
        mid = lo_dt + (hi_dt - lo_dt) / 2
        fmt = "%Y-%m-%dT%H:%M:%SZ"
        return walk(lo, mid.strftime(fmt)) + walk(mid.strftime(fmt), hi)

    return walk(start, end)


def task_dates_by_title(dida: str, titles: set[str], start: str, end: str) -> dict[str, set[date]]:
    """把「已完成任务」当打卡：同一标题的重复任务，按完成时间（东八区）落到天。"""
    projects = run_dida(dida, ["project", "list", "--json"])
    project_ids = [item["id"] for item in projects]  # type: ignore[union-attr]
    if not project_ids or not titles:
        return {}
    rows = fetch_completed_tasks(dida, project_ids, start, end)
    result: dict[str, set[date]] = defaultdict(set)
    for task in rows:
        title = (task.get("title") or "").strip()
        if title not in titles:
            continue
        completed = task.get("completedTime")
        if not completed:
            continue
        result[title].add(parse_dida_time(completed))
    return result


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
               create_missing: bool, task_dates: dict[str, set[date]] | None = None) -> tuple[list[dict], list[dict]]:
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
        else:
            append_start_op(habit_ops, app_habit, first_day)

        for stamp in chosen:
            day = stamp_to_date(stamp)
            status = "DONE" if records[stamp].get("status") == STATUS_DONE else "MISS"
            checkin_ops.append(checkin_op(app_habit["publicId"], day, status, "dida"))

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

    # 滴答里当成「每日重复任务」而不是习惯的，按完成时间反推打卡
    for name, days in (task_dates or {}).items():
        if not days:
            continue
        app_habit = by_name.get(name, [None])[0]
        if app_habit is None:
            report.append({"name": name, "note": "本项目没有同名习惯，无法归入任务来源", "habitId": None})
            continue
        ordered = sorted(days)
        append_start_op(habit_ops, app_habit, ordered[0])
        for day in ordered:
            checkin_ops.append(checkin_op(app_habit["publicId"], day, "DONE", "dida-task"))
        report.append({
            "name": f"{name}(任务)",
            "habitId": app_habit["publicId"],
            "startDate": app_habit["startDate"],
            "newStartDate": min(ordered[0].isoformat(), app_habit["startDate"]),
            "done": len(ordered),
            "miss": 0,
            "ignoredMiss": 0,
            "unknown": 0,
            "range": f"{ordered[0].isoformat()}-{ordered[-1].isoformat()}",
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
    parser.add_argument("--task-habits", default="",
                        help="把滴答里同名的「已完成任务」当打卡，逗号分隔（如 写日记）")
    parser.add_argument("--create-missing", action="store_true", help="本项目缺同名习惯时自动创建")
    parser.add_argument("--dry-run", action="store_true", help="只打印计划，不写库")
    parser.add_argument("--batch", type=int, default=200)
    args = parser.parse_args()

    if not args.token and not args.dry_run:
        raise SystemExit("缺少 --token（或环境变量 APP_ACCESS_TOKEN）")

    only_names = {item.strip() for item in args.habits.split(",") if item.strip()}
    task_names = {item.strip() for item in args.task_habits.split(",") if item.strip()}

    log("1/4 读取滴答清单习惯…")
    dida_habits = fetch_dida_habits(args.dida)
    log(f"    滴答习惯 {len(dida_habits)} 个")

    log("2/4 拉取滴答打卡记录…")
    dida_checkins = fetch_dida_checkins(args.dida, [h["id"] for h in dida_habits], args.start, args.end)
    log(f"    覆盖 {len(dida_checkins)} 个习惯，共 {sum(len(v) for v in dida_checkins.values())} 条记录")

    task_dates: dict[str, set[date]] = {}
    if task_names:
        log(f"    另外按「已完成任务」反推：{', '.join(sorted(task_names))}")
        task_dates = task_dates_by_title(
            args.dida, task_names,
            f"{args.start[:4]}-{args.start[4:6]}-{args.start[6:]}T00:00:00Z",
            f"{args.end[:4]}-{args.end[4:6]}-{args.end[6:]}T00:00:00Z",
        )
        for name in sorted(task_names):
            log(f"      {name}: {len(task_dates.get(name, set()))} 天")

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
        dida_habits, dida_checkins, app_habits, only_names, args.missed, args.create_missing, task_dates
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
