package limax.key.ed;

import java.net.URI;

interface KeyTranslate {
	KeyRep createKeyRep(URI uri) throws Exception;

	KeyRep createKeyRep(byte[] ident) throws Exception;
}
