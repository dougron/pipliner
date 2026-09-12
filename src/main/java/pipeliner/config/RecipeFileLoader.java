package pipeliner.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.yaml.snakeyaml.Yaml;

/**
 * Parses a recipe YAML document (see the sketch in pipeline-architecture-
 * notes.md) into a CompositionRecipe. This is the only class that knows the
 * on-disk YAML shape — everything downstream (PipelineLoader, Registry,
 * adapters) works with StageSpec/CompositionRecipe and never sees YAML,
 * keeping config-loading generic per decision #3.
 *
 * An "include" entry's "ref" sits as a sibling of "type" in the YAML (a file
 * path), not nested under "params". It's normalized into a params map with a
 * "ref" key here, so PipelineLoader's include handling (decision #4) stays
 * uniform with how every other stage type receives its params.
 */
public class RecipeFileLoader {

	private final Yaml yaml = new Yaml();

	public CompositionRecipe loadFile(Path path) {
		try {
			String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			return parse(content);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read recipe file: " + path, e);
		}
	}

	@SuppressWarnings("unchecked")
	public CompositionRecipe parse(String yamlContent) {
		Map<String, Object> raw = yaml.load(yamlContent);
		String name = (String) raw.get("name");
		List<Map<String, Object>> rawStages = (List<Map<String, Object>>) raw.getOrDefault("stages", Collections.emptyList());

		List<StageSpec> stages = new ArrayList<>();
		for (Map<String, Object> rawStage : rawStages) {
			stages.add(toStageSpec(rawStage));
		}
		return new CompositionRecipe(name, stages);
	}

	/**
	 * Resolves an include's "ref" relative to baseDir by loading and parsing
	 * that file — pass to PipelineLoader's recipeResolver constructor param.
	 */
	public Function<String, CompositionRecipe> fileResolver(Path baseDir) {
		return ref -> loadFile(baseDir.resolve(ref));
	}

	@SuppressWarnings("unchecked")
	private StageSpec toStageSpec(Map<String, Object> rawStage) {
		String type = (String) rawStage.get("type");
		if (PipelineLoader.INCLUDE_TYPE.equals(type)) {
			return new StageSpec(type, Collections.singletonMap("ref", rawStage.get("ref")));
		}
		Map<String, Object> params = (Map<String, Object>) rawStage.get("params");
		return new StageSpec(type, params);
	}

}
