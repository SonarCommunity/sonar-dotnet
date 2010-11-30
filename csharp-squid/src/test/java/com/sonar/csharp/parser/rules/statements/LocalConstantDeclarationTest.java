/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.statements;

import static com.sonar.sslr.test.parser.ParserMatchers.notParse;
import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class LocalConstantDeclarationTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.localConstantDeclaration);
    g.type.mock();
    g.constantExpression.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("const type id = constantExpression"));
    assertThat(p, parse("const type id1 = constantExpression, id2 = constantExpression"));
  }

  @Test
  public void testKo() {
    assertThat(p, notParse("const type id"));
    assertThat(p, notParse("const type id1 = constantExpression, id2"));
  }

}
