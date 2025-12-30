package com.example.editor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取 uploads 文件夹的绝对路径
        String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

        // 建立映射：URL(/uploads/xxx) -> 硬盘文件(file:///C:/.../uploads/xxx)
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}