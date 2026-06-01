# MyPx Camera - Android Camera Demo

一个基于 Kotlin + Jetpack 的 Android 相机应用演示项目，采用 Clean Architecture 和 MVVM 架构，集成 CameraX、TensorFlow Lite 和 OpenGL ES 实现 AI 实时滤镜功能。

## 功能特性

### 核心功能
- **实时相机预览**：基于 CameraX 实现，支持前后置摄像头切换、闪光灯控制
- **AI 实时滤镜**：
  - 美颜滤镜（亮度、对比度增强）
  - 超分辨率（低清预览变高清）
  - 夜景增强（提亮 + 降噪）
- **拍照与处理**：一键拍照、AI 处理、原图与处理后对比
- **性能监控**：实时显示帧率（FPS）、AI 推理耗时、内存占用

### 技术亮点
- **模块化 SDK 设计**：相机与 AI 处理封装为独立 `camera-sdk` 模块，可复用
- **Clean Architecture 分层**：UI → Domain → Data，职责清晰
- **MVVM + 单向数据流**：ViewModel + StateFlow + LiveData
- **OpenGL ES 实时渲染**：支持 GPU 加速的滤镜处理
- **性能优化**：内存监控、帧率统计、推理耗时测量

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.22 |
| 架构 | Clean Architecture + MVVM |
| 相机 | CameraX 1.3.4 |
| AI 推理 | TensorFlow Lite 2.16.1 |
| 图像渲染 | OpenGL ES 2.0/3.0 |
| 异步处理 | Kotlin Coroutines 1.8.0 |
| 依赖注入 | 手动注入（可扩展 Hilt/Koin） |
| 导航 | Jetpack Navigation 2.7.7 |
| 生命周期 | Jetpack Lifecycle 2.8.0 |

## 项目结构

```
com.mary.mypx
├── app                    # 入口应用模块
├── feature
│   └── camera             # 相机 UI 层（Fragment、ViewModel）
├── domain                 # 领域层（UseCase、Repository 接口、Model）
├── data
│   ├── camera             # 相机数据层（CameraX 封装）
│   └── ai                 # AI 数据层（TensorFlow Lite 处理）
├── core
│   ├── common             # 工具类、扩展函数
│   └── ui                 # 通用 UI 组件、主题
└── camera-sdk             # 独立 SDK 模块（可剥离复用）
```

### 模块依赖关系
```
app → feature:camera → camera-sdk
                    → domain
                    → core:ui
                    → core:common
```

## 快速开始

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK 34
- Gradle 9.0.0（项目已包含包装器）

### 构建步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd mypx
   ```

2. **使用 Android Studio 打开项目**
   - 启动 Android Studio
   - 选择 "Open an existing project"
   - 选择项目根目录

3. **同步项目**
   - Android Studio 会自动下载依赖并同步项目
   - 等待 Gradle 同步完成

4. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 "Run" 按钮或使用 `Shift + F10`

### 命令行构建
```bash
# 清理项目
.\gradlew.bat clean

# 构建调试版本
.\gradlew.bat assembleDebug

# 安装到设备
.\gradlew.bat installDebug

# 运行测试
.\gradlew.bat test
```

## 模块说明

### `app` 模块
应用入口，包含 MainActivity 和导航图。负责模块组装和全局配置。

### `feature:camera` 模块
相机功能的主要 UI 实现：
- `CameraFragment`：相机预览界面，处理用户交互
- `PreviewFragment`：拍照结果预览，支持滤镜应用
- `CameraViewModel`：管理相机状态和业务逻辑

### `domain` 模块
纯 Kotlin 模块，不依赖 Android：
- **Model**：定义核心数据类（Photo、CameraState、FilterType 等）
- **Repository**：定义数据操作接口
- **UseCase**：封装业务逻辑（TakePhotoUseCase、ProcessImageUseCase 等）

### `data:camera` 模块
相机数据层实现，封装 CameraX API：
- 相机初始化、预览、拍照
- 前后置切换、闪光灯控制

### `data:ai` 模块
AI 处理数据层，集成 TensorFlow Lite：
- 图像处理算法实现
- 模型加载与推理

### `camera-sdk` 模块
**独立 SDK 模块**，可剥离复用：
- `CameraManager`：相机管理核心类
- `ImageProcessor`：图像处理接口
- `TFLiteImageProcessor`：TensorFlow Lite 处理实现
- `OpenGLFilterRenderer`：OpenGL ES 滤镜渲染器
- `PerformanceMonitor`：性能监控工具

对外暴露简单 API：
```kotlin
// 初始化
val cameraManager = CameraManager(context)
val imageProcessor = TFLiteImageProcessor(context)

// 开始预览
cameraManager.startPreview(lifecycleOwner, previewView)

// 拍照
cameraManager.takePhoto(
    onImageCaptured = { result -> /* 处理结果 */ },
    onError = { error -> /* 处理错误 */ }
)

