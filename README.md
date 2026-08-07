# AI 代码母亲（Zou AI Code Mother）

基于 Spring Boot + LangChain4j 的 AI 代码生成平台，支持通过自然语言对话生成 HTML、多文件及 Vue 项目代码，并提供流式输出、智能路由、应用部署等能力。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| **核心框架** | Spring Boot | 3.5.9 |
| **开发语言** | Java | 21 |
| **AI 框架** | LangChain4j | 1.1.0 |
| **AI 模型** | DeepSeek（deepseek-chat） | - |
| **ORM 框架** | MyBatis-Flex | 1.11.0 |
| **数据库** | MySQL | - |
| **缓存/会话** | Redis + Spring Session | - |
| **分布式锁/限流** | Redisson | 3.50.0 |
| **本地缓存** | Caffeine | - |
| **对象存储** | 腾讯云 COS | - |
| **网页截图** | Selenium | 4.33.0 |
| **接口文档** | Knife4j (OpenAPI 3) | 4.4.0 |
| **工具库** | Hutool | 5.8.38 |
| **数据库连接池** | HikariCP | - |

## 核心功能

- **AI 代码生成**：支持三种生成模式
  - `HTML` — 原生 HTML 单文件模式
  - `MULTI_FILE` — 原生多文件模式
  - `VUE_PROJECT` — Vue 项目模式
- **智能路由**：根据用户输入自动选择最合适的代码生成模式
- **AI 应用命名**：自动为生成的应用生成名称
- **流式输出**：基于 Reactor 的 SSE 流式响应，实时展示生成过程
- **AI 工具调用**：内置文件读写、修改、删除等工具，支持 AI 自主调用
- **对话历史**：保存用户与 AI 的完整对话记录
- **用户系统**：注册、登录、权限管理（基于 Spring Session + Redis）
- **应用部署**：生成的代码可部署到服务器
- **网页截图**：基于 Selenium 的网页截图服务
- **项目下载**：支持下载生成的完整项目代码
- **限流防护**：基于 Redisson 的接口限流

## 项目结构

```
src/main/java/com/zou/zouaicodemother/
├── ai/                          # AI 相关核心模块
│   ├── guardrail/               #   输入/输出安全防护
│   ├── model/                   #   AI 响应消息模型
│   ├── tools/                   #   AI 可调用的工具集
│   ├── AiCodeGeneratorService   #   代码生成服务
│   ├── AiCodeGenTypeRoutingService  # 智能路由服务
│   └── AiAppNameGenerationService  # 应用命名服务
├── core/                        # 核心处理流程
│   ├── builder/                 #   项目构建器（Vue）
│   ├── handler/                 #   流式消息处理器
│   ├── parser/                  #   代码解析器
│   ├── saver/                   #   代码文件保存器
│   └── AiCodeGeneratorFacade    #   代码生成门面（编排入口）
├── config/                      # 配置类
├── controller/                  # 控制器层
├── service/                     # 业务服务层
├── mapper/                      # 数据访问层
├── model/                       # 数据模型（DTO/VO/Entity/Enum）
├── aop/                         # 切面（权限拦截）
├── ratelimiter/                 # 限流模块
├── manager/                     # 管理器（COS 等）
├── exception/                   # 异常处理
└── utils/                       # 工具类
```

## 快速开始

### 环境要求

- JDK 21+
- MySQL 8.0+
- Redis 7.0+
- Maven 3.8+

### 配置说明

项目通过 Spring Profile 管理多环境配置：

| 配置文件 | 说明 |
|----------|------|
| `application.yml` | 主配置（公共配置、默认激活 `local`） |
| `application-local.yml` | 本地开发环境配置 |
| `application-prod.yml` | 生产环境配置 |

**切换环境**：修改 `application.yml` 中的 `spring.profiles.active`，或通过启动参数指定：

```bash
java -jar zou-ai-code-mother.jar --spring.profiles.active=prod
```

### 需配置的外部服务

1. **MySQL**：创建数据库 `yu_ai_code_mother`
2. **Redis**：用于 Session 存储与缓存
3. **DeepSeek API Key**：在配置文件中设置 `langchain4j.open-ai.chat-model.api-key`
4. **腾讯云 COS**：配置 `cos.client` 相关参数（secretId / secretKey / bucket 等）

### 构建与运行

```bash
# 编译打包
mvn clean package -DskipTests

# 本地运行
java -jar target/zou-ai-code-mother-0.0.1-SNAPSHOT.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

启动后访问接口文档：http://localhost:8123/api/doc.html

## 接口文档

项目集成了 Knife4j 接口文档，启动后可通过以下地址访问：

- **本地**：http://localhost:8123/api/doc.html
- **生产**：需配置 Knife4j 账号密码（见 `application-prod.yml`）

主要接口模块：

- `AppController` — 应用管理（生成、部署、下载）
- `UserController` — 用户管理（注册、登录）
- `ChatHistoryController` — 对话历史
- `HealthController` — 健康检查

## 部署

生产环境部署地址配置在 `application-prod.yml`：

```yaml
code:
  deploy-host: http://8.148.75.84/dist
```
