package pipeliner;

public interface PipelineStage {

	Wrapper process(Wrapper input);

}
