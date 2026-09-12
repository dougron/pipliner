package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.PipelineStage;
import pipeliner.Wrapper;

class ConditionalStage_Tests {

	@Test
	void when_the_condition_is_true_then_the_wrapped_stages_run_in_order() {
		List<PipelineStage> childStages = Arrays.asList(new AppendStage("a"), new AppendStage("b"));
		ConditionalStage stage = new ConditionalStage(w -> true, childStages);
		Wrapper input = new MapWrapper();
		input.setParam("log", "");

		Wrapper result = stage.process(input);

		assertThat(result.getParam("log", String.class)).isEqualTo("ab");
	}


	@Test
	void when_the_condition_is_false_then_the_wrapper_passes_through_unchanged() {
		ConditionalStage stage = new ConditionalStage(w -> false, Collections.singletonList(new AppendStage("a")));
		Wrapper input = new MapWrapper();
		input.setParam("log", "");

		Wrapper result = stage.process(input);

		assertThat(result.getParam("log", String.class)).isEqualTo("");
	}


	@Test
	void when_the_condition_reads_a_wrapper_param_then_it_is_evaluated_against_the_input() {
		ConditionalStage stage = new ConditionalStage(
			w -> w.getParam("density", Double.class) > 0.5,
			Collections.singletonList(new AppendStage("a")));
		Wrapper input = new MapWrapper();
		input.setParam("log", "");
		input.setParam("density", 0.9);

		Wrapper result = stage.process(input);

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
