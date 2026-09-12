package pipeliner.adapter;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.PipelineStage;
import pipeliner.Wrapper;
import pipeliner.config.PipelineLoader;
import pipeliner.config.StageSpec;
import pipeliner.registry.Registry;

/**
 * End-to-end demonstration of decision #3's wiring:
 * config-loader -> registry -> adapter -> external library.
 */
class ScaleConstraintStage_Tests {

	@Test
	void when_a_ScaleConstraint_spec_is_loaded_then_the_adapter_quantizes_pitches_via_the_external_library() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("ScaleConstraint", params ->
			new ScaleConstraintStage(new ExternalScaleQuantizer(), String.valueOf(params.get("scale"))));
		PipelineLoader loader = new PipelineLoader(registry);

		List<PipelineStage> stages = loader.load(Collections.singletonList(
			new StageSpec("ScaleConstraint", Collections.singletonMap("scale", "dorian"))));
		Wrapper wrapper = new MapWrapper();
		wrapper.setParam("pitches", new int[] { 61, 66 });

		Wrapper result = stages.get(0).process(wrapper);

		assertThat(result.getParam("pitches", int[].class)).containsExactly(60, 65);
	}

}
