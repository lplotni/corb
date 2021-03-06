/*
 * Copyright (c)2005-2012 Mark Logic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */
package com.marklogic.developer.corb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.marklogic.developer.Utilities;
import com.marklogic.developer.SimpleLogger;

import com.marklogic.xcc.AdhocQuery;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentCreateOptions;
import com.marklogic.xcc.ContentFactory;
import com.marklogic.xcc.ContentSource;
import com.marklogic.xcc.ContentSourceFactory;
import com.marklogic.xcc.Request;
import com.marklogic.xcc.RequestOptions;
import com.marklogic.xcc.ResultItem;
import com.marklogic.xcc.ResultSequence;
import com.marklogic.xcc.SecurityOptions;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;
import com.marklogic.xcc.exceptions.XccConfigException;
import com.marklogic.xcc.exceptions.XccException;
import com.marklogic.xcc.types.XSInteger;
import com.marklogic.xcc.types.XdmItem;

/**
 * @author Michael Blakeley, MarkLogic Corporation
 * @author Colleen Whitney, MarkLogic Corporation
 */
public class Manager implements Runnable {

    public static final String OUTPUT_FILE_NAME_FORMAT = "output.file.name.format";

    public static String VERSION = "2012-03-14.1";

    public class CallerBlocksPolicy implements RejectedExecutionHandler {

        private BlockingQueue<Runnable> queue;

        private boolean warning = false;

        /*
         * (non-Javadoc)
         *
         * @see
         * java.util.concurrent.RejectedExecutionHandler#rejectedExecution(java
         * .lang.Runnable, java.util.concurrent.ThreadPoolExecutor)
         */
        public void rejectedExecution(Runnable r,
                ThreadPoolExecutor executor) {
            if (null == queue) {
                queue = executor.getQueue();
            }
            try {
                // block until space becomes available
                if (!warning) {
                    logger.info("queue is full: size = " + queue.size()
                            + " (will only appear once)");
                    warning = true;
                }
                queue.put(r);
            } catch (InterruptedException e) {
                // reset interrupt status and exit
                Thread.interrupted();
                // someone is trying to interrupt us
                throw new RejectedExecutionException(e);
            }
        }

    }

    private static String versionMessage = "version " + VERSION + " on "
            + System.getProperty("java.version") + " ("
            + System.getProperty("java.runtime.name") + ")";

    /**
     *
     */
    private static final String DECLARE_NAMESPACE_MLSS_XDMP_STATUS_SERVER = "declare namespace mlss = 'http://marklogic.com/xdmp/status/server'\n";

    /**
     *
     */
    private static final String XQUERY_VERSION_0_9_ML = "xquery version \"0.9-ml\"\n";

    /**
     *
     */
    private static final String NAME = Manager.class.getName();

    private URI connectionUri;

    private String collection;

    private TransformOptions options = new TransformOptions();

    private ThreadPoolExecutor pool = null;

    private ContentSource contentSource;

    private Monitor monitor;

    private SimpleLogger logger;

    private SimpleLogger outputLogger;

    private String moduleUri;

    private Thread monitorThread;

    private ExecutorCompletionService<String> completionService;

    /**
     * @param connectionUri
     * @param collection
     * @param modulePath
     * @param uriListPath
     */
    public Manager(URI connectionUri, String collection) {
        this.connectionUri = connectionUri;
        this.collection = collection;
    }

    /**
     * @param args
     * @throws URISyntaxException
     */
    public static void main(String[] args) throws URISyntaxException {
        if (args.length < 3) {
            usage();
            return;
        }

        // gather inputs
        URI connectionUri = new URI(args[0]);
        String collection = args[1];

        Manager tm = new Manager(connectionUri, collection);
        // options
        TransformOptions options = tm.getOptions();

        options.setProcessModule(args[2]);

        if (args.length > 3 && !args[3].equals("")) {
            options.setThreadCount(Integer.parseInt(args[3]));
        }
        if (args.length > 4 && !args[4].equals("")) {
            options.setUrisModule(args[4]);
        }
        if (args.length > 5 && !args[5].equals("")) {
            options.setModuleRoot(args[5]);
        }
        if (args.length > 6 && !args[6].equals("")) {
            options.setModulesDatabase(args[6]);
        }
        if (args.length > 7 && !args[7].equals("")) {
            if (args[7].equals("false") || args[7].equals("0"))
                options.setDoInstall(false);
        }

        String outputFileNameFormat = System.getProperty(OUTPUT_FILE_NAME_FORMAT);
        if (outputFileNameFormat != null) {
            options.setOutputLogFileNameFormat(outputFileNameFormat);
        }
        tm.run();
    }

