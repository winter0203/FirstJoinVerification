# FirstJoinVerification

Paper/Purpur 首次进入验证码插件，支持 Java 版聊天输入，以及 Geyser/Floodgate 基岩版原生弹窗输入。

## 功能

- 玩家首次进入服务器后会被冻结，无法移动。
- **Java 版：** 玩家直接在聊天栏发送数字验证码即可，不需要输入 `/verify` 命令前缀。
- **基岩版：** 优先通过 Geyser/Floodgate 的 Cumulus API 弹出原生输入表单，玩家在输入框填写数字并提交。
- Java 版玩家发送的纯数字验证消息会被插件拦截，不会广播到公共聊天。
- 基岩版表单不可用时，回退为聊天栏直接输入数字。
- 验证成功后按 UUID 保存到 `plugins/FirstJoinVerification/verified.yml`，之后不再重复验证。
- 支持错误次数限制、错误后踢出、未验证期间的交互限制。
- 管理员可执行 `/verifyadmin reload` 重载配置。
- 原来的 `/verify <数字>` 命令仍保留为兼容输入方式。

## 环境要求

- Paper/Purpur 26.2
- Java 25
- Gradle
- 基岩版弹窗需要服务器端正确安装并启用 Geyser（建议配合 Floodgate）。插件使用 Geyser/Floodgate API 发送 Cumulus 表单。

如果服务器使用代理（例如 Velocity），请确保代理和后端的 Geyser/Floodgate 配置正确；Floodgate 相关密钥与转发配置需要与官方部署方式保持一致。

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

messages:
  bedrock-form-title: "首次进入验证"
  bedrock-form-label: "请输入服务器提供的数字验证码"
  bedrock-form-placeholder: "在此输入数字"
```

把 `verification.code` 改成你想设置的数字验证码。请保留引号，例如 `"001234"`，这样开头的 0 不会丢失。不要将验证码公开发送到聊天频道。

`max-attempts: 0` 表示不限制错误次数。

## 玩家使用

### Java 版

直接打开聊天栏，输入验证码数字并发送，例如：

```text
123456
```

不要输入 `/verify`。插件会拦截纯数字验证消息，验证码不会出现在公共聊天中。

### 基岩版

首次进入时会自动弹出原生验证码窗口。输入数字并提交即可。关闭窗口或输入错误后，窗口会在提示间隔后再次出现；错误提交会立即重新弹出。

如果当前 Geyser/Floodgate 环境无法发送表单，插件会提示玩家改为直接在聊天栏发送数字。

## 管理员命令

| 命令 | 说明 |
|---|---|
| `/verifyadmin reload` | 重载配置文件 |
| `/verify <数字>` | 兼容用法，普通玩家不必使用 |

权限：`firstjoinverification.admin`（默认仅 OP）。

## 构建

1. 安装 JDK 25。
2. 安装 Gradle 9.2.1 或兼容版本。
3. 在项目根目录执行：

   ```bash
   gradle build
   ```

4. 构建产物位于 `build/libs/FirstJoinVerification-1.0.0.jar`。

## 安装

1. 将 JAR 放入服务器 `plugins` 目录。
2. 确认 Geyser/Floodgate 已正确安装并能让基岩版玩家进入服务器。
3. 启动服务器，设置 `config.yml` 中的验证码。
4. 执行 `/verifyadmin reload`，或重启服务器。

## 数据文件

`plugins/FirstJoinVerification/verified.yml` 保存已验证玩家的 UUID。删除该文件会导致玩家重新验证。

## 实现说明

- Paper 的 `AsyncChatEvent` 用于拦截 Java 玩家发出的纯数字验证消息；事件可能异步触发，因此插件在回调中只提取并取消消息，实际验证处理切回服务器主线程。
- 基岩版表单使用 Floodgate/Geyser 提供的 Cumulus `CustomForm`。表单提交和聊天栏输入共用同一套验证码校验、尝试次数和 UUID 持久化逻辑。
- 具有管理员权限的其他插件若通过绕过 Bukkit 事件的底层方式移动玩家，不在本插件保证范围内。
- **注意：本次修改尚未在目标服务器上进行实际构建及 Java/基岩版联机测试。**
