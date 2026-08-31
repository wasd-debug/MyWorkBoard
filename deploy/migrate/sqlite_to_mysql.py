#!/usr/bin/env python3
"""
旧版 SQLite 数据 → MySQL 初始化 SQL 迁移工具

用法:
    python3 sqlite_to_mysql.py <salary-latest.db> <output.sql>

生成 INSERT 语句（幂等，含 ON DUPLICATE KEY UPDATE），
可直接放入 MySQL 容器的 /docker-entrypoint-initdb.d/ 首次启动时自动导入。
"""
import json
import sqlite3
import sys


def esc(s):
    return str(s).replace("\\", "\\\\").replace("'", "''")


def main(db_path, out_path):
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    lines = [
        "-- 由旧版 SQLite 数据迁移生成（sqlite_to_mysql.py）",
        "-- 源文件: " + db_path,
        "",
    ]

    # settings（单行 JSON）
    row = con.execute("SELECT id, data FROM settings WHERE id=1").fetchone()
    if row:
        data = json.dumps(json.loads(row["data"]), ensure_ascii=False)
        lines.append(
            f"INSERT INTO settings (id, data) VALUES (1, '{esc(data)}') "
            f"ON DUPLICATE KEY UPDATE data = VALUES(data);"
        )

    # records
    recs = con.execute("SELECT date, start, end, rest FROM records ORDER BY date").fetchall()
    for r in recs:
        lines.append(
            f"INSERT INTO records (date, start, end, rest) VALUES "
            f"('{esc(r['date'])}', '{esc(r['start'])}', '{esc(r['end'])}', {int(r['rest'] or 0)}) "
            f"ON DUPLICATE KEY UPDATE start = VALUES(start), end = VALUES(end), rest = VALUES(rest);"
        )

    # kv
    kvs = con.execute("SELECT `key`, value FROM kv").fetchall()
    for r in kvs:
        lines.append(
            f"INSERT INTO kv (`key`, value) VALUES ('{esc(r['key'])}', '{esc(r['value'])}') "
            f"ON DUPLICATE KEY UPDATE value = VALUES(value);"
        )

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"OK: settings={1 if row else 0}, records={len(recs)}, kv={len(kvs)} -> {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
