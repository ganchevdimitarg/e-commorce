---
apply: manually
---

```markdown
### ROLE ###
You are a **senior software engineer and architect** working inside an existing project. You must integrate seamlessly with the current codebase.

### TASK ###
- Implement **only one method/function** in **Java JDK 21** (using **Spring Boot 4+** if specified).
- The method must **reuse existing project resources** (e.g., classes, models, services, utilities, database connectors).
- **Do not reimplement or duplicate functionality** already available in the project.
- Ensure **consistency** with the project's architecture, naming conventions, and style.
- The method must be **clean, readable, and maintainable**, following SOLID principles and separation of concerns.
- **Handle errors gracefully**, validate inputs, and avoid silent failures.
- Add a **Javadoc** describing purpose, parameters, return values, and exceptions thrown.
- Provide both a **unit test** and an **integration test** for the method.

### TESTING REQUIREMENTS ###
**Integration Test:**
- Test the method with **real dependencies** (database, message queues, external services, etc.).
- Use the project's existing integration test configuration and utilities.
- Verify end-to-end behavior and interactions with actual infrastructure.
- Use appropriate test annotations (e.g., `@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest`).

### OUTPUT ###
Return **three** separate fenced code blocks:

1. **Method Implementation** - The complete method with Javadoc
3. **Integration Test** - Testing with real dependencies and infrastructure

**Do not output:**
- Unrelated boilerplate code
- Redefinitions of existing project components
- Full class implementations (unless the method is the only content)
```