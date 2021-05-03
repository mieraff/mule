/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.touch;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_CLASS_PACKAGES_PROPERTY;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.core.internal.context.ArtifactStoppedPersistenceListener.ARTIFACT_STOPPED_LISTENER;
import static org.mule.runtime.deployment.model.api.DeployableArtifactDescriptor.PROPERTY_CONFIG_RESOURCES;
import static org.mule.runtime.deployment.model.api.application.ApplicationStatus.CREATED;
import static org.mule.runtime.deployment.model.api.application.ApplicationStatus.DESTROYED;
import static org.mule.runtime.deployment.model.api.application.ApplicationStatus.STOPPED;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.EXPORTED_PACKAGES;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.EXPORTED_RESOURCES;
import static org.mule.runtime.deployment.model.api.domain.DomainDescriptor.DEFAULT_CONFIGURATION_RESOURCE;
import static org.mule.runtime.deployment.model.api.domain.DomainDescriptor.DEFAULT_DOMAIN_NAME;
import static org.mule.runtime.module.deployment.impl.internal.util.DeploymentPropertiesUtils.resolveDeploymentProperties;
import static org.mule.runtime.module.deployment.internal.DefaultArchiveDeployer.START_ARTIFACT_ON_DEPLOYMENT_PROPERTY;

import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleFatalException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.policy.PolicyParametrization;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.deployment.model.api.application.Application;
import org.mule.runtime.deployment.model.api.application.ApplicationStatus;
import org.mule.runtime.deployment.model.api.domain.Domain;
import org.mule.runtime.deployment.model.api.policy.PolicyRegistrationException;
import org.mule.runtime.deployment.model.internal.domain.DomainClassLoaderFactory;
import org.mule.runtime.deployment.model.internal.nativelib.DefaultNativeLibraryFinderFactory;
import org.mule.runtime.deployment.model.internal.nativelib.NativeLibraryFinderFactory;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.deployment.api.DeploymentListener;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.DomainFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.JarFileBuilder;
import org.mule.tck.probe.PollingProber;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;

/**
 * Contains test for domain deployment
 */
public class DomainDeploymentTestCase extends AbstractDeploymentTestCase {

  public DomainDeploymentTestCase(boolean parallelDeployment) {
    super(parallelDeployment);
  }

  @After
  public void disposeStaleDomains() {
    TestDomainFactory.after();
  }

  @Test
  @Ignore("MULE-12255 Add the test plugin as a plugin of the domain")
  public void redeployModifiedDomainAndRedeployFailedApps() throws Exception {
    DomainFileBuilder sharedBundleDomainFileBuilder = new DomainFileBuilder("shared-domain")
        .definedBy("shared-domain-config.xml");
    addExplodedDomainFromBuilder(sharedBundleDomainFileBuilder);

    // change shared config name to use a wrong name
    File domainConfigFile =
        new File(domainsDir + "/" + sharedBundleDomainFileBuilder.getDeployedPath(),
                 Paths.get("mule", DEFAULT_CONFIGURATION_RESOURCE).toString());
    String correctDomainConfigContent = IOUtils.toString(new FileInputStream(domainConfigFile));
    String wrongDomainFileContext = correctDomainConfigContent.replace("test-shared-config", "test-shared-config-wrong");
    copyInputStreamToFile(new ByteArrayInputStream(wrongDomainFileContext.getBytes()), domainConfigFile);
    long firstFileTimestamp = domainConfigFile.lastModified();

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());

    reset(applicationDeploymentListener);
    reset(domainDeploymentListener);

