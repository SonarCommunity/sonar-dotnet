/*
 * Sonar .NET Plugin :: NDeps
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.csharp.ndeps.results;

import com.google.common.base.Predicate;

import com.google.common.base.Predicate;

import com.google.common.collect.Iterables;

import com.google.common.collect.Collections2;

import org.sonar.api.rules.ActiveRule;

import org.sonar.api.profiles.RulesProfile;

import org.sonar.api.rules.Violation;

import org.sonar.api.rules.Rule;

import org.sonar.api.rules.RuleFinder;

import org.sonar.plugins.csharp.ndeps.NDepsConstants;

import org.sonar.api.rules.RuleQuery;

import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.measures.RangeDistributionBuilder;

import com.google.common.collect.Sets;

import org.sonar.plugins.dotnet.api.utils.ResourceHelper;

import com.google.common.collect.Lists;

import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;

import com.google.common.base.Joiner;

import org.sonar.plugins.dotnet.api.DotNetConfiguration;

import org.sonar.plugins.dotnet.api.DotNetConstants;

import org.sonar.plugins.dotnet.api.microsoft.MicrosoftWindowsEnvironment;
import org.sonar.plugins.dotnet.api.microsoft.VisualStudioProject;
import org.sonar.plugins.dotnet.api.microsoft.VisualStudioSolution;

import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.SMEvent;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.design.Dependency;
import org.sonar.api.resources.Library;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.utils.SonarException;
import org.sonar.plugins.dotnet.api.DotNetResourceBridge;
import org.sonar.plugins.dotnet.api.DotNetResourceBridges;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class NDepsResultParser implements BatchExtension {

  private static final Logger LOG = LoggerFactory.getLogger(NDepsResultParser.class);

  private static final Number[] RFC_DISTRIB_BOTTOM_LIMITS = {0, 5, 10, 20, 30, 50, 90, 150};

  private static final Number[] LCOM4_DISTRIB_BOTTOM_LIMITS = {2, 3, 4, 5, 10};

  private final DotNetResourceBridge resourceBridge;
  private final SensorContext context;
  private final Project project;
  private final ResourceHelper resourceHelper;
  private final VisualStudioSolution vsSolution;
  private final VisualStudioProject vsProject;
  private final RulesProfile rulesProfile;
  private final boolean keyGenerationSafeMode;

  public NDepsResultParser(MicrosoftWindowsEnvironment env, DotNetResourceBridges bridges, Project project, SensorContext context, DotNetConfiguration configuration, ResourceHelper resourceHelper, RulesProfile rulesProfile) {
    this.resourceBridge = bridges.getBridge(project.getLanguageKey());
    this.resourceHelper = resourceHelper;
    this.context = context;
    this.project = project;
    vsSolution = env.getCurrentSolution();
    if (vsSolution == null) {
      // not a C# project
      vsProject = null;
    } else {
      vsProject = vsSolution.getProjectFromSonarProject(project);
    }
    this.rulesProfile = rulesProfile;
    keyGenerationSafeMode = "safe".equalsIgnoreCase(configuration.getString(DotNetConstants.KEY_GENERATION_STRATEGY_KEY));
  }

  public void parse(String scope, File file) {
    SMInputFactory inputFactory = new SMInputFactory(XMLInputFactory.newInstance());
    try {
      SMHierarchicCursor cursor = inputFactory.rootElementCursor(file);
      SMInputCursor assemblyCursor = cursor.advance().descendantElementCursor("Assembly");
      parseAssemblyBlocs(scope, assemblyCursor);
      cursor.getStreamReader().closeCompletely();
    } catch (XMLStreamException e) {
      throw new SonarException("Error while reading NDeps result file: " + file.getAbsolutePath(), e);
    }
  }

  private void parseAssemblyBlocs(String scope, SMInputCursor cursor) throws XMLStreamException {
    // Cursor is on <Assembly>
    while (cursor.getNext() != null) {
      if (cursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {

        String assemblyName = cursor.getAttrValue("name");
        String assemblyVersion = cursor.getAttrValue("version");

        final Resource<?> from;
        VisualStudioProject vsProjectFromReport = vsSolution.getProject(assemblyName);
        if (vsProject.equals(vsProjectFromReport)) {
          // direct dependencies of current project
          from = project;
        } else if (vsProjectFromReport == null) {
          // indirect dependencies
          from = getResource(assemblyName, assemblyVersion);
        } else {
          // dependencies of other projects from the same solution
          // (covered by the analysis of these same projects)
          from = null;
        }

        if (from != null) {
          SMInputCursor childCursor = cursor.childElementCursor();
          while (childCursor.getNext() != null) {
            if ("References".equals(childCursor.getLocalName())) {
              SMInputCursor referenceCursor = childCursor.childElementCursor();
              parseReferenceBlock(scope, referenceCursor, from);
            }
            else if ("TypeReferences".equals(childCursor.getLocalName())) {
              SMInputCursor typeReferenceCursor = childCursor.childElementCursor();
              parseTypeReferenceBlock(typeReferenceCursor);
            }
            else if ("Design".equals(childCursor.getLocalName())) {
              SMInputCursor typeReferenceCursor = childCursor.childElementCursor();
              parseDesignBlock(typeReferenceCursor);
            }
          }
        }
      }
    }
  }

  private void parseReferenceBlock(String scope, SMInputCursor cursor, Resource<?> from) throws XMLStreamException {
    // Cursor is on <Reference>
    while (cursor.getNext() != null) {
      if (cursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {
        String referenceName = cursor.getAttrValue("name");
        String referenceVersion = cursor.getAttrValue("version");

        Resource<?> to = getResource(referenceName, referenceVersion);

        // keep the dependency in cache
        Dependency dependency = new Dependency(from, to);
        dependency.setUsage(scope);
        dependency.setWeight(1);
        context.saveDependency(dependency);

        LOG.debug("Saving dependency from {} to {}", from.getName(), to.getName());
      }
    }
  }

  private void parseTypeReferenceBlock(SMInputCursor cursor) throws XMLStreamException {
    // Cursor is on <From> or <Violation>
    while (cursor.getNext() != null) {
      if (cursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {
        String elementName = cursor.getLocalName();
        if ("From".equals(elementName)) {
          //String fromType = cursor.getAttrValue("fullname");
          Resource<?> fromResource = findResource(cursor, "path", "fullname"); //resourceBridge.getFromTypeName(fromType);

          SMInputCursor toCursor = cursor.childElementCursor();
          while (toCursor.getNext() != null) {
            if (toCursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {
              String toType = toCursor.getAttrValue("fullname");

              //Resource<?> fromResource = resourceBridge.getFromTypeName(fromType);
              Resource<?> toResource = resourceBridge.getFromTypeName(toType);

              // check if the source is not filtered
              if (fromResource != null && toResource != null) {
                // get the parent folder
                Resource<?> fromParentFolderResource = (Resource<?>) fromResource.getParent();
                Resource<?> toParentFolderResource = (Resource<?>) toResource.getParent();

                // find the folder to folder dependency
                Dependency folderDependency = findFolderDependency(fromParentFolderResource, toParentFolderResource);

                // save the file to file dependency
                Dependency fileDependency = new Dependency(fromResource, toResource);
                fileDependency.setParent(folderDependency);
                fileDependency.setUsage("USES");
                fileDependency.setWeight(1);
                context.saveDependency(fileDependency);
                LOG.debug("Saving dependency from {} to {}", fromResource.getName(), toResource.getName());
              }
            }
          }
        } else if ("Violation".equals(elementName)) {
          String subjectType = cursor.getAttrValue("fullname");
          final String fromPattern = cursor.getAttrValue("fromPattern");
          final String toPattern = cursor.getAttrValue("toPattern");
          String dependency = cursor.getAttrValue("dependency");
          Resource<?> resource = findResource(cursor, "path", "fullname");

          List<ActiveRule> rules
            = rulesProfile.getActiveRulesByRepository(NDepsConstants.REPOSITORY_KEY + "-" + project.getLanguageKey());
          ActiveRule rule = Iterables.find(rules, new Predicate<ActiveRule>() {
            public boolean apply(ActiveRule rule) {
              return StringUtils.equals(fromPattern, rule.getParameter("fromClasses")) && StringUtils.equals(toPattern, rule.getParameter("toClasses"));
            }});

          Violation violation
            = Violation.create(rule, resource);
          violation.setMessage("Type " + subjectType + " has a reference to type " + dependency);
          violation.setSeverity(rule.getSeverity());
          context.saveViolation(violation);
        }
      }
    }
  }

  private Resource<?> findResource(SMInputCursor cursor, String pathAttr, String typeAttr) throws XMLStreamException {
    final Resource<?> resource;
    String sourcePath = cursor.getAttrValue(pathAttr);
    if (StringUtils.isEmpty(sourcePath)) {
      String type = cursor.getAttrValue(typeAttr);
      Resource<?> resourceFromType = resourceBridge.getFromTypeName(type);
      if (resourceFromType != null && resourceHelper.isResourceInProject(resourceFromType, project)) {
          resource = resourceFromType;
      } else {
        resource = null;
      }
    } else {
      java.io.File sourceFile = new java.io.File(sourcePath).getAbsoluteFile();
      VisualStudioProject currentVsProject = vsSolution.getProject(sourceFile);
      if (vsProject.equals(currentVsProject)) {
        if (vsProject.isTest()) {
          resource = org.sonar.api.resources.File.fromIOFile(sourceFile, project.getFileSystem().getTestDirs());
        } else {
          resource = org.sonar.api.resources.File.fromIOFile(sourceFile, project);
        }
      } else {
        resource = null;
      }
    }
    LOG.debug("Found resource {}", resource);
    return resource;
  }

  private void parseDesignBlock(SMInputCursor cursor) throws XMLStreamException {
    // we need to keep track of the resources
    // already treated since several Type blocks
    // in the report may be related to the same
    // source file
    Set<String> savedResourceIds = Sets.newHashSet();

    // Cursor is on <type>
    while (cursor.getNext() != null) {
      if (cursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {
        String type = cursor.getAttrValue("fullName");
        LOG.debug("Parsing design data for type {}", type);

        Resource<?> resource = findResource(cursor, "source", "fullName");
        if (resource == null || savedResourceIds.contains(resource.getKey())) {
          continue;
        }
        savedResourceIds.add(resource.getKey());

        double rfc = new Integer(cursor.getAttrValue("rfc")).doubleValue();
        double dit = new Integer(cursor.getAttrValue("dit")).doubleValue();

        String mergedTypes = cursor.getAttrValue("mergedTypes");
        if (StringUtils.isNotEmpty(mergedTypes)) {
          Measure measure = new Measure(NDepsConstants.MERGED_TYPES, mergedTypes);
          context.saveMeasure(resource, measure);
        }

        Joiner joiner = Joiner.on(",");
        List<String> blockList = Lists.newArrayList();
        SMInputCursor blockCursor = cursor.childElementCursor();
        while (blockCursor.getNext() != null) {
          if (blockCursor.getCurrEvent().equals(SMEvent.START_ELEMENT)) {
            SMInputCursor eltCursor = blockCursor.childElementCursor();
            List<String> eltList = Lists.newArrayList();
            while (eltCursor.getNext() != null) {
              String eltType = "Field".equals(eltCursor.getAttrValue("type")) ? "FLD" : "MET";
              String eltName = eltCursor.getAttrValue("name");
              eltList.add("{\"q\":\""+ eltType +"\",\"n\":\""+ eltName +"\"}");
            }
            String block = "[" + joiner.join(eltList) + "]";
            blockList.add(block);
          }
        }

        context.saveMeasure(resource, CoreMetrics.RFC, rfc);
        RangeDistributionBuilder rfcDistribution = new RangeDistributionBuilder(CoreMetrics.RFC_DISTRIBUTION, RFC_DISTRIB_BOTTOM_LIMITS);
        rfcDistribution.add(rfc);
        context.saveMeasure(resource, rfcDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));

        context.saveMeasure(resource, CoreMetrics.DEPTH_IN_TREE, dit);

        final int lcom4;
        final String lcom4Json;
        if (blockList.size() == 0) {
          lcom4 = 1;
          lcom4Json = "[]";
        } else {
          lcom4 = blockList.size();
          lcom4Json = "[" + joiner.join(blockList) + "]";
        }

        double lcom4Value = new Integer(lcom4).doubleValue();
        context.saveMeasure(resource, CoreMetrics.LCOM4, lcom4Value);
        RangeDistributionBuilder lcom4Distribution = new RangeDistributionBuilder(CoreMetrics.LCOM4_DISTRIBUTION, LCOM4_DISTRIB_BOTTOM_LIMITS);
        lcom4Distribution.add(lcom4Value);
        context.saveMeasure(resource, lcom4Distribution.build().setPersistenceMode(PersistenceMode.MEMORY));

        Measure measure = new Measure(CoreMetrics.LCOM4_BLOCKS, lcom4Json);
        context.saveMeasure(resource, measure);

      }
    }
  }

  private Dependency findFolderDependency(Resource<?> fromParentFolderResource, Resource<?> toParentFolderResource) {
    Dependency folderDependency = findDependency(fromParentFolderResource, toParentFolderResource);
    if (folderDependency == null) {
      folderDependency = new Dependency(fromParentFolderResource, toParentFolderResource);
      folderDependency.setUsage("USES");
    }

    // save it
    folderDependency.setWeight(folderDependency.getWeight() + 1);
    context.saveDependency(folderDependency);
    LOG.debug("Saving dependency from {} to {}", fromParentFolderResource.getName(), toParentFolderResource.getName());
    return folderDependency;
  }

  private Dependency findDependency(Resource<?> from, Resource<?> to) {
    for (Dependency d : context.getDependencies()) {
      if (d.getFrom().equals(from) && d.getTo().equals(to)) {
        return d;
      }
    }

    return null;
  }

  private Resource<?> getProjectFromKey(Project parentProject, String projectKey) {
    for (Project subProject : parentProject.getModules()) {
      if (subProject.getKey().equals(projectKey)) {
        return subProject;
      }
    }

    return null;
  }

  private Resource<?> getResource(String name, String version) {
    // try to find the project
    VisualStudioProject linkedProject = vsSolution.getProject(name);

    Resource<?> result;

    // if not found in the solution, get the binary
    if (linkedProject == null) {

      Library library = new Library(name, version); // key, version
      library.setName(name);
      result = context.getResource(library);

      // not already exists, save it
      if (result == null) {
        context.index(library);
      }
      result = library;

    } else {
      String projectKey;
      if (keyGenerationSafeMode) {
        projectKey = project.getParent().getKey() + ":" + StringUtils.deleteWhitespace(linkedProject.getName());
      } else {
        projectKey =  StringUtils.substringBefore(project.getParent().getKey(), ":") + ":" + StringUtils.deleteWhitespace(linkedProject.getName());
      }
      if (StringUtils.isNotEmpty(project.getBranch())) {
        projectKey += ":" + project.getBranch();
      }

      result = getProjectFromKey(project.getParent(), projectKey);
    }

    return result;
  }
}
