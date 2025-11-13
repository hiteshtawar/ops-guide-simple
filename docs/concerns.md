❌ Major Concerns with Direct Database Access
1. Violates Service Ownership & Boundaries
Each service should own its data exclusively
Direct DB access breaks encapsulation and creates tight coupling
You become dependent on internal implementation details (schema) instead of a stable API contract
2. Bypasses Critical Business Logic
The downstream service likely has:
✅ Validation rules
✅ Business logic
✅ State machine enforcement
✅ Audit logging
✅ Side effects (notifications, webhooks, integrations)
✅ Transaction management
Direct DB access skips all of this → Data integrity risks
3. Security & Compliance Issues
Need to manage database credentials (additional security surface)
API tokens are easier to rotate and manage
Audit trails might be incomplete (compliance risk)
Direct DB access may violate security policies
4. Operational Risks
┌─────────────────────────────────────────────────┐│ What happens when downstream service:          │├─────────────────────────────────────────────────┤│ • Changes schema?           → Your service breaks││ • Adds new validation?      → You bypass it     ││ • Shards/partitions DB?     → Your queries fail ││ • Migrates to new DB?       → Major rewrite     ││ • Adds required fields?     → Data corruption   │└─────────────────────────────────────────────────┘
5. Goes Against the Purpose
This is meant to be a lightweight operational support tool:
Help production support execute runbooks
Provide guided workflows
Not replace or duplicate the actual service