package com.concordeu.mail;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.enums.MailFields;
import com.concordeu.mail.enums.MailTypeEnum;
import com.concordeu.mail.helpers.CreateMailFields;
import com.concordeu.mail.service.EmailService;

@SpringBootApplication
@EnableEurekaClient
@OpenAPIDefinition(info =
@Info(title = "Mail API", description = "Documentation Mail API v1.0")
)
public class MailApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailApplication.class, args);
    }
}
