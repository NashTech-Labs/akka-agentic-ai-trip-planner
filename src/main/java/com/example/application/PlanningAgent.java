package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import com.example.domain.AgentRequest;
import com.example.entity.PreferencesEntity;

import java.util.stream.Collectors;

@ComponentId("planning-agent")
@AgentDescription(
        name = "Planning Agent",
        description = """
          An agent that suggests plans in the real world. Like for example,
          a team building activity, sports, an indoor or outdoor game,
          board games, a city trip, etc.
        """,
        role = "worker"
)
public final class PlanningAgent extends Agent {

    private static final String SYSTEM_MESSAGE =
            """
            You are a planning agent. Your job is to suggest places in the
            real world. Like for example, a metropolitan city, hill station, a costal region,
            a cruise trip, etc.
            """.stripIndent();

    private final ComponentClient componentClient;

    public PlanningAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect<String> query(AgentRequest request) {
        var allPreferences = componentClient
                .forEventSourcedEntity(request.userId())
                .method(PreferencesEntity::getPreferences)
                .invoke();

        String userMessage;
        if (allPreferences.entries().isEmpty()) {
            userMessage = request.message();
        } else {
            userMessage = request.message() +
                    "\nPreferences:\n" +
                    allPreferences.entries().stream().collect(Collectors.joining("\n", "- ", ""));
        }

        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(userMessage)
                .thenReply();
    }
}
