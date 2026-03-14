# pathfinder

INFO6205 Program Structure & Algorithms 项目作业，最终交付一个可打包直接运行的完整 Java 项目。

## 技术栈

- 前端: TypeScript + React + Vite + HTML5 Canvas + axios
- 后端: Java + Spring Boot

## 需求

### 前端

- 初始化时获取前端尺寸映射的地图信息，并全屏绘制打点模拟地图（不引入真实地图sdk）；
- 手动录入地点信息（比如某个商场银行等），执行空间索引查询；
- 点击地图展示坐标点及其地点信息（比如某个商场银行等）；
- 选好起点终点后点击开始寻路，发送请求并接收数据，同时渲染三种寻路动画；

### 后端

- 配置开发环境，加载 GeoJSON 数据，构建图数据结构（邻接表）；
- 初始化时转化 geojson 数据为算法可用数据，并获取前端尺寸映射的地图信息，存在内存或持久化到本地。
- 实现对应的三种寻路算法，供接口调用；
- 实现对应的接口；

## 开发流程

### 前端

1. 配置开发环境，跨域代理后端接口；
2. 实现 `CoordinateTransformer` (JavaScript): 写一个工具类，把经纬度转换成 Canvas 的 XY 坐标。
3. 实现“逐点绘制”动画函数: 不要用 `setTimeout`，用一个 `frame` 计数器，每帧从 `visited` 数组里取出 N 个点画出来，这样动画会非常丝滑。
4. 要在同一个界面同时跑三种算法且不卡顿，最佳实践是使用 HTML5 Canvas 分层处理。
   1. Layer 0 (底图层): 离屏渲染（Offscreen Canvas）。将 GeoJSON 的所有道路一次性画好成灰色背景，之后不再重绘。

   2. Layer 1 (动画层): 专门渲染 `visitedOrder`（搜索过程）。在一个 `requestAnimationFrame` 循环里用不同的颜色（Dijkstra: 蓝色, A\*: 绿色, Bi-BFS: 红色）和 2px 的坐标偏移（防止重合）。

   3. Layer 2 (交互层): 渲染用户选中的起点、终点和最终的最短路径。

5. UI 交互与 API 对接：
   - 初始化时调用 `/api/map-info` 获取地图边界，完成坐标系映射并绘制底图。
   - 地图点击依次调用 `/api/node-info` 吸附最近节点，确认起终点。
   - POI 输入框调用 `/api/poi-search` 模糊匹配并展示候选列表，选中后自动设为起终点。
   - 起终点确认后调用 `/api/path-finding/compare`，拿到结果同步触发三路动画并更新对比数据面板（耗时、距离、探索节点数）。

### 后端

1. 配置开发环境，配置跨域，定义数据模型，引入必要的库。
2. 数据扁平化： GeoJSON 是嵌套格式，不适合高频寻路，写一个 `StartupRunner`，在 Spring Boot 启动时解析 `full.geojson`。
   - Nodes Map: `Map<String, Node>` (Key 是经纬度拼接的字符串或 Hash，Value 是坐标)。
   - Adjacency List (邻接表): `Map<String, List<Edge>>`。
   - 同步计算并缓存地图边界（south/north/west/east），供 `/api/map-info` 直接返回。
3. R-Tree 空间索引： 引入 `lucenespatial` 或简单的网格索引，用于快速实现用户点击地图坐标 -> 找到最近的 Node ID；同步加载 `pois.csv` 支持 POI 名称模糊查询。
4. 后端实现"多线程并行计算": 既然要对比，后端可以利用 `CompletableFuture` 同时启动三个算法任务，汇总结果后一次性返回，压榨 CPU 性能。
5. Controller 层： 实现四个接口端点，注入算法服务与空间索引服务，处理坐标参数验证及最近节点解析，统一异常响应格式。

## 接口文档

### 1. `GET /api/map-info`

`mapBounds` 是静态数据，无需每次寻路都返回。前端初始化时调用一次，无请求体

响应：

```jsonc
{
  "mapBounds": {
    "south": 38.8, // 地图南边界纬度

    "north": 38.9, // 地图北边界纬度

    "west": -77.1, // 地图西边界经度

    "east": -77.0, // 地图东边界经度
  },
}
```

### 2. `POST /api/path-finding/compare`

用户选好起终点后调用

返回结构约定：

- `resultsByAlgo` 使用对象按算法名存放结果，避免通过数组索引取值

- `algoOrder` 单独定义前端展示顺序，保证图例/颜色/表格稳定

请求体：

```jsonc
{
  "startLng": -77.05, // 起点经度 (double)

  "startLat": 38.85, // 起点纬度 (double)

  "endLng": -77.03, // 终点经度 (double)

  "endLat": 38.87, // 终点纬度 (double)
}
```

响应：

```jsonc
{
  "summary": {
    "totalComputeMs": 22, // compare 接口整体计算耗时（毫秒）

    "fastestAlgo": "Bi-BFS", // 三种算法里耗时最短的算法
  },

  "algoOrder": ["Dijkstra", "A*", "Bi-BFS"], // 前端展示顺序（颜色、图例、表格列都按这个顺序）

  "resultsByAlgo": {
    "Dijkstra": {
      "path": [
        [-77.05, 38.85],

        [-77.048, 38.852],

        [-77.03, 38.87],
      ],

      "visited": [
        [-77.05, 38.85],

        [-77.0495, 38.8505],

        [-77.049, 38.851],
      ],

      "details": {
        "timeMs": 26, // 当前算法运行耗时（毫秒，long）

        "distance": 1200.5, // 最短路径总距离（米，double）

        "nodesExplored": 210, // 算法探索的节点总数（int）
      },
    },

    "A*": {
      "path": [
        [-77.05, 38.85],

        [-77.048, 38.852],

        [-77.03, 38.87],
      ],

      "visited": [
        [-77.05, 38.85],

        [-77.0495, 38.8505],

        [-77.049, 38.851],
      ],

      "details": {
        "timeMs": 15,

        "distance": 1200.5,

        "nodesExplored": 150,
      },
    },

    "Bi-BFS": {
      "path": [
        [-77.05, 38.85],

        [-77.048, 38.852],

        [-77.03, 38.87],
      ],

      "visited": [
        [-77.05, 38.85],

        [-77.0497, 38.8503],

        [-77.0492, 38.8509],
      ],

      "details": {
        "timeMs": 9,

        "distance": 1201.1,

        "nodesExplored": 120,
      },
    },
  },
}
```

### 3. `GET /api/poi-search?q={name}`

用户在输入框搜索地点名称时调用，返回匹配的 POI 列表

请求参数：

- `q`: 地点名称关键词（模糊匹配）

响应：

```jsonc
{
  "results": [
    {
      "name": "Whole Foods Market", // POI 名称

      "lng": -77.0498, // 经度

      "lat": 38.8501, // 纬度
    },
  ],
}
```

### 4. `GET /api/node-info?lng={lng}&lat={lat}`

用户点击地图某处时调用，后端用空间索引找到最近的 Node 并返回其属性

请求参数：

- `lng`: 用户点击位置的经度

- `lat`: 用户点击位置的纬度

响应：

```jsonc
{
  "nodeId": "abc123", // 节点唯一 ID（与图数据结构中的 key 对应）

  "lng": -77.0498, // 最近节点的实际经度（double）

  "lat": 38.8501, // 最近节点的实际纬度（double）

  "name": "Pennsylvania Ave NW", // 道路/地点名称，可能为 null

  "tags": { "highway": "primary" }, // 来自 OSM 原始标签，如道路等级、限速等
}
```
