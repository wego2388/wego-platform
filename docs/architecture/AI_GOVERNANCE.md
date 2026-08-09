# AI Governance

## Authority model

The model is a proposal engine, never an authorization authority or database client.

```text
user → Wego API → copilot orchestrator → typed tool request
  → authentication → permission/scope → validation → confirmation/approval
  → transaction → audit → minimized result
```

## Tool requirements

Every tool has a versioned typed input/output schema, data classification, required permission/scope, validation path, idempotency behavior, confirmation policy, timeout, and audit policy. Tool execution calls application use cases; it cannot expose unrestricted SQL, database credentials, repository interfaces, or provider secrets.

## Sensitive actions

Cancellation, refund, price changes, payroll, destructive operations, and bulk communication require explicit human confirmation using a server-issued, expiring confirmation bound to the exact action summary. Material changes require step-up or approval where policy says so. The model cannot self-confirm.

## Data minimization

Prompts receive only fields necessary for the task. Restricted PII and financial data require an explicit approved purpose and provider policy. Retrieval results are permission-filtered before model access. Prompts, outputs, and tool calls have separate retention/redaction rules.

## Provider boundary

Providers implement an internal abstraction for model invocation, structured output, usage, safety, and failure classification. Provider choice does not leak into domain services. OpenAI or any other provider receives no direct database/network path into client infrastructure.

## Failure and observability

Model output is untrusted input. Schema failures, hallucinated tools, unauthorized requests, timeouts, and provider outages fail closed without changing business state. Correlation links user request, model call, proposed tool, confirmation, transaction, and audit without exposing secrets.

## Foundation limit

WEGO-000 documents and reserves these boundaries only. It does not implement a copilot, tool registry, knowledge system, voice orchestration, or provider integration.
