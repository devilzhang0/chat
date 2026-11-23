package org.zz.mychat.config;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

@Deprecated
/**
 * 该类的作用 优先于 application中的端口号，springboot 与 swagger 启动还是同一个端口
 * 注入容器生效
 * @Component
 */
public class SwaggerServerCustomizer
        implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {


    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        if (isSwaggerContext()) {
            factory.setPort(9090); // 设置 Swagger 端口
        }
    }

    private boolean isSwaggerContext() {
        // 这里可以根据环境变量、配置或者其他条件决定是否启用 Swagger 端口
        return true;
    }
}
