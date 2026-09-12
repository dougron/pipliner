package pipeliner.stage;

import java.util.List;
import java.util.Random;

public class ChoiceGenerator implements Generator {

	private final List<Object> choices;
	private final Random random;

	public ChoiceGenerator(List<Object> choices, Random random) {
		this.choices = choices;
		this.random = random;
	}

	@Override
	public Object generate() {
		return choices.get(random.nextInt(choices.size()));
	}

}
