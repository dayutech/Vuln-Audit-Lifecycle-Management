package com.vulnaudit.dto;

import com.vulnaudit.entity.SsoBinding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 已登录用户的 SSO 绑定（t104）
 */
@Schema(description = "SSO 绑定")
public record SsoBindingVO(UUID id, String code, String externalId, String displayName, LocalDateTime boundAt) {

    public static SsoBindingVO from(SsoBinding binding) {
        return new SsoBindingVO(binding.getId(), binding.getProviderCode(), binding.getExternalId(),
                binding.getDisplayName(), binding.getBoundAt());
    }
}
