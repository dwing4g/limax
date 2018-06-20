package org.limax.android.chatclient.ndk;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import limax.endpoint.variant.Variant;

public class Helper {
	
	private static Variant collectionVariantFromJSonObject(Variant v,
			JSONObject obj) throws JSONException {
		JSONArray ja = obj.getJSONArray("value");
		final int count = ja.length();
		for (int i = 0; i < count; i++)
			v.collectionInsert(variantFromJSonObject(ja.getJSONObject(i)));
		return v;
	}

	private static Variant mapVariantFromJSonObject(Variant v, JSONObject obj) throws JSONException {
		JSONArray ja = obj.getJSONArray("value");
		final int count = ja.length();
		for (int i = 0; i < count; i++) {
			JSONObject e = ja.getJSONObject(i);
			Variant key = variantFromJSonObject(e.getJSONObject("key"));
			Variant value = variantFromJSonObject(e.getJSONObject("value"));
			v.mapInsert(key, value);
		}
		return v;
	}

	private static Variant structVariantFromJSonObject(Variant v, JSONObject obj) throws JSONException {
		JSONObject jv = obj.getJSONObject("value");
		Iterator<?> keys = jv.keys();
		while( keys.hasNext()) {
			final String name  = (String)keys.next();
			v.structSetValue(name,
					variantFromJSonObject(jv.getJSONObject(name)));
		}
		return v;
	}

	public static Variant variantFromJSonObject(JSONObject obj) throws JSONException {

		final String type = obj.getString("type");
		if (type.equals("null"))
			return Variant.Null;
		else if (type.equals("bool"))
			return Variant.create(obj.getBoolean("value"));
		else if (type.equals("byte"))
			return Variant.create((byte) obj.getInt("value"));
		else if (type.equals("short"))
			return Variant.create((short) obj.getInt("value"));
		else if (type.equals("int"))
			return Variant.create((int) obj.getInt("value"));
		else if (type.equals("long"))
			return Variant.create(obj.getLong("value"));
		else if (type.equals("float"))
			return Variant.create((float) obj.getDouble("value"));
		else if (type.equals("double"))
			return Variant.create(obj.getDouble("value"));
		else if (type.equals("string"))
			return Variant.create(obj.getString("value"));
		else if (type.equals("list"))
			return collectionVariantFromJSonObject(Variant.createList(), obj);
		else if (type.equals("vector"))
			return collectionVariantFromJSonObject(Variant.createVector(), obj);
		else if (type.equals("set"))
			return collectionVariantFromJSonObject(Variant.createSet(), obj);
		else if (type.equals("map"))
			return mapVariantFromJSonObject(Variant.createMap(), obj);
		else if (type.equals("struct"))
			return structVariantFromJSonObject(Variant.createStruct(), obj);
		else
			throw new RuntimeException("unknow type = " + type);
	}

	public static Variant variantFromJSonString(String str) {
		try {
			return variantFromJSonObject(new JSONObject(str));
		} catch (JSONException e) {
			e.printStackTrace();
			return Variant.Null;
		}
	}
}
