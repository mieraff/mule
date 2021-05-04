/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.internal.util;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.mule.runtime.api.deployment.meta.Product.MULE;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_CLASS_PACKAGES_PROPERTY;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_RESOURCE_PROPERTY;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.EXPORTED_PACKAGES;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.EXPORTED_RESOURCES;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.MULE_LOADER_ID;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.EXTENSION_BUNDLE_TYPE;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_PLUGIN_CLASSIFIER;
import static org.mule.runtime.extension.api.loader.xml.XmlExtensionModelLoader.RESOURCE_XML;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.ARTIFACT_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.CLASSIFIER;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.GROUP_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.TYPE;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.VERSION;
import static org.mule.runtime.module.deployment.internal.AbstractApplicationDeploymentTestCase.PRIVILEGED_EXTENSION_ARTIFACT_ID;
import static org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase.BAR_POLICY_NAME;
import static org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase.BAZ_POLICY_NAME;
import static org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase.EXCEPTION_POLICY_NAME;
import static org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase.MULE_EXTENSION_CLASSIFIER;
import static org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase.MULE_POLICY_CLASSIFIER;
import static org.mule.runtime.module.deployment.internal.ClassLoaderLeakTestCase.FOO_POLICY_NAME;
import static org.mule.runtime.module.extension.api.loader.java.DefaultJavaExtensionModelLoader.JAVA_LOADER_ID;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptorBuilder;
import org.mule.runtime.api.deployment.meta.MulePluginModel;
import org.mule.runtime.api.deployment.meta.MulePluginModel.MulePluginModelBuilder;
import org.mule.runtime.api.deployment.meta.MulePolicyModel;
import org.mule.runtime.api.deployment.meta.MulePolicyModel.MulePolicyModelBuilder;
import org.mule.runtime.api.deployment.meta.Product;
import org.mule.runtime.api.util.collection.SmallMap;
import org.mule.runtime.extension.api.loader.xml.XmlExtensionModelLoader;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.DomainFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.JarFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.PolicyFileBuilder;
import org.mule.runtime.module.deployment.internal.AbstractDeploymentTestCase;
import org.mule.tck.util.CompilerUtils;
import org.mule.tck.util.CompilerUtils.ExtensionCompiler;
import org.mule.tck.util.CompilerUtils.JarCompiler;
import org.mule.tck.util.CompilerUtils.SingleClassCompiler;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

public class TestArtifactsCachingFactory {

  public static final Logger logger = getLogger(TestArtifactsCachingFactory.class);

  protected static final String MIN_MULE_VERSION = "4.0.0";

  private final Map<String, File> classes = new HashMap<>();
  private final Map<String, File> jars = new HashMap<>();
  private final Map<String, JarFileBuilder> jarsLibs = new HashMap<>();
  private final Map<String, ArtifactPluginFileBuilder> plugins = new HashMap<>();
  private final Map<String, ApplicationFileBuilder> applications = new HashMap<>();
  private final Map<String, DomainFileBuilder> domains = new HashMap<>();
  private final Map<String, PolicyFileBuilder> policies = new HashMap<>();

  public File createEchoTestClassFile() throws URISyntaxException {
    if (classes.containsKey("echoTestClassFile")) {
      return classes.get("echoTestClassFile");
    }

    File echoTestClassFile = new SingleClassCompiler().compile(getResourceFile("/org/foo/EchoTest.java"));

    classes.put("echoTestClassFile", echoTestClassFile);
    return echoTestClassFile;
  }

