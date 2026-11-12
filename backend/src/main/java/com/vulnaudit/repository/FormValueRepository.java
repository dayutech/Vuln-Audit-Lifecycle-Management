package com.vulnaudit.repository;

import com.vulnaudit.entity.FormValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** 表单字段值仓储（t178） */
@Repository
public interface FormValueRepository extends JpaRepository<FormValue, UUID> {

    /** 字段是否已有提交数据（用于「禁止改类型/删除」判断） */
    long countByFieldId(UUID fieldId);
}
