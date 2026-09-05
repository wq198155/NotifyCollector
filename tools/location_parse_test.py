# -*- coding: utf-8 -*-
# 扩展压测：覆盖「无起点标记」「无标点」「前缀冗长」等真实短信变体
import re

LOC_START = re.compile(
    "已放置于|放置于|已放置至|放置至|"
    "已送达至|送达至|已到达|到达|已到|已送至|送至|送到|"
    "已放到|放到|已放至|放至|已放置|放置在|放在|已放|放置|寄放|"
    "位于|地址|位置|到|至|处"
)
SENT_END = re.compile("[，,。;；、！!？?\n]|凭|取件码|验证码")
SENT_CHARS = ['，', ',', '。', ';', '；', '、', '！', '!', '？', '?', '\n']
ADDR_ANCHOR = re.compile(r"\d+号|室|栋|楼|单元|层|驿站|自提点|自提柜|丰巢|菜鸟|超市|便利店|门面|门市|店")
STRONG_ADDR = re.compile(r"\d+号|室|栋|楼|单元|层|驿站|自提|丰巢|菜鸟|超市|便利店|门面|门市|店")
TAIL_NOISE = re.compile("(?:取件码|取件|领取|领件|领取件|签收|自提|代收|代取|请|领|取|拿)+$")
PREFIX_NOISE = re.compile(r"^(?:您的快件|您的包裹|您的快递|您的|快件|包裹|快递)+")
HEAD_NOISE = ['的', '了', '达', '至', '在', '到', '于', '由', '已', '：', ':', ' ']


def hasCJK(s):
    return any('\u4e00' <= c <= '\u9fff' for c in s)


def clean(raw):
    s = raw.strip()
    s = TAIL_NOISE.sub("", s).strip()
    if s.startswith("（") and not s.endswith("）"):
        s = s[1:]
    if s.endswith("）") and "（" not in s:
        s = s[:-1]
    if s.startswith("(") and not s.endswith(")"):
        s = s[1:]
    if s.endswith(")") and "(" not in s:
        s = s[:-1]
    s = s.strip()
    while s and s[0] in HEAD_NOISE:
        s = s[1:]
    s = s.strip()
    s = PREFIX_NOISE.sub("", s).strip()
    while s and s[0] in HEAD_NOISE:
        s = s[1:]
    s = s.strip()
    if len(s) < 2 or not hasCJK(s):
        return None
    if not STRONG_ADDR.search(s):
        return None
    return s


def take_until_end(text, frm):
    if frm >= len(text):
        return None
    rest = text[frm:]
    m = SENT_END.search(rest)
    raw = rest if m is None else rest[:m.start()]
    return clean(raw)


def last_index_any(s, chars):
    idx = -1
    for c in chars:
        p = s.rfind(c)
        if p > idx:
            idx = p
    return idx


def extract(text):
    st = LOC_START.search(text)
    if st:
        r = take_until_end(text, st.end())
        if r:
            return r
    a = ADDR_ANCHOR.search(text)
    if not a:
        return None
    head = text[:a.start()]
    loc_starts = [m.end() for m in LOC_START.finditer(head)]
    begin = max(last_index_any(head, SENT_CHARS) + 1, loc_starts[-1] if loc_starts else 0)
    return take_until_end(text, begin)


CASES = [
    # ---- 已验证通过的基础用例（回归保护）----
    ("【驿小哥】您的快递中通，已到春江新城一期鸿觉坊8栋101室顺丰，凭15-3-0250免费取，如有疑问可到店咨询，谢谢；",
     "春江新城一期鸿觉坊8栋101室顺丰"),
    ("【中通快递】您的快递已到达春江新城驿站，请凭A123取件", "春江新城驿站"),
    ("您的包裹已到菜鸟驿站，取件码8-2-3011", "菜鸟驿站"),
    ("请到春江新城鸿觉坊8栋101室取件，取件码1234", "春江新城鸿觉坊8栋101室"),
    ("春江新城鸿觉坊8栋101室，取件码1234", "春江新城鸿觉坊8栋101室"),
    ("【韵达】包裹已到达南京市雨花台区春江新城一期鸿觉坊8栋101室顺丰便利店，凭2-1-3088取",
     "南京市雨花台区春江新城一期鸿觉坊8栋101室顺丰便利店"),
    ("【极兔】您的快递已送至春江新城一期鸿觉坊8栋101室菜鸟驿站，请凭3-2-1108领取",
     "春江新城一期鸿觉坊8栋101室菜鸟驿站"),
    ("【顺丰】您的快件已送达至丰巢柜（春江新城店），取件码5566", "丰巢柜（春江新城店）"),
    ("您的快递已放到楼下超市，请及时领取", "楼下超市"),

    # ---- 新增严苛变体 ----
    # 1. 起点标记后紧跟逗号（应回退到反向定位）
    ("您的快递已到达，请到春江新城鸿觉坊8栋101室顺丰便利店取件，取件码15-3-0250",
     "春江新城鸿觉坊8栋101室顺丰便利店"),
    # 2. 整句无标点，靠「凭」截断，结尾有「请」
    ("快递已到南京市雨花台区铁心桥街道春江新城一期鸿觉坊8栋101室顺丰便利店请凭码取件",
     "南京市雨花台区铁心桥街道春江新城一期鸿觉坊8栋101室顺丰便利店"),
    # 3. 「请于…内到…取件」
    ("请于24小时内到春江新城一期鸿觉坊8栋101室顺丰取件，逾期将退回",
     "春江新城一期鸿觉坊8栋101室顺丰"),
    # 4. 「放置于」——起点词未覆盖「放置于」
    ("您的京东快递已放置于丰巢快递柜，取件码6-1-2088", "丰巢快递柜"),
    # 5. 无「到/至」起点，靠锚点反向定位，前缀冗长
    ("您的快递已由丰巢柜代收，取件码3-4-5566", "丰巢柜"),
    # 6. 空格代替标点
    ("快递已到春江新城一期鸿觉坊8栋101室顺丰 凭15-3-0250免费取",
     "春江新城一期鸿觉坊8栋101室顺丰"),
    # 7. 句号分隔
    ("您的快递已到春江新城一期鸿觉坊8栋101室顺丰。凭15-3-0250免费取",
     "春江新城一期鸿觉坊8栋101室顺丰"),
    # 8. 短信末尾带换行
    ("【韵达】您的快递已送至春江新城菜鸟驿站\n取件码2-3-4056",
     "春江新城菜鸟驿站"),

    # ---- 负样本：不应抓出地址 ----
    ("您的快递已签收，感谢使用中通快递", None),
    ("短信验证码123456，用于登录，请勿泄露", None),
    ("【招商银行】您尾号1234的账户于9月5日消费人民币100.00元", None),
    ("抖音支付开通快捷支付功能", None),
]

ok = 0
fails = []
for text, want in CASES:
    got = extract(text)
    good = (got == want)
    ok += good
    if not good:
        fails.append((text, got, want))
    print(("[PASS]" if good else "[FAIL]"), repr(got), "| want:", repr(want))

print("\n%d/%d PASS" % (ok, len(CASES)))
if fails:
    print("\n--- 失败明细 ---")
    for t, g, w in fails:
        print("  输入:", t[:50])
        print("  实得:", repr(g), " 期望:", repr(w))
