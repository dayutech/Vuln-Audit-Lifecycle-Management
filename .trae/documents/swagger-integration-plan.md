# Swagger API 文档集成计划

## 目标
为漏洞审计生命周期管理系统后端集成 Swagger/OpenAPI 文档，确保每个接口都有详细的请求、响应说明。

## 当前状态分析

### 已有基础
- 项目已使用 `io.swagger.v3.oas.annotations` 注解（OpenAPI 3 风格）
- 控制器已有 `@Tag`、`@Operation`、`@Parameter` 等基础注解
- Spring Boot 3.2.1 版本

### 需要改进的地方
1. 缺少 SpringDoc OpenAPI 依赖
2. 缺少 OpenAPI 全局配置（API 信息、安全认证等）
3. 缺少请求/响应模型的详细文档
4. 缺少认证相关的 Swagger 配置
5. 部分接口缺少详细的参数说明和响应示例

## 实施步骤

### 步骤一：添加 SpringDoc OpenAPI 依赖
在 pom.xml 中添加 springdoc-openapi-starter-webmvc-ui 依赖（适配 Spring Boot 3.x）

### 步骤二：创建 OpenAPI 配置类
创建 `OpenApiConfig.java`，配置：
- API 基本信息（标题、描述、版本、联系方式）
- 安全认证方案（JWT Bearer Token）
- 全局响应描述
- API 分组配置

### 步骤三：增强 DTO 文档注解
为所有 DTO 类添加：
- `@Schema` 注解描述字段含义
- `@Schema` 描述必填项、示例值
- 添加字段验证说明

### 步骤四：增强控制器文档注解
为所有控制器添加：
- `@SecurityRequirement` 注解标记需要认证的接口
- `@ApiResponses` 描述各种响应状态码
- `@RequestBody` 添加详细描述和示例
- 完善参数描述

### 步骤五：配置 Security 放行 Swagger 路径
在 SecurityConfig 中放行以下路径：
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/swagger-ui.html`

### 步骤六：验证和测试
- 启动项目访问 Swagger UI
- 验证所有接口文档完整性
- 测试 JWT 认证功能

## 文件修改清单

| 文件 | 操作 | 说明 |
|------|------|------|
| pom.xml | 修改 | 添加 SpringDoc 依赖 |
| OpenApiConfig.java | 新建 | OpenAPI 全局配置 |
| SecurityConfig.java | 修改 | 放行 Swagger 路径 |
| ApiResponse.java | 修改 | 添加 Schema 注解 |
| PageResponse.java | 修改 | 添加 Schema 注解 |
| LoginRequest.java | 修改 | 添加 Schema 注解 |
| LoginResponse.java | 修改 | 添加 Schema 注解 |
| UserCreateRequest.java | 修改 | 添加 Schema 注解 |
| UserUpdateRequest.java | 修改 | 添加 Schema 注解 |
| ChangePasswordRequest.java | 修改 | 添加 Schema 注解 |
| AuthController.java | 修改 | 增强文档注解 |
| UserController.java | 修改 | 增强文档注解 |
| VulnerabilityController.java | 修改 | 增强文档注解 |

## 预期成果

1. 访问 `http://localhost:8080/swagger-ui.html` 可查看完整 API 文档
2. 支持 JWT Token 认证测试
3. 每个接口都有清晰的请求参数说明和响应示例
4. API 按模块分组展示
