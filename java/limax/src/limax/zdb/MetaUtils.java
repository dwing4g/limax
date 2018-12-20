package limax.zdb;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import limax.util.XMLUtils;
import limax.xmlgen.Cbean;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;
import limax.zdb.tool.Convert;
import limax.zdb.tool.ConvertType;

final class MetaUtils {

	private MetaUtils() {
	}

	public static Set<String> testAndTrySaveToDbAndReturnUnusedTables(limax.xmlgen.Zdb meta) {
		limax.xmlgen.Zdb dbmeta;
		try {
			dbmeta = limax.xmlgen.Zdb.loadFromDb(meta.getDbHome());
		} catch (Exception e) {
			throw new XError(e);
		}
		Set<String> unusedTables = new HashSet<>();
		if (dbmeta != null) {
			if (dbmeta.isDynamic() && !meta.isDynamic())
				throw new XError("dynamic cast to static is not permitted.");
			if (!dbmeta.isDynamic() && meta.isDynamic())
				throw new XError("convert needed to cast to dynamic");
			Map<String, ConvertType> diff = Convert.diff(dbmeta, meta, false, unusedTables);
			if (!diff.isEmpty())
				throw new XError("convert needed: " + diff);
		}
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			Element e = doc.createElement("zdb");
			if (meta.isDynamic())
				e.setAttribute("dynamic", "true");
			doc.appendChild(e);
			saveZdb(meta, e);
			byte[] data;
			try (ByteArrayOutputStream dest = new ByteArrayOutputStream()) {
				XMLUtils.prettySave(doc, dest);
				data = dest.toByteArray();
			}
			switch (meta.getEngineType()) {
			case MYSQL:
				try (Connection conn = DriverManager.getConnection(meta.getDbHome())) {
					try (Statement st = conn.createStatement()) {
						st.execute(
								"CREATE TABLE IF NOT EXISTS _meta_(id INT NOT NULL PRIMARY KEY, value MEDIUMBLOB NOT NULL)ENGINE=INNODB");
					}
					try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO _meta_ VALUES(0, ?)")) {
						ps.setBytes(1, data);
						ps.executeUpdate();
					}
				}
				break;
			case EDB:
				Files.write(Paths.get(meta.getDbHome(), "meta.xml"), data, StandardOpenOption.CREATE,
						StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
				break;
			}
		} catch (Exception e) {
			throw new XError(e);
		}
		return unusedTables;
	}

	private static void saveZdb(limax.xmlgen.Zdb source, Element ele) {
		List<limax.xmlgen.Table> tables = new ArrayList<>();
		Map<String, Cbean> cbeans = new TreeMap<>();
		Map<String, Xbean> xbeans = new TreeMap<>();

		for (limax.xmlgen.Table t : source.getTables()) {
			if (!t.isMemory()) {
				tables.add(t);
				Set<limax.xmlgen.Type> beans = new HashSet<>();
				t.depends(beans);
				for (limax.xmlgen.Type b : beans) {
					if (b instanceof Cbean) {
						Cbean cb = (Cbean) b;
						cbeans.put(cb.getName(), cb);
					} else if (b instanceof Xbean) {
						Xbean xb = (Xbean) b;
						xbeans.put(xb.getName(), xb);
					}
				}
			}
		}
		cbeans.values().forEach(b -> saveCbean(b, newChild(ele, "cbean")));
		xbeans.values().forEach(b -> saveXbean(b, newChild(ele, "xbean")));
		tables.forEach(t -> saveTable(t, newChild(ele, "table")));
	}

	private static void saveTable(limax.xmlgen.Table table, Element ele) {
		ele.setAttribute("name", table.getName());
		ele.setAttribute("key", table.getKey());
		ele.setAttribute("value", table.getValue());
		if (table.isAutoIncrement())
			ele.setAttribute("autoIncrement", "true");
	}

	private static void saveCbean(Cbean bean, Element ele) {
		ele.setAttribute("name", bean.getName());
		bean.getVariables().forEach(var -> saveVar(var, newChild(ele, "variable")));
	}

	private static void saveXbean(Xbean bean, Element ele) {
		ele.setAttribute("name", bean.getName());
		if (bean.isAny())
			ele.setAttribute("any", "true");
		bean.getStaticVariables().forEach(var -> saveVar(var, newChild(ele, "variable")));
	}

	private static void saveVar(Variable var, Element ele) {
		ele.setAttribute("name", var.getName());
		ele.setAttribute("type", var.getTypeString());
		if (!var.getKey().isEmpty())
			ele.setAttribute("key", var.getKey());
		if (!var.getValue().isEmpty())
			ele.setAttribute("value", var.getValue());
	}

	private static Element newChild(Element ele, String tag) {
		Element e = ele.getOwnerDocument().createElement(tag);
		ele.appendChild(e);
		return e;
	}

}
