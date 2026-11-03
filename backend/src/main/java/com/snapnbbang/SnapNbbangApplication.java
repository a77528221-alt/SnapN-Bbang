package com.snapnbbang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.snapnbbang.mapper")
public class SnapNbbangApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnapNbbangApplication.class, args);
    }
}

