package pipeliner.registry;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class Registry_Tests {

	@Test
	void when_a_type_is_registered_then_create_passes_the_params_to_the_factory() {
		Registry<String> registry = new Registry<>();
		registry.register("greeting", params -> "hello " + params.get("name"));

		String result = registry.create("greeting", Collections.singletonMap("name", "world"));

		assertThat(result).isEqualTo("hello world");
	}


	@Test
	void when_create_is_called_for_an_unregistered_type_then_an_exception_is_thrown() {
		Registry<String> registry = new Registry<>();

		assertThatThrownBy(() -> registry.create("missing", Collections.emptyMap()))
			.isInstanceOf(IllegalArgumentException.class);
	}


	@Test
	void when_the_same_type_name_is_registered_twice_then_an_exception_is_thrown() {
		Registry<String> registry = new Registry<>();
		registry.register("greeting", params -> "hello");

		assertThatThrownBy(() -> registry.register("greeting", params -> "hi"))
			.isInstanceOf(IllegalStateException.class);
	}


	@Test
	void when_a_type_is_registered_then_isRegistered_returns_true() {
		Registry<String> registry = new Registry<>();
		registry.register("greeting", params -> "hello");

		assertThat(registry.isRegistered("greeting")).isTrue();
	}


	@Test
	void when_a_type_is_not_registered_then_isRegistered_returns_false() {
		Registry<String> registry = new Registry<>();

		assertThat(registry.isRegistered("missing")).isFalse();
	}

}
