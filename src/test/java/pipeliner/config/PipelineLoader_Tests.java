package pipeliner.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;
import pipeliner.registry.Registry;

class PipelineLoader_Tests {

	@Test
	void when_specs_are_loaded_then_a_stage_is_created_per_spec_in_order() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("First", params -> new NamedStage("First"));
		registry.register("Second", params -> new NamedStage("Second"));
		PipelineLoader loader = new PipelineLoader(registry);

		List<PipelineStage> stages = loader.load(Arrays.asList(
			new StageSpec("First", Collections.emptyMap()),
			new StageSpec("Second", Collections.emptyMap())
		));

		assertThat(stages).hasSize(2);
		assertThat(((NamedStage) stages.get(0)).name).isEqualTo("First");
		assertThat(((NamedStage) stages.get(1)).name).isEqualTo("Second");
	}


	@Test
	void when_a_spec_has_params_then_they_are_passed_through_to_the_registered_factory() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("Configured", params -> new NamedStage(String.valueOf(params.get("label"))));
		PipelineLoader loader = new PipelineLoader(registry);

		List<PipelineStage> stages = loader.load(Collections.singletonList(
			new StageSpec("Configured", Collections.singletonMap("label", "dorian"))
		));

		assertThat(((NamedStage) stages.get(0)).name).isEqualTo("dorian");
	}


	@Test
	void when_a_spec_names_an_unregistered_type_then_loading_throws() {
		Registry<PipelineStage> registry = new Registry<>();
		PipelineLoader loader = new PipelineLoader(registry);

		assertThatThrownBy(() -> loader.load(Collections.singletonList(
				new StageSpec("Missing", Collections.emptyMap()))))
			.isInstanceOf(IllegalArgumentException.class);
	}


	@Test
	void when_an_include_spec_is_loaded_then_it_resolves_via_the_recipeResolver_and_wraps_as_a_SubPipelineStage() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("First", params -> new NamedStage("First"));
		CompositionRecipe harmonyBase = new CompositionRecipe("harmony-base",
			Collections.singletonList(new StageSpec("First", Collections.emptyMap())));
		PipelineLoader loader = new PipelineLoader(registry, ref -> harmonyBase);

		List<PipelineStage> stages = loader.load(Collections.singletonList(
			new StageSpec(PipelineLoader.INCLUDE_TYPE, Collections.singletonMap("ref", "harmony-base"))));

		assertThat(stages).hasSize(1);
		assertThat(stages.get(0)).isInstanceOf(SubPipelineStage.class);
		SubPipelineStage sub = (SubPipelineStage) stages.get(0);
		assertThat(sub.getName()).isEqualTo("harmony-base");
		assertThat(sub.getStages()).hasSize(1);
	}


	@Test
	void when_a_CompositionRecipe_is_loaded_via_the_recipe_overload_then_its_stages_are_loaded() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("First", params -> new NamedStage("First"));
		CompositionRecipe recipe = new CompositionRecipe("sparse-arpeggio",
			Collections.singletonList(new StageSpec("First", Collections.emptyMap())));
		PipelineLoader loader = new PipelineLoader(registry);

		List<PipelineStage> stages = loader.load(recipe);

		assertThat(stages).hasSize(1);
		assertThat(((NamedStage) stages.get(0)).name).isEqualTo("First");
	}


	@Test
	void when_no_recipeResolver_is_configured_and_an_include_is_encountered_then_it_throws() {
		Registry<PipelineStage> registry = new Registry<>();
		PipelineLoader loader = new PipelineLoader(registry);

		assertThatThrownBy(() -> loader.load(Collections.singletonList(
				new StageSpec(PipelineLoader.INCLUDE_TYPE, Collections.singletonMap("ref", "harmony-base")))))
			.isInstanceOf(IllegalStateException.class);
	}


	@Test
	void when_an_included_recipe_itself_includes_another_then_the_nesting_is_preserved() {
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("First", params -> new NamedStage("First"));
		CompositionRecipe innermost = new CompositionRecipe("innermost",
			Collections.singletonList(new StageSpec("First", Collections.emptyMap())));
		CompositionRecipe outer = new CompositionRecipe("outer",
			Collections.singletonList(new StageSpec(PipelineLoader.INCLUDE_TYPE, Collections.singletonMap("ref", "innermost"))));
		Map<String, CompositionRecipe> recipes = new HashMap<>();
		recipes.put("innermost", innermost);
		recipes.put("outer", outer);
		PipelineLoader loader = new PipelineLoader(registry, recipes::get);

		List<PipelineStage> stages = loader.load(outer);

		assertThat(stages).hasSize(1);
		SubPipelineStage sub = (SubPipelineStage) stages.get(0);
		assertThat(sub.getName()).isEqualTo("innermost");
		assertThat(sub.getStages()).hasSize(1);
	}


	private static class NamedStage implements PipelineStage {
		private final String name;

		NamedStage(String name) {
			this.name = name;
		}

		@Override
		public Wrapper process(Wrapper input) {
			return input;
		}
	}

}
