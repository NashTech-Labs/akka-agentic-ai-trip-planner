package com.example.domain;

/**
 * Represents a single step within an Agent Plan.
 * Each step is assigned to a specific agent and contains a command description.
 */
public record AgentPlanStep(String agentId, String query) {}