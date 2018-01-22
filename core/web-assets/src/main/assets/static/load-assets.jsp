<%@ page contentType="text/html;charset=UTF-8" language="java" import="
java.lang.*,
java.util.*,
org.opennms.web.assets.api.*,
org.opennms.web.assets.impl.*,
org.slf4j.*
" %><%!
private static final Logger LOG = LoggerFactory.getLogger(AssetLocator.class);
%><%
final AssetLocator locator = AssetLocatorImpl.getInstance();
locator.reload();
final long lastModified = locator.lastModified();

if (locator == null) {
    LOG.warn("load-assets.jsp is missing the locator");
} else {
    final String media = request.getParameter("media");
    final String mediaString = media == null? "" : " media=\"" + media + "\"";
    final String type = request.getParameter("type");
    final boolean defer = Boolean.valueOf(request.getParameter("defer"));
    final boolean async = Boolean.valueOf(request.getParameter("async"));

    final String[] assets = request.getParameterValues("asset");
    //if (LOG.isDebugEnabled()) LOG.debug("assets={}, type={}, media={}", Arrays.toString(assets), type, media);

    for (final String assetParam : assets) {
        LOG.debug("load-assets.jsp: asset={}, type={}, media={}", assetParam, type, media);
        final Optional<Collection<AssetResource>> resources = locator.getResources(assetParam);
        if (!resources.isPresent()) {
            LOG.warn("load-assets.jsp: resources not found for asset {}", assetParam);
        } else {
            for (final AssetResource resource : resources.get()) {
                final StringBuilder sb = new StringBuilder();
                if (type != null && !type.equals(resource.getType())) {
                    LOG.trace("load-assets.jsp: skipping type {} for asset {}, page requested {}", resource.getType(), assetParam, type);
                    continue;
                }
                if ("js".equals(resource.getType())) {
                    sb.append("<script ");
                    if (defer) {
                        sb.append("defer ");
                    }
                    if (async) {
                        sb.append("async ");
                    }
                    sb.append("src=\"assets/")
                        .append(resource.getPath())
                        .append("?v=").append(lastModified)
                        .append("\"></script>");
                } else if ("css".equals(resource.getType())) {
                    sb.append("<link rel=\"stylesheet\" href=\"assets/")
                        .append(resource.getPath())
                        .append("?v=").append(lastModified)
                        .append("\"")
                        .append(mediaString)
                        .append(">");
                } else {
                    LOG.warn("load-assets.jsp: unknown/unhandled asset resource type: {}", resource.getType());
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                LOG.trace("Writing HTML: {}", sb.toString());
                out.write(sb.toString());
            }
        }
    }
}
%>