package com.vulnaudit.repository;

import com.vulnaudit.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户数据访问层接口
 * 
 * 功能说明：
 * - 提供用户实体的CRUD操作
 * - 提供用户查询相关的自定义方法
 * - 支持用户名和邮箱的唯一性检查
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * 根据用户名查询用户
     * 用于登录验证和用户名唯一性检查
     * 
     * @param username 用户名
     * @return 用户实体（如果存在）
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     * 用于邮箱唯一性检查和通过邮箱找回用户
     * 
     * @param email 邮箱地址
     * @return 用户实体（如果存在）
     */
    Optional<User> findByEmail(String email);

    /**
     * 检查用户名是否已存在
     * 用于用户注册时的用户名唯一性验证
     * 
     * @param username 用户名
     * @return 如果用户名已存在返回true，否则返回false
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否已存在
     * 用于用户注册时的邮箱唯一性验证
     * 
     * @param email 邮箱地址
     * @return 如果邮箱已存在返回true，否则返回false
     */
    boolean existsByEmail(String email);

    /**
     * 根据状态查询用户数量
     * 用于统计不同状态的用户数量
     * 
     * @param status 用户状态
     * @return 符合条件的用户数量
     */
    long countByStatus(String status);

}
