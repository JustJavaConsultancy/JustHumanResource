package com.justjava.humanresource.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;



class SchedulingConfigurationDefaultTest {

    @Test
    void schedulingRemainsEnabledWhenPropertyIsAbsent() {
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
