package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ChoiceGenerator_Tests {

	@Test
	void when_generate_is_called_then_the_result_is_one_of_the_configured_choices() {
		List<Object> choices = Arrays.asList("dorian", "phrygian", "lydian");
		ChoiceGenerator generator = new ChoiceGenerator(choices, new Random(1));

		for (int i = 0; i < 20; i++) {
			assertThat(generator.generate()).isIn(choices);
		}
	}

}
