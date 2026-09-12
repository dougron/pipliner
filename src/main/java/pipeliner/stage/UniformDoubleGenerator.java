package pipeliner.stage;

import java.util.Random;

public class UniformDoubleGenerator implements Generator {

	private final double min;
	private final double max;
	private final Random random;

	public UniformDoubleGenerator(double min, double max, Random random) {
		this.min = min;
		this.max = max;
		this.random = random;
	}

	@Override
	public Object generate() {
		return min + random.nextDouble() * (max - min);
	}

}
