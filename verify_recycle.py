"""
复刻回收站核心逻辑（Repository.recycleOld / restoreRecycled + DAO 的 forGroup / groupsWithCount 过滤），
验证「超过 14 天的通知自动进入回收站、已回收的不计入分组、可恢复、可清空」。
与真机 Kotlin 行为逐条对应（SQLite 比较用毫秒时间戳）。
"""

RECYCLE_DAYS = 14
DAY = 24 * 60 * 60 * 1000


def recycle_old(rows, now):
    """对应 notificationDao.recycleOld(cutoff, now)：
    UPDATE ... SET recycledAt=now WHERE recycledAt IS NULL AND postTime < cutoff"""
    cutoff = now - RECYCLE_DAYS * DAY
    moved = 0
    for r in rows:
        if r["recycledAt"] is None and r["postTime"] < cutoff:
            r["recycledAt"] = now
            moved += 1
    return moved


def restore(rows, _id):
    for r in rows:
        if r["id"] == _id:
            r["recycledAt"] = None


def clear_recycled(rows):
    return [r for r in rows if r["recycledAt"] is None]


def for_group(rows, gid):
    """对应 forGroup：仅返回未回收的"""
    return [r for r in rows if r["groupId"] == gid and r["recycledAt"] is None]


def group_count(rows, gid):
    """对应 groupsWithCount 子查询：仅统计未回收"""
    return sum(1 for r in rows if r["groupId"] == gid and r["recycledAt"] is None)


def mk(_id, gid, age_days, recycled=None):
    return {"id": _id, "groupId": gid, "postTime": NOW - age_days * DAY, "recycledAt": recycled}


NOW = 1_000_000_000_000

cases = []
# —— 用例 1：超过 14 天自动进回收站 ——
rows = [
    mk(1, 10, 20),   # 20 天前 -> 应回收
    mk(2, 10, 10),   # 10 天前 -> 在列
    mk(3, 10, 15),   # 15 天前 -> 应回收
    mk(4, 10, 5),    # 5 天前  -> 在列
]
moved = recycle_old(rows, NOW)
cases.append(("超过14天自动回收(20d/15d进,10d/5d留)", moved == 2 and rows[0]["recycledAt"] == NOW and rows[2]["recycledAt"] == NOW and rows[1]["recycledAt"] is None and rows[3]["recycledAt"] is None, f"moved={moved} recycled={[r['id'] for r in rows if r['recycledAt'] is not None]}"))

# —— 用例 2：恰好 14 天（边界，<cutoff 才回收，等于不回收）——
r2 = [mk(1, 1, 14)]
recycle_old(r2, NOW)
cases.append(("恰好14天不回收(>=cutoff 不触发)", r2[0]["recycledAt"] is None, f"recycledAt={r2[0]['recycledAt']}"))

# —— 用例 3：forGroup 排除已回收 ——
cases.append(("forGroup 仅含在列(2条)", len(for_group(rows, 10)) == 2, f"count={len(for_group(rows,10))}"))

# —— 用例 4：groupsWithCount 计数排除已回收 ——
cases.append(("分组计数排除回收站(2)", group_count(rows, 10) == 2, f"count={group_count(rows,10)}"))

# —— 用例 5：恢复后重新出现在 forGroup ——
restore(rows, 1)
cases.append(("恢复后回到在列(3条)", len(for_group(rows, 10)) == 3 and rows[0]["recycledAt"] is None, f"count={len(for_group(rows,10))}"))

# —— 用例 6：清空回收站 ——
# 到此处状态：id1 已恢复(null)、id2 null、id3 已回收(NOW)、id4 null
# 残留回收态仅 id3，故清空后剩余 3 条、且全部未回收
left = clear_recycled(rows)
cases.append(("清空回收站仅留未回收(3条)", len(left) == 3 and all(r["recycledAt"] is None for r in left), f"left={len(left)}"))

# —— 用例 7：已回收再次跑 sweep 不会被重复搬运 ——
r3 = [mk(1, 1, 30, recycled=NOW-1)]  # 已是回收态
m2 = recycle_old(r3, NOW)
cases.append(("已回收不再二次搬运", m2 == 0 and r3[0]["recycledAt"] == NOW - 1, f"moved={m2}"))

print(f"{'用例':40} {'结果':6} 明细")
print("-" * 70)
allpass = True
for name, ok, detail in cases:
    allpass = allpass and ok
    print(f"{name:38} {'PASS' if ok else 'FAIL':6} {detail}")
print("-" * 70)
print("总计:", "ALL PASS" if allpass else "SOME FAILED")
