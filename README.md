# FOXO Gateway

FOXO 是一个 Android WebView 应用。本项目为它实现了一个**本地 OpenAI 兼容网关**：
在设备内启动 HTTP 服务，把多个上游 AI 服务的专有协议适配成标准 OpenAI 接口，
让任何支持自定义 base URL 的客户端（Cherry Studio、ChatBox、LobeChat、各类 IDE 插件等）
都能统一调用这些免费额度。

**所有请求都从你的设备直接发往厂商服务器，不经过任何中间服务器。**

---

## 目录

- [它能做什么](#它能做什么)
- [五组免费模型](#五组免费模型)
- [支持的供应商](#支持的供应商)
- [架构](#架构)
- [构建](#构建)
- [下载](#下载)
- [参考项目](#参考项目)
- [免责声明](#免责声明)

---

## 它能做什么

| 能力 | 说明 |
|---|---|
| **协议适配** | 把各家的非标准请求体、非标准认证头、非标准 SSE 帧统一成 OpenAI 语义 |
| **流式转换** | 上游自定义事件（如 `event: output` + `data.response`）转成 `chat.completion.chunk` |
| **增量合并** | 上游发全量快照时按前缀差量裁剪，避免客户端重复渲染 |
| **多账号轮换** | 同一上游可登录多个账号，失败自动切换下一个 |
| **凭证刷新** | JWT 过期前主动刷新，避免请求失败 |
| **一键签到** | 服务商页可直接签到/领任务，积分到账实时显示 |
| **模型选择** | 逐模型开关，未勾选的不会出现在网关清单里 |

对外只暴露标准 OpenAI 接口：

```
GET  /v1/models
POST /v1/chat/completions    (支持 stream: true)
```

---

## 五组免费模型

选模型页把可用模型分成 **5 组**，全部为直连上游，无中转：

### 第 1 组 · 免登录直连（5 家厂商 + Kilo，开箱即用）

**这一组不需要注册、不需要填 Key**，装好就能直接调。共 5 家厂商 + 1 个 Kilo 免费池：

| # | 厂商 | 归属 | 说明 | 上游地址 |
|---|---|---|---|---|
| 1 | **Pollinations** | Pollinations.ai | 匿名通道，无需 Key | `text.pollinations.ai/openai` |
| 2 | **LLM7** | llm7.io | 匿名每日约 60 次，可选 Key | `api.llm7.io/v1` |
| 3 | **OVHcloud AI** | OVHcloud | 公共 AI Endpoints，无 SLA | `oai.endpoints.kepler.ai.cloud.ovh.net/v1` |
| 4 | **BlockRun** | NVIDIA 托管 | `billing=free` 公共模型池 | `blockrun.ai/api/v1` |
| 5 | **OpenCode Zen** | OpenCode | 匿名额度易耗尽，默认关闭 | `opencode.ai/zen/v1` |
| — | **Kilo 免费池** | Kilo | 首页 Kilo 免费模型，归入本组 | — |

> **调用方式**：这些渠道的请求由你的设备**直接发往厂商服务器**，不经过任何中转。
> 本地网关只做协议翻译（把各家的非标准格式转成 OpenAI 格式），请求链路仍是
> 设备 → 厂商，没有中间服务器。

### 第 2 组 · 免费额度类（拿到 Key 就有额度，不用每天签到）

| 厂商 | 说明 | 上游地址 |
|---|---|---|
| **Cline** | Cline 账号免费额度 | `api.cline.bot/api/v1` |
| **商汤日日新** | 新用户赠送额度（Token Plan 公测） | `token.sensenova.cn/v1` |
| **Gemini** | Google AI Studio 免费层，按模型限速 | `generativelanguage.googleapis.com/v1beta/openai` |
| **AMD Radeon** | Radeon 云端推理 | `developer.amd.com.cn/radeon/api/v1` |
| **墨点计划** | 上海AI实验室，书生·端砚科研模型，每月免费墨点 | `discovery-api.intern-ai.org.cn/v1` |
| **Agnes AI** | FCC 线路，推理以 think 标签返回 | `apihub.agnes-ai.com/v1` |
| **硅基流动** | 注册送额度，国内站 `.cn` / 国际站 `.com` | `api.siliconflow.cn/v1` |
| **OpenRouter** | 免费路由，每天约 200 次，免信用卡 | `openrouter.ai/api/v1` |

### 第 3 组 · 供应商账号（服务商页登录后自动接入）

手机号/网页/设备码登录，登录后自动拉取模型目录：

| 供应商 | 归属 | 登录方式 | 签到 | 任务 |
|---|---|---|---|---|
| **Trae** | 字节 | 网页登录 | ✅ | — |
| **Loomy** | 讯飞 | 手机号验证码 | ✅ | — |
| **WorkBuddy** | 腾讯 | 设备授权 | ✅ | ✅ |
| **美团** | 美团 | 网页登录 | — | — |
| **智谱** | 智谱 AI | 手机号验证码 | ✅ | ✅ |
| **当贝** | 当贝 | 网页登录 | — | — |
| **秒悟** | 阿里 | 网页登录 | ✅ | — |
| **Tabbit** | Tabbit | 手机号验证码 | ✅ | — |

签到按钮就在服务商页每家卡片的按钮行里，点一下对该公司所有已启用账号依次执行，
结果显示在账号行上（成功带积分数，失败带原因）。

### 第 4 组 · 自定义服务商（手动接入）

任何 OpenAI 兼容接口，填 Base URL + Key + 模型名即可接入。

### 第 5 组 · 自定义中转站（多密钥轮换）

同一个中转站可填多个 Key，请求时自动轮换，单 Key 失效不影响使用。

---

## 白嫖页的另一分类

白嫖页把渠道按获取方式分成三类（与选模型页的 5 组视角不同）：

| 分类 | 含义 | 代表厂商 |
|---|---|---|
| **免登录直连** | 无需注册与 Key，开箱即用 | Pollinations、LLM7、OVHcloud、BlockRun、OpenCode Zen |
| **免费额度类** | 拿到 Key 就有免费额度 | Cline、Gemini、硅基流动、OpenRouter、商汤、AMD、墨点、Agnes |
| **签到类** | 每日签到领积分 | LobsterAI（有道）、Qoder（阿里系）、Trae（字节）、Loomy（讯飞）、MiniMax Code、ZCode（智谱） |

---

## 支持的供应商

除上述渠道外，还内置了对以下专有协议的适配（不走标准 OpenAI 格式，需要协议转换）：

| 供应商 | 协议特点 |
|---|---|
| **Trae** | SSE 是 `event: output` + `data.response`，非 OpenAI 格式 |
| **美团 CatPaw** | 原生消息格式，`modelType` 是数字 ID，需先 `/round` 再 `/event` 再 `/turn` |
| **智谱清言** | `agent-api` 三接口链：取 agent → 建会话 → stream |
| **智谱** | `autoclaw-proxy`，需 MD5 签名头 |
| **WorkBuddy** | 国内版/国际版双端点，10 个会话头 |
| **当贝** | AI_SEARCH + 自定义签名 |
| **秒悟** | 无 `/chat/completions`，走 `entitlement/init` |
| **Tabbit** | Android 客户端形态认证，`x-tabbit-client-platform` |
| **Loomy** | Cookie 认证（ssoSessionId + account_id + token） |

---

## 架构

```
客户端 (OpenAI 格式)
        │  POST /v1/chat/completions
        ▼
┌─────────────────────────────────────┐
│  CustomRoute (设备内本地 HTTP 服务)  │
│                                     │
│  按 model 前缀分发 → 协议适配器      │
└─────────────────────────────────────┘
        │
        ├──► 标准 OpenAI 上游  ──────────► 厂商服务器
        ├──► Cookie 认证上游   ──────────► 厂商服务器
        ├──► 自定义 SSE 上游   ──────────► 厂商服务器
        └──► 数字 modelType 上游 ────────► 厂商服务器

        ▼
  OpenAI chunk 流 (SSE) 返回客户端
```

关键点：**网关跑在你自己手机上**。它只做协议翻译，请求体从你的设备直接发往厂商，
不经过作者或任何第三方的服务器。你的 Token 也只存在本机 localStorage 里。

### 代码结构

```
inject/com/nbasdws/zjsj/
├── CustomRoute.java    # 网关路由：各上游协议适配 + SSE 转换
└── WebLogin.java       # 网页登录：Cookie 抓取、凭证解析、目录拉取

assets/index.html       # 单文件前端（模型/白嫖/服务商/选模型/统计/网关）
scripts/
├── repack.py           # 重建 APK（替换 assets 与 dex，4 字节对齐）
├── strip_comments.py   # 源码注释剥离工具
└── inject_secrets.py   # 构建时密钥注入
```

---

## 构建

本项目**不包含**基础 APK。构建需要你自备合法的应用安装包。

```bash
# 1. 准备密钥（仓库不含真实值）
cat > secrets.json <<'EOF'
{
  "chatglm_sign_key": "<自行填写>",
  "zhipu_sign_key": "<自行填写>"
}
EOF

# 2. 注入密钥，生成 build/ 下的源码副本
python scripts/inject_secrets.py

# 3. 编译 Java -> dex，替换 assets，重建 APK
export FOXO_BASE_APK=/path/to/base.apk
python scripts/repack.py

# 4. 签名
apksigner sign --ks <your.keystore> --out app-signed.apk build/app-repacked.apk
```

`secrets.json` 已被 `.gitignore` 排除，**不要提交**。

### 密钥占位符

源码中涉及上游私有签名密钥的位置使用占位符：

| 占位符 | 用途 |
|---|---|
| `__SIGN_KEY_CHATGLM__` | 某上游请求签名 |
| `__SIGN_KEY_ZHIPU__` | 另一上游请求签名 |

构建时由 `scripts/inject_secrets.py` 替换为真实值。仓库中的源码永远只有占位符。

---

## 下载

编译好的 APK 发布在 [Releases](../../releases) 页面，从那里下载最新版本。

> 安装包不放在仓库里，只通过 Releases 分发。

安装前需允许「安装未知来源应用」。首次启动后：

1. 进「服务商」页登录你想用的厂商
2. 进「选模型」页勾选要暴露给网关的模型
3. 进「网关」页启动服务，记下端口和密钥
4. 在客户端里填 `http://127.0.0.1:<端口>/v1` 作为 Base URL

---

## 参考项目

本项目的渠道清单与协议实现参考了以下开源项目：

- **[dsh-our-free-model](https://github.com/)** — 前端界面与渠道分组设计的来源
- **[freellmpool](https://github.com/0xzr/freellmpool)** — 免费模型池目录与路由分类（MIT，Copyright © 2026 0xzr）

感谢这些项目的作者。

---

## 免责声明

- 本项目仅用于**协议研究与互操作学习**，不提供任何上游服务的访问权限。
- 使用本项目可能违反上游服务的服务条款。**请自行评估风险并承担后果**。
- 本项目不包含、不分发任何第三方应用的代码、资源或签名。
- 上游接口随时可能变更，作者不保证可用性。
- 仓库内 APK 仅供个人测试，请勿用于商业用途。

---

## 许可

MIT License，详见 [LICENSE](LICENSE)。
