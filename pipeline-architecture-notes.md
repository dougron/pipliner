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
- **Partly superseded by #7 (2026-10-11):** strict transformation stages and
  typed, registry-resolved `Generator`s stay. What changes is *where* the
  fill-in happens: the runner does it from plugin-declared options, instead of
  recipes listing an explicit `RandomizeParam` stage per param.

## Revision 2026-10-11

Prompted by a clearer picture of how the library is used:

1. The YAML defines the processing steps and any preset values. A user can
   preset any setting; anything not preset is generated randomly.
2. Plugins (stages) carry a default range of values to choose from (an
   `options` field).
3. The user passes in one argument object holding the YAML config, optionally
   a Mu object, and metadata (e.g. analyses — still loosely defined).
4. Third-party plugins must be registrable. The user manages them; in practice
   they're likely plugins heading for inclusion in this project.
5. Plugins update the Mu and the config, and may add metadata.
6. A stage's valid options can depend on what earlier stages produced — e.g.
   embellishment pattern indices are limited by the number of structure tones,
   which is not itself a param.
7. One way to make variations is to edit values in an existing (resolved)
   config and run it again. Downstream generated values should survive those
   edits where they can — e.g. extended when the structure-tone count grows —
   rather than having to be deleted and regenerated from scratch.

Decisions #7–#13 follow from these.

### 7. Plugins declare their params and default options; the runner resolves them
- Each plugin declares its params: name, type, and default `options` (the
  range or set it picks from when nothing is preset).
- Before calling a plugin, the **runner** resolves each declared param, in this
  order of precedence:
  1. a value pinned in the YAML (`params`) for that stage entry,
  2. otherwise, a previously generated value (`generated`), kept or adapted
     per #13,
  3. otherwise, a pick from an `options` override in the YAML for that entry,
  4. otherwise, a pick from the plugin's default options.
- Options are worked out from the run so far, not fixed — see #12.
- The plugin receives fully resolved params, so it stays strict and
  deterministic — the testability argument from #6 still holds. Fill-in logic
  is written and tested once, in the runner.
- Options are expressed with the existing `Generator` types (e.g.
  `uniformDouble`, `choice`) via the generator registry, so #6's typed
  generators are reused rather than replaced.
- Recipes no longer need a `RandomizeParam` stage per param; "any setting can
  be preset" falls out for free. `RandomizeParamStage` can stay for one-off
  cases but is no longer the main mechanism.

### 8. One argument object (`RunContext`) replaces `Wrapper` as what flows through the pipeline
- `RunContext` holds: the config (`CompositionRecipe`), the Mu (optional on
  input), and a metadata map (analyses etc.).
- This splits what `Wrapper` mixed together: settings live in the config,
  musical content lives in Mu, everything else goes in metadata.
- A single entry point — `Pipeline.run(RunContext, registries)` — replaces
  callers loading a `List<PipelineStage>` and calling `process()` by hand.
