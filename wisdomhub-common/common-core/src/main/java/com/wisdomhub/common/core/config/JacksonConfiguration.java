package com.wisdomhub.common.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@AutoConfiguration
public class JacksonConfiguration {

    @Bean
    public Module wisdomhubJsonModule() {
        SimpleModule module = new SimpleModule("wisdomhub-json");

        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);

        module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        return module;
    }


    private static final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toInstant(ZoneOffset.UTC).toString());
        }
    }
}
