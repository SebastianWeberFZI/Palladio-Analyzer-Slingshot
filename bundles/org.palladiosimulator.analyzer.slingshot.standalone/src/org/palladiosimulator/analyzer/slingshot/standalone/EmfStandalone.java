package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.File;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * EMF without Eclipse, on top of {@link ClasspathExtensionRegistry}.
 *
 * <p>
 * Packages, resource factories and URI mappings are registered by
 * {@code EcorePlugin.ExtensionProcessor} (see {@link ClasspathExtensionRegistry}).
 * This class resolves {@code uri_mapping} targets of the form
 * {@code platform:/plugin/<symbolic name>/...} (e.g. the one behind
 * {@code pathmap://PCM_MODELS/}) to the jar in one step, so that model
 * references resolve no matter how often the URI converter applies the map.
 * {@code platform:/resource/<project>/} can be mapped to a directory with
 * {@link #mapProject(String, File)}.
 */
public final class EmfStandalone {

	private static final Logger LOGGER = Logger.getLogger(EmfStandalone.class);
	private static boolean done;

	private EmfStandalone() {
	}

	/** Idempotent. Requires {@link ClasspathExtensionRegistry#install(ClassLoader)}. */
	public static synchronized void setup(final IExtensionRegistry registry) {
		if (done) {
			return;
		}
		final Map<URI, URI> uriMap = URIConverter.URI_MAP;
		for (final ClasspathBundles.Bundle bundle : ClasspathExtensionRegistry.bundles().values()) {
			uriMap.put(URI.createPlatformPluginURI(bundle.symbolicName() + "/", true),
					URI.createURI(bundle.root().toExternalForm()));
		}
		int mappings = 0;
		for (final IConfigurationElement e : registry.getConfigurationElementsFor("org.eclipse.emf.ecore.uri_mapping")) {
			final String source = e.getAttribute("source");
			final String target = e.getAttribute("target");
			if (source == null || target == null) {
				continue;
			}
			URI targetUri = URI.createURI(target);
			if (targetUri.isRelative()) {
				// relative to the contributing bundle
				final ClasspathBundles.Bundle bundle = ClasspathExtensionRegistry.bundles()
						.get(e.getContributor().getName());
				if (bundle == null) {
					continue;
				}
				targetUri = targetUri.resolve(URI.createURI(bundle.root().toExternalForm()));
			}
			uriMap.put(URI.createURI(source), resolvePlatformPlugin(targetUri, uriMap));
			mappings++;
		}
		// Models without a registered extension (e.g. *.measuringpoint in some releases) are XMI.
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().putIfAbsent(Resource.Factory.Registry.DEFAULT_EXTENSION,
				new XMIResourceFactoryImpl());
		done = true;
		LOGGER.info("EMF standalone: " + mappings + " URI mappings, "
				+ ClasspathExtensionRegistry.bundles().size() + " platform:/plugin roots");
	}

	/** {@code platform:/resource/<project>/} points to {@code directory}. */
	public static void mapProject(final String project, final File directory) {
		URIConverter.URI_MAP.put(URI.createPlatformResourceURI(project + "/", true),
				URI.createFileURI(directory.getAbsolutePath() + File.separator));
	}

	private static URI resolvePlatformPlugin(final URI uri, final Map<URI, URI> uriMap) {
		if (!uri.isPlatformPlugin()) {
			return uri;
		}
		for (final Map.Entry<URI, URI> m : uriMap.entrySet()) {
			final String prefix = m.getKey().toString();
			if (m.getKey().isPlatformPlugin() && uri.toString().startsWith(prefix)) {
				return URI.createURI(m.getValue().toString() + uri.toString().substring(prefix.length()));
			}
		}
		return uri;
	}
}
