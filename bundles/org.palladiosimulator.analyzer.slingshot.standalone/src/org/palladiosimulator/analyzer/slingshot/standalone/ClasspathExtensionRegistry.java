package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.emf.ecore.plugin.EcorePlugin;

/**
 * An Equinox extension registry without OSGi, filled from the
 * {@code plugin.xml} files on the class path.
 *
 * <p>
 * Slingshot and the Palladio libraries find their parts through the extension
 * registry: Slingshot modules ({@code org.palladiosimulator.analyzer.slingshot}),
 * recorders ({@code org.palladiosimulator.recorderframework.core}), calculator
 * factories, EMF packages and URI mappings. Outside of OSGi,
 * {@code Platform.getExtensionRegistry()} returns {@code null}.
 *
 * <p>
 * EMF already ships the standalone machinery for this:
 * {@link EcorePlugin.ExtensionProcessor#process(ClassLoader)} creates a
 * registry, makes it the default one, adds every {@code plugin.xml} on the
 * class path (with its {@code plugin.properties}), maps
 * {@code platform:/plugin/<symbolic name>/} to the physical location and reads
 * the EMF extension points. This class runs it once and remembers which bundles
 * it saw. A second registry must not be created: every {@code plugin.xml} would
 * then be contributed twice, and Slingshot would install every module twice.
 */
public final class ClasspathExtensionRegistry {

	private static final Logger LOGGER = Logger.getLogger(ClasspathExtensionRegistry.class);
	private static IExtensionRegistry registry;
	private static Map<String, ClasspathBundles.Bundle> bundles;

	private ClasspathExtensionRegistry() {
	}

	/**
	 * Creates the registry once per JVM and makes it the default registry.
	 *
	 * @return the registry (the same instance on every further call)
	 */
	public static synchronized IExtensionRegistry install(final ClassLoader loader) throws IOException {
		if (registry != null) {
			return registry;
		}
		if (RegistryFactory.getRegistry() != null) {
			throw new IllegalStateException("An extension registry is already installed (running in OSGi?)");
		}
		EcorePlugin.ExtensionProcessor.process(loader);
		final IExtensionRegistry reg = RegistryFactory.getRegistry();
		if (reg == null) {
			throw new IllegalStateException("EMF did not install an extension registry");
		}
		bundles = ClasspathBundles.scan(loader);
		registry = reg;
		LOGGER.info("Standalone extension registry: " + bundles.size() + " bundles, "
				+ reg.getExtensionPoints().length + " extension points");
		return reg;
	}

	/** Bundles found by {@link #install(ClassLoader)}, keyed by symbolic name. */
	public static synchronized Map<String, ClasspathBundles.Bundle> bundles() {
		if (bundles == null) {
			throw new IllegalStateException("install() has not been called");
		}
		return bundles;
	}
}
