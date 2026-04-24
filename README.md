# pathfinder

A comparative platform for shortest-path algorithms on a road network.

## Stack Overview

- **Backend**: Java Spring Boot
- **Frontend**: React

## Structure

### server

- Spring Boot service that loads a road-network GeoJSON into an in-memory graph (adjacency list).
- Implements Dijkstra and A* on top of a pluggable edge-cost model with three objectives: distance, time, and balanced.
- Exposes REST endpoints for metadata, POI search, nearest-node snapping, and routing.

### web

- React application that visualizes the pathfinding process and results.
- Uses Vite for fast development and build.
- Renders the exploration process and final path with different colors based on the data received from the backend.

## Getting Started

### server

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080`。

### web

```powershell
cd web
npm install
npm run dev
```

## 构建与打包交付

目标：把 React 前端和 Spring Boot 后端合并成一个 Windows 可执行文件夹，压缩成 zip 发给用户。**用户机器不需要装 Java 或 Node.js**。

### 构建机器前置条件

- **JDK 17+**（推荐 [Eclipse Temurin 17](https://adoptium.net/)），`bin/` 加入 `PATH`。JDK 自带 `java`、`javac`、`jpackage`。
- **Node.js 18+**（带 `npm`）。
- **PowerShell**（Windows 自带）。

### 核心思路

React 前端打包后是一堆纯静态文件（HTML / JS / CSS），Spring Boot 天然能作为静态文件服务器。所以整个交付流程就是两件事：

1. **合并**：让 Spring Boot 同时托管前端静态资源和后端 API 接口。→ 产出一个可执行 jar。
2. **封装**：把这个 jar 和一份精简 JRE 捆成原生可执行程序，让没装 Java 的用户也能跑。→ 产出一个 zip。

### 五个步骤

#### 步骤 1：构建前端

```powershell
cd web
npm install
npm run build
```

**产物**：[web/dist/](web/dist/)（`index.html` + 一堆 assets）。

**原理**：Vite 做三件事 —— 把 TypeScript 编译成 JS、把模块依赖打成少量 chunk、对 JS/CSS 做压缩和指纹命名。产物是纯静态文件，没有任何 Node.js 运行时依赖。

#### 步骤 2：把前端和数据塞进后端的 classpath

```powershell
# 把前端产物放进 Spring Boot 的静态资源目录
Copy-Item web\dist\* server\src\main\resources\static\ -Recurse

# 把地图数据也放进去
Copy-Item data\full.geojson server\src\main\resources\data\
```

**原理**：Spring Boot 启动时会自动把 `classpath:/static/` 暴露为 web 根路径。放在那里的 `index.html` 自动成为 welcome page，`/assets/index-xxx.js` 自动映射成 `http://localhost:8080/assets/index-xxx.js`。这一切都是 Spring Boot 的默认配置，不用写一行代码。

[GeoJsonLoader](server/src/main/java/edu/northeastern/pathfinder/graph/GeoJsonLoader.java) 在开发态读 `../data/full.geojson`，打包后读不到就回退到 `classpath:/data/full.geojson`。所以数据文件也需要一并进 resources。

**为什么前端要用相对路径调 API**：[web/src/api/pathfinderApi.ts](web/src/api/pathfinderApi.ts) 中所有请求都写成 `/api/...` 而不是 `http://localhost:8080/api/...`。这样前端被 Spring Boot 托管时，请求自然打到同源同进程，不走跨域。开发态则靠 [web/vite.config.ts](web/vite.config.ts) 的 proxy 把 `/api/*` 转发到后端。

**为什么需要 SPA fallback**：React Router 用 `/routes/foo` 这种客户端路由。用户在这种地址刷新时，浏览器会向 Spring Boot 发真实 HTTP 请求 `GET /routes/foo`，但磁盘上没有这个文件，Spring Boot 会 404。解决办法是 [WebConfig.java](server/src/main/java/edu/northeastern/pathfinder/config/WebConfig.java) 中声明：任何不是静态资源、不是 `/api/*` 的路径都 forward 到 `index.html`，让前端 JS 接管路由。

#### 步骤 3：构建 Spring Boot fat jar

```powershell
cd server
.\mvnw.cmd clean package -DskipTests
```

**产物**：`server/target/pathfinder-0.0.1-SNAPSHOT.jar`（约 57 MB，含前端 + 数据 + 所有依赖）。

**原理**：[pom.xml](server/pom.xml) 里声明的 `spring-boot-maven-plugin` 会把编译后的 `.class`、`src/main/resources/` 下的一切（包括上一步复制进来的前端和数据）、以及所有 Maven 依赖 jar，打成一个"可执行 jar"。它的结构大致是：

