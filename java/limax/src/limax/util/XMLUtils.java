package limax.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

public final class XMLUtils {
	private XMLUtils() {
	}

	public static void prettySave(Document document, OutputStream destination) throws IOException {
		prettySave(document, destination, "UTF-8");
	}

	public static void prettySave(Document document, OutputStream destination, String encoding) throws IOException {
		DOMImplementation impl = document.getImplementation();
		Object f = impl.getFeature("LS", "3.0");
		if (f != null) {
			DOMImplementationLS ls = (DOMImplementationLS) f;
			LSSerializer s = ls.createLSSerializer();
			s.setNewLine("\r\n");
			DOMConfiguration cfg = s.getDomConfig();
			cfg.setParameter("format-pretty-print", Boolean.TRUE);
			LSOutput dst = ls.createLSOutput();
			dst.setEncoding(encoding);
			dst.setByteStream(destination);
			s.write(document, dst);
			return;
		}

		try {
			Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty(OutputKeys.METHOD, "xml");
			t.setOutputProperty(OutputKeys.ENCODING, encoding);
			t.transform(new DOMSource(document), new StreamResult(destination));
		} catch (TransformerException e) {
			throw new IOException(e);
		}
	}

	public static List<Element> getChildElements(Element e) {
		List<Element> list = new ArrayList<>();
		NodeList nodes = e.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE)
				list.add((Element) node);
		}
		return list;
	}

	public static String getCDataTextChildren(Node node) {
		StringBuilder sb = new StringBuilder();
		NodeList nodeList = node.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node n = nodeList.item(i);
			if (n.getNodeType() == Node.CDATA_SECTION_NODE) {
				sb.append(n.getNodeValue());
			}
		}
		return sb.toString();
	}

	private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setXIncludeAware(true);
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder();
	}

	public static Element createRootElement(String name) throws Exception {
		DocumentBuilder db = createDocumentBuilder();
		final Document doc = db.newDocument();
		final Element root = doc.createElement(name);
		doc.appendChild(root);
		return root;
	}

	public static Element getRootElement(File file) throws Exception {
		DocumentBuilder db = createDocumentBuilder();
		final Document doc = db.parse(file);
		return doc.getDocumentElement();
	}

	public static Element getRootElement(String filename) throws Exception {
		return getRootElement(new File(filename));
	}

	public static Element getRootElement(InputStream is) throws Exception {
		DocumentBuilder db = createDocumentBuilder();
		final Document doc = db.parse(is);
		return doc.getDocumentElement();
	}

	public static Element getRootElement(Reader reader) throws Exception {
		DocumentBuilder db = createDocumentBuilder();
		final Document doc = db.parse(new InputSource(reader));
		return doc.getDocumentElement();
	}
}