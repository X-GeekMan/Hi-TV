# Hi TV

Hi TV 基于 [Null_](https://github.com/Null9797) 的 [Halo TV](https://github.com/Null9797/halo-tv) 二次开发，**当前作者 BenBen**。Halo TV 本身基于开源项目 [Aerial](https://github.com/pengchujin/aerial-tv)。本项目为非官方版本，保留上游版权声明与 MIT 许可。

完整版适用于 Android 5.0 及以上，Standard 和 Guard 二选一。Lite 使用独立包名，面向 Android 4.0–4.4（测试版）。

## 下载

- [最新 Release](https://github.com/X-GeekMan/Hi-TV/releases/latest)
- [HiTV-2.1.2-standard.apk](https://github.com/X-GeekMan/Hi-TV/releases/download/v2.1.2/HiTV-2.1.2-standard.apk)

## 构建

使用 JDK 17 和 Android SDK：

```bash
./gradlew :app:assembleStandardRelease :app:assembleGuardRelease :lite:assembleRelease
```

[English](README.en.md)
