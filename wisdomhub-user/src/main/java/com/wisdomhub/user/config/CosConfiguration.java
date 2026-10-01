package com.wisdomhub.user.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CosConfiguration {

    private final CosPropertyConfiguration properties;

    @Bean
    public COSClient cosClient() {

        COSCredentials credentials =
                new BasicCOSCredentials(
                        properties.getSecretId(),
                        properties.getSecretKey()
                );

        Region region = new Region(properties.getRegion());

        ClientConfig clientConfig = new ClientConfig(region);

        return new COSClient(credentials, clientConfig);
    }
}