- Mu is the companion libraries' musical object. **The core library refers
  to it only through a type parameter** (`RunContext<M>`, `Plugin<M>`), so it
  never depends on the music libraries (consistent with #3). Real plugins use
  `RunContext<Mu>`; core tests use a small fake Mu type.
- **`Wrapper`, `MapWrapper` and `PipelineStage.process(Wrapper)` are replaced
  outright**, not kept alongside. Nothing real depends on them yet — only the
  built-in stages and the test adapter, which move to `Plugin`/`RunContext`.
- **Java target: JDK 25 (current LTS)**, up from 1.8 (2026-10-11). Chosen
  over JDK 27 (newest, but non-LTS and superseded March 2027).
- `ConditionalStage` predicates (#5) move from `Predicate<Wrapper>` to
  `Predicate<RunContext>`, same reasoning: the context *is* the state.

### 9. A run returns a resolved config; the input config is never mutated
- The input `CompositionRecipe` stays immutable (consistent with #2). A run
  returns a **new, resolved recipe** with every param filled in.
- The **runner** records resolved values into that output; plugins don't edit
  the config themselves. Plugins do write to Mu and metadata directly.
- The output keeps **where each value came from** (amended by #13): values
  the user pinned stay under `params`; values the runner picked go under
  `generated`. A flat set of resolved values would lose that distinction.
- Payoff: every random run can be replayed exactly from its output YAML.
  Storing resolved values (rather than just an RNG seed) survives code
  changes to generators and plugin ordering.

### 10. Serialization rules
- **Config values are plain YAML types only** (numbers, strings, booleans,
  lists, maps). Musical objects (`int[]` pitches, enums, Mu fragments) belong
  in Mu, never in the config, so the config always round-trips.
- **Params are namespaced per stage entry**, not global keys, so two plugins
  with the same param name (e.g. `density`) can't collide.
- **Dump support:** add the reverse of `RecipeFileLoader` (`CompositionRecipe`
  → YAML). The `include` entry's `ref` must be written back as a sibling of
  `type`, undoing the normalization into `params`, so files keep their shape.

### 11. Third-party plugin registration
- Keeps #3's manual registration: the user registers third-party plugins on
  the same `Registry` instances, passed into `Pipeline.run`.
- The library provides a default registry pre-populated with built-in
  plugins and generators, which the user extends. (Today that wiring only
  exists in tests.)
- Duplicate registration keeps throwing, so a third-party plugin can't
  silently shadow a built-in one.

### 12. Options are worked out from the run so far
- `Plugin.params(RunContext ctx)` takes the context. The runner calls it just
  before the stage runs, so Mu already holds everything earlier stages
  produced — e.g. the embellishment plugin limits pattern indices to
  `0..structureToneCount-1`.
- `params(ctx)` is a plain function of the context, unit-testable with Mu
  fixtures ("one structure tone → options are `[0]` only"). `process` still
  receives fully resolved values and stays strict (#6's testability argument
  holds).
- **Options are derived from Mu (and metadata), not from other stages'
  config.** Params are namespaced per stage entry (#10), so reading another
  stage's param would couple recipes by stage type and position. Mu is the
  actual result; config is a record of the choices that produced it. It's
  also the only place to find derived facts that were never params (e.g.
  structure-tone count). This applies even where the config would work, e.g.
  time signature.
- A `ParamSpec` carries a **validity check derived from the context**, not
  just a generator.
- Params within one stage resolve in declared order; a later param's options
  can see earlier resolved params of the same stage.

### 13. Pinned vs. generated values; generated values are adapted, not discarded
- **Pinned** (`params`, user-authored): used as-is. What happens when the
  context makes a pinned value invalid is **undecided** (see open questions).
  Provisional default until then: the run fails, naming the stage, param and
  reason. The same applies to a pick from a YAML `options` override.
- **Generated** (`generated`, the runner's earlier choice): a starting point.
  Kept if still valid; otherwise **adapted** via the `ParamSpec`'s adapt step.
- `ParamSpec` has an optional **adapt step**: `(oldValue, ctx, generator) →
  closest valid value`.
  - Default when absent: keep if valid, otherwise regenerate fully — plugins
    with nothing clever to do are still correct.
  - List-shaped params (e.g. embellishment pattern indices): keep existing
    entries, generate only the new ones when the upstream count grows; drop
    out-of-range entries when it shrinks.
- User controls:
  - **Delete a generated value** → rolled again from scratch.
  - **Move a value from `generated` to `params`** → kept no matter what.
- The runner records each adaptation in metadata (e.g.
  `Embellishment.patternIndices: extended 3 → 5`), so it's visible which
  parts of a variation changed because of an edit vs. were rolled fresh.
- **Known limitation — positional values:** keep-front-and-extend assumes new
  structure tones are appended. If an edit inserts a tone mid-sequence,
  later embellishments shift by one tone (still valid music, just
  unexpected). Accepted; stable Mu item IDs were considered as a fix and
  deliberately set aside — not a design constraint on adapt steps.

## Revised sketch (post-#7–#13)

```java
public interface Plugin {
    List<ParamSpec> params(RunContext ctx);   // options/validity may depend on Mu so far (#12)
    void process(RunContext ctx, Map<String, Object> resolved);
}

class EmbellishmentPlugin implements Plugin {
    public List<ParamSpec> params(RunContext ctx) {
        int toneCount = ctx.mu.structureTones().size();
        return List.of(ParamSpec.of("patternIndices", Generators.indexList(0, toneCount - 1))
            .adapt((old, c, gen) -> extendOrTrim(old, toneCount, gen)));   // #13
    }
    // ...
}

public class RunContext {
    CompositionRecipe config;   // input: as authored; output: fully resolved
    Mu mu;                      // optional on input
    Map<String, Object> metadata;
}

// RunContext out = Pipeline.run(in, registries);
```

Input recipe (option syntax illustrative, not final):

```yaml
name: "sparse-arpeggio"
stages:
  - type: RhythmicDensity       # density not preset → plugin's default options
  - type: ScaleConstraint
    params: { scale: "dorian" } # preset, used as-is
  - type: Swing
    options:                    # override the plugin's default range
      amount: { generator: uniformDouble, min: 0.1, max: 0.3 }
```

Resolved output (replayable; pinned vs. generated kept apart per #13):

```yaml
name: "sparse-arpeggio"
stages:
  - type: RhythmicDensity
    generated: { density: 0.47 }
  - type: ScaleConstraint
    params: { scale: "dorian" }
  - type: Swing
    options:
      amount: { generator: uniformDouble, min: 0.1, max: 0.3 }
    generated: { amount: 0.22 }
  - type: Embellishment
    generated: { patternIndices: [2, 0, 1] }  # adapted, not rerolled, if tone count changes
```

## Original sketch (pre-#7, kept for history)

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

Decisions #3–#6 are resolved. Open after the 2026-10-11 revision:

- **Includes in resolved output (#4 vs #9):** values randomized inside an
  included sub-recipe can't be written back into the shared file. Choose
  between inlining the sub-recipe in the resolved output, or keeping the
  `ref` plus a per-include overrides block. Overrides keep the sub-recipe's
  identity (#4); inlining is simpler to replay.
- **Nested stages in resolved output:** the include question above is part of
  a broader one — `ConditionalStage` also nests stages, so the resolved
  config is a tree and nested generated values need a home. First
  implementation supports flat recipes only.
- **Metadata shape:** a free-form map for now; revisit once real analyses
  exist.
- **`options` YAML syntax:** the example above is a placeholder.
- **Editing a generated value in place:** does hand-editing a value under
  `generated` pin it, or does it stay a starting point that may be adapted?
  Currently implied: it stays generated; moving it to `params` is the way to
  pin.
- **Invalid pinned values (#13):** fail, clamp/adapt, or a per-param policy.
  Deferred until the behavior can be observed in real runs; failing is only
  a provisional default.
