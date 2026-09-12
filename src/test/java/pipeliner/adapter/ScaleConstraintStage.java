package pipeliner.adapter;

import pipeliner.PipelineStage;
import pipeliner.Wrapper;

/**
 * Adapter absorbing ExternalScaleQuantizer's mismatched API (raw int[], its
 * own scale-name spelling) behind PipelineStage/Wrapper. This is the only
 * class that knows ExternalScaleQuantizer exists (decision #3).
 */
public class ScaleConstraintStage implements PipelineStage {

	private final ExternalScaleQuantizer quantizer;
	private final String scaleName;

	public ScaleConstraintStage(ExternalScaleQuantizer quantizer, String scaleName) {
		this.quantizer = quantizer;
		this.scaleName = scaleName;
	}

	@Override
	public Wrapper process(Wrapper input) {
		int[] pitches = input.getParam("pitches", int[].class);
		input.setParam("pitches", quantizer.quantizeToScale(pitches, scaleName));
		return input;
	}

}
