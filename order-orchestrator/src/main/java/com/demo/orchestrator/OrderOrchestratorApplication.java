package com.demo.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class OrderOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderOrchestratorApplication.class, args);
    }
}
