"""
复刻 NotifyCollector 的写入去重逻辑，验证「当天相同取件码只抓一条」。
逻辑与 Repository.insertNotification 一一对应：
  1) 60s 内同 包名+标题+正文 -> 重复
  2) 当天(本地时区0点起)同分组内已存在相同 extractedCode -> 重复（本次新增）
"""
import sqlite3
from datetime import datetime

def day_start_millis(ts_ms: int) -> int:
    """对应 Repository.dayStartMillis：本地时区当天 0 点"""
    dt = datetime.fromtimestamp(ts_ms / 1000)          # 本地时区
    start = dt.replace(hour=0, minute=0, second=0, microsecond=0)
    return int(start.timestamp() * 1000)

def exists_recent(cur, group_id, pkg, title, text, since):
    cur.execute(
        "SELECT COUNT(*) FROM notifications "
        "WHERE groupId=? AND packageName=? AND title=? AND text=? AND postTime>?",
        (group_id, pkg, title, text, since),
    )
    return cur.fetchone()[0]

def exists_same_code_today(cur, group_id, code, day_start):
    cur.execute(
        "SELECT COUNT(*) FROM notifications "
        "WHERE groupId=? AND extractedCode=? AND postTime>=?",
        (group_id, code, day_start),
    )
    return cur.fetchone()[0]

def insert(cur, n: dict) -> int:
    """返回插入后的 id；判定为重复则返回 -1。对应 Repository.insertNotification。"""
    since = n["postTime"] - 60_000
    if exists_recent(cur, n["groupId"], n["packageName"], n["title"], n["text"], since) > 0:
        return -1
    code = n.get("extractedCode")
    if code:
        day_start = day_start_millis(n["postTime"])
        if exists_same_code_today(cur, n["groupId"], code, day_start) > 0:
            return -1
    cur.execute(
        "INSERT INTO notifications(groupId,packageName,title,text,postTime,extractedCode,read) "
        "VALUES (?,?,?,?,?,?,0)",
        (n["groupId"], n["packageName"], n["title"], n["text"], n["postTime"], code),
    )
    return cur.lastrowid

def ts(y, m, d, hh, mm, ss=0):
    return int(datetime(y, m, d, hh, mm, ss).timestamp() * 1000)

# ---- 建内存库，schema 同设备 notifications 表 ----
con = sqlite3.connect(":memory:")
con.execute(
    "CREATE TABLE notifications("
    "id INTEGER PRIMARY KEY AUTOINCREMENT, groupId INTEGER, packageName TEXT, "
    "appName TEXT, title TEXT, text TEXT, postTime INTEGER, "
    "extractedCode TEXT, read INTEGER)"
)
cur = con.cursor()

CASE_GROUP = 3          # 取件码分组
OTHER_GROUP = 1         # 验证码分组（用于跨分组互不影响）
BASE = ts(2026, 9, 5, 10, 0, 0)   # 当天 10:00
SAME_DAY_LATER = ts(2026, 9, 5, 10, 5, 0)   # 当天 10:05（同码、不同软件）
NEXT_DAY = ts(2026, 9, 6, 10, 0, 0)          # 次日（新的一天）
CODE = "15-3-0250"

def scenario(name, n, expect):
    rid = insert(cur, n)
    status = "PASS" if (rid == -1) == expect else "FAIL"
    print(f"[{status}] {name}: insert返回={rid} (期望重复={expect})")

print("=== 当天相同取件码去重 用例 ===")
# 1) 快递App 首条 -> 入库
scenario("① 快递App 首条取件码(入库)", {
    "groupId": CASE_GROUP, "packageName": "com.kuaidi.app", "title": "取件通知",
    "text": "您的取件码 15-3-0250", "postTime": BASE, "extractedCode": CODE,
}, expect=False)

# 2) 短信App 同码同天 -> 跳过（核心规则）
scenario("② 短信App 同码同天(跳过)", {
    "groupId": CASE_GROUP, "packageName": "com.android.mms", "title": "【中通】取件",
    "text": "凭15-3-0250免费取", "postTime": SAME_DAY_LATER, "extractedCode": CODE,
}, expect=True)

# 3) 微信 同码同天 -> 跳过
scenario("③ 微信 同码同天(跳过)", {
    "groupId": CASE_GROUP, "packageName": "com.tencent.mm", "title": "取件提醒",
    "text": "取件码15-3-0250", "postTime": SAME_DAY_LATER, "extractedCode": CODE,
}, expect=True)

# 4) 次日 同码 -> 重新入库（新一天）
scenario("④ 次日同码(重新入库)", {
    "groupId": CASE_GROUP, "packageName": "com.kuaidi.app", "title": "取件通知",
    "text": "您的取件码 15-3-0250", "postTime": NEXT_DAY, "extractedCode": CODE,
}, expect=False)

# 5) 同天 不同码 -> 入库
scenario("⑤ 同天不同码(入库)", {
    "groupId": CASE_GROUP, "packageName": "com.kuaidi.app", "title": "取件通知",
    "text": "您的取件码 12-1-8888", "postTime": SAME_DAY_LATER, "extractedCode": "12-1-8888",
}, expect=False)

# 6) 跨分组 同码 -> 入库（验证码分组不受影响）
scenario("⑥ 跨分组同码(入库)", {
    "groupId": OTHER_GROUP, "packageName": "com.kuaidi.app", "title": "验证码",
    "text": "15-3-0250", "postTime": SAME_DAY_LATER, "extractedCode": CODE,
}, expect=False)

# 7) 无码物流提醒 -> 只走60s去重，不走同码规则（此处间隔>60s 入库）
scenario("⑦ 无码物流提醒(入库)", {
    "groupId": CASE_GROUP, "packageName": "com.kuaidi.app", "title": "物流更新",
    "text": "您的包裹已揽收", "postTime": SAME_DAY_LATER,
}, expect=False)

print("\n=== 当前库内记录数 ===")
cur.execute("SELECT groupId, packageName, extractedCode, postTime FROM notifications ORDER BY id")
for row in cur.fetchall():
    print(row)
cur.execute("SELECT COUNT(*) FROM notifications")
print("总条数:", cur.fetchone()[0])
