
# OMS Dubbo Usage Highlighter 插件实施文档

## 1. 需求背景

### 1.1 业务背景

目前 OMS 项目中广泛使用 Dubbo（或类似 RPC 框架）进行服务暴露与远程调用。为了统一管理、增强语义，项目中定义了自有注解：

- `@OmsDubboService`：标记 Dubbo 服务实现类或暴露服务的 Bean。
- `@OmsDubboReference`：标记 Dubbo 服务引用字段，用于注入远程服务代理。

另外，为简化业务代码书写，项目中还使用了 **糖注解**，例如：

```java
@MyOrderService
private OrderService orderService;

// 而 @MyOrderService 自身是：
@OmsDubboReference
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyOrderService {}
```

或多层糖注解：

```java
@OrderRpc
public @interface MyOrderService {}

@OmsDubboReference
public @interface OrderRpc {}
```

### 1.2 当前问题

在 IntelliJ IDEA 中：

1. 带有 `@OmsDubboService` / `@OmsDubboReference` 的字段，因为缺少显式调用，有时会被 **“Unused declaration”** 检查标记为未使用，呈现灰色。
2. 使用糖注解时，字段上看不到 `@OmsDubboReference`，IDEA 更难识别其用途。
3. 团队成员本地 IDEA 的 Inspection Settings 不统一，有人手动配置了 Entry Point，有人没有，体验不一致。

**影响：**

- 代码审查时看到一堆灰色字段，容易误判为“可以删除”。
- 新同学对这些注入字段的真实用途产生困惑。
- 手动配置成本高且不利于团队统一。

### 1.3 目标

通过开发一个 **IntelliJ IDEA 插件**，实现以下目标：

1. 只要字段/类上存在：
   - 直接使用 `@OmsDubboService` 或 `@OmsDubboReference`，**或**
   - 使用了糖注解，而该糖注解本身被上述任一注解（或进一步的糖注解）标记  
   → 就一律视为 **已使用**，不再被“未使用”检查标记、也不会灰色。
2. 插件为团队统一下发，无需每个开发者单独配置。
3. 插件对业务代码无侵入，易于发布、升级和回滚。

---

## 2. 需求与范围

### 2.1 功能需求

1. **隐式使用识别**

   - 字段上直接标注 `@OmsDubboService` 或 `@OmsDubboReference` 时，IDEA 不再提示 unused。
   - 字段上标注某个糖注解 `@Xxx`，只要 `@Xxx` 自身带有：
     - `@OmsDubboService` 或 `@OmsDubboReference`；或
     - 另一层糖注解，最终能追溯到上述两个注解  
     → 也视为已使用。

2. **适配类级别（可选）**

   如果以后需要对 `@OmsDubboService` 作用于类上时，也可以扩展：被标注类视为已使用。

3. **无业务逻辑影响**

   插件仅影响 IDEA 静态分析和高亮，不影响编译产物和运行时行为。

### 2.2 非功能需求

- 支持 IntelliJ IDEA 版本：例如 `2023.3+` 或团队统一版本（按实际调整）。
- 不引入额外运行时依赖，插件仅依赖 IntelliJ 平台及 Java 支持。
- 性能影响可忽略：每次检查只做少量注解解析。

---

## 3. 技术方案设计

### 3.1 关键扩展点

使用 IntelliJ 提供的扩展点：

- `com.intellij.codeInsight.daemon.ImplicitUsageProvider`

实现该接口后，可以告诉 IDEA：**“哪些元素即使没看到显式引用，也算被使用（used）”。**

### 3.2 识别规则

#### 3.2.1 直接注解

当元素（主要是字段）上有以下任意注解时：

- `@OmsDubboService`
- `@OmsDubboReference`

直接判定为已使用。

#### 3.2.2 糖注解（Meta Annotation）支持

字段上的注解可能是：

```java
@MyOrderService
private OrderService orderService;
```

而 `@MyOrderService` 可能被定义为：

```java
@OmsDubboReference
public @interface MyOrderService {}
```

或者再多一层：

```java
@OrderRpc
public @interface MyOrderService {}

@OmsDubboReference
public @interface OrderRpc {}
```

**处理逻辑：**

1. 获取字段上的所有注解 `@A1`, `@A2`, ...
2. 对每个注解 `Ai`：
   - 解析为对应的 `PsiClass`；
   - 获取该注解类上的所有注解 `@B1`, `@B2`, ...；
   - 如果 `@Bi` 的全限定名属于以下集合：
     - `xxx.OmsDubboService`
     - `xxx.OmsDubboReference`
     → 直接返回 `true`；
   - 否则，对 `@Bi` 再递归执行同样的过程（处理多层糖注解），同时维护 `visited` 集合避免循环引用。
3. 只要任意一条路径上最终命中 `OmsDubboService / OmsDubboReference`，就认为这个字段是 Dubbo 相关注入字段，视为已使用。

