package edu.illinois.library.cantaloupe.resource.admin;

import edu.illinois.library.cantaloupe.RestletApplication;
import edu.illinois.library.cantaloupe.cache.CacheFactory;
import edu.illinois.library.cantaloupe.config.Configuration;
import edu.illinois.library.cantaloupe.config.FileConfiguration;
import edu.illinois.library.cantaloupe.config.Key;
import edu.illinois.library.cantaloupe.image.Format;
import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.operation.Scale;
import edu.illinois.library.cantaloupe.processor.InitializationException;
import edu.illinois.library.cantaloupe.processor.Processor;
import edu.illinois.library.cantaloupe.processor.ProcessorFactory;
import edu.illinois.library.cantaloupe.processor.UnsupportedSourceFormatException;
import edu.illinois.library.cantaloupe.source.Source;
import edu.illinois.library.cantaloupe.source.SourceFactory;
import org.restlet.data.Header;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.util.Series;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Handles the web-based Control Panel.
 */
public class AdminResource extends AbstractAdminResource {

    /**
     * <p>Sources, caches, etc. can't be accessed from the templates, so
     * instances of this class will proxy for them.</p>
     *
     * <p>N.B.: Velocity requires this class to be public.</p>
     */
    public static class ObjectProxy {
        protected Object object;

        ObjectProxy(Object object) {
            this.object = object;
        }

        public String getName() {
            return object.getClass().getSimpleName();
        }
    }

    /**
     * N.B.: Velocity requires this class to be public.
     */
    public static class ProcessorProxy extends ObjectProxy {

        ProcessorProxy(Processor proc) {
            super(proc);
        }

        public boolean supports(Format format) {
            try {
                ((Processor) object).setSourceFormat(format);
                return true;
            } catch (UnsupportedSourceFormatException e) {
                return false;
            }
        }

        /**
         * @return List of all processor warnings, plus the message of the
         *         return value of
         *         {@link Processor#getInitializationException()}, if any.
         */
        public List<String> getWarnings() {
            Processor proc = (Processor) object;

            List<String> warnings = new ArrayList<>();

            // Add the InitializationException message
            InitializationException e = proc.getInitializationException();
            if (e != null) {
                warnings.add(e.getMessage());
            }

            // Add warnings
            warnings.addAll(proc.getWarnings());

            return warnings;
        }
    }

    /**
     * @return HTML representation of the admin interface.
     */
    @Get("html")
    public Representation doGet() {
        return template("/admin.vm", getTemplateVars());
    }

