package pipeliner.stage;

import java.util.List;
import java.util.function.Predicate;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;

/**
 * Wraps a predicate plus the stage(s) to run if it holds; passes the wrapper
 * through unchanged if it doesn't. The predicate is a plain Java class, not a
 * config-level condition grammar — see decision #5 (pipeline-architecture-
 * notes.md). It operates on the same Wrapper stages consume, since Wrapper
 * already is the pipeline's context/output-so-far.
 */
public class ConditionalStage implements PipelineStage {

	private final Predicate<Wrapper> condition;
	private final List<PipelineStage> stages;

	public ConditionalStage(Predicate<Wrapper> condition, List<PipelineStage> stages) {
		this.condition = condition;
		this.stages = stages;
	}

	@Override
	public Wrapper process(Wrapper input) {
		if (!condition.test(input)) {
			return input;
		}
		Wrapper current = input;
		for (PipelineStage stage : stages) {
			current = stage.process(current);
		}
		return current;
	}

}
