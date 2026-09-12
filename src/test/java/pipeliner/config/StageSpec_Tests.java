package pipeliner.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StageSpec_Tests {

	@Test
	void when_a_StageSpec_is_created_with_params_then_getType_and_getParams_return_them() {
		Map<String, Object> params = Collections.singletonMap("scale", "dorian");
		StageSpec spec = new StageSpec("ScaleConstraint", params);

		assertThat(spec.getType()).isEqualTo("ScaleConstraint");
		assertThat(spec.getParams()).isEqualTo(params);
	}


	@Test
	void when_a_StageSpec_is_created_with_null_params_then_getParams_returns_an_empty_map() {
		StageSpec spec = new StageSpec("Noop", null);

		assertThat(spec.getParams()).isEmpty();
	}


	@Test
	void when_two_StageSpecs_have_the_same_type_and_params_then_they_are_equal() {
		StageSpec a = new StageSpec("ScaleConstraint", Collections.singletonMap("scale", "dorian"));
		StageSpec b = new StageSpec("ScaleConstraint", Collections.singletonMap("scale", "dorian"));

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}


	@Test
	void when_two_StageSpecs_have_different_params_then_they_are_not_equal() {
		StageSpec a = new StageSpec("ScaleConstraint", Collections.singletonMap("scale", "dorian"));
		StageSpec b = new StageSpec("ScaleConstraint", Collections.singletonMap("scale", "phrygian"));

		assertThat(a).isNotEqualTo(b);
	}

}
