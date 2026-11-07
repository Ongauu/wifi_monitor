package com.crimson.wifimonitor;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(WifiMonitorProperties.class)
public class WifiMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WifiMonitorApplication.class, args);
    }
}