    /**
     * @return
     */
    private TransformOptions getOptions() {
        return options;
    }

    /**
     *
     */
    private static void usage() {
        PrintStream err = System.err;
        err.println("\nusage:");
        err.println("\t" + NAME
                + " xcc://user:password@host:port/[ database ]"
                + " input-selector module-name.xqy"
                + " [ thread-count [ uris-module [ module-root"
                + " [ modules-database [ install ] ] ] ] ]");
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    public void run() {
        configureLogger();
        configureOutputLogger();
        logger.info(NAME + " starting: " + versionMessage);
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        logger.info("maximum heap size = " + maxMemory + " MiB");

        RuntimeMXBean RuntimemxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = RuntimemxBean.getInputArguments();
        logger.info("runtime arguments = " + Utilities.join(arguments, " "));

        prepareContentSource();
        registerStatusInfo();
        prepareModules();
        monitorThread = preparePool();

        try {
            populateQueue();

            while (monitorThread.isAlive()) {
                try {
                    monitorThread.join();
                } catch (InterruptedException e) {
                    // reset interrupt status and continue
                    Thread.interrupted();
                    logger.logException(
                            "interrupted while waiting for monitor", e);
                }
            }
        } catch (XccException e) {
            logger.logException(connectionUri.toString(), e);
            stop();
            // fatal
            throw new RuntimeException(e);
        }
    }

    /**
     * @return
     */
    private Thread preparePool() {
        RejectedExecutionHandler policy = new CallerBlocksPolicy();
        int threads = options.getThreadCount();
        // an array queue should be somewhat lighter-weight
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(
                options.getQueueSize());
        pool = new ThreadPoolExecutor(threads, threads, 16,
                TimeUnit.SECONDS, workQueue, policy);
        pool.prestartAllCoreThreads();
        completionService = new ExecutorCompletionService<String>(pool);
        monitor = new Monitor(pool, completionService, this, logger);
        Thread monitorThread = new Thread(monitor);
        return monitorThread;
    }

    /**
     * @throws IOException
     * @throws RequestException
     *
     */
    private void prepareModules() {
        String[] resourceModules = new String[] {
                options.getUrisModule(), options.getProcessModule() };
        String modulesDatabase = options.getModulesDatabase();
        logger.info("checking modules, database: " + modulesDatabase);
        Session session = contentSource.newSession(modulesDatabase);
        InputStream is = null;
        Content c = null;
        ContentCreateOptions opts = ContentCreateOptions
                .newTextInstance();
        try {
            for (int i = 0; i < resourceModules.length; i++) {
                // Start by checking install flag.
                if (!options.isDoInstall()) {
                    logger.info("Skipping module installation: "
                            + resourceModules[i]);
                    continue;
                }
                // Next check: if XCC is configured for the filesystem, warn
                // user
                else if (options.getModulesDatabase().equals("")) {
                    logger
                            .warning("XCC configured for the filesystem: please install modules manually");
                    return;
                }
                // Finally, if it's configured for a database, install.
                else {
                    File f = new File(resourceModules[i]);
                    // If not installed, are the specified files on the
                    // filesystem?
                    if (f.exists()) {
                        moduleUri = options.getModuleRoot() + f.getName();
                        c = ContentFactory.newContent(moduleUri, f, opts);
                    }
                    // finally, check package
                    else {
                        logger.warning("looking for "
                                + resourceModules[i] + " as resource");
                        moduleUri = options.getModuleRoot()
                                + resourceModules[i];
                        is = this.getClass().getResourceAsStream(
                                resourceModules[i]);
                        if (null == is) {
                            throw new NullPointerException(
                                    resourceModules[i]
                                            + " could not be found on the filesystem,"
                                            + " or in package resources");
                        }
                        c = ContentFactory
                                .newContent(moduleUri, is, opts);
                    }
                    session.insertContent(c);
                }
            }
        } catch (IOException e) {
            logger.logException("fatal error", e);
            throw new RuntimeException(e);
        } catch (RequestException e) {
            logger.logException("fatal error", e);
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    /**
     *
     */
    private void prepareContentSource() {
        logger.info("using content source " + connectionUri);
        try {
            // support SSL
            boolean ssl = connectionUri.getScheme().equals("xccs");
            contentSource = ssl ? ContentSourceFactory.newContentSource(
                    connectionUri, newTrustAnyoneOptions())
                    : ContentSourceFactory
                            .newContentSource(connectionUri);
        } catch (XccConfigException e) {
            logger.logException(connectionUri.toString(), e);
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            logger.logException(connectionUri.toString(), e);
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            logger.logException(connectionUri.toString(), e);
            throw new RuntimeException(e);
        }
    }

    private void registerStatusInfo() {
        Session session = contentSource.newSession();
        AdhocQuery q = session.newAdhocQuery(XQUERY_VERSION_0_9_ML
                + DECLARE_NAMESPACE_MLSS_XDMP_STATUS_SERVER
                + "let $status := \n"
                + " xdmp:server-status(xdmp:host(), xdmp:server())\n"
                + "let $modules := $status/mlss:modules\n"
                + "let $root := $status/mlss:root\n"
                + "return (data($modules), data($root))");
        ResultSequence rs = null;
        try {
            rs = session.submitRequest(q);
        } catch (RequestException e) {
            e.printStackTrace();
        } finally {
            session.close();
        }
        while (rs.hasNext()) {
            ResultItem rsItem = rs.next();
            XdmItem item = rsItem.getItem();
            if (rsItem.getIndex() == 0 && item.asString().equals("0")) {
                options.setModulesDatabase("");
            }
            if (rsItem.getIndex() == 1) {
                options.setXDBC_ROOT(item.asString());
            }
        }
        logger.info("Configured modules db: "
                + options.getModulesDatabase());
        logger.info("Configured modules root: " + options.getXDBC_ROOT());
        logger.info("Configured uri module: " + options.getUrisModule());
        logger.info("Configured process module: "
                + options.getProcessModule());
    }

    /**
     * @throws XccException
     */
    private void populateQueue() throws XccException {
        logger.info("populating queue");

        TaskFactory tf = new TaskFactory(contentSource, options
                .getModuleRoot()
                + options.getProcessModule());

        // must not cache the results, or we quickly run out of memory
        RequestOptions opts = new RequestOptions();
        logger.fine("buffer size = " + opts.getResultBufferSize()
                + ", caching = " + opts.getCacheResult());
        opts.setCacheResult(false);
        // this should be a noop, but xqsync does it
        opts.setResultBufferSize(0);
        logger.info("buffer size = " + opts.getResultBufferSize()
                + ", caching = " + opts.getCacheResult());

        Session session = null;
        int count = 0;
        int total = -1;

        try {
            session = contentSource.newSession();
            String urisModule = options.getModuleRoot()
                    + options.getUrisModule();
            logger.info("invoking module " + urisModule);
            Request req = session.newModuleInvoke(urisModule);
            // NOTE: collection will be treated as a CWSV
            req.setNewStringVariable("URIS", collection);
            // TODO support DIRECTORY as type
            req.setNewStringVariable("TYPE",
                    TransformOptions.COLLECTION_TYPE);
            req.setNewStringVariable("PATTERN", "[,\\s]+");
            req.setOptions(opts);

            ResultSequence res = session.submitRequest(req);

            // like a Pascal string, the first item will be the count
            total = ((XSInteger) res.next().getItem()).asPrimitiveInt();
            logger.info("expecting total " + total);
            if (0 == total) {
                logger.info("nothing to process");
                stop();
                return;
            }


            monitor.setTaskCount(total);
            monitorThread.start();

            // this may return millions of items:
            // try to be memory-efficient
            String uri;
            long lastMessageMillis = System.currentTimeMillis();
            long freeMemory;
            boolean isFirst = true;
            // char primitives use less memory than strings
            // arrays use less memory than lists or queues
            char[][] urisArray = new char[total][];

            count = 0;
            while (res.hasNext() && null != pool) {
                uri = res.next().asString();

                if (count >= urisArray.length) {
                    throw new
                        ArrayIndexOutOfBoundsException("received more than "
                                                       + total
                                                       + " results: " + uri);
                }

                // we want to test the work module immediately,
                // but we also want to ensure that
                // all uris queue as quickly as possible
                if (isFirst) {
                    isFirst = false;
                    completionService.submit(tf.newTask(uri, outputLogger));
                    urisArray[count] = null;
                    logger.info("received first uri: " + uri);
                } else {
                    urisArray[count] = uri.toCharArray();
                }
                count++;

                if (0 == count % 25000) {
                    logger.info("received " + count + "/" + total + ": " + uri);

                    if (System.currentTimeMillis() - lastMessageMillis
                        > (1000 * 4)) {
                        logger.warning("Slow receive!"
                                       + " Consider increasing max heap size"
                                       + " and using -XX:+UseConcMarkSweepGC");
                        freeMemory = Runtime.getRuntime().freeMemory();
                        logger.info("free memory: "
                                    + (freeMemory / (1024 * 1024))
                                    + " MiB");
                    }
                    lastMessageMillis = System.currentTimeMillis();
                }

            }

            logger.info("received " + count + "/" + total);
            // done with result set - close session to close everything
            if (null != session) {
                session.close();
            }

            // start with 1 not 0 because we already queued result 0
            for (int i=1; i<urisArray.length; i++) {
                // check pool occasionally, for fast-fail
                if (null == pool) {
                    break;
                }
                uri = new String(urisArray[i]);
                completionService.submit(tf.newTask(uri, outputLogger));
                urisArray[i] = null;

                String msg = "queued " + i + "/" + total + ": " + uri;
                if (0 == i % 50000) {
                    logger.info(msg);
                    freeMemory = Runtime.getRuntime().freeMemory();
                    if (freeMemory < (16 * 1024 * 1024)) {
                        logger.warning("free memory: "
                                       + (freeMemory / (1024 * 1024))
                                       + " MiB");
                    }
                    lastMessageMillis = System.currentTimeMillis();
                } else {
                    logger.finest(msg);
                }
                if (i > total) {
                    logger.warning("expected " + total + ", got " + i);
                    logger.warning("check your uri module!");
                }
            }
            logger.info("queued " + urisArray.length + "/" + total);
            urisArray = null;
            pool.shutdown();

        } catch (XccException e) {
            stop();
            throw e;
        } finally {
            if (null != session) {
                session.close();
            }
        }
        // if the pool went away, the monitor stopped it: bail out.
        if (null == pool) {
            return;
        }

        assert total == count;
        logger.fine("queue is populated with " + total + " tasks");
    }

    private void configureLogger() {
        if (logger == null) {
            logger = SimpleLogger.getSimpleLogger();
        }
        Properties props = new Properties();
        props.setProperty("LOG_LEVEL", options.getLogLevel());
        props.setProperty("LOG_HANDLER", options.getLogHandler());
        logger.configureLogger(props);
    }

    private void configureOutputLogger() {
        if (outputLogger == null) {
            outputLogger = SimpleLogger.getSimpleLogger("output");
        }
        Properties props = new Properties();
        props.setProperty("LOG_HANDLER", "FILE");
        props.setProperty("LOG_FILEHANDLER_PATH", options.getOutputLogFileNameFormat());
        props.setProperty("LOG_FORMATTER", "BareText");
        outputLogger.configureLogger(props);
    }

    /**
     * @param e
     */
    public void stop() {
        logger.info("cleaning up");
        if (null != pool) {
            List<Runnable> remaining = pool.shutdownNow();
            if (remaining != null && remaining.size() > 0) {
                logger.warning("thread pool was shut down with "
                        + remaining.size() + " pending tasks");
            }
            pool = null;
        }
        if (null != monitor) {
            monitor.shutdownNow();
        }
        if (null != monitorThread) {
            monitorThread.interrupt();
        }
    }

    /**
     * @param e
     */
    public void stop(ExecutionException e) {
        logger.logException("fatal error", e.getCause());
        logger.warning("exiting due to fatal error");
        stop();
    }

    protected static SecurityOptions newTrustAnyoneOptions()
            throws KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] trust = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            /**
             * @throws CertificateException
             */
            public void checkClientTrusted(X509Certificate[] certs,
                    String authType) throws CertificateException {
                // no exception means it's okay
            }

            /**
             * @throws CertificateException
             */
            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) throws CertificateException {
                // no exception means it's okay
            }
        } };

        SSLContext sslContext = SSLContext.getInstance("SSLv3");
        sslContext.init(null, trust, null);
        return new SecurityOptions(sslContext);
    }
}
