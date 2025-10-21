package com.example.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a agent plan consisting of multiple steps to be executed by different agents.
 */
public record AgentPlan(List<AgentPlanStep> steps) {
    /**
     * Creates an empty plan with no steps.
     */
    public AgentPlan() {
        this(new ArrayList<>());
    }
}
