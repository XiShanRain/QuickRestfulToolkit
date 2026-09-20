# QuickRestfulToolkit

![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3%2B-blue)
![JDK](https://img.shields.io/badge/JDK-21-orange)
![Language](https://img.shields.io/badge/Language-Kotlin-7f52ff)

一个用于 IntelliJ IDEA 的插件，支持根据请求 URL 或路径快速跳转到对应的后端 REST Controller 方法。

QuickRestfulToolkit 是开源项目 [RestfulToolkitPlus](https://github.com/shenszq/RestfulToolkitPlus)（Java 版）的 **Kotlin 复刻重写版**。它保留了原项目的核心能力与交互方式，并全程改用 Kotlin 实现，目标很简单：当你已经知道接口地址时，尽可能快地帮你定位到服务端入口代码。插件支持 Spring MVC / Spring Boot、JAX-RS，同时兼容 Java 与 Kotlin 控制器项目。

> 关于名称：`Quick` 取自"快速定位"之意——本插件的核心目标就是当你已知接口地址时，能尽快跳转到服务端入口代码。它是 Java 版 RestfulToolkitPlus 的 Kotlin 复刻重写版。

## 功能特性

- 支持通过完整 URL、相对路径或纯接口路径搜索控制器方法
- 自动规范化输入内容，去掉协议、域名、端口、查询参数和片段标识
- 支持 Spring MVC / Spring Boot 和 JAX-RS 接口扫描
- 支持 Java 与 Kotlin 控制器
- 支持在搜索弹窗中按 HTTP Method 过滤结果
- 支持仅搜索当前模块
- 优先复用编辑器选中文本或剪贴板中的 URL 作为初始搜索内容
- 兼容 Kotlin K2 模式，不依赖脆弱的 Kotlin 内部索引实现
- 纯 Kotlin 实现，遵循 Kotlin 惯用写法（属性访问、作用域函数、`entries`、空安全等）

## 适用场景

在中大型后端项目里，已知请求地址但不知道入口代码的位置，是一个很常见的问题。

例如：

- 你从浏览器、Postman 或日志里复制了一条请求地址，想快速定位控制器
- 你只知道某个接口路径，但不知道它属于哪个模块、哪个类
- 项目是多模块结构，且同时混用了 Java、Kotlin、Spring 或 JAX-RS

QuickRestfulToolkit 就是为这种场景准备的。

## 安装方式

### 从本地构建包安装

1. 先构建插件包：

```bash
./gradlew buildPlugin
```

Windows：

```bat
gradlew.bat buildPlugin
```

2. 构建完成后，插件包会生成在：

```text
build/distributions/QuickRestfulToolkit-<version>.zip
```

3. 在 IntelliJ IDEA 中安装：

   - 打开 `Settings` / `Preferences`
   - 进入 `Plugins`
   - 点击右上角齿轮图标
   - 选择 `Install Plugin from Disk...`
   - 选择上面生成的 `.zip` 文件
   - 重启 IDE

### 注意事项

- 安装时请选择 `build/distributions` 下的 `.zip` 文件，不是内部的 `.jar`
- 当前插件面向 IntelliJ IDEA `2024.3+`
- 如果“从磁盘安装”没有反应，先确认 IDEA 版本和安装文件路径是否正确

## 使用说明

### 打开搜索弹窗

插件动作名称：

- `Go to REST Service`

默认快捷键：

- `Ctrl + Alt + /`

你也可以通过 `Find Action` 搜索这个动作。

### 支持的输入方式

下面这些内容都可以直接输入：

```text
/api/users/42
api/users/42
https://example.com/api/users/42?active=true
localhost:8080/api/users/42
```

### 输入规范化规则

插件会在匹配前自动处理输入内容：

- 去掉协议、域名和端口
- 去掉查询参数和 hash 片段
- 将反斜杠转换为正斜杠
- 合并重复斜杠
- 自动补齐开头的 `/`

同时也支持常见 RESTful 路径变量：

```text
/users/{id}
/orders/{orderId:\d+}
```

### 典型使用流程

1. 触发 `Go to REST Service`
2. 粘贴或输入 URL / 路径
3. 按需选择 HTTP Method 过滤
4. 按需勾选 `当前模块`
5. 选择结果后跳转到对应控制器方法

## 兼容性

| 项目 | 要求 |
| ---- | ---- |
| IDE | IntelliJ IDEA 2024.3+ |
| Platform Build | `243+` |
| 构建 JDK | JDK 21 |
| Kotlin 插件 | 兼容 K2 模式 |
| 依赖插件 | Java、Kotlin、YAML、Properties |

## 开发说明

### 在沙箱 IDE 中运行插件

```bash
./gradlew runIde
```

Windows：

```bat
gradlew.bat runIde
```

### 构建可安装插件包

```bash
./gradlew buildPlugin
```

### 运行测试

```bash
./gradlew test
```

### 校验插件兼容性与元数据

```bash
./gradlew verifyPlugin
```

## 项目结构

插件源码全部使用 Kotlin 编写。受 `build.gradle.kts` 中 `sourceSets` 配置的影响，生产代码目录位于 `src/main/java/`（尽管文件是 `.kt`）。

```text
src/main/java/com/shenszq/restful/
├── annotations/                 Spring / JAX-RS 注解定义
├── common/
│   ├── resolver/                接口解析器（Spring / JAX-RS）
│   ├── spring/                  Spring 请求映射辅助类
│   ├── jaxrs/                   JAX-RS 注解辅助类
│   ├── PsiAnnotationHelper.kt   注解属性解析
│   ├── ServiceHelper.kt         汇总各解析器结果并去重
│   ├── ToolkitIcons.kt          HTTP Method 图标
│   └── UrlPatternUtils.kt       URL 归一化与匹配逻辑
├── method/                      HTTP Method 与请求路径模型
└── navigation/action/           动作、弹窗模型、贡献器与结果项

src/main/resources/META-INF/
└── plugin.xml                   IntelliJ 插件描述文件
```

## 关键文件

- `build.gradle.kts`：插件构建、IDE 基线版本、兼容性配置
- `src/main/resources/META-INF/plugin.xml`：插件 ID、依赖、动作和兼容性声明
- `src/main/java/com/shenszq/restful/navigation/action/GotoRequestMappingAction.kt`：入口动作
- `src/main/java/com/shenszq/restful/common/UrlPatternUtils.kt`：URL 归一化与匹配规则
- `src/main/java/com/shenszq/restful/common/resolver/SpringResolver.kt`：Spring 接口扫描实现
- `src/main/java/com/shenszq/restful/common/resolver/JaxrsResolver.kt`：JAX-RS 接口扫描实现

## 常见问题

### 从磁盘安装没有反应

优先检查这些问题：

- 是否选择了 `build/distributions` 下的 zip 包
- IDEA 版本是否为 `2024.3+`
- 安装后是否重启了 IDE

### 本地构建失败

请确认：

- `JAVA_HOME` 指向 JDK 21
- Gradle 可以访问 Maven Central 和 JetBrains 仓库
- 你是在项目根目录执行构建命令

## 致谢

本项目复刻自 GitHub 开源项目 [RestfulToolkitPlus](https://github.com/shenszq/RestfulToolkitPlus)（Java 实现），感谢其提供的设计思路与功能基线。QuickRestfulToolkit 在此基础上完成了 Kotlin 化的整体重写。

## 贡献

欢迎提交 Issue 和 Pull Request。

如果你准备继续扩展这个插件，建议从下面几个入口开始阅读：

1. `GotoRequestMappingAction`
2. `GotoRequestMappingModel`
3. `SpringResolver` / `JaxrsResolver`
4. `UrlPatternUtils`
