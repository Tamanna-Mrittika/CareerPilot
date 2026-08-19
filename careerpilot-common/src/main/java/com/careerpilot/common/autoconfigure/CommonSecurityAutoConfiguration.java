package com.careerpilot.common.autoconfigure;

import com.careerpilot.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Applies the shared resource-server rules to any servlet service configured with a
 * {@code jwk-set-uri}.
 *
 * <p>The property condition matters for tests: a slice test with no identity service
 * running would otherwise fail at startup trying to fetch a JWKS that does not exist.
 */
@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "jwk-set-uri")
@Import(ResourceServerSecurityConfig.class)
public class CommonSecurityAutoConfiguration {
}
