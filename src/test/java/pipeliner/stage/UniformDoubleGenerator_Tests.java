package pipeliner.stage;

import static org.assertj.core.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

class UniformDoubleGenerator_Tests {

	@Test
	void when_generate_is_called_then_the_result_is_within_the_configured_range() {
		UniformDoubleGenerator generator = new UniformDoubleGenerator(0.2, 0.8, new Random(42));

		for (int i = 0; i < 100; i++) {
			double value = (Double) generator.generate();
			assertThat(value).isBetween(0.2, 0.8);
		}
	}


	@Test
	void when_seeded_with_the_same_seed_then_generate_is_deterministic() {
		UniformDoubleGenerator a = new UniformDoubleGenerator(0.0, 1.0, new Random(7));
		UniformDoubleGenerator b = new UniformDoubleGenerator(0.0, 1.0, new Random(7));

		assertThat(a.generate()).isEqualTo(b.generate());
	}

}
