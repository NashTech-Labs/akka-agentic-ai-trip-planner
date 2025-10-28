# akka-agentic-ai-trip-planner
A sample application demonstrating how to build a trip planning system using Akka and OpenAI.

## Akka components

This sample leverages specific Akka components:

- **Workflow**: Manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.
- **EventSourced Entity**: Maintains the session memory, storing the sequence of interactions between the user and the system.
- **HTTP Endpoint**: Serves the application endpoints for interacting with the multi-agent system (`/plans`).
- **OpenAI**: The system uses OpenAI to assist in agent selection, plan creation, and summarization.
