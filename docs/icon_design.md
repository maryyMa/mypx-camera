# MyPx Camera 应用图标设计说明

## 设计理念
应用图标设计体现项目的核心功能：相机与AI滤镜。采用简洁现代的设计风格，符合Material Design规范。

## 设计元素

### 1. 色彩方案
- **主色调**：紫色渐变
  - 起始颜色：#6200EE（深紫色）
  - 结束颜色：#3700B3（深紫色）
  - 渐变角度：135度
- **辅助色**：
  - 白色：#FFFFFF（相机主体）
  - 灰色：#E0E0E0（相机部件）
  - 黄色：#FFD600（闪光灯、AI装饰）

### 2. 相机元素
- **相机主体**：白色圆角矩形，位于图标中央
- **镜头系统**：
  - 外圈：灰色圆环
  - 中圈：紫色圆环（#6200EE）
  - 内圈：深紫色圆点（#3700B3）
- **闪光灯**：黄色小圆点，位于左上角
- **取景器**：灰色矩形，位于顶部

### 3. AI装饰元素
- **星星**：两个黄色四角星，分别位于右上角和左下角
- **小圆点**：两个白色小圆点，增强视觉层次

## 图标结构

### 自适应图标（Android 8.0+）
```
┌─────────────────────────────┐
│                             │
│        背景图层              │
│      （紫色渐变）            │
│                             │
│        前景图层              │
│    （相机和AI元素）          │
│                             │
└─────────────────────────────┘
```

### 图标层次
1. **背景层**：紫色渐变（135度）
2. **前景层**：
   - 相机主体（白色）
   - 镜头系统（灰色、紫色）
   - 闪光灯（黄色）
   - 取景器（灰色）
   - AI装饰（黄色星星、白色圆点）

## 技术规格

### 尺寸
- **设计尺寸**：108dp × 108dp（自适应图标）
- **安全区域**：66dp × 66dp（中心区域）
- **边距**：四周各留18dp边距

### 格式
- **Android 8.0+**：自适应图标（XML vector drawable）
- **旧版本Android**：PNG图标（多种密度）

### 密度支持
- mdpi（48×48）
- hdpi（72×72）
- xhdpi（96×96）
- xxhdpi（144×144）
- xxxhdpi（192×192）

## 文件位置

### 自适应图标
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

### 图标资源
- `app/src/main/res/drawable/ic_launcher_background.xml`（背景）
- `app/src/main/res/drawable/ic_launcher_foreground.xml`（前景）
- `app/src/main/res/drawable/ic_app_icon.xml`（备用图标）

## 使用说明

### 在Android Studio中生成图标
1. 右键点击 `app/src/main/res` 目录
2. 选择 `New → Image Asset`
3. 选择 `Launcher Icons (Legacy and Adaptive)`
4. 配置图标选项：
   - 前景图层：选择相机图标
   - 背景图层：选择渐变颜色 #6200EE → #3700B3
   - 形状：圆形或方形
5. 点击 `Next` 然后 `Finish`

### 自定义图标
如需修改图标设计：
1. 编辑 `drawable/ic_launcher_foreground.xml` 修改前景元素
2. 编辑 `drawable/ic_launcher_background.xml` 修改背景颜色
3. 使用Android Studio重新生成图标

## 设计原则

### 1. 简洁性
- 避免过多细节，确保在小尺寸下清晰可辨
- 使用简单的几何形状，易于识别

### 2. 一致性
- 与项目整体设计风格保持一致
- 符合Material Design设计规范

### 3. 可识别性
- 在不同背景下都能清晰显示
- 在不同尺寸下都能保持辨识度

### 4. 品牌体现
- 体现项目核心功能（相机+AI）
- 使用项目主色调（紫色）

## 注意事项

1. **安全区域**：重要元素应放在安全区域内（66dp×66dp）
2. **对比度**：确保前景与背景有足够对比度
3. **测试**：在不同设备和背景下测试图标显示效果
4. **更新**：修改图标后记得更新所有密度的图标文件

## 参考资源

- [Android 图标设计指南](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
- [Material Design 图标规范](https://material.io/design/iconography/)
- [自适应图标官方文档](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)

---

**MyPx Camera** - 简洁现代的相机应用图标设计