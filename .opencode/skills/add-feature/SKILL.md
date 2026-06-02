# 新增功能技能

按照项目的 Clean Architecture + MVVM 架构规范，新增一个功能模块。

## 使用场景

- 添加新的相机功能（如新滤镜、新特效）
- 添加新的 UI 交互
- 添加新的数据处理逻辑

## 架构规范

### 必须遵循的层次结构

```
1. Domain 层（domain 模块）
   - 定义 Model（数据类）
   - 定义 Repository 接口
   - 定义 UseCase（业务逻辑）

2. Data 层（data:* 模块）
   - 实现 Repository 接口
   - 处理数据源（网络、数据库、文件）

3. SDK 层（camera-sdk 模块）
   - 实现硬件相关逻辑
   - 提供简单接口给上层使用

4. Feature 层（feature:* 模块）
   - 实现 UI（Fragment + XML）
   - 实现 ViewModel
   - 观察 ViewModel 的 StateFlow/LiveData
```

### 代码规范

1. **每个类必须有 KDoc 注释**
2. **每个公开方法必须有 KDoc 注释**
3. **使用常量代替魔法数字**
4. **异步操作使用 Kotlin Coroutines**
5. **UI 更新必须在主线程**
6. **IO 操作必须在 Dispatchers.IO**

### 依赖规则

- **Domain 层**：禁止依赖 Android SDK
- **Feature 层**：禁止直接使用 CameraX API
- **SDK 层**：禁止依赖 domain、feature、core 模块

## 执行步骤

1. 分析需求，确定功能属于哪个层次
2. 在 Domain 层定义接口和模型
3. 在 Data 层实现接口
4. 在 SDK 层实现硬件相关逻辑
5. 在 Feature 层实现 UI 和 ViewModel
6. 编写单元测试
7. 验证编译通过
