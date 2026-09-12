package pipeliner.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import pipeliner.PipelineStage;
import pipeliner.registry.Registry;

/**
 * Turns a recipe's ordered StageSpec list into PipelineStage instances via the
 * registry. Stays fully generic: knows only StageSpec, CompositionRecipe,
 * PipelineStage, and Registry — never music or the external libraries an
 * adapter wraps (decision #3, pipeline-architecture-notes.md). Dependency
 * direction is config-loader -> registry -> adapters -> external libraries.
 *
 * Recognizes the reserved "include" type specially (decision #4): its "ref"
 * param names another CompositionRecipe, resolved via the recipeResolver and
 * wrapped (not flattened) as a SubPipelineStage.
 */
public class PipelineLoader {

	public static final String INCLUDE_TYPE = "include";

	private final Registry<PipelineStage> registry;
	private final Function<String, CompositionRecipe> recipeResolver;

	public PipelineLoader(Registry<PipelineStage> registry) {
		this(registry, ref -> {
			throw new IllegalStateException("No recipe resolver configured; cannot resolve include: " + ref);
		});
	}

	public PipelineLoader(Registry<PipelineStage> registry, Function<String, CompositionRecipe> recipeResolver) {
		this.registry = registry;
		this.recipeResolver = recipeResolver;
	}

	public List<PipelineStage> load(CompositionRecipe recipe) {
		return load(recipe.getStages());
	}

	public List<PipelineStage> load(List<StageSpec> specs) {
		List<PipelineStage> stages = new ArrayList<>();
		for (StageSpec spec : specs) {
			stages.add(INCLUDE_TYPE.equals(spec.getType()) ? loadInclude(spec) : registry.create(spec.getType(), spec.getParams()));
		}
		return stages;
	}

	private PipelineStage loadInclude(StageSpec spec) {
		String ref = String.valueOf(spec.getParams().get("ref"));
		CompositionRecipe included = recipeResolver.apply(ref);
		return new SubPipelineStage(included.getName(), load(included));
	}

}
