package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import com.example.domain.AgentRequest;

@ComponentId("weather-agent")
@AgentDescription(
        name = "Weather Agent",
        description = """
          An agent that provides weather information. It can provide current weather,
          forecasts, and other related information.
        """,
        role = "worker"
)
public class WeatherAgent extends Agent {

    private static final String SYSTEM_MESSAGE =
            """
            You are a weather agent.
            Your job is to provide weather information.
            You provide current weather, forecasts, and other related information.
            """.stripIndent();

    public Effect<String> query(AgentRequest request) {
        // prettier-ignore
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(request.message())
                .thenReply();
    }
}
