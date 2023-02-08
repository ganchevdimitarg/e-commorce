package com.concordeu.profile.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public record GithubRevokeTokenDto(@SerializedName("access_token") String accessToken) {
}
