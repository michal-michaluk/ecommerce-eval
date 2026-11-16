# Infrastructure — JSON, Event Types, Configuration, Clock

Four pieces of plumbing are shared by every context and live in `tools/`: the one Jackson mapper, the Hibernate hop that lets a record
live in a JSONB column, the event-type registry that turns an event class into the `{type, version}` pair the event store writes, and the
generic JSONB configuration store. `tools/` holds no business language (`utils.no-domain-vocabulary`), and the dependency direction is
one-way: a context may use `tools/`.

## The one mapper

Built once as a static, exposed as a bean, and used by Hibernate, by the event store and by the tests.

```java
package <root-package>.tools;

@Configuration
public class JsonMapperConfiguration {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v
                    .withVisibility(PropertyAccessor.CREATOR, ANY)
                    .withVisibility(PropertyAccessor.FIELD, ANY))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
            .configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .build();

    @Bean
    ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }
}
```

- Use the one `OBJECT_MAPPER` (or the injected bean) everywhere; a second `new JsonMapper()` in a context means two JSON dialects over one
  database; `json.single-mapper`
- Keep field and creator visibility `ANY` — that is what lets a package-private record be an API payload and a JSONB column without getters
  and without widening its visibility (`structure.no-public-for-json`); `json.visibility-any`
- Ignore unknown properties so an old row survives a new component and a new client survives an old field; `json.unknown-properties-ignored`
- Disable `FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS` so `@JsonUnwrapped` composes with the type discriminator on the event union (`DomainEvent`); `json.unwrapped-type-identifiers`
- Write `BigDecimal` as plain (`51.09836221719513`, not `5.109836221719513E1`), because a decimal quantity is compared and copied between
  strategies as text; `json.bigdecimal-plain`
- Import `tools.jackson.*` for mapper and node types and `com.fasterxml.jackson.annotation` for annotations — Jackson 3 keeps the
  annotation package and moves everything else; `json.jackson3-imports`
- Derive a second mapper only as a `private static final` field inside the adapter that needs it, never by mutating `OBJECT_MAPPER`; `json.derived-mapper-declared`

## The Hibernate hop

One `FormatMapper` implementation plus one line of configuration.

```java
package <root-package>.tools;

public class JacksonJsonFormatMapper implements FormatMapper {

    private final ObjectMapper objectMapper = JsonMapperConfiguration.OBJECT_MAPPER;

    @Override
    public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        try {
            return objectMapper.readValue(charSequence.toString(),
                    objectMapper.getTypeFactory().constructType(javaType.getJavaType()));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("unreadable JSON column", e);
        }
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("unwritable JSON column", e);
        }
    }
}
```

```yaml
spring:
  jpa:
    properties:
      hibernate.type.json_format_mapper: <root-package>.tools.JacksonJsonFormatMapper
```

- Wire the format mapper once, on the boot configuration — every `jsonb` column then uses the process mapper with no per-entity
  annotation; `json.hibernate-format-mapper`
- Give the format mapper no constructor arguments and take the process mapper from a field initialiser: Hibernate instantiates it by
  class name, so there is nothing to inject; `json.hibernate-format-mapper`
- Keep exactly one `FormatMapper` — a second one is a second JSON dialect for the same columns, and Hibernate takes one; `json.hibernate-format-mapper`
- Mark the column `@JdbcTypeCode(SqlTypes.JSON)` and keep the Java type a plain record; `persistence.json-column`
- A leftover `hibernate-types` properties file is dead once the format mapper above exists: no dependency on the classpath reads it.
  Delete it rather than update it; `json.no-legacy-properties` (reviewed)

## The event-type registry

The registry is not written by hand: it is derived from the `@JsonSubTypes` names on the event union, at startup.

