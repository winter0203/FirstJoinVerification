# FirstJoinVerification

一个用于 Paper/Purpur 的首次进入验证码插件，支持 Java 版和通过 Geyser/Floodgate 接入的基岩版玩家。

## 功能

- 玩家首次进入服务器时进入冻结状态，无法走动、跳跃或通过传送离开当前位置；允许转动视角。
- 玩家在聊天栏输入 `/verify <数字验证码>`，验证码正确后立即解锁。
- 验证成功后按玩家 UUID 写入 `plugins/FirstJoinVerification/verified.yml`，以后不再重复验证。
- Java 版和基岩版使用同一条验证命令；安装 Floodgate/Geyser 时会识别基岩版并显示对应提示。
- 可配置错误次数上限、错误后踢出，以及未验证期间的交互限制。
- 管理员可以执行 `/verifyadmin reload` 重载配置。

## 环境要求

本项目按服务器版本 **26.2** 编译，使用 **Java 25** 和 Gradle。

服务器需要安装 Paper 或兼容核心（例如 Purpur）。基岩版接入需要正常工作的 Geyser，建议同时安装 Floodgate。本插件不强制依赖 Geyser/Floodgate，缺少它们时 Java 版功能仍可用。

## 配置

首次启动后会生成 `plugins/FirstJoinVerification/config.yml`：

```yaml
verification:
  code: "123456"
  max-attempts: 5
  kick-on-max-attempts: true
  prompt-interval-seconds: 10

freeze:
  block-interactions: true
  block-inventory: true
  block-damage: true
  block-item-pickup: true
```

把 `verification.code` 改成你要使用的固定数字。请保留引号，例如 `"001234"`，这样开头的 0 不会丢失。验证码不会发送给玩家。 `max-attempts: 0` 表示不限制错误次数。

## 玩家使用方法

未验证玩家在聊天栏/命令输入界面执行：

```text
/verify 123456
```

将示例数字替换成管理员在配置文件中设置的验证码。命令也支持 `/验证` 和 `/验证码` 别名。

## 管理员命令

| 命令 | 说明 |
|---|---|
| `/verifyadmin reload` | 重载配置文件 |

权限：`firstjoinverification.admin`（默认仅 OP）。

## 构建

1. 安装 JDK 25，并确认 `java -version` 显示 25。
2. 安装 Gradle 9.2.1 或兼容版本。
3. 在项目根目录执行：

   ```bash
   gradle build
   ```

4. 构建产物位于 `build/libs/FirstJoinVerification-1.0.0.jar`。

## 安装

1. 将 `FirstJoinVerification-1.0.0.jar` 放入服务器的 `plugins` 目录。
2. 确认 Geyser/Floodgate 已正确安装并能让基岩版玩家进入服务器。
3. 启动服务器，编辑生成的配置文件设置验证码。
4. 执行 `/verifyadmin reload`，或重启服务器使配置生效。

## 数据文件

`plugins/FirstJoinVerification/verified.yml` 保存已验证玩家的 UUID。请不要删除该文件，否则玩家会被要求重新验证。若代理或 Floodgate 账户关联方式变化导致 UUID 改变，玩家会被视为新玩家。

## 实现说明与边界

- 插件不拦截普通聊天，因此玩家可以与管理员沟通；验证码应通过私下渠道告知玩家，不要公开发送。
- 移动限制依靠 Paper 的玩家移动事件取消坐标变化。具有管理员权限的其他插件若通过绕过 Bukkit 事件的底层方式移动玩家，不在本插件保证范围内。
- 基岩版识别通过 Floodgate API 优先、Geyser API 兜底。两个 API 都是 `compileOnly`，不会被打包进最终插件；服务器安装对应插件时才调用。
- 当前实现是“每个 UUID 只验证一次”。如果希望每次重新进服都验证，需要改为不持久化验证状态。
- **注意：当前源码尚未在目标 Java/Paper 版本上完成实际构建和服务器联机测试。**