    /**
     * @return Map containing keys that will be used as variables in the admin
     *         interface's HTML template.
     */
    private Map<String,Object> getTemplateVars() {
        final Map<String, Object> vars = getCommonTemplateVars(getRequest());
        vars.put("adminUri", vars.get("baseUri") + RestletApplication.ADMIN_PATH);

        ////////////////////////////////////////////////////////////////////
        //////////////////////// status section ////////////////////////////
        ////////////////////////////////////////////////////////////////////

        // VM info
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        vars.put("vmArguments", runtimeMxBean.getInputArguments());
        vars.put("vmName", runtimeMxBean.getVmName());
        vars.put("vmVendor", runtimeMxBean.getVmVendor());
        vars.put("vmVersion", runtimeMxBean.getVmVersion());
        vars.put("javaVersion", runtimeMxBean.getSpecVersion());

        // Reverse-Proxy headers
        final Series<Header> headers = getRequest().getHeaders();
        vars.put("xForwardedProtoHeader",
                headers.getFirstValue("X-Forwarded-Proto", true, ""));
        vars.put("xForwardedHostHeader",
                headers.getFirstValue("X-Forwarded-Host", true, ""));
        vars.put("xForwardedPortHeader",
                headers.getFirstValue("X-Forwarded-Port", true, ""));
        vars.put("xForwardedPathHeader",
                headers.getFirstValue("X-Forwarded-Path", true, ""));
        vars.put("xForwardedForHeader",
                headers.getFirstValue("X-Forwarded-For", true, ""));

        Configuration config = Configuration.getInstance();
        if (config instanceof FileConfiguration) {
            final File configFile = ((FileConfiguration) config).getFile();
            vars.put("configFilePath", (configFile != null) ?
                    configFile.getAbsolutePath() : "None");
        }

        ////////////////////////////////////////////////////////////////////
        //////////////////////// sources section ///////////////////////////
        ////////////////////////////////////////////////////////////////////

        SourceFactory.SelectionStrategy selectionStrategy =
                new SourceFactory().getSelectionStrategy();
        vars.put("sourceSelectionStrategy", selectionStrategy);

        if (selectionStrategy.equals(SourceFactory.SelectionStrategy.STATIC)) {
            try {
                Source source = new SourceFactory().newSource(
                        new Identifier("irrelevant"),
                        getDelegateProxy());
                vars.put("currentSource", new ObjectProxy(source));
            } catch (Exception e) {
                // nothing we can do
            }
        }

        List<ObjectProxy> sortedProxies = SourceFactory.getAllSources().
                stream().
                map(ObjectProxy::new).
                sorted(Comparator.comparing(ObjectProxy::getName)).
                collect(Collectors.toList());
        vars.put("sources", sortedProxies);

        ////////////////////////////////////////////////////////////////////
        ////////////////////// processors section //////////////////////////
        ////////////////////////////////////////////////////////////////////

        // source format assignments
        Map<Format,ProcessorProxy> assignments = new TreeMap<>();
        for (Format format : Format.values()) {
            try (Processor proc = new ProcessorFactory().newProcessor(format)) {
                assignments.put(format, new ProcessorProxy(proc));
            } catch (UnsupportedSourceFormatException |
                    InitializationException |
                    ReflectiveOperationException e) {
                // nothing we can do
            }
        }
        vars.put("processorAssignments", assignments);

        // image source formats
        List<Format> imageFormats = Arrays.stream(Format.values()).
                filter(f -> Format.Type.IMAGE.equals(f.getType()) && !Format.DCM.equals(f)).
                sorted(Comparator.comparing(Format::getName)).
                collect(Collectors.toList());
        vars.put("imageSourceFormats", imageFormats);

        // video source formats
        List<Format> videoFormats = Arrays.stream(Format.values()).
                filter(f -> Format.Type.VIDEO.equals(f.getType())).
                sorted(Comparator.comparing(Format::getName)).
                collect(Collectors.toList());
        vars.put("videoSourceFormats", videoFormats);

        // source format assignments
        vars.put("sourceFormats", Format.values());

        List<ProcessorProxy> sortedProcessorProxies =
                ProcessorFactory.getAllProcessors().stream().
                        map(ProcessorProxy::new).
                        sorted(Comparator.comparing(ObjectProxy::getName)).
                        collect(Collectors.toList());

        // warnings
        vars.put("anyWarnings", sortedProcessorProxies.stream().
                anyMatch(p -> !p.getWarnings().isEmpty()));

        vars.put("processors", sortedProcessorProxies);

        // source formats
        vars.put("scaleFilters", Scale.Filter.values());

        ////////////////////////////////////////////////////////////////////
        //////////////////////// caches section ////////////////////////////
        ////////////////////////////////////////////////////////////////////

        // source caches
        try {
            vars.put("currentSourceCache", CacheFactory.getSourceCache());
        } catch (Exception e) {
            // noop
        }

        sortedProxies = CacheFactory.getAllSourceCaches().stream().
                map(ObjectProxy::new).
                sorted(Comparator.comparing(ObjectProxy::getName)).
                collect(Collectors.toList());
        vars.put("sourceCaches", sortedProxies);

        // derivative caches
        try {
            vars.put("currentDerivativeCache",
                    CacheFactory.getDerivativeCache());
        } catch (Exception e) {
            // noop
        }

        sortedProxies = CacheFactory.getAllDerivativeCaches().stream().
                map(ObjectProxy::new).
                sorted(Comparator.comparing(ObjectProxy::getName)).
                collect(Collectors.toList());
        vars.put("derivativeCaches", sortedProxies);

        ////////////////////////////////////////////////////////////////////
        /////////////////////// overlays section ///////////////////////////
        ////////////////////////////////////////////////////////////////////

        vars.put("fonts", GraphicsEnvironment.getLocalGraphicsEnvironment().
                getAvailableFontFamilyNames());
        vars.put("currentOverlayFont", Configuration.getInstance().
                getString(Key.OVERLAY_STRING_FONT, ""));

        return vars;
    }

}
