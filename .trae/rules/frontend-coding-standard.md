# Frontend Coding Standard

This document defines coding standards for the Vuln-Audit-Lifecycle-Management frontend project.

## 1. Project Structure

Base directory: src

- api: API service functions
- components: Reusable UI components
- pages: Page components
- views: Route views
- router: Route configuration
- store: State management
- utils: Utility functions
- types: TypeScript type definitions
- assets: Static assets
- styles: Global styles

## 2. Naming Conventions

### File Naming

| Type | Convention | Example |
|------|------------|---------|
| Component | PascalCase | AssetList.vue, UserCard.tsx |
| API Service | kebab-case | asset-api.ts, user-service.ts |
| Utility | kebab-case | date-util.ts, format-util.ts |
| Type | PascalCase | AssetType.ts, UserVO.ts |
| Style | kebab-case | asset-styles.scss |

### Variable Naming

- Variables and functions: camelCase
- Components: PascalCase
- Constants: UPPER_SNAKE_CASE
- Boolean variables: prefix with is, has, can, should

## 3. Component Design

- Use functional components with hooks
- Split large components into smaller reusable parts
- Use props for component input
- Emit events for component output
- Keep components focused and single-purpose
- Extract reusable logic into custom hooks

## 4. State Management

- Use global store for shared state
- Use local state for component-specific data
- Organize store by feature modules
- Use immutable state updates
- Separate synchronous and asynchronous actions

## 5. API Communication

- Use centralized API service layer
- Separate API calls from components
- Use TypeScript interfaces for request/response
- Handle errors consistently
- Use loading states for async operations

## 6. TypeScript Usage

- Define explicit types for all variables
- Use interfaces for object shapes
- Avoid using any type
- Use generics for reusable components
- Enable strict mode in tsconfig

## 7. Error Handling

- Handle API errors with user-friendly messages
- Use try-catch for async operations
- Display validation errors to users
- Log errors for debugging
- Show appropriate loading states

## 8. Security

- Never expose sensitive data in client-side code
- Validate all user input before sending to server
- Use HTTPS for all API calls
- Implement proper authentication flow
- Sanitize user-generated content

## 9. Code Organization

- Group related files by feature
- Use barrel files (index.ts) for clean imports
- Keep files focused and small
- Extract constants to dedicated files
- Separate business logic from UI code

## 10. Performance

- Use lazy loading for routes
- Memoize expensive computations
- Optimize re-renders with useMemo/useCallback
- Use virtual scrolling for large lists
- Optimize bundle size with code splitting

---

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2024-01-01 | Initial version |
