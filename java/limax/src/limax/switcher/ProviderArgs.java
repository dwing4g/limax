package limax.switcher;

import limax.defines.VariantDefines;

interface ProviderArgs {

	int getProviderId();

	boolean isVariantEnabled();

	boolean isVariantSupported();

	boolean isScriptEnabled();

	boolean isScriptSupported();

	boolean isStateless();

	boolean isPaySupported();

	boolean isLoginDataSupported();

	VariantDefines getVariantDefines();

	String getScriptDefines();

	String getScriptDefinesKey();
}
