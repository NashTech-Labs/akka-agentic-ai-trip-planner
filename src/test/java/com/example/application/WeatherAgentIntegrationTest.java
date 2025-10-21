package com.example.application;

import akka.javasdk.testkit.TestKitSupport;
import java.util.UUID;

import com.example.domain.AgentRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WeatherAgentIntegrationTest extends TestKitSupport {

    @Test
    public void testAgent() {
        var sessionId = UUID.randomUUID().toString();
        var message = new AgentRequest("user1", "Tokyo");
        var forecast = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(WeatherAgent::query)
                .invoke(message);

        System.out.println(forecast);
        assertThat(forecast).isNotBlank();
    }
}
