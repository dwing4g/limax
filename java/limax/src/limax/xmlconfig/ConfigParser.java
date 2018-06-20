package limax.xmlconfig;

import org.w3c.dom.Element;

public interface ConfigParser {
	void parse(Element self) throws Exception;
}
