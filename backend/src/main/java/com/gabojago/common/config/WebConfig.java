package com.tripmate.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS는 SecurityConfig.corsConfigurationSource()에서 관리합니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
