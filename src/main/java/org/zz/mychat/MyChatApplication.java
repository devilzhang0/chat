package org.zz.mychat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("org.zz.mychat.mapper")
@EnableScheduling
public class MyChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyChatApplication.class, args);
    }

}
