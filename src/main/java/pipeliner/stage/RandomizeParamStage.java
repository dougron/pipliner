package pipeliner.stage;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;

/**
 * Writes a param onto the wrapper only if it isn't already set, so a recipe
 * can still override the default by setting the value elsewhere. Placed
 * earlier in a recipe than the strict transformation stage(s) that need the
 * param — decision #6 (pipeline-architecture-notes.md).
 */
public class RandomizeParamStage implements PipelineStage {

	private final String key;
	private final Generator generator;

	public RandomizeParamStage(String key, Generator generator) {
		this.key = key;
		this.generator = generator;
	}

	@Override
	public Wrapper process(Wrapper input) {
		if (!input.hasParam(key)) {
			input.setParam(key, generator.generate());
		}
		return input;
	}

}
