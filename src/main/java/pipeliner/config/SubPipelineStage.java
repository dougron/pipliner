package pipeliner.config;

import java.util.List;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;

/**
 * Wraps an included recipe's stages as a single, distinct, reusable unit
 * rather than splicing them into the parent stage list — decision #4
 * (pipeline-architecture-notes.md) resolved to wrap, not flatten, so the
 * sub-recipe keeps its identity for testing and debugging.
 */
public class SubPipelineStage implements PipelineStage {

	private final String name;
	private final List<PipelineStage> stages;

	public SubPipelineStage(String name, List<PipelineStage> stages) {
		this.name = name;
		this.stages = stages;
	}

	public String getName() {
		return name;
	}

	public List<PipelineStage> getStages() {
		return stages;
	}

	@Override
	public Wrapper process(Wrapper input) {
		Wrapper current = input;
		for (PipelineStage stage : stages) {
			current = stage.process(current);
		}
		return current;
	}

}
