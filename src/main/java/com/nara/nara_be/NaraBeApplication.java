package com.nara.nara_be;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.nara.nara_be.dao")
public class NaraBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NaraBeApplication.class, args);
    }

}
