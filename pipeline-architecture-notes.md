# Melody Pipeline Refactor — Architecture Notes

Context: a set of interlinked Java libraries for algorithmic music composition, used
to generate different musical parts via different combinations of pipeline stages.
Expecting many more combinations over time.

## Current state

- Melody generator modelled as a pipeline where each stage declares what data it
  depends on, and only runs once that data is present (lazy, dependency-driven
  execution order).
- This was built for a case where stage order was uncertain/dynamic. That case no
  longer applies — stage order per recipe is now known ahead of time.
- Each stage currently has **dual responsibility**: it does its musical
  transformation AND checks whether its parameters exist on the wrapper, generating
  them randomly if missing. This dual responsibility is a hangover from the old
  lazy-dependency design (stages had to be self-sufficient so order didn't matter).

## Direction agreed on

### 1. Drop the dependency-tracking pipeline in favor of a flat, ordered stage list
No dependency graph resolution needed if order is always known per recipe. Simpler
and easier to debug ("why didn't this stage run" goes away).

### 2. Move combinations into config files ("recipes")
- Recipe = ordered list of stage specs (`type` + `params`), stored as YAML/JSON.
- New combination = new config file, not new code.
- A loaded recipe should be treated as a **first-class object** (e.g.
  `CompositionRecipe`) — has identity, can be stored, versioned, compared, reused —
  not just parameters consumed and discarded.

### 3. Common interface + adapters, kept separate from config loading
- Core `PipelineStage` interface (defines input/output type, `configure(params)`).
- One small adapter class per external library class, implementing `PipelineStage`,
  absorbing that library's mismatched API. This is where all the music-library
  mess lives.
- Config-loading code stays fully generic — it only knows about `StageSpec` (type
  name + params), never about music or the external libraries.
- The **registry** (`Map<String, Supplier<PipelineStage>>` or similar) is the one
  place that links config type-names to adapter classes. Populated once at startup.
- Dependency direction: `config-loader → registry → adapters → external libraries`.
- **Population mechanism — RESOLVED: manual static registration**, not classpath
  scanning or annotation-based self-registration.
  - Classpath scanning adds startup cost and "magic" — what's registered isn't
    visible without running it, which reintroduces the same opacity decision #1
    dropped the dependency-graph pipeline to get away from ("why didn't this
    stage run").
  - Annotation-based self-registration needs either an annotation processor
    (extra build complexity) or runtime reflection (same opacity as scanning),
    and is vulnerable to Java's "static initializer only runs once the class is
    loaded" footgun if nothing else references the class first.
  - Manual registration is explicit and in one place per registry (stage
    adapters, generators — #6, conditions — #5), and is the easiest to test: a
    test can construct a registry with just the entries it needs, no classpath
    or annotation-processing sandbox required.
  - The "expecting many more combinations over time" driver (see Context, top
    of doc) is about *recipes* (config files), not Java stage/generator/
    condition types, so the type count this registration touches grows slowly
    — keeping manual registration's cost low.

### 4. Nested configs
- Add a stage type like `include`/`ref` whose param is a path to another config
  file.
- Loader recognizes this specially: recursively loads the referenced file's stage
  list and wraps it as a `SubPipelineStage` — **RESOLVED: wrap, not flatten.**
  - Flattening would discard the sub-recipe's identity at include-time, which
    contradicts decision #2 (a loaded recipe is a first-class object, not just
    parameters consumed and discarded).
  - Wrapping lets a sub-recipe (e.g. "harmony-base") be unit-tested once,
    standalone, and reused across parent recipes without re-testing it in every
    including context — same duplication argument that decided #6.
  - Wrapping also keeps debugging traces legible: a `SubPipelineStage` shows up
    as itself in logs/traces, so "why didn't this run" stays traceable to which
    included file, instead of being lost in a merged flat list.
- Enables building a reusable sub-recipe (e.g. "harmony") once, referenced from
  multiple higher-level recipes.

### 5. Conditional logic — keep in code, not a config mini-language
- `ConditionalStage` wraps a predicate (checked against pipeline context /
  output-so-far) plus the stage(s) to run if true.
- Config just references the wrapper type and its target stage(s); the actual
  condition logic is a plain Java class.
- Only worth building a config-level condition grammar/interpreter if non-coders
  need to author conditions directly — otherwise this adds a mini-language to
  maintain for no real benefit.
- **API shape — RESOLVED:**
  - Predicate type is `Predicate<Wrapper>` — no separate context object.
    `Wrapper` already *is* the pipeline's output-so-far/context (decision #1
    dropped the old dependency-graph context), so a second state-carrier would
    just duplicate it.
  - The condition class is **registry-resolved by type name**, same mechanism
    as generators (decision #6) and stage adapters (decision #3) — config
    names a `condition` type, registry maps it to a `Predicate<Wrapper>` impl.
  - Target is `List<PipelineStage>`, run in order if the predicate holds;
    `Wrapper` passes through unchanged if not — a list avoids forcing a
    wrapper-of-a-wrapper when a condition needs to gate more than one stage.
  - Testability: the predicate is unit-testable against `Wrapper` fixtures like
    any strict transformation stage; `ConditionalStage` itself only needs
    true→runs-stages-in-order / false→passthrough tests, independent of what
    the guarded stages do.

```java
class ConditionalStage implements PipelineStage {
    private final Predicate<Wrapper> condition;
    private final List<PipelineStage> stages;

    public Wrapper process(Wrapper w) {
        if (!condition.test(w)) return w;
        for (PipelineStage stage : stages) {
            w = stage.process(w);
        }
        return w;
    }
}
```

### 6. Parameter resolution: separate "provide or randomize" from transformation — RESOLVED
Two options were discussed; **the explicit generator-stage approach was chosen**
as the better fit:

- Transformation stages become **strict**: they read required params off the
  wrapper and fail if missing. No fallback logic, no randomness, no conditionals
  inside them — they only do musical transformation.
- Randomization becomes its own stage type (`RandomizeParam` or similar), placed
  earlier in the sequence, whose only job is writing a param onto the wrapper if
  it isn't already set (conditional on `hasParam(key)`, per the sketch below —
  so a recipe can still override the default by setting the value elsewhere).
- This makes "what's fixed vs. what's randomized" fully visible and decided in the
  recipe itself, not hidden inside stage code.
- Deciding factor: **testability**. With dual responsibility, every transformation
  stage needs two test paths per param (present vs. missing-and-randomized), and
  the "missing" path forces mocking/seeding the RNG or loose range assertions —
  that duplication multiplies across every stage touching a random param. With
  the split, transformation stages are pure functions of their inputs (exact
  output assertions, no randomness in the test), and the fill-in-if-missing logic
  is tested once in `RandomizeParamStage` and reused everywhere.
- Generic vs. typed randomize stage — RESOLVED as a hybrid: **one generic
  `RandomizeParam` stage, with a typed, registry-resolved `Generator`**.
  - `RandomizeParam` stays a single stage class (`key` + `generator` config).
  - The `generator` field names a type (e.g. `uniformDouble`, `choice`) resolved
    through the same registry pattern used for stage adapters (decision #3) —
    e.g. `UniformDoubleGenerator`, `ChoiceGenerator` implementing a `Generator`
    interface.
  - This gets the low stage-count of the generic approach plus the per-type
    config validation and clear error messages of the typed approach, reusing
    the existing registry mechanism instead of inventing a second pattern for
    the same problem.

## Sketch of the resulting shape

```java
public interface PipelineStage {
    Wrapper process(Wrapper input);
}

class RhythmicDensityStage implements PipelineStage {
    public Wrapper process(Wrapper w) {
        double density = w.getParam("density", Double.class); // throws if absent
        // ...transform using density...
    }
}

class RandomizeParamStage implements PipelineStage {
    private final String key;
    private final Supplier<Object> generator;

    public Wrapper process(Wrapper w) {
        if (!w.hasParam(key)) {
            w.setParam(key, generator.get());
        }
        return w;
    }
}
```

```yaml
name: "sparse-arpeggio"
stages:
  - type: RandomizeParam
    params: { key: "density", range: [0.2, 0.8] }
  - type: RhythmicDensity        # will always find "density" now
  - type: ScaleConstraint
    params: { scale: "dorian" }  # fixed, no randomization needed
  - type: include
    ref: "harmony-base.yaml"
```

## Not yet decided / next steps

All open questions have been resolved — see decisions #3, #5, #6 above.
