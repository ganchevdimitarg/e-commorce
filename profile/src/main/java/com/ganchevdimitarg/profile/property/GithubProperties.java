package com.ganchevdimitarg.profile.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GithubProperties(String clientId, String secret) {}