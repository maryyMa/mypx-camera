# AGENTS.md - MyPx Camera 项目 AI 助手开发规则

> 本文件定义了 AI 助手在本项目中进行开发时必须遵守的规则和约束。

---

## 项目概述

MyPx 是一款 Android 相机应用，核心功能是基于 OpenGL ES 的实时美颜渲染。采用 Clean Architecture + MVVM 架构。

---

## 模块架构与依赖规则

```
app ──→ feature:camera ──→ domain (纯 Kotlin，无 Android 依赖)
 │          │
 │          ├──→ camera-sdk (独立 SDK，不依赖任何项目模块)
 │          ├──→ core:ui
 │          └──→ core:common
 ├──→ core:ui
 └──→ core:common
```

### 模块职责边界（严禁越界）

| 模块 | 职责 | 禁止 |
|---|---|---|
| `domain` | 领域模型、Repository 接口、UseCase | 禁止依赖 Android SDK、禁止依赖任何项目模块 |
| `camera-sdk` | 相机硬件抽象、OpenGL 渲染、AI 推理 | 禁止依赖 domain、feature、core 模块 |
| `feature:camera` | UI 界面、用户交互、ViewModel | 禁止直接操作 CameraX API（必须通过 camera-sdk） |
| `core:common` | 通用工具类、扩展函数 | 禁止依赖 Android 框架（纯 Kotlin/JVM） |
| `core:ui` | 通用 UI 组件、主题 | 禁止包含业务逻辑 |
| `data:*` | Repository 接口的实现 | 禁止包含 UI 代码 |

### 依赖方向规则

- **单向依赖**：上层可以依赖下层，下层不能依赖上层
- **同层不依赖**：feature 模块之间不能互相依赖
- **domain 是核心**：所有业务逻辑的契约定义在 domain 层

---

## 代码规范

### Kotlin 代码风格

```kotlin
// ✅ 正确：使用 4 空格缩进
class MyClass {
    fun myMethod() {
        // 实现
    }
}

// ✅ 正确：函数命名用驼峰
fun takePhoto() { }

// ✅ 正确：常量用大写下划线
companion object {
    private const val TAG = "CameraFragment"
}

// ❌ 错误：不要使用硬编码的魔法数字
beautyLevel = 0.7f  // 应定义为常量 DEFAULT_BEAUTY_LEVEL
```

### 命名规范

| 类型 | 规范 | 示例 |
|---|---|---|
| 类名 | 大驼峰 | `BeautyGLRenderer` |
| 函数名 | 小驼峰 | `takePhoto()` |
| 常量 | 大写下划线 | `MAX_BEAUTY_LEVEL` |
| 布局文件 | 小写下划线 | `fragment_camera.xml` |
| ID 命名 | 小写下划线 | `button_take_photo` |
| 包名 | 全小写 | `com.mary.mypx.sdk` |

### 注释规范

```kotlin
/**
 * 拍照 - 截取当前渲染帧
 * 
 * 【执行流程】
 * 1. 调用 OpenGL 截取帧缓冲区
 * 2. 生成 Bitmap（已包含美颜效果）
 * 3. 回调到主线程
 * 
 * @param callback 拍照完成回调
 */
fun takePhoto(callback: (Bitmap) -> Unit) { }
```

- **类**：必须有 KDoc 注释，说明职责和使用方式
- **公开方法**：必须有 KDoc 注释，说明参数和返回值
- **复杂逻辑**：必须有行内注释说明意图
- **禁止**：不要添加无意义的注释（如 `// 设置变量`）

---

## 架构约束

### Clean Architecture 规则

1. **Domain 层只能定义接口**，不能有实现
2. **ViewModel 只能调用 UseCase**，不能直接调用 Repository 实现
3. **UI 层只能观察 ViewModel 的 StateFlow/LiveData**，不能直接修改数据

### 线程模型规则

```
主线程：UI 更新、用户交互
IO 线程：文件读写、网络请求
渲染线程：所有 OpenGL 操作必须在同一个线程
```

- OpenGL 操作**严禁跨线程**：所有 GL 命令必须在渲染线程执行
- 跨线程通信使用 `@Volatile` 标志或 `Handler`
- 耗时操作使用 `withContext(Dispatchers.IO)`

### 资源管理规则

- `SurfaceTexture`、`Bitmap` 等必须在使用完毕后 `release()` 或 `recycle()`
- Fragment 的 `_binding` 必须在 `onDestroyView()` 中置空
- 相机资源必须在 `onDestroyView()` 中释放

---

## 开发流程

### 新增功能的标准流程

1. **Domain 层**：定义 Model、Repository 接口、UseCase
2. **Data 层**：实现 Repository 接口
3. **SDK 层**：实现硬件相关逻辑（如需要）
4. **Feature 层**：实现 UI 和 ViewModel
5. **测试**：编写单元测试和 UI 测试

### 修改现有代码的规则

1. **先读再改**：修改前必须先 Read 相关文件，理解上下文
2. **保持架构**：不能为了方便而绕过架构层次
3. **最小改动**：只修改必要的代码，不要重构不相关的部分
4. **验证编译**：修改后必须确认代码能编译通过

---

## 禁止事项

1. **禁止在 feature 层直接使用 CameraX API**（必须通过 camera-sdk）
2. **禁止在 domain 层添加 Android 依赖**
3. **禁止在渲染线程执行 IO 操作**
4. **禁止硬编码密钥、Token 等敏感信息**
5. **禁止删除已有的 KDoc 注释**
6. **禁止引入项目未使用的新框架**（如需引入，先与用户确认）

---

## 测试规范

```kotlin
// 单元测试命名：被测方法_场景_期望结果
@Test
fun captureCurrentFrame_whenWidthIsZero_returnsNull() {
    // Given
    renderer.width = 0
    
    // When
    val result = renderer.captureCurrentFrame()
    
    // Then
    assertNull(result)
}
```

- 测试文件放在对应模块的 `src/test/` 目录
- 使用 JUnit 4 + Mockito
- 遵循 Given-When-Then 模式

---

## 提交规范

```
<type>(<scope>): <subject>

类型：
- feat: 新功能
- fix: 修复 bug
- refactor: 重构（不改变功能）
- docs: 文档更新
- style: 代码格式调整
- test: 添加测试
- chore: 构建/工具变更

示例：
feat(camera): 添加美颜强度实时调节
fix(renderer): 修复 OpenGL 截帧 Y 轴翻转问题
refactor(viewmodel): 将状态管理改为 StateFlow
```

---

## 常用命令

| 操作 | 命令 |
|---|---|
| 编译检查 | `./gradlew assembleDebug` |
| 运行单元测试 | `./gradlew testDebugUnitTest` |
| 代码检查 | `./gradlew lint` |
| 清理构建 | `./gradlew clean` |
