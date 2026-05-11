# LiveCamera-LBS

LiveCamera-LBS 是一个 Android 实景巡礼匹配助手。用户可以拍摄或上传现实场景图片，让 AI 判断它更适合动漫圣地巡礼还是国内旅行识别，并结合地点搜索、Anitabi/Bangumi 数据、地图导航和本地巡礼日记完成一次完整的打卡流程。

当前版本：**v1.5.0**

[下载最新版 APK](https://github.com/ayumu317/livecamera/releases/latest)

## 核心功能

- **智能识别默认首页**：默认不强迫用户选择模式，由系统根据图片判断识别路线。
- **动漫圣地巡礼**：适合街景、车站、学校、桥、展馆、海边、神社、商业街等巡礼图。
- **国内旅行识别**：识别中国境内景点、城市地标、建筑和自然风光，并支持地图导航。
- **作品名二次匹配**：用户选择或输入作品名后，系统会把“作品名 + 当前图片”再次交给 AI 匹配巡礼地点。
- **候选巡礼地点**：Anitabi 返回多个点位时，先展示主结果，用户可点击“换一个结果”查看其他候选。
- **作品介绍补全**：搜索到对应作品后，会补充 Bangumi 作品信息，例如中文名、原名、放送日期、集数、简介等。
- **巡礼日记**：保存打卡记录，支持查看详情、删除记录、导出 JSON 或 TXT。
- **独立设置页**：支持默认识别模式、图片预览方式、保存后动作、颜色主题和功能介绍。

## 使用流程

1. 打开 App。
2. 点击相册或拍照按钮，选择一张现实场景图片。
3. 点击“开始识别”。
4. 如果系统给出动漫作品候选，选择正确作品。
5. 系统会结合“作品名 + 当前图片”再次匹配巡礼地点。
6. 查看主结果、AI 巡礼解说和作品介绍。
7. 如果结果不准确，点击“换一个结果”查看候选地点，或手动输入正确作品名重新匹配。
8. 点击“记录打卡”保存到巡礼日记。
9. 在巡礼日记中查看详情、删除记录，或导出 JSON/TXT。

## 识别模式

### 智能识别

默认入口。系统根据图片内容判断更适合动漫巡礼还是国内旅行。

### 动漫巡礼

面向动漫圣地巡礼场景。该模式会优先保留动漫巡礼链路，不会因为模型偶尔返回国内景点就直接切走。适合上传海外街景、车站、学校、桥梁、展馆、海边、神社、商业街等素材。

### 国内旅行

面向中国境内景点、城市地标、建筑和自然风光。该模式会强制走国内旅行展示链路，不调用动漫作品和巡礼点 API。

## v1.5.0 更新重点

- 新增独立设置页。
- 新增功能介绍页面，说明上传识别流程、模式区别、日记导出和使用技巧。
- 新增巡礼日记详情弹窗。
- 新增日记删除功能。
- 新增日记 JSON/TXT 导出。
- 新增侧边栏入口。
- 默认首页调整为智能识别。
- 优化图片预览方式设置。
- 新增颜色主题设置。
- 动漫巡礼结果补充 Bangumi 作品介绍。
- 强化保存快照，减少展示结果与保存记录不一致的问题。

## 技术栈

- Android Java
- Material Design Components
- DrawerLayout
- Room
- Glide
- Gson
- OkHttp
- Doubao Responses API
- Tencent Location SDK
- Anitabi API
- Bangumi API
- SerpApi fallback

## 本地构建

```bash
./gradlew.bat :app:assembleDebug --console=plain
```

构建完成后，APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 配置

敏感配置放在 `local.properties`，不要提交到仓库。

```properties
ARK_API_KEY=你的火山方舟 API Key
DOUBAO_MODEL_ID=doubao-seed-2-0-lite-260428
DOUBAO_RESPONSES_URL=https://ark.cn-beijing.volces.com/api/v3/responses
TENCENT_MAP_SDK_KEY=你的腾讯地图 SDK Key
SERPAPI_KEY=你的 SerpApi Key
```

项目仍保留旧字段兼容：

```properties
DOUBAO_BASE_URL=
DOUBAO_API_KEY=
DOUBAO_MODEL=
```

## 数据安全

- API Key 不写入源码。
- `local.properties` 不应提交。
- 巡礼日记目前保存在本地 Room 数据库。
- 日记导出文件通过系统分享面板导出，支持 JSON 和 TXT。

## 适合的测试图片

- 动漫巡礼：车站、学校、桥、海边、展馆、神社、商业街、街道路口。
- 国内旅行：东方明珠、故宫、西湖、城市地标、自然风景。

如果知道作品名，建议在动漫巡礼模式下输入正确作品名重新匹配，系统会结合当前图片再次判断地点线索。
