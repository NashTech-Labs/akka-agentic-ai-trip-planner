# akka-agentic-ai-trip-planner
A sample application demonstrating how to build a trip planning system using Akka and OpenAI.

## Akka components

This sample leverages specific Akka components:

- **Workflow**: Manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.
- **EventSourced Entity**: Maintains the session memory, storing the sequence of interactions between the user and the system.
- **HTTP Endpoint**: Serves the application endpoints for interacting with the multi-agent system (`/plans`).
- **OpenAI**: The system uses OpenAI to assist in agent selection, plan creation, and summarization.

## Running the application

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher

### Build and run

To run the application, you need to provide the following environment variable:
- `OPENAI_API_KEY`: Your OpenAI API key. If you prefer to use a different AI Service, follow the instructions in `application.conf` to change it.

Set the environment variables:

- On Linux or macOS:

  ```shell
  export OPENAI_API_KEY=your-openai-api-key
  ```

- On Windows (command prompt):

  ```shell
  set OPENAI_API_KEY=your-openai-api-key
  ```

To build and run the application, go through the blog series - [Akka Agentic AI](https://blog.nashtechglobal.com/akka-agentic-ai-secret-to-planning-a-perfect-trip-part-6/).
