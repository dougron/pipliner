package pipeliner;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MapWrapper_Tests {

	@Test
	void when_a_param_is_set_then_hasParam_returns_true() {
		MapWrapper w = new MapWrapper();
		w.setParam("density", 0.5);

		assertThat(w.hasParam("density")).isTrue();
	}


	@Test
	void when_a_param_is_not_set_then_hasParam_returns_false() {
		MapWrapper w = new MapWrapper();

		assertThat(w.hasParam("density")).isFalse();
	}


	@Test
	void when_a_param_is_set_then_getParam_returns_it_cast_to_the_requested_type() {
		MapWrapper w = new MapWrapper();
		w.setParam("density", 0.5);

		assertThat(w.getParam("density", Double.class)).isEqualTo(0.5);
	}


	@Test
	void when_a_param_is_missing_then_getParam_throws() {
		MapWrapper w = new MapWrapper();

		assertThatThrownBy(() -> w.getParam("density", Double.class))
			.isInstanceOf(IllegalStateException.class);
	}

}
