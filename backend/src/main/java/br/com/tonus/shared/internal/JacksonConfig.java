package br.com.tonus.shared.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializerByType(LocalTime.class, new LocalTimeSerializer(TIME_FORMAT));
    }
}
