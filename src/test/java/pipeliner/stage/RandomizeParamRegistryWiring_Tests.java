package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.PipelineStage;
import pipeliner.Wrapper;
import pipeliner.config.PipelineLoader;
import pipeliner.config.StageSpec;
import pipeliner.registry.Registry;

/**
 * End-to-end demonstration of decision #6's resolution: one generic
 * RandomizeParam stage, with a typed, registry-resolved Generator.
 */
class RandomizeParamRegistryWiring_Tests {

	@Test
	void when_a_RandomizeParam_spec_names_a_registered_generator_type_then_it_fills_in_the_param() {
		Registry<Generator> generatorRegistry = new Registry<>();
		generatorRegistry.register("uniformDouble", params ->
			new UniformDoubleGenerator((Double) params.get("min"), (Double) params.get("max"), new Random(42)));

		Registry<PipelineStage> stageRegistry = new Registry<>();
		stageRegistry.register("RandomizeParam", params -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> generatorParams = (Map<String, Object>) params.get("generatorParams");
			Generator generator = generatorRegistry.create((String) params.get("generator"), generatorParams);
			return new RandomizeParamStage((String) params.get("key"), generator);
		});
		PipelineLoader loader = new PipelineLoader(stageRegistry);

		Map<String, Object> generatorParams = new HashMap<>();
		generatorParams.put("min", 0.2);
		generatorParams.put("max", 0.8);
		Map<String, Object> stageParams = new HashMap<>();
		stageParams.put("key", "density");
		stageParams.put("generator", "uniformDouble");
		stageParams.put("generatorParams", generatorParams);

		List<PipelineStage> stages = loader.load(Collections.singletonList(new StageSpec("RandomizeParam", stageParams)));
		Wrapper wrapper = new MapWrapper();

		Wrapper result = stages.get(0).process(wrapper);

		assertThat(result.getParam("density", Double.class)).isBetween(0.2, 0.8);
	}

}
