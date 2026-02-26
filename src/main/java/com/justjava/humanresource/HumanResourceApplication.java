package com.justjava.humanresource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
/*(exclude = {
        org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration.class
})*/
public class HumanResourceApplication {
	public static void main(String[] args) {
		SpringApplication.run(HumanResourceApplication.class, args);
	}

}
