# -*- coding: utf-8 -*-
# 离线复现 ParcelText.extractLocation（Kotlin/Java 正则语义与 Python 一致：
# 交替分支按书写顺序取第一个命中，search 取最左匹配）
# 用于验证「地址取到句子结束」的提取逻辑
import re

LOC_START = re.compile(
    "已送达至|送达至|已到达|到达|已到|已送至|送至|送到|"
    "已放到|放到|已放至|放至|已放置|放置在|放在|已放|放置|寄放|"
    "位于|地址|位置|到|至|处"
)
SENT_END = re.compile("[，,。;；、！!？?\n]|凭|取件码|验证码")
SENT_CHARS = ['，', ',', '。', ';', '；', '、', '！', '!', '？', '?', '\n']
ADDR_ANCHOR = re.compile(r"\d+号|室|栋|楼|单元|层|驿站|自提点|自提柜|丰巢|菜鸟|超市|便利店|门面|门市|店")
STRONG_ADDR = re.compile(r"\d+号|室|栋|楼|单元|层|驿站|自提|丰巢|菜鸟|超市|便利店|门面|门市|店")
TAIL_NOISE = re.compile("(?:取件码|取件|领取|领件|领取件|签收|自提|领取|领|取|拿)+$")
HEAD_NOISE = ['的', '了', '达', '至', '在', '到', '：', ':', ' ']


def hasCJK(s):
    return any('\u4e00' <= c <= '\u9fff' for c in s)


def clean(raw):
    s = raw.strip()
    s = TAIL_NOISE.sub("", s).strip()
    # 只在括号不配对时剥掉多余的一半；配对的括号是地址本身的一部分
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
    # 用户实测样本：地址必须取到句子结束（含「顺丰」）
    ("【驿小哥】您的快递中通，已到春江新城一期鸿觉坊8栋101室顺丰，凭15-3-0250免费取，如有疑问可到店咨询，谢谢；",
     "春江新城一期鸿觉坊8栋101室顺丰"),
    ("【中通快递】您的快递已到达春江新城驿站，请凭A123取件", "春江新城驿站"),
    ("您的包裹已到菜鸟驿站，取件码8-2-3011", "菜鸟驿站"),
    ("请到春江新城鸿觉坊8栋101室取件，取件码1234", "春江新城鸿觉坊8栋101室"),
    ("春江新城鸿觉坊8栋101室，取件码1234", "春江新城鸿觉坊8栋101室"),
    ("【顺丰】您的快件已送达至丰巢柜（春江新城店），取件码5566", "丰巢柜（春江新城店）"),
    ("您的快递已放到楼下超市，请及时领取", "楼下超市"),
    ("【韵达】包裹已到达南京市雨花台区春江新城一期鸿觉坊8栋101室顺丰便利店，凭2-1-3088取",
     "南京市雨花台区春江新城一期鸿觉坊8栋101室顺丰便利店"),
    ("【极兔】您的快递已送至春江新城一期鸿觉坊8栋101室菜鸟驿站，请凭3-2-1108领取",
     "春江新城一期鸿觉坊8栋101室菜鸟驿站"),
    # 负样本：不应误抓出地址
    ("抖音支付开通快捷支付功能", None),
    ("您的验证码是123456，5分钟内有效", None),
    ("【招商银行】您尾号1234的账户于9月5日消费人民币100.00元", None),
]

ok = 0
for text, want in CASES:
    got = extract(text)
    good = (got == want)
    ok += good
    print(("[PASS]" if good else "[FAIL]"), repr(got), "| want:", repr(want))
print("\n%d/%d PASS" % (ok, len(CASES)))
