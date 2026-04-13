# mrcp-proxy

基于 **Spring Boot** 与 **Netty** 的语音代理服务：客户端通过 **WebSocket** 发送与阿里云 NLS 风格相近的 JSON 控制报文，由代理转发到后端的 **ASR（语音识别）** 与 **TTS（语音合成）** 引擎，并将结果回写给客户端。

## 功能概览

- **入站协议**：WebSocket（文本帧为 JSON，二进制帧为 PCM 音频，用于 ASR）。
- **TTS**：解析 `SpeechSynthesizer` 命名空间消息，将合成请求转到配置的 TTS 后端，并把音频/状态写回客户端通道。
- **ASR**：解析 `SpeechTranscriber` 命名空间消息；在 `StartTranscription` 时建立会话并与后端 ASR WebSocket 对接；后续二进制音频透传给后端，识别结果再封装回客户端。

## 技术栈

| 类别 | 说明 |
|------|------|
| 运行时 | Java 8 |
| 框架 | Spring Boot 2.3.5 |
| 网络 | Netty 4.1（HTTP 握手 + WebSocket） |
| JSON | Fastjson 2 |
| 工具 | Lombok、Hutool、Apache HttpClient、OkHttp |
| 云 SDK | 阿里云 NLS（`nls-sdk-tts`、`nls-sdk-transcriber`，供 `alitts` / `aliasr` 使用） |

`pom.xml` 中还包含 Springfox（Swagger 2），当前源码中未发现已启用的 REST 接口；业务入口以 WebSocket 为主。

## 项目结构（简要）

```
src/main/java/com/mrcp/proxy/
├── ProxyApplication.java          # Spring Boot 启动类
├── protocol/                      # 入站 JSON：MessageHeader、SynthesisMessage、TranscriptionMessage 等
├── ws/
│   ├── NettyServer.java           # WebSocket 服务、消息分发
│   ├── NettyConfig.java           # ws.port、ws.ws-path
│   ├── AsrConfig.java / TtsConfig.java
│   └── client/                    # 出站 WebSocket 客户端（含阿里云、Netty 实现）
├── handler/
│   ├── asr/                       # FunasrHandler、AliAsrHandler（工厂可注册扩展）
│   ├── tts/                       # IndexTTSHandler、AliTtsHandler
│   └── status/                    # ASR/TTS 状态机
└── utils/
```

## 配置说明

主要配置在 `src/main/resources/application.yml`：

| 配置项 | 含义 |
|--------|------|
| `server.port` | 嵌入式 Tomcat 端口；默认 `-1` 表示不启用 Web 端口，仅依赖 Netty WebSocket |
| `ws.port` | WebSocket 监听端口（默认 `8854`） |
| `ws.ws-path` | WebSocket 路径（默认 `/ws/v1`） |
| `tts.tts-handler` | TTS 实现：`indextts`（自建 WebSocket）、`alitts`（阿里云） |
| `tts.tts-url` | IndexTTS 等服务地址 |
| `tts.tts-properties` | 阿里云等所需的 appKey、AccessKey 等（勿将真实密钥提交到仓库） |
| `asr.asr-handler` | ASR 实现：`funasr`、`aliasr` |
| `asr.asr-url` | FunASR 等 WebSocket 地址 |
| `asr.asr-properties` | 阿里云 ASR 凭证等 |
| `*.audio-save-enabled` / `*.audio-save-dir` | 可选：保存调试音频到本地目录 |

请按部署环境修改 URL 与密钥；仓库中的示例值仅为占位。

## 构建与运行

```bash
# 编译打包
mvn -q -DskipTests package

# 运行（需本机已安装 JDK 8+、Maven）
mvn spring-boot:run
```

或可执行打包生成的 fat JAR（以实际 `target` 下文件名为准）：

```bash
java -Dfile.encoding=UTF-8 -jar target/mrcp-proxy-1.0.jar
```

## 客户端连接

在默认配置下，WebSocket 地址为：

```text
ws://<主机>:8854/ws/v1
```

- **TTS**：发送 JSON，`header.namespace` 为 `SpeechSynthesizer`（如 `StartSynthesis`），结构与 `SynthesisMessage` 一致。
- **ASR**：先发送 `SpeechTranscriber` / `StartTranscription` 等 JSON（`context.session_id` 用于会话）；随后在**同一连接**上发送 **PCM 二进制帧** 作为音频输入。

## 扩展后端

- **ASR**：在 `AsrHandlerFactory` 中 `register` 新的名称与 `AsrHandler` 构造方式，并在配置 `asr.asr-handler` 中指定该名称。
- **TTS**：在 `TtsHandlerFactory` 中同样注册，并配置 `tts.tts-handler`。

## 许可证与作者

项目内 `ProxyApplication` 标注作者为 wt；若需对外发布请自行补充 LICENSE 与贡献说明。
