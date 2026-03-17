# Java Coding Standard

This document defines coding standards for the Vuln-Audit-Lifecycle-Management backend project.

## 1. Project Structure

Base package: com.vulnaudit

- config: Configuration classes
- controller: REST controllers
- service: Business logic layer
- repository: Data access layer
- entity: Domain entities
- dto: Data transfer objects (Request/Response)
- vo: View objects
- enums: Enumerations
- exception: Custom exceptions
- util: Utility classes
- security: Security related classes
- mapper: DTO/VO to Entity mappers

## 2. Naming Conventions

### Class Naming

| Type | Convention | Example |
|------|------------|---------|
| Entity | EntityName + Entity | AssetEntity |
| Repository | EntityName + Repository | AssetRepository |
| Service | EntityName + Service | AssetService |
| Service Impl | EntityName + ServiceImpl | AssetServiceImpl |
| Controller | EntityName + Controller | AssetController |
| DTO | EntityName + DTO | AssetDTO, CreateAssetRequestDTO |
| VO | EntityName + VO | AssetVO, AssetDetailVO |
| Enum | Name + Enum | AssetStatusEnum |

### Variable Naming

- Variables and methods: camelCase
- Constants: UPPER_SNAKE_CASE
- Boolean variables: prefix with is, has, can

## 3. REST Controller

- Use @RestController and @RequestMapping
- Use @RequiredArgsConstructor for dependency injection
- Use DTO for request and response, separate from Entity
- Validate all user input using @Valid annotation
- Use validation constraints in DTO: @NotNull, @NotBlank, @Size, @Pattern, etc.
- Follow standard HTTP methods: GET for query, POST for create, PUT for update, DELETE for delete
- Return appropriate HTTP status codes
- Use pagination for list endpoints

## 4. Service Layer

- Use @Service annotation
- Use @Transactional for business operations
- Use @Transactional(readOnly = true) for read-only methods
- Include logging for important operations
- Validate input parameters

## 5. Repository Layer

- Extend JpaRepository
- Use Spring Data JPA query method naming conventions
- Use @Query for complex queries
- Use @Param for query parameters

## 6. DTO Design

- Use separate DTOs for request and response
- Add validation constraints: @NotNull, @NotBlank, @Size, @Min, @Max, @Pattern, @Email, etc.
- Use @Valid on nested objects
- Group validation constraints by scenario: create, update

## 7. Entity Design

- Use JPA annotations for mapping
- Use Lombok annotations: @Getter, @Setter, @Builder, @NoArgsConstructor, @AllArgsConstructor
- Use @EntityListeners(AuditingEntityListener.class) for automatic timestamp
- Define proper relationships between entities
- Use appropriate fetch types

## 8. Exception Handling

- Create custom exception classes extending RuntimeException
- Use @RestControllerAdvice for global exception handling
- Return appropriate HTTP status codes
- Log errors appropriately

## 9. Security

- Use Spring Security for authentication and authorization
- Use JWT for stateless authentication
- Configure appropriate security filters
- Validate permissions for each endpoint

## 10. API Response Format

- Standard success response with success flag and data
- Standard error response with error code and message
- Paginated response with items, total, page, pageSize

## 11. Testing

- Write unit tests for service layer
- Use Mockito for mocking dependencies
- Follow test naming conventions
- Test both success and failure scenarios

---

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2024-01-01 | Initial version |
