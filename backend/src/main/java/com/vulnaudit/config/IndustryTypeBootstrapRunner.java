package com.vulnaudit.config;

import com.vulnaudit.service.IndustryTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 行业类型启动初始化（t129）
 *
 * <p>表为空时插入 GB/T 4754 的内置门类/大类（builtin=true）；已有数据则跳过（幂等）。
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class IndustryTypeBootstrapRunner implements ApplicationRunner {

    private final IndustryTypeService industryTypeService;

    @Override
    public void run(ApplicationArguments args) {
        boolean inserted = industryTypeService.ensureBuiltin();
        log.info("行业类型初始化完成: inserted={}", inserted);
    }
}
