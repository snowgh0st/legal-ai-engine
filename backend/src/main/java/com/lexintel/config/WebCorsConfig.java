package com.lexintel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SAD PATH reference: if the frontend starts getting CORS errors in the browser console after
 * a new deploy, it's almost always because the Vercel preview/production URL isn't in this
 * allow-list yet — update {@code allowedOrigins} below.
 */
@Configuration
public class WebCorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("*")
                        .allowedOrigins(
                            "http://localhost:3000",
                            "https://*.vercel.app",
                            "https://legal-ai-engine-seven.vercel.app/"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}