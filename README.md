# IDEA Plugins

面向 JEOMS / OMS 开发的 IntelliJ IDEA 插件集合。各插件独立构建和安装，根目录不提供聚合构建。

## 项目

| 目录 | 插件 | 功能 | 构建要求 | IDEA 平台范围 |
| --- | --- | --- | --- | --- |
| [anno-highlighter](anno-highlighter/) | OMS Dubbo Usage Highlighter | 支持自定义组合注解及多级元注解识别，避免相关 Java 类和字段被误报为未使用 | JDK 17、Maven | `233` 至 `253.*` |
| [jeoms-tcc-plugin](jeoms-tcc-plugin/README.md) | JEOMS TCC Navigation | TCC 模板方法与 `doPrepare`、`doCommit`、`doRollback` 之间的导航及反向用法查找 | JDK 25、Gradle Wrapper | `262` 起，未设置上限 |

兼容范围来自各插件配置，不代表已在所有版本完成验证。

## 构建

### OMS Dubbo Usage Highlighter

支持自定义组合注解：在自定义注解上标记 `@OmsDubboService`、`@OmsDubboReference`，或通过多级元注解间接关联，插件即可识别使用该自定义注解的 Java 类和字段。当前识别入口为这两个注解，不提供任意目标注解的设置页面。

使用 JDK 17，在仓库根目录执行：

```sh
cd anno-highlighter
mvn test package
```

该项目当前没有 Maven Wrapper，使用本机安装的 Maven。

安装包：`anno-highlighter/target/oms-dubbo-usage-highlighter-1.0.0-SNAPSHOT.zip`。

项目使用 `src/stub/java` 中的 IntelliJ API 占位类型编译和测试，打包时排除这些类型；单元测试不能替代真实 IDEA 中的安装验证。

### JEOMS TCC Navigation

使用 JDK 25，在仓库根目录执行：

```sh
cd jeoms-tcc-plugin
./gradlew test buildPlugin
```

Windows 使用 `gradlew.bat`。也可以指定本地 IDEA：

```sh
./gradlew test buildPlugin -PlocalIdePath="/path/to/IntelliJ IDEA.app"
```

安装包：`jeoms-tcc-plugin/build/distributions/jeoms-tcc-plugin-1.0.2.zip`。更多行为说明及验证命令见[插件 README](jeoms-tcc-plugin/README.md)。

## 安装

在 IDEA 的 `Settings > Plugins` 中打开齿轮菜单，选择 `Install Plugin from Disk`，选择对应 ZIP 文件，并按提示重启 IDE。

## 许可证

本仓库采用 [MIT License](LICENSE)。第三方依赖遵循各自许可证。
