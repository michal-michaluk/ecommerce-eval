# Testing

The project follows a test pyramid: fast unit tests at the base, service tests with fakes in the middle, integration tests with Testcontainers at the top.

---

## Test Pyramid

| Layer | Technology | Speed | What it tests |
|-------|-----------|-------|---------------|
| **Unit** | JUnit 5 + AssertJ | ms | Aggregate invariants, value objects, policy rules |
| **Service** | JUnit 5 + Fake Repo | ms | Service orchestration, command application |
| **Integration** | Testcontainers + Spring Boot | seconds | Repository persistence, projection updates |
| **Architecture** | ArchUnit | seconds | Package dependency rules, context isolation |
| **Contract** | Pact (consumer/provider) | minutes | API compatibility between services |

---

## Unit Tests (Domain Model)

Test the aggregate and value objects directly — no Spring context, no database. Use test fixtures and custom assertions.

**How to implement:**
- Package-private test class in the same package as the tested class
- Use `DeviceFixture` static factory methods for test data
- Use `DeviceConfigurationAssert` for fluent domain-specific assertions
- Build domain objects through a fixture — never `new` with more than 2 constructor arguments
- Assert aggregate state through the snapshot or the `{Name}Assert` object — never through field access or a getter
- One test method per business scenario

```java
class DeviceTest {

    @Test
    void assignStationToOwner() {
        Device device = givenDevice();
        device.assignTo(someOtherOwnership());

        assertThat(device)
                .hasOwnership(someOtherOwnership())
                .hasNoViolations();
    }

    @Test
    void resetOwnership() {
        Device device = givenDevice();
        device.assignTo(Ownership.unowned());

        assertThat(device)
                .hasOwnership(Ownership.unowned())
                .hasSettings(Settings.defaultSettings())
                .hasLocation(null)
                .hasOpeningHours(OpeningHours.alwaysOpened())
                .hasViolationsLikeNotConfiguredDevice();
    }
}
```

**Best practices:**
- Use descriptive test method names in business language
- Structure as given/when/then — visible through fixture call / method call / assertion chain
- Test each business method independently

---

## Service Tests

Test the service layer with a fake repository implementation — no Spring, no database.

**How to implement:**
- Inline `FakeRepo` implementing the repository port with an in-memory `HashMap`
- Construct the service directly: `new DeviceService(new FakeRepo())`
- Use `DeviceFixture` for test data

```java
class DeviceServiceTest {

    final Map<String, Device> devices = new HashMap<>();
    final DeviceService service = new DeviceService(new FakeRepo());

    @Test
    void update() {
        String existingDeviceId = givenDevice();

        Optional<DeviceConfiguration> configuration = service.updateDevice(
                existingDeviceId,
                builder().openingHours(closedAtWeekend())
                        .settings(settingsWithPublicAccessAndShowOnMapOnly())
                        .build()
        );

        assertThat(configuration)
                .hasOwnership(ownership())
                .hasLocation(location())
                .hasOpeningHours(closedAtWeekend())
                .hasSettings(settingsForPublicDevice())
                .hasNoViolations();
    }

    class FakeRepo implements DeviceRepository {
        public Optional<Device> get(String deviceId) {
            return Optional.ofNullable(devices.get(deviceId));
        }
        public void save(Device device) {
            devices.put(device.deviceId, device);
        }
    }
}
```

---

## Integration Tests

Integration tests start a full Spring Boot context with Testcontainers (PostgreSQL). Use the custom `@IntegrationTest` annotation.

**How to implement:**
- Annotate with `@IntegrationTest` — composite of `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("integration-test")`
- `IntegrationConfiguration` starts Testcontainers PostgreSQL
- Use `@Autowired` to inject repositories, services, or projections
- Use `@Transactional` for test isolation (rollback after each test)

```java
@IntegrationTest
@Transactional
class DeviceEventSourcingRepositoryTest {

    @Autowired
    DeviceEventSourcingRepository repository;

    @Test
    void saveAndGetDevice() {
        Device saved = DeviceFixture.givenStepByStepConfiguredDevice();

        transactional(() -> repository.save(saved));
        Optional<Device> read = transactional(() -> repository.get(saved.deviceId));

        assertThat(read).isExactlyLike(saved);
    }
}
```

**Best practices:**
- `@Transactional` on test class rolls back after each test — no cleanup needed
- Use `TestTransaction.transactional()` helper when you need explicit transaction boundaries
- Use `JsonAssert` for JSON output assertions with strict or lenient mode

---

## Custom Test Infrastructure

