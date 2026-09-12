package pipeliner.config;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class CompositionRecipe_Tests {

	@Test
	void when_a_recipe_is_created_then_getName_and_getStages_return_them() {
		List<StageSpec> stages = Collections.singletonList(new StageSpec("Noop", null));
		CompositionRecipe recipe = new CompositionRecipe("sparse-arpeggio", stages);

		assertThat(recipe.getName()).isEqualTo("sparse-arpeggio");
		assertThat(recipe.getStages()).isEqualTo(stages);
	}


	@Test
	void when_two_recipes_have_the_same_name_and_stages_then_they_are_equal() {
		List<StageSpec> stages = Collections.singletonList(new StageSpec("Noop", null));

		CompositionRecipe a = new CompositionRecipe("sparse-arpeggio", stages);
		CompositionRecipe b = new CompositionRecipe("sparse-arpeggio", stages);

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}


	@Test
	void when_two_recipes_have_different_stages_then_they_are_not_equal() {
		CompositionRecipe a = new CompositionRecipe("r", Collections.singletonList(new StageSpec("A", null)));
		CompositionRecipe b = new CompositionRecipe("r", Collections.singletonList(new StageSpec("B", null)));

		assertThat(a).isNotEqualTo(b);
	}


	@Test
	void when_a_recipe_is_created_then_its_stage_list_is_unmodifiable() {
		CompositionRecipe recipe = new CompositionRecipe("r", new ArrayList<>());

		assertThatThrownBy(() -> recipe.getStages().add(new StageSpec("A", null)))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
