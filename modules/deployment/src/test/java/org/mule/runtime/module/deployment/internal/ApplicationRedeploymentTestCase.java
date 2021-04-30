/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.internal;

import static java.io.File.separator;
import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.deployment.model.api.DeployableArtifactDescriptor.PROPERTY_CONFIG_RESOURCES;

import org.mule.runtime.module.deployment.api.DeploymentListener;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import io.qameta.allure.Flaky;

/**
 * Contains test for application re-deployment on the default domain
 */
public class ApplicationRedeploymentTestCase extends ApplicationDeploymentTestCase {

  public ApplicationRedeploymentTestCase(boolean parallelDeployment) {
    super(parallelDeployment);
  }

  @Parameters(name = "Parallel: {0}")
  public static List<Boolean> params() {
    // Only run without parallel deployment since this configuration does not affect re-deployment at all
    return asList(false);
  }

  @Test
  public void removesPreviousAppFolderOnRedeploy() throws Exception {
    startDeployment();

    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    assertAppsDir(NONE, new String[] {testArtifacts.createEmptyAppFileBuilder().getId()}, true);
    assertEquals("Application has not been properly registered with Mule", 1, deploymentService.getApplications().size());

    reset(applicationDeploymentListener);

    ApplicationFileBuilder emptyAppFileBuilder =
        appFileBuilder("empty-app").usingResource("empty-config.xml", "empty-config.xml")
            .deployedWith(PROPERTY_CONFIG_RESOURCES, "empty-config.xml");

    addPackedAppFromBuilder(emptyAppFileBuilder);

    assertApplicationRedeploymentSuccess(emptyAppFileBuilder.getId());

    assertApplicationFiles(emptyAppFileBuilder.getId(), new String[] {"empty-config.xml"});
  }

  @Test
  public void redeploysAppZipDeployedAfterStartup() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    startDeployment();

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    assertAppsDir(NONE, new String[] {testArtifacts.createEmptyAppFileBuilder().getId()}, true);
    assertEquals("Application has not been properly registered with Mule", 1, deploymentService.getApplications().size());

