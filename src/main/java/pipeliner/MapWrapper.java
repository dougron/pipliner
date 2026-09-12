package pipeliner;

import java.util.HashMap;
import java.util.Map;

public class MapWrapper implements Wrapper {

	private final Map<String, Object> params = new HashMap<>();

	@Override
	public <T> T getParam(String key, Class<T> type) {
		if (!params.containsKey(key)) {
			throw new IllegalStateException("Missing required param: " + key);
		}
		return type.cast(params.get(key));
	}

	@Override
	public void setParam(String key, Object value) {
		params.put(key, value);
	}

	@Override
	public boolean hasParam(String key) {
		return params.containsKey(key);
	}

}
