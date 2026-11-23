package org.zz.mychat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("org.zz.mychat.mapper")
@EnableAsync
public class MyChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyChatApplication.class, args);
    }

}