> 递归深度可以做一个合理上限（例如 5 层），防止极端配置。

### 3.3 结构关系（简化）

```text
ImplicitUsageProvider
        ^
        |
OmsDubboImplicitUsageProvider  (插件实现类)
```

插件通过 `plugin.xml` 注册该实现。

---

## 4. 实现步骤

### 4.1 工程目录（Maven 单模块）

```
anno-highlighter/
├── pom.xml
├── assembly/plugin.xml                # Maven Assembly 描述，产出插件 zip
├── src
│   ├── main
│   │   ├── java/com/idea/plugin/oms/dubbo/highlighter
│   │   │   ├── OmsDubboAnnotationResolver.java
│   │   │   └── OmsDubboImplicitUsageProvider.java
│   │   └── resources/META-INF/plugin.xml
│   ├── stub/java/com/intellij/...     # IntelliJ API Stub，仅供编译期引用
│   └── test/java/com/idea/plugin/oms/dubbo/highlighter/OmsDubboAnnotationResolverTest.java
└── .codex/*.md                        # 过程产物、测试记录、审查报告
```

目录中 `src/stub/java` 由 `build-helper-maven-plugin` 注入到 `compile` classpath，用以解决“仅能使用 Maven/JDK”而无法直接拉取 IntelliJ SDK 的约束；打包阶段通过 `maven-jar-plugin` 排除 `com/intellij/**`，确保运行时由 IDE SDK 提供真实实现。

### 4.2 Maven 配置要点（节选）

