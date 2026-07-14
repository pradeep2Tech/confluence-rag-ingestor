# Injects session context so the agent skips manual mvn compile (saves tokens).
$context = @"
Maven compile hook is active for this project.
- Do NOT run mvn compile, mvn test, or mvn verify unless the user explicitly asks.
- After you edit Java files under src/main/java or src/test/java, compile runs automatically when your turn ends.
- If compile fails, you will receive the compiler output as a follow-up message — fix those errors without re-running Maven.
"@

@{ additional_context = $context.Trim() } | ConvertTo-Json -Compress
exit 0
