package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.domain.PreferencesEvent;
import com.example.entity.PreferencesEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("preferences-consumer")
@Consume.FromEventSourcedEntity(PreferencesEntity.class)
public class PreferencesConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesConsumer.class);

    private final ComponentClient componentClient;

    public PreferencesConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onPreferenceAdded(PreferencesEvent.PreferenceAdded event) {
        var userId = messageContext().eventSubject().get(); // the entity id
        logger.info("Preference added for user {}: {}", userId, event.preference());

        // Get all plan (sessions) for this user from the PlanView
        var plans = componentClient
                .forView()
                .method(PlanView::getPlans)
                .invoke(userId);

        // Call EvaluatorAgent for each session
        for (var plan : plans.entries()) {
            if (plan.finalAnswer() != null && !plan.finalAnswer().isEmpty()) {
                var evaluationRequest = new EvaluatorAgent.EvaluationRequest(
                        userId,
                        plan.userQuestion(),
                        plan.finalAnswer()
                );

                var evaluationResult = componentClient
                        .forAgent()
                        .inSession(plan.sessionId())
                        .method(EvaluatorAgent::evaluate)
                        .invoke(evaluationRequest);

                logger.info(
                        "Evaluation completed for session {}: score={}, feedback='{}'",
                        plan.sessionId(),
                        evaluationResult.score(),
                        evaluationResult.feedback()
                );

                if (evaluationResult.score() <= 0) {
                    // run the workflow again to generate a better answer

                    componentClient
                            .forWorkflow(plan.sessionId())
                            .method(PlanTripWorkflow::runAgain)
                            .invoke();

                    logger.info(
                            "Started workflow {} for user {} to re-answer question: '{}'",
                            plan.sessionId(),
                            userId,
                            plan.userQuestion()
                    );
                }
            }
        }

        return effects().done();
    }
}
