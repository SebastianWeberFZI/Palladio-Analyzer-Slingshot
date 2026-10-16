package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * The OSGi bundles found on a plain class path.
 *
 * <p>
 * Every class path entry (jar or directory) that carries a
 * {@code META-INF/MANIFEST.MF} with a {@code Bundle-SymbolicName} counts as a
 * bundle. Its root URL is used to resolve
 * {@code platform:/plugin/<symbolic name>/...} URIs.
 */
public final class ClasspathBundles {

	/** A bundle on the class path: symbolic name and the URL of its root. */
	public record Bundle(String symbolicName, URL root) {
	}

	private ClasspathBundles() {
	}

	/** All bundles visible to the class loader, keyed by symbolic name (first one wins). */
	public static Map<String, Bundle> scan(final ClassLoader loader) throws IOException {
		final Map<String, Bundle> bundles = new LinkedHashMap<>();
		final Enumeration<URL> manifests = loader.getResources("META-INF/MANIFEST.MF");
		for (final URL manifestUrl : Collections.list(manifests)) {
			final String name;
			try (InputStream in = manifestUrl.openStream()) {
				name = symbolicName(new Manifest(in).getMainAttributes().getValue("Bundle-SymbolicName"));
			} catch (final IOException e) {
				continue;
			}
			if (name == null || bundles.containsKey(name)) {
				continue;
			}
			final String text = manifestUrl.toExternalForm();
			final URL root = URI.create(text.substring(0, text.length() - "META-INF/MANIFEST.MF".length())).toURL();
			bundles.put(name, new Bundle(name, root));
		}
		return bundles;
	}

	/** {@code org.foo;singleton:=true} becomes {@code org.foo}. */
	static String symbolicName(final String header) {
		if (header == null || header.isBlank()) {
			return null;
		}
		final int semicolon = header.indexOf(';');
		return (semicolon < 0 ? header : header.substring(0, semicolon)).trim();
	}
}
