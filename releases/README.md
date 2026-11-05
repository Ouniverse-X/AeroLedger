# Installable Builds

- [AeroLedger-v1.1.apk](AeroLedger-v1.1.apk)：课程兼容版，沿用 v1.0 签名，可覆盖更新。
- [AeroLedger-v1.1-stable.apk](AeroLedger-v1.1-stable.apk)：固定发布签名，新安装或按完整备份流程迁移后使用。
- [SHA256SUMS](SHA256SUMS)：各 APK 对应校验值。
- [AeroLedger-release-certificate.pem](AeroLedger-release-certificate.pem)：固定签名的公开证书，不包含私钥。

支持 Android 8.0 / API 26 以上。无需 Android Studio、账号或网络。通知需在各自设备授权；每台设备独立保存账本。

签名不同的安装包无法直接相互覆盖。v1.0 成员先覆盖课程 v1.1 并导出完整备份，再按 [RELEASE.md](../RELEASE.md) 迁移固定签名版。备份同时保留预算、计划、回顾和主题，CSV 只包含账目。

开发方式见 [DEVELOPMENT.md](../DEVELOPMENT.md)，备份约定见 [BACKUP_FORMAT.md](../BACKUP_FORMAT.md)。历史 v1.0 保留供升级验证。
