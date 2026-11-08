package com.wisdomhub.video.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.mps.v20190612.MpsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MpsConfiguration {

    private final MpsProperties properties;

    @Bean
    public MpsClient mpsClient() {
        Credential credential = new Credential(properties.getSecretId(), properties.getSecretKey());

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(properties.getEndpoint());
        httpProfile.setConnTimeout(5);
        httpProfile.setReadTimeout(30);

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        return new MpsClient(credential, properties.getRegion(), clientProfile);
    }
}
