# AppOpsNext

[English](README.md) | **简体中文**

AppOpsNext 是一款面向 Android 15+、基于
[Shizuku](https://shizuku.rikka.app/) 的现代 AppOps 管理工具，采用
clean-room 方式独立实现。

AppOpsNext 用于读取和修改 Android 系统内置的 AppOps 状态。

> [!IMPORTANT]
> Android 15（API 35）的开发和验证设备为 ASUS AI2302；原生后端还经过一位
> 用户在运行 HyperOS 3 / Android 16（API 36）的 Xiaomi 24117RN76G 上独立
> 验证。对这些已测试设备和系统版本之外环境的支持尚不明确。

## 与旧版 App Ops 的关系

AppOpsNext 只是沿用了旧版 App Ops（包名 `rikka.appops`）的产品思路和
部分使用流程，并以 clean-room 方式重新实现相关功能。它不是旧版应用的
fork、移植版、修改版、破解版或官方续作。

- 项目没有使用旧版应用的源码、反编译代码、资源、品牌素材或配置数据。
- AppOpsNext 不会连接旧版应用，不提供配置迁移或互操作，也不要求安装旧版应用。
- AppOpsNext 与 RikkaApps 及旧版 App Ops 原作者不存在开发、维护、授权、
  背书或支持关系。
- AppOpsNext 将 Shizuku 作为独立发布的特权桥接工具使用。这项技术依赖不代表
  AppOpsNext 与 Shizuku 或旧版 App Ops 的维护者存在从属或官方合作关系。

文中出现的第三方项目名称仅用于说明兼容性和项目背景。本项目名称中的
“AppOps”指 Android 系统内置的 AppOps 服务，并非旧版应用专有的接口或技术。

## 功能

- 浏览当前用户的应用，并显示包名、UID 和系统应用信息
- 默认隐藏系统应用，也可以在设置中持久切换
- 按本地化名称或原始系统名称搜索应用和权限
- 分别读取应用包作用域和 UID 作用域的 AppOps
- 修改 AppOps 模式前检查状态是否过期，写入后独立回读验证
- 写入验证失败时自动尝试恢复原始模式
- 只显示当前权限条目的处理进度，不重新加载整个详情页
- 以简洁、本地化的格式显示最近使用时间
- 查看相机、麦克风和位置权限的系统历史，包括汇总图表、分类统计和可跳转至
  应用详情的时间线
- 自行增删要监测的 AppOps，并在历史页面可见、应用位于前台且特权连接可用时自动刷新历史
- 创建可复用的权限模板，自定义模式并自动回退 AppOps 写入作用域
- 添加、移除模板规则，通过长按拖动持久调整顺序
- 给单个应用套用模板，或批量给多个应用套用模板
- 配置不可删除的新装应用默认模板，并按需启用自动套用、补检测和结果通知
- 在单个应用内批量修改多个权限
- 通过结果弹窗完整报告每一项批量操作的成功或失败
- 支持跟随系统、简体中文和英文
- 通过内置原生守护进程执行 AppOps 命令，并保留 Shizuku UserService 作为
  自动回退后端

## 运行要求

- Android 15（API 35）或更高版本
- Shizuku 13 或更高版本
- 非 Root 设备需要通过 ADB 或无线调试启动 Shizuku

Shizuku 以 shell 身份启动 AppOpsNext 内置的原生守护进程，应用随后通过私有
进程管道与其通信，从而避开在部分 Android 16 / HyperOS 设备上可能失效的
UserService 回调路径。如果原生后端启动失败，AppOpsNext 会自动尝试 Shizuku
UserService 后端。设备重启后若 Shizuku 未运行，或用户撤销授权，特权读取和
修改功能将不可用，直到重新建立连接。

## 安装

1. 安装并启动 Shizuku。
2. 从 [GitHub Releases](https://github.com/1zumiii/AppOpsNext/releases)
   下载最新 APK。
3. 安装 APK，并在 Shizuku 请求时授权 AppOpsNext。
4. 打开目标应用，确认需要修改的权限，再执行修改。

Android 运行时权限与 AppOps 是不同层级。AppOps 可以进一步限制已经授予的能力，
但无法授予被 Android 运行时权限或厂商策略拒绝的能力。因此，系统可能会归一化或
拒绝某些模式；遇到回读结果不一致时，AppOpsNext 会报告验证失败，而不会显示为成功。

## 安全机制

每次单项或批量写入都采用相同的有限事务：

```text
读取当前值
  -> 确认当前值没有变化
  -> 写入请求的类型化模式
  -> 回读并验证
  -> 失败时恢复并验证原始值
```

模板会先写入当前应用包；如果 Android 拒绝该作用域，并且原始状态已经安全恢复，
AppOpsNext 只会在 UID 唯一属于目标应用时自动改用 UID 写入。已经明确存在的 UID
记录仍可能影响共用同一系统身份的多个应用，因此确认界面会列出受影响的应用。
批量操作会按顺序执行，并为每个目标保留独立结果。

## 开发

项目需要 JDK 17、Go 1.24 或更高版本以及 Android SDK。Gradle 会通过
`GOOS=android` 和 `CGO_ENABLED=0` 交叉编译内置的 ARM64 守护进程。
主要测试环境是通过 USB 调试连接的 Android 15 实体设备。如果 `go` 不在
`PATH` 中，可以通过 `GO_EXECUTABLE` 指定其路径。

构建 Debug 应用：

```shell
./gradlew :app:assembleDebug
```

运行本地验证：

```shell
(cd daemon && go test ./...)
./gradlew :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug
```

Debug 构建只会在 AppOpsNext 位于前台时保持屏幕常亮。Release 构建不会修改
系统休眠时间。

### Release 签名

Release 密钥库不会提交到 Git。构建 Release 版本需要
`.signing/appopsnext-release.keystore` 和以下环境变量：

```shell
export APPOPSNEXT_STORE_PASSWORD="<密钥库密码>"
export APPOPSNEXT_KEY_PASSWORD="<密钥密码>"
./gradlew :app:assembleRelease
```

请离线备份密钥库和密码。签名密钥丢失后，将无法发布能够覆盖安装现有版本的更新。

## 项目结构

- `presentation`：Compose 界面、状态和可复用 UI
- `appops`：命令适配、解析、仓库和验证写入
- `nativebackend`：原生守护进程启动、私有管道协议和网关
- `shizuku`：授权、进程启动和 UserService 回退
- `daemon`：使用 Go 构建、只执行白名单命令的 ARM64 shell 守护进程
- `apps`：应用发现和纯函数过滤
- `settings`：类型化 Preferences DataStore 设置
- `templates`：版本化模板持久化和排序
- `newapps`：新装应用检测、待处理任务、自动策略执行和结果通知
- `history`：离散 AppOps 历史解析、仓库和统计

详细维护约束参见[架构说明](docs/ARCHITECTURE.md)，后端选择和兼容性证据参见
[特权后端说明](docs/PRIVILEGED_BACKENDS.md)，实体设备行为记录参见
[Android 15 设备验证结果](docs/DEVICE_FINDINGS.md)。发布 APK 前请同时遵循
[发布检查清单](docs/RELEASE.md)。
