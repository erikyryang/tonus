package br.com.tonus.shared.internal;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    /**
     * Hora do projeto é HH:mm. O padrão do Jackson serializa LocalTime com
     * segundos ("07:00:00"), o que não é errado, só ruído: a agenda do estúdio
     * é marcada em minutos cheios e nada aqui tem resolução de segundo.
     *
     * Só a escrita muda — na leitura o parser ISO já aceita "07:00".
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(new SimpleModule().addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMAT)));
    }
}
