# JEOMS TCC Navigation

Java 调用处按 Cmd 点击 prepare commit rollback 时 使用 IDEA 原生选择框选择对应的 do 方法或原模板入口
Option Cmd B 或右键 Go To > Implementation 跳转到对应的 do 方法
沿用 IDEA 原有 Go to Implementation 动作及用户快捷键配置 普通调用保留原生行为

在实现类的 doPrepare doCommit doRollback 上使用 Find Usages 或 Show Usages
结果保留直接引用 并补充静态类型能够确定分派到该实现的 prepare commit rollback 调用
继承该实现的子类调用也会包含 覆盖该实现的子类和其他实现的调用不会混入
接口或基类变量的运行时类型无法确定时 不添加为具体实现的调用
反向查找遵守 IDEA 选择的搜索范围 不改变重命名行为

支持具体类型变量 this 隐式调用 多层继承 泛型覆盖及方法引用
只沿静态接收类型的继承链定位 不扫描项目 不维护缓存 不依赖 jeoms 或 Seata 运行时
接口或基类类型无法确定具体实现时 保留 IDEA 默认导航 可使用 Go to Implementation
索引期间保留 IDEA 默认行为 当前不提供 Kotlin 或远程开发前后端分离模式支持

## 构建

需要 JDK 25

```sh
./gradlew test buildPlugin
./gradlew test buildPlugin -PlocalIdePath="/path/to/IntelliJ IDEA.app"
./gradlew runIde -PlocalIdePath="/path/to/IntelliJ IDEA.app"
./gradlew verifyPlugin -PlocalIdePath="/path/to/IntelliJ IDEA.app"
```

安装 build/distributions/jeoms-tcc-plugin-1.0.2.zip
在 Settings > Plugins > 齿轮 > Install Plugin from Disk 中选择该文件

## 兼容性

最低 IDEA 2026.2 平台 262 不设置 until-build
不限制未来安装不等于保证未来 API 兼容 发布新版前运行 ./gradlew verifyPlugin
采用 JetBrains 官方 IntelliJ Platform Gradle Plugin 与 Java PSI API

https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
