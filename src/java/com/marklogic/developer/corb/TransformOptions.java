/*
 * Copyright (c)2005-2010 Mark Logic Corporation
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

/**
 * @author Michael Blakeley, michael.blakeley@marklogic.com
 * @author Colleen Whitney, colleen.whitney@marklogic.com
 * 
 */
public class TransformOptions {

    public static final int SLEEP_TIME_MS = 500;

    public static final long PROGRESS_INTERVAL_MS = 60 * SLEEP_TIME_MS;

    public static final String NAME = TransformOptions.class.getName();

    private static final String SLASH = "/";

    private static final char SLASHCHAR = SLASH.toCharArray()[0];

    public static final String COLLECTION_TYPE = "COLLECTION";

    public static final String DIRECTORY_TYPE = "DIRECTORY";

    public static final String QUERY_TYPE = "QUERY";

    public static final String DEFAULT_OUTPUT_LOG_FILE_NAME_FORMAT = "output-%u-%g.log";

    private String processModule = null;

    // Defaults for optional arguments
    private String moduleRoot = SLASH
            + TransformOptions.class.getPackage().getName().replace('.',
                    SLASHCHAR) + SLASH;

    private String urisModule = "get-uris.xqy";

    private int threadCount = 1;

    private boolean doInstall = true;

    // We could get rid of this now that we check status...
    private String modulesDatabase = "Modules";

    // Set on status check
    private String XDBC_ROOT = SLASH;

    private String outputLogFileNameFormat = DEFAULT_OUTPUT_LOG_FILE_NAME_FORMAT;

    /**
     * @return
     */
    public String getXDBC_ROOT() {
        return XDBC_ROOT;
    }

    /**
     * @param xdbc_root
     */
    public void setXDBC_ROOT(String xdbc_root) {
        XDBC_ROOT = xdbc_root;
    }

    /**
     * @return
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * @param count
     */
    public void setThreadCount(int count) {
        this.threadCount = count;
    }

    /**
     * @return
     */
    public String getLogLevel() {
        // TODO LogLevel make configurable
        return "INFO";
    }

    /**
     * @return
     */
    public String getLogHandler() {
        // TODO LogHandler make configurable
        return "CONSOLE";
    }

    /**
     * @return
     */
    public String getModulesDatabase() {
        return this.modulesDatabase;
    }

    /**
     * @param modulesDatabase
     */
    public void setModulesDatabase(String modulesDatabase) {
        this.modulesDatabase = modulesDatabase;
    }

    /**
     * @return
     */
    public String getUrisModule() {
        return urisModule;
    }

    /**
     * @param urisModule
     */
    public void setUrisModule(String urisModule) {
        this.urisModule = urisModule;
    }

    /**
     * @return
     */
    public String getProcessModule() {
        return processModule;
    }

    /**
     * @param processModule
     */
    public void setProcessModule(String processModule) {
        this.processModule = processModule;
    }

    /**
     * @return
     */
    public String getModuleRoot() {
        return moduleRoot;
    }

    /**
     * @param moduleRoot
     */
    public void setModuleRoot(String moduleRoot) {
        this.moduleRoot = moduleRoot;
    }

    /**
     * @return
     */
    public boolean isDoInstall() {
        return doInstall;
    }

    /**
     * @param doInstall
     */
    public void setDoInstall(boolean doInstall) {
        this.doInstall = doInstall;
    }

    /**
     * @return
     */
    public int getQueueSize() {
        return 100 * 1000;
    }
    
    public String getOutputLogFileNameFormat() {
        return outputLogFileNameFormat;
    }
    
    public void setOutputLogFileNameFormat(String pathFormat) {
        this.outputLogFileNameFormat = pathFormat;
    }

}
