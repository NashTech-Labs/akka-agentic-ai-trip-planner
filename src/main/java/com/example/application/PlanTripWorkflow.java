package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import com.example.domain.AgentPlan;
import com.example.domain.AgentPlanStep;
import com.example.domain.AgentRequest;
import com.example.domain.AgentSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static java.time.Duration.ofSeconds;

@ComponentId("plan-trip")
public class PlanTripWorkflow extends Workflow<PlanTripWorkflow.State> {

    private static final Logger logger = LoggerFactory.getLogger(PlanTripWorkflow.class);

    public record Request(String userId, String message) {}

    enum Status {
        STARTED,
        COMPLETED,
        FAILED,
    }

    public record State(
            String userId,
            String userQuery,
            AgentPlan plan,
            String finalAnswer,
            Map<String, String> agentResponses,
            Status status
    ) {
        public static State init(String userId, String query) {
            return new State(userId, query, new AgentPlan(), "", new HashMap<>(), Status.STARTED);
        }

        public State withFinalAnswer(String answer) {
            return new State(userId, userQuery, plan, answer, agentResponses, status);
        }

        public State addAgentResponse(String response) {
            // when we add a response, we always do it for the agent at the head of the plan queue
            // therefore we remove it from the queue and proceed
            var agentId = plan.steps().removeFirst().agentId();
            agentResponses.put(agentId, response);
            return this;
        }

        public AgentPlanStep nextStepPlan() {
            return plan.steps().getFirst();
        }

        public boolean hasMoreSteps() {
            return !plan.steps().isEmpty();
        }

        public State withPlan(AgentPlan plan) {
            return new State(userId, userQuery, plan, finalAnswer, agentResponses, Status.STARTED);
        }

        public State complete() {
            return new State(userId, userQuery, plan, finalAnswer, agentResponses, Status.COMPLETED);
        }

        public State failed() {
            return new State(userId, userQuery, plan, finalAnswer, agentResponses, Status.FAILED);
        }
    }

    private final ComponentClient componentClient;

    public PlanTripWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect<Done> start(Request request) {
        if (currentState() == null) {
            return effects()
                    .updateState(State.init(request.userId(), request.message()))
                    .transitionTo(PlanTripWorkflow::selectAgentsStep)
                    .thenReply(Done.getInstance());
        } else {
            return effects()
                    .error("Workflow '" + commandContext().workflowId() + "' already started");
        }
    }

    public Effect<Done> runAgain() {
        if (currentState() != null) {
            return effects()
                    .updateState(State.init(currentState().userId(), currentState().userQuery()))
                    .transitionTo(PlanTripWorkflow::selectAgentsStep)
                    .thenReply(Done.getInstance());
        } else {
            return effects()
                    .error("Workflow '" + commandContext().workflowId() + "' has not been started");
        }
    }

    public ReadOnlyEffect<String> getAnswer() {
        if (currentState() == null) {
            return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
        } else {
            return effects().reply(currentState().finalAnswer());
        }
    }

    @StepName("select-agents")
    private StepEffect selectAgentsStep() {
        var selection = componentClient
                .forAgent()
                .inSession(sessionId())
                .method(SelectorAgent::selectAgents)
                .invoke(currentState().userQuery);

        logger.info("Selected agents: {}", selection.agents());
        if (selection.agents().isEmpty()) {
            var newState = currentState()
                    .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
                    .failed();
            return stepEffects().updateState(newState).thenEnd(); // terminate workflow
        } else {
            return stepEffects()
                    .thenTransitionTo(PlanTripWorkflow::createPlanStep)
                    .withInput(selection);
        }
    }

    @StepName("create-plan")
    private StepEffect createPlanStep(AgentSelection agentSelection) {
        logger.info(
                "Calling planner with: '{}' / {}",
                currentState().userQuery,
                agentSelection.agents()
        );

        var plan = componentClient
                .forAgent()
                .inSession(sessionId())
                .method(CoordinatorAgent::createPlan)
                .invoke(new CoordinatorAgent.Request(currentState().userQuery, agentSelection));

        logger.info("Execution plan: {}", plan);
        return stepEffects()
                .updateState(currentState().withPlan(plan))
                .thenTransitionTo(PlanTripWorkflow::executePlanStep);
    }

    @StepName("execute-plan")
    private StepEffect executePlanStep() {
        var stepPlan = currentState().nextStepPlan();
        logger.info(
                "Executing plan step (agent:{}), asking {}",
                stepPlan.agentId(),
                stepPlan.query()
        );
        var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query());
        if (agentResponse.startsWith("ERROR")) {
            throw new RuntimeException(
                    "Agent '" + stepPlan.agentId() + "' responded with error: " + agentResponse
            );
        } else {
            logger.info("Response from [agent:{}]: '{}'", stepPlan.agentId(), agentResponse);
            var newState = currentState().addAgentResponse(agentResponse);

            if (newState.hasMoreSteps()) {
                logger.info("Still {} steps to execute.", newState.plan().steps().size());
                return stepEffects()
                        .updateState(newState)
                        .thenTransitionTo(PlanTripWorkflow::executePlanStep);
            } else {
                logger.info("No further steps to execute.");
                return stepEffects()
                        .updateState(newState)
                        .thenTransitionTo(PlanTripWorkflow::summarizeStep);
            }
        }
    }

    private String callAgent(String agentId, String query) {
        var request = new AgentRequest(currentState().userId(), query);
        DynamicMethodRef<AgentRequest, String> call = componentClient
                .forAgent()
                .inSession(sessionId())
                .dynamicCall(agentId);
        return call.invoke(request);
    }

    @StepName("summarize")
    private StepEffect summarizeStep() {
        var agentsAnswers = currentState().agentResponses.values();
        var finalAnswer = componentClient
                .forAgent()
                .inSession(sessionId())
                .method(SummarizerAgent::summarize)
                .invoke(new SummarizerAgent.Request(currentState().userQuery, agentsAnswers));

        return stepEffects()
                .updateState(currentState().withFinalAnswer(finalAnswer).complete())
                .thenPause();
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
                .defaultStepTimeout(ofSeconds(30))
                .defaultStepRecovery(maxRetries(1).failoverTo(PlanTripWorkflow::interruptStep))
                .stepRecovery(
                        PlanTripWorkflow::selectAgentsStep,
                        maxRetries(1).failoverTo(PlanTripWorkflow::summarizeStep)
                )
                .build();
    }

    @StepName("interrupt")
    private StepEffect interruptStep() {
        logger.info("Interrupting workflow");

        return stepEffects().updateState(currentState().failed()).thenEnd();
    }

    private String sessionId() {
        return commandContext().workflowId();
    }

    private StepEffect error() {
        return stepEffects().thenEnd();
    }
}
