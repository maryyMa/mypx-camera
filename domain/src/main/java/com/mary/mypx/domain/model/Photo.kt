package com.mary.mypx.domain.model

/**
 * 照片数据模型 - 领域层核心实体
 * 
 * 【设计原则】
 * 这是纯Kotlin数据类，不依赖任何Android框架类（如android.net.Uri）。
 * 这样做的好处是：
 * 1. 领域层可以在JVM上独立测试，不需要Android设备
 * 2. 领域层不依赖具体实现，便于替换存储方式（本地、云端等）
 * 3. 符合Clean Architecture的依赖规则：外层依赖内层，内层不知道外层
 * 
 * 【为什么用String而不是Uri？】
 * Uri是Android特有的类，如果在domain层使用，就会污染领域层。
 * 我们用String表示URI路径，在data层再转换为Uri。
 * 这样domain层保持纯净，只包含业务逻辑。
 * 
 * @param uriString 照片的URI字符串，用于定位照片文件位置
 * @param timestamp 照片拍摄时间戳，默认为当前时间，用于排序和显示
 * @param isProcessed 照片是否经过AI处理，用于区分原图和处理后的图
 * @param filterType 应用的滤镜类型，默认为NONE（无滤镜）
 */
data class Photo(
    val uriString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    val filterType: FilterType = FilterType.NONE
)

/**
 * 滤镜类型枚举 - 定义所有支持的AI滤镜
 * 
 * 【枚举的优势】
 * 1. 类型安全：编译时检查，避免传入无效的滤镜类型
 * 2. 可读性：代码中使用有意义的名称而不是魔法数字
 * 3. 可扩展：添加新滤镜只需添加新枚举值
 * 
 * 【滤镜说明】
 * - NONE: 无滤镜，显示原图
 * - BEAUTY: 美颜滤镜，提亮肤色、平滑皮肤
 * - SUPER_RESOLUTION: 超分辨率，将低清图片变高清
 * - NIGHT_MODE: 夜景增强，在暗光环境下提亮画面
 */
enum class FilterType {
    NONE,               // 无滤镜
    BEAUTY,             // 美颜滤镜
    SUPER_RESOLUTION,   // 超分辨率
    NIGHT_MODE          // 夜景增强
}
