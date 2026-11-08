# 消息播报 · 正式版

一个安卓通知语音播报 App：来通知时，用带性格的声音念出来，还能替你做点评。

> 纯本地监听 + 自己配模型接口。App 不上传通知内容到作者的服务器，只有播报文案会发送到你配置的模型接口。

## 功能

- **通知语音播报**：任意 App 的通知 → 大模型现编一段点评 → 语音念出来（应用名、谁发的、说了什么）
- **人设模板**：内置「DeepSeek 小助手 / 傲娇管家 / 东北大哥 / 高冷 AI / 相声捧哏 / 老父亲」等十种，可自定义
- **关注度分级**：每个 App 一根 1–10 滑杆，低分只一句带过，高分详细念
- **联系人记忆库**：每个联系人一份长期档案（自动累积 + 手动添加/编辑/删除），播报时会提起「上回…」
- **快速回复**：播报后通知栏出现「回复 XX」，可直接打字回；快捷短语一键发
- **助理格式代回**：`【助理回复】我家主人说，她在忙，晚点回你。`
- **免打扰时段**、播报记录、一键暂停/开始、常驻通知保活

## 需要你自己准备

| 项 | 说明 |
|---|---|
| 模型接口 | 任何 OpenAI 兼容接口（默认 `https://api.deepseek.com`） |
| API Key | 在「设置 › 模型与接口」里填 |
| 通知使用权 | 首次使用引导里授权 |
| 录音 / 悬浮窗权限 | 可选；语音回复与浮层卡片需要 |

## 自己编译

需要 JDK 17 + Android 命令行工具（`aapt2`、`d8`、`apksigner`）和一个 `android.jar`（API 34）。

```bash
# 1. 生成 dex
mkdir -p build/classes build/dex
javac -source 11 -target 11 -classpath "$ANDROID_JAR" -d build/classes $(find src -name '*.java')
d8 --lib "$ANDROID_JAR" --min-api 24 --output build/dex $(find build/classes -name '*.class')

# 2. 打资源与清单
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  -R build/res.zip --min-sdk-version 24 --target-sdk-version 34

# 3. 塞 dex、对齐、签名
python3 build_apk.py            # 需要改里面的 classes.dex 路径
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug"
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/app.apk build/aligned.apk
```

## 权限说明

- `通知使用权`：读取通知内容用于播报（不会拦截或修改通知）
- `RECORD_AUDIO`：语音回复时录音，仅用于识别你说的那句话
- `SYSTEM_ALERT_WINDOW`：播报时在屏幕顶部显示「要语音回复吗」小卡片
- `POST_NOTIFICATIONS`：显示快速回复通知

## 免责声明

- 本项目与 DeepSeek、微信、飞书等公司无关。
- 通知内容会发送到你配置的模型接口，请自行评估隐私风险。
- 语音合成使用系统 TTS 与公开接口，商用前请自行确认对应服务的授权条款。

## License

MIT
