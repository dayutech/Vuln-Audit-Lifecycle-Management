---
alwaysApply: false
globs: backend/*
---
# Java Coding Standard

## 1. Project Structure
com.vulnaudit包下：config(配置) | controller(控制器) | service(业务) | repository(持久化) | entity(实体) | dto(传输对象) | vo(视图对象) | enums(枚举) | exception(异常) | util(工具)

## 2. Naming
- Entity: AssetEntity
- Repository: AssetRepository
- Service: AssetService / AssetServiceImpl
- Controller: AssetController
- DTO: CreateAssetRequestDTO / AssetResponseDTO
- VO: AssetDetailVO
- 方法/变量: camelCase
- 常量: UPPER_SNAKE_CASE

## 3. Controller
- @RestController + @RequestMapping
- @RequiredArgsConstructor依赖注入
- 请求/响应使用DTO，与Entity分离
- @Valid + 注解验证 (@NotNull, @NotBlank, @Size等)
- GET查询 / POST创建 / PUT更新 / DELETE删除
- 列表使用分页

## 4. Service
- @Service注解
- @Transactional事务控制
- @Transactional(readOnly = true)只读方法
- 重要操作需日志记录

## 5. Repository
- 继承JpaRepository
- 复杂查询用@Query + @Param

## 6. DTO/VO
- 请求/响应分别定义
- 添加验证注解
- @Valid级联验证

## 7. Entity
- JPA注解映射
- Lombok: @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
- @EntityListeners(AuditingEntityListener.class)自动时间戳

## 8. Exception
- 自定义异常继承RuntimeException
- @RestControllerAdvice全局处理
- 统一返回HTTP状态码

## 9. Security
- Spring Security + JWT
- 接口权限校验

## 10. API Response
- 成功: {success: true, data: ...}
- 错误: {success: false, code, message}
- 分页: {items: [], total, page, pageSize}
