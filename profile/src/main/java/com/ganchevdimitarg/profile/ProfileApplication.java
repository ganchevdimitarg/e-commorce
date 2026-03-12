package com.ganchevdimitarg.profile;

import com.ganchevdimitarg.profile.property.EcommerceOAuth2Properties;
import com.ganchevdimitarg.profile.property.GithubProperties;
import com.ganchevdimitarg.profile.property.PaymentServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GithubProperties.class, EcommerceOAuth2Properties.class, PaymentServiceProperties.class})
public class ProfileApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProfileApplication.class, args);
    }
}
