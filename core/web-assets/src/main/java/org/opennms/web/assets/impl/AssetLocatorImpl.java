/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.assets.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opennms.core.utils.StringUtils;
import org.opennms.web.assets.api.AssetLocator;
import org.opennms.web.assets.api.AssetResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

public class AssetLocatorImpl extends AbstractResourceResolver implements AssetLocator, InitializingBean {
    private static Logger LOG = LoggerFactory.getLogger(AssetLocatorImpl.class);
    private static final boolean s_useMinified = Boolean.parseBoolean(System.getProperty("org.opennms.web.assets.minified", "true"));
    private static final String s_filesystemPath = System.getProperty("org.opennms.web.assets.path");
    private static AssetLocator s_instance;

    private static final Resource s_assetsPath = new ClassPathResource("/assets/");

    private final ScheduledExecutorService m_executor = Executors.newSingleThreadScheduledExecutor();

    private Map<String,List<AssetResource>> m_assets = new HashMap<>();
    private long m_lastModified = 0;

    public AssetLocatorImpl() {
    }

    public static AssetLocator getInstance() {
        return s_instance;
    }

    public long lastModified() {
        return m_lastModified;
    }

    public Collection<String> getAssets() {
        return m_assets.keySet();
    }

    public Optional<Collection<AssetResource>> getResources(final String assetName) {
        return Optional.ofNullable(m_assets.get(assetName));
    }

    public Optional<AssetResource> getResource(final String assetName, final String type) {
        final List<AssetResource> resources = m_assets.get(assetName);
        if (resources == null || resources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resources.parallelStream().filter(resource -> {
            return type.equals(resource.getType());
        }).findFirst().orElse(null));
    }

    public Optional<InputStream> open(final String assetName, final String type) throws IOException {
        final Optional<AssetResource> r = getResource(assetName, type);
        if (!r.isPresent()) {
            LOG.info("Unable to locate asset resource {}:{}", assetName, type);
            return Optional.empty();
        }
        final AssetResource resource = r.get();

        if (s_filesystemPath != null) {
            final Path p = Paths.get(s_filesystemPath).resolve(resource.getPath());
            LOG.debug("assets path is set, attempting to load {}:{} from {}", assetName, type, p);
            if (p.toFile().exists()) {
                return Optional.of(new FileInputStream(p.toFile()));
            }
        }

        final String resourcePath = resource.getPath();
        LOG.debug("Opening resource {} for asset {}", resourcePath, r);
        final URL url = getClass().getResource(resourcePath);
        if (url != null) {
            return Optional.of(url.openStream());
        }
        return Optional.of(new ClassPathResource(resourcePath).getInputStream());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        m_executor.scheduleAtFixedRate(() -> {
            reload();
        }, 5, 5, TimeUnit.MINUTES);
        reload();
        // make it easier to reach from JSP pages
        s_instance = this;
    }

    @Override
    public void reload() {
        try {
            final Map<String,List<AssetResource>> newAssets = new HashMap<>();

            Resource r = new ClassPathResource(s_useMinified? "/assets/assets.min.json" : "/assets/assets.json");

            if (s_filesystemPath != null) {
                final Path p = Paths.get(s_filesystemPath).resolve(s_useMinified? "assets.min.json" : "assets.json");
                if (p.toFile().exists()) {
                    r = new FileSystemResource(p.toFile());
                }
            }

            LOG.info("Loading asset data from {}", r);
            byte[] bdata = FileCopyUtils.copyToByteArray(r.getInputStream());

            final String json = new String(bdata, StandardCharsets.UTF_8);
            final JSONObject assetsObj = new JSONObject(json);
            final JSONArray names = assetsObj.names();
            for (int i=0; i < names.length(); i++) {
                final String assetName = names.getString(i);
                final JSONObject assetObj = assetsObj.getJSONObject(assetName);
                final List<AssetResource> assets = new ArrayList<>(assetObj.length());
                final JSONArray keys = assetObj.names();
                for (int j=0; j < keys.length(); j++) {
                    final String type = keys.getString(j);
                    final String path = assetObj.getString(type);
                    assets.add(new AssetResource(assetName, type, path));
                }
                if (assetObj.length() > 0) {
                    newAssets.put(assetName, assets);
                }
            }
            
            m_lastModified = r.lastModified();
            m_assets = newAssets;
        } catch (final Exception e) {
            LOG.warn("Failed to load asset manifest.", e);
        }
    }

    @Override
    protected Resource resolveResourceInternal(final HttpServletRequest request, final String requestPath, final List<? extends Resource> locations, final ResourceResolverChain chain) {
        return getResource(requestPath, locations);
    }

    protected Resource getResource(final String requestPath, final List<? extends Resource> locations) {
        for (final Resource location : locations) {
            try {
                if (resourcesMatch(s_assetsPath, location)) {
                    final Resource resource = location.createRelative(requestPath);
                    LOG.debug("checking request {} in location {}", requestPath, location);
                    final String fileName = resource.getFilename();

                    if (s_filesystemPath != null) {
                        final File f = Paths.get(s_filesystemPath, fileName).toFile();
                        LOG.debug("Checking for resource in filesystem: {}", f);
                        if (f.exists() && f.canRead()) {
                            LOG.trace("File exists and is readable: {}", f);
                            return new FileSystemResource(f);
                        }
                    }

                    final int index = fileName.lastIndexOf(".");
                    if (index > 0) {
                        final String assetName = fileName.substring(0,  index);
                        final String type = fileName.substring(index + 1);
                        final Optional<AssetResource> assetResource = getResource(assetName, type);
                        LOG.debug("Checking for resource in classpath: {}.{} ({})", assetName, type, assetResource);
                        if (assetResource.isPresent()) {
                            final Resource found = new ClassPathResource(assetResource.get().getPath());
                            LOG.debug("Found ClassPathResource: {}", found);
                            if (found.exists() && found.isReadable()) {
                                LOG.trace("Resource exists and is readable: {}", found);
                                return found;
                            }
                        }
                    }
                    
                    if (resource.exists()) {
                        return resource;
                    }
                }

                LOG.debug("unhandled location {} for request path {}", location, requestPath);
            } catch (final IOException e) {
                LOG.debug("Failed to create relative path from {} in {}. Trying next location.", requestPath, location, e);
            }
        }
        return null;
    }

    private boolean resourcesMatch(final Resource a, final Resource b) {
        final String aPath = getPath(a);
        final String bPath = getPath(b);
        if (aPath == null || bPath == null) {
            return false;
        }
        return aPath.equals(bPath);
    }

    private String getPath(final Resource resource) {
        String ret = null;
        if (resource instanceof UrlResource) {
            try {
                ret = resource.getURL().toExternalForm();
            } catch (final IOException e) {
            }
        } else if (resource instanceof ClassPathResource) {
            ret = ((ClassPathResource) resource).getPath();
        } else if (resource instanceof ServletContextResource) {
            ret = ((ServletContextResource) resource).getPath();
        }
        else {
            try {
                ret = resource.getURL().getPath();
            } catch (final IOException e) {
            }
        }
        return StringUtils.hasText(ret)? ret.startsWith("/")? ret.substring(1) : ret : null;
    }

    @Override
    protected String resolveUrlPathInternal(final String resourcePath, final List<? extends Resource> locations, final ResourceResolverChain chain) {
        return (StringUtils.hasText(resourcePath) && getResource(resourcePath, locations) != null ? resourcePath : null);
    }
}
