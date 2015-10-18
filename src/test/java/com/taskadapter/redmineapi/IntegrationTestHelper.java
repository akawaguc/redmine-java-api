package com.taskadapter.redmineapi;

import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.ProjectFactory;
import com.taskadapter.redmineapi.bean.User;
import com.taskadapter.redmineapi.bean.UserFactory;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

public class IntegrationTestHelper {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestHelper.class);

    // to make sure we all use the same config
    public static TestConfig getTestConfig() {
        return new TestConfig();
    }

    public static User getOurUser() {
        TestConfig testConfig = getTestConfig();
        Integer userId = Integer.parseInt(testConfig.getParam("createissue.userid"));
        String login = testConfig.getLogin();
        String fName = testConfig.getParam("userFName");
        String lName = testConfig.getParam("userLName");
        User user = UserFactory.create(userId);
        user.setLogin(login);
        user.setFirstName(fName);
        user.setLastName(lName);
        user.setApiKey(testConfig.getParam("apikey"));
        return user;
    }

    public static RedmineManager createRedmineManager() {
        TestConfig testConfig = getTestConfig();
        logger.info("Running Redmine integration tests using: " + testConfig.getURI());
        final TransportConfiguration transportConfiguration = getTransportConfigurationForTestServer();
        return RedmineManagerFactory.createWithUserAuth(testConfig.getURI(),
                testConfig.getLogin(), testConfig.getPassword(),
                transportConfiguration);
    }

    public static Project createProject(RedmineManager mgr) {
        Project testProject = ProjectFactory.create("test project", "test" + Calendar.getInstance().getTimeInMillis());
        try {
            return mgr.getProjectManager().createProject(testProject);
        } catch (Exception e) {
            logger.error("Exception while configuring tests", e);
            throw new RuntimeException(e);
        }
    }

    public static Project createAndReturnProject(ProjectManager mgr) {
        Project testProject = ProjectFactory.create("test project", "test" + Calendar.getInstance().getTimeInMillis());
        try {
            return mgr.createProject(testProject);
        } catch (Exception e) {
            logger.error("Exception while configuring tests", e);
            throw new RuntimeException(e);
        }
    }
    /**
     * Delete the project if it exists. this method ignores NULL or empty projectKey parameter.
     */
    public static void deleteProject(RedmineManager mgr, String projectKey) {
        try {
            if (mgr != null && projectKey != null) {
                mgr.getProjectManager().deleteProject(projectKey);
            }
        } catch (Exception e) {
            logger.error("Exception while deleting test project", e);
            throw new RuntimeException("can't delete the test project '" + projectKey + ". reason: " + e.getMessage());
        }
    }

    public static TransportConfiguration getTransportConfigurationForTestServer() {
        final PoolingClientConnectionManager connectionManager;
        try {
            connectionManager = createConnectionManagerWithOurDevKeystore();
        } catch (Exception e) {
            throw new RuntimeException("cannot create connection manager: " + e, e);
        }
        return RedmineManagerFactory.createShortTermConfig(connectionManager);
    }

    private static PoolingClientConnectionManager createConnectionManagerWithOurDevKeystore() throws
            NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException,
            CertificateException, KeyStoreException {
        final Collection<KeyStore> extraStores = new ArrayList<KeyStore>();
        final KeyStore builtInExtension = getExtensionKeystore();
        if (builtInExtension != null) {
            extraStores.add(builtInExtension);
        }
        if (!extraStores.isEmpty()) {
            return RedmineManagerFactory.createConnectionManagerWithExtraTrust(extraStores);
        }
        return RedmineManagerFactory.createDefaultConnectionManager();
    }

    /**
     * Returns a key store with the additional SSL certificates.
     * this is how we provide the self-signed SSL certificate for our
     * Redmine dev server.
     */
    private static KeyStore getExtensionKeystore() {
        final InputStream extStore =
                IntegrationTestHelper.class.getClassLoader().getResourceAsStream("ta-dev-cacerts");
        if (extStore == null) {
            return null;
        }
        try {
            final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(extStore, "changeit".toCharArray());
            return ks;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                extStore.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
