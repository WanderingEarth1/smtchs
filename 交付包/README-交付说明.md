# 交付包说明

两个压缩包，按接收方角色选择。

---

## 📦 smtchs-source.zip（632 KB）— 给开发者

**完整源码**，需要 Node.js 环境。

```bash
unzip smtchs-source.zip
cd smtchs-source
pnpm install
pnpm dev
```

浏览器打开 `http://127.0.0.1:5273/`。

**包含**：`src/` 源码、`tests/` 185 项测试、完整 `README.md`、需求文档、界面截图。

**验证状态**：解压后 `pnpm install` → `pnpm test`（185 项全过）→ `pnpm build` 均正常。

---

## 📦 smtchs-dist.zip（304 KB）— 给非开发者

**已构建好的静态站点**，无需安装任何环境。

```bash
unzip smtchs-dist.zip
cd smtchs-dist
python3 -m http.server 8080
```

浏览器打开 `http://localhost:8080/`。

**也可以**：把解压后的文件直接上传到任意静态托管（阿里云 OSS、Nginx、Vercel 等）。

**验证状态**：解压后用 Python 服务器运行，员工端、地图、管理端 PC/移动、Excel 导出全部正常。

---

## 演示账号（两个包通用）

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 员工·张三 | `zhangsan` | `123456` |
| 员工·李四 | `lisi` | `123456` |
| 员工·王五 | `wangwu` | `123456` |
| 员工·赵六（已停用） | `zhaoliu` | `123456` |

---

## 校验和

```
381f058e31aaca48f9ed15d9a18babf1bc274d867b5582f675ae054f0be76614  smtchs-dist.zip
96eff15fc2f7fca7254e7068fed4c60d33c2c19422b5b15d259adebc219e477d  smtchs-source.zip
```

验证：`shasum -a 256 smtchs-source.zip`

---

## 两个包的差异

| | 源码包 | 构建包 |
|---|---|---|
| 需要 Node.js | ✅ 是 | ❌ 否 |
| 可改代码 | ✅ 是 | ❌ 否 |
| 可跑测试 | ✅ 185 项 | ❌ 否 |
| 可直接部署 | 需先 build | ✅ 是 |
| 体积 | 632 KB | 304 KB |

---

## 注意事项

**数据存储**：所有数据存在浏览器 localStorage，每个人看到的是自己的一份，清理缓存会重置为演示数据。

**定位功能**：浏览器要求 GPS 必须 HTTPS 或 localhost。用局域网 IP 访问时定位会被拦截，记录标记「未获取定位」（需求定义的兜底行为，不影响提交）。

**地图**：使用高德公开瓦片，需能访问 `webrd0*.is.autonavi.com`。离线环境地图不显示，其他功能正常。

**二维码**：`访问二维码.png` 是打包时的局域网地址，换网络后需重新生成。
