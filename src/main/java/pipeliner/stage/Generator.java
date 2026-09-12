package pipeliner.stage;

/**
 * Produces a value to fill in a missing param. Typed implementations (e.g.
 * UniformDoubleGenerator, ChoiceGenerator) are resolved by type name through
 * the same Registry mechanism used for stage adapters, rather than
 * RandomizeParamStage having per-type branching baked into it — decision #6
 * (pipeline-architecture-notes.md).
 */
public interface Generator {

	Object generate();

}
