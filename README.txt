# EarthSpirit 地灵羁绊插件项目

这是一个根据您的设计文档生成的 Java 插件项目源码。

## 如何编译安装
1. 确保您安装了 JDK 21 和 Maven。
2. 将本文件夹 `EarthSpirit_Project` 复制到您的开发环境。
3. 在文件夹内打开终端，运行命令：
   ```bash
   mvn clean package
   ```
4. 编译成功后，在 `target` 文件夹中找到 `EarthSpirit-1.0-SNAPSHOT.jar`。
5. 将 jar 文件放入服务器的 `plugins` 文件夹并重启。

## 功能说明
- **灵契风铃**: 使用 `/getbell` 获取。
- **召唤**: 右键地面召唤隐形盔甲架（地灵）。
- **喂食**: 手持食物右键地灵增加心情。
- **环境感知**: 自动检测周围花朵/矿石数量改变形态。
- **过继**: 地灵被遗弃（7天未喂）后，陌生人喂食3次可获得所有权。

## 注意事项
- 目前代码中关于 Towny 的集成部分（自动建城、权限转移）被注释掉了（标记为 TODO），因为直接调用 Towny API 需要依赖库。
- 如果您希望简单集成，可以在 `SpiritListener.java` 中取消 `Bukkit.dispatchCommand` 的注释，让插件代替玩家执行 `/town new` 等指令。
