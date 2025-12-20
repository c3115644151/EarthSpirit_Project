# 地灵系统 (Earth Spirit) 操作文档

## 1. 系统概述 (Overview)
**地灵系统** 是一个基于 Spigot/Paper API 开发的 Minecraft 伴随/守护实体系统。它通过独特的“双实体架构”（ArmorStand + Wolf）实现了流畅的跟随逻辑和交互体验，并集成了饥饿、心情、等级、背包以及 Towny 城镇插件联动等功能。

系统的核心设计理念是让玩家拥有一个既有“灵魂”（自主行为、情绪反馈）又有“功能”（背包、守护、资源加成）的伙伴。

---

## 2. 核心架构 (Core Architecture)

### 2.1 模块结构

| 模块 | 类文件 | 职责 |
| :--- | :--- | :--- |
| **实体核心** | `SpiritEntity.java` | 定义地灵的所有行为逻辑（移动、状态、渲染、特效、气泡、双实体同步）。 |
| **生命周期管理** | `SpiritManager.java` | 管理地灵数据的加载、保存 (`spirits.yml`)、索引（按 Owner/EntityID 查找）。 |
| **交互监听** | `SpiritListener.java` | 处理玩家输入（右键召唤、GUI 打开、物品交互、进城提示、伤害事件取消）。 |
| **GUI 系统** | `SpiritGUI.java` | 提供可视化操作界面（抚摸、投喂、背包、设置、嘴馋清单、状态查看）。 |
| **背包系统** | `SpiritInventory.java` | 处理地灵背包的存储，支持 Base64 序列化与反序列化。 |
| **嘴馋系统** | `CravingManager.java` | 管理每日随机任务请求及奖励结算。 |
| **皮肤管理** | `SpiritSkinManager.java` | 管理自定义头颅纹理、表情切换（开心/难过/眨眼）及缓存。 |
| **任务调度** | `SpiritTask.java` | 全局 Runnable，负责每 tick 驱动所有地灵的更新 (`tick()`)。 |
| **特效渲染** | `SpiritParticleTask.java` | 负责处理环境粒子特效（如富集区金粉、灵域加成蓝火），基于 `BiomeGiftsHelper` 判断。 |
| **Towny 集成** | `TownyIntegration.java` | 负责与 Towny 插件的数据交互（自动创建城镇、居所加成判断、权限同步）。 |
| **跨插件支持** | `BiomeGiftsHelper.java` | 通过反射软连接 `BiomeGifts` 插件，实现群系判断和物品获取，无需强依赖。 |
| **插件入口** | `EarthSpiritPlugin.java` | 负责插件初始化、指令注册 (`/getbell`, `/getwand`) 及配方注册。 |

### 2.2 依赖关系
*   **Soft Depend**: `Towny` (城镇功能), `BiomeGifts` (群系判定与物品)
*   **API**: Spigot/Paper 1.20+

---

## 3. 详细模块解析 (Detailed Module Analysis)

### 3.1 实体核心逻辑 (`SpiritEntity.java`)
这是整个系统最复杂的部分，采用了**双实体驱动 (Dual-Entity Driver)** 方案：
*   **Driver (驱动层)**: 一只隐形、静音、无敌的幼年狼 (`Wolf`)。利用原版 AI 处理寻路和跟随。
*   **Renderer (渲染层)**: 一个小型的盔甲架 (`ArmorStand`)。负责显示头颅、手持物和发光。

#### 关键特性：
1.  **平滑移动**: 使用线性插值 (Lerp) 算法平滑同步 ArmorStand 与 Wolf 的位置，消除卡顿感。
2.  **动态高度**: 智能判断地形，处于高处时自动悬浮，进出低地时平滑过渡。
3.  **状态系统**:
    *   **Mood (心情)**: 0-100。UI显示为粉色(High)/绿色(Mid)/红色(Low)进度条。影响皮肤表情和加成。
    *   **Hunger (饱食度)**: 随时间消耗。过低会触发“饿饿...饭饭...”悬浮气泡。
    *   **Mode (模式)**: 
        *   `COMPANION`: 跟随玩家，提供背包。
        *   `GUARDIAN`: 驻守原地（需在居所内），生成底盘，提供范围加成。

### 3.2 交互与物品 (`SpiritListener.java`)
#### 物品识别
*   **灵契风铃 (Bell)**: `CustomModelData: 10001` - 右键召唤/收回地灵。
*   **风铃杖 (Blaze Rod)**: `CustomModelData: 10002` - 右键地面指挥地灵移动/守卫。

#### 投喂机制
*   **动态识别**: 使用 `Material.isEdible()` 兼容所有模组食物。
*   **数值逻辑**: 
    *   基础回复量 = 原版食物回复量。
    *   **饥饿时**: 额外回复大量心情与经验。
    *   **不饿时**: 心情低则加心情，心情高则无额外收益。

### 3.3 Towny 深度集成 (`TownyIntegration.java`)
*   **一键建城**: 玩家通过特定交互可直接创建 Towny 城镇，自动设置 HomeBlock 和 Spawn。
*   **灵域加成**: 当地灵处于 **GUARDIAN** 模式且心情高涨时，为城镇提供 Buff：
    *   **减伤**: 居所内受到的伤害减少 10%-20%。
    *   **生长 (CuisineFarming联动)**: 
        *   不再直接干预作物。
        *   提供 **Growth Bonus** API 供 `CuisineFarming` 调用。
        *   加成逻辑：地灵心情 >= 90 提供满额加成，< 60 无加成。
        *   **缓存优化**: 实现了基于 Chunk 的 `chunkGrowthBonusCache`，缓存时间 1 分钟，极大减少了高频生长事件中对 Towny API 的查询压力。

### 3.4 嘴馋与每日任务 (`CravingManager.java`)
*   **每日刷新**: 每日凌晨 4:00 重置。
*   **任务分级**: C -> S 级，奖励递增。
*   **内容**: 随机需求原版物品或 BiomeGifts 特产（如“热带糖蜜”）。
*   **特殊奖励**: 完成高阶任务概率获得 **灵契之种**，用于种植特殊作物。

### 3.5 数据持久化 (`SpiritManager.java`)
*   **文件**: `spirits.yml`
*   **机制**: 
    *   使用 YAML 结构化存储。
    *   保存所有地灵属性、背包内容 (Base64)、过继记录等。
    *   **容错**: 加载时若世界不存在，会自动处理位置防止崩服。

---

## 4. 指令与权限 (Commands & Permissions)

| 指令 | 权限 | 描述 |
| :--- | :--- | :--- |
| `/getbell` | `earthspirit.admin` | 获取灵契风铃（管理员用）。 |
| `/getwand` | `earthspirit.admin` | 获取风铃杖（管理员用）。 |

---

## 5. 常见问题排查 (Troubleshooting)

*   **Q: 地灵无法移动/卡住？**
    *   A: 尝试使用灵契风铃收回再重新召唤。这是双实体同步偶尔会出现的“脱钩”现象。
*   **Q: 没有灵域加成效果？**
    *   A: 检查：1. 是否在 Towny 居所内；2. 地灵是否为 GUARDIAN 模式；3. 地灵心情是否 >= 60。
*   **Q: 无法投喂？**
    *   A: 确保物品可食用。如果地灵属于他人，只有在地灵“抑郁”（被遗弃）状态下才允许陌生人投喂（触发过继逻辑）。

---

**文档维护人**: 开发组
**最后更新**: 2025-12-19