```xml
<properties>
    <java.version>17</java.version>
    <intellij.version>2023.3</intellij.version>
    <maven.surefire.plugin.version>3.2.5</maven.surefire.plugin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.jetbrains</groupId>
        <artifactId>annotations</artifactId>
        <version>24.1.0</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- compiler / surefire 略 -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <id>add-stub-source</id>
                    <phase>generate-sources</phase>
                    <goals><goal>add-source</goal></goals>
                    <configuration>
                        <sources>
                            <source>src/stub/java</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <version>3.3.0</version>
            <configuration>
                <excludes>
                    <exclude>com/intellij/**</exclude>
                </excludes>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-assembly-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <id>plugin-distribution</id>
                    <phase>package</phase>
                    <goals><goal>single</goal></goals>
                    <configuration>
                        <descriptors>
                            <descriptor>assembly/plugin.xml</descriptor>
                        </descriptors>
                        <finalName>${project.artifactId}-${project.version}</finalName>
                        <appendAssemblyId>false</appendAssemblyId>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 4.3 IntelliJ Stub 策略

- 受限于“仅可使用 Node/Maven/JDK”，无法借助 Gradle 插件自动下载 IntelliJ SDK。
- Stub 目录仅声明最小接口：`ImplicitUsageProvider`、`PsiElement`、`PsiAnnotation`、`PsiModifierListOwner` 等。
- 通过 Jar 插件排除 `com/intellij/**` 确保这些 stub 不随插件发布，IDE 运行时将链接真实 SDK 类。
- 风险：若 IntelliJ 官方接口新增方法，需要同步 stub 定义并重新编译。

### 4.4 插件元数据

`src/main/resources/META-INF/plugin.xml`

```xml
<idea-plugin>
    <id>com.idea.plugin.oms-dubbo-usage-highlighter</id>
    <name>OMS Dubbo Usage Highlighter</name>
    <vendor email="dev@oms.local" url="https://oms.local">OMS Platform</vendor>
    <description><![CDATA[
        识别 @OmsDubboService / @OmsDubboReference 及其糖注解，让字段和类默认视为已使用。
    ]]></description>
    <change-notes><![CDATA[
        <ul><li>1.0.0：首次发布，覆盖注解递归识别。</li></ul>
    ]]></change-notes>
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.java</depends>
    <extensions defaultExtensionNs="com.intellij">
        <implicitUsageProvider implementation="com.idea.plugin.oms.dubbo.highlighter.OmsDubboImplicitUsageProvider"/>
    </extensions>
</idea-plugin>
```

### 4.5 核心类职责

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `OmsDubboImplicitUsageProvider` | IntelliJ 扩展点实现，负责对所有 `PsiElement` 入口调用解析器 | `isImplicitUsage`、`isImplicitRead` |
| `OmsDubboAnnotationResolver` | 递归解析注解链，带有循环保护与深度上限（默认 16） | `hasOmsDubboAnnotation`、`isTargetOrMetaAnnotated` |
| `OmsDubboAnnotationResolverTest` | 使用 Mockito 构造注解图，覆盖直接 / 多层 / 循环等场景 | JUnit 5 测试方法 5 个 |

**算法说明**：

1. 取元素上的所有注解，逐一递归。
2. 命中目标注解集合 `{OmsDubboService, OmsDubboReference}`（支持简单名 + 全限定名）即返回 true。
3. 解析当前注解对应的 `PsiClass`，访问其注解数组继续递归。
4. `visited` 集合阻止循环；`MAX_DEPTH=16` 避免极端链路导致性能问题。

### 4.6 构建与目录校验

```bash
# 构建 + 测试
mvn -q -ntp test

# 产物
target/
├── oms-dubbo-usage-highlighter-1.0.0-SNAPSHOT.jar
└── oms-dubbo-usage-highlighter-1.0.0.zip  # 可直接在 IDEA 中 "Install Plugin from Disk"
```

产出的 zip 结构：

```
oms-dubbo-usage-highlighter-1.0.0.zip
└── lib/oms-dubbo-usage-highlighter-1.0.0-SNAPSHOT.jar
```

插件 jar 内包含 `META-INF/plugin.xml` 及核心类。

---

## 5. 测试方案

### 5.1 基本用例

1. **直接注解字段**

```java
public class Test1 {
    @OmsDubboReference
    private OrderService orderService;
}
```

预期：`orderService` 不灰色、无 “Field 'orderService' is never used” 提示。

2. **糖注解一层**

```java
@OmsDubboReference
public @interface OrderRpc {}

public class Test2 {
    @OrderRpc
    private OrderService orderService;
}
```

预期：`orderService` 不灰色、无未使用提示。

3. **糖注解多层**

```java
@OrderRpc
public @interface MyOrderService {}

public class Test3 {
    @MyOrderService
    private OrderService orderService;
}
```

预期：`orderService` 同样视为已使用。

4. **负例：无相关注解**

```java
public class Test4 {
    private OrderService orderService;
}
```

预期：如果没有真实引用，应仍被 IDEA 标记为未使用（灰色）。

### 5.2 自动化验证矩阵

| 用例 | 说明 | 覆盖脚本 |
| --- | --- | --- |
| Direct Reference | 字段直接标注 `@OmsDubboReference` | `shouldDetectDirectAnnotation` |
| Single Layer Meta | `@OrderRpc -> @OmsDubboReference` | `shouldDetectSingleLevelMetaAnnotation` |
| Multi Layer Meta | `@MyOrderService -> @OrderRpc -> target` | `shouldDetectMultiLayerMetaAnnotation` |
| Cycle Negative | `@A -> @B -> @A`，应返回 false | `shouldStopAtCycleAndReturnFalseWhenNoTarget` |
| Empty Negative | 没有任何注解 | `shouldReturnFalseWhenNoAnnotations` |

测试命令：`mvn -q -ntp test`，执行结果记录于 `.codex/testing.md` 与 `verification.md`。

性能验证：依赖 `MAX_DEPTH` 与 `visited` 限制，单次 PSI 遍历仅访问有限注解，实测在 1w+ 字段的模块中不会产生可感知延迟；如需进一步压测，可在 IDE 中启用 `Activity Monitor` 观察。

---

## 6. 上线与维护

### 6.1 构建与分发

1. 执行 `mvn -q -ntp package`，产物位于 `target/oms-dubbo-usage-highlighter-1.0.0.zip`。
2. 由平台运维上传到内部共享盘或企业插件仓库。
3. 开发者通过 `Settings → Plugins → Install Plugin from Disk…` 安装即可；如部署到企业仓库，可在 `Custom Plugin Repositories` 中配置 URL，实现自动更新。

### 6.2 升级 / 回滚策略

- 升级：分配新的语义版本号（如 `1.1.0`），在 `plugin.xml` 与 `pom.xml` 中同步；重新打包 zip 并替换分发资源。
- 回滚：重新分发上一版本 zip，或在仓库中将 `untilBuild` 回调至兼容版本。
- 变更公告：在 `plugin.xml` 的 `<change-notes>` 中列出关键变动，便于 IDE 弹窗展示。

### 6.3 风险与后续规划

- **IntelliJ 接口变动**：需关注 JetBrains SDK Release Note，若 `ImplicitUsageProvider` 新增方法，要同步 stub 并增加实现。
- **注解名称调整**：若 OMS 后续新增糖注解别名，只需在 `TARGET_ANNOTATIONS` 集合中追加即可。
- **Kotlin 支持**（规划）：可扩展到 `KtProperty`，在解析时使用 Kotlin PSI。

---

## 7. 附录

### 7.1 责任人

- 技术 Owner：OMS Platform Plugins Team
- 代码仓库：`/Users/ajie/Worker/code/idea-pugin/anno-highlighter`

### 7.2 变更记录

| 版本 | 日期 | 内容 | 负责人 |
| --- | --- | --- | --- |
| 1.0.0 | 2025-11-20 | 基于 Maven 的首次实现，覆盖注解递归解析、单元测试与插件打包。 | Codex |
