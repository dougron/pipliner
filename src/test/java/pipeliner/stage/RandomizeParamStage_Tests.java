package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import pipeliner.MapWrapper;
import pipeliner.Wrapper;

class RandomizeParamStage_Tests {

	@Test
	void when_the_param_is_missing_then_the_generator_fills_it_in() {
		RandomizeParamStage stage = new RandomizeParamStage("density", () -> 0.42);
		Wrapper input = new MapWrapper();

		Wrapper result = stage.process(input);

		assertThat(result.getParam("density", Double.class)).isEqualTo(0.42);
	}


	@Test
	void when_the_param_is_already_set_then_the_generator_is_not_used_and_the_value_is_unchanged() {
		RandomizeParamStage stage = new RandomizeParamStage("density", () -> {
			throw new AssertionError("generator should not be called when the param is already set");
		});
		Wrapper input = new MapWrapper();
		input.setParam("density", 0.7);

		Wrapper result = stage.process(input);

		assertThat(result.getParam("density", Double.class)).isEqualTo(0.7);
	}

}
