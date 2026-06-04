# MyPx Camera - Android 相机应用

一款基于 Kotlin + Jetpack 的 Android 相机应用，核心功能是基于 OpenGL ES 的实时美颜渲染。采用 Clean Architecture + MVVM 架构，支持拍照、美颜调节、照片预览和 GitHub 云存储。

## 功能演示

https://github.com/user-attachments/assets/demo.mp4

<video src="docs/demo.mp4" controls width="300"></video>

## 功能特性

### 核心功能
- **实时相机预览**：基于 CameraX 实现，支持前后置摄像头切换、闪光灯控制
- **OpenGL 实时美颜**：
  - 双边滤波（Bilateral Filter）磨皮
  - 亮度调整（提亮肤色）
  - 对比度增强（立体感）
  - 饱和度调整（红润肤色）
  - 可调节强度（0% - 100%）
- **拍照与预览**：一键拍照、实时美颜效果预览、原图对比
- **照片保存**：保存到系统相册
- **GitHub 云上传**：将照片上传到 GitHub 仓库（需配置 Token）

### 技术亮点
- **OpenGL ES 2.0 实时渲染**：GPU 加速的美颜处理，30fps+ 流畅体验
- **模块化 SDK 设计**：相机与 OpenGL 渲染封装为独立 `camera-sdk` 模块
- **Clean Architecture 分层**：UI → Domain → Data，职责清晰
- **MVVM + 单向数据流**：ViewModel + StateFlow
- **跨线程安全**：渲染线程与主线程通过 `@Volatile` 标志通信

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 架构 | Clean Architecture + MVVM |
| 相机 | CameraX |
| 图像渲染 | OpenGL ES 2.0（双边滤波美颜） |
| 网络请求 | Retrofit 2.9 + OkHttp 4.12 |
| 异步处理 | Kotlin Coroutines |
| 导航 | Jetpack Navigation |
| 生命周期 | Jetpack Lifecycle |
| UI | ViewBinding + Material Design |

## 项目结构

```
mypx
├── app                    # 入口应用模块
├── feature
│   └── camera             # 相机 UI 层（Fragment、ViewModel、网络）
├── domain                 # 领域层（Repository 接口、Model）
├── data
│   ├── camera             # 相机数据层
│   └── ai                 # AI 数据层
├── core
│   ├── common             # 工具类、扩展函数
│   └── ui                 # 通用 UI 组件、主题
└── camera-sdk             # 独立 SDK 模块（OpenGL 渲染、相机管理）
```

### 模块依赖关系
```
app ──→ feature:camera ──→ camera-sdk (独立 SDK)
 │          │──→ domain (纯 Kotlin)
 │          │──→ core:ui
 │          └──→ core:common
 ├──→ core:ui
 └──→ core:common
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

2. **配置 GitHub Token（可选，用于图片上传）**
   
   在项目根目录创建 `local.properties` 文件，添加：
   ```properties
   sdk.dir=你的SDK路径
   GITHUB_TOKEN=你的GitHub Token
   ```
   
   > Token 生成地址：https://github.com/settings/tokens
   > 所需权限：`repo`（Full control of private repositories）

3. **使用 Android Studio 打开项目**
   - 启动 Android Studio
   - 选择 "Open an existing project"
   - 选择项目根目录

4. **同步项目**
   - Android Studio 会自动下载依赖并同步项目
   - 等待 Gradle 同步完成

5. **运行应用**
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
- `CameraFragment`：相机预览界面，处理用户交互（拍照、切换摄像头、闪光灯）
- `PreviewFragment`：拍照结果预览，支持美颜调节、保存、上传
- `CameraViewModel`：管理相机状态和业务逻辑
- `network/`：GitHub 上传相关（API、Service、Model）

### `domain` 模块
纯 Kotlin 模块，不依赖 Android：
- **Model**：定义核心数据类（CameraState、FilterType 等）
- **Repository**：定义数据操作接口

### `camera-sdk` 模块
**独立 SDK 模块**，可剥离复用：

- `BeautyTextureView`：核心预览视图，集成 OpenGL 渲染管线
  - EGL 环境管理
  - 渲染线程管理
  - SurfaceTexture 生命周期
  - 拍照截帧
- `BeautyGLRenderer`：OpenGL 美颜渲染器
  - 双边滤波着色器
  - 亮度/对比度/饱和度调节
  - OES 纹理处理
- `CameraManager`：CameraX 封装
  - 相机预览启动
  - 前后置切换
  - 闪光灯控制

### `core:common` 模块
通用工具类和扩展函数（纯 Kotlin/JVM）。

### `core:ui` 模块
通用 UI 组件、主题、样式定义。

## 架构设计

### OpenGL 渲染管线
```
CameraX 相机帧
     ↓
