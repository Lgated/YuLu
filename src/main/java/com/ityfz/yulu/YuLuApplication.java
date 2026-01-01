package com.ityfz.yulu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ityfz.yulu.**.mapper")
public class YuLuApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuLuApplication.class, args);
    }

}
