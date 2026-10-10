# ChessWeb — 国际象棋练习 & 英语单词听写

一个基于 Spring Boot 的 Web 应用，包含两个功能模块：**国际象棋人机对弈**和**初中英语单词听写**。

## 功能模块

### ♟️ 国际象棋对弈

- 基于 [Stockfish NNUE](https://stockfishchess.org/) 引擎，AI 水平约候补棋协大师（Elo 2150）
- 支持执白 / 执黑新局、认输操作
- 完整棋谱记录与实时状态显示
- 支持兵的升变选择
- SVG 矢量棋子渲染，跨平台视觉一致

### 📖 初中英语单词听写

- 词库来源：外研版（新）七年级上册（Starter + Unit 1~6，共 14 个单词表）
- 勾选范围后乱序听写，支持自定义朗读间隔（2~5 秒）
- 使用 [Edge TTS](https://github.com/rany2/edge-tts) 生成美式英语发音
- 听写完成后自动获取例句与中文翻译，辅助复习
- Redis 缓存例句（7 天有效期），减少重复 API 调用

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 8 + Spring Boot 2.7.18 |
| 缓存 | Redis（Jedis 客户端） |
| 前端 | 原生 HTML / CSS / JavaScript |
| 象棋引擎 | Stockfish NNUE 16（WebAssembly） |
| 棋局规则 | chess.js |
| 语音服务 | Edge TTS（Python 本地代理） |
| 构建工具 | Maven |

## 项目结构

```
├── src/main/
│   ├── java/com/chessweb/
│   │   ├── config/              # Web 配置
│   │   ├── dictation/           # 听写模块
│   │   │   ├── dto/             # 请求 DTO
│   │   │   ├── model/           # 数据模型
│   │   │   ├── DictationController.java
│   │   │   ├── DictationService.java
│   │   │   ├── ExampleService.java    # 例句生成（含 API + 降级）
│   │   │   └── VocabLoader.java       # 词库加载
│   │   └── ChessWebApplication.java
│   └── resources/
│       ├── static/
│       │   ├── css/             # 样式文件
│       │   ├── js/              # 前端脚本（game.js / dictation.js）
│       │   ├── engine/          # Stockfish 引擎文件
│       │   ├── index.html       # 象棋页面
│       │   └── dictation.html   # 听写页面
│       └── vocab/               # 词库 JSON（fltrp-g7a）
├── scripts/
│   ├── tts_server.py            # Edge TTS 本地代理服务
│   └── generate_vocab.py        # 词库生成脚本
├── start.bat                    # 一键启动脚本
└── stop.bat                     # 一键停止脚本
```

## 环境要求

- **Java** 1.8+
- **Maven** 3.6+
- **Redis** 5.0+
- **Python** 3.10+（用于 Edge TTS 语音服务）

## 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/wlw0071985/chessWeb.git
cd chessWeb
```

### 2. 安装 Python 依赖

```bash
pip install edge-tts
```

### 3. 一键启动

双击运行 `start.bat`，脚本会自动依次启动：

1. **Redis** 服务（端口 6379）
2. **Edge TTS** 语音代理（端口 5050）
3. **Spring Boot** 应用（端口 8080）

> 启动前请确认 `start.bat` 中 Redis 的路径与你本机一致。

### 4. 访问应用

- 国际象棋：http://localhost:8080/
- 英语听写：http://localhost:8080/dictation.html

### 停止服务

双击运行 `stop.bat` 即可停止所有服务。

## 配置说明

主要配置在 `src/main/resources/application.properties`：

```properties
server.port=8080                          # 应用端口
spring.redis.host=127.0.0.1              # Redis 地址
spring.redis.port=6379                    # Redis 端口
dictation.selection-ttl-hours=12          # 听写选词缓存时长（小时）
dictation.example-ttl-days=7             # 例句缓存时长（天）
```

## 例句生成机制

听写复习环节的例句获取采用多级降级策略：

1. **主 API** — 调用 free dictionary API 提取真实例句
2. **降级策略** — API 失败或例句不足时，自动生成备用例句
3. **翻译** — 通过 MyMemory 翻译 API 获取中文释义，失败时标记"暂无中文翻译"

所有例句经 Redis 缓存，避免重复请求。
