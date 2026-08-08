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
     * The project's time format is HH:mm. Jackson's default serializes
     * LocalTime with seconds ("07:00:00"), which isn't wrong, just noise: the
     * studio's schedule is set in whole minutes and nothing here has
     * second-level resolution.
     *
     * Only writing changes — on reading, the ISO parser already accepts "07:00".
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
