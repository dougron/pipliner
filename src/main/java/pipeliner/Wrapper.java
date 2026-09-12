package pipeliner;

public interface Wrapper {

	<T> T getParam(String key, Class<T> type);

	void setParam(String key, Object value);

	boolean hasParam(String key);

}
