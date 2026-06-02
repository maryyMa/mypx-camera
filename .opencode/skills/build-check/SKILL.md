# 编译检查技能

检查项目是否能正常编译，并运行基本的代码检查。

## 使用场景

- 修改代码后验证编译是否通过
- 提交代码前的检查
- 排查编译错误

## 执行步骤

1. 清理项目：`./gradlew clean`
2. 编译 Debug 版本：`./gradlew assembleDebug`
3. 运行 Lint 检查：`./gradlew lint`
4. 运行单元测试（如有）：`./gradlew testDebugUnitTest`

## 输出

- 编译成功：返回 "BUILD SUCCESSFUL"
- 编译失败：返回错误信息和修复建议
- Lint 警告：列出所有警告并给出修复建议