```
pathfinder-0.0.1-SNAPSHOT.jar
├── META-INF/MANIFEST.MF         ← 指向 Spring Boot 的启动类
├── BOOT-INF/
│   ├── classes/                 ← 我们的代码 + resources
│   │   ├── edu/northeastern/... ← 编译后的 Java 类
│   │   ├── static/index.html    ← 前端产物
│   │   └── data/full.geojson    ← 地图数据
│   └── lib/                     ← 所有依赖 jar (Tomcat, Jackson, H2, ...)
└── org/springframework/boot/loader/...  ← 启动 loader
```

这就是为什么 `java -jar pathfinder-xxx.jar` 能直接运行 —— MANIFEST 指向的不是我们的 main，而是 Spring Boot 的 `JarLauncher`，它在运行时把 `BOOT-INF/lib/*.jar` 动态挂到 classpath 上，再调用我们的 `PathfinderApplication.main()`。

#### 步骤 4：用 jpackage 生成内嵌 JRE 的原生程序

```powershell
jpackage `
    --type app-image `
    --name Pathfinder `
    --app-version 0.0.1 `
    --input server\target `
    --main-jar pathfinder-0.0.1-SNAPSHOT.jar `
    --dest release `
    --java-options "-Dspring.profiles.active=prod"
```

**产物**：`release/Pathfinder/`（约 186 MB）

```
release/Pathfinder/
├── Pathfinder.exe       ← 原生启动器
├── app/                 ← 放我们的 fat jar
└── runtime/             ← 内嵌的精简 JRE (几十 MB)
```

**原理**：

- `jpackage` 内部先调 `jlink`，扫描 fat jar 依赖了哪些 JDK 模块（`java.base`、`java.desktop`、`java.sql`、`java.naming` 等），生成一份**只含所需模块的精简 JRE**（完整 JRE 有 200+ MB，裁剪后只剩几十 MB）。
- 然后生成一个原生 C 启动器 `Pathfinder.exe`，其中硬编码了"去同目录 `runtime/` 找 JRE，用它启动 `app/pathfinder.jar`，并传 `-Dspring.profiles.active=prod` 这类 JVM 参数"。
- `-Dspring.profiles.active=prod` 触发加载 [application-prod.properties](server/src/main/resources/application-prod.properties)：把 H2 数据库路径改到 `%USERPROFILE%\.pathfinder\`（因为安装目录可能只读）、关闭 H2 web 控制台、启用浏览器自动打开。

这样最终产物就是自包含的 —— 用户机器不需要装 Java，因为 `runtime/` 就是一份便携 JRE。

**浏览器自动打开**是通过 [PathfinderApplication](server/src/main/java/edu/northeastern/pathfinder/PathfinderApplication.java) 里监听 `ApplicationReadyEvent` 实现的：Tomcat 绑定好端口之后，调 `java.awt.Desktop.browse()` 用系统默认浏览器打开 `http://localhost:8080`。

#### 步骤 5：压缩成 zip 交付

```powershell
Compress-Archive -Path release\Pathfinder -DestinationPath release\Pathfinder-0.0.1.zip
```

**产物**：`release/Pathfinder-0.0.1.zip`（约 97 MB）。

**原理**：只是个普通 zip，Windows 自带右键"解压"。之所以能直接 zip（不用安装包），是因为 app-image 里所有文件都用相对路径互相引用 —— 用户解压到哪里都能跑。

### 一键执行所有步骤

上面五步写在了 [package.ps1](package.ps1) 里，串行执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\package.ps1
```

### 交付

把 `release/Pathfinder-0.0.1.zip` 发给用户。用户操作：

1. 右键解压到任意目录。
2. 双击 `Pathfinder.exe`。
3. 等 5–10 秒，浏览器自动弹出应用首页。

### 流程全貌一张图

```
 web/                 data/                  server/src/main/resources/
 ├── src/             └── full.geojson ─┐    ├── static/    ← 步骤 2 复制进来
 └── dist/ ─────────────────────────────┤    └── data/      ← 步骤 2 复制进来
     ↑                                  │            │
     步骤 1: npm run build              │            ↓ 步骤 3: mvn package
                                        │    server/target/pathfinder-0.0.1-SNAPSHOT.jar
                                        │            │
                                        │            ↓ 步骤 4: jpackage
                                        │    release/Pathfinder/  (exe + JRE + jar)
                                        │            │
                                        │            ↓ 步骤 5: Compress-Archive
                                        │    release/Pathfinder-0.0.1.zip
                                        │            │
                                        │            ↓ 交付
                                        └────→  用户双击 Pathfinder.exe
                                                  内嵌 JRE 启动 jar
                                                  Spring Boot 托管前端 + API
                                                  浏览器自动打开 localhost:8080
```
