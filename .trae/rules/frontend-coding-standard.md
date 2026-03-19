# Frontend Coding Standard

## 1. Project Structure
src目录下：api(接口) | components(组件) | pages(页面) | router(路由) | store(状态) | utils(工具) | types(类型)

## 2. Naming
- 组件/类型: PascalCase (UserCard.tsx, AssetVO.ts)
- 文件/函数: camelCase
- 常量: UPPER_SNAKE_CASE
- 布尔变量: is/has/can/should开头

## 3. Component
- 函数式组件 + Hooks
- Props输入 / Emit事件输出
- 大组件拆分为可复用小组件
- 提取逻辑为自定义Hook

## 4. API
- 集中API服务层，组件内不直接调用
- 使用TypeScript接口定义请求/响应
- 统一错误处理和Loading状态

## 5. TypeScript
- 显式类型定义，禁止any
- 接口定义对象结构
- 启用strict模式

## 6. Security
- 敏感数据不上前端
- 输入验证后发送
- 用户内容需转义

## 7. Performance
- 路由懒加载
- useMemo/useCallback优化重渲染
- 大列表虚拟滚动
