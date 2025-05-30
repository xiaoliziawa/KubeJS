package dev.latvian.mods.kubejs.script;

import dev.latvian.mods.kubejs.CommonProperties;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.StringUtilsWrapper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ScriptFile implements Comparable<ScriptFile> {
	private static final Pattern PROPERTY_PATTERN = Pattern.compile("^(\\w+)\\s*[:=]?\\s*(-?\\w+)$");

	public final ScriptPack pack;
	public final ScriptFileInfo info;

	private final Map<String, List<String>> properties;
	private int priority;
	private boolean ignored;
	private String packMode;
	private final Set<String> requiredMods;
	private boolean requiredClient;
	public String[] lines;
	public long lastModified;

	public ScriptFile(ScriptPack pack, ScriptFileInfo info) throws Exception {
		this.pack = pack;
		this.info = info;

		this.properties = new HashMap<>();
		this.priority = 0;
		this.ignored = false;
		this.packMode = "";
		this.requiredMods = new HashSet<>(0);
		this.requiredClient = false;

		this.lines = Files.readAllLines(info.path).toArray(StringUtilsWrapper.EMPTY_STRING_ARRAY);

		try {
			this.lastModified = Files.getLastModifiedTime(this.info.path).toMillis();
		} catch (Exception ex) {
			this.lastModified = 0L;
		}

		for (int i = 0; i < lines.length; i++) {
			var tline = lines[i].trim();

			if (tline.isEmpty() || tline.startsWith("import ")) {
				lines[i] = "";
			} else if (tline.startsWith("//")) {
				var matcher = PROPERTY_PATTERN.matcher(tline.substring(2).trim());

				if (matcher.find()) {
					properties.computeIfAbsent(matcher.group(1).trim(), k -> new ArrayList<>()).add(matcher.group(2).trim());
				}

				lines[i] = "";
			}
		}

		this.priority = Integer.parseInt(getProperty("priority", "0"));
		this.ignored = getProperty("ignored", "false").equals("true") || getProperty("ignore", "false").equals("true");
		this.packMode = getProperty("packmode", "");
		this.requiredMods.addAll(getProperties("requires"));
		this.requiredClient = requiredMods.remove("client");
	}

	public void load(KubeJSContext cx) throws Throwable {
		cx.evaluateString(cx.topLevelScope, String.join("\n", lines), info.location, 1, null);
		lines = StringUtilsWrapper.EMPTY_STRING_ARRAY; // free memory
	}

	public List<String> getProperties(String s) {
		return properties.getOrDefault(s, List.of());
	}

	public String getProperty(String s, String def) {
		var l = getProperties(s);
		return l.isEmpty() ? def : l.getLast();
	}

	public int getPriority() {
		return priority;
	}

	public String skipLoading() {
		if (ignored) {
			return "Ignored";
		}

		if (requiredClient && !FMLLoader.getDist().isClient()) {
			return "Client only";
		}

		if (!packMode.isEmpty() && !packMode.equals(CommonProperties.get().packMode)) {
			return "Pack mode mismatch";
		}

		if (!requiredMods.isEmpty()) {
			for (String mod : requiredMods) {
				if (!ModList.get().isLoaded(mod)) {
					return "Mod " + mod + " is not loaded";
				}
			}
		}

		return "";
	}

	@Override
	public int compareTo(ScriptFile o) {
		int i = Integer.compare(o.priority, priority);
		return i == 0 ? info.locationPath.compareToIgnoreCase(o.info.locationPath) : i;
	}
}