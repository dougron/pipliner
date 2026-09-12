package pipeliner.config;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;
import pipeliner.registry.Registry;

class RecipeFileLoader_Tests {

	@Test
	void when_a_recipe_yaml_string_is_parsed_then_name_and_stages_are_mapped() {
		String yamlText = String.join("\n",
			"name: \"sparse-arpeggio\"",
			"stages:",
			"  - type: RandomizeParam",
			"    params:",
			"      key: \"density\"",
			"      range: [0.2, 0.8]",
			"  - type: RhythmicDensity",
			"  - type: ScaleConstraint",
			"    params:",
			"      scale: \"dorian\"");

		CompositionRecipe recipe = new RecipeFileLoader().parse(yamlText);

		assertThat(recipe.getName()).isEqualTo("sparse-arpeggio");
		assertThat(recipe.getStages()).hasSize(3);
		assertThat(recipe.getStages().get(0).getType()).isEqualTo("RandomizeParam");
		assertThat(recipe.getStages().get(0).getParams()).containsEntry("key", "density");
		assertThat(recipe.getStages().get(1).getType()).isEqualTo("RhythmicDensity");
		assertThat(recipe.getStages().get(1).getParams()).isEmpty();
		assertThat(recipe.getStages().get(2).getParams()).containsEntry("scale", "dorian");
	}


	@Test
	void when_an_include_stage_has_a_sibling_ref_then_it_is_normalized_into_params() {
		String yamlText = String.join("\n",
			"name: \"sparse-arpeggio\"",
			"stages:",
			"  - type: include",
			"    ref: \"harmony-base.yaml\"");

		CompositionRecipe recipe = new RecipeFileLoader().parse(yamlText);

		StageSpec includeSpec = recipe.getStages().get(0);
		assertThat(includeSpec.getType()).isEqualTo(PipelineLoader.INCLUDE_TYPE);
		assertThat(includeSpec.getParams()).containsEntry("ref", "harmony-base.yaml");
	}


	@Test
	void when_a_recipe_is_loaded_from_a_file_then_its_content_is_parsed(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("sparse-arpeggio.yaml");
		Files.write(file, Arrays.asList("name: \"sparse-arpeggio\"", "stages:", "  - type: RhythmicDensity"));

		CompositionRecipe recipe = new RecipeFileLoader().loadFile(file);

		assertThat(recipe.getName()).isEqualTo("sparse-arpeggio");
		assertThat(recipe.getStages()).hasSize(1);
	}


	@Test
	void when_fileResolver_is_used_with_PipelineLoader_then_an_include_resolves_and_wraps_the_referenced_file(
			@TempDir Path tempDir) throws IOException {
		Files.write(tempDir.resolve("harmony-base.yaml"), Arrays.asList(
			"name: \"harmony-base\"",
			"stages:",
			"  - type: ChordStack"));
		Files.write(tempDir.resolve("sparse-arpeggio.yaml"), Arrays.asList(
			"name: \"sparse-arpeggio\"",
			"stages:",
			"  - type: RhythmicDensity",
			"  - type: include",
			"    ref: \"harmony-base.yaml\""));

		RecipeFileLoader recipeFileLoader = new RecipeFileLoader();
		Registry<PipelineStage> registry = new Registry<>();
		registry.register("RhythmicDensity", params -> new NoopStage());
		registry.register("ChordStack", params -> new NoopStage());
		PipelineLoader loader = new PipelineLoader(registry, recipeFileLoader.fileResolver(tempDir));

		CompositionRecipe recipe = recipeFileLoader.loadFile(tempDir.resolve("sparse-arpeggio.yaml"));
		List<PipelineStage> stages = loader.load(recipe);

		assertThat(stages).hasSize(2);
		assertThat(stages.get(1)).isInstanceOf(SubPipelineStage.class);
		SubPipelineStage sub = (SubPipelineStage) stages.get(1);
		assertThat(sub.getName()).isEqualTo("harmony-base");
		assertThat(sub.getStages()).hasSize(1);
	}


	private static class NoopStage implements PipelineStage {
		@Override
		public Wrapper process(Wrapper input) {
			return input;
		}
	}

}