```java
public final class EventTypeRegistry {

    public record Type(String name, int version) {

        static Type of(String subtypeName) {
            int marker = subtypeName.lastIndexOf("_v");
            if (marker < 0) {
                throw new IllegalArgumentException("event subtype '" + subtypeName + "' must end with _v<N>");
            }
            return new Type(subtypeName.substring(0, marker), Integer.parseInt(subtypeName.substring(marker + 2)));
        }
    }

    private static Map<Class<?>, Type> types = Map.of();

    static void init(Map<Class<?>, Type> subtypes) {
        types = Map.copyOf(subtypes);
    }

    public static Type of(Class<?> eventType) {
        return types.get(eventType);
    }
}
```

```java
@Component
class EventTypeRegistryInitializer {

    @PostConstruct
    void initEventTypes() {
        DeserializationConfig config = JsonMapperConfiguration.OBJECT_MAPPER.deserializationConfig();
        AnnotationIntrospector ai = config.getAnnotationIntrospector();
        Map<Class<?>, EventTypeRegistry.Type> subtypes = Stream.of(DomainEvent.class)
                .map(type -> AnnotatedClassResolver.resolve(config, JsonMapperConfiguration.OBJECT_MAPPER.constructType(type), config))
                .flatMap(type -> ai.findSubtypes(config, type).stream())
                .collect(Collectors.toMap(NamedType::getType, type -> EventTypeRegistry.Type.of(type.getName())));
        EventTypeRegistry.init(subtypes);
    }
}
```

- Name every subtype `{Event}_v{N}` and let `Type.of` split it — it throws with an instructive message when the suffix is
  missing, which is the only validation the registry has; `event.name-version`
- Register a new event type by adding it to the union's `@JsonSubTypes`, never by editing the registry; `json.event-type-registry`
- Treat `EventTypeRegistry.of` as usable only after the context started — an initialiser that runs too early silently yields `null` for
  the type column; `json.event-type-registry-ready` (reviewed)
- Use the registry's type for the event table's `type` column and keep the version where it already is — inside the JSON
  discriminator; the column stores the unversioned name (`{Event}`) while the payload carries `{Event}_v2`; add a version column
  deliberately if you must query by version; `json.type-column-unversioned`
- Add a `LegacyDomainEvent`-style upcast in the adapter that reads the old shape, and register its subtype on the derived mapper only; `event.upcast-on-read`

## The configuration store

One table, one key, one JSONB document — for configuration a business user may edit.

```java
class ConfigurationStore {

    private final ConfigurationEntityRepository repository;

    public Configuration save(String name, Object configuration) {
        var entity = repository.findByName(name).orElseGet(() -> new ConfigurationEntity(name));
        JsonNode json = JsonMapperConfiguration.OBJECT_MAPPER.valueToTree(configuration);
        entity.configuration = json;
        repository.save(entity);
        return new Configuration(name, json);
    }

    public Optional<Configuration> get(String name) {
        return repository.findByName(name)
                .map(entity -> new Configuration(entity.name, entity.configuration));
    }

    …   // ConfigurationEntityRepository: the Spring Data interface over ConfigurationEntity

    @Entity
    @Table(name = "configuration")
    @NoArgsConstructor
    static class ConfigurationEntity {

        @Id
        String name;

        @JdbcTypeCode(SqlTypes.JSON)
        JsonNode configuration;

        ConfigurationEntity(String name) {
            this.name = name;
        }
    }

    public record Configuration(String name, JsonNode configuration) {

        public <T> Optional<T> as(Class<T> type) {
            try {
                return Optional.ofNullable(JsonMapperConfiguration.OBJECT_MAPPER.treeToValue(configuration, type));
            } catch (JacksonException e) {
                return Optional.empty();
            }
        }

        public JsonNode raw() {
            return configuration;
        }
    }
}
```

- Store a configuration under a stable `CONFIG_NAME` constant declared by the owning adapter, and fall back to the domain default in
  `get()`; `persistence.config-document-key`
