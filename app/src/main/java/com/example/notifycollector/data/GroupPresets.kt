package com.example.notifycollector.data

/**
 * 常用分组模板：一键创建，省去手输正则。
 * 用户可在此基础上再编辑微调。
 */
data class PresetGroup(
    val name: String,
    val matchType: String,
    val pattern: String,
    val codePattern: String = "",
    val expireMinutes: Int = 0,
    /** 列表是否以卡片形式展示（取件码等需要突出取件码/公司/地点的分组为 true） */
    val cardView: Boolean = false,
    /** 模板列表里展示的一句话说明 */
    val desc: String
)

object GroupPresets {
    val all: List<PresetGroup> = listOf(
        PresetGroup(
            name = "验证码",
            matchType = MatchType.REGEX,
            pattern = "验证码|动态密码|校验码|短信验证码|verification code",
            // 必须"跟在关键词后面"取数字，否则正文里的年份/金额会被误当成验证码
            // （实测：【顺丰】2026年订单验证码8821 → 旧写法会提取成 2026）
            codePattern = "(?:验证码|动态密码|校验码|code)[^0-9]{0,12}?([0-9]{4,8})",
            expireMinutes = 5,
            desc = "提取 4-8 位数字，5 分钟后自动置灰"
        ),
        PresetGroup(
            name = "取件码",
            matchType = MatchType.REGEX,
            // 不再逐个枚举驿站品牌（兔喜/驿小寻/驿小哥…），品牌太多且一直在变。
            // 改为更通用的判定：信息里出现「快递公司名」(顺丰/中通/圆通/韵达/极兔/申通/京东/德邦…)
            //   或「快递 / 物流 / 取件 / 包裹 / 驿站 / 自提 / 柜」等物流关键字 即可命中。
            pattern = "顺丰|中通|圆通|韵达|极兔|申通|邮政|EMS|京东|德邦|百世|天天|汇通|优速|全峰|宅急送|中铁|安能|快递|物流|取件|包裹|驿站|丰巢|菜鸟|自提|柜",
            // 取件号两档锚定提取：
            //  ① 带短线优先：紧跟「取件码/凭/码/包裹…」等关键词，主体为「数字 + (连字符/破折号 + 数字){1~2}」。
            //     允许关键词与码之间最多 20 个非数字（跨过地址/门牌号），因为带短线的数字几乎一定是取件码而非门牌。
            //  ② 无短线兜底：仅在取件关键词「紧贴」（≤2 个非数字）后出现、且≥2 位时才认，并排除"室/栋/号/楼"等门牌后缀，
            //     避免把"取件码101室"里的房间号误当取件码。
            //  不做"裸数字兜底"——无取/凭/码语境的纯数字（手机号、运单号、门牌）一律不认，杜绝误抓。
            codePattern = "(?:取件码|取件凭证|取件号|凭码|凭|取件|取货号|码|包裹|快递柜|柜)[^0-9]{0,20}?([0-9]+(?:[-—][0-9]+){1,2})|(?:取件码|取件凭证|取件号|凭|取件|码)[^0-9]{0,2}?([0-9]{2,}(?:[-—][0-9]+){0,2})(?!室|栋|号|楼)",
            expireMinutes = 0,
            cardView = true,
            desc = "含快递公司名(顺丰/中通/圆通/韵达/极兔…)或 快递/物流/取件/包裹 等关键字，且带数字取件号(无短线或 1~2 个短线)即收集"
        ),
        PresetGroup(
            name = "银行动账",
            matchType = MatchType.REGEX,
            pattern = "尾号\\d{4}.*(扣款|支出|转入|入账|消费)|您(的)?账户.*(转入|转出|扣款|消费)",
            codePattern = "",
            expireMinutes = 0,
            desc = "银行卡资金变动提醒"
        ),
        PresetGroup(
            name = "快递物流",
            matchType = MatchType.REGEX,
            pattern = "快递|派送|已签收|运输中|正在派件|已发货",
            codePattern = "",
            expireMinutes = 0,
            desc = "物流状态更新"
        )
    )
}
