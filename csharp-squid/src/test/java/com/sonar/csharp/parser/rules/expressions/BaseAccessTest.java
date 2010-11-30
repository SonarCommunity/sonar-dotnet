/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.expressions;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class BaseAccessTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.baseAccess);
    g.expressionList.mock();
    g.typeArgumentList.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("base.id"));
    assertThat(p, parse("base.id typeArgumentList"));
    assertThat(p, parse("base[ expressionList ]"));
  }

}