- Read with `as(Class)` and return `Optional` — "no configuration" is a value, not an exception; `config.optional-read`
- Expose `raw()`/`JsonNode` only on the wire boundary, never inside a context; `config.raw-on-boundary` (reviewed)
- Keep the store generic: it knows a name and a JSON node, never a schema (`utils.generic`); a typed accessor belongs to the owning
  adapter; `config.no-schema-in-tools`
- Use a configuration document only for values a user edits at runtime; a structural setting is a `@ConfigurationProperties` class found
  by `@ConfigurationPropertiesScan` on the boot application class; `config.properties-for-structure`

## The clock

```java
@Configuration
class ClockProvider {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

- Inject `Clock` and read `Instant.now(clock)`; never call `Instant.now()` in a service, a policy or a model; `clock.injected`
- Stamp every persisted event from the injected clock: an entity that calls `Instant.now()` itself produces a timestamp that is
  untestable and can disagree with the clock the service used; `clock.no-static-now` (held at `warning`)
- Keep one `Clock` bean for the process; a second bean is a second notion of "now" and `@Primary` on a clock is a smell; `clock.single-bean` (reviewed)

## Boot configuration that `tools/` depends on

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none
  liquibase:
    change-log: classpath:db/changelog.yaml
server:
  error:
    include-message: never
```

- Keep one `@SpringBootApplication` with `@EnableJpaRepositories(considerNestedRepositories = true)` — without that flag the nested Spring
  Data interfaces inside every adapter are invisible; `structure.entry-point`
- Keep `spring.jpa.hibernate.ddl-auto: none` and point `liquibase.change-log` at the master changelog; the schema is a file, not a
  side effect; `persistence.no-ddl-auto`
- Keep `server.error.include-message: never` — an exception message never reaches a client; `http.error-message-hidden` (reviewed)
- Keep `spring.datasource.driverClassName`, the Hikari pool size and the PostgreSQL dialect in the profile, not in code; `config.profile-settings` (reviewed)
- A library on the classpath that no code imports, next to configuration keys no adapter reads, is a decision waiting to be made, not
  infrastructure; `structure.no-unused-infrastructure` (reviewed)

## What to avoid / NOGO

- Do not create an `ObjectMapper` inside a context, a test or an adapter — inject the bean or use the constant; `json.single-mapper`
- Do not configure the mapper per call (`mapper.copy().configure(...)`) — a dialect per call site is a dialect per bug report; `json.single-mapper`
- Do not add getters so Jackson can serialise; field visibility is already `ANY` (`structure.no-public-for-json`); `json.visibility-any`
- Do not hand-maintain a `Class → name` map for events; the annotation is the source; `json.event-type-registry`
- Do not put a schema, a default or a business rule into the configuration store — it is a box, the adapter owns the shape; `config.no-schema-in-tools`
- Do not read the system clock directly; pass `Clock` in; `clock.injected`
- Do not let `tools/` reference a context type, not even fully qualified — a fully qualified reference is invisible to an import-based
  rule; `utils.no-context-dependency`

## Enforcement

Nothing in this file is encoded as a rule yet: `json.*`, `config.*` and `clock.*` are proposals for the review step, and the classifier
inputs they would use already exist as name suffixes, as `framework-anywhere`, and as `in.model`.

Cheap to encode, in order of value:

| Id | Shape of the check |
|---|---|
| `json.single-mapper` | no `JsonMapper.builder()` / `new ObjectMapper()` outside the mapper configuration in `tools/` and the one derived mapper inside an adapter |
| `json.jackson3-imports` | no import of `com.fasterxml.jackson.databind..` anywhere — annotations stay `com.fasterxml.jackson.annotation..` |
| `json.hibernate-format-mapper` | the key `hibernate.type.json_format_mapper` is set in the boot configuration and names the class |
| `clock.injected` | a `Clock` field appears only in a `*Service` or an adapter, and no `Instant.now()`/`System.currentTimeMillis()` call in model code |

`json.no-legacy-properties`, `config.raw-on-boundary` and `json.event-type-registry-ready` stay reviewed.
