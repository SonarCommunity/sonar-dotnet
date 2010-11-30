/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.statements;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class UsingDirectiveTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.usingDirective);
    g.namespaceOrTypeName.mock();
    g.namespaceName.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("using id = namespaceOrTypeName;"));
    assertThat(p, parse("using namespaceName;"));
  }

}
