package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.application.PlanTripWorkflow;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.http.HttpResponses;
import com.example.application.PlanView;
import com.example.entity.PreferencesEntity;

import java.util.List;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class PlanningEndpoint {

    public record Request(String message) {}
    public record AddPreference(String preference) {}

    private final ComponentClient componentClient;

    public record PlansList(List<Suggestion> suggestions) {
        static PlansList fromView(PlanView.PlanEntries entries) {
            return new PlansList(
                    entries.entries().stream().map(Suggestion::fromView).toList()
            );
        }
    }

    public record Suggestion(String userQuestion, String answer) {
        static Suggestion fromView(PlanView.PlanEntry entry) {
            return new Suggestion(entry.userQuestion(), entry.finalAnswer());
        }
    }

    public PlanningEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/plans/{userId}")
    public HttpResponse suggestPlans(String userId, Request request) {
        var sessionId = UUID.randomUUID().toString();
        var res = componentClient
                .forWorkflow(sessionId)
                .method(PlanTripWorkflow::start)
                .invoke(new PlanTripWorkflow.Request(userId, request.message()));
        return HttpResponses.created(res, "/plans/" + userId + "/" + sessionId);
    }

    @Get("/plans/{userId}/{sessionId}")
    public HttpResponse getAnswer(String userId, String sessionId) {
        var res = componentClient
                .forWorkflow(sessionId)
                .method(PlanTripWorkflow::getAnswer)
                .invoke();

        if (res.isEmpty()) return HttpResponses.notFound(
                "Answer for '" + sessionId + "' not available (yet)"
        );
        else return HttpResponses.ok(res);
    }

    @Post("/preferences/{userId}")
    public HttpResponse addPreference(String userId, AddPreference request) {
        componentClient
                .forEventSourcedEntity(userId)
                .method(PreferencesEntity::addPreference)
                .invoke(new PreferencesEntity.AddPreference(request.preference()));

        return HttpResponses.created();
    }

    @Get("/plans/{userId}")
    public PlansList listPlans(String userId) {
        var viewResult = componentClient
                .forView()
                .method(PlanView::getPlans)
                .invoke(userId);

        return PlansList.fromView(viewResult);
    }
}