SurfaceTexture (OES 纹理)
     ↓
OpenGL 片段着色器（美颜处理）
  - 双边滤波（磨皮）
  - 亮度调整
  - 对比度增强
  - 饱和度调整
     ↓
TextureView 显示到屏幕
```

### Clean Architecture 分层
```
┌─────────────────────────────────────┐
│           UI Layer (feature)        │
│  Fragment → ViewModel → UiState    │
├─────────────────────────────────────┤
│        Domain Layer (domain)        │
│  Repository Interface → Model      │
├─────────────────────────────────────┤
│         Data Layer (data)           │
│  Repository Impl → CameraX/API     │
└─────────────────────────────────────┘
```

### 线程模型
```
主线程：UI 更新、用户交互、拍照回调
渲染线程：EGL 初始化、OpenGL 渲染、截帧
IO 线程：文件读写、网络请求
```

### 状态管理
```kotlin
sealed class CameraState {
    object Initializing : CameraState()
    object Preview : CameraState()
    object TakingPhoto : CameraState()
    data class Error(val message: String) : CameraState()
}
```

## GitHub 云上传

### 功能说明
支持将拍摄的照片上传到 GitHub 仓库，使用 GitHub Contents API。

### 配置步骤
1. 在 GitHub 生成 Personal Access Token
2. 在 `local.properties` 中配置 `GITHUB_TOKEN`
3. 重新构建项目

### 技术实现
- 使用 Retrofit 2.9 + OkHttp 4.12
- 图片转 Base64 编码
- 通过 PUT 请求上传到指定仓库目录
- Token 通过 BuildConfig 注入，不硬编码

## 性能优化

### 已实现的优化
1. **GPU 加速**：使用 OpenGL ES 进行美颜处理，比 CPU 处理快 10-100 倍
2. **实时渲染**：30fps+ 流畅预览
3. **内存管理**：及时释放 Bitmap、SurfaceTexture 等资源
4. **异步处理**：使用协程处理 IO 操作

### 美颜算法
使用双边滤波（Bilateral Filter）实现磨皮：
- 保留边缘（重要特征）
- 平滑皮肤（去除噪点）
- 可调节强度（0.0 - 1.0）

## 应用图标

### 图标设计
应用图标采用简洁现代的设计风格，体现相机和 AI 功能：

- **主色调**：紫色渐变（#6200EE → #3700B3），符合 Material Design 规范
- **相机元素**：白色相机主体、紫色镜头、黄色闪光灯
- **AI 元素**：黄色星星装饰，代表 AI 滤镜功能

### 图标文件
- **自适应图标**：`mipmap-anydpi-v26/ic_launcher.xml`
- **圆形图标**：`mipmap-anydpi-v26/ic_launcher_round.xml`
- **背景**：`drawable/ic_launcher_background.xml`（紫色渐变）
- **前景**：`drawable/ic_launcher_foreground.xml`（相机和 AI 元素）

## 常见问题

### Q: 如何切换前后置摄像头？
A: 点击界面右下角的切换按钮。

### Q: 如何调整美颜强度？
A: 在相机预览界面，拖动底部的"美颜"滑块（0% - 100%）。

### Q: 如何上传照片到 GitHub？
A: 在预览页面点击"上传"按钮。需要先在 `local.properties` 中配置 `GITHUB_TOKEN`。

### Q: 相机预览是白的/黑的？
A: 请检查相机权限是否已授予，以及设备是否支持 OpenGL ES 2.0。

## 贡献指南

1. Fork 项目
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'feat(scope): Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

### 提交规范
```
feat(scope): 新功能
fix(scope): 修复 bug
refactor(scope): 重构
docs: 文档更新
style: 代码格式调整
test: 添加测试
chore: 构建/工具变更
```

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
