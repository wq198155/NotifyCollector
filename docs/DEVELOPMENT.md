# 通知收集器（NotifyCollector）

一个**只读、不拦截**的安卓通知收集 App：按你设定的关键字 / 正则表达式把通知分到不同分组，
命中通知进入对应分组列表，点开可看详情；若命中"验证码 / 取件码"，以卡片形式高亮展示。

> 与 DoNotNotify / 通知滤盒的区别：**只收集、不过滤、不屏蔽**任何通知。

---

## 功能

- 监听系统通知（NotificationListenerService），覆盖短信 / 微信 / 快递 / 银行 / 各类 App 通知栏消息
- **常用分组模板**：右上角「模板」一键添加验证码 / 取件码 / 银行动账 / 快递物流，免手输正则
- 自定义分组：名称 + 匹配方式（关键字 或 正则表达式）+ 表达式
- 命中即归档到对应分组，首页显示分组与数量
- 分组内是命中通知列表，点开看完整详情
- 可选"提取码正则"：自动从通知中抠出验证码 / 取件码，详情页以大字卡片展示 + 一键复制
- **有效期（分钟）**：如验证码设 5 分钟，到期后列表与卡片自动置灰并标记"已过期"
- 本地存储（Room），完全离线，**无网络权限、无上传**

## 技术栈

- Kotlin + Jetpack Compose（Material3）
- Room（本地数据库）
- minSdk 24 / targetSdk 34 / AGP 8.5.2 / Kotlin 1.9.24

## 如何构建 / 安装

**方式一：Android Studio（推荐）**
1. 用 Android Studio（Hedgehog 及以上）打开本目录（含 `settings.gradle.kts`）。
2. 等待 Gradle Sync 完成（首次会下载 Gradle 8.9 与依赖）。
3. 连上手机（或启动带 Google APIs 的模拟器），点 ▶ Run，或 `Build → Build Bundle(s) / APK(s) → Build APK`。
4. 生成的 APK 在 `app/build/outputs/apk/debug/`，侧载安装即可。

**方式二：命令行**
```bash
./gradlew assembleDebug      # macOS / Linux
gradlew.bat assembleDebug    # Windows
```

> 注：仓库已带 `gradle-wrapper.properties`，Android Studio 首次打开会自动补全 wrapper。

## 首次使用

1. 打开 App，首页若提示"启用通知监听权限"，点击 → 在系统"通知使用权"里勾选**通知收集器** → 允许。
2. **最快上手**：点右上角「模板」，把"验证码 / 取件码 / 银行动账 / 快递物流"一键添加进来；同名分组不会重复创建。
3. 也可点右下角 **+** 自建分组：
   - 名称：`验证码`
   - 匹配方式：关键字（或正则）
   - 表达式：`验证码|动态密码|verification code`
   - 提取码正则（可选）：`(?:验证码|code)[^0-9]{0,12}?([0-9]{4,8})`  ← 用于卡片展示
   - 有效期（分钟）：`5`（0 表示不过期）
4. 保存后首页出现"验证码"分组。之后任意 App 弹出含"验证码"的通知，都会被收集进来。
5. 点分组 → 看命中列表 → 点某条 → 详情页看到大卡片里的验证码，可一键复制。

## 常用正则示例

| 用途 | 匹配方式 | 表达式 | 提取码正则 | 建议有效期 |
|------|---------|--------|-----------|-----------|
| 验证码 | 正则 | `验证码\|动态密码\|校验码\|verification code` | `(?:验证码\|动态密码\|code)[^0-9]{0,12}?([0-9]{4,8})` | 5 分钟 |
| 取件码 | 正则 | `取件码\|包裹\|驿站\|快递柜\|菜鸟\|丰巢` | `(?:取件码\|凭码\|取货号)[：: ]*([A-Za-z0-9-]{4,12})` | 0 |
| 银行动账 | 正则 | `尾号\d{4}.*(扣款\|支出\|转入\|入账\|消费)` | `尾号(\d{4})` | 0 |
| 快递物流 | 正则 | `快递\|派送\|已签收\|运输中\|正在派件\|已发货` | （留空） | 0 |
| 营销促销 | 关键字 | `闪购\|限时\|coupon` | （留空） | 0 |

> 提示 1：关键字是"包含即命中"；正则更灵活但需写对，保存时会做合法性校验。
>
> 提示 2：**提取码正则一定要带上下文**。若只写 `([0-9]{4,8})`，像
> `【顺丰】2026年订单验证码8821` 会把年份 `2026` 当成验证码（实测踩过）。
> 写成 `(?:验证码|code)[^0-9]{0,12}?([0-9]{4,8})` 才能正确取到 `8821`。

## 已知限制

- 抓不到"不弹通知栏"的内容（App 静默处理、通知渠道被关、系统折叠的收不到）。
- 微信等若未开启"显示通知详情"，正文可能只有"你收到一条新消息"，正则对其无效。
- 通知使用权可随时被系统/用户撤销，撤销后监听失效，App 会提示重新开启。
- 因声明 `QUERY_ALL_PACKAGES`，**不适合上架 Google Play**；自用 / 侧载无妨。

## 排障：系统"通知使用权"里找不到本 App

如果系统的「通知使用权 / 通知访问」列表里没有"通知收集器"，99% 是 `AndroidManifest.xml` 里
监听服务的 intent-filter action 写错了。**必须是**：

```xml
<service android:name=".service.NotifyListenerService"
         android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
         android:exported="true">
    <intent-filter>
        <!-- 注意是这个，不是 android.intent.action.NOTIFICATION_LISTENER_SERVICE -->
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

系统按 `NotificationListenerService.SERVICE_INTERFACE` 枚举监听器，action 名不对就不会被列出。

调试时可用 adb 直接授权（免手动点设置）：

```bash
adb shell cmd notification allow_listener \
  com.example.notifycollector/com.example.notifycollector.service.NotifyListenerService

# 验证是否已绑定
adb shell dumpsys notification | grep notifycollector

# 发一条测试通知（正文要写成单个无空格参数，否则会被 shell 拆成多个参数）
adb shell cmd notification post verify_test 您的验证码为123456，请在5分钟内使用

# 加入电池优化白名单，防止后台被杀漏抓
adb shell dumpsys deviceidle whitelist +com.example.notifycollector
```

## 版本记录

- **v2**（当前）：新增「常用分组模板」一键添加；新增分组"有效期（分钟）"，到期列表/卡片自动置灰；
  修复提取码正则会把年份/金额误当验证码的问题；数据库 v1→v2 平滑迁移（保留已收集数据）。
- **v1**：通知监听 + 关键字/正则分组 + 提取码卡片 + 一键复制 / 删除。

## 隐私

应用声明**无任何网络权限**，所有通知仅存于本机 SQLite，不上传任何服务器。
