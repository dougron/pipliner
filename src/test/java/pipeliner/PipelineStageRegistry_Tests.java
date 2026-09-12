package pipeliner;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import pipeliner.registry.Registry;

class PipelineStageRegistry_Tests {

	@Test
	void when_a_stage_type_is_registered_then_the_registry_creates_stage_instances_by_type_name() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("PassThrough", params -> new PassThroughStage());

		PipelineStage stage = registry.create("PassThrough", Collections.emptyMap());

		assertThat(stage).isInstanceOf(PassThroughStage.class);
	}


	@Test
	void when_a_registered_stage_processes_a_wrapper_then_it_returns_the_transformed_wrapper() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("PassThrough", params -> new PassThroughStage());
		Wrapper input = new MapWrapper();

		Wrapper result = registry.create("PassThrough", Collections.emptyMap()).process(input);

		assertThat(result).isSameAs(input);
	}


	private static class PassThroughStage implements PipelineStage {
		@Override
		public Wrapper process(Wrapper input) {
			return input;
		}
	}

}
