package pipeliner.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A named, ordered list of StageSpecs. Loaded recipes are first-class objects
 * — they have identity, can be stored, versioned, compared, and reused — not
 * just parameters consumed and discarded (decision #2,
 * pipeline-architecture-notes.md). This is also the unit a "ref" in an
 * {@code include} StageSpec resolves to (decision #4).
 */
public class CompositionRecipe {

	private final String name;
	private final List<StageSpec> stages;

	public CompositionRecipe(String name, List<StageSpec> stages) {
		this.name = name;
		this.stages = Collections.unmodifiableList(stages);
	}

	public String getName() {
		return name;
	}

	public List<StageSpec> getStages() {
		return stages;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CompositionRecipe)) {
			return false;
		}
		CompositionRecipe other = (CompositionRecipe) o;
		return Objects.equals(name, other.name) && Objects.equals(stages, other.stages);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, stages);
	}

	@Override
	public String toString() {
		return "CompositionRecipe{name='" + name + "', stages=" + stages + "}";
	}

}
