package com.vulnaudit.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 环境部署（容器编排）配置
 *
 * <p>映射 {@code app.env.*}。默认开启自动探测：宿主机存在 Docker 守护进程（socket /
 * DOCKER_HOST）且 docker CLI 可用时使用 {@code DockerProvisioner}，否则退化为
 * {@code NoopProvisioner}，部署操作会明确失败而不再伪装成功。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.env")
public class EnvironmentProvisionProperties {

    /**
     * Docker 编排配置
     */
    private Docker docker = new Docker();

    @Data
    public static class Docker {

        /**
         * 是否允许使用 Docker 编排（关闭后强制走 Noop，部署返回失败并说明原因）
         */
        private boolean enabled = true;

        /**
         * docker CLI 可执行文件（默认使用 PATH 中的 docker）
         */
        private String command = "docker";

        /**
         * 单条 docker 命令的最长执行时间（毫秒）
         */
        private long commandTimeoutMs = 10000;

        /**
         * 可用性探测结果的缓存时间（毫秒），避免每次请求都执行 docker 命令
         */
        private long availabilityCacheMs = 30000;

        /**
         * 未在环境配置中指定镜像时使用的兜底镜像
         */
        private String defaultImage = "alpine:3.20";

        /**
         * 容器名前缀，最终容器名为 prefix + 环境ID
         */
        private String namePrefix = "vulnaudit-env-";

        /**
         * 默认 docker 网络（为空则不指定）
         */
        private String network;

        /**
         * 自定义 socket 路径（默认 Linux /var/run/docker.sock，Windows //./pipe/docker_engine）
         */
        private String socketPath;
    }
}
