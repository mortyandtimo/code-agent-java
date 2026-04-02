# code-agent-java Requirement Freeze

## Goal
Build a Java resume-ready code agent backend inspired by claude-code-from-scratch.

## Deliverable
A Spring Boot project using MVC at the interface layer and DDD in the core domain, with REST API, thin CLI, MySQL persistence, basic LLM gateway integration, and auditable task/session/event flow.

## Constraints
- Java 8 compatible
- Spring Boot 2.7.x
- Real OpenAI-compatible API integration surface
- Keep first phase minimal but structurally complete

## Acceptance Criteria
- Project compiles with Maven
- Requirement and plan docs are frozen in repo
- Core package structure exists
- Task/session/event persistence model exists
- REST endpoint and CLI entry exist

## Non-goals
- Frontend UI
- Multi-agent orchestration
- Sandbox container
- Full production-grade tool execution in phase 1
