package limax.auany.appconfig;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import limax.util.XMLUtils;

final class Context {
	final Map<Integer, Switcher> switcherMap = new HashMap<>();
	final Map<Integer, ShareProvider> shareProviderMap = new HashMap<>();
	final Map<Integer, App> appMap = new HashMap<>();
	final Map<Integer, App.Service.PrivateProvider> privateProviderMap = new HashMap<>();
	final List<Integer> optionalPvids = new ArrayList<>();
	private volatile StringBuilder message = new StringBuilder();

	void mergeSwitcher(Context ctx) {
		Set<Integer> set = new HashSet<>(switcherMap.keySet());
		set.retainAll(ctx.switcherMap.keySet());
		set.removeAll(set.stream().filter(i -> switcherMap.get(i).equals(ctx.switcherMap.get(i)))
				.collect(Collectors.toList()));
		if (!set.isEmpty())
			updateErrorMessage("Switcher id conflict " + set);
		switcherMap.putAll(ctx.switcherMap);
	}

	void mergeShareProvider(Context ctx) {
		Set<Integer> set = new HashSet<>(shareProviderMap.keySet());
		set.retainAll(ctx.shareProviderMap.keySet());
		set.removeAll(set.stream().filter(i -> shareProviderMap.get(i).equals(ctx.shareProviderMap.get(i)))
				.collect(Collectors.toList()));
		if (!set.isEmpty())
			updateErrorMessage("Provider id conflict " + set);
		else
			set.clear();
		set.addAll(shareProviderMap.keySet());
		set.retainAll(ctx.privateProviderMap.keySet());
		if (!set.isEmpty())
			updateErrorMessage("Provider id conflict " + set);
		shareProviderMap.putAll(ctx.shareProviderMap);
	}

	void mergeApp(Context ctx) {
		Set<Integer> set0 = new HashSet<>(appMap.keySet());
		set0.retainAll(ctx.appMap.keySet());
		set0.forEach(appid -> {
			Set<Integer> set1 = new HashSet<>(appMap.get(appid).getServices().keySet());
			set1.retainAll(ctx.appMap.get(appid).getServices().keySet());
			set1.removeAll(set1.stream().filter(
					i -> appMap.get(appid).getServices().get(i).equals(ctx.appMap.get(appid).getServices().get(i)))
					.collect(Collectors.toList()));
			if (!set1.isEmpty())
				updateErrorMessage("Service id conflict " + set1 + " app = " + appid);
			appMap.get(appid).getServices().putAll(ctx.appMap.get(appid).getServices());
		});
		set0.removeAll(set0.stream().filter(i -> appMap.get(i).equals(ctx.appMap.get(i))).collect(Collectors.toList()));
		if (!set0.isEmpty())
			updateErrorMessage("App id conflict " + set0);
		ctx.appMap.forEach((k, v) -> appMap.putIfAbsent(k, v));
		ctx.privateProviderMap.forEach((k, v) -> {
			App.Service.PrivateProvider o = privateProviderMap.putIfAbsent(k, v);
			if (o != null && (!o.getService().equals(v.getService()) || !o.getApp().equals(v.getApp())))
				updateErrorMessage("PrivateProvider id conflict " + k + " app.service.id1 = " + v.getApp().getId() + "."
						+ v.getService().getId() + " app.service.id2 = " + o.getApp().getId() + "."
						+ o.getService().getId());
		});
	}

	void initOptionalPvids() {
		optionalPvids.addAll(ShareProvider.getOptionalPvids(this));
	}

	void updateErrorMessage(String s) {
		message.append(s).append("\n");
	}

	String getErrorMessage() {
		StringBuilder tmp = message;
		message = null;
		return tmp.toString();
	}

	Context merge(Path path) throws Exception {
		try (InputStream is = new FileInputStream(path.toFile())) {
			return App.load(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is).getDocumentElement(),
					this);
		}
	}

	void save(Path path) throws Exception {
		if (Files.isWritable(path))
			Files.move(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("appconfig");
		doc.appendChild(root);
		Switcher.createElements(doc, this).forEach(e -> root.appendChild(e));
		ShareProvider.createElements(doc, this).forEach(e -> root.appendChild(e));
		App.createElements(doc, this).forEach(e -> root.appendChild(e));
		try (OutputStream os = new FileOutputStream(path.toFile())) {
			XMLUtils.prettySave(doc, os, "UTF-8");
		}
	}
}
