package de.berlios.vch.bundleloader;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.wiring.FrameworkWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleLoader implements BundleActivator, BundleListener {
    private static transient Logger logger = LoggerFactory.getLogger(BundleLoader.class);

    private static final String DIR = "plugins";

    private String pluginsDir = DIR;

    private BundleContext ctx;

    @Override
    public void start(BundleContext ctx) throws Exception {
        this.ctx = ctx;

        String vchPluginsDir = ctx.getProperty("vch.plugins.dir");
        logger.debug("vch.plugins.dir={}", vchPluginsDir);
        if (vchPluginsDir != null && !vchPluginsDir.trim().isEmpty()) {
            pluginsDir = vchPluginsDir;
        }

        load();
        ctx.addBundleListener(this);
    }

    public void load() {
        List<Bundle> installedBundles = new ArrayList<Bundle>();
        File pluginDir = new File(pluginsDir);
        if (!pluginDir.exists()) {
            logger.warn("Directory {} does not exist", pluginDir);
            return;
        }
        logger.info("Looking for plugins in {}", pluginDir.getAbsolutePath());

        // discover new bundle jars
        File[] plugins = pluginDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        // install the bundles
        for (File file : plugins) {
            Bundle bundle = null;
            try {
                bundle = ctx.installBundle("file:" + file.getAbsolutePath());
                installedBundles.add(bundle);
                file.delete();
            } catch (BundleException e) {
                logger.warn("Couldn't install plugin " + file.getAbsolutePath(), e);
            }
        }

        // start the bundles
        for (Bundle bundle : installedBundles) {
            try {
                if (resolve(bundle)) {
                    if (isFragment(bundle)) {
                        logger.debug("Fragment bundle will not be started {}", bundle.getSymbolicName());
                    } else {
                        bundle.start();
                    }
                }
            } catch (BundleException e) {
                logger.warn("Couldn't start bundle " + bundle.getSymbolicName(), e);
            }
        }
    }

    private boolean isFragment(Bundle bundle) {
        Dictionary<?, ?> headers = bundle.getHeaders();
        Enumeration<?> keys = headers.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if ("Fragment-Host".equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void bundleChanged(BundleEvent e) {
        if (e.getType() == BundleEvent.INSTALLED) {
            load();
        }
    }

    private boolean resolve(Bundle bundle) {
    	Bundle systemBundle = ctx.getBundle(0);
    	FrameworkWiring frameworkWiring = systemBundle.adapt(FrameworkWiring.class);
    	if (frameworkWiring == null) {
    		logger.warn("Couldn't adapt system bundle to FrameworkWiring");
    		return false;
    	}
    	
    	List<Bundle> resolveThis = new ArrayList<Bundle>();
    	resolveThis.add(bundle);
        return frameworkWiring.resolveBundles(resolveThis);
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
    }
}