    copyInputStreamToFile(new ByteArrayInputStream(correctDomainConfigContent.getBytes()), domainConfigFile);
    alterTimestampIfNeeded(domainConfigFile, firstFileTimestamp);

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());
  }

  @Test
  public void deploysTwoIdenticalDomainsWithDifferentNames() throws Exception {
    String aDomainName = testArtifacts.createEmptyDomainFileBuilder().getId() + "A";
    String anotherDomainName = testArtifacts.createEmptyDomainFileBuilder().getId() + "B";

    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(), aDomainName);
    assertDeploymentSuccess(domainDeploymentListener, aDomainName);

    reset(domainDeploymentListener);

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(), anotherDomainName);
    assertDeploymentSuccess(domainDeploymentListener, anotherDomainName);
  }

  @Test
  public void deployTwoCompatibleDomains() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomain100FileBuilder(),
                                 testArtifacts.createEmptyDomain100FileBuilder().getId());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomain100FileBuilder().getId());

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomain101FileBuilder(),
                                 testArtifacts.createEmptyDomain101FileBuilder().getId());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomain101FileBuilder().getId());
  }

  @Test
  public void deploysDomainWithSharedLibPrecedenceOverApplicationSharedLib() throws Exception {
    final String domainId = "shared-lib";
    final DomainFileBuilder domainFileBuilder =
        new DomainFileBuilder(domainId)
            .dependingOnSharedLibrary(new JarFileBuilder("barUtils1", testArtifacts.createBarUtils1_0JarFile()))
            .definedBy("empty-domain-config.xml");
    final ApplicationFileBuilder applicationFileBuilder = new ApplicationFileBuilder("shared-lib-precedence-app")
        .definedBy("app-shared-lib-precedence-config.xml")
        .dependingOnSharedLibrary(new JarFileBuilder("barUtils2", testArtifacts.createBarUtils2_0JarFile()))
        .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class")
        .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysDomainWithSharedLibPrecedenceOverApplicationLib() throws Exception {
    final String domainId = "shared-lib";
    final DomainFileBuilder domainFileBuilder =
        new DomainFileBuilder(domainId)
            .dependingOnSharedLibrary(new JarFileBuilder("barUtils1_0", testArtifacts.createBarUtils1_0JarFile()))
            .definedBy("empty-domain-config.xml");
    final ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("shared-lib-precedence-app").definedBy("app-shared-lib-precedence-config.xml")
            .dependingOnSharedLibrary(new JarFileBuilder("barUtils2_0", testArtifacts.createBarUtils2_0JarFile()))
            .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class")
            .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysDomainWithSharedLibPrecedenceOverApplicationPluginLib() throws Exception {
    final String domainId = "shared-lib";
    final ArtifactPluginFileBuilder pluginFileBuilder =
        new ArtifactPluginFileBuilder("echoPlugin1").configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo,org.bar")
            .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class")
            .dependingOn(new JarFileBuilder("barUtils2_0", testArtifacts.createBarUtils2_0JarFile()));

    final DomainFileBuilder domainFileBuilder =
        new DomainFileBuilder(domainId)
            .dependingOnSharedLibrary(new JarFileBuilder("barUtils1.0", testArtifacts.createBarUtils1_0JarFile()))
            .definedBy("empty-domain-config.xml");

    final ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("shared-lib-precedence-app").definedBy("app-shared-lib-precedence-config.xml")
            .dependingOn(pluginFileBuilder).dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  @Issue("MULE-17112")
  @Description("If a plugin uses a library and the domain sets another version of that library as a sharedLib, the plugin internally uses its own version of the lib and not the domain's.")
  public void pluginWithDependencyAndConflictingVersionSharedByApp() throws Exception {
    ArtifactPluginFileBuilder echoPluginWithLib1 = new ArtifactPluginFileBuilder("echoPlugin1")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo")
        .dependingOn(new JarFileBuilder("barUtils1", testArtifacts.createBarUtils1_0JarFile()))
        .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class");

    final String domainId = "shared-lib";
    final DomainFileBuilder domainFileBuilder = new DomainFileBuilder(domainId)
        .dependingOnSharedLibrary(new JarFileBuilder("barUtils2_0", testArtifacts.createBarUtils2_0JarFile()))
        .definedBy("empty-domain-config.xml");

    final ApplicationFileBuilder differentLibPluginAppFileBuilder =
        new ApplicationFileBuilder("appInDomainWithLibDifferentThanPlugin")
            .definedBy("app-plugin-different-lib-config.xml")
            .dependingOn(echoPluginWithLib1)
            .dependingOn(domainFileBuilder)
            .containingClass(testArtifacts.createPluginEcho2TestClassFile(), "org/foo/echo/Plugin2Echo.class");

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(differentLibPluginAppFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, differentLibPluginAppFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  @Issue("MULE-17593")
  @Description("The IBM CTG connector must be prevented to use the fix in MULE-17112.")
  public void blacklistedPluginWithDependencyAndConflictingVersionSharedByApp() throws Exception {
    ArtifactPluginFileBuilder echoPluginWithLib1 = new ArtifactPluginFileBuilder("mule-ibm-ctg-connector")
        .withGroupId("com.mulesoft.connectors").withVersion("2.3.1")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo")
        .dependingOn(new JarFileBuilder("barUtils1", testArtifacts.createBarUtils1_0JarFile()))
        .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class");

    final String domainId = "shared-lib";
    final DomainFileBuilder domainFileBuilder = new DomainFileBuilder(domainId)
        .dependingOnSharedLibrary(new JarFileBuilder("barUtils2_0", testArtifacts.createBarUtils2_0JarFile()))
        .definedBy("empty-domain-config.xml");

    final ApplicationFileBuilder differentLibPluginAppFileBuilder =
        new ApplicationFileBuilder("appInDomainWithLibDifferentThanPlugin")
            .definedBy("app-plugin-different-lib-config.xml")
            .dependingOn(echoPluginWithLib1)
            .dependingOn(domainFileBuilder)
            .containingClass(testArtifacts.createPluginEcho2TestClassFile(), "org/foo/echo/Plugin2Echo.class");

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(differentLibPluginAppFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, differentLibPluginAppFileBuilder.getId());

    try {
      executeApplicationFlow("main");
      fail("Flow should throw an exception which original cause is a NoSuchMethodError");
    } catch (Throwable caught) {
      Throwable originalCause = getOriginalCause(caught);
      assertThat(originalCause, instanceOf(NoSuchMethodError.class));
      assertThat(originalCause.getMessage(), containsString("BarUtils.doStuff"));
    }
  }

  private static Throwable getOriginalCause(Throwable exception) {
    if (exception.getCause() == null) {
      return exception;
    }

    if (exception.getCause() == exception) {
      return exception;
    }

    return getOriginalCause(exception.getCause());
  }

  @Test
  public void pluginFromDomainUsedInApp() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createExceptionThrowingPluginImportingDomainFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        createExtensionApplicationWithServices("exception-throwing-app.xml")
            .dependingOn(testArtifacts.createExceptionThrowingPluginImportingDomainFileBuilder());
    addPackedAppFromBuilder(applicationFileBuilder);
    startDeployment();

    try {
      executeApplicationFlow("main");
      fail("Flow execution was expected to throw an exception");
    } catch (MuleRuntimeException expected) {
      assertThat(expected.getCause().getCause().getClass().getName(), is(equalTo("org.exception.CustomException")));
    }

  }

  @Test
  public void deploysDomainZipOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);

    final Domain domain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());
    assertNotNull(domain);
    assertNotNull(domain.getRegistry());
    assertDomainAnchorFileExists(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysPackagedDomainAndVerifyAnchorFileIsCreatedAfterDeploymentEnds() throws Exception {
    Action deployPackagedWaitDomainAction = () -> addPackedDomainFromBuilder(testArtifacts.createWaitDomainFileBuilder());
    deploysDomainAndVerifyAnchorFileIsCreatedAfterDeploymentEnds(deployPackagedWaitDomainAction);
  }

  @Test
  public void deploysExplodedDomainAndVerifyAnchorFileIsCreatedAfterDeploymentEnds() throws Exception {
    Action deployExplodedWaitDomainAction = () -> addExplodedDomainFromBuilder(testArtifacts.createWaitDomainFileBuilder());
    deploysDomainAndVerifyAnchorFileIsCreatedAfterDeploymentEnds(deployExplodedWaitDomainAction);
  }

  @Test
  public void deploysExplodedDomainBundleOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainBundleFileBuilder());
    addPackedAppFromBuilder(new ApplicationFileBuilder(testArtifacts.createDummyAppDescriptorFileBuilder())
        .dependingOn(testArtifacts.createDummyDomainBundleFileBuilder()));

    startDeployment();

    deploysDomain();
  }

  @Test
  public void deploysDomainBundleZipOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainBundleFileBuilder());
    addPackedAppFromBuilder(
                            new ApplicationFileBuilder(testArtifacts.createDummyAppDescriptorFileBuilder())
                                .dependingOn(testArtifacts.createDummyDomainBundleFileBuilder()));

    startDeployment();

    deploysDomain();
  }

  @Test
  public void deploysDomainBundleZipAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createDummyDomainBundleFileBuilder());
    addPackedAppFromBuilder(new ApplicationFileBuilder(testArtifacts.createDummyAppDescriptorFileBuilder())
        .dependingOn(testArtifacts.createDummyDomainBundleFileBuilder()));

    deploysDomain();
  }

  private void deploysDomain() throws URISyntaxException {
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainBundleFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createDummyDomainBundleFileBuilder().getId()}, true);

    final Domain domain = findADomain(testArtifacts.createDummyDomainBundleFileBuilder().getId());
    assertNotNull(domain);
    assertNotNull(domain.getRegistry());

    assertApplicationDeploymentSuccess(applicationDeploymentListener,
                                       testArtifacts.createDummyAppDescriptorFileBuilder().getId());
    assertAppsDir(NONE, new String[] {testArtifacts.createDummyAppDescriptorFileBuilder().getId()}, true);

    final Application app = findApp(testArtifacts.createDummyAppDescriptorFileBuilder().getId(), 1);
    assertNotNull(app);
  }

  @Test
  public void deploysInvalidExplodedDomainBundleOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createInvalidDomainBundleFileBuilder());

    startDeployment();

    deploysInvalidDomainBundleZip();
  }

  @Test
  public void deploysInvalidExplodedDomainBundleAfterStartup() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createInvalidDomainBundleFileBuilder());

    deploysInvalidDomainBundleZip();
  }

  @Test
  public void deploysInvalidDomainBundleZipOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createInvalidDomainBundleFileBuilder());

    startDeployment();

    deploysInvalidDomainBundleZip();
  }

  @Test
  public void deploysInvalidDomainBundleZipAfterStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createInvalidDomainBundleFileBuilder());

    startDeployment();

    deploysInvalidDomainBundleZip();
  }

  private void deploysInvalidDomainBundleZip() throws URISyntaxException {
    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createInvalidDomainBundleFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createInvalidDomainBundleFileBuilder().getId()}, true);

    assertAppsDir(NONE, new String[] {}, true);
  }

  @Test
  public void deploysDomainZipAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);

    final Domain domain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());
    assertNotNull(domain);
    assertNotNull(domain.getRegistry());
    assertDomainAnchorFileExists(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysBrokenDomainZipOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createBrokenDomainFileBuilder());

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createBrokenDomainFileBuilder().getId());

    assertDomainDir(new String[] {testArtifacts.createBrokenDomainFileBuilder().getDeployedPath()},
                    new String[] {DEFAULT_DOMAIN_NAME}, true);

    assertDomainAnchorFileDoesNotExists(testArtifacts.createBrokenDomainFileBuilder().getId());

    assertArtifactIsRegisteredAsZombie(testArtifacts.createBrokenDomainFileBuilder().getDeployedPath(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void deploysBrokenDomainZipAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createBrokenDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createBrokenDomainFileBuilder().getId());

    assertDomainDir(new String[] {testArtifacts.createBrokenDomainFileBuilder().getDeployedPath()},
                    new String[] {DEFAULT_DOMAIN_NAME}, true);

    assertDomainAnchorFileDoesNotExists(testArtifacts.createBrokenDomainFileBuilder().getId());

    assertArtifactIsRegisteredAsZombie(testArtifacts.createBrokenDomainFileBuilder().getDeployedPath(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void redeploysDomainZipDeployedOnStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    File dummyDomainFile = new File(domainsDir, testArtifacts.createEmptyDomainFileBuilder().getZipPath());
    long firstFileTimestamp = dummyDomainFile.lastModified();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);
    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());

    reset(domainDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    alterTimestampIfNeeded(dummyDomainFile, firstFileTimestamp);

    assertDomainRedeploymentSuccess(testArtifacts.createEmptyDomainFileBuilder().getId());
    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);
  }

  @Test
  public void redeployedDomainsAreDifferent() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    File dummyDomainFile = new File(domainsDir, testArtifacts.createEmptyDomainFileBuilder().getZipPath());
    long firstFileTimestamp = dummyDomainFile.lastModified();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());
    Domain firstDomain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());

    reset(domainDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    alterTimestampIfNeeded(dummyDomainFile, firstFileTimestamp);

    assertDomainRedeploymentSuccess(testArtifacts.createEmptyDomainFileBuilder().getId());
    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());
    Domain secondDomain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());

    assertNotSame(firstDomain, secondDomain);
  }

  @Test
  public void redeploysDomainZipRefreshesApps() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    File dummyDomainFile = new File(domainsDir, testArtifacts.createDummyDomainFileBuilder().getZipPath());
    long firstFileTimestamp = dummyDomainFile.lastModified();

    addPackedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    alterTimestampIfNeeded(dummyDomainFile, firstFileTimestamp);

    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
  }

  @Test
  @Issue("MULE-19040")
  @Description("When a domain was stopped and the server is restarted, the domain should not start")
  public void redeploysDomainZipRefreshesAppsButIfTheyWereStoppedTheyDoNotStart() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    File dummyDomainFile = new File(domainsDir, testArtifacts.createDummyDomainFileBuilder().getZipPath());
    long firstFileTimestamp = dummyDomainFile.lastModified();

    addPackedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());

    final Application app = findApp(testArtifacts.createDummyDomainApp1FileBuilder().getId(), 1);
    app.stop();

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    alterTimestampIfNeeded(dummyDomainFile, firstFileTimestamp);

    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertStatus(testArtifacts.createDummyDomainApp1FileBuilder().getId(), CREATED);
  }

  @Test
  public void redeploysDomainZipDeployedAfterStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    File dummyDomainFile = new File(domainsDir, testArtifacts.createDummyDomainFileBuilder().getZipPath());
    long firstFileTimestamp = dummyDomainFile.lastModified();

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createDummyDomainFileBuilder().getId()}, true);
    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());

    reset(domainDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());
    alterTimestampIfNeeded(dummyDomainFile, firstFileTimestamp);

    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());
    assertEquals("Domain has not been properly registered with Mule", 2, deploymentService.getDomains().size());
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createDummyDomainFileBuilder().getId()}, true);
  }

  @Test
  public void deploysAppUsingDomainPlugin() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createEchoPluginFileBuilder());

    ApplicationFileBuilder echoPluginAppFileBuilder =
        new ApplicationFileBuilder("dummyWithEchoPlugin").definedBy("app-with-echo-plugin-config.xml")
            .dependingOn(domainFileBuilder);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(echoPluginAppFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, echoPluginAppFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  @Issue("MULE-14131")
  @Description("Plugin as dependency in domain and app")
  public void deploysAppAndDomainWithSamePluginDependency() throws Exception {

    final String domainId = "shared-lib";
    final ArtifactPluginFileBuilder pluginFileBuilder =
        new ArtifactPluginFileBuilder("echoPlugin1").configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo,org.bar")
            .containingClass(testArtifacts.createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class")
            .dependingOn(new JarFileBuilder("barUtils2_0", testArtifacts.createBarUtils2_0JarFile()));

    final DomainFileBuilder domainFileBuilder =
        new DomainFileBuilder(domainId)
            .dependingOnSharedLibrary(new JarFileBuilder("barUtils1.0", testArtifacts.createBarUtils1_0JarFile()))
            .definedBy("empty-domain-config.xml").dependingOn(pluginFileBuilder);

    final ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("shared-lib-precedence-app").definedBy("app-shared-lib-precedence-config.xml")
            .dependingOn(pluginFileBuilder).dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysAppUsingDomainPluginThatLoadsAppResource() throws Exception {
    ArtifactPluginFileBuilder loadsAppResourceCallbackPlugin = new ArtifactPluginFileBuilder("loadsAppResourceCallbackPlugin")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo")
        .dependingOn(new JarFileBuilder("loadsAppResourceCallbackJar", testArtifacts.createLoadsAppResourceCallbackJarFile()));

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("loading-domain")
        .definedBy("empty-domain-config.xml")
        .dependingOn(loadsAppResourceCallbackPlugin);

    ApplicationFileBuilder nonExposingAppFileBuilder = new ApplicationFileBuilder("exposing-app")
        .configuredWith(EXPORTED_PACKAGES, "org.bar")
        .configuredWith(EXPORTED_RESOURCES, "test-resource.txt")
        .definedBy("app-with-loads-app-resource-plugin-config.xml")
        .containingClass(testArtifacts.createLoadsAppResourceCallbackClassFile(), "org/foo/LoadsAppResourceCallback.class")
        .containingClass(testArtifacts.createBarUtils1ClassFile(), "org/bar/BarUtils.class")
        .containingClass(testArtifacts.createEchoTestClassFile(), "org/foo/EchoTest.class")
        .containingResource("test-resource.txt", "test-resource.txt")
        .containingResource("test-resource.txt", "test-resource-not-exported.txt");

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(nonExposingAppFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, nonExposingAppFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysAppWithPluginDependingOnDomainPlugin() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createEchoPluginFileBuilder());

    ArtifactPluginFileBuilder dependantPlugin =
        new ArtifactPluginFileBuilder("dependantPlugin").configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo.echo")
            .containingClass(testArtifacts.createPluginEcho3TestClassFile(), "org/foo/echo/Plugin3Echo.class")
            .dependingOn(testArtifacts.createEchoPluginFileBuilder());

    ApplicationFileBuilder echoPluginAppFileBuilder =
        new ApplicationFileBuilder("dummyWithEchoPlugin").definedBy("app-with-echo-plugin-config.xml")
            .dependingOn(domainFileBuilder).dependingOn(dependantPlugin);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(echoPluginAppFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, echoPluginAppFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysAppUsingDomainExtension() throws Exception {
    installEchoService();
    installFooService();

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("appWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void deploysAppUsingDomainExtensionWithSharedExtension() throws Exception {
    installEchoService();
    installFooService();

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("hello-domain-bundle")
        .definedBy("hello-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("appWithSharedHelloExtension").definedBy(APP_WITH_SHARED_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    executeApplicationFlow("main");
  }

  @Test
  public void failsToDeployAppWithDomainPluginVersionMismatch() throws Exception {
    installEchoService();
    installFooService();

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("dummyWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder)
            .dependingOn(testArtifacts.createHelloExtensionV2PluginFileBuilder());


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentFailure(applicationDeploymentListener, applicationFileBuilder.getId());
  }

  @Test
  public void failsToDeployDomainWithPluginThatUsesExtensionsClient() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("goodbye-domain-config.xml")
        .dependingOn(testArtifacts.createGoodbyeExtensionV1PluginFileBuilder());

    addPackedDomainFromBuilder(domainFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
  }

  @Test
  public void appliesApplicationPolicyUsingDomainPlugin() throws Exception {
    installEchoService();
    installFooService();

    policyManager.registerPolicyTemplate(testArtifacts.createPolicyIncludingPluginFileBuilder().getArtifactFile());

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("dummyWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    policyManager.addPolicy(applicationFileBuilder.getId(),
                            testArtifacts.createPolicyIncludingPluginFileBuilder().getArtifactId(),
                            new PolicyParametrization(FOO_POLICY_ID, s -> true, 1, emptyMap(),
                                                      getResourceFile("/appPluginPolicy.xml"), emptyList()));

    assertManualExecutionsCount(1);
  }

  @Test
  public void appliesApplicationPolicyWithPluginDependingOnDomainPlugin() throws Exception {
    installEchoService();
    installFooService();

    policyManager.registerPolicyTemplate(testArtifacts.createPolicyIncludingDependantPluginFileBuilder().getArtifactFile());

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("dummyWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    policyManager.addPolicy(applicationFileBuilder.getId(),
                            testArtifacts.createPolicyIncludingPluginFileBuilder().getArtifactId(),
                            new PolicyParametrization(FOO_POLICY_ID, s -> true, 1, emptyMap(),
                                                      getResourceFile("/appPluginPolicy.xml"), emptyList()));

    assertManualExecutionsCount(1);
  }

  @Test
  public void appliesApplicationPolicyDuplicatingDomainPlugin() throws Exception {
    installEchoService();
    installFooService();

    policyManager.registerPolicyTemplate(testArtifacts.createPolicyIncludingPluginFileBuilder().getArtifactFile());

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("dummyWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);


    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    policyManager.addPolicy(applicationFileBuilder.getId(),
                            testArtifacts.createPolicyIncludingPluginFileBuilder().getArtifactId(),
                            new PolicyParametrization(FOO_POLICY_ID, s -> true, 1, emptyMap(),
                                                      getResourceFile("/appPluginPolicy.xml"), emptyList()));

    assertManualExecutionsCount(1);
  }

  @Ignore("MULE-15842: fix once we support declaring share objects plugins in policies")
  @Test
  public void failsToApplyApplicationPolicyWithDomainPluginVersionMismatch() throws Exception {
    installEchoService();
    installFooService();

    policyManager.registerPolicyTemplate(testArtifacts.createPolicyIncludingHelloPluginV2FileBuilder().getArtifactFile());

    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml")
        .dependingOn(testArtifacts.createHelloExtensionV1PluginFileBuilder());

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("dummyWithHelloExtension").definedBy(APP_WITH_EXTENSION_PLUGIN_CONFIG)
            .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    try {
      policyManager.addPolicy(applicationFileBuilder.getId(),
                              testArtifacts.createPolicyIncludingHelloPluginV2FileBuilder().getArtifactId(),
                              new PolicyParametrization(FOO_POLICY_ID, s -> true, 1, emptyMap(),
                                                        getResourceFile("/appPluginPolicy.xml"), emptyList()));
      fail("Policy application should have failed");
    } catch (PolicyRegistrationException expected) {
    }
  }

  @Test
  public void deploysExplodedDomainOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);
    assertDomainAnchorFileExists(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysPackagedDomainOnStartupWhenExplodedDomainIsAlsoPresent() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Checks that dummy app was deployed just once
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysExplodedDomainAfterStartup() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createEmptyDomainFileBuilder().getId()}, true);
    assertDomainAnchorFileExists(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysInvalidExplodedDomainOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(), "domain with spaces");

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, "domain with spaces");

    // Maintains app dir created
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, "domain with spaces"}, true);
    assertArtifactIsRegisteredAsZombie("domain with spaces", deploymentService.getZombieDomains());
  }

  @Test
  public void deploysInvalidExplodedDomainAfterStartup() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(), "domain with spaces");

    assertDeploymentFailure(domainDeploymentListener, "domain with spaces");

    // Maintains app dir created
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, "domain with spaces"}, true);
    assertArtifactIsRegisteredAsZombie("domain with spaces", deploymentService.getZombieDomains());
  }

  @Test
  public void deploysInvalidExplodedDomainOnlyOnce() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(), "domain with spaces");
    assertDeploymentFailure(domainDeploymentListener, "domain with spaces", times(1));

    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    DomainFileBuilder updatedDomainFileBuilder =
        new DomainFileBuilder("empty2-domain", testArtifacts.createEmptyDomainFileBuilder());
    addExplodedDomainFromBuilder(updatedDomainFileBuilder);
    assertDeploymentSuccess(domainDeploymentListener, updatedDomainFileBuilder.getId());

    // After three update cycles should have only one deployment failure notification for the broken app
    assertDeploymentFailure(domainDeploymentListener, "domain with spaces");
  }

  @Test
  public void deploysBrokenExplodedDomainOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Maintains app dir created
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createIncompleteDomainFileBuilder().getId()}, true);
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteDomainFileBuilder().getId(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void deploysBrokenExplodedDomainAfterStartup() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Maintains app dir created
    assertDomainDir(NONE, new String[] {DEFAULT_DOMAIN_NAME, testArtifacts.createIncompleteDomainFileBuilder().getId()}, true);
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteDomainFileBuilder().getId(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  @Ignore("MULE-12255 Add the test plugin as a plugin of the domain")
  public void receivesDomainMuleContextDeploymentNotifications() throws Exception {
    // NOTE: need an integration test like this because DefaultMuleApplication
    // class cannot be unit tested.
    addPackedDomainFromBuilder(testArtifacts.createSharedDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertMuleContextCreated(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertMuleContextInitialized(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
  }

  @Test
  public void undeploysStoppedDomain() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
    final Domain domain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());
    domain.stop();

    deploymentService.undeploy(domain);
  }

  @Test
  @Issue("MULE-19040")
  @Description("When a domain was stopped, this state should be persisted as a deployment property")
  public void whenDomainIsStoppedStateIsPersistedAsDeploymentProperty() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
    final Domain domain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());
    domain.stop();

    assertThat(domain.getRegistry().lookupByName(ARTIFACT_STOPPED_LISTENER), is(notNullValue()));

    Properties deploymentProperties =
        resolveDeploymentProperties(testArtifacts.createEmptyDomainFileBuilder().getId(), Optional.empty());
    assertThat(deploymentProperties.get(START_ARTIFACT_ON_DEPLOYMENT_PROPERTY), is(notNullValue()));
    assertThat(deploymentProperties.get(START_ARTIFACT_ON_DEPLOYMENT_PROPERTY), is("false"));
  }

  @Test
  @Issue("MULE-19040")
  @Description("When a domain was stopped by undeployment, this state should not be persisted as a deployment property")
  public void whenDomainIsStoppedByUndeploymentStateIsNotPersistedAsDeploymentProperty() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
    final Domain domain = findADomain(testArtifacts.createEmptyDomainFileBuilder().getId());

    assertThat(domain.getRegistry().lookupByName(ARTIFACT_STOPPED_LISTENER), is(notNullValue()));
    deploymentService.undeploy(domain);

    Properties deploymentProperties =
        resolveDeploymentProperties(testArtifacts.createEmptyDomainFileBuilder().getId(), Optional.empty());
    assertThat(deploymentProperties.get(START_ARTIFACT_ON_DEPLOYMENT_PROPERTY), is(nullValue()));
  }

  @Test
  public void undeploysDomainRemovingAnchorFile() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertTrue("Unable to remove anchor file", removeDomainAnchorFile(testArtifacts.createEmptyDomainFileBuilder().getId()));

    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void undeploysDomainAndDomainsApps() throws Exception {
    doDomainUndeployAndVerifyAppsAreUndeployed(() -> {
      Domain domain = findADomain(testArtifacts.createDummyDomainFileBuilder().getId());
      deploymentService.undeploy(domain);
    });
  }

  @Test
  public void undeploysDomainAndDomainsAppsRemovingAnchorFile() throws Exception {
    doDomainUndeployAndVerifyAppsAreUndeployed(createUndeployDummyDomainAction());
  }

  @Test
  public void undeployDomainDoesNotDeployAllApplications() throws Exception {
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    doDomainUndeployAndVerifyAppsAreUndeployed(createUndeployDummyDomainAction());

    assertThat(findApp(testArtifacts.createEmptyAppFileBuilder().getId(), 1), notNullValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void findDomainApplicationsWillNullDomainFails() {
    deploymentService.findDomainApplications(null);
  }

  @Test
  public void findDomainApplicationsWillNonExistentDomainReturnsEmptyCollection() {
    Collection<Application> domainApplications = deploymentService.findDomainApplications("");
    assertThat(domainApplications, notNullValue());
    assertThat(domainApplications.isEmpty(), is(true));
  }

  @Test
  public void undeploysDomainCompletelyEvenOnStoppingException() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    TestDomainFactory testDomainFactory =
        TestDomainFactory.createDomainFactory(new DomainClassLoaderFactory(containerClassLoader.getClassLoader(),
                                                                           getNativeLibraryFinderFactory()),
                                              containerClassLoader, serviceManager, moduleRepository,
                                              createDescriptorLoaderRepository());
    testDomainFactory.setFailOnStopApplication();

    deploymentService.setDomainFactory(testDomainFactory);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertTrue("Unable to remove anchor file", removeDomainAnchorFile(testArtifacts.createEmptyDomainFileBuilder().getId()));

    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertAppFolderIsDeleted(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void undeploysDomainCompletelyEvenOnDisposingException() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    TestDomainFactory testDomainFactory =
        TestDomainFactory.createDomainFactory(new DomainClassLoaderFactory(containerClassLoader.getClassLoader(),
                                                                           getNativeLibraryFinderFactory()),
                                              containerClassLoader, serviceManager, moduleRepository,
                                              createDescriptorLoaderRepository());
    testDomainFactory.setFailOnDisposeApplication();
    deploymentService.setDomainFactory(testDomainFactory);
    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertTrue("Unable to remove anchor file", removeDomainAnchorFile(testArtifacts.createEmptyDomainFileBuilder().getId()));

    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    assertAppFolderIsDeleted(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void deploysIncompleteZipDomainOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Check that the failed application folder is still there
    assertDomainFolderIsMaintained(testArtifacts.createIncompleteDomainFileBuilder().getId());
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteDomainFileBuilder().getId(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void deploysIncompleteZipDomainAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Check that the failed application folder is still there
    assertDomainFolderIsMaintained(testArtifacts.createIncompleteDomainFileBuilder().getId());
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteDomainFileBuilder().getId(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void mantainsDomainFolderOnExplodedAppDeploymentError() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Check that the failed application folder is still there
    assertDomainFolderIsMaintained(testArtifacts.createIncompleteDomainFileBuilder().getId());
    assertArtifactIsRegisteredAsZombie(testArtifacts.createIncompleteDomainFileBuilder().getId(),
                                       deploymentService.getZombieDomains());
  }

  @Test
  public void redeploysZipDomainAfterDeploymentErrorOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Deploys another domain to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(),
                               testArtifacts.createIncompleteDomainFileBuilder().getZipPath());
    assertFailedDomainRedeploymentSuccess(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  @Test
  public void redeploysZipDomainAfterDeploymentErrorAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(),
                               testArtifacts.createIncompleteDomainFileBuilder().getZipPath());

    assertFailedDomainRedeploymentSuccess(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  @Test
  public void deployAndRedeployDomainWithDeploymentProperties() throws Exception {
    Properties deploymentProperties = new Properties();
    deploymentProperties.put(COMPONENT_NAME, COMPONENT_CLASS);
    startDeployment();
    deployAndVerifyPropertyInRegistry(testArtifacts.createDomainWithPropsFileBuilder().getArtifactFile().toURI(),
                                      deploymentProperties,
                                      (registry) -> registry.lookupByName(COMPONENT_NAME_IN_APP).get() instanceof TestComponent);


    // Redeploys without deployment properties (remains the same, as it takes the deployment properties from the persisted file)
    redeployAndVerifyPropertyInRegistry(testArtifacts.createDomainWithPropsFileBuilder().getId(), null,
                                        (registry) -> registry.lookupByName(COMPONENT_NAME_IN_APP)
                                            .get() instanceof TestComponent);


    // Redeploy with new deployment properties
    deploymentProperties.clear();
    deploymentProperties.put(COMPONENT_NAME, COMPONENT_CLASS_ON_REDEPLOY);
    redeployAndVerifyPropertyInRegistry(testArtifacts.createDomainWithPropsFileBuilder().getId(), deploymentProperties,
                                        (registry) -> registry.lookupByName(COMPONENT_NAME_IN_APP)
                                            .get() instanceof TestComponentOnRedeploy);
  }

  @Test
  @Issue("MULE-19040")
  @Description("When a domain is restarted, if its apps were stopped before restart, they should not get started")
  public void redeployDomainWithStoppedAppsShouldPersistStoppedStateAndDoNotStartApps() throws Exception {
    DeploymentListener mockDeploymentListener = spy(new DeploymentStatusTracker());
    deploymentService.addDeploymentListener(mockDeploymentListener);
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());

    addPackedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());
    final Domain domain = findADomain(testArtifacts.createDummyDomainFileBuilder().getId());
    assertThat(domain.getRegistry().lookupByName(ARTIFACT_STOPPED_LISTENER), is(notNullValue()));

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());

    final Application app = findApp(testArtifacts.createDummyDomainApp1FileBuilder().getId(), 1);
    app.stop();

    assertStatus(testArtifacts.createDummyDomainApp1FileBuilder().getId(), STOPPED);

    redeployId(testArtifacts.createDummyDomainFileBuilder().getId(), null);

    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());
    verify(mockDeploymentListener, times(1)).onRedeploymentSuccess(testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertStatus(testArtifacts.createDummyDomainApp1FileBuilder().getId(), CREATED);

    Properties deploymentProperties =
        resolveDeploymentProperties(testArtifacts.createDummyDomainApp1FileBuilder().getId(), Optional.empty());
    assertThat(deploymentProperties.get(START_ARTIFACT_ON_DEPLOYMENT_PROPERTY), is(notNullValue()));
    assertThat(deploymentProperties.get(START_ARTIFACT_ON_DEPLOYMENT_PROPERTY), is("false"));

  }

  @Ignore("MULE-6926: flaky test")
  @Test
  public void refreshDomainClassloaderAfterRedeployment() throws Exception {
    startDeployment();

    // Deploy domain and apps and wait until success
    addPackedDomainFromBuilder(testArtifacts.createSharedDomainFileBuilder());
    addPackedAppFromBuilder(testArtifacts.createSharedAAppFileBuilder());
    addPackedAppFromBuilder(testArtifacts.createSharedBAppFileBuilder());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());

    // Ensure resources are registered at domain's registry
    Domain domain = findADomain(testArtifacts.createSharedDomainFileBuilder().getId());
    assertThat(domain.getRegistry().lookupByName("http-listener-config").isPresent(), is(true));

    ArtifactClassLoader initialArtifactClassLoader = domain.getArtifactClassLoader();

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    // Force redeployment by touching the domain's config file
    File domainFolder = new File(domainsDir.getPath(), testArtifacts.createSharedDomainFileBuilder().getId());
    File configFile = new File(domainFolder, testArtifacts.createSharedDomainFileBuilder().getConfigFile());
    long firstFileTimestamp = configFile.lastModified();
    touch(configFile);
    alterTimestampIfNeeded(configFile, firstFileTimestamp);

    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());
    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());

    domain = findADomain(testArtifacts.createSharedDomainFileBuilder().getId());
    ArtifactClassLoader artifactClassLoaderAfterRedeployment = domain.getArtifactClassLoader();

    // Ensure that after redeployment the domain's class loader has changed
    assertThat(artifactClassLoaderAfterRedeployment, not(sameInstance(initialArtifactClassLoader)));

    // Undeploy domain and apps
    removeAppAnchorFile(testArtifacts.createSharedAAppFileBuilder().getId());
    removeAppAnchorFile(testArtifacts.createSharedBAppFileBuilder().getId());
    removeDomainAnchorFile(testArtifacts.createSharedDomainFileBuilder().getId());
    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedAAppFileBuilder().getId());
    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createSharedBAppFileBuilder().getId());
    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createSharedDomainFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipDomainAfterSuccessfulDeploymentOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder(),
                               testArtifacts.createEmptyDomainFileBuilder().getZipPath());

    assertDomainRedeploymentFailure(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipDomainAfterSuccessfulDeploymentAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder(),
                               testArtifacts.createEmptyDomainFileBuilder().getZipPath());
    assertDomainRedeploymentFailure(testArtifacts.createEmptyDomainFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipDomainAfterFailedDeploymentOnStartup() throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    reset(domainDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertFailedDomainRedeploymentFailure(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  @Test
  public void redeploysInvalidZipDomainAfterFailedDeploymentAfterStartup() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());
    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    reset(domainDeploymentListener);

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertFailedDomainRedeploymentFailure(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  @Test
  public void redeploysExplodedDomainAfterDeploymentError() throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    // Deploys another app to confirm that DeploymentService has execute the updater thread
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Redeploys a fixed version for incompleteDomain
    addExplodedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder(),
                                 testArtifacts.createIncompleteDomainFileBuilder().getId());

    assertFailedDomainRedeploymentSuccess(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  @Test
  public void redeploysFixedDomainAfterBrokenExplodedDomainOnStartup() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    startDeployment();

    doRedeployFixedDomainAfterBrokenDomain();
  }

  @Test
  public void redeploysFixedDomainAfterBrokenExplodedDomainAfterStartup() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createIncompleteDomainFileBuilder());

    doRedeployFixedDomainAfterBrokenDomain();
  }

  @Test
  public void redeploysDomainAndItsApplications() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder(),
                                 testArtifacts.createDummyDomainFileBuilder().getId());

    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder(),
                              testArtifacts.createDummyDomainApp1FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder(),
                              testArtifacts.createDummyDomainApp2FileBuilder().getId());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    doRedeployDummyDomainByChangingConfigFileWithGoodOne();

    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationRedeploymentSuccess(testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationRedeploymentSuccess(testArtifacts.createDummyDomainApp2FileBuilder().getId());
  }

  @Test
  public void redeploysDomainAndAllApplicationsEvenWhenOneFails() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder(),
                                 testArtifacts.createDummyDomainFileBuilder().getId());

    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder(),
                              testArtifacts.createDummyDomainApp1FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder(),
                              testArtifacts.createDummyDomainApp2FileBuilder().getId());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    deploymentService.getLock().lock();
    try {
      doRedeployDummyDomainByChangingConfigFileWithGoodOne();
      ApplicationFileBuilder updateAppDomainBuilder =
          new ApplicationFileBuilder("dummy-domain-app1").definedBy("incomplete-app-config.xml");
      addExplodedAppFromBuilder(updateAppDomainBuilder);
    } finally {
      deploymentService.getLock().unlock();
    }

    assertDomainRedeploymentFailure(testArtifacts.createDummyDomainFileBuilder().getId());
    assertApplicationRedeploymentFailure(testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationRedeploymentSuccess(testArtifacts.createDummyDomainApp2FileBuilder().getId());
  }

  @Test
  public void doesNotRedeployDomainWithRedeploymentDisabled() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createDummyUndeployableDomainFileBuilderFileBuilder(),
                                 testArtifacts.createDummyUndeployableDomainFileBuilderFileBuilder().getId());
    addPackedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener,
                            testArtifacts.createDummyUndeployableDomainFileBuilderFileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    // change domain and app since once the app redeploys we can check the domain did not
    doRedeployDomainByChangingConfigFileWithGoodOne(testArtifacts.createDummyUndeployableDomainFileBuilderFileBuilder());
    doRedeployAppByChangingConfigFileWithGoodOne(testArtifacts.createEmptyAppFileBuilder().getDeployedPath());

    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());
    verify(domainDeploymentListener, never())
        .onDeploymentSuccess(testArtifacts.createDummyUndeployableDomainFileBuilderFileBuilder().getId());
  }

  @Test
  public void redeploysDomainAndFails() throws Exception {
    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder(),
                                 testArtifacts.createDummyDomainFileBuilder().getId());

    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder(),
                              testArtifacts.createDummyDomainApp1FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder(),
                              testArtifacts.createDummyDomainApp2FileBuilder().getId());

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    doRedeployDummyDomainByChangingConfigFileWithBadOne();

    assertDomainRedeploymentFailure(testArtifacts.createDummyDomainFileBuilder().getId());

    assertNoDeploymentInvoked(applicationDeploymentListener);
  }

  @Test
  public void redeploysDomainWithOneApplicationFailedOnFirstDeployment() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder(),
                                 testArtifacts.createDummyDomainFileBuilder().getId());

    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder(),
                              testArtifacts.createDummyDomainApp1FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder(),
                              testArtifacts.createDummyDomainApp2FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp3FileBuilder(),
                              testArtifacts.createDummyDomainApp3FileBuilder().getId());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createDummyDomainApp3FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    deploymentService.getLock().lock();
    try {
      doRedeployDummyDomainByChangingConfigFileWithGoodOne();
      doRedeployAppByChangingConfigFileWithGoodOne(testArtifacts.createDummyDomainApp3FileBuilder().getDeployedPath());
    } finally {
      deploymentService.getLock().unlock();
    }

    assertDomainRedeploymentSuccess(testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());
    assertDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp3FileBuilder().getId());
  }

  @Test
  public void redeploysDomainWithOneApplicationFailedAfterRedeployment() throws Exception {
    startDeployment();

    addExplodedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder(),
                                 testArtifacts.createDummyDomainFileBuilder().getId());

    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder(),
                              testArtifacts.createDummyDomainApp1FileBuilder().getId());
    addExplodedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder(),
                              testArtifacts.createDummyDomainApp2FileBuilder().getId());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());

    reset(domainDeploymentListener);
    reset(applicationDeploymentListener);

    deploymentService.getLock().lock();
    try {
      doRedeployDummyDomainByChangingConfigFileWithGoodOne();
      doRedeployAppByChangingConfigFileWithBadOne(testArtifacts.createDummyDomainApp2FileBuilder().getDeployedPath());
    } finally {
      deploymentService.getLock().unlock();
    }

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());
    assertDomainRedeploymentFailure(testArtifacts.createDummyDomainFileBuilder().getId());
  }

  @Test
  public void deployFailsWhenMissingFile() throws Exception {
    startDeployment();

    addExplodedAppFromBuilder(testArtifacts.createEmptyAppFileBuilder());

    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());
    reset(applicationDeploymentListener);

    File originalConfigFile =
        new File(appsDir + "/" + testArtifacts.createEmptyAppFileBuilder().getDeployedPath(),
                 getConfigFilePathWithinArtifact(MULE_CONFIG_XML_FILE));
    forceDelete(originalConfigFile);

    assertDeploymentFailure(applicationDeploymentListener, testArtifacts.createEmptyAppFileBuilder().getId());
    assertStatus(testArtifacts.createEmptyAppFileBuilder().getId(), ApplicationStatus.DEPLOYMENT_FAILED);
  }

  @Test
  public void synchronizesDomainDeployFromClient() throws Exception {
    final Action action =
        () -> deploymentService.deployDomain(testArtifacts.createDummyDomainFileBuilder().getArtifactFile().toURI());

    final Action assertAction =
        () -> verify(domainDeploymentListener, never()).onDeploymentStart(testArtifacts.createDummyDomainFileBuilder().getId());
    doSynchronizedDomainDeploymentActionTest(action, assertAction);
  }

  @Test
  public void synchronizesDomainUndeployFromClient() throws Exception {
    final Action action = () -> deploymentService.undeployDomain(testArtifacts.createEmptyDomainFileBuilder().getId());

    final Action assertAction =
        () -> verify(domainDeploymentListener, never()).onUndeploymentStart(testArtifacts.createEmptyDomainFileBuilder().getId());
    doSynchronizedDomainDeploymentActionTest(action, assertAction);
  }

  @Test
  public void synchronizesDomainRedeployFromClient() throws Exception {
    final Action action = () -> {
      // Clears notification from first deployment
      reset(domainDeploymentListener);
      deploymentService.redeployDomain(testArtifacts.createEmptyDomainFileBuilder().getId());
    };

    final Action assertAction =
        () -> verify(domainDeploymentListener, never()).onDeploymentStart(testArtifacts.createEmptyDomainFileBuilder().getId());
    doSynchronizedDomainDeploymentActionTest(action, assertAction);
  }

  @Test
  public void applicationBundledWithinDomainNotRemovedAfterFullDeploy()
      throws Exception {
    resetUndeployLatch();
    addPackedDomainFromBuilder(testArtifacts.createDummyDomainBundleFileBuilder());
    addPackedAppFromBuilder(new ApplicationFileBuilder(testArtifacts.createDummyAppDescriptorFileBuilder())
        .dependingOn(testArtifacts.createDummyDomainBundleFileBuilder()));
    startDeployment();
    deploysDomain();

    doRedeployBrokenDomainAfterFixedDomain();
  }

  @Test
  public void domainIncludingForbiddenJavaClass() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("forbidden-domain")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo.echo")
        .definedBy("empty-domain-config.xml")
        .containingClass(testArtifacts.createPluginForbiddenJavaEchoTestClassFile(), "org/foo/echo/PluginForbiddenJavaEcho.class")
        .dependingOn(new JarFileBuilder("barUtilsForbiddenJavaJarFile", testArtifacts.createBarUtilsForbiddenJavaJarFile()));

    final ApplicationFileBuilder forbidden = appFileBuilder("forbidden")
        .definedBy("app-with-forbidden-java-echo-plugin-config.xml")
        .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(forbidden);

    startDeployment();

    assertDeploymentSuccess(applicationDeploymentListener, forbidden.getId());

    try {
      executeApplicationFlow("main");
      fail("Expected to fail as there should be a missing class");
    } catch (Exception e) {
      assertThat(e.getCause().getCause(), instanceOf(MuleFatalException.class));
      assertThat(e.getCause().getCause().getCause(), instanceOf(NoClassDefFoundError.class));
      assertThat(e.getCause().getCause().getCause().getMessage(), containsString("java/lang/BarUtils"));
    }
  }

  @Test
  public void domainIncludingForbiddenMuleContainerClass() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("forbidden-domain")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo.echo")
        .definedBy("empty-domain-config.xml")
        .containingClass(testArtifacts.createPluginForbiddenMuleContainerEchoTestClassFile(),
                         "org/foo/echo/PluginForbiddenMuleContainerEcho.class")
        .dependingOn(new JarFileBuilder("barUtilsForbiddenMuleContainerJarFile",
                                        testArtifacts.createBarUtilsForbiddenMuleContainerJarFile()));

    final ApplicationFileBuilder forbidden = appFileBuilder("forbidden")
        .definedBy("app-with-forbidden-mule-echo-plugin-config.xml")
        .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(forbidden);

    startDeployment();

    assertDeploymentSuccess(applicationDeploymentListener, forbidden.getId());

    try {
      executeApplicationFlow("main");
      fail("Expected to fail as there should be a missing class");
    } catch (Exception e) {
      assertThat(e.getCause().getCause(), instanceOf(MuleFatalException.class));
      assertThat(e.getCause().getCause().getCause(), instanceOf(NoClassDefFoundError.class));
      assertThat(e.getCause().getCause().getCause().getMessage(), containsString("org/mule/runtime/api/util/BarUtils"));
    }
  }

  @Test
  public void domainIncludingForbiddenMuleContainerThirdParty() throws Exception {
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("forbidden-domain")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo.echo")
        .definedBy("empty-domain-config.xml")
        .containingClass(testArtifacts.createPluginForbiddenMuleThirdPartyEchoTestClassFile(),
                         "org/foo/echo/PluginForbiddenMuleThirdPartyEcho.class")
        .dependingOn(new JarFileBuilder("barUtilsForbiddenMuleThirdPartyJarFile",
                                        testArtifacts.createBarUtilsForbiddenMuleContainerJarFile()));

    final ApplicationFileBuilder forbidden = appFileBuilder("forbidden")
        .definedBy("app-with-forbidden-mule3rd-echo-plugin-config.xml")
        .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(forbidden);

    startDeployment();

    assertDeploymentSuccess(applicationDeploymentListener, forbidden.getId());

    try {
      executeApplicationFlow("main");
      fail("Expected to fail as there should be a missing class");
    } catch (Exception e) {
      assertThat(e.getCause().getCause(), instanceOf(MuleFatalException.class));
      assertThat(e.getCause().getCause().getCause(), instanceOf(NoClassDefFoundError.class));
      assertThat(e.getCause().getCause().getCause().getMessage(), containsString("org/slf4j/BarUtils"));
    }
  }

  @Test
  @Issue("MULE-18159")
  public void pluginDeclaredInDomainIsAbleToLoadClassesExportedByTheAppWhereItIsUsed() throws Exception {
    // Given a plugin which loads classes.
    final ArtifactPluginFileBuilder pluginWhichLoadsClasses = testArtifacts.createLoadClassExtensionPluginFileBuilder();

    // Given a domain depending on the plugin.
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("domain-with-test-plugin")
        .definedBy("empty-domain-config.xml")
        .dependingOn(pluginWhichLoadsClasses);

    // Given an app depending on the domain and exporting a class.
    final ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("app-with-load-class-operation").definedBy("app-with-load-class-operation.xml")
            .containingClass(testArtifacts.createEchoTestClassFile(), "org/foo/EchoTest.class")
            .configuredWith(EXPORTED_PACKAGES, "org.foo")
            .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    // When the app uses the plugin in order to load the exported class, then it doesn't raise any error.
    executeApplicationFlow("flowWhichTriesToLoadTheClass");
  }

  @Test
  @Issue("MULE-18159")
  public void pluginDeclaredInDomainIsAbleToLoadClassesExportedByTheAppWhereItIsUsedOnNonBlockingCompletion() throws Exception {
    // Given a plugin which loads classes.
    final ArtifactPluginFileBuilder pluginWhichLoadsClasses = testArtifacts.createLoadClassExtensionPluginFileBuilder();

    // Given a domain depending on the plugin.
    DomainFileBuilder domainFileBuilder = new DomainFileBuilder("domain-with-test-plugin")
        .definedBy("empty-domain-config.xml")
        .dependingOn(pluginWhichLoadsClasses);

    // Given an app depending on the domain and exporting a class.
    final ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder("app-with-load-class-operation").definedBy("app-with-load-class-operation.xml")
            .containingClass(testArtifacts.createEchoTestClassFile(), "org/foo/EchoTest.class")
            .configuredWith(EXPORTED_PACKAGES, "org.foo")
            .dependingOn(domainFileBuilder);

    addPackedDomainFromBuilder(domainFileBuilder);
    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();

    assertDeploymentSuccess(domainDeploymentListener, domainFileBuilder.getId());
    assertDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    ExecutorService executor = newSingleThreadExecutor();

    // When the app uses the plugin in order to load the exported class on non-blocking completion (within error handler),
    // then it doesn't raise any error.
    executor.submit(() -> {
      try {
        executeApplicationFlow("flowWhichSavesTheCallbackAndLoadsClassesInErrorHandler");
      } catch (Exception e) {
        fail(e.getMessage());
      }
    });

    CompletionCallback<Object, Object> completionCallback = getCompletionCallback("SavedCallback");
    ClassLoader anotherClassLoader = mock(ClassLoader.class);
    withContextClassLoader(anotherClassLoader, () -> completionCallback.error(new NullPointerException()));
  }

  private CompletionCallback<Object, Object> getCompletionCallback(String callbackName) {
    Registry registry = deploymentService.getApplications().get(0).getRegistry();
    Map<String, CompletionCallback<Object, Object>> callbacksMap =
        (Map<String, CompletionCallback<Object, Object>>) registry.lookupByName("completion.callbacks").get();
    PollingProber.probe(() -> callbacksMap.containsKey(callbackName));
    return callbacksMap.get(callbackName);
  }

  protected ApplicationFileBuilder appFileBuilder(final String artifactId) {
    return new ApplicationFileBuilder(artifactId);
  }

  private void doSynchronizedDomainDeploymentActionTest(final Action deploymentAction, final Action assertAction)
      throws Exception {
    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    final DeploymentListener domainDeploymentListener = this.domainDeploymentListener;
    final String artifactId = testArtifacts.createEmptyDomainFileBuilder().getId();

    doSynchronizedArtifactDeploymentActionTest(deploymentAction, assertAction, domainDeploymentListener, artifactId);
  }

  private Action createUndeployDummyDomainAction() {
    return () -> removeDomainAnchorFile(testArtifacts.createDummyDomainFileBuilder().getId());
  }

  private void doDomainUndeployAndVerifyAppsAreUndeployed(Action undeployAction) throws Exception {
    startDeployment();

    addPackedDomainFromBuilder(testArtifacts.createDummyDomainFileBuilder());

    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());

    addPackedAppFromBuilder(testArtifacts.createDummyDomainApp1FileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());

    addPackedAppFromBuilder(testArtifacts.createDummyDomainApp2FileBuilder());
    assertApplicationDeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());

    deploymentService.getLock().lock();
    try {
      undeployAction.perform();
    } finally {
      deploymentService.getLock().unlock();
    }

    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp1FileBuilder().getId());
    assertUndeploymentSuccess(applicationDeploymentListener, testArtifacts.createDummyDomainApp2FileBuilder().getId());
    assertUndeploymentSuccess(domainDeploymentListener, testArtifacts.createDummyDomainFileBuilder().getId());
  }

  private void doRedeployDummyDomainByChangingConfigFileWithGoodOne() throws URISyntaxException, IOException {
    doRedeployDomainByChangingConfigFile("/empty-domain-config.xml", testArtifacts.createDummyDomainFileBuilder());
  }

  private void doRedeployDomainByChangingConfigFileWithGoodOne(DomainFileBuilder domain) throws URISyntaxException, IOException {
    doRedeployDomainByChangingConfigFile("/empty-domain-config.xml", domain);
  }

  private void doRedeployDummyDomainByChangingConfigFileWithBadOne() throws URISyntaxException, IOException {
    doRedeployDomainByChangingConfigFile("/bad-domain-config.xml", testArtifacts.createDummyDomainFileBuilder());
  }

  private void doRedeployDomainByChangingConfigFile(String configFile, DomainFileBuilder domain)
      throws URISyntaxException, IOException {
    File originalConfigFile =
        new File(new File(domainsDir, domain.getDeployedPath()), getConfigFilePathWithinArtifact(domain.getConfigFile()));
    assertThat("Cannot find domain config file: " + originalConfigFile, originalConfigFile.exists(), is(true));
    URL url = getClass().getResource(configFile);
    File newConfigFile = new File(url.toURI());
    copyFile(newConfigFile, originalConfigFile);
  }

  private void doRedeployFixedDomainAfterBrokenDomain() throws Exception {
    assertDeploymentFailure(domainDeploymentListener, testArtifacts.createIncompleteDomainFileBuilder().getId());

    reset(domainDeploymentListener);

    File originalConfigFile =
        new File(domainsDir + File.separator + testArtifacts.createIncompleteDomainFileBuilder().getId(),
                 DEFAULT_CONFIGURATION_RESOURCE);
    URL url = getClass().getResource("/empty-domain-config.xml");
    File newConfigFile = new File(url.toURI());
    updateFileModifiedTime(originalConfigFile.lastModified(), newConfigFile);
    copyFile(newConfigFile, originalConfigFile);
    assertFailedDomainRedeploymentSuccess(testArtifacts.createIncompleteDomainFileBuilder().getId());

    addPackedDomainFromBuilder(testArtifacts.createEmptyDomainFileBuilder());
    assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createEmptyDomainFileBuilder().getId());

    // Check that the failed application folder is still there
    assertDomainFolderIsMaintained(testArtifacts.createIncompleteDomainFileBuilder().getId());
  }

  /**
   * After a successful deploy using the {@link DomainDeploymentTestCase#domainDeploymentListener}, this method deploys a domain
   * zip with the same name and a wrong configuration. Applications dependant of the domain should not be deleted after this
   * failure full redeploy.
   */
  private void doRedeployBrokenDomainAfterFixedDomain() throws Exception {
    final String dummyAppDescriptorId = testArtifacts.createDummyAppDescriptorFileBuilder().getId();
    assertApplicationAnchorFileExists(dummyAppDescriptorId);

    reset(domainDeploymentListener);

    DomainFileBuilder domainBundleWrongFullRedeploy = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("incomplete-domain-config.xml");


    addPackedDomainFromBuilder(domainBundleWrongFullRedeploy);

    assertDeploymentFailure(domainDeploymentListener, domainBundleWrongFullRedeploy.getId());

    assertThat(undeployLatch.await(5000, SECONDS), is(true));

    assertApplicationAnchorFileExists(dummyAppDescriptorId);
    Application dependantApplication = deploymentService.getApplications().get(0);
    assertThat(dependantApplication, is(notNullValue()));
    assertThat(dependantApplication.getStatus(), is(DESTROYED));
  }

  @Test
  public void domainWithNonExistentConfigResourceOnDeclaration() throws Exception {
    DomainFileBuilder domainBundleNonExistentConfigResource = new DomainFileBuilder("non-existent-domain-config-resource")
        .definedBy("empty-domain-config.xml").deployedWith(PROPERTY_CONFIG_RESOURCES, "mule-non-existent-config.xml");

    addPackedDomainFromBuilder(domainBundleNonExistentConfigResource);
    startDeployment();

    assertDeploymentFailure(domainDeploymentListener, domainBundleNonExistentConfigResource.getId());
  }

  private void deploysDomainAndVerifyAnchorFileIsCreatedAfterDeploymentEnds(Action deployArtifactAction) throws Exception {
    Action verifyAnchorFileDoesNotExists =
        () -> assertDomainAnchorFileDoesNotExists(testArtifacts.createWaitDomainFileBuilder().getId());
    Action verifyDeploymentSuccessful =
        () -> assertDeploymentSuccess(domainDeploymentListener, testArtifacts.createWaitDomainFileBuilder().getId());
    Action verifyAnchorFileExists = () -> assertDomainAnchorFileExists(testArtifacts.createWaitDomainFileBuilder().getId());
    deploysArtifactAndVerifyAnchorFileCreatedWhenDeploymentEnds(deployArtifactAction, verifyAnchorFileDoesNotExists,
                                                                verifyDeploymentSuccessful, verifyAnchorFileExists);
  }

  @Override
  protected void deployURI(URI uri, Properties deploymentProperties) throws IOException {
    deploymentService.deployDomain(uri, deploymentProperties);
  }

  @Override
  protected void redeployId(String id, Properties deploymentProperties) throws IOException {
    if (deploymentProperties == null) {
      deploymentService.redeployDomain(id);
    } else {
      deploymentService.redeployDomain(id, deploymentProperties);
    }
  }

  private NativeLibraryFinderFactory getNativeLibraryFinderFactory() {
    return new DefaultNativeLibraryFinderFactory();
  }

}
