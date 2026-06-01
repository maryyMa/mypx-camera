# 生成应用图标脚本
# 使用Android Studio的Image Asset工具生成图标

Write-Host "=== MyPx Camera 图标生成指南 ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "推荐方法：使用Android Studio Image Asset工具" -ForegroundColor Yellow
Write-Host "1. 在Android Studio中，右键点击 app/src/main/res 目录"
Write-Host "2. 选择 New → Image Asset"
Write-Host "3. 选择 'Launcher Icons (Legacy and Adaptive)'"
Write-Host "4. 选择 'Image' 作为图标类型"
Write-Host "5. 设置图标路径（如果需要）"
Write-Host "6. 配置图标选项："
Write-Host "   - 前景图层：选择相机图标"
Write-Host "   - 背景图层：选择渐变颜色 #6200EE → #3700B3"
Write-Host "   - 形状：圆形或方形"
Write-Host "7. 点击 'Next' 然后 'Finish'"

Write-Host ""
Write-Host "当前已创建的图标资源：" -ForegroundColor Green
Write-Host "- 自适应图标：mipmap-anydpi-v26/ic_launcher.xml"
Write-Host "- 自适应圆形图标：mipmap-anydpi-v26/ic_launcher_round.xml"
Write-Host "- 背景drawable：drawable/ic_launcher_background.xml"
Write-Host "- 前景drawable：drawable/ic_launcher_foreground.xml"
Write-Host "- 应用图标drawable：drawable/ic_app_icon.xml"

Write-Host ""
Write-Host "注意事项：" -ForegroundColor Yellow
Write-Host "1. 当前创建的图标是vector drawable格式"
Write-Host "2. 对于旧版本Android（<26），需要PNG格式图标"
Write-Host "3. 建议使用Android Studio生成所有密度的PNG图标"
Write-Host "4. 图标设计包含：相机镜头、AI星星装饰、紫色渐变背景"

Write-Host ""
Write-Host "图标设计说明：" -ForegroundColor Cyan
Write-Host "- 主色调：紫色渐变（#6200EE → #3700B3）"
Write-Host "- 相机元素：白色相机主体、紫色镜头、黄色闪光灯"
Write-Host "- AI元素：黄色星星装饰，代表AI滤镜功能"
Write-Host "- 整体风格：简洁现代，符合Material Design规范"

Write-Host ""
Write-Host "完成！" -ForegroundColor Green
Write-Host "图标已准备好，可以构建和运行应用。"