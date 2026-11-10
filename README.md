# FOXO Gateway

FOXO 是一个 Android WebView 应用。本项目为它实现了一个**本地 OpenAI 兼容网关**：
在设备内启动 HTTP 服务，把多个上游 AI 服务的专有协议适配成标准 OpenAI 接口，
让任何支持自定义 base URL 的客户端都能统一调用。

## 组成

```
inject/com/nbasdws/zjsj/
├── CustomRoute.java    # 网关路由：各上游协议适配 + SSE 转换
└── WebLogin.java       # 网页登录：Cookie 抓取、凭证解析、目录拉取

assets/index.html       # 单文件前端（模型 / 白嫖 / 服务商 / 选模型 / 统计 / 网关）
scripts/
├── repack.py           # 重建 APK（替换 assets 与 dex，4 字节对齐）
├── strip_comments.py   # 源码注释剥离工具
└── inject_secrets.py   # 构建时密钥注入
```

## 架构

```
客户端 (OpenAI 格式)
        │  POST /v1/chat/completions
        ▼
  CustomRoute  ──── 按 provider 分发 ────► 上游 A (专有协议)
        │                                  ├─► 上游 B (Cookie 认证)
        │                                  ├─► 上游 C (SSE 自定义格式)
        │                                  └─► 上游 D (数字 modelType)
        ▼
  OpenAI chunk 流 (SSE)
```

核心能力：

- **协议适配**：把各家非标准请求体、非标准认证头、非标准 SSE 帧格式统一成 OpenAI 语义
- **流式转换**：上游自定义事件（如 `event: output` + `data.response`）转成 `chat.completion.chunk`
- **增量合并**：上游发全量快照时按前缀差量裁剪，避免客户端重复渲染
- **多账号轮换**：同一上游可登录多个账号，失败自动切换
- **凭证刷新**：JWT 过期前主动刷新，避免请求失败

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
python scripts/repack.py

# 4. 签名
apksigner sign --ks <your.keystore> --out app-signed.apk app-repacked.apk
```

`secrets.json` 已被 `.gitignore` 排除，**不要提交**。

## 密钥占位符

源码中涉及上游私有签名密钥的位置使用占位符：

| 占位符 | 用途 |
|---|---|
| `__SIGN_KEY_CHATGLM__` | 某上游请求签名 |
| `__SIGN_KEY_ZHIPU__` | 另一上游请求签名 |

构建时由 `scripts/inject_secrets.py` 替换为真实值。仓库中永远只有占位符。

## 免责声明

- 本项目仅用于**协议研究与互操作学习**，不提供任何上游服务的访问权限。
- 使用本项目可能违反上游服务的服务条款。**请自行评估风险并承担后果**。
- 本项目不包含、不分发任何第三方应用的代码、资源或签名。
- 上游接口随时可能变更，作者不保证可用性。

## 许可

MIT License，详见 [LICENSE](LICENSE)。
