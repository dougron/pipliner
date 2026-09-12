package pipeliner.adapter;

/**
 * Stand-in for a third-party music library class with an API that doesn't
 * match this project's conventions (raw {@code int[]}, its own scale-name
 * spelling) — a test fixture demonstrating what an adapter absorbs
 * (decision #3, pipeline-architecture-notes.md). Lives in test scope: real
 * adapters for real external libraries belong in whatever project consumes
 * them, not in this generic core library.
 */
public class ExternalScaleQuantizer {

	private static final int[] DORIAN_INTERVALS = { 0, 2, 3, 5, 7, 9, 10 };

	public int[] quantizeToScale(int[] rawPitches, String scaleName) {
		if (!"dorian".equals(scaleName)) {
			throw new UnsupportedOperationException("Unsupported scale: " + scaleName);
		}
		int[] result = new int[rawPitches.length];
		for (int i = 0; i < rawPitches.length; i++) {
			result[i] = quantizeOne(rawPitches[i]);
		}
		return result;
	}

	private int quantizeOne(int pitch) {
		int octave = pitch / 12;
		int pitchClass = pitch % 12;
		int closest = DORIAN_INTERVALS[0];
		int smallestDiff = Integer.MAX_VALUE;
		for (int interval : DORIAN_INTERVALS) {
			int diff = Math.abs(interval - pitchClass);
			if (diff < smallestDiff) {
				smallestDiff = diff;
				closest = interval;
			}
		}
		return octave * 12 + closest;
	}

}
