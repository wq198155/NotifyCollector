"""复刻 RuleBackup（util/RuleBackup.kt）的「分组 -> JSON -> zip -> 解析回分组」逻辑，
验证导出/导入备份的字段完整性与往返一致性。"""
import io, json, zipfile, datetime

ZIP_ENTRY = "groups.json"
JSON_VERSION = 1

def groups_to_json(groups):
    root = {"app": "NotifyCollector", "version": JSON_VERSION, "count": len(groups)}
    arr = []
    for g in groups:
        arr.append({
            "name": g["name"], "matchType": g["matchType"], "pattern": g["pattern"],
            "codePattern": g.get("codePattern", ""), "expireMinutes": g.get("expireMinutes", 0),
            "sortOrder": g.get("sortOrder", 0), "createdAt": g.get("createdAt", 0),
        })
    root["groups"] = arr
    return json.dumps(root, ensure_ascii=False, indent=2)

def json_to_groups(text):
    root = json.loads(text)
    out = []
    for o in root["groups"]:
        out.append({
            "name": o["name"], "matchType": o["matchType"], "pattern": o["pattern"],
            "codePattern": o.get("codePattern", ""), "expireMinutes": o.get("expireMinutes", 0),
            "sortOrder": o.get("sortOrder", 0), "createdAt": o.get("createdAt", 0),
        })
    return out

def write_zip(buf, groups):
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(ZIP_ENTRY, groups_to_json(groups))

def read_zip(buf):
    with zipfile.ZipFile(buf, "r") as zf:
        names = zf.namelist()
        if ZIP_ENTRY not in names:
            raise RuntimeError(f"备份包内未找到 {ZIP_ENTRY}")
        return json_to_groups(zf.read(ZIP_ENTRY).decode("utf-8"))

# —— 用例 ——
sample = [
    {"name": "快递取件", "matchType": "KEYWORD", "pattern": "取件码", "codePattern": "(\\d{6})",
     "expireMinutes": 5, "sortOrder": 0, "createdAt": 1700000000000},
    {"name": "验证码", "matchType": "REGEX", "pattern": "【(.+)】", "codePattern": "",
     "expireMinutes": 0, "sortOrder": 1, "createdAt": 1700000100000},
    {"name": "纯关键字无码", "matchType": "KEYWORD", "pattern": "账单", "codePattern": "",
     "expireMinutes": 0, "sortOrder": 2, "createdAt": 1700000200000},
]

cases = []
buf = io.BytesIO()
write_zip(buf, sample)
back = read_zip(io.BytesIO(buf.getvalue()))
# 导入时 id 会归零，但这里不涉及 id；仅校验除 id 外的全部字段往返一致
cases.append(("导出->zip->导入 字段往返一致",
              back == sample, f"back={len(back)} sample={len(sample)}"))

# 空分组也能导出/导入
buf2 = io.BytesIO()
write_zip(buf2, [])
back2 = read_zip(io.BytesIO(buf2.getvalue()))
cases.append(("空分组往返", back2 == [] and len(back2) == 0, f"back2={back2}"))

# 坏包（无 groups.json）应抛错
try:
    bad = io.BytesIO()
    with zipfile.ZipFile(bad, "w") as zf:
        zf.writestr("other.txt", "x")
    read_zip(io.BytesIO(bad.getvalue()))
    cases.append(("坏包应报错", False, "未抛错"))
except Exception as e:
    cases.append(("坏包应报错", True, f"{type(e).__name__}"))

# 编码含中文/emoji 也能保留
cn = [{"name": "外卖🍔", "matchType": "KEYWORD", "pattern": "美团", "codePattern": "",
        "expireMinutes": 0, "sortOrder": 0, "createdAt": 1700000300000}]
buf3 = io.BytesIO(); write_zip(buf3, cn)
cn_back = read_zip(io.BytesIO(buf3.getvalue()))
cases.append(("中文/emoji 字段保留", cn_back == cn, f"back={cn_back}"))

ok = True
for name, passed, info in cases:
    print(f"{'PASS' if passed else 'FAIL'} | {name} | {info}")
    ok = ok and passed
print("ALL PASS" if ok else "HAS FAIL")
