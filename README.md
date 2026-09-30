# Kail Location（本地版）

> 调试者的空间坐标实验舱：路线模拟、位置模拟、导航模拟、步频模拟、虚拟定位与 NFC 模拟。
>
> 本仓库为个人自用本地版：已移除登录/订阅/次数限制、广告与数据上报，全功能免登录直接可用。仅供开发者本人调试使用，不分发。

<p align="center">
  <img alt="Language" src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img alt="UI" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img alt="Architecture" src="https://img.shields.io/badge/MVVM-111827?style=for-the-badge" />
  <img alt="License" src="https://img.shields.io/badge/GPL--3.0-0F172A?style=for-the-badge" />
</p>

---

## 核心能力

| 模块 | 能力 |
| --- | --- |
| 路线模拟 | 构建并回放调试路线，用于验证位置相关业务流程。 |
| 位置模拟 | 在授权环境下模拟设备位置，辅助开发与专业测试。 |
| 导航模拟 | 模拟导航过程，观察路线、方向与状态变化。 |
| 步频模拟 | 模拟步频数据，用于传感器相关场景调试。 |
| 虚拟定位 | 提供虚拟坐标能力，便于定位逻辑验证。 |
| NFC 模拟 | 面向调试场景的 NFC 模拟能力。 |

---

## 构建

```bash
./gradlew assembleDebug
```

也可通过 GitHub Actions 构建：推送到 `local-free` 分支即自动出包，产物见 Actions Artifacts 与 Releases。

## 配置百度地图 Key（重要）

源码构建的包不含百度 key，地图显示与位置搜索需要自行配置：

1. 到[百度地图开放平台](https://lbsyun.baidu.com/apiconsole/key)创建应用，类型选 **Android 应用**
2. 填入包名 `com.kail.location` 和本构建的签名 SHA1
3. 将拿到的 AK 填入 App「设置 → 百度地图 Key」，重启 App 生效

详细步骤见 [docs/baiduApiKey.md](docs/baiduApiKey.md)。

## 使用说明

本软件仅供开发人员或专业人士在合法、授权、可控的环境中进行调试与测试。

注意：root 模式启动模拟前需开机满 100 秒（等待 system_server 就绪，否则注入会触发设备重启）。

## License

基于 GNU 通用公共许可证 v3.0（GPL-3.0）开源。

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).
