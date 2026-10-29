package com.vulnaudit.service.env;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.config.EnvironmentProvisionProperties;
import com.vulnaudit.entity.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Docker 部署器：通过 docker CLI 真实创建 / 启动 / 停止容器
 *
 * <p>可用性判定（任一不满足即不可用）：
 * <ol>
 *     <li>{@code app.env.docker.enabled=true}；</li>
 *     <li>宿主机存在 Docker 端点：环境变量 {@code DOCKER_HOST}、或配置的 socket 路径、
 *         或平台默认 socket（Linux {@code /var/run/docker.sock}、Windows {@code //./pipe/docker_engine}）；</li>
 *     <li>{@code docker version} 能在超时内返回服务端版本。</li>
 * </ol>
 *
 * <p>环境 {@code config}（JSON）支持字段：
 * <pre>
 * {
 *   "image": "nginx:alpine",          // 必填（缺省用 app.env.docker.default-image）
 *   "command": ["nginx", "-g", "daemon off;"],
 *   "env": {"KEY": "VALUE"},
 *   "ports": {"8081": "80"},
 *   "network": "bridge"
 * }
 * </pre>
 */
@Slf4j
@Component
public class DockerProvisioner implements EnvironmentProvisioner {

    private final EnvironmentProvisionProperties properties;
    private final ObjectMapper objectMapper;

    private volatile long availabilityCheckedAt;
    private volatile boolean availableCache;
    private volatile String availabilityDetail = "尚未探测";

    public DockerProvisioner(EnvironmentProvisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "docker";
    }

    @Override
    public boolean isAvailable() {
        refreshAvailabilityIfNeeded();
        return availableCache;
    }

    @Override
    public String availabilityMessage() {
        refreshAvailabilityIfNeeded();
        if (availableCache) {
            return "Docker 容器编排已就绪（" + availabilityDetail + "）";
        }
        return EnvironmentProvisioner.UNAVAILABLE_MESSAGE + "（" + availabilityDetail + "）";
    }

    // ------------------------------------------------------------------ 容器操作

    @Override
    public ProvisionResult provision(Environment environment) {
        if (environment == null || environment.getId() == null) {
            return ProvisionResult.fail("环境信息不完整，无法部署");
        }
        if (!isAvailable()) {
            return ProvisionResult.fail(availabilityMessage());
        }

        Map<String, Object> config;
        try {
            config = parseConfig(environment.getConfig());
        } catch (IllegalArgumentException e) {
            return ProvisionResult.fail(e.getMessage());
        }

        String image = text(config.get("image"));
        if (image == null) {
            image = text(properties.getDocker().getDefaultImage());
        }
        if (image == null) {
            return ProvisionResult.fail("环境配置缺少 image 镜像，无法创建容器");
        }

        EnvironmentProvisionProperties.Docker docker = properties.getDocker();
        String containerName = containerName(environment.getId());

        List<String> command = new ArrayList<>();
        command.add(docker.getCommand());
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(containerName);
        command.add("--label");
        command.add("vulnaudit.environment=" + environment.getId());

        String network = text(config.get("network"));
        if (network == null) {
            network = text(docker.getNetwork());
        }
        if (network != null) {
            command.add("--network");
            command.add(network);
        }

        Map<String, Object> envVars = asMap(config.get("env"));
        envVars.forEach((key, value) -> {
            command.add("-e");
            command.add(key + "=" + value);
        });

        Map<String, Object> ports = asMap(config.get("ports"));
        ports.forEach((host, container) -> {
            command.add("-p");
            command.add(host + ":" + container);
        });

        command.add(image);
        command.addAll(commandArgs(config.get("command")));

        CommandResult runResult = run(command, docker.getCommandTimeoutMs());
        if (runResult.timedOut()) {
            return ProvisionResult.fail("docker run 超时（" + docker.getCommandTimeoutMs() + "ms）");
        }
        if (runResult.exitCode() != 0) {
            String stderr = firstLine(runResult.stderr());
            if (stderr != null && stderr.toLowerCase(Locale.ROOT).contains("already in use")) {
                log.info("容器名已存在，改为启动已有容器: name={}", containerName);
                CommandResult startResult = run(List.of(docker.getCommand(), "start", containerName), docker.getCommandTimeoutMs());
                if (startResult.exitCode() == 0) {
                    return ProvisionResult.ok(resolveContainerId(containerName));
                }
                return ProvisionResult.fail("容器已存在但启动失败: " + firstLine(startResult.stderr()));
            }
            log.warn("docker run 失败: name={}, stderr={}", containerName, stderr);
            return ProvisionResult.fail("docker run 失败: " + (stderr == null ? "未知错误" : stderr));
        }

        String containerId = resolveContainerId(containerName);
        log.info("容器创建并启动成功: environmentId={}, container={}", environment.getId(), containerId);
        return ProvisionResult.ok(containerId);
    }

    @Override
    public ProvisionResult start(Environment environment) {
        if (environment == null) {
            return ProvisionResult.fail("环境信息不完整，无法启动");
        }
        if (!isAvailable()) {
            return ProvisionResult.fail(availabilityMessage());
        }
        String containerId = text(environment.getContainerId());
        if (containerId == null) {
            return ProvisionResult.fail("环境尚未部署（缺少 containerId），请先执行部署");
        }
        CommandResult result = run(List.of(properties.getDocker().getCommand(), "start", containerId),
                properties.getDocker().getCommandTimeoutMs());
        if (result.exitCode() == 0) {
            return ProvisionResult.ok(containerId);
        }
        return ProvisionResult.fail("docker start 失败: " + firstLine(result.stderr()));
    }

    @Override
    public ProvisionResult stop(Environment environment) {
        if (environment == null) {
            return ProvisionResult.fail("环境信息不完整，无法停止");
        }
        if (!isAvailable()) {
            return ProvisionResult.fail(availabilityMessage());
        }
        String containerId = text(environment.getContainerId());
        if (containerId == null) {
            return ProvisionResult.fail("环境没有关联容器，无法停止");
        }
        CommandResult result = run(List.of(properties.getDocker().getCommand(), "stop", "-t", "10", containerId),
                properties.getDocker().getCommandTimeoutMs());
        if (result.exitCode() == 0) {
            return ProvisionResult.ok(containerId);
        }
        return ProvisionResult.fail("docker stop 失败: " + firstLine(result.stderr()));
    }

    @Override
    public ProvisionResult remove(Environment environment) {
        if (environment == null || text(environment.getContainerId()) == null || !isAvailable()) {
            return ProvisionResult.fail("无法删除容器（环境无容器或 Docker 不可用）");
        }
        CommandResult result = run(List.of(properties.getDocker().getCommand(), "rm", "-f", environment.getContainerId()),
                properties.getDocker().getCommandTimeoutMs());
        return result.exitCode() == 0
                ? ProvisionResult.ok(environment.getContainerId())
                : ProvisionResult.fail("docker rm 失败: " + firstLine(result.stderr()));
    }

    // ------------------------------------------------------------------ 可用性探测

    private synchronized void refreshAvailabilityIfNeeded() {
        long cacheMs = Math.max(0L, properties.getDocker().getAvailabilityCacheMs());
        long now = System.currentTimeMillis();
        if (now - availabilityCheckedAt < cacheMs) {
            return;
        }
        applyProbe(probe());
        availabilityCheckedAt = now;
    }

    /**
     * 内部：强制刷新探测结果（测试用）
     */
    synchronized void forceRefreshAvailability() {
        applyProbe(probe());
        availabilityCheckedAt = System.currentTimeMillis();
    }

    private void applyProbe(Probe probe) {
        availableCache = probe.available();
        availabilityDetail = probe.detail();
    }

    private Probe probe() {
        EnvironmentProvisionProperties.Docker docker = properties.getDocker();
        if (!docker.isEnabled()) {
            return new Probe(false, "app.env.docker.enabled=false，容器编排已关闭");
        }

        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank()) {
            String socketPath = resolveSocketPath(docker);
            if (!socketPath.startsWith("//./pipe") && !socketPath.startsWith("npipe:")
                    && !Files.exists(Path.of(socketPath))) {
                return new Probe(false, "未找到 Docker socket: " + socketPath);
            }
            if (!socketPath.startsWith("//./pipe") && !socketPath.startsWith("npipe:")
                    && !Files.isReadable(Path.of(socketPath))) {
                return new Probe(false, "Docker socket 不可读: " + socketPath);
            }
        }

        CommandResult result = run(List.of(docker.getCommand(), "version", "--format", "{{.Server.Version}}"), 3000);
        if (result.timedOut()) {
            return new Probe(false, "docker CLI 探测超时");
        }
        if (result.exitCode() != 0) {
            String error = firstLine(result.stderr());
            return new Probe(false, "docker CLI 不可用: " + (error == null ? "未知错误" : error));
        }
        String version = result.stdout() == null ? "" : result.stdout().trim();
        return new Probe(true, version.isBlank() ? "docker CLI 可用" : "Docker " + version);
    }

    private String resolveSocketPath(EnvironmentProvisionProperties.Docker docker) {
        String configured = text(docker.getSocketPath());
        if (configured != null) {
            return configured;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "//./pipe/docker_engine" : "/var/run/docker.sock";
    }

    // ------------------------------------------------------------------ 辅助

    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("环境配置不是合法的 JSON 对象: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private List<String> commandArgs(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    private String resolveContainerId(String containerName) {
        CommandResult inspect = run(
                List.of(properties.getDocker().getCommand(), "inspect", "-f", "{{.Id}}", containerName),
                Math.min(3000L, properties.getDocker().getCommandTimeoutMs()));
        String id = inspect.exitCode() == 0 ? text(inspect.stdout()) : null;
        return id == null ? containerName : id;
    }

    private String containerName(java.util.UUID environmentId) {
        String prefix = text(properties.getDocker().getNamePrefix());
        String base = (prefix == null ? "vulnaudit-env-" : prefix) + environmentId;
        return base.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] lines = value.trim().split("\n");
        String line = lines[0].trim();
        return line.length() > 300 ? line.substring(0, 300) : line;
    }

    /**
     * 执行外部命令，捕获 stdout/stderr 并强制超时
     */
    CommandResult run(List<String> command, long timeoutMs) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            process = builder.start();
        } catch (IOException e) {
            return new CommandResult(-1, "", "命令无法执行: " + e.getMessage(), false);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutDrainer = drain(process.getInputStream(), stdout);
        Thread stderrDrainer = drain(process.getErrorStream(), stderr);

        try {
            boolean finished = process.waitFor(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(stdoutDrainer);
                joinQuietly(stderrDrainer);
                return new CommandResult(-1, stdout.toString(), stderr.toString(), true);
            }
            joinQuietly(stdoutDrainer);
            joinQuietly(stderrDrainer);
            return new CommandResult(process.exitValue(), stdout.toString(), stderr.toString(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(-1, stdout.toString(), stderr.toString(), true);
        }
    }

    private Thread drain(InputStream stream, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!target.isEmpty()) {
                        target.append('\n');
                    }
                    target.append(line);
                }
            } catch (IOException ignored) {
                // 进程被强杀时读流中断属正常
            }
        }, "docker-provisioner-io");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void joinQuietly(Thread thread) {
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 命令执行结果
     */
    record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }

    /**
     * 可用性探测结果
     */
    private record Probe(boolean available, String detail) {
    }
}
