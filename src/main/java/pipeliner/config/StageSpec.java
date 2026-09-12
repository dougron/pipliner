package pipeliner.config;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * A single stage entry from a recipe: a type name plus its fixed, recipe-level
 * params (e.g. {@code scale: "dorian"}). This is deliberately the only concept
 * the config-loading code knows about — never music or the external libraries
 * an adapter wraps (decision #3, pipeline-architecture-notes.md).
 */
public class StageSpec {

	private final String type;
	private final Map<String, Object> params;

	public StageSpec(String type, Map<String, Object> params) {
		this.type = type;
		this.params = params == null ? Collections.emptyMap() : params;
	}

	public String getType() {
		return type;
	}

	public Map<String, Object> getParams() {
		return params;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StageSpec)) {
			return false;
		}
		StageSpec other = (StageSpec) o;
		return Objects.equals(type, other.type) && Objects.equals(params, other.params);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, params);
	}

	@Override
	public String toString() {
		return "StageSpec{type='" + type + "', params=" + params + "}";
	}

}
