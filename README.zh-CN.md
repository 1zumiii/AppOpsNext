# AppOpsNext

[English](README.md) | **简体中文**

AppOpsNext 是一款面向 Android 15+、基于
[Shizuku](https://shizuku.rikka.app/) 的现代 AppOps 管理工具，采用
clean-room 方式独立实现。

AppOpsNext 用于读取和修改 Android 系统内置的 AppOps 状态。

> [!IMPORTANT]
> 1.0.0 版本目前以一台运行 Android 15（API 35）的 ASUS AI2302
> 为开发和验证设备，暂未承诺兼容其他 Android 版本或厂商 ROM。

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
- 创建可复用的权限模板，并自定义模式和作用域
- 添加、移除模板规则，通过长按拖动持久调整顺序
- 给单个应用套用模板，或批量给多个应用套用模板
- 在单个应用内批量修改多个权限
- 通过结果弹窗完整报告每一项批量操作的成功或失败
- 支持跟随系统、简体中文和英文

## 运行要求

- Android 15（API 35）
- Shizuku 13 或更高版本
- 非 Root 设备需要通过 ADB 或无线调试启动 Shizuku

AppOpsNext 使用 Shizuku 提供的 shell 身份。如果设备重启后 Shizuku
没有运行，或者用户撤销了授权，特权读取和修改功能将不可用，直到重新建立连接。

## 安装

1. 安装并启动 Shizuku。
2. 从 [GitHub Releases](https://github.com/1zumiii/AppOpsNext/releases)
   下载 `AppOpsNext-v1.0.0.apk`。
3. 安装 APK，并在 Shizuku 请求时授权 AppOpsNext。
4. 打开目标应用，确认权限的应用包/UID 作用域，再执行修改。

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

UID 作用域的修改可能影响共用同一 UID 的多个应用，确认界面会在写入前显示作用范围。
批量操作会按顺序执行，并为每个目标保留独立结果。

## 开发

项目需要 JDK 17 和 Android SDK，主要测试环境是通过 USB 调试连接的
Android 15 实体设备。

构建 Debug 应用：

```shell
./gradlew :app:assembleDebug
```

运行本地验证：

```shell
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
- `shizuku`：授权、Binder 生命周期和特权 UserService
- `apps`：应用发现和纯函数过滤
- `settings`：类型化 Preferences DataStore 设置
- `templates`：版本化模板持久化和排序

详细维护约束参见[架构说明](docs/ARCHITECTURE.md)，实体设备行为记录参见
[Android 15 设备验证结果](docs/DEVICE_FINDINGS.md)。发布 APK 前请同时遵循
[发布检查清单](docs/RELEASE.md)。
