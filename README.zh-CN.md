# AppOpsNext

[English](README.md) | **简体中文**

管理 Android AppOps、复用权限模板、查看系统权限历史。
使用 Kotlin 与 Jetpack Compose 构建原生界面，通过
[Shizuku](https://shizuku.rikka.app/) 执行特权操作。

[下载 APK](https://github.com/1zumiii/AppOpsNext/releases/latest) ·
[反馈问题](https://github.com/1zumiii/AppOpsNext/issues) ·
[构建状态](https://github.com/1zumiii/AppOpsNext/actions/workflows/ci.yml)

## 主要功能

| 功能 | 说明 |
| --- | --- |
| 应用列表 | 浏览当前用户的应用，搜索应用名称和包名，按需显示系统应用。 |
| AppOps 管理 | 查看应用包与 UID 作用域的模式，按本地化名称或系统名称搜索权限，修改后回读验证。 |
| 模板与批量操作 | 创建和排序可复用规则，将模板应用到多个应用，或一次修改单个应用的多项权限。 |
| 新装应用管理 | 按需开启自动套用模板，补处理待完成的安装任务，查看保存的逐项执行结果。 |
| 权限历史 | 查看权限使用分布、应用统计和时间线，自选并排序要查看的权限。 |
| 设置与诊断 | 支持简体中文、英文和跟随系统语言，提供连接状态与诊断报告。 |

## 安装与上手

### 运行要求

- **Android 15 或更高版本**（API 35+）。
- **Shizuku 13 或更高版本**，已启动并向 AppOpsNext 授权。
- 内置原生后端面向 **ARM64**，其他 CPU 架构尚未验证。

通过 ADB 或无线调试启动 Shizuku 时，无需 Root。
AppOpsNext 的特权操作仍依赖 Shizuku。

1. 安装并启动 [Shizuku](https://shizuku.rikka.app/)。
2. 从[最新 Release](https://github.com/1zumiii/AppOpsNext/releases/latest)
   下载并安装 `app-release.apk`。
3. 打开 AppOpsNext，在 Shizuku 提示时允许访问。
4. 选择目标应用和权限，阅读确认信息后执行修改。

在**模板**页配置可复用规则，在**历史**页查看系统记录。
新装应用自动套用模板是可选功能，请先配置规则，再启用自动执行。
首次检测会建立已有应用基线，不会追溯地向所有已安装应用套用模板。

更新时直接覆盖安装新的 Release APK，即可保留设置。
如果设备重启后 Shizuku 未运行，或授权被撤销，需要恢复连接后才能重新读取或修改
系统状态；已保存的历史记录仍可查看。

### 已验证环境

| 设备 | 系统 | 验证范围 |
| --- | --- | --- |
| ASUS AI2302 | Android 15 / API 35 | 主要开发与真机测试环境。 |
| Xiaomi 24117RN76G | HyperOS 3 / Android 16 / API 36 | 用户独立验证了原生后端、读取与写入、权限历史以及相机权限限制。 |

以上是已测试环境的结果，不代表所有厂商 ROM 均兼容。
具体证据与已知限制参见[后端兼容性说明](docs/PRIVILEGED_BACKENDS.md)。

## 历史记录如何工作

历史数据来自 Android 保留的系统记录。AppOpsNext 优先显示逐次访问记录；
对于剪贴板等可能不提供逐次记录的权限，则使用系统按时间段汇总的统计。
记录保留时间和时间戳精度由设备决定，它不是一份独立采集的完整审计日志。

- 每项权限最后一次成功读取的结果会保存到本地，重新打开应用时恢复，并显示更新时间。
- **五分钟内**返回页面会复用新鲜结果。较旧或尚未读取的结果，只在历史页面可见、
  应用位于前台且后端已连接时读取；周期刷新也遵循这些条件。
- 手动刷新会跳过缓存新鲜度检查，各项权限读取完成后陆续更新。
  读取失败时保留上次结果，并显示错误提示。
- 首次成功读取之前，页面会区分“尚未读取”和真正的零记录。

本地保存的是最近一次成功读取的缓存，不是永久历史档案。
缓存存放在应用私有目录中，不参与 Android 备份；清除应用数据或卸载应用会将其移除。

## 理解 AppOps 修改

**AppOps 与 Android 运行时权限是两个层级。** 将某个 AppOp 设为“允许”，
无法授予尚未取得的运行时权限。Android 或厂商策略也可能归一化、拒绝所请求的模式。
AppOpsNext 会解释运行时权限相关的失败，并提供前往该应用系统设置的入口。

每次修改都经过验证事务：

```text
读取并检查原始状态
  → 写入请求的模式
  → 回读并验证
  → 失败时尝试恢复原始状态，再验证恢复结果
```

手动修改、批量操作和自动模板共用串行事务队列。
命令执行结束不等于修改成功；应用会分别报告验证与恢复结果。
恢复是失败后的补救尝试，并不保证一定成功。

UID 作用域的修改可能影响共用同一 UID 的多个应用，确认界面会列出这些应用。
自动切换写入作用域也受限制，以免静默扩大影响范围。
批量操作会为每个目标分别报告结果。

## 常见问题

- **无法连接：** 确认 Shizuku 正在运行且 AppOpsNext 已获授权，再到
  **设置 → 连接与诊断**查看详情。应用优先使用内置原生后端，启动失败时自动尝试
  Shizuku UserService。
- **某个模式无法生效：** 检查 Android 运行时权限和回读验证结果。
  部分系统限制无法通过 AppOps 覆盖。
- **历史记录较旧或不完整：** 检查保存时间，尝试手动刷新并确认后端连接。
  可读取哪些记录由 Android 决定。
- **反馈问题：** 请提供设备、Android / ROM 版本、复现步骤，以及设置页中的诊断报告。
  公开提交 Issue 前，请先检查报告并删去不希望公开的信息。

## 从源码构建

准备 **JDK 17**、**Go 1.24+** 和安装了 **Platform 36 的 Android SDK**。
仓库已包含 Gradle Wrapper。将 `JAVA_HOME` 指向 JDK 17，并通过
`local.properties` 中的 `sdk.dir` 或 `ANDROID_HOME` 指定 SDK 路径。

```shell
# 可选：如果 Go 不在 PATH 中，指定其绝对路径。
# export GO_EXECUTABLE="/absolute/path/to/go"

./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。
Gradle 同时会为 Android ARM64 交叉编译内置守护进程，无需单独进行 NDK 构建。
Debug 版本在应用前台时保持屏幕常亮，签名与公开发布的 Release 版本不同。

运行与 [Android CI](.github/workflows/ci.yml) 相同的检查：

```shell
(cd daemon && "${GO_EXECUTABLE:-go}" test ./...)
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

### Release 签名构建

公开发行版的签名材料不会提交到 Git。当前 Release 构建配置要求提供
`.signing/appopsnext-release.keystore`，使用别名 `appopsnext`，并在环境中设置
`APPOPSNEXT_STORE_PASSWORD` 和 `APPOPSNEXT_KEY_PASSWORD`：

```shell
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。
自行分发时请使用自己的密钥库。覆盖更新已有安装必须使用相同的签名身份。
维护者的验证和发布流程参见[发布检查清单](docs/RELEASE.md)。

## 代码与文档

Android 代码位于 `app/src/main/java/dev/izumi/appopsnext/`。

| 路径 | 职责 |
| --- | --- |
| `presentation/` | Compose 界面、ViewModel 和 UI 状态。 |
| `appops/` | 命令、解析、作用域处理与验证写入事务。 |
| `nativebackend/`、`shizuku/` | 特权连接、原生管道和 UserService 回退。 |
| `apps/`、`settings/` | 应用发现、元数据缓存和用户偏好。 |
| `templates/`、`newapps/` | 模板持久化、新装应用检测和可恢复的规则执行。 |
| `history/` | 系统历史解析、刷新调度和本地快照。 |
| `diagnostics/` | 环境与连接报告。 |
| 仓库根目录下的 [`daemon/`](daemon/) | 使用 Go 编写、仅接受白名单命令的守护进程。 |

进一步阅读：[架构说明](docs/ARCHITECTURE.md) ·
[特权后端](docs/PRIVILEGED_BACKENDS.md) ·
[设备验证记录](docs/DEVICE_FINDINGS.md) ·
[发布检查清单](docs/RELEASE.md)。

## 项目背景

AppOpsNext 受旧版 App Ops（`rikka.appops`）的产品思路和使用流程启发，
以 clean-room 方式独立实现。它不是旧版应用的 fork、移植版、修改版或官方续作。

项目未包含旧版应用的源码、反编译代码、资源、品牌素材或配置数据，
不依赖旧版应用，也不提供其配置迁移功能。
AppOpsNext 与 RikkaApps 及旧版 App Ops 作者不存在开发、背书或支持关系；
使用 Shizuku 也不代表与其维护者存在从属关系。
名称中的“AppOps”指 Android 内置的系统服务。
