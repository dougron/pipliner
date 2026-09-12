package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.PipelineStage;
import pipeliner.Wrapper;
import pipeliner.config.PipelineLoader;
import pipeliner.config.StageSpec;
import pipeliner.registry.Registry;

/**
 * End-to-end demonstration of decision #5's resolution: the condition is
 * registry-resolved by type name, the same mechanism used for generators and
 * stage adapters.
 */
class ConditionalStageRegistryWiring_Tests {

	@Test
	void when_a_Conditional_spec_names_a_registered_condition_type_then_it_gates_its_child_stages() {
		Registry<PipelineStage> stageRegistry = new Registry<>();
		PipelineLoader loader = new PipelineLoader(stageRegistry);

		Registry<Predicate<Wrapper>> conditionRegistry = new Registry<>();
		conditionRegistry.register("isDense", params ->
			w -> w.getParam("density", Double.class) > (Double) params.get("threshold"));

		stageRegistry.register("Append", params -> new AppendStage((String) params.get("suffix")));
		stageRegistry.register("Conditional", params -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> conditionSpec = (Map<String, Object>) params.get("condition");
			@SuppressWarnings("unchecked")
			Map<String, Object> conditionParams = (Map<String, Object>) conditionSpec.get("params");
			Predicate<Wrapper> condition = conditionRegistry.create((String) conditionSpec.get("type"), conditionParams);
			@SuppressWarnings("unchecked")
			List<StageSpec> childSpecs = (List<StageSpec>) params.get("stages");
			return new ConditionalStage(condition, loader.load(childSpecs));
		});

		Map<String, Object> conditionParams = new HashMap<>();
		conditionParams.put("threshold", 0.5);
		Map<String, Object> conditionSpec = new HashMap<>();
		conditionSpec.put("type", "isDense");
		conditionSpec.put("params", conditionParams);

		Map<String, Object> appendParams = new HashMap<>();
		appendParams.put("suffix", "a");
		Map<String, Object> conditionalParams = new HashMap<>();
		conditionalParams.put("condition", conditionSpec);
		conditionalParams.put("stages", Collections.singletonList(new StageSpec("Append", appendParams)));

		List<PipelineStage> stages = loader.load(Collections.singletonList(new StageSpec("Conditional", conditionalParams)));
		Wrapper wrapper = new MapWrapper();
		wrapper.setParam("log", "");
		wrapper.setParam("density", 0.9);

		Wrapper result = stages.get(0).process(wrapper);

		assertThat(result.getParam("log", String.class)).isEqualTo("a");
	}


	private static class AppendStage implements PipelineStage {
		private final String suffix;

		AppendStage(String suffix) {
			this.suffix = suffix;
		}

		@Override
		public Wrapper process(Wrapper input) {
			input.setParam("log", input.getParam("log", String.class) + suffix);
			return input;
		}
	}

}
