package pipeliner.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.PipelineStage;
import pipeliner.Wrapper;

class SubPipelineStage_Tests {

	@Test
	void when_processed_then_the_wrapped_stages_run_in_order() {
		List<PipelineStage> childStages = Arrays.asList(new AppendStage("a"), new AppendStage("b"));
		SubPipelineStage sub = new SubPipelineStage("harmony-base", childStages);
		Wrapper input = new MapWrapper();
		input.setParam("log", "");

		Wrapper result = sub.process(input);

		assertThat(result.getParam("log", String.class)).isEqualTo("ab");
	}


	@Test
	void when_created_then_getName_and_getStages_return_them() {
		List<PipelineStage> childStages = Arrays.asList(new AppendStage("a"));
		SubPipelineStage sub = new SubPipelineStage("harmony-base", childStages);

		assertThat(sub.getName()).isEqualTo("harmony-base");
		assertThat(sub.getStages()).isEqualTo(childStages);
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
