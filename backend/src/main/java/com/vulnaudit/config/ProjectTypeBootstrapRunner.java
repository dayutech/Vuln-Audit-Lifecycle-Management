package com.vulnaudit.config;

import com.vulnaudit.service.ProjectTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 项目类型启动初始化（t130）
 *
 * <p>表为空时插入内置 5 项（builtin=true）；已有数据则跳过（幂等）。
 * 与 {@link IndustryTypeBootstrapRunner}（@Order(30)）同一模式，排在行业之后。
 */
@Slf4j
@Component
@Order(31)
@RequiredArgsConstructor
public class ProjectTypeBootstrapRunner implements ApplicationRunner {

    private final ProjectTypeService projectTypeService;

    @Override
    public void run(ApplicationArguments args) {
        boolean inserted = projectTypeService.ensureBuiltin();
        log.info("项目类型初始化完成: inserted={}", inserted);
    }
}