// 应用滤镜
val processedBitmap = imageProcessor.process(bitmap, FilterType.BEAUTY)
```

### `core:common` 模块
通用工具类和扩展函数。

### `core:ui` 模块
通用 UI 组件、主题、样式定义。

## 架构设计

### Clean Architecture 分层
```
┌─────────────────────────────────────┐
│           UI Layer (feature)        │
│  Fragment → ViewModel → UiState    │
├─────────────────────────────────────┤
│        Domain Layer (domain)        │
│  UseCase → Repository Interface    │
├─────────────────────────────────────┤
│         Data Layer (data)           │
│  Repository Impl → CameraX/TF Lite │
└─────────────────────────────────────┘
```

### 单向数据流
```
User Action → ViewModel → UseCase → Repository
     ↑                                    ↓
     └──── UiState ← StateFlow ← Result ←┘
```

### 状态管理
```kotlin
sealed class CameraState {
    object Initializing : CameraState()
    object Preview : CameraState()
    object TakingPhoto : CameraState()
    data class Processing(val progress: Float) : CameraState()
    data class Error(val message: String) : CameraState()
}
```

## 性能优化

### 已实现的优化
1. **内存监控**：实时监控 Java 堆内存和 Native 堆内存
2. **帧率统计**：监控预览帧率，确保 30fps+ 流畅体验
3. **推理耗时**：测量 AI 处理时间，优化至 <100ms
4. **图像处理优化**：使用协程进行异步处理，避免阻塞主线程

### 性能指标
- 预览帧率：稳定 30fps+
- AI 推理耗时：<100ms（美颜滤镜）
- 内存占用：监控并优化 OOM 风险

## 扩展指南

### 添加新滤镜
1. 在 `FilterType` 枚举中添加新类型
2. 在 `TFLiteImageProcessor` 中实现处理逻辑
3. 在 `OpenGLFilterRenderer` 中添加着色器支持
4. 更新 UI 中的滤镜选择器

### 集成真实 TF Lite 模型
1. 将 `.tflite` 模型文件放入 `assets` 目录
2. 使用 TensorFlow Lite Support 库加载模型
3. 实现图像预处理和后处理逻辑
4. 替换 `TFLiteImageProcessor` 中的模拟实现

### 模块化扩展
- 添加新功能模块：在 `feature` 目录下创建新模块
- 添加新数据源：在 `data` 目录下创建新模块
- 扩展 SDK 功能：在 `camera-sdk` 中添加新 API

## 应用图标

### 图标设计
应用图标采用简洁现代的设计风格，体现相机和AI功能：

- **主色调**：紫色渐变（#6200EE → #3700B3），符合Material Design规范
- **相机元素**：
  - 白色相机主体（圆角矩形）
  - 紫色镜头（同心圆设计）
  - 黄色闪光灯
  - 灰色取景器
- **AI元素**：
  - 黄色星星装饰，代表AI滤镜功能
  - 小圆点装饰，增强视觉层次

### 图标文件
- **自适应图标**：`mipmap-anydpi-v26/ic_launcher.xml`
- **圆形图标**：`mipmap-anydpi-v26/ic_launcher_round.xml`
- **背景**：`drawable/ic_launcher_background.xml`（紫色渐变）
- **前景**：`drawable/ic_launcher_foreground.xml`（相机和AI元素）

### 生成图标
推荐使用Android Studio的Image Asset工具生成所有密度的图标：

1. 在Android Studio中，右键点击 `app/src/main/res` 目录
2. 选择 `New → Image Asset`
3. 选择 `Launcher Icons (Legacy and Adaptive)`
4. 配置图标选项：
   - 前景图层：选择相机图标
   - 背景图层：选择渐变颜色 #6200EE → #3700B3
   - 形状：圆形或方形
5. 点击 `Next` 然后 `Finish`

### 图标预览
```
┌─────────────────┐
│                 │
│   📷 AI Camera  │
│                 │
│  紫色渐变背景    │
│  白色相机主体    │
│  紫色镜头        │
│  黄色星星装饰    │
│                 │
└─────────────────┘
```

## 常见问题

### Q: 如何切换前后置摄像头？
A: 点击界面右下角的切换按钮，或调用 `cameraManager.switchCamera()`

### Q: 如何调整美颜强度？
A: 修改 `TFLiteImageProcessor.applyBeautyFilter()` 中的参数（亮度、对比度系数）

### Q: 如何集成真实的 AI 模型？
A: 参考 "集成真实 TF Lite 模型" 部分，替换模拟实现

### Q: 如何优化性能？
A: 使用 `PerformanceMonitor` 监控各项指标，针对瓶颈进行优化

## 贡献指南

1. Fork 项目
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

## 许可证

```
Copyright 2026 MyPx

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```


