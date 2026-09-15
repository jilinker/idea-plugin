# Verification Report (2025-11-20)

- **构建命令**：`mvn -q -ntp test`
- **结果**：通过。
- **环境**：macOS（提供的容器），JDK 17，Maven 3.9.x。
- **验证范围**：
  - `OmsDubboAnnotationResolver` 单元测试覆盖直接/多级注解及负例。
  - Maven Assembly 产物生成 `target/oms-dubbo-usage-highlighter-1.0.0.zip`。
- **遗留风险**：运行时需要 IntelliJ 平台提供真实 `com.intellij` 类，当前通过 Stub 方式只在编译期占位；部署时必须依赖 IDE SDK。
- **修复补充**（2025-11-20 11:10 UTC+8）：在 `plugin.xml` 中新增 `<version>` 和 `<idea-version>` 元素后重新运行 `mvn -q -ntp test` 与 `mvn -q -ntp package`，产出的 `target/oms-dubbo-usage-highlighter-1.0.0-SNAPSHOT.zip` 可被 IntelliJ 识别。
