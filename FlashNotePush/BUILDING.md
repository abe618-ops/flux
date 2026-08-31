# 构建说明

运行 `bash build.sh`。需要 Android SDK Platform 35、Build Tools 35.0.1 和 JDK 17；源码按 Java 8 目标编译。

发布仓库**不包含任何签名私钥、keystore 或密码**。构建时通过环境变量指定你自己的签名文件：

```bash
export FLASHNOTE_KEYSTORE=/absolute/path/to/your-release.jks
export FLASHNOTE_KEY_ALIAS=flashnote
export FLASHNOTE_KEYSTORE_PASS='your-password'
export FLASHNOTE_KEY_PASS='your-password'
bash build.sh
```

APK 输出到 `dist/FlashNotePush-Web-v1.5.3.apk`。

> 安全提示：不要把 `.jks` / `.keystore`、密码或其他签名凭据提交到 GitHub。
