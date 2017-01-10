/*
 * SonarQube C# Plugin
 * Copyright (C) 2014-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.csharp;

import org.sonar.api.config.Settings;
import org.sonarsource.dotnet.shared.plugins.AbstractConfiguration;

public class CSharpConfiguration extends AbstractConfiguration {

  static final String ROSLYN_REPORT_PATH_PROPERTY_KEY = "sonar.cs.roslyn.reportFilePath";
  static final String ANALYZER_PROJECT_OUT_PATH_PROPERTY_KEY = "sonar.cs.analyzer.projectOutPath";
  static final String ANALYSIS_OUTPUT_DIRECTORY_NAME = "output-cs";

  public CSharpConfiguration(Settings settings) {
    super(settings);
  }

  @Override
  public String getRoslynJsonReportPathProperty() {
    return ROSLYN_REPORT_PATH_PROPERTY_KEY;
  }

  @Override
  public String getAnalyzerWorkDirProperty() {
    return ANALYZER_PROJECT_OUT_PATH_PROPERTY_KEY;
  }

  @Override
  public String getAnalyzerReportDir() {
    return ANALYSIS_OUTPUT_DIRECTORY_NAME;
  }

}