package io.mycelar.springai;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.routing.MycelarRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;

class SpringAiModuleSmokeTest {

    @Test
    void coreAndSpringAiTypesAreVisible() {
        assertThat(MycelarRuntime.class.getName()).contains("mycelar");
        assertThat(ChatClient.class.getName()).contains("springframework");
        assertThat(AutoConfiguration.class.getName()).contains("springframework");
    }
}
