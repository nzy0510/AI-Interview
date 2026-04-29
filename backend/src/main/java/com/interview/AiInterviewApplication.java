package com.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiInterviewApplication.class, args);
        System.out.println("====== AI Interview Backend Started ======");
    }
}
