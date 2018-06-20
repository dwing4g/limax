package limax.xmlgen.java;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import limax.xmlgen.Monitorset;

public class Monitorgen {

	private final Collection<Monitorset> mslist;

	public Monitorgen(List<Monitorset> mslist) {
		this.mslist = mslist;
		String dup = mslist.stream().map(Monitorset::getTableName)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
				.filter(e -> e.getValue() > 1).map(e -> e.getKey() + " occurs " + e.getValue() + " times")
				.collect(Collectors.joining(","));
		if (!dup.isEmpty())
			throw new RuntimeException(
					"Monitorset tablename conflict: " + dup + " (assign tableName attribute to distinguish.)");
	}

	public void make() {
		mslist.forEach(ms -> new MonitorFormatter(ms).make());
	}
}
