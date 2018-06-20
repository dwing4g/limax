package limax.xmlconfig;

import org.w3c.dom.Element;

public interface ConfigParserCreator {
	ConfigParser createConfigParse(Element self) throws Exception;
}
