package pipeliner.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps config type-names to factories of instances, each factory taking the
 * StageSpec's params so a single registered type can produce differently
 * configured instances per recipe entry. One instance of this class backs the
 * stage-adapter registry (decision #3); generator and condition registries
 * (decisions #5, #6) reuse the same class rather than duplicating it.
 */
public class Registry<T> {

	private final Map<String, Function<Map<String, Object>, T>> factories = new HashMap<>();

	public void register(String typeName, Function<Map<String, Object>, T> factory) {
		if (factories.containsKey(typeName)) {
			throw new IllegalStateException("Type already registered: " + typeName);
		}
		factories.put(typeName, factory);
	}

	public T create(String typeName, Map<String, Object> params) {
		Function<Map<String, Object>, T> factory = factories.get(typeName);
		if (factory == null) {
			throw new IllegalArgumentException("Unknown type: " + typeName);
		}
		return factory.apply(params);
	}

	public boolean isRegistered(String typeName) {
		return factories.containsKey(typeName);
	}

}