### @IntegrationTest Composite Annotation

```java[exercise-1-strategy-foundation.md](../../../../ai-for-business/marketing/exercise-1-strategy-foundation.md)
[exercise-2-prospecting-plan.md](../../../../ai-for-business/marketing/exercise-2-prospecting-plan.md)
[exercise-3-creation-publication.md](../../../../ai-for-business/marketing/exercise-3-creation-publication.md)
[exercise-4-evaluation-iteration.md](../../../../ai-for-business/marketing/exercise-4-evaluation-iteration.md)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles
public @interface IntegrationTest {
    @AliasFor(annotation = ActiveProfiles.class, attribute = "profiles")
    String[] profiles() default {"integration-test"};
}
```

### JsonAssert — Fluent JSON Assertion

```java
public class JsonAssert {
    public static JsonAssert assertThat(Object object) { ... }

    public JsonAssert isExactlyLike(String json) { ... }   // strict (field order + count)
    public JsonAssert hasFieldsLike(String json) { ... }   // lenient (subset of fields)
}
```

**Used for:** repository integration tests to verify JSONB serialization round-trips.

### TestTransaction — Programmatic Transaction Control

```java
public class TestTransaction {
    public static void transactional(Runnable body) { ... }
    public static <T> T transactional(Supplier<T> body) { ... }
}
```

---

## Architecture Tests (ArchUnit)

Each context has an `ArchitectureOf{Context}Test` that enforces:
- Adapters depend only on their own context + tools
- Services depend only on their own context + Spring stereotypes
- Model depends only on model + java + Jackson + validation
- Adapters are accessed only within their context
- Services are accessed only within their context or by mediators
- Model internals are accessed only within their context

See `arch-unit.md` for details.

---

## Fixture Pattern

Fixtures are public classes with static factory methods producing domain objects. One fixture per context.

```java
public class DeviceFixture {
    public static Device givenDevice() { ... }
    public static Ownership ownership() { ... }
    public static Location location() { ... }
    public static Settings settingsForPublicDevice() { ... }
}
```

**NOGO:**
- Do not use mutable shared fixture instances — always create fresh via factory methods
- Do not test domain logic only through integration tests — unit tests must cover all business rules
- Do not skip architecture tests for new contexts

---

## Object Construction and Aggregate Assertions

Two rules keep a test readable in business terms and keep domain internals out of it. The construction rule is enforced on test sources by `test.new-needs-factory` in `example/src/test/java/.sqt.yaml`. The assertion rule is **not** statically checkable — `assertThat(device)` and `assertThat(visibility)` are syntactically identical — so it relies on review.

### Construction — fixtures only

A domain object whose constructor takes more than two parameters is never assembled with `new` inside a test; that belongs in a `{Name}Fixture` factory method. A wide `new` in a test bakes the constructor shape into every scenario, so a signature change ripples through the whole suite and each test reads as an object graph instead of a scenario.

```java
// NO — 6 constructor arguments inside the test
Device device = new Device(randomId(), new ArrayList<>(), ownership(), location(),
        OpeningHours.alwaysOpened(), Settings.defaultSettings());

// YES — one named factory call
Device device = DeviceFixture.givenDevice();
```

The rule covers test sources only, and any code inside a `{Name}Fixture` is exempt. The aggregate's own `static` factory method (`Device.newDevice`) and the adapters that reconstruct an aggregate from persisted state (`new Device(...)` in a repository) legitimately call the wide constructor — that is where construction belongs.

### Assertions — snapshot or assert object

Assert aggregate state through the read-only snapshot (`toDeviceConfiguration()`, `toSnapshot()`) or through the context's `{Name}Assert` object. Never reach into the aggregate with a field read or a getter.

```java
// NO — reaches into aggregate internals
assertThat(product.events).hasSize(1);
assertThat(device.ownership).isEqualTo(someOtherOwnership());

// YES — the assert object reads the aggregate, the test reads business terms
assertThat(product).hasEvents(1);
assertThat(device).hasOwnership(someOtherOwnership());
```

`{Name}Assert` lives in the same package as the aggregate, so it is the one place permitted to read internals; the test itself stays outside. The snapshot gives the same guarantee for reads that cross a boundary (repository, projection, HTTP) — `JsonAssert.isExactlyLike` / `hasFieldsLike` assert the whole documented read model instead of selected fields.

**NOGO:**
- Do not call `new` on a domain object with more than 2 constructor parameters outside a `{Name}Fixture` factory method
- Do not assert aggregate internals through field access or getters — use the snapshot or the `{Name}Assert` object
