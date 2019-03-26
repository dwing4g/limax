package limax.net.io;

import javax.net.ssl.SSLEngine;

public interface SSLEngineDecorator {
	SSLEngine decorate(SSLEngine engine);
}
