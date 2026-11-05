# Builds, Signing and Releases

## Available Builds

| Build | Signing | Purpose |
| --- | --- | --- |
| `debug` | 本机开发证书 | 开发、调试与设备测试 |
| `demo` | 本机开发证书，启用压缩 | 课程安装；本仓库提供的 v1.1 APK 沿用 v1.0 证书 |
| `release` | 固定私有发布证书 | 长期维护；未配置签名时拒绝构建 |

- `releases/AeroLedger-v1.1.apk`：课程兼容版，可覆盖仓库原 v1.0 安装包。
- `releases/AeroLedger-v1.1-stable.apk`：固定签名版。与课程版签名不同，首次迁移需使用完整备份。

不同成员本机生成的开发证书可能不同，不能把各自构建的 demo 当作同一发布证书的连续版本。签名证书不同也不能直接覆盖安装；请按 [备份迁移说明](BACKUP_FORMAT.md) 操作，切勿为安装方便先删除未备份的账本。

## Local Signing

已有发布身份时继续使用，避免重复生成。首次建立身份：

```bash
python3 scripts/prepare-signing.py
./gradlew :app:assembleRelease
```

脚本在被 Git 忽略的 `signing/` 中创建固定密钥和权限为 600 的配置，不打印密码。请由项目维护者安全备份该目录。配置文件 `signing/release.properties`：

```properties
STORE_FILE=signing/aeroledger-release.jks
STORE_PASSWORD=YOUR_PRIVATE_STORE_PASSWORD
KEY_ALIAS=aeroledger
KEY_PASSWORD=YOUR_PRIVATE_KEY_PASSWORD
```

也可使用 `AEROLEDGER_STORE_FILE`、`AEROLEDGER_STORE_PASSWORD`、`AEROLEDGER_KEY_ALIAS`、`AEROLEDGER_KEY_PASSWORD` 环境变量覆盖配置。密钥和密码不得提交。

## CI and GitHub Releases

`.github/workflows/android.yml` 在 push / PR 时运行业务断言、构建、lint，以及 API 26、33、35 的设备回归和生命周期检查。开发产物证书由 CI 环境决定，仅用于开发验证。

`.github/workflows/release.yml` 在 `v*` 标签时验证标签与 `versionName` 一致，再构建固定签名 APK、计算校验值并创建 GitHub Release。需配置仓库 Actions secrets：

- `AEROLEDGER_KEYSTORE_BASE64`：固定 JKS 文件的 Base64 内容。
- `AEROLEDGER_STORE_PASSWORD`：密钥库密码。
- `AEROLEDGER_KEY_ALIAS`：签名别名。
- `AEROLEDGER_KEY_PASSWORD`：私钥密码。

发布任务只消费加密 secrets，结束后清理运行器中的签名文件。当前仓库已配置本项目固定身份；复制到其他仓库时，由维护者配置自己的身份。Actions 依赖固定为提交 SHA。

更新 `versionCode` 与 `versionName`，完成测试后提交并推送版本标签：

```bash
git tag v1.1
git push origin v1.1
```

同一个版本标签不重复发布或移动。正式签名证书摘要与各 APK 的 SHA-256 可用于核验安装包来源。
