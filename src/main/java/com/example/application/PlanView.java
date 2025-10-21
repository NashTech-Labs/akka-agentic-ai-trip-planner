package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.util.List;

@ComponentId("plan-view")
public class PlanView extends View {

    public record PlanEntries(List<PlanEntry> entries) {}

    public record PlanEntry(
            String userId,
            String sessionId,
            String userQuestion,
            String finalAnswer
    ) {}

    @Query("SELECT * AS entries FROM plans WHERE userId = :userId")
    public QueryEffect<PlanEntries> getPlans(String userId) {
        return queryResult();
    }

    @Consume.FromWorkflow(PlanTripWorkflow.class)
    public static class Updater extends TableUpdater<PlanEntry> {

        public Effect<PlanEntry> onStateChange(PlanTripWorkflow.State state) {
            var sessionId = updateContext().eventSubject().get();
            return effects()
                    .updateRow(
                            new PlanEntry(state.userId(), sessionId, state.userQuery(), state.finalAnswer())
                    );
        }

        @DeleteHandler
        public Effect<PlanEntry> onDelete() {
            return effects().deleteRow();
        }
    }
}
