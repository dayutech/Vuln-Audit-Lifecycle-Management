package com.vulnaudit.service.env;

import com.vulnaudit.entity.Environment;

/**
 * 环境部署器（可插拔）
 *
 * <p>实现约定：
 * <ul>
 *     <li>{@link #isAvailable()} 必须无副作用、可被频繁调用（实现应自行缓存探测结果）；</li>
 *     <li>不可用时 {@link #provision}/{@link #start}/{@link #stop} 必须返回失败且
 *         消息以 {@link #UNAVAILABLE_MESSAGE} 开头，禁止伪装成功；</li>
 *     <li>实现内部吞掉外部命令异常并转换为 {@link ProvisionResult}。</li>
 * </ul>
 */
public interface EnvironmentProvisioner {

    /**
     * 未启用容器编排时的统一提示语（前端与测试依赖该文案）
     */
    String UNAVAILABLE_MESSAGE = "当前部署未启用容器编排";

    /**
     * 部署器类型：docker / noop
     */
    String type();

    /**
     * 是否可用
     */
    boolean isAvailable();

    /**
     * 不可用原因（可用时返回就绪说明）
     */
    String availabilityMessage();

    /**
     * 创建并启动容器
     */
    ProvisionResult provision(Environment environment);

    /**
     * 启动已存在的容器
     */
    ProvisionResult start(Environment environment);

    /**
     * 停止容器
     */
    ProvisionResult stop(Environment environment);

    /**
     * 删除容器（尽力而为，删除环境时调用）
     */
    default ProvisionResult remove(Environment environment) {
        return ProvisionResult.fail("当前部署器不支持删除容器");
    }
}
