package com.aurora.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aurora")
public class AuroraApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuroraApplication.class, args);
  }
}