    reset(applicationDeploymentListener);

    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationRedeploymentSuccess(testArtifacts.createEmptyAppFileBuilder().getId());
    assertEquals("Application has not been properly registered with Mule", 1, deploymentService.getApplications().size());
    assertAppsDir(NONE, new String[] {testArtifacts.createEmptyAppFileBuilder().getId()}, true);
  }

  @Test
  public void redeploysExplodedAppOnStartup() throws Exception {
    addExplodedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());

    startDeployment();

    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);

    reset(applicationDeploymentListener);

    File configFile = new File(appsDir + separator + testArtifacts.createDummyAppDescriptorFileBuilder().getDeployedPath(),
                               getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    configFile.setLastModified(configFile.lastModified() + FILE_TIMESTAMP_PRECISION_MILLIS);

    assertApplicationRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
  }

  @Test
  public void redeploysExplodedAppAfterStartup() throws Exception {
    startDeployment();

    addExplodedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());
    assertAppsDir(NONE, new String[] {testArtifacts.createEmptyAppFileBuilder().getId()}, true);

    reset(applicationDeploymentListener);

    File configFile =
        new File(appsDir + "/" + testArtifacts.createEmptyAppFileBuilder().getDeployedPath(),
                 getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    assertThat("Configuration file does not exists", configFile.exists(), is(true));
    assertThat("Could not update last updated time in configuration file",
               configFile.setLastModified(configFile.lastModified() + FILE_TIMESTAMP_PRECISION_MILLIS), is(true));

    assertApplicationRedeploymentSuccess(testArtifacts.createEmptyAppFileBuilder().getId());
  }

  @Test
  public void redeploysBrokenExplodedAppOnStartup() throws Exception {
    addExplodedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    startDeployment();

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    // Maintains app dir created
    assertAppsDir(NONE, new String[] {testArtifacts.createIncompleteAppFileBuilder().getId()}, true);
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteAppFileBuilder().getId(),
                                       deploymentService.getZombieApplications());

    reset(applicationDeploymentListener);

    final ReentrantLock lock = deploymentService.getLock();
    lock.lock();
    try {
      File configFile = new File(appsDir + "/" + testArtifacts.createIncompleteAppFileBuilder().getDeployedPath(),
                                 getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
      assertThat(configFile.exists(), is(true));
      configFile.setLastModified(configFile.lastModified() + FILE_TIMESTAMP_PRECISION_MILLIS);
    } finally {
      lock.unlock();
    }

    assertFailedApplicationRedeploymentFailure(applicationDeploymentListener,
                                               testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysBrokenExplodedAppAfterStartup() throws Exception {
    startDeployment();

    addExplodedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    // Maintains app dir created
    assertAppsDir(NONE, new String[] {testArtifacts.createIncompleteAppFileBuilder().getId()}, true);
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteAppFileBuilder().getId(),
                                       deploymentService.getZombieApplications());

    reset(applicationDeploymentListener);

    File configFile = new File(appsDir + "/" + testArtifacts.createIncompleteAppFileBuilder().getDeployedPath(),
                               getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    updateFileModifiedTime(configFile.lastModified(), configFile);

    assertFailedApplicationRedeploymentFailure(applicationDeploymentListener,
                                               testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidExplodedAppAfterSuccessfulDeploymentOnStartup() throws Exception {
    addExplodedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());

    startDeployment();

    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());

    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);

    reset(applicationDeploymentListener);

    File originalConfigFile = new File(appsDir + "/" + testArtifacts.createDummyAppDescriptorFileBuilder().getDeployedPath(),
                                       getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    URL url = getClass().getResource(BROKEN_CONFIG_XML);
    File newConfigFile = new File(url.toURI());
    copyFile(newConfigFile, originalConfigFile);

    assertApplicationRedeploymentFailure(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
  }

  @Test
  @Ignore("MULE-16403")
  @Flaky
  public void redeploysInvalidExplodedAppAfterSuccessfulDeploymentAfterStartup() throws Exception {
    startDeployment();

    addExplodedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);

    reset(applicationDeploymentListener);

    File originalConfigFile = new File(appsDir + "/" + testArtifacts.createDummyAppDescriptorFileBuilder().getDeployedPath(),
                                       getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    assertThat(originalConfigFile.exists(), is(true));
    URL url = getClass().getResource(BROKEN_CONFIG_XML);
    File newConfigFile = new File(url.toURI());
    copyFile(newConfigFile, originalConfigFile);

    assertApplicationRedeploymentFailure(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
  }

  @Test
  public void redeploysFixedAppAfterBrokenExplodedAppOnStartup() throws Exception {
    addExplodedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    startDeployment();

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    reset(applicationDeploymentListener);

    File originalConfigFile = new File(appsDir + "/" + testArtifacts.createIncompleteAppFileBuilder().getDeployedPath(),
                                       getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    assertThat(originalConfigFile.exists(), is(true));
    URL url = getClass().getResource(EMPTY_APP_CONFIG_XML);
    File newConfigFile = new File(url.toURI());
    copyFile(newConfigFile, originalConfigFile);
    assertFailedApplicationRedeploymentSuccess(testArtifacts.createIncompleteAppFileBuilder().getId());

    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    // Check that the failed application folder is still there
    assertAppFolderIsMaintained(testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysFixedAppAfterBrokenExplodedAppAfterStartup() throws Exception {
    startDeployment();

    addExplodedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    reset(applicationDeploymentListener);

    ReentrantLock deploymentLock = deploymentService.getLock();
    deploymentLock.lock();
    try {
      File originalConfigFile = new File(appsDir + "/" + testArtifacts.createIncompleteAppFileBuilder().getDeployedPath(),
                                         getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
      URL url = getClass().getResource(EMPTY_DOMAIN_CONFIG_XML);
      File newConfigFile = new File(url.toURI());
      copyFile(newConfigFile, originalConfigFile);
    } finally {
      deploymentLock.unlock();
    }

    assertFailedApplicationRedeploymentSuccess(testArtifacts.createIncompleteAppFileBuilder().getId());

    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    // Check that the failed application folder is still there
    assertAppFolderIsMaintained(testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysZipAppOnConfigChanges() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());

    startDeployment();

    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());

    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);
    assertEquals("Application has not been properly registered with Mule", 1, deploymentService.getApplications().size());

    reset(applicationDeploymentListener);

    File configFile = new File(appsDir + "/" + testArtifacts.createDummyAppDescriptorFileBuilder().getDeployedPath(),
                               getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    configFile.setLastModified(configFile.lastModified() + FILE_TIMESTAMP_PRECISION_MILLIS);

    assertApplicationRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    assertEquals("Application has not been properly registered with Mule", 1, deploymentService.getApplications().size());
    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);
  }

  @Test
  public void redeployedFailedAppAfterTouched() throws Exception {
    addExplodedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    File appFolder = new File(appsDir.getPath(), testArtifacts.createEmptyAppFileBuilder().getId());

    File configFile = new File(appFolder, getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    writeStringToFile(configFile, "you shall not pass");

    startDeployment();
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());
    reset(applicationDeploymentListener);

    URL url = getClass().getResource(EMPTY_DOMAIN_CONFIG_XML);
    copyFile(new File(url.toURI()), configFile);

    assertFailedApplicationRedeploymentSuccess(testArtifacts.createEmptyAppFileBuilder().getId());
  }

  @Test
  public void redeploysZipAppAfterDeploymentErrorOnStartup() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    startDeployment();

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder(),
                            testArtifacts.createIncompleteAppFileBuilder().getZipPath());
    assertFailedApplicationRedeploymentSuccess(testArtifacts.createIncompleteAppFileBuilder().getId());

    assertNoZombiePresent(deploymentService.getZombieApplications());
  }

  @Test
  public void redeploysZipAppAfterDeploymentErrorAfterStartup() throws Exception {
    startDeployment();

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder(),
                            testArtifacts.createIncompleteAppFileBuilder().getZipPath());
    assertFailedApplicationRedeploymentSuccess(testArtifacts.createIncompleteAppFileBuilder().getId());

    assertNoZombiePresent(deploymentService.getZombieApplications());
  }

  @Test
  public void redeploysInvalidZipAppAfterSuccessfulDeploymentOnStartup() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    startDeployment();

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    reset(applicationDeploymentListener);

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder(),
                            testArtifacts.createEmptyAppFileBuilder().getZipPath());

    assertApplicationRedeploymentFailure(testArtifacts.createEmptyAppFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipAppAfterSuccessfulDeploymentAfterStartup() throws Exception {
    startDeployment();

    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder(),
                            testArtifacts.createEmptyAppFileBuilder().getZipPath());
    assertApplicationRedeploymentFailure(testArtifacts.createEmptyAppFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipAppAfterFailedDeploymentOnStartup() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    startDeployment();

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    reset(applicationDeploymentListener);

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    assertFailedApplicationRedeploymentFailure(applicationDeploymentListener,
                                               testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipAppAfterFailedDeploymentAfterStartup() throws Exception {
    startDeployment();

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    reset(applicationDeploymentListener);

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    assertFailedApplicationRedeploymentFailure(applicationDeploymentListener,
                                               testArtifacts.createIncompleteAppFileBuilder().getId());
  }

  @Test
  public void redeploysExplodedAppAfterDeploymentError() throws Exception {
    startDeployment();

    addPackedAppFromBuilder(testArtifacts.createIncompleteAppFileBuilder());

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createIncompleteAppFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    // Redeploys a fixed version for incompleteApp
    addExplodedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder(), testArtifacts.createIncompleteAppFileBuilder().getId());

    assertFailedApplicationRedeploymentSuccess(testArtifacts.createIncompleteAppFileBuilder().getId());
    assertNoZombiePresent(deploymentService.getZombieApplications());
  }

  @Test
  public void redeployMethodRedeploysIfApplicationIsAlreadyDeployedPacked() throws Exception {
    DeploymentListener mockDeploymentListener = spy(new DeploymentStatusTracker());
    deploymentService.addDeploymentListener(mockDeploymentListener);

    // Deploy an application (packed)
    addPackedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());
    startDeployment();

    // Application was deployed
    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    verify(mockDeploymentListener, times(1)).onDeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    verify(mockDeploymentListener, times(0)).onRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());

    reset(mockDeploymentListener);

    // Redeploy by using redeploy method
    deploymentService.redeploy(testArtifacts.createDummyAppDescriptorFileBuilder().getArtifactFile().toURI());

    // Application was redeployed
    verify(mockDeploymentListener, times(1)).onRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
  }

  @Test
  public void redeployMethodRedeploysIfApplicationIsAlreadyDeployedExploded() throws Exception {
    DeploymentListener mockDeploymentListener = spy(new DeploymentStatusTracker());
    deploymentService.addDeploymentListener(mockDeploymentListener);

    // Deploy an application (exploded)
    addExplodedAppFromBuilder(testArtifacts.createDummyAppDescriptorFileBuilder());
    startDeployment();

    // Application was deployed
    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    verify(mockDeploymentListener, times(1)).onDeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    verify(mockDeploymentListener, times(0)).onRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());

    // Redeploy by using redeploy method
    deploymentService.redeploy(testArtifacts.createDummyAppDescriptorFileBuilder().getArtifactFile().toURI());

    // Application was redeployed
    verify(mockDeploymentListener, times(1)).onRedeploymentSuccess(testArtifacts.createDummyAppDescriptorFileBuilder().getId());
  }

}