  public File createPluginEcho1TestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginEcho1TestClassFile")) {
      return classes.get("pluginEcho1TestClassFile");
    }

    File pluginEcho1TestClassFile =
        new SingleClassCompiler().dependingOn(createBarUtils1_0JarFile()).compile(getResourceFile("/org/foo/Plugin1Echo.java"));

    classes.put("pluginEcho1TestClassFile", pluginEcho1TestClassFile);
    return pluginEcho1TestClassFile;
  }

  public File createPluginEcho2TestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginEcho2TestClassFile")) {
      return classes.get("pluginEcho2TestClassFile");
    }

    File pluginEcho2TestClassFile =
        new SingleClassCompiler().dependingOn(createBarUtils2_0JarFile())
            .compile(getResourceFile("/org/foo/echo/Plugin2Echo.java"));

    classes.put("pluginEcho2TestClassFile", pluginEcho2TestClassFile);
    return pluginEcho2TestClassFile;
  }

  public File createPluginEcho3TestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginEcho3TestClassFile")) {
      return classes.get("pluginEcho3TestClassFile");
    }

    File pluginEcho3TestClassFile =
        new SingleClassCompiler().compile(getResourceFile("/org/foo/echo/Plugin3Echo.java"));

    classes.put("pluginEcho3TestClassFile", pluginEcho3TestClassFile);
    return pluginEcho3TestClassFile;
  }

  public File createPluginEchoSpiTestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginEchoSpiTestClassFile")) {
      return classes.get("pluginEchoSpiTestClassFile");
    }

    File pluginEchoSpiTestClassFile =
        new SingleClassCompiler().compile(getResourceFile("/org/foo/echo/PluginSpiEcho.java"));

    classes.put("pluginEchoSpiTestClassFile", pluginEchoSpiTestClassFile);
    return pluginEchoSpiTestClassFile;
  }

  public File createPluginForbiddenJavaEchoTestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginForbiddenJavaEchoTestClassFile")) {
      return classes.get("pluginForbiddenJavaEchoTestClassFile");
    }

    File pluginForbiddenJavaEchoTestClassFile =
        new SingleClassCompiler().dependingOn(createBarUtilsForbiddenJavaJarFile())
            .compile(getResourceFile("/org/foo/echo/PluginForbiddenJavaEcho.java"));

    classes.put("pluginForbiddenJavaEchoTestClassFile", pluginForbiddenJavaEchoTestClassFile);
    return pluginForbiddenJavaEchoTestClassFile;
  }

  public File createPluginForbiddenMuleContainerEchoTestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginForbiddenMuleContainerEchoTestClassFile")) {
      return classes.get("pluginForbiddenMuleContainerEchoTestClassFile");
    }

    File pluginForbiddenMuleContainerEchoTestClassFile =
        new SingleClassCompiler().dependingOn(createBarUtilsForbiddenMuleContainerJarFile())
            .compile(getResourceFile("/org/foo/echo/PluginForbiddenMuleContainerEcho.java"));

    classes.put("pluginForbiddenMuleContainerEchoTestClassFile", pluginForbiddenMuleContainerEchoTestClassFile);
    return pluginForbiddenMuleContainerEchoTestClassFile;
  }

  public File createPluginForbiddenMuleThirdPartyEchoTestClassFile() throws URISyntaxException {
    if (classes.containsKey("pluginForbiddenMuleThirdPartyEchoTestClassFile")) {
      return classes.get("pluginForbiddenMuleThirdPartyEchoTestClassFile");
    }

    File pluginForbiddenMuleThirdPartyEchoTestClassFile =
        new SingleClassCompiler().dependingOn(createBarUtilsForbiddenMuleThirdPartyJarFile())
            .compile(getResourceFile("/org/foo/echo/PluginForbiddenMuleThirdPartyEcho.java"));

    classes.put("pluginForbiddenMuleThirdPartyEchoTestClassFile", pluginForbiddenMuleThirdPartyEchoTestClassFile);
    return pluginForbiddenMuleThirdPartyEchoTestClassFile;
  }

  public File createBarUtils1ClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtils1ClassFile")) {
      return classes.get("barUtils1ClassFile");
    }

    File barUtils1ClassFile = new SingleClassCompiler().compile(getResourceFile("/org/bar1/BarUtils.java"));

    classes.put("barUtils1ClassFile", barUtils1ClassFile);
    return barUtils1ClassFile;
  }

  public File createBarUtils2ClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtils2ClassFile")) {
      return classes.get("barUtils2ClassFile");
    }

    File barUtils2ClassFile = new SingleClassCompiler().compile(getResourceFile("/org/bar2/BarUtils.java"));

    classes.put("barUtils2ClassFile", barUtils2ClassFile);
    return barUtils2ClassFile;
  }

  public File createBarUtilsForbiddenJavaClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtilsForbiddenJavaClassFile")) {
      return classes.get("barUtilsForbiddenJavaClassFile");
    }

    File barUtilsForbiddenJavaClassFile = new SingleClassCompiler().compile(getResourceFile("/java/lang/BarUtils.java"));

    classes.put("barUtilsForbiddenJavaClassFile", barUtilsForbiddenJavaClassFile);
    return barUtilsForbiddenJavaClassFile;
  }

  public File createBarUtilsForbiddenMuleContainerClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtilsForbiddenMuleContainerClassFile")) {
      return classes.get("barUtilsForbiddenMuleContainerClassFile");
    }

    File barUtilsForbiddenMuleContainerClassFile =
        new SingleClassCompiler().compile(getResourceFile("/org/mule/runtime/api/util/BarUtils.java"));

    classes.put("barUtilsForbiddenMuleContainerClassFile", barUtilsForbiddenMuleContainerClassFile);
    return barUtilsForbiddenMuleContainerClassFile;
  }

  public File createBarUtilsForbiddenMuleThirdPartyClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtilsForbiddenMuleThirdPartyClassFile")) {
      return classes.get("barUtilsForbiddenMuleThirdPartyClassFile");
    }

    File barUtilsForbiddenMuleThirdPartyClassFile =
        new SingleClassCompiler().compile(getResourceFile("/org/slf4j/BarUtils.java"));

    classes.put("barUtilsForbiddenMuleThirdPartyClassFile", barUtilsForbiddenMuleThirdPartyClassFile);
    return barUtilsForbiddenMuleThirdPartyClassFile;
  }

  public File createBarUtilsJavaxClassFile() throws URISyntaxException {
    if (classes.containsKey("barUtilsJavaxClassFile")) {
      return classes.get("barUtilsJavaxClassFile");
    }

    File barUtilsJavaxClassFile =
        new SingleClassCompiler().compile(getResourceFile("/javax/annotation/BarUtils.java"));

    classes.put("barUtilsJavaxClassFile", barUtilsJavaxClassFile);
    return barUtilsJavaxClassFile;
  }

  public File createLoadsAppResourceCallbackClassFile() throws URISyntaxException {
    if (classes.containsKey("loadsAppResourceCallbackClassFile")) {
      return classes.get("loadsAppResourceCallbackClassFile");
    }

    File loadsAppResourceCallbackClassFile =
        new SingleClassCompiler().compile(getResourceFile("/org/foo/LoadsAppResourceCallback.java"));

    classes.put("loadsAppResourceCallbackClassFile", loadsAppResourceCallbackClassFile);
    return loadsAppResourceCallbackClassFile;
  }

  public File createEchoTestJarFile() throws URISyntaxException {
    if (jars.containsKey("echoTestJarFile")) {
      return jars.get("echoTestJarFile");
    }

    File echoTestJarFile = new JarCompiler().compiling(getResourceFile("/org/foo/EchoTest.java")).compile("echo.jar");

    jars.put("echoTestJarFile", echoTestJarFile);
    return echoTestJarFile;
  }

  public File createDefaulServiceEchoJarFile() throws URISyntaxException {
    if (jars.containsKey("defaulServiceEchoJarFile")) {
      return jars.get("defaulServiceEchoJarFile");
    }

    File defaulServiceEchoJarFile = new JarCompiler()
        .compiling(getResourceFile("/org/mule/echo/DefaultEchoService.java"),
                   getResourceFile("/org/mule/echo/EchoServiceProvider.java"))
        .compile("mule-module-service-echo-default-4.0-SNAPSHOT.jar");

    jars.put("defaulServiceEchoJarFile", defaulServiceEchoJarFile);
    return defaulServiceEchoJarFile;
  }

  public File createDefaultFooServiceJarFile() throws URISyntaxException {
    if (jars.containsKey("defaultFooServiceJarFile")) {
      return jars.get("defaultFooServiceJarFile");
    }

    File defaultFooServiceJarFile = new JarCompiler().compiling(getResourceFile("/org/mule/service/foo/DefaultFooService.java"),
                                                                getResourceFile("/org/mule/service/foo/FooServiceProvider.java"))
        .dependingOn(createDefaulServiceEchoJarFile().getAbsoluteFile())
        .compile("mule-module-service-foo-default-4.0-SNAPSHOT.jar");

    jars.put("defaultFooServiceJarFile", defaultFooServiceJarFile);
    return defaultFooServiceJarFile;
  }

  public File createLoadsAppResourceCallbackJarFile() throws URISyntaxException {
    if (jars.containsKey("loadsAppResourceCallbackJarFile")) {
      return jars.get("loadsAppResourceCallbackJarFile");
    }

    File loadsAppResourceCallbackJarFile = new JarCompiler().compiling(getResourceFile("/org/foo/LoadsAppResourceCallback.java"))
        .compile("loadsAppResourceCallback.jar");

    jars.put("loadsAppResourceCallbackJarFile", loadsAppResourceCallbackJarFile);
    return loadsAppResourceCallbackJarFile;
  }

  public File createBarUtils1_0JarFile() throws URISyntaxException {
    if (jars.containsKey("barUtils1_0JarFile")) {
      return jars.get("barUtils1_0JarFile");
    }

    File barUtils1_0JarFile =
        new JarFileBuilder("barUtils1",
                           new JarCompiler().compiling(getResourceFile("/org/bar1/BarUtils.java")).compile("bar-1.0.jar"))
                               .getArtifactFile();

    jars.put("barUtils1_0JarFile", barUtils1_0JarFile);
    return barUtils1_0JarFile;
  }

  public File createBarUtils2_0JarFile() throws URISyntaxException {
    if (jars.containsKey("barUtils2_0JarFile")) {
      return jars.get("barUtils2_0JarFile");
    }

    File barUtils2_0JarFile =
        new JarCompiler().compiling(getResourceFile("/org/bar2/BarUtils.java")).compile("bar-2.0.jar");

    jars.put("barUtils2_0JarFile", barUtils2_0JarFile);
    return barUtils2_0JarFile;
  }

  public File createBarUtilsForbiddenJavaJarFile() throws URISyntaxException {
    if (jars.containsKey("barUtilsForbiddenJavaJarFile")) {
      return jars.get("barUtilsForbiddenJavaJarFile");
    }

    File barUtilsForbiddenJavaJarFile =
        new JarCompiler().compiling(getResourceFile("/java/lang/BarUtils.java")).compile("bar-javaForbidden.jar");

    jars.put("barUtilsForbiddenJavaJarFile", barUtilsForbiddenJavaJarFile);
    return barUtilsForbiddenJavaJarFile;
  }

  public File createBarUtilsForbiddenMuleContainerJarFile() throws URISyntaxException {
    if (jars.containsKey("barUtilsForbiddenMuleContainerJarFile")) {
      return jars.get("barUtilsForbiddenMuleContainerJarFile");
    }

    File barUtilsForbiddenMuleContainerJarFile =
        new JarCompiler().compiling(createBarUtilsForbiddenMuleContainerClassFile())
            .compile("bar-muleContainerForbidden.jar");

    jars.put("barUtilsForbiddenMuleContainerJarFile", barUtilsForbiddenMuleContainerJarFile);
    return barUtilsForbiddenMuleContainerJarFile;
  }

  public File createBarUtilsForbiddenMuleThirdPartyJarFile() throws URISyntaxException {
    if (jars.containsKey("barUtilsForbiddenMuleThirdPartyJarFile")) {
      return jars.get("barUtilsForbiddenMuleThirdPartyJarFile");
    }

    File barUtilsForbiddenMuleThirdPartyJarFile =
        new JarCompiler().compiling(createBarUtilsForbiddenMuleThirdPartyClassFile())
            .compile("bar-muleThirdPartyForbidden.jar");

    jars.put("barUtilsForbiddenMuleThirdPartyJarFile", barUtilsForbiddenMuleThirdPartyJarFile);
    return barUtilsForbiddenMuleThirdPartyJarFile;
  }

  public File createBarUtilsJavaxJarFile() throws URISyntaxException {
    if (jars.containsKey("barUtilsJavaxJarFile")) {
      return jars.get("barUtilsJavaxJarFile");
    }

    File barUtilsJavaxJarFile =
        new JarCompiler().compiling(createBarUtilsJavaxClassFile()).compile("bar-javax.jar");

    jars.put("barUtilsJavaxJarFile", barUtilsJavaxJarFile);
    return barUtilsJavaxJarFile;
  }

  public File createSimpleExtensionJarFile() throws URISyntaxException {
    if (jars.containsKey("simpleExtensionJarFile")) {
      return jars.get("simpleExtensionJarFile");
    }

    File simpleExtensionJarFile =
        new ExtensionCompiler().compiling(getResourceFile("/org/foo/simple/SimpleExtension.java"),
                                          getResourceFile("/org/foo/simple/SimpleOperation.java"))
            .compile("mule-module-simple-4.0-SNAPSHOT.jar", "1.0.0");

    jars.put("simpleExtensionJarFile", simpleExtensionJarFile);
    return simpleExtensionJarFile;
  }

  public File createWithErrorDeclarationExtensionJarFile() throws URISyntaxException {
    if (jars.containsKey("withErrorDeclarationJarFile")) {
      return jars.get("withErrorDeclarationJarFile");
    }

    File withErrorDeclarationJarFile =
        new ExtensionCompiler().compiling(getResourceFile("/org/foo/withErrorDeclaration/WithErrorDeclarationExtension.java"),
                                          getResourceFile("/org/foo/withErrorDeclaration/WithErrorDeclarationOperation.java"))
            .compile("mule-module-with-error-declaration-4.0-SNAPSHOT.jar", "1.0.0");

    jars.put("withErrorDeclarationJarFile", withErrorDeclarationJarFile);
    return withErrorDeclarationJarFile;
  }

  public File createHello1ExtensionV1JarFile() throws URISyntaxException {
    if (jars.containsKey("hello1ExtensionV1JarFile")) {
      return jars.get("hello1ExtensionV1JarFile");
    }

    File hello1ExtensionV1JarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/hello/HelloExtension.java"),
                   getResourceFile("/org/foo/hello/HelloOperation.java"))
        .including(getResourceFile("/org/foo/hello/registry-bootstrap.properties"),
                   "META-INF/org/mule/runtime/core/config/registry-bootstrap.properties")
        .compile("mule-module-hello-1.0.0.jar", "1.0.0");

    jars.put("hello1ExtensionV1JarFile", hello1ExtensionV1JarFile);
    return hello1ExtensionV1JarFile;
  }

  public File createHelloExtensionV2JarFile() throws URISyntaxException {
    if (jars.containsKey("helloExtensionV2JarFile")) {
      return jars.get("helloExtensionV2JarFile");
    }

    File helloExtensionV2JarFile = new ExtensionCompiler().compiling(getResourceFile("/org/foo/hello/HelloExtension.java"),
                                                                     getResourceFile("/org/foo/hello/HelloOperation.java"))
        .compile("mule-module-hello-2.0.0.jar", "2.0.0");

    jars.put("helloExtensionV2JarFile", helloExtensionV2JarFile);
    return helloExtensionV2JarFile;
  }

  public File createGoodbyeExtensionV1JarFile() throws URISyntaxException {
    if (jars.containsKey("goodbyeExtensionV1JarFile")) {
      return jars.get("goodbyeExtensionV1JarFile");
    }

    File goodbyeExtensionV1JarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/goodbye/GoodByeConfiguration.java"),
                   getResourceFile("/org/foo/goodbye/GoodByeExtension.java"))
        .compile("mule-module-goodbye-1.0.0.jar", "1.0.0");

    jars.put("goodbyeExtensionV1JarFile", goodbyeExtensionV1JarFile);
    return goodbyeExtensionV1JarFile;
  }

  public File createUsingObjectStoreJarFile() throws URISyntaxException {
    if (jars.containsKey("usingObjectStoreJarFile")) {
      return jars.get("usingObjectStoreJarFile");
    }

    File usingObjectStoreJarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/os/UsingObjectStoreExtension.java"))
        .compile("mule-module-using-object-store-1.0.0.jar", "1.0.0");

    jars.put("usingObjectStoreJarFile", usingObjectStoreJarFile);
    return usingObjectStoreJarFile;
  }

  public File createOracleExtensionJarFile() throws URISyntaxException {
    if (jars.containsKey("oracleExtensionJarFile")) {
      return jars.get("oracleExtensionJarFile");
    }

    File oracleExtensionJarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/oracle/OracleExtension.java"),
                   getResourceFile("/org/foo/oracle/OracleOperation.java"))
        .compile("mule-module-oracle-1.0.0.jar", "1.0.0");

    jars.put("oracleExtensionJarFile", oracleExtensionJarFile);
    return oracleExtensionJarFile;
  }

  public File createLoadClassExtensionJarFile() throws URISyntaxException {
    if (jars.containsKey("loadClassExtensionJarFile")) {
      return jars.get("loadClassExtensionJarFile");
    }

    File loadClassExtensionJarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/classloading/LoadClassExtension.java"),
                   getResourceFile("/org/foo/classloading/LoadClassOperation.java"))
        .including(getResourceFile("/org/foo/classloading/registry-bootstrap.properties"),
                   "META-INF/org/mule/runtime/core/config/registry-bootstrap.properties")
        .compile("mule-module-classloading-1.0.0.jar", "1.0.0");

    jars.put("loadClassExtensionJarFile", loadClassExtensionJarFile);
    return loadClassExtensionJarFile;
  }

  public File createPrivilegedExtensionV1JarFile() throws URISyntaxException {
    if (jars.containsKey("privilegedExtensionV1JarFile")) {
      return jars.get("privilegedExtensionV1JarFile");
    }

    // Application plugin artifact builders
    File privilegedExtensionV1JarFile = new ExtensionCompiler()
        .compiling(getResourceFile("/org/foo/privileged/PrivilegedExtension.java"),
                   getResourceFile("/org/foo/privileged/PrivilegedOperation.java"))
        .compile("mule-module-privileged-1.0.jar", "1.0");

    jars.put("privilegedExtensionV1JarFile", privilegedExtensionV1JarFile);
    return privilegedExtensionV1JarFile;
  }

  public JarFileBuilder createOverriderLibraryJarFile() throws URISyntaxException {
    if (jarsLibs.containsKey("overriderLibrary")) {
      return jarsLibs.get("overriderLibrary");
    }

    JarFileBuilder overriderLibrary = new JarFileBuilder("overrider-library",
                                                         new JarCompiler()
                                                             .compiling(getResourceFile("/classloading-troubleshooting/src/OverrideMe.java"))
                                                             .compile("overrider-library.jar"));

    jarsLibs.put("overriderLibrary", overriderLibrary);
    return overriderLibrary;
  }

  public JarFileBuilder createOverrider2LibraryJarFile() throws URISyntaxException {
    if (jarsLibs.containsKey("overrider2Library")) {
      return jarsLibs.get("overrider2Library");
    }

    JarFileBuilder overrider2Library = new JarFileBuilder("overrider2-library",
                                                          new JarCompiler()
                                                              .compiling(getResourceFile("/classloading-troubleshooting/src/OverrideMe2.java"))
                                                              .compile("overrider2-library.jar"));

    jarsLibs.put("overrider2Library", overrider2Library);
    return overrider2Library;
  }

  public JarFileBuilder createOverriderTestLibraryJarFile() throws URISyntaxException {
    if (jarsLibs.containsKey("overriderTestLibrary")) {
      return jarsLibs.get("overriderTestLibrary");
    }

    JarFileBuilder overriderTestLibrary = new JarFileBuilder("overrider-test-library",
                                                             new JarCompiler()
                                                                 .compiling(getResourceFile("/classloading-troubleshooting/src/test/OverrideMe.java"))
                                                                 .compile("overrider-test-library.jar"));

    jarsLibs.put("overriderTestLibrary", overriderTestLibrary);
    return overriderTestLibrary;
  }

  public ArtifactPluginFileBuilder createHelloExtensionV2PluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("helloExtensionV2Plugin")) {
      return plugins.get("helloExtensionV2Plugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("helloExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("helloExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "2.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder()
        .setId(MULE_LOADER_ID).build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.hello.HelloExtension")
        .addProperty("version", "2.0.0");
    final ArtifactPluginFileBuilder helloExtensionV2Plugin = new ArtifactPluginFileBuilder("helloExtensionPlugin-2.0.0")
        .dependingOn(new JarFileBuilder("helloExtensionV2", createHelloExtensionV2JarFile()))
        .describedBy((mulePluginModelBuilder.build()));
    plugins.put("helloExtensionV2Plugin", helloExtensionV2Plugin);
    return helloExtensionV2Plugin;
  }

  public ArtifactPluginFileBuilder createGoodbyeExtensionV1PluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("goodbyeExtensionV1Plugin")) {
      return plugins.get("goodbyeExtensionV1Plugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("goodbyeExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("goodbyeExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "2.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder()
        .setId(MULE_LOADER_ID).build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.goodbye.GoodByeExtension")
        .addProperty("version", "2.0.0");
    final ArtifactPluginFileBuilder goodbyeExtensionV1Plugin = new ArtifactPluginFileBuilder("goodbyeExtensionPlugin-1.0.0")
        .dependingOn(new JarFileBuilder("goodbyeExtensionV1", createGoodbyeExtensionV1JarFile()))
        .describedBy((mulePluginModelBuilder.build()));
    plugins.put("goodbyeExtensionV1Plugin", goodbyeExtensionV1Plugin);
    return goodbyeExtensionV1Plugin;
  }

  public ArtifactPluginFileBuilder createByeXmlPluginFileBuilder() {
    if (plugins.containsKey("byeXmlPlugin")) {
      return plugins.get("byeXmlPlugin");
    }

    final String prefixModuleName = "module-bye";
    String extensionName = "bye-extension";
    final String resources = "org/mule/module/";
    String moduleDestination = resources + prefixModuleName + ".xml";
    MulePluginModel.MulePluginModelBuilder builder =
        new MulePluginModel.MulePluginModelBuilder().setName(extensionName).setMinMuleVersion(MIN_MULE_VERSION);
    builder.withExtensionModelDescriber().setId(XmlExtensionModelLoader.DESCRIBER_ID).addProperty(RESOURCE_XML,
                                                                                                  moduleDestination);
    builder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()));
    builder.withBundleDescriptorLoader(createBundleDescriptorLoader(extensionName, MULE_EXTENSION_CLASSIFIER, MULE_LOADER_ID));
    builder.setRequiredProduct(MULE).setMinMuleVersion(MIN_MULE_VERSION);

    final ArtifactPluginFileBuilder byeXmlPlugin = new ArtifactPluginFileBuilder(extensionName)
        .containingResource("module-byeSource.xml", moduleDestination)
        .containingResource("module-using-bye-catalogSource.xml", resources + prefixModuleName + "-catalog.xml")
        .containingResource("module-bye-type-schemaSource.json", resources + "type1-schema.json")
        .containingResource("module-bye-type-schemaSource.json", resources + "inner/folder/type2-schema.json")
        .containingResource("module-bye-type-schemaSource.json", "org/mule/type3-schema.json")
        .describedBy(builder.build());
    plugins.put("byeXmlPlugin", byeXmlPlugin);
    return byeXmlPlugin;
  }

  public ArtifactPluginFileBuilder createModuleUsingByeXmlPluginFileBuilder() {
    if (plugins.containsKey("moduleUsingByeXmlPlugin")) {
      return plugins.get("moduleUsingByeXmlPlugin");
    }

    String moduleFileName = "module-using-bye.xml";
    String extensionName = "using-bye-extension";
    String moduleDestination = "org/mule/module/" + moduleFileName;

    MulePluginModel.MulePluginModelBuilder builder =
        new MulePluginModel.MulePluginModelBuilder().setName(extensionName).setMinMuleVersion(MIN_MULE_VERSION)
            .setRequiredProduct(MULE);
    builder.withExtensionModelDescriber().setId(XmlExtensionModelLoader.DESCRIBER_ID).addProperty(RESOURCE_XML,
                                                                                                  moduleDestination);
    builder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder()
        .addProperty(EXPORTED_PACKAGES, asList("org.foo")).setId(MULE_LOADER_ID)
        .build());
    builder.withBundleDescriptorLoader(createBundleDescriptorLoader(extensionName, MULE_EXTENSION_CLASSIFIER, MULE_LOADER_ID));

    final ArtifactPluginFileBuilder moduleUsingByeXmlPlugin = new ArtifactPluginFileBuilder(extensionName)
        .containingResource("module-using-byeSource.xml", moduleDestination)
        .dependingOn(createByeXmlPluginFileBuilder())
        .describedBy(builder.build());
    plugins.put("moduleUsingByeXmlPlugin", moduleUsingByeXmlPlugin);
    return moduleUsingByeXmlPlugin;
  }

  public ArtifactPluginFileBuilder createExceptionThrowingPluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("exceptionThrowingPlugin")) {
      return plugins.get("exceptionThrowingPlugin");
    }

    final String pluginName = "exceptionPlugin";

    MulePluginModel.MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModel.MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION)
        .setName(pluginName)
        .setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(pluginName,
                                                                 MULE_PLUGIN_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID,
                                                                 "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder()
        .setId(MULE_LOADER_ID)
        .addProperty(EXPORTED_RESOURCES,
                     asList("/META-INF/mule-exception.xsd",
                            "/META-INF/mule.schemas"))
        .build());

    File exceptionTestClassFile =
        new CompilerUtils.SingleClassCompiler().compile(getResourceFile("/org/exception/CustomException.java"));
    File serviceTestClassFile = new CompilerUtils.SingleClassCompiler()
        .compile(getResourceFile("/org/exception/ExceptionComponentBuildingDefinitionProvider.java"));

    ArtifactPluginFileBuilder exceptionPluginFileBuilder = new ArtifactPluginFileBuilder("exceptionPlugin")
        .containingResource("exception/META-INF/mule.schemas", "META-INF/mule.schemas")
        .containingResource("exception/META-INF/mule-exception.xsd", "META-INF/mule-exception.xsd")
        .containingResource("exception/META-INF/services/org.mule.runtime.dsl.api.component.ComponentBuildingDefinitionProvider",
                            "META-INF/services/org.mule.runtime.dsl.api.component.ComponentBuildingDefinitionProvider")
        .containingClass(exceptionTestClassFile, "org/exception/CustomException.class")
        .containingClass(serviceTestClassFile, "org/exception/ExceptionComponentBuildingDefinitionProvider.class")
        .configuredWith(EXPORTED_RESOURCE_PROPERTY, "META-INF/mule-exception.xsd,META-INF/mule.schemas")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.exception")
        .describedBy(mulePluginModelBuilder.build());

    plugins.put("exceptionThrowingPlugin", exceptionPluginFileBuilder);
    return exceptionPluginFileBuilder;

  }

  public ArtifactPluginFileBuilder createHelloExtensionV1PluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("helloExtensionV1Plugin")) {
      return plugins.get("helloExtensionV1Plugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("helloExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("helloExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.hello.HelloExtension")
        .addProperty("version", "1.0.0");
    final ArtifactPluginFileBuilder helloExtensionV1Plugin = new ArtifactPluginFileBuilder("helloExtensionPlugin-1.0.0")
        .dependingOn(new JarFileBuilder("helloExtensionV1", createHello1ExtensionV1JarFile()))
        .describedBy((mulePluginModelBuilder.build()));
    plugins.put("helloExtensionV1Plugin", helloExtensionV1Plugin);
    return helloExtensionV1Plugin;
  }

  public ArtifactPluginFileBuilder createLoadClassExtensionPluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("loadClassExtensionPlugin")) {
      return plugins.get("loadClassExtensionPlugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("loadClassExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("loadClassExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.classloading.LoadClassExtension")
        .addProperty("version", "1.0.0");
    final ArtifactPluginFileBuilder loadClassExtensionPlugin = new ArtifactPluginFileBuilder("loadClassExtensionPlugin-1.0.0")
        .dependingOn(new JarFileBuilder("loadClassExtension", createLoadClassExtensionJarFile()))
        .describedBy((mulePluginModelBuilder.build()));
    plugins.put("loadClassExtensionPlugin", loadClassExtensionPlugin);
    return loadClassExtensionPlugin;
  }

  public ArtifactPluginFileBuilder createOracleExtensionPluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("oracleExtensionPlugin")) {
      return plugins.get("oracleExtensionPlugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("oracleExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("oracleExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.oracle.OracleExtension")
        .addProperty("version", "1.0.0");
    ArtifactPluginFileBuilder oracleExtensionPlugin = new ArtifactPluginFileBuilder("oracleExtensionPlugin-1.0.0")
        .dependingOn(new JarFileBuilder("oracleExtension", createOracleExtensionJarFile()))
        .describedBy((mulePluginModelBuilder.build()));

    oracleExtensionPlugin
        .dependingOnSharedLibrary(new JarFileBuilder("oracle-driver-v1", getResourceFile("/oracle/jdbc/oracle-driver-v1.jar")));

    plugins.put("oracleExtensionPlugin", oracleExtensionPlugin);
    return oracleExtensionPlugin;
  }

  public ArtifactPluginFileBuilder createUsingObjectStorePluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("usingObjectStorePlugin")) {
      return plugins.get("usingObjectStorePlugin");
    }

    MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("usingObjectStorePlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("usingObjectStorePlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.os.UsingObjectStoreExtension")
        .addProperty("version", "1.0.0");
    final ArtifactPluginFileBuilder usingObjectStorePlugin = new ArtifactPluginFileBuilder("usingObjectStorePlugin-1.0.0")
        .dependingOn(new JarFileBuilder("usingObjectStore", createUsingObjectStoreJarFile()))
        .describedBy((mulePluginModelBuilder.build()));
    plugins.put("usingObjectStorePlugin", usingObjectStorePlugin);
    return usingObjectStorePlugin;
  }

  public ArtifactPluginFileBuilder createEchoPluginFileBuilder() throws URISyntaxException {
    if (plugins.containsKey("echoPlugin")) {
      return plugins.get("echoPlugin");
    }

    ArtifactPluginFileBuilder echoPlugin = new ArtifactPluginFileBuilder("echoPlugin")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo")
        .dependingOn(new JarFileBuilder("echoTestJar", createEchoTestJarFile()));
    plugins.put("echoPlugin", echoPlugin);
    return echoPlugin;
  }

  public ArtifactPluginFileBuilder createSingleExtensionPlugin() throws URISyntaxException {
    if (plugins.containsKey("singleExtensionPlugin")) {
      return plugins.get("singleExtensionPlugin");
    }

    MulePluginModel.MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModel.MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("simpleExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("simpleExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.simple.SimpleExtension")
        .addProperty("version", "1.0.0");
    final ArtifactPluginFileBuilder singleExtensionPlugin = new ArtifactPluginFileBuilder("simpleExtensionPlugin")
        .dependingOn(new JarFileBuilder("simpleExtension", createSimpleExtensionJarFile()))
        .describedBy(mulePluginModelBuilder.build());
    plugins.put("singleExtensionPlugin", singleExtensionPlugin);
    return singleExtensionPlugin;
  }

  public ArtifactPluginFileBuilder createEchoPluginWithLib1() throws URISyntaxException {
    if (plugins.containsKey("echoPluginWithLib1")) {
      return plugins.get("echoPluginWithLib1");
    }

    // Application plugin artifact builders
    ArtifactPluginFileBuilder echoPluginWithLib1 = new ArtifactPluginFileBuilder("echoPlugin1")
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo")
        .dependingOn(new JarFileBuilder("barUtils1", createBarUtils1_0JarFile()))
        .containingClass(createPluginEcho1TestClassFile(), "org/foo/Plugin1Echo.class");

    plugins.put("echoPluginWithLib1", echoPluginWithLib1);
    return echoPluginWithLib1;
  }

  public ArtifactPluginFileBuilder createPrivilegedExtensionPlugin() throws URISyntaxException {
    if (plugins.containsKey("privilegedExtensionPlugin")) {
      return plugins.get("privilegedExtensionPlugin");
    }

    MulePluginModel.MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModel.MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName(PRIVILEGED_EXTENSION_ARTIFACT_ID).setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(PRIVILEGED_EXTENSION_ARTIFACT_ID, MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder()
        .setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.hello.PrivilegedExtension")
        .addProperty("version", "1.0");
    final ArtifactPluginFileBuilder privilegedExtensionPlugin = new ArtifactPluginFileBuilder(PRIVILEGED_EXTENSION_ARTIFACT_ID)
        .dependingOn(new JarFileBuilder("privilegedExtensionV1", createPrivilegedExtensionV1JarFile()))
        .describedBy(mulePluginModelBuilder.build());
    plugins.put("privilegedExtensionPlugin", privilegedExtensionPlugin);
    return privilegedExtensionPlugin;
  }


  public ApplicationFileBuilder createEmptyAppFileBuilder() {
    if (applications.containsKey("emptyApp")) {
      return applications.get("emptyApp");
    }

    ApplicationFileBuilder emptyApp = new ApplicationFileBuilder("empty-app").definedBy("empty-config.xml");
    applications.put("emptyApp", emptyApp);
    return emptyApp;

  }

  public ApplicationFileBuilder createDummyAppDescriptorFileBuilder() throws URISyntaxException {
    if (applications.containsKey("dummyApp")) {
      return applications.get("dummyApp");
    }

    ApplicationFileBuilder dummyApp = new ApplicationFileBuilder("dummy-app")
        .definedBy("dummy-app-config.xml").configuredWith("myCustomProp", "someValue")
        .containingClass(createEchoTestClassFile(), "org/foo/EchoTest.class");
    applications.put("dummyApp", dummyApp);
    return dummyApp;
  }

  public ApplicationFileBuilder createIncompleteAppFileBuilder() throws URISyntaxException {
    return createIncompleteAppFileBuilder("1.0");
  }

  public ApplicationFileBuilder createIncompleteAppFileBuilder(String classloaderModelVersion) throws URISyntaxException {
    if (applications.containsKey("incompleteAppFileBuilder" + classloaderModelVersion)) {
      return applications.get("incompleteAppFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder incompleteAppFileBuilder = new ApplicationFileBuilder("incomplete-app")
        .withClassloaderModelVersion(classloaderModelVersion).definedBy("incomplete-app-config.xml");
    applications.put("incompleteAppFileBuilder" + classloaderModelVersion, incompleteAppFileBuilder);
    return incompleteAppFileBuilder;
  }

  public ApplicationFileBuilder createBrokenAppFileBuilder() throws URISyntaxException {
    return createBrokenAppFileBuilder("1.0");
  }

  public ApplicationFileBuilder createBrokenAppFileBuilder(String classloaderModelVersion) throws URISyntaxException {
    if (applications.containsKey("brokenAppFileBuilder" + classloaderModelVersion)) {
      return applications.get("brokenAppFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder brokenAppFileBuilder = new ApplicationFileBuilder("broken-app")
        .withClassloaderModelVersion(classloaderModelVersion).corrupted();
    applications.put("brokenAppFileBuilder" + classloaderModelVersion, brokenAppFileBuilder);
    return brokenAppFileBuilder;
  }

  public ApplicationFileBuilder createBrokenAppWithFunkyNameAppFileBuilder() throws URISyntaxException {
    return createBrokenAppWithFunkyNameAppFileBuilder("1.0");
  }

  public ApplicationFileBuilder createBrokenAppWithFunkyNameAppFileBuilder(String classloaderModelVersion)
      throws URISyntaxException {
    if (applications.containsKey("brokenAppWithFunkyNameAppFileBuilder" + classloaderModelVersion)) {
      return applications.get("brokenAppWithFunkyNameAppFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder brokenAppWithFunkyNameAppFileBuilder =
        new ApplicationFileBuilder("broken-app+", createBrokenAppFileBuilder(classloaderModelVersion))
            .withClassloaderModelVersion(classloaderModelVersion).corrupted();
    applications.put("brokenAppWithFunkyNameAppFileBuilder" + classloaderModelVersion, brokenAppWithFunkyNameAppFileBuilder);
    return brokenAppWithFunkyNameAppFileBuilder;
  }

  public ApplicationFileBuilder createWaitAppFileBuilder() throws URISyntaxException {
    return createWaitAppFileBuilder("1.0");
  }

  public ApplicationFileBuilder createWaitAppFileBuilder(String classloaderModelVersion) throws URISyntaxException {
    if (applications.containsKey("waitAppFileBuilder" + classloaderModelVersion)) {
      return applications.get("waitAppFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder waitAppFileBuilder = new ApplicationFileBuilder("wait-app")
        .withClassloaderModelVersion(classloaderModelVersion)
        .definedBy("wait-app-config.xml");
    applications.put("waitAppFileBuilder" + classloaderModelVersion, waitAppFileBuilder);
    return waitAppFileBuilder;
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithPropsFileBuilder() throws URISyntaxException {
    return createDummyAppDescriptorWithPropsFileBuilder("1.0");
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithPropsFileBuilder(String classloaderModelVersion)
      throws URISyntaxException {
    if (applications.containsKey("dummyAppDescriptorWithPropsFileBuilder" + classloaderModelVersion)) {
      return applications.get("dummyAppDescriptorWithPropsFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder dummyAppDescriptorWithPropsFileBuilder = new ApplicationFileBuilder("dummy-app-with-props")
        .withClassloaderModelVersion(classloaderModelVersion)
        .definedBy("dummy-app-with-props-config.xml")
        .containingClass(createEchoTestClassFile(), "org/foo/EchoTest.class");

    applications.put("dummyAppDescriptorWithPropsFileBuilder" + classloaderModelVersion, dummyAppDescriptorWithPropsFileBuilder);
    return dummyAppDescriptorWithPropsFileBuilder;
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithPropsDependencyFileBuilder() throws URISyntaxException {
    return createDummyAppDescriptorWithPropsDependencyFileBuilder("1.0");
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithPropsDependencyFileBuilder(String classloaderModelVersion)
      throws URISyntaxException {
    if (applications.containsKey("dummyAppDescriptorWithPropsDependencyFileBuilder" + classloaderModelVersion)) {
      return applications.get("dummyAppDescriptorWithPropsDependencyFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder dummyAppDescriptorWithPropsDependencyFileBuilder =
        new ApplicationFileBuilder("dummy-app-with-props-dependencies")
            .withClassloaderModelVersion(classloaderModelVersion)
            .withMinMuleVersion("4.3.0") // MULE-19038
            .definedBy("dummy-app-with-props-dependencies-config.xml");

    applications.put("dummyAppDescriptorWithPropsDependencyFileBuilder" + classloaderModelVersion,
                     dummyAppDescriptorWithPropsDependencyFileBuilder);
    return dummyAppDescriptorWithPropsDependencyFileBuilder;
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithStoppedFlowFileBuilder() throws URISyntaxException {
    return createDummyAppDescriptorWithStoppedFlowFileBuilder("1.0");
  }

  public ApplicationFileBuilder createDummyAppDescriptorWithStoppedFlowFileBuilder(String classloaderModelVersion)
      throws URISyntaxException {
    if (applications.containsKey("dummyAppDescriptorWithStoppedFlowFileBuilder" + classloaderModelVersion)) {
      return applications.get("dummyAppDescriptorWithStoppedFlowFileBuilder" + classloaderModelVersion);
    }

    ApplicationFileBuilder dummyAppDescriptorWithStoppedFlowFileBuilder =
        new ApplicationFileBuilder("dummy-app-with-stopped-flow-config")
            .withClassloaderModelVersion(classloaderModelVersion)
            .withMinMuleVersion("4.3.0") // MULE-19127
            .definedBy("dummy-app-with-stopped-flow-config.xml")
            .containingClass(createEchoTestClassFile(),
                             "org/foo/EchoTest.class");

    applications.put("dummyAppDescriptorWithStoppedFlowFileBuilder" + classloaderModelVersion,
                     dummyAppDescriptorWithStoppedFlowFileBuilder);
    return dummyAppDescriptorWithStoppedFlowFileBuilder;
  }

  public DomainFileBuilder createDummyDomainFileBuilder() {
    if (domains.containsKey("dummyDomain")) {
      return domains.get("dummyDomain");
    }

    DomainFileBuilder dummyDomain =
        new DomainFileBuilder("dummy-domain").definedBy("empty-domain-config.xml");
    domains.put("dummyDomain", dummyDomain);
    return dummyDomain;
  }

  public DomainFileBuilder createExceptionThrowingPluginImportingDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("exceptionThrowingPluginImportingDomain")) {
      return domains.get("exceptionThrowingPluginImportingDomain");
    }

    DomainFileBuilder exceptionThrowingPluginImportingDomain =
        new DomainFileBuilder("exception-throwing-plugin-importing-domain").definedBy("empty-domain-config.xml")
            .dependingOn(createExceptionThrowingPluginFileBuilder());
    domains.put("exceptionThrowingPluginImportingDomain", exceptionThrowingPluginImportingDomain);
    return exceptionThrowingPluginImportingDomain;
  }

  public DomainFileBuilder createBrokenDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("brokenDomain")) {
      return domains.get("brokenDomain");
    }

    DomainFileBuilder brokenDomain = new DomainFileBuilder("brokenDomain").corrupted();
    domains.put("brokenDomain", brokenDomain);
    return brokenDomain;
  }

  public DomainFileBuilder createEmptyDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("emptyDomain")) {
      return domains.get("emptyDomain");
    }

    DomainFileBuilder emptyDomain = new DomainFileBuilder("empty-domain").definedBy("empty-domain-config.xml");
    domains.put("emptyDomain", emptyDomain);
    return emptyDomain;
  }

  public DomainFileBuilder createWaitDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("waitDomain")) {
      return domains.get("waitDomain");
    }

    DomainFileBuilder waitDomain = new DomainFileBuilder("wait-domain").definedBy("wait-domain-config.xml");
    domains.put("waitDomain", waitDomain);
    return waitDomain;
  }

  public DomainFileBuilder createIncompleteDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("incompleteDomain")) {
      return domains.get("incompleteDomain");
    }

    DomainFileBuilder incompleteDomain =
        new DomainFileBuilder("incompleteDomain").definedBy("incomplete-domain-config.xml");
    domains.put("incompleteDomain", incompleteDomain);
    return incompleteDomain;
  }

  public DomainFileBuilder createInvalidDomainBundleFileBuilder() throws URISyntaxException {
    if (domains.containsKey("invalidDomainBundle")) {
      return domains.get("invalidDomainBundle");
    }

    DomainFileBuilder invalidDomainBundle =
        new DomainFileBuilder("invalid-domain-bundle").definedBy("incomplete-domain-config.xml");
    domains.put("invalidDomainBundle", invalidDomainBundle);
    return invalidDomainBundle;
  }

  public DomainFileBuilder createDummyDomainBundleFileBuilder() throws URISyntaxException {
    if (domains.containsKey("dummyDomainBundle")) {
      return domains.get("dummyDomainBundle");
    }

    DomainFileBuilder dummyDomainBundle = new DomainFileBuilder("dummy-domain-bundle")
        .definedBy("empty-domain-config.xml");
    domains.put("dummyDomainBundle", dummyDomainBundle);
    return dummyDomainBundle;
  }

  public DomainFileBuilder createDummyUndeployableDomainFileBuilderFileBuilder() throws URISyntaxException {
    if (domains.containsKey("dummyUndeployableDomain")) {
      return domains.get("dummyUndeployableDomain");
    }

    DomainFileBuilder dummyUndeployableDomain =
        new DomainFileBuilder("dummy-undeployable-domain")
            .definedBy("empty-domain-config.xml").deployedWith("redeployment.enabled", "false");
    domains.put("dummyUndeployableDomain", dummyUndeployableDomain);
    return dummyUndeployableDomain;
  }

  public DomainFileBuilder createSharedDomainFileBuilder() throws URISyntaxException {
    if (domains.containsKey("sharedDomain")) {
      return domains.get("sharedDomain");
    }

    DomainFileBuilder sharedDomain =
        new DomainFileBuilder("shared-domain").definedBy("shared-domain-config.xml");
    domains.put("sharedDomain", sharedDomain);
    return sharedDomain;
  }

  public DomainFileBuilder createDomainWithPropsFileBuilder() throws URISyntaxException {
    if (domains.containsKey("domainWithProps")) {
      return domains.get("domainWithProps");
    }

    DomainFileBuilder domainWithProps =
        new DomainFileBuilder("domain-with-props").definedBy("domain-with-props-config.xml");
    domains.put("domainWithProps", domainWithProps);
    return domainWithProps;
  }

  public DomainFileBuilder createEmptyDomain100FileBuilder() throws URISyntaxException {
    if (domains.containsKey("emptyDomain100")) {
      return domains.get("emptyDomain100");
    }

    DomainFileBuilder emptyDomain100 =
        new DomainFileBuilder("empty-domain").definedBy("empty-domain-config.xml").withVersion("1.0.0");
    domains.put("emptyDomain100", emptyDomain100);
    return emptyDomain100;
  }

  public DomainFileBuilder createEmptyDomain101FileBuilder() throws URISyntaxException {
    if (domains.containsKey("emptyDomain101")) {
      return domains.get("emptyDomain101");
    }

    DomainFileBuilder emptyDomain101 =
        new DomainFileBuilder("empty-domain").definedBy("empty-domain-config.xml").withVersion("1.0.1");
    domains.put("emptyDomain101", emptyDomain101);
    return emptyDomain101;
  }

  public ApplicationFileBuilder createDummyDomainApp1FileBuilder() throws URISyntaxException {
    if (applications.containsKey("dummyDomainApp1")) {
      return applications.get("dummyDomainApp1");
    }

    ApplicationFileBuilder dummyDomainApp1 =
        new ApplicationFileBuilder("dummy-domain-app1").definedBy("empty-config.xml")
            .dependingOn(createDummyDomainFileBuilder());
    applications.put("dummyDomainApp1", dummyDomainApp1);
    return dummyDomainApp1;
  }

  public ApplicationFileBuilder createDummyDomainApp2FileBuilder() throws URISyntaxException {
    if (applications.containsKey("dummyDomainApp2")) {
      return applications.get("dummyDomainApp2");
    }

    ApplicationFileBuilder dummyDomainApp2 =
        new ApplicationFileBuilder("dummy-domain-app2").definedBy("empty-config.xml")
            .dependingOn(createDummyDomainFileBuilder());
    applications.put("dummyDomainApp2", dummyDomainApp2);
    return dummyDomainApp2;
  }

  public ApplicationFileBuilder createDummyDomainApp3FileBuilder() throws URISyntaxException {
    if (applications.containsKey("dummyDomainApp3")) {
      return applications.get("dummyDomainApp3");
    }

    ApplicationFileBuilder dummyDomainApp3 =
        new ApplicationFileBuilder("dummy-domain-app3")
            .definedBy("bad-app-config.xml").dependingOn(createDummyDomainFileBuilder());
    applications.put("dummyDomainApp3", dummyDomainApp3);
    return dummyDomainApp3;
  }

  public ApplicationFileBuilder createSharedAAppFileBuilder() throws URISyntaxException {
    if (applications.containsKey("sharedAApp")) {
      return applications.get("sharedAApp");
    }

    ApplicationFileBuilder sharedAApp = new ApplicationFileBuilder("shared-app-a")
        .definedBy("shared-a-app-config.xml").dependingOn(createSharedDomainFileBuilder());
    applications.put("sharedAApp", sharedAApp);
    return sharedAApp;
  }

  public ApplicationFileBuilder createSharedBAppFileBuilder() throws URISyntaxException {
    if (applications.containsKey("sharedBApp")) {
      return applications.get("sharedBApp");
    }

    ApplicationFileBuilder sharedBApp = new ApplicationFileBuilder("shared-app-b")
        .definedBy("shared-b-app-config.xml").dependingOn(createSharedDomainFileBuilder());
    applications.put("sharedBApp", sharedBApp);
    return sharedBApp;
  }

  public PolicyFileBuilder createFooPolicyFileBuilder() throws URISyntaxException {
    if (policies.containsKey("fooPolicy")) {
      return policies.get("fooPolicy");
    }

    PolicyFileBuilder fooPolicy =
        new PolicyFileBuilder(FOO_POLICY_NAME).describedBy(new MulePolicyModel.MulePolicyModelBuilder()
            .setMinMuleVersion(MIN_MULE_VERSION)
            .setName(FOO_POLICY_NAME)
            .setRequiredProduct(MULE)
            .withBundleDescriptorLoader(createBundleDescriptorLoader(FOO_POLICY_NAME,
                                                                     MULE_POLICY_CLASSIFIER,
                                                                     PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
            .withClassLoaderModelDescriptorLoader(
                                                  new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()))
            .build())
            .dependingOn(createSingleExtensionPlugin());
    policies.put("fooPolicy", fooPolicy);
    return fooPolicy;
  }

  public PolicyFileBuilder createBarPolicyFileBuilder() {
    if (policies.containsKey("barPolicy")) {
      return policies.get("barPolicy");
    }

    final PolicyFileBuilder barPolicy = new PolicyFileBuilder(BAR_POLICY_NAME).describedBy(new MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION)
        .setName(BAR_POLICY_NAME)
        .setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(BAR_POLICY_NAME,
                                                                 MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
        .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()))
        .build());
    policies.put("barPolicy", barPolicy);
    return barPolicy;
  }

  public PolicyFileBuilder createPolicyUsingAppPluginFileBuilder() {
    if (policies.containsKey("policyUsingAppPlugin")) {
      return policies.get("policyUsingAppPlugin");
    }

    final PolicyFileBuilder policyUsingAppPlugin = new PolicyFileBuilder(BAR_POLICY_NAME).describedBy(new MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION)
        .setName(BAR_POLICY_NAME)
        .setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(BAR_POLICY_NAME,
                                                                 MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
        .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()))
        .build());
    policies.put("policyUsingAppPlugin", policyUsingAppPlugin);
    return policyUsingAppPlugin;
  }

  public PolicyFileBuilder createPolicyIncludingHelloPluginV2FileBuilder() throws URISyntaxException {
    if (policies.containsKey("policyIncludingHelloPluginV2")) {
      return policies.get("policyIncludingHelloPluginV2");
    }

    MulePolicyModelBuilder mulePolicyModelBuilder = new MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName(BAZ_POLICY_NAME).setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(BAZ_POLICY_NAME, MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID));
    mulePolicyModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()));
    final PolicyFileBuilder policyIncludingHelloPluginV2 = new PolicyFileBuilder(BAZ_POLICY_NAME)
        .describedBy(mulePolicyModelBuilder.build())
        .dependingOn(createHelloExtensionV2PluginFileBuilder());
    policies.put("policyIncludingHelloPluginV2", policyIncludingHelloPluginV2);
    return policyIncludingHelloPluginV2;
  }

  public PolicyFileBuilder createExceptionThrowingPluginImportingPolicyFileBuilder() throws URISyntaxException {
    if (policies.containsKey("exceptionThrowingPluginImportingPolicy")) {
      return policies.get("exceptionThrowingPluginImportingPolicy");
    }

    final PolicyFileBuilder exceptionThrowingPluginImportingPolicy = new PolicyFileBuilder(EXCEPTION_POLICY_NAME)
        .describedBy(new MulePolicyModelBuilder()
            .setMinMuleVersion(MIN_MULE_VERSION)
            .setName(EXCEPTION_POLICY_NAME)
            .setRequiredProduct(MULE)
            .withBundleDescriptorLoader(
                                        createBundleDescriptorLoader(EXCEPTION_POLICY_NAME,
                                                                     MULE_POLICY_CLASSIFIER,
                                                                     PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
            .withClassLoaderModelDescriptorLoader(
                                                  new MuleArtifactLoaderDescriptor(MULE_LOADER_ID,
                                                                                   emptyMap()))
            .build())
        .dependingOn(createExceptionThrowingPluginFileBuilder());
    policies.put("exceptionThrowingPluginImportingPolicy", exceptionThrowingPluginImportingPolicy);
    return exceptionThrowingPluginImportingPolicy;
  }

  public PolicyFileBuilder createPolicyIncludingPluginFileBuilder() throws URISyntaxException {
    if (policies.containsKey("policyIncludingPlugin")) {
      return policies.get("policyIncludingPlugin");
    }

    MulePolicyModelBuilder mulePolicyModelBuilder = new MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName(BAZ_POLICY_NAME)
        .setRequiredProduct(Product.MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(BAZ_POLICY_NAME, MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
        .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()));
    final PolicyFileBuilder policyIncludingPlugin = new PolicyFileBuilder(BAZ_POLICY_NAME).describedBy(mulePolicyModelBuilder
        .build()).dependingOn(createHelloExtensionV1PluginFileBuilder());
    policies.put("policyIncludingPlugin", policyIncludingPlugin);
    return policyIncludingPlugin;
  }

  public PolicyFileBuilder createPolicyIncludingDependantPluginFileBuilder() throws URISyntaxException {
    if (policies.containsKey("policyIncludingDependantPlugin")) {
      return policies.get("policyIncludingDependantPlugin");
    }

    MulePolicyModelBuilder mulePolicyModelBuilder = new MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName(BAZ_POLICY_NAME)
        .setRequiredProduct(Product.MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(BAZ_POLICY_NAME, MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
        .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()));
    ArtifactPluginFileBuilder dependantPlugin =
        new ArtifactPluginFileBuilder("dependantPlugin").configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo.echo")
            .containingClass(new SingleClassCompiler().compile(getResourceFile("/org/foo/echo/Plugin3Echo.java")),
                             "org/foo/echo/Plugin3Echo.class")
            .dependingOn(createHelloExtensionV1PluginFileBuilder());

    final PolicyFileBuilder policyIncludingDependantPlugin =
        new PolicyFileBuilder(BAZ_POLICY_NAME).describedBy(mulePolicyModelBuilder
            .build()).dependingOn(dependantPlugin);
    policies.put("policyIncludingDependantPlugin", policyIncludingDependantPlugin);
    return policyIncludingDependantPlugin;
  }

  ////////////////////////////////////////////
  ////////////////////////////////////////////

  public static MuleArtifactLoaderDescriptor createBundleDescriptorLoader(String artifactId, String classifier,
                                                                          String bundleDescriptorLoaderId) {
    return createBundleDescriptorLoader(artifactId, classifier, bundleDescriptorLoaderId, "1.0.0");
  }

  public static MuleArtifactLoaderDescriptor createBundleDescriptorLoader(String artifactId, String classifier,
                                                                          String bundleDescriptorLoaderId, String version) {
    Map<String, Object> attributes = SmallMap.of(VERSION, version,
                                                 GROUP_ID, "org.mule.test",
                                                 ARTIFACT_ID, artifactId,
                                                 CLASSIFIER, classifier,
                                                 TYPE, EXTENSION_BUNDLE_TYPE);

    return new MuleArtifactLoaderDescriptor(bundleDescriptorLoaderId, attributes);
  }

  protected static File getResourceFile(String resource) throws URISyntaxException {
    return new File(AbstractDeploymentTestCase.class.getResource(resource).toURI());
  }

}